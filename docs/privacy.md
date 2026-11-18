# Fion Privacy Policy

Effective date: November 18, 2026

Fion forwards SMS and missed calls from your Android phone to a Bark server that you choose. This policy lists the data Fion reads, where it goes and how long it stays.

## Data Fion reads

| Data | Android permission | Purpose |
| :--- | :--- | :--- |
| Incoming SMS sender and text | `RECEIVE_SMS` | Build the SMS push |
| Missed call number, time and the number of missed calls from that number today | `READ_CALL_LOG` | Build the missed call push |
| SIM phone number, carrier and display name | `READ_PHONE_STATE`, `READ_PHONE_NUMBERS` | Show which SIM received the event |

Fion forwards SMS and missed calls after you grant access, while forwarding is on and a valid Bark channel is set. If you revoke call log access and grant it again, Fion may forward missed calls from the time in between. Fion never forwards answered or outgoing calls.

## Where the data goes

- Fion sends each push over HTTPS to the Bark server you enter in Settings → Bark channel. If that server answers with an HTTPS redirect, Fion follows it, so the push can reach another domain chosen by that server.
- The default server `api.day.app` is run by the Bark project, not by Fion. A self-hosted server is run by you.
- The Bark server delivers the push to your Apple devices through Apple Push Notification service.
- If you set an encryption key, Fion encrypts the notification content with AES before it leaves the phone. The Device Key and a message ID stay unencrypted so Bark can route the push. Only someone holding your key, normally your Bark app, can decrypt the content.
- Fion has no server of its own. Fion's developer receives no data.

## Data stored on the phone

- Your settings, including the Bark server, Device Key, encryption key and push templates.
- Statistics, including forwarded counts, failure count, average latency and per-SIM counts with SIM name and carrier.
- Unsent pushes. A push that fails temporarily waits in a local queue and is retried once the network is back. When a retry reaches a queued push older than 24 hours, Fion drops it. A push the server rejects permanently is dropped at once.

Fion keeps this data in its private app storage. Android backup is disabled, so the data never leaves the phone through cloud backup.

## What Fion does not do

- Fion does not include analytics, advertising, crash reporting or update checks.
- Fion does not sell data or send it anywhere outside the delivery path above.
- Fion does not upload your SMS inbox or call history.

## Your control

- Turn off SMS or missed call forwarding in Fion at any time.
- Revoke any permission in Android Settings → Apps → Fion → Permissions.
- Reset statistics in Fion Settings → Reset statistics.
- Uninstall Fion, or clear its storage, to delete all data on the phone.

## Children

Fion is not directed at children under 13.

## Changes

Changes to this policy are published at this address with a new effective date.

## Contact

Open an issue at <https://github.com/HypaiORG/Fion/issues>.
