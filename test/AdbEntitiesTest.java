// SPDX-License-Identifier: Apache-2.0
import java.lang.reflect.Method;

/** Device-free tests for AdbEntities' package-private static methods,
 *  reflected into since this test lives outside the plugin's package
 *  (same convention as the Hello World template's own test). */
public final class AdbEntitiesTest {
    public static void main(String[] args) throws Exception {
        Class<?> entities = Class.forName("me.jxl.kiosk.plugins.networkadb.AdbEntities");

        Method selectStateFor = entities.getDeclaredMethod("selectStateFor", boolean.class);
        selectStateFor.setAccessible(true);
        assertEquals("Enabled", selectStateFor.invoke(null, true), "enabled maps to the Enabled option");
        assertEquals("Disabled", selectStateFor.invoke(null, false), "disabled maps to the Disabled option");

        Method enabledFromOption = entities.getDeclaredMethod("enabledFromOption", Object.class);
        enabledFromOption.setAccessible(true);
        assertEquals(true, enabledFromOption.invoke(null, "Enabled"), "the Enabled option means true");
        assertEquals(false, enabledFromOption.invoke(null, "Disabled"), "the Disabled option means false");
        assertEquals(null, enabledFromOption.invoke(null, "enabled"), "case matters — no fuzzy matching");
        assertEquals(null, enabledFromOption.invoke(null, (Object) null), "a null option is unrecognized, not a default");
        assertEquals(null, enabledFromOption.invoke(null, Boolean.TRUE), "a non-string option is unrecognized");

        java.lang.reflect.Field optionsField = entities.getDeclaredField("OPTIONS");
        optionsField.setAccessible(true);
        String[] options = (String[]) optionsField.get(null);
        assertEquals(2, options.length, "exactly two options — this stands in for a switch");
        assertEquals("Enabled", options[0], "Enabled listed first");
        assertEquals("Disabled", options[1], "Disabled listed second");

        System.out.println("PASS: enabled-setting <-> select-option mapping, both directions, including unrecognized input.");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!objectsEquals(expected, actual)) {
            throw new AssertionError(message + " — expected " + expected + " but got " + actual);
        }
    }

    private static boolean objectsEquals(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
