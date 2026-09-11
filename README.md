# Network ADB for Kiosk Satellite

Keep ADB over TCP (port 5555) available on a panel, reasserting it at every app start on firmware that strips `persist.adb.tcp.port` at boot despite the property's name suggesting it survives on its own — the same unreliability [ha-paneld's own `AdbController.kt`](https://github.com/maxlyth/ha-paneld) documents, and the reason this plugin exists: getting a whole device fleet reliably onto ADB over the network took manual, per-panel babysitting without it.

This is ADB over TCP/IP — it works identically whether the panel is on WiFi or Ethernet. Most of a real kiosk fleet tends to be on Ethernet for reliability, which is the whole reason this plugin isn't called "Wireless ADB": the mechanism has never had anything to do with WiFi specifically.

## Requirements

- Kiosk Satellite with **plugin SDK 1 support**.
- Root (e.g. Magisk) to actually open or close the port. Checking whether ADB is currently active needs no root at all — `getprop` and `/proc/net/tcp*` are both ordinarily app-readable.

## Install and use

1. Wait for a stable GitHub release and its GitHub Actions build to complete.
2. Open **Plugin Manager > Add plugin**, paste this repository URL, review the manifest and README and choose **Trust and install**.
3. Enable **Network ADB** on its entry row and open the subpage.
4. Turn on **Enable network ADB**. It reasserts itself automatically at every future app start — no need to touch it again unless you want it off.

The plugin declares one action, **Check root access and current status**.

## Home Assistant entities

Toggling and monitoring this plugin also works from Home Assistant, not just the plugin subpage:

| Entity | Type | Meaning |
| --- | --- | --- |
| Network ADB | select ("Enabled"/"Disabled") | SDK 1 has no writable switch entity type, only select — this two-option select is the closest equivalent, and mirrors the **Enable network ADB** setting. Changing it from Home Assistant applies immediately, same as the on-device toggle. |
| ADB over TCP active | binary_sensor (`connectivity`) | The *actual* detected state, via the same property/`/proc/net/tcp*` check the plugin subpage uses — not just the desired setting. These can disagree: root missing, a change still propagating, or ADB enabled by something other than this plugin. |

## Security

Enabling this leaves a standing, unauthenticated ADB port open to anything that can reach the panel on your network. It's opt-in and off by default for exactly that reason — only turn it on for panels you're actively administering, and prefer turning it back off once you're done rather than leaving it on indefinitely.

## Never closes a port it didn't open

Turning the setting off only closes ADB if *this plugin's own* current session is what opened it. A technician's manual `adb tcpip 5555`, or ADB enabled through some other mechanism, is left alone — tracked in memory only, no cross-restart ownership record (this plugin has no `Context` and therefore nowhere durable to keep one, the same reasoning the CPU Performance Mode plugin documents for its own governor-restore behavior). Disabling or uninstalling this plugin behaves the same way: it closes only what it itself opened.

## Detection: real listener state, not just a property that might be lying

Whether ADB is actually active is checked two ways, and either one finding it active wins: the `persist.adb.tcp.port`/`service.adb.tcp.port` properties, and a direct read of `/proc/net/tcp`/`/proc/net/tcp6` for an actual kernel-level LISTEN on port 5555. The second check is the one that matters on a panel whose firmware silently clears the property while ADB is still technically bound — verified against a real device: ADB listens on `[::]` (dual-stack IPv6), so the `/proc/net/tcp6` path, not `/proc/net/tcp`, is what actually finds it on real hardware, and the parser is written to handle that IPv6 address-field width automatically.

## Build and test

```sh
export JAVA_HOME=/path/to/jdk
python3 tools/test.py
python3 tools/build.py
```

`tools/test.py` runs device-free logic tests: `/proc/net/tcp*` LISTEN-row parsing (including the real IPv6 format captured from a physical panel, not just a synthetic IPv4 fixture), the property tri-state resolution, and the select-option/enabled-setting mapping in both directions. `tools/build.py` produces the ZIP, checksum and manifest in `dist/`.

## Publishing and handoff

Apache-2.0. The plugin ID is `network-adb`. See [jxlarrea/kiosk-satellite-plugin-hello-world](https://github.com/jxlarrea/kiosk-satellite-plugin-hello-world) for the SDK 1 documentation this plugin was built against.

Author: David Coulson. Built with AI assistance (Claude Code).
