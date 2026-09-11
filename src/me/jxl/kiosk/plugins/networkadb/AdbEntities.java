// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.networkadb;

/**
 * Pure mapping between this plugin's boolean "enabled" setting and the two-
 * option select entity SDK 1 exposes it as — no PluginHost calls, so it's
 * unit-testable; see test/AdbEntitiesTest.java.
 *
 * SDK 1 has no writable "switch" entity type, only sensor/text_sensor/
 * binary_sensor (all read-only) and select — so a two-option select
 * ("Enabled"/"Disabled") is the closest thing to a Home Assistant switch
 * this SDK offers, and what this plugin publishes for {@code adb}.
 */
final class AdbEntities {
    private AdbEntities() {}

    static final String ENABLED_OPTION = "Enabled";
    static final String DISABLED_OPTION = "Disabled";
    static final String[] OPTIONS = {ENABLED_OPTION, DISABLED_OPTION};

    static String selectStateFor(boolean enabled) {
        return enabled ? ENABLED_OPTION : DISABLED_OPTION;
    }

    /** The desired enabled-state an incoming {@code select.adb} command's
     *  {@code option} value represents, or null for anything unrecognized
     *  — never silently defaults to either state on a malformed command. */
    static Boolean enabledFromOption(Object option) {
        if (ENABLED_OPTION.equals(option)) return true;
        if (DISABLED_OPTION.equals(option)) return false;
        return null;
    }
}
