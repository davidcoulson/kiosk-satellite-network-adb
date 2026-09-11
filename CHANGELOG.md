# Changelog

## 0.3.0

- Replace the two-option select workaround with a real Home Assistant `switch`, now that upstream shipped writable switches ("Add SDK 1 writable plugin switches" in jxlarrea/kiosk-satellite, resolving [the feature request](https://github.com/jxlarrea/kiosk-satellite-plugin-hello-world/issues/3) filed for exactly this). Commands arrive as `switch.adb` with `{on: boolean}`. Removes `AdbEntities` and its test — the workaround's string↔boolean mapping has no remaining purpose.
- Breaking for Home Assistant: the entity changes type from `select` to `switch`, so any automation or dashboard card referencing the old select entity needs updating.

## 0.2.0

- Publish a Home Assistant select entity ("Enabled"/"Disabled") standing in for a switch — SDK 1 has no writable switch entity type, only select — that mirrors and controls the **Enable network ADB** setting. Publish a read-only `adb_active` binary_sensor for the actually-detected state, which can disagree with the desired setting (root missing, a change still propagating, or ADB enabled by something else). Declares the `entities` capability.

## 0.1.1-20260911

- Correction: an earlier attempt at this release used a 4-component date-based version (`2026.09.11.01`), which Kiosk Satellite's plugin manifest validator rejects (`FormatException: Invalid plugin ID or version`) — it requires 3-component semver, optionally with a `-suffix`. That broken release has been removed; this one embeds the date as a semver prerelease suffix instead.
- Metadata only otherwise: author field and AI-assisted note in the README.

## 0.1.0

- Enable/disable ADB over TCP (port 5555), reasserted automatically at every app start for firmware that strips `persist.adb.tcp.port` at boot.
- Detection via both the ADB properties and a direct `/proc/net/tcp`/`/proc/net/tcp6` LISTEN check — no root required to check status, only to change it.
- Never closes a port this plugin didn't open itself, in the current session — an externally-enabled ADB port is left alone.
- Simulation mode for testing without root or a real device.
