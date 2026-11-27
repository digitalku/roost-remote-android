# TODO

## Next

- [x] Build and run on the emulator against a shared library (2026-11-26): pairing, a wrong
      password, the grid, the drawer, a folder, search, the viewer and a swipe, a rating, tags
      with suggestions, an edit from another client arriving live, trash and Undo.
- [x] Adding files (ADR-0073, 2026-11-27), tried on the emulator against a window: the Photo
      Picker into a folder, the file picker, a share from Google Photos, a duplicate with
      *Add to folder*, the notification. Found and fixed there: every batch after the first went
      to the first batch's folder (WorkManager's input merging), and a shared file was unreadable
      by the time the work ran.
- [x] The Photo Picker gives no file name (`24.jpg`): *Photos and videos* opens the system's file
      picker instead, filtered to pictures and videos, which keeps names (the owner's choice,
      2026-11-27).
- [x] Settings, with the theme: follow the system, light or dark (2026-11-27), tried on the
      emulator with the system in both modes.
- [ ] A large video over real Wi-Fi: the notification, Cancel part-way, and the screen locked.
- [ ] Video and audio on a real file: playback, and whether the pager still swipes over a
      `PlayerView`, which takes the touch.
- [x] Seeking a video (2026-11-27, the owner's report from a phone): the pager took the drag on
      `PlayerView`'s seek bar. A transport drawn in Compose, tried on the emulator with a minute of
      `testsrc`: dragged to 0:43, and a swipe over the picture still moved to the next file.
- [x] A tag or folder chip goes there or comes off, through a menu; tags are picked from a
      searchable list or made; the folder picker is searchable; the drawer finds folders and tags;
      folders are drawn in the grid, inside a folder and when a search names them (2026-11-27).
      Tried on the emulator. Found there: a grid key that was not a `Bundle` type crashed the app
      the moment a tile was drawn, the drawer's field raised the keyboard at every open, and the
      search field put the caret before a folder's query.
- [ ] Pinch zoom on a real photograph, and a HEIC or RAW through `/bytes/render/`.
- [ ] A real phone over Wi-Fi and over Tailscale.
- [ ] The library's remembered order (`settings:read`), instead of the default.
- [ ] Comments in the detail sheet (`comment:add`, `comment:set`, `comment:remove`).
- [ ] Deleting for good, with RR-10's confirmation and preview.
- [x] Release signing and an `.aab` for Google Play internal testing (2026-11-27): the upload key
  is read from `~/.gradle/gradle.properties`, and the minified release, signed with the debug key,
  was tried on the emulator: pairing again after a 401, the grid, a picture, the drawer, and a
  file added to a folder. `docs/RELEASING.md`, `PRIVACY.md`.
- [ ] The first upload to internal testing: the owner's upload key, the store listing, the
  privacy policy's URL, and the foreground service's video (`docs/RELEASING.md` §3).
- [x] A foldable opened or closed, or a window resized, kept the app where it was (2026-11-27):
  `smallestScreenSize` and `density` were missing from `configChanges`, so the activity was
  recreated and the app went back to the list. Tried on the emulator at 2208×1840 and back.
- [x] An adaptive launcher icon (2026-11-27): the gradient behind, the bird in front with the wing
  and eye as holes, and the same bird as the monochrome layer; tried in the emulator's drawer.
  `play/icon-512.png` is the square Play Store icon, rendered from `play/icon.svg`.

## The window's features, still to bring

The owner asked on 2026-11-27 for as much of the window as a phone can carry. In rough order:

- [ ] Selecting several files in the grid, and tagging, filing, rating or trashing them together.
- [x] The filter bar: tags (Any or All), rating and type over the grid (ADR-0035), 2026-11-27.
      Tried on the emulator: two tags gave 18 under Any and 2 under All, the query read
      `tag:keluarga tag:pantai`, the ticks stood when a folder was opened, MP4 alone gave the video,
      and Clear gave back all 61.
- [ ] The library's own order and the sort menu (`settings:read`, ADR-0039).
- [ ] Show subfolder contents, with the *In subfolders* divider (ADR-0036, ADR-0059).
- [ ] Making, renaming, moving and deleting folders (ADR-0033, ADR-0067).
- [ ] The tags page: groups, rename, merge and delete (ADR-0061).
- [ ] Comments in the detail sheet, at a moment of a video too (ADR-0023).
- [ ] Deleting for good, with RR-10's confirmation and preview.
- [ ] Markdown and text files read in the viewer (`asset:readText`, ADR-0070).
