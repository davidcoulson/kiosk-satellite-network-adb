// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.networkadb;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Keeps ADB over TCP on port 5555 available, reasserting it at every
 * plugin start — some panel firmwares strip `persist.adb.tcp.port` at
 * boot despite the property's name suggesting it survives on its own
 * (the same unreliability ha-paneld's own AdbController.kt documents and
 * this session hit firsthand getting a whole device fleet onto adb-over-
 * TCP). Detection works without root (`getprop` and `/proc/net/tcp*` are
 * both ordinarily app-readable); actually opening or closing the port
 * needs root.
 *
 * Never closes a port this plugin didn't open: "off" only tears ADB down
 * if this session's own configure() calls are what turned it on — an
 * externally-enabled ADB port (a technician's manual `adb tcpip 5555`) is
 * left alone, in-memory tracking only, no cross-restart ownership record
 * (this plugin has no Context and therefore nowhere durable to keep one;
 * see the CPU Performance Mode plugin's identical reasoning for why).
 *
 * Also publishes two SDK 1 entities (see AdbEntities): a two-option
 * select ("Enabled"/"Disabled") standing in for a switch — SDK 1 has no
 * writable switch entity type — and a read-only binary_sensor reporting
 * whether ADB is *actually* active right now, distinct from the select's
 * desired-state value (they can disagree, e.g. while root access is
 * missing or a change is still propagating).
 */
public final class NetworkAdbPlugin implements KioskPlugin {
    private static final int PORT = 5555;

    private final AtomicBoolean alive = new AtomicBoolean();
    private PluginHost host;
    private ExecutorService worker;
    private Map<String, Object> settings = new HashMap<>();

    private volatile boolean rooted;
    private Boolean lastSimulation;
    // In-memory only: true once THIS session's own apply() has turned ADB
    // on. Only this plugin's own opened port ever gets closed by it.
    private boolean openedByThisSession;

    public void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        alive.set(true);
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "network-adb");
            t.setDaemon(true);
            return t;
        });
        configure(settings);
    }

    public void configure(Map<String, Object> values) {
        Map<String, Object> copy = new HashMap<>(values);
        submit(() -> {
            boolean simulation = Boolean.TRUE.equals(copy.get("simulation"));
            boolean recheck = lastSimulation == null || lastSimulation != simulation;
            settings = copy;
            lastSimulation = simulation;
            if (recheck) detect();
            reconcile();
        });
    }

    public void execute(String command, Map<String, Object> args) {
        submit(() -> {
            if ("detect".equals(command)) {
                detect();
                reportStatus();
            } else {
                throw new IllegalArgumentException("Unknown command");
            }
        });
    }

    public void onEvent(String event, Map<String, Object> payload) {
        if (!"select.adb".equals(event)) return;
        Boolean wantEnabled = AdbEntities.enabledFromOption(payload.get("option"));
        if (wantEnabled == null) return;
        submit(() -> {
            settings.put("enabled", wantEnabled);
            host.saveSettings(settings);
            reconcile();
        });
    }

    private boolean simulation() {
        return Boolean.TRUE.equals(settings.get("simulation"));
    }

    private void detect() {
        rooted = simulation() || RootShell.isRooted();
    }

    /** True/false/null (unknown) — never assumed false just because a read
     *  failed, matching AdbMath's own fail-to-unknown contract. Any
     *  positive signal (a property or the actual kernel listener) wins
     *  immediately; false only once every signal is confirmed inactive. */
    private Boolean isActive() {
        boolean everyPropInactive = true;
        for (String prop : new String[]{"persist.adb.tcp.port", "service.adb.tcp.port"}) {
            String raw = RootShell.runPlain(new String[]{"getprop", prop}, RootShell.DETECT_TIMEOUT_MS);
            Boolean state = AdbMath.portPropertyActive(raw);
            if (Boolean.TRUE.equals(state)) return true;
            if (state == null) everyPropInactive = false;
        }
        boolean everyListenerInactive = true;
        for (String path : new String[]{"/proc/net/tcp", "/proc/net/tcp6"}) {
            String raw = RootShell.runPlain(new String[]{"cat", path}, RootShell.DETECT_TIMEOUT_MS);
            Boolean state = AdbMath.tcpListening(raw, PORT);
            if (Boolean.TRUE.equals(state)) return true;
            if (state == null) everyListenerInactive = false;
        }
        return (everyPropInactive && everyListenerInactive) ? false : null;
    }

    private void reconcile() {
        boolean wantEnabled = Boolean.TRUE.equals(settings.get("enabled"));
        if (simulation()) {
            openedByThisSession = wantEnabled;
            reportStatus();
            return;
        }
        if (!rooted) {
            host.status("Root access is required to change ADB state and isn't available on this panel.", true);
            publishEntities(null);
            return;
        }
        if (wantEnabled) {
            boolean ok = RootShell.run(
                "setprop persist.adb.tcp.port " + PORT + " && "
                    + "setprop service.adb.tcp.port " + PORT + " && "
                    + "setprop ctl.restart adbd",
                RootShell.COMMAND_TIMEOUT_MS);
            if (ok) openedByThisSession = true;
            host.status(ok ? "OK: ADB over TCP on port " + PORT
                : "Failed to enable ADB — check root access.", !ok);
            publishEntities(ok ? true : null);
        } else if (openedByThisSession) {
            boolean ok = RootShell.run(
                "setprop persist.adb.tcp.port \"\" && "
                    + "setprop service.adb.tcp.port \"\" && "
                    + "setprop ctl.restart adbd",
                RootShell.COMMAND_TIMEOUT_MS);
            if (ok) openedByThisSession = false;
            host.status(ok ? "OK: ADB over TCP closed" : "Failed to disable ADB.", !ok);
            publishEntities(ok ? false : null);
        } else {
            reportStatus();
        }
    }

    private void reportStatus() {
        if (!alive.get()) return;
        if (simulation()) {
            host.status("Simulation mode. No hardware is being changed.", false);
            publishEntities(openedByThisSession);
            return;
        }
        if (!rooted) {
            host.status("Root access (e.g. via Magisk) is required to enable ADB from here.", true);
            publishEntities(null);
            return;
        }
        Boolean active = isActive();
        String state = active == null ? "unknown" : (active ? "active" : "off");
        host.status("ADB over TCP: " + state
            + (openedByThisSession ? " (opened by this plugin)" : ""), false);
        publishEntities(active);
    }

    /** Publishes the two-option select standing in for a switch (state
     *  mirrors the "enabled" setting — the desired state), and a
     *  read-only binary_sensor for [active] — whether ADB is *actually*
     *  reachable right now, which can lag or disagree with the desired
     *  state (root missing, a change still propagating, or someone else
     *  entirely toggling ADB outside this plugin). */
    private void publishEntities(Boolean active) {
        host.publishSelect("adb", "Network ADB", AdbEntities.OPTIONS,
            AdbEntities.selectStateFor(Boolean.TRUE.equals(settings.get("enabled"))));
        host.publishBinarySensor("adb_active", "ADB over TCP active", "connectivity", active);
    }

    private interface Task { void run() throws Exception; }

    private void submit(Task task) {
        if (!alive.get()) return;
        worker.execute(() -> {
            if (!alive.get()) return;
            try {
                task.run();
            } catch (Exception e) {
                host.status(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), true);
            }
        });
    }

    public void stop() throws Exception {
        alive.set(false);
        worker.shutdownNow();
        worker.awaitTermination(1000, TimeUnit.MILLISECONDS);
        // Close only what this plugin itself opened, same rule as a live
        // toggle-off — disabling or uninstalling this plugin must not
        // strand a technician's own manually-enabled ADB port closed.
        if (rooted && !simulation() && openedByThisSession) {
            RootShell.run(
                "setprop persist.adb.tcp.port \"\" && "
                    + "setprop service.adb.tcp.port \"\" && "
                    + "setprop ctl.restart adbd",
                RootShell.COMMAND_TIMEOUT_MS);
        }
    }
}
