// SPDX-License-Identifier: Apache-2.0
import java.lang.reflect.Method;

/** Device-free tests for AdbMath's package-private static methods,
 *  reflected into since this test lives outside the plugin's package
 *  (same convention as the Hello World template's own test). */
public final class AdbMathTest {
    public static void main(String[] args) throws Exception {
        Class<?> math = Class.forName("me.jxl.kiosk.plugins.networkadb.AdbMath");

        Method tcpListening = math.getDeclaredMethod("tcpListening", String.class, int.class);
        tcpListening.setAccessible(true);

        String header = "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode";
        // Real /proc/net/tcp uses big-endian hex for the address and little-endian-looking hex
        // for the port within local_address — 15B3 = 5555 decimal, 0A = TCP_LISTEN.
        String listeningOn5555 = header + "\n"
            + "   0: 00000000:15B3 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0        0 12345 1 0000000000000000 100 0 0 10 0";
        assertTrue((Boolean) tcpListening.invoke(null, listeningOn5555, 5555), "LISTEN row on the target port detected");
        assertFalse((Boolean) tcpListening.invoke(null, listeningOn5555, 5556), "a different port is not a match");

        String notListening = header + "\n"
            + "   0: 00000000:15B3 00000000:0000 01 00000000:00000000 00:00000000 00000000     0        0 12345 1 0000000000000000 100 0 0 10 0";
        assertFalse((Boolean) tcpListening.invoke(null, notListening, 5555), "ESTABLISHED (01), not LISTEN, is not a match");

        String onlyHeader = header;
        assertFalse((Boolean) tcpListening.invoke(null, onlyHeader, 5555), "a header with no rows is inactive, not unknown");

        // Real /proc/net/tcp6 output captured from a panel: adbd binds `::`
        // (dual-stack IPv6), not a plain IPv4 socket, so this is the format
        // that actually matters in practice — the address field is 32 hex
        // chars instead of tcp4's 8, but parsing only ever looks at the
        // last colon, so no separate handling is needed.
        String tcp6Listening = header + "\n"
            + "   0: 00000000000000000000000000000000:15B3 00000000000000000000000000000000:0000 0A 00000000:00000000 00:00000000 00000000  2000        0 18022 1 0000000000000000 100 0 0 10 0\n"
            + "   2: 0000000000000000FFFF00008104020A:15B3 0000000000000000FFFF00004D03020A:EFCF 01 00000000:00000000 00:00000000 00000000  2000        0 171886 1 0000000000000000 20 4 25 10 -1";
        assertTrue((Boolean) tcpListening.invoke(null, tcp6Listening, 5555), "IPv6 LISTEN row on the target port detected");
        assertFalse((Boolean) tcpListening.invoke(null, tcp6Listening, 5556), "IPv6 rows on a different port are not a match");

        assertNull(tcpListening.invoke(null, null, 5555), "missing content is unknown");
        assertNull(tcpListening.invoke(null, "garbage\nmore garbage", 5555), "unrecognized header is unknown");
        assertNull(tcpListening.invoke(null, header + "\ntoo short", 5555), "a malformed row is unknown");

        Method portPropertyActive = math.getDeclaredMethod("portPropertyActive", String.class);
        portPropertyActive.setAccessible(true);
        assertTrue((Boolean) portPropertyActive.invoke(null, "5555"), "a positive port number is active");
        assertTrue((Boolean) portPropertyActive.invoke(null, "5555\n"), "trailing whitespace is trimmed");
        assertFalse((Boolean) portPropertyActive.invoke(null, ""), "an empty property is inactive");
        assertFalse((Boolean) portPropertyActive.invoke(null, "0"), "a zero port is inactive");
        assertNull(portPropertyActive.invoke(null, (Object) null), "a missing read is unknown");
        assertNull(portPropertyActive.invoke(null, "not-a-number"), "a malformed value is unknown");

        System.out.println("PASS: TCP listener parsing and property tri-state resolution.");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError("expected true: " + message);
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) throw new AssertionError("expected false: " + message);
    }

    private static void assertNull(Object actual, String message) {
        if (actual != null) throw new AssertionError(message + " — expected null but got " + actual);
    }
}
