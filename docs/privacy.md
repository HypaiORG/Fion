# Fion Privacy Policy

| Item | Details |
| :--- | :--- |
| Effective Date | November 21, 2026 |
| Application | Fion (Android) |
| Developer Entity | HYPAI ORG |
| Privacy Contact | <support@hypai.org> |

## 1. Scope and Developer Entity

This Privacy Policy explains how **HYPAI ORG** ("we", "us", or "our") handles personal and sensitive user data in the Android application **Fion**. Fion forwards incoming SMS messages and missed call notifications from your Android device to a Bark push server that you configure.

By installing Fion, granting runtime permissions, or enabling forwarding, you acknowledge the data practices described in this Privacy Policy. Fion does not require or support user account registration.

## 2. Personal and Sensitive Data Accessed

Fion accesses only the device data required to deliver its core forwarding functionality.

| Data | Permission | Purpose & Limit |
| :--- | :--- | :--- |
| Incoming SMS sender, message text, and timestamp | `RECEIVE_SMS` | Build and send the SMS push. Fion never reads your SMS inbox. |
| Missed call number, timestamp, SIM reference, and daily missed count | `READ_CALL_LOG` | Build and send the missed call push. Answered and outgoing calls are ignored. |
| SIM phone number, carrier, slot index, and display name | `READ_PHONE_STATE`, `READ_PHONE_NUMBERS` | Identify which SIM received the alert. Fion never places, answers, or manages calls. |

Fion accesses and forwards SMS messages and missed calls only after you grant the corresponding permissions, enable forwarding, and save a valid Bark server configuration. If you revoke `READ_CALL_LOG` and grant it again later, Fion may forward missed calls recorded during the period when access was revoked.

HYPAI ORG operates no backend server for Fion and collects no user data. We do not receive, access, sell, rent, or share your SMS content, call logs, SIM details, or device identifiers. Fion includes no third-party advertising SDKs, analytics trackers, crash reporting tools, or automatic update checks.

## 3. Data Transmission and Third-Party Services

Fion transmits notification data outside your device only through the delivery path you configure.

- **User-Configured Bark Server**. Fion sends each push notification over HTTPS (`POST /push`) to the server entered in `Settings › Bark push settings`. If that server returns an HTTP 307/308 redirect, Fion follows same-origin HTTPS redirects only (same host and port, up to 3 hops) and blocks cross-origin redirects or plaintext HTTP downgrades.
- **Default and Self-Hosted Servers**. The default server `api.day.app` is operated independently by the Bark project, not by HYPAI ORG. Any self-hosted Bark server is operated by you or your chosen host.
- **Apple Push Notification service (APNs)**. The Bark server relays the notification to your Apple devices through APNs, operated by Apple Inc.

Once a push notification leaves your device, its handling, logging, retention, and deletion are governed by the operator of your chosen Bark server and Apple Inc. HYPAI ORG has no control over third-party servers and cannot access or delete data stored on them. Review the privacy policies of your Bark server operator and Apple Inc. before transmitting sensitive content.

## 4. Data Stored on Your Device

Fion stores the following data solely on your Android device to operate reliably.

- **Application settings**. Your Bark server address, Device Key, optional AES encryption key and cipher settings, forwarding toggles, push templates, notification sound, notification level, archive preference, and boot auto-start preference (app display language is managed by Android's `LocaleManager`).
- **Forwarding statistics**. Total forwarded SMS count, missed call count, failed delivery count, average delivery latency, and per-SIM delivery counts with SIM display name and carrier.
- **Offline retry queue**. Rendered push title, body, SIM label, timestamp, and message ID for notifications that fail temporarily due to network or server errors.
- **Internal runtime state**. Onboarding completion flag, the last processed call-log record ID watermark, and a locally generated secret used to compute deduplication message IDs.

## 5. Data Security

We apply technical safeguards in transit and on your device to protect sensitive user data.

- **Encryption in transit**. Fion enforces HTTPS when connecting to the configured Bark server.
- **Optional end-to-end payload encryption**. When you configure an AES encryption key (AES-128, AES-192, or AES-256 in CBC, GCM, or ECB mode), Fion encrypts the notification payload (title, body, group, sound, notification level, and archive flag) on your device before transmission. The `device_key`, deduplication message `id`, and initialization vector `iv` (when used by CBC or GCM) remain unencrypted so Bark can route and decrypt the notification. Only a party holding your AES key, normally your Bark app, can decrypt the payload.
- **On-device isolation**. All local data (settings, runtime state, and offline retry queue) is stored exclusively inside Fion's Android private app storage, which other apps cannot read and which Android keeps encrypted with your lock-screen credential and inaccessible until you first unlock the device after it restarts. Android cloud backup (`allowBackup="false"`) and device-to-device data transfer are disabled so settings, keys, statistics, and queued notifications do not leave your device through system backup or migration.

## 6. Data Retention and Deletion

- **HYPAI ORG servers**. Zero retention, because HYPAI ORG receives no user data.
- **Offline retry queue**. Queued notifications are deleted immediately upon successful delivery or permanent rejection by the Bark server. Undelivered items older than 24 hours are discarded automatically during retry.
- **Settings, statistics, and runtime state**. Retained on your device until you change, reset, clear, or remove them.
- **Third-party servers**. Retention and deletion of delivered pushes depend on your Bark server operator and Apple Inc.

You can delete your local data at any time using the following steps.

1. Reset forwarding statistics in Fion under `Settings › Reset statistics`.
2. Delete all locally stored settings, keys, statistics, runtime state, and queued notifications by clearing Fion's app storage in Android settings or uninstalling Fion.

## 7. Your Controls and Privacy Rights

You retain direct control over data access and forwarding at all times.

- Disable SMS forwarding or missed call forwarding inside Fion at any time.
- Revoke any runtime permission in `Android Settings › Apps › Fion › Permissions`.
- Replace the default server `api.day.app` with a self-hosted Bark server or enable AES encryption before forwarding sensitive messages.

Depending on your jurisdiction, including the European Economic Area, the United Kingdom, and California, you may have rights to know, access, restrict, or delete personal data and to withdraw consent. Because HYPAI ORG stores no user data on its own servers, you can exercise deletion and withdrawal directly on your device using the controls above, or contact us with any privacy request.

## 8. Children's Privacy

Fion is not directed to children under 13 years of age, or the equivalent minimum age in your jurisdiction. HYPAI ORG does not knowingly collect personal data from children.

## 9. Changes to This Privacy Policy

HYPAI ORG may update this Privacy Policy when Fion's data practices or applicable legal requirements change. Any update will be published at the same web address with a revised effective date.

## 10. Contact Us

For privacy questions, compliance inquiries, or requests regarding this Privacy Policy, contact HYPAI ORG directly.

- **Developer Entity** HYPAI ORG
- **Privacy Contact Email** <support@hypai.org>

For non-confidential technical bug reports only, you may also open an issue at <https://github.com/HypaiORG/Fion/issues>. Do not post personal or sensitive data in public issue trackers.
