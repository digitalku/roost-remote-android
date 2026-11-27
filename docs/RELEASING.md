# Releasing Roost Remote on Google Play

The app is published on Google Play by the owner's developer account. This file is what the
repository needs for that, and the answers Play Console asks for.

## 1. The upload key (once)

Play App Signing holds the key that signs what users install. The repository's builds are signed
with an **upload key**, which is the owner's and never in this repository (`.gitignore` refuses
`*.jks` and `*.keystore`). Create it once, outside the repository, and keep a backup of it and its
passwords. If it is lost, Play can reset it, but only through support.

```bash
keytool -genkeypair -v -keystore <outside the repository>/roost-remote-google-play-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 36500
```

A PKCS12 keystore, which `keytool` makes by default, has one password for the store and the key.

Then add to `~/.gradle/gradle.properties`, which is outside the repository:

```properties
roostRemoteUploadStoreFile=/absolute/path/to/roost-remote-google-play-upload.jks
roostRemoteUploadStorePassword=...
roostRemoteUploadKeyAlias=upload
roostRemoteUploadKeyPassword=...
```

Both passwords are the same one. A `#` after a value is part of the value in a properties file, so
write no comment on those lines. Without the four properties a release builds unsigned, which Play
refuses.

## 2. A release

1. Raise `versionCode` in `app/build.gradle.kts`, which Play requires for every upload, and
   `versionName` as the release warrants.
2. Build the bundle:

   ```bash
   ./gradlew testDebugUnitTest bundleRelease
   ```

   The bundle is `app/build/outputs/bundle/release/app-release.aab`.
3. Check that the bundle is signed with the upload key:

   ```bash
   keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab
   ```

4. In Play Console, go to **Testing → Internal testing → Create new release**, upload the bundle,
   write the release notes, and roll it out.
5. Tag the commit `v<versionName>` and push the tag.

The release build is minified by R8. Before a first upload, or after a dependency changes, install
a release build on a phone or emulator and try pairing, the grid, a picture, a video and adding a
file. A class R8 removed shows up only there.

## 3. Play Console: what to answer

**App content**

- *Privacy policy*: the URL of `PRIVACY.md` on GitHub, or a copy on roost.digitalku.com.
- *Ads*: no.
- *App access*: the app needs a Roost library shared from a computer. Give review a way in, or
  say that it cannot be reached without one. Internal testing is not reviewed. Closed and
  production tracks are.
- *Target audience*: 18 and over.
- *Data safety*: nothing reaches the developer. Everything the app sends goes to an address the
  user typed, which is their own computer (`PRIVACY.md`). This is the owner's declaration to make.
- *Foreground service*: `dataSync`, for sending files the user picked with the app closed. Play
  asks for a short video of it: pick a file with `+`, leave the app, and show the notification with
  its progress.
- *Government apps, financial features, health*: no.

**Store listing**: name *Roost Remote*, a short and a full description, the icon at 512×512, a
feature graphic at 1024×500, and at least two phone screenshots.

## 4. Internal testing

Internal testing reaches up to 100 testers by email, and a release is available to them within
minutes, without review. Add the testers under **Testing → Internal testing → Testers**, then send
them the opt-in link. They install from the Play Store on their phone with the same Google account.
