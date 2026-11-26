# Roost Remote

An Android app that opens a [Roost](https://roost.digitalku.com) library kept on another computer,
over the local network or your Tailscale. Roost on the computer shares the library it has open
(**Share on this network…** in the `☰`). The phone browses it and edits tags, ratings, folders and
notes. Every edit lands in the library's journal, the same as an edit made in the window.

It is a client of Roost's remote protocol, `docs/REMOTE.md` in the Roost repository, API 1. It holds
no library of its own: no index, no journal, no copies of files.

## What it does

- Pairs with a library using the library's password, once. After that it holds a device token,
  sealed with a key in the Android Keystore. The password is not kept.
- The grid of the library, paged as the window pages it. The window's navigation pane is a
  drawer here: places, folders, smart folders, tags.
- Search with the window's query language. The bar holds the whole query, as the window's
  search field does.
- One asset at a time, swiped through: pictures with pinch and double-tap zoom, video and audio
  streamed with `Range`.
- Rating, tags (with suggestions from the library), folders, note, and the trash with Undo.
- Stays current: the window's changes arrive over server-sent events.
- Adds files to the library (ADR-0073 in Roost, REMOTE.md revision 1.1): the `+` button opens the
  system's file picker, for photos and videos or for anything, and Roost Remote is under **Share** in other apps. The
  files go to the folder on screen, one at a time, and keep going with the app closed, with a
  notification and **Cancel**. A file the library already has is said, with *Add to folder*.

- **Settings**, from the gear on the list of libraries: the theme, light, dark or as the system
  says.

Not yet: deleting for good, comments.

## Building

JDK 21 and the Android SDK (platform 36). See `docs/SETUP.md`.

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Licence

MIT © Digitalku. See `LICENSE`.
