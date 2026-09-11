# Changelog

## 0.1.0

- Enable/disable ADB over TCP (port 5555), reasserted automatically at every app start for firmware that strips `persist.adb.tcp.port` at boot.
- Detection via both the ADB properties and a direct `/proc/net/tcp`/`/proc/net/tcp6` LISTEN check — no root required to check status, only to change it.
- Never closes a port this plugin didn't open itself, in the current session — an externally-enabled ADB port is left alone.
- Simulation mode for testing without root or a real device.
