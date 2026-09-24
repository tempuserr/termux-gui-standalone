# Standalone Termux:GUI

This is a fork of [termux/termux-gui](https://github.com/termux/termux-gui) that
runs as a **standalone Android GUI daemon**, without requiring Termux to be
installed or running on the device.

## What changed

- `GUIReceiver` is now `android:exported="true"` (was `false`).
- `ServiceShutdownReceiver` is now `android:exported="true"` (was `false`).
- `android:sharedUserId="com.termux"` has been removed from the manifest.
- The client UID check in `ConnectionHandler` (comparing
  `peerCredentials.uid` against the GUI app's own UID) has been removed.
- `GUIService` remains `android:exported="false"` — it is not a direct
  IPC entry point, `GUIReceiver` is.
- No allowlist, package/signature check, permission check, token, or
  root requirement has been added in place of the removed UID check.
  The connection surface is intentionally open to any local app on the
  device.
- The JSON/Protobuf protocol (`V0Json`, `V0Proto`) is unchanged.

As a result, **any locally installed Android app** (not just Termux, and
not just apps signed with the same key or sharing the same UID) can send
an explicit broadcast to `GUIReceiver` and use the Termux:GUI IPC
protocol, and can send a broadcast to `ServiceShutdownReceiver` to stop
the service.

## Example: connecting to GUIReceiver

```sh
am broadcast -n com.termux.gui/.GUIReceiver \
  --es mainSocket "my_main_socket" \
  --es eventSocket "my_event_socket"
```

`mainSocket` and `eventSocket` are the names of two abstract `LocalSocket`
servers your client has already created and is listening on. After the
broadcast, the GUI daemon connects to both sockets and performs the
normal protocol handshake (JSON V0 or Protobuf V0, unchanged from
upstream).

## Example: stopping the service

```sh
am broadcast -n com.termux.gui/.ServiceShutdownReceiver
```

This stops `GUIService` if it is running. No error occurs if it is
already stopped.

## Who can use the API

Every local process on the device can attempt to use the API — that is
the explicit design goal of this fork. There is no per-client
authentication or pairing step.

## Installation

This build **cannot update an existing `com.termux.gui` installation
in-place**: it uses a different signing key and no longer declares
`sharedUserId`, so Android will refuse the update. If you currently
have the upstream Termux:GUI (or an earlier build of this fork with a
different key) installed:

1. Uninstall the existing `com.termux.gui` app first. **This removes
   its data and settings.**
2. Install this build's APK.

There is intentionally no attempt to preserve an upgrade path with the
plugin-based upstream version.

## Required one-time setup

Because the daemon runs on-demand rather than as a resident background
process (see below), a cold start from the background depends on
Android permissions that must be granted once, manually:

1. Launch the app at least once after installing it. Do **not** use
   "Force stop" afterwards — a force-stopped app does not receive
   broadcasts.
2. Enable **"Display over other apps"** (`SYSTEM_ALERT_WINDOW`) for
   this app in Android's app settings.
3. Optionally, set the app's battery usage to **"Unrestricted"**
   (naming varies by manufacturer). This costs nothing at idle, since
   the app has no background tasks, alarms, or jobs.

Some device manufacturers may require additional permissions for
starting windows from the background.

## On-demand model

There is no resident process. `GUIService` stops itself
(`stopSelf()`) after `Settings.instance.timeout` seconds without any
connected client (default: 3 seconds). There is no `BOOT_COMPLETED`
receiver, no `AlarmManager`, `JobScheduler`, or `WorkManager` usage,
and no other keep-alive mechanism. After the service stops, the
process is an ordinary cached process that Android may kill at any
time.

## Recommended client pattern

Because a cold background start is not always possible depending on
the OS version and the calling context, clients should follow this
fallback pattern:

1. Send the broadcast to `GUIReceiver` and wait a few seconds for a
   connection.
2. If no connection is established, start the app to the foreground
   with:
```sh
   am start -n com.termux.gui/.GUIConfigActivity
```
   and retry the broadcast. A visible foreground app is exempt from
   the background-start restrictions; starting it from Termux or
   another background app may itself require permissions of its own.
3. If a connection still cannot be established, surface an error to
   the user (for example via `cmd notification post`, purely
   informational) and return an error code from the client.

## Known limitations

- `targetSdk` is intentionally kept at 34 and should not be raised:
  doing so narrows the background foreground-service-start exemption
  this fork relies on.
- The default view of the home screen widget (`GUIWidget`) still
  hard-codes a reference to `com.termux/.app.TermuxActivity`. Without
  Termux installed, tapping the default widget view does nothing.
  This is unchanged from upstream and out of scope for this fork.
