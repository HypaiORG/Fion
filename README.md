<div align="center">

<img src="docs/images/icon.png" width="96" alt="Fion">

# Fion

Forward SMS and missed calls from your Android phone to [Bark](https://github.com/Finb/Bark).

English | [简体中文](README.zh-CN.md)

</div>

<p align="center">
  <img src="docs/images/en/home.jpg" width="19%" alt="Home">
  <img src="docs/images/en/settings.jpg" width="19%" alt="Settings">
  <img src="docs/images/en/forward.jpg" width="19%" alt="SMS forwarding">
  <img src="docs/images/en/bark.jpg" width="19%" alt="Bark push settings">
  <img src="docs/images/en/keep.jpg" width="19%" alt="Permissions and background">
</p>

## Features

- Forward SMS and missed calls separately, each with its own template, notification group, sound, alert level and history setting.
- Encrypt pushes with AES-128, AES-192 or AES-256 in CBC, ECB or GCM mode.
- Tell dual SIMs apart. Each push names the receiving SIM and its number.
- Queue messages while offline and resend them once the network is back.
- Track total forwarded, success rate, average latency and share per SIM.

## Privacy

- Fion sends no network requests other than pushes to the Bark server you configure.
- No analytics, no crash reporting, no update checks. Dependencies are AndroidX and kotlinx only.
- SMS and calls are parsed on the device. A push that fails temporarily waits on the phone for retry, and queued messages older than 24 hours are dropped automatically on retry.
- Full details in the [privacy policy](docs/privacy.md).

## Requirements

- Android 15 (API 35) or later.

## Install

1. Download `Fion-x.y.z.apk` from [Releases](../../releases) and install it.
2. Open the app and grant SMS, call log, SIM info, notification and background permissions as guided.
3. Go to Settings → Bark push settings and enter your server address and Device Key.

### If SMS access is denied

Android blocks SMS and call log access for apps installed from an APK file and shows "App was denied access".

1. Open system Settings → Apps → Fion.
2. Tap ⋮ in the top right and choose "Allow restricted settings", then verify your identity. The ⋮ menu appears only after Android has denied the permission once.
3. Return to Fion and grant SMS and call log access again.

<p align="center">
  <img src="docs/images/en/restricted-denied.jpg" width="30%" alt="App was denied access">
  <img src="docs/images/en/restricted-allow.jpg" width="30%" alt="Allow restricted settings">
</p>
