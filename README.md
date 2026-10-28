<div align="center">
   <img src="art/icon-512.png" alt="A panda reading a message on a phone, with a chat bubble" width="160">
   <h1>sms-droid</h1>
</div>

An SMS app for Android 16 that does SMS only, and keeps every message in a git repository.

- **Contact names for sender names.** Banks and operators send from names like `ResalatBank` or `HAMRAH_AVAL`. Save a contact with that name as its number, and the conversation shows the contact. Spaces, underscores and case do not matter.
- **Backup to git over SSH.** One JSON-lines file per conversation, added to and never trimmed. The SSH key is made on the phone. Use a private repository unless you want the messages public. Restore puts back what the phone is missing.
- **Copy one-time codes** from the notification, for `رمز پویا`, `کد`, `code` and `OTP`.
- **Two SIMs.** A reply goes out on the SIM the conversation came in on, and the composer lets you choose.
- **Delete ads.** An ad is a message with the opt-out line `لغو11`, or anything from a sender you marked "Treat as ads"; one-time codes never are. It arrives without a notification and is deleted once the backup has it. The backup files ads under `ads/`, apart from `messages/`, and keeps the ad senders in `ad-senders.txt`, which you can edit in git.
- **Delete conversations** from the list (long press to select several) or the conversation. When a backup repository is set, a backup runs first, and nothing is deleted if it fails, so the backup keeps every deleted message.
- **The naz theme** from notes-droid (dark, or Daylight on paper), with Persian set in Vazirmatn; or Material You from the wallpaper, as in passwords-droid. Settings > Look.
- Reply and mark as read from the notification, delivery reports, and quick replies to a call.

There is no MMS. The phone it is written for is on a network that has none.

## Build

```bash
just sdk       # once
just ci
just install   # debug build, installed next to any release build
```

## License

GPL-3.0. Parts of the sending and receiving logic follow [Fossify Messages](https://github.com/FossifyOrg/Messages).
