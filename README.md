<p align="center"><img src="docs/logo.png" width="112" alt="BT Call Fix logo"></p>

<h1 align="center">BT Call Fix</h1>

Stops cheap Bluetooth earbuds from blasting their own loud ringtone, while still letting you answer calls on them.

Free and open source. No ads, no tracking, and the app has no internet permission.

## The problem

Many unbranded TWS earbuds play a built-in ring sound at a fixed volume whenever the phone's **Phone calls** Bluetooth profile is on. The phone's volume setting doesn't affect it. With the profile off, the ring stays on the phone, but you can't use the earbuds' microphone for calls.

## What this app does

It switches that per-device **Phone calls** profile automatically:

| Phone state | Profile | Result |
|---|---|---|
| Ringing | OFF | Earbuds stay silent, phone rings normally |
| Answered / dialing | ON | Call audio and mic move to the earbuds (about half a second) |
| Call ended | OFF | Back to media-only |

No root needed. It uses [Shizuku](https://shizuku.rikka.app/) to change the setting that Android only lets system apps change.

## Requirements

- [Shizuku](https://shizuku.rikka.app/) installed and running
- Bluetooth earbuds already paired with the phone

## The app

A single screen with a status card that says whether everything is ready, a short setup checklist (permissions, Shizuku, earbuds) with buttons to fix each item, a list of your paired devices to pick the earbuds, two test buttons, and an activity log you can copy or clear.

## Install

1. Download the APK: **[BtCallFix.apk](https://github.com/decentcodes/BtCallFix/releases/latest/download/BtCallFix.apk)**, or build it yourself, see below. Open the file on your phone and allow installing from that source when asked.
2. Install **Shizuku** and start it (see Shizuku's own guide). If your phone has a security feature that blocks debugging, it must be off while you do this, see [Limitations](#limitations).
3. Open BT Call Fix and tap:
   1. **Allow phone + Bluetooth permissions**
   2. **Allow Shizuku access**
4. Connect your earbuds and pick them in the list.
5. Tap **Test: calls OFF** and **Test: calls ON**. The "Phone calls" switch for the earbuds in Android's Bluetooth settings should follow.
6. In Android settings, set the app's battery usage to **Unrestricted**.

After that you can close the app. Android wakes it up for each call.

## Daily use

- After every **phone reboot**, start Shizuku again. The app itself needs nothing.
- If Shizuku isn't running when a call comes in, the earbuds simply stay in whatever call setting they were last in.
- Leave the earbuds' "Phone calls" switch to the app. It sets it again at the next call event.
- The app only touches the earbuds you picked.

## Build

**With GitHub Actions (no local setup):** push to `main`, open the **Actions** tab, and download the `BtCallFix-debug-apk` artifact from the run.

**With Android Studio:** open this folder and press Run. The project needs JDK 17 and Gradle 8.9 (Android Studio downloads both).

## Troubleshooting

The log at the bottom of the app shows what happened on each call and each test. Common messages:

| Log message | Meaning / fix |
|---|---|
| `timeout: Shizuku not ready` | Shizuku isn't running. Start it. |
| `Shizuku permission not granted` | Tap "Allow Shizuku access" in the app. |
| `no bluetooth adapter [...]` | The Shizuku side couldn't reach Bluetooth. Open an issue with the full line. |
| `ERROR: SecurityException ...` | The phone refused the change. This setup may not work on your device. |
| Nothing logged on calls | Check that the app's battery setting is Unrestricted, that your phone's "put unused apps to sleep" style setting is off for it, and that phone permission is granted. |

More detail on how it works: [docs/how-it-works.md](docs/how-it-works.md).

## Limitations

- **Phone security features can get in the way.** Some phones have a protection mode that blocks USB or wireless debugging, which Shizuku needs. Turning such a mode on stopped Shizuku, so it has to stay off for this app to work. That lowers protection against sideloaded apps and USB commands, so be careful what you install.
- **Shizuku must be restarted after each reboot.** This is a Shizuku limitation without root.
- The app relies on hidden Android Bluetooth internals. They can change between Android versions, so a future update may need code changes.
- Earbuds differ. Some may still ring on their own even with the profile off, or take longer to connect after answering.
- Only one earbud pair is supported at a time.

## Project layout

```
app/src/main/
  aidl/.../IBtService.aidl      interface between the app and the Shizuku process
  java/com/example/btcallfix/
    call/CallReceiver.kt        listens for phone state changes
    shizuku/ShizukuBridge.kt    connects to Shizuku, queues requests
    shizuku/BtUserService.kt    runs with shell privileges, changes the profile
    data/Prefs.kt               saved earbuds address and the in-app log
    ui/MainActivity.kt          the single screen (status, setup, earbuds, test, log)
    ui/Theme.kt                 light/dark colour palette
  res/drawable/                 logo, launcher icon layers, sun/moon icons
  res/mipmap-anydpi/            adaptive launcher icon
docs/                           how it works
.github/workflows/              CI build and release
```

## License

[MIT](LICENSE). Not affiliated with Shizuku.
