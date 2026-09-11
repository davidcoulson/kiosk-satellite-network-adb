// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.networkadb;

/**
 * Pure, device-free parsing — no process launches, no Android APIs. Kept
 * separate from {@link NetworkAdbPlugin} so it's unit-testable; see
 * test/AdbMathTest.java.
 *
 * Ported from ha-paneld's AdbController.parseTcpListenerInventory, minus
 * its IPv6/property cross-check epoch machinery (built for a broader
 * "Hardened vs Relaxed" security-authority system this plugin has no
 * counterpart to) — the actual kernel-ABI parsing is identical.
 */
final class AdbMath {
    private AdbMath() {}

    private static final String LISTEN_STATE = "0A";

    /**
     * True if `/proc/net/tcp` or `/proc/net/tcp6` content [raw] shows a
     * LISTEN-state row on [port]. Null means "unknown" (missing, malformed,
     * or an unrecognized header) rather than false — a caller must not
     * treat "can't tell" as "definitely off". `/proc/net/tcp*` is stable
     * kernel ABI: local address is column 2, state is column 4, both
     * 0-indexed after splitting on whitespace.
     */
    static Boolean tcpListening(String raw, int port) {
        if (raw == null) return null;
        String[] lines = raw.split("\n");
        if (lines.length == 0) return null;
        String header = lines[0];
        if (!header.contains("local_address") || !header.contains("st")) return null;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] columns = line.split("\\s+");
            if (columns.length < 4) return null;
            String local = columns[1];
            int separator = local.lastIndexOf(':');
            if (separator <= 0 || separator == local.length() - 1) return null;
            Integer localPort = hexPort(local.substring(separator + 1));
            if (localPort == null) return null;
            String state = columns[3].toUpperCase(java.util.Locale.ROOT);
            if (!isHexByte(state)) return null;
            if (state.equals(LISTEN_STATE) && localPort == port) return true;
        }
        return false;
    }

    private static Integer hexPort(String hex) {
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isHexByte(String s) {
        if (s.length() != 2) return false;
        for (char c : s.toCharArray()) {
            if (Character.digit(c, 16) < 0) return false;
        }
        return true;
    }

    /** Parses a `getprop <name>` result into a port-active tri-state:
     *  true when it's a valid positive TCP port, false when explicitly
     *  cleared (empty or "0"), null when unreadable/malformed. */
    static Boolean portPropertyActive(String rawGetpropOutput) {
        if (rawGetpropOutput == null) return null;
        String value = rawGetpropOutput.trim();
        if (value.isEmpty()) return false;
        try {
            int port = Integer.parseInt(value);
            if (port > 0 && port <= 65535) return true;
            if (port <= 0) return false;
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
