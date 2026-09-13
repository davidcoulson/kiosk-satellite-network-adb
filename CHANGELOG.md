# Changelog

## 0.5.0

- **Falls back to Shizuku when direct root is unavailable.** Declares the `shizuku` capability and runs its property writes through the host's Shizuku channel when `su` will not start. Root is still tried first and still preferred: the persistent session costs one grant for the plugin's lifetime, where every Shizuku call is a fresh binder round trip.
- This exists because `su` is granted per app UID. Reinstalling Kiosk Satellite gives it a new UID, so a Magisk grant made beforehand silently stops applying and the plugin reports "root isn't available" on a demonstrably rooted panel. Shizuku's authorization is held against the KS package instead, so it survives the reinstall that breaks the root grant.
- The privilege gap is surfaced rather than hidden: a Shizuku server started from ADB runs as shell (UID 2000), and setting the ADB properties may need root on a given firmware. The status line now names the channel — `root`, `Shizuku (root)` or `Shizuku (shell)` — and a refusal is reported as a failed command rather than a mysterious no-op.
- Status and error text no longer claim root specifically where Shizuku would do.
- Tested against fake hosts with no root, no Shizuku and no device: ungranted and pre-capability hosts are treated as "no channel", and a non-zero exit or a host-side timeout is reported as failure rather than as an empty success.

## 0.4.0

- **One root shell per plugin instead of one per command.** Every root call used to spawn a fresh `su`, and Magisk shows its "granted Superuser rights" toast per request. The plugin now holds a single `su` session and writes commands to its stdin, so root is granted once per plugin start.
- The unprivileged probe path (`runPlain`) is unchanged and still spawns a plain process: it answers "is ADB already on" without root, and costs no Superuser grant at all.
- Commands are framed by a per-session random sentinel (`echo <token>:$?`), so exit codes and output read exactly as before. Each command runs in a subshell, so one containing `exit` ends that subshell rather than silently killing the session and costing root for the rest of the plugin's life.
- A timeout or a dead shell closes the session and the next call opens a clean one. Late output from a timed-out command can't be told apart from the next command's, so resynchronising would be guesswork — it's killed instead. Failures cost one extra grant, never silent corruption.
- The session ends with the plugin: `stop()` closes it, so disabling the plugin doesn't leave a root shell alive.
- Tested against `sh` rather than `su`, which needs no root or device: the load-bearing assertion is that two commands report the same PID, since a regression to per-command spawning would only show up as toast spam on a panel.

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
