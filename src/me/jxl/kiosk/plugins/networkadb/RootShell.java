// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.networkadb;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Plain `su -c` command execution — same pattern as this session's other
 *  plugins: infrequent, user-triggered operations, not a hot path. */
final class RootShell {
    private RootShell() {}

    static final long DETECT_TIMEOUT_MS = 4000L;
    static final long COMMAND_TIMEOUT_MS = 4000L;

    static boolean isRooted() {
        return run("id", DETECT_TIMEOUT_MS);
    }

    static boolean run(String cmd, long timeoutMs) {
        try {
            Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            boolean finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Runs [cmd] unprivileged (no su) — used for the property/listener
     *  reads that don't need root, matching ha-paneld's own reasoning:
     *  detecting an externally-enabled ADB port should work even on a
     *  panel this plugin has no root access to. */
    static String runPlain(String[] cmd, long timeoutMs) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = readAll(p.getInputStream());
            boolean finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                return null;
            }
            return p.exitValue() == 0 ? out : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readAll(InputStream in) throws Exception {
        StringBuilder out = new StringBuilder();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) out.append(new String(buf, 0, n, StandardCharsets.UTF_8));
        return out.toString();
    }
}
