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
- [ ] An adaptive launcher icon.
