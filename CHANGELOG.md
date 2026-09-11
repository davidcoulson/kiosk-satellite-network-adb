# Changelog

## 0.1.1-20260911

- Correction: an earlier attempt at this release used a 4-component date-based version (`2026.09.11.01`), which Kiosk Satellite's plugin manifest validator rejects (`FormatException: Invalid plugin ID or version`) — it requires 3-component semver, optionally with a `-suffix`. That broken release has been removed; this one embeds the date as a semver prerelease suffix instead.
- Metadata only otherwise: author field and AI-assisted note in the README.

## 0.1.0

- Enable/disable ADB over TCP (port 5555), reasserted automatically at every app start for firmware that strips `persist.adb.tcp.port` at boot.
- Detection via both the ADB properties and a direct `/proc/net/tcp`/`/proc/net/tcp6` LISTEN check — no root required to check status, only to change it.
- Never closes a port this plugin didn't open itself, in the current session — an externally-enabled ADB port is left alone.
- Simulation mode for testing without root or a real device.
