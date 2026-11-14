# How it works

## Background

Android's Bluetooth settings have a per-device **Phone calls** switch. Internally it is the *connection policy* of the Hands-Free (HFP) headset profile for that device: `ALLOWED` (100) or `FORBIDDEN` (0). Normal apps cannot change it because it needs the `BLUETOOTH_PRIVILEGED` permission. The `shell` user has it.

[Shizuku](https://shizuku.rikka.app/) starts a small service that runs as the `shell` user and lets other apps run code there. This app uses that to change the policy.

## Flow

```
Phone state change (RINGING / OFFHOOK / IDLE)
        |
CallReceiver  (manifest receiver, woken by Android)
        |  decides ON or OFF, ignores duplicates and call-waiting rings
        v
ShizukuBridge  (binds the Shizuku user service, queues the request, 10 s timeout)
        |  Binder / AIDL (IBtService)
        v
BtUserService  (runs as shell)
        |  gets BluetoothAdapter -> BluetoothHeadset proxy
        |  setConnectionPolicy(device, 100 or 0)  + connect / disconnect
        v
Earbuds' "Phone calls" profile changes
```

## State rules (`CallReceiver`)

- `RINGING` -> OFF, unless a call is already active (call waiting), then ignored.
- `OFFHOOK` -> ON (answered or dialing out).
- `IDLE` -> OFF.
- Repeated identical broadcasts are ignored.

## Workarounds in `BtUserService`

The Shizuku process is not a normal app process, so a few things Android usually sets up are missing:

- **Bluetooth service manager:** installed by hand with reflection, otherwise Bluetooth lookups hit a null object.
- **Adapter lookup:** tries the normal route, then `getDefaultAdapter()`, then builds the adapter directly. The log notes which one failed.
- **Message loop:** Bluetooth proxy calls run on the process's main looper, while the waiting happens on the binder thread, otherwise it deadlocks.
- **Hidden APIs:** unlocked with `HiddenApiBypass`, then the hidden methods are called by reflection.

## Why the setup needs Shizuku running

Without Shizuku, the app cannot change the policy, so a call just leaves the earbuds as they were.

## UI notes

The screen is built in code with plain Android views (no extra UI libraries). `MainActivity.render()` rebuilds the whole screen whenever something changes: permissions, Shizuku starting or stopping, choosing earbuds, or switching the theme. The theme choice (follow phone / light / dark) is stored in `Prefs`.
