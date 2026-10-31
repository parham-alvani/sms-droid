# CLAUDE.md

Guidance for Claude Code working in this repository.

## What this is

An SMS-only messaging app for one phone: an A142 on Android 16 with two Iranian SIMs, whose network has no MMS at all. It replaces Fossify Messages, which reuses `PhoneLookup` and so never shows a contact name for a sender name such as `ResalatBank` or `HAMRAH_AVAL`. It keeps every message in a git repository, the way notes-droid and passwords-droid keep theirs.

The build setup is copied from [passwords-droid](https://github.com/parham-alvani/passwords-droid), which copied it from [notes-droid](https://github.com/parham-alvani/notes-droid); notes-droid's CLAUDE.md explains the toolchain traps (JDK 17 only, AGP 9 built-in Kotlin, never apply `org.jetbrains.kotlin.android`). They all apply here. `minSdk` is 36 on purpose: the one phone runs 16, and no compatibility path is kept.

## Commands — always `just`

```bash
just doctor    # confirm the toolchain is usable
just ci        # ktlint, android lint, tests, debug build
just test      # unit tests (JVM + Robolectric)
just fmt       # ktlint format
just install   # debug build onto the connected device
just verify-apk
```

## Modules

```text
core/sms/    kotlin("jvm")     addresses, contact matching, one-time codes, backup format and merge -- NO Android
core/data/   android-library   SMS provider, contacts, settings, git (JGit + sshd), backup repository and worker
app/         application       Compose UI, receivers, sending, notifications, DI wiring
```

Keep Android out of `core/sms`. Anything that decides who a message is from, or what the backup file says, belongs there and is tested there.

## Rules that matter

- **The SMS provider is the only copy.** There is no Room mirror like Fossify's. Screens query `SmsStore` and re-query when `SmsStore.changes()` emits.
- **Only the default SMS app may write messages, and it must.** `SmsDeliverReceiver` is the only thing that stores an incoming message; if it fails before the insert, the message is gone. Keep it small.
- **Sender names are matched by `ContactIndex`, never by `PhoneLookup`.** `PhoneLookup` reduces an address to digits, so a sender name matches nothing; that bug is why this app exists.
- **The backup only adds.** `BackupFile.merge` never removes a line, so deleting a message on the phone keeps it in git, and every run is safe to repeat. A line that does not parse stops the backup (`BackupFile.Corrupt`) instead of being written over. The one exception is filing: `BackupPlan.backup` moves a line between `messages/` and `ads/` when `AdRule` changes its mind, and the two folders together never lose one.
- **Every backup starts from the remote.** `BackupGit.refresh` resets the work tree hard to the remote branch; the phone's messages are merged in again each run. A rejected push means "refresh and merge again", up to three times. There is no rebase and no conflict state.
- **One file per address**, `messages/<address>.jsonl`, by `BackupLayout.pathFor`. Sender names are lower-cased, because a case-insensitive checkout would merge two files git keeps apart. Ads go to `ads/<address>.jsonl` instead, and restore reads only `messages/`: an ad put back would be deleted again.
- **Ads are deleted only after the backup has them.** `ConversationDeleter.deleteAds` backs up first and deletes only ads older than the backup's start. An ad is `Ads.isAd` (the `لغو11` opt-out line) or anything from an ad sender, and never a message with a one-time code.
- **The ad senders live in git too**, in `ad-senders.txt` at the root. Each backup joins it with the phone's list by `AdSenders.merge`, a three-way merge against `Settings.adSendersSynced`, so a removal on either side sticks. The phone takes the merged list only after the push.
- **Blocking is one list for messages and calls**, `BlockRule` in `core/sms`, kept in git as `blocked.txt` and merged like the ad senders. An entry is a number, a sender name (`Ads.senderKey`) or a number range ending in `*` (`+98942*`); `Blocking.entry` spells each the canonical way, so a hand-edited `0942*` in git works. A block has no one-time-code exception. What a blocked sender sent is filed under `blocked/` and deleted from the phone after the backup has it (`ConversationDeleter.sweep`), whether or not ads are deleted.
- **Calls are blocked by `CallScreener`, not by being the dialer.** It holds the call screening role, which Android asks only about numbers that are not contacts. A rejected call skips the notification but stays in the call log. Any doubt lets the call through.
- **No MMS.** `MmsPushReceiver` and the `mms`/`mmsto` schemes exist only because the SMS role refuses an app without them.
- **The SSH key is sealed** by the at-rest Keystore key, which does not require an unlocked phone: the backup runs after a message arrives, usually in a pocket.
- **Host keys are pinned to GitHub's** in `PinnedHostKeys`, copied from passwords-droid.

## Tests

`core/data` runs Robolectric on SDK 36, because Robolectric refuses a manifest whose `minSdk` is above the emulated SDK. SDK 36 needs Java 21, so `core/data/build.gradle.kts` gives the test JVM a Java 21 launcher and opens `java.io` and `jdk.internal.access`; the code itself is still compiled for 17. `BackupGitTest` runs against a bare repository on disk.

## Public-repo hygiene

This repository is public; the backup it writes is the user's choice. Never commit a message, a phone number from a real phone, a remote URL, or a log from a real backup. `.gitignore` refuses `*.jsonl` outside test resources.
