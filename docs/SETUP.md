# Setup

On the owner's laptop (Ubuntu 26.04) nothing comes from Android Studio. Everything is the command
line.

## Once

```bash
sudo apt install openjdk-21-jdk-headless
```

The Android command-line tools are unpacked at `~/Android/Sdk/cmdline-tools/latest`, from Google's
`commandlinetools-linux-*_latest.zip`. From version 23 `sdkmanager` is deprecated in favour of the
`android` program beside it, which no longer asks for the licences to be accepted separately. Run
it with `--no-metrics`, or it reports its usage to Google:

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH
android --no-metrics --sdk=$ANDROID_HOME sdk install platform-tools platforms/android-37.2 \
    build-tools/37.0.0 emulator system-images/android-36/google_apis/x86_64
avdmanager create avd -n roost -k "system-images;android-36;google_apis;x86_64" -d pixel_8
```

The app compiles against API 37.2 because Compose and Coil require it, and targets API 36.
`local.properties` holds `sdk.dir=/home/orion/Android/Sdk` and is not committed.

## Every day

```bash
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest             # the protocol tests, on the JVM
emulator -avd roost -no-metrics &      # KVM is there, so it is quick
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Trying it against a library

The emulator reaches the computer it runs on at `10.0.2.2`, which is a private address, so the app
talks to it. In the roost repository, `node scripts/serve-remote-sample.mjs` opens a sample library
in a window and shares it: password `roost-remote`. Or, in Roost, open a library that has a
password and tick **Share on this network…**. In the app, type `10.0.2.2:7387`.

`adb shell input` and `adb exec-out screencap -p` drive the emulator from a terminal: type into a
field only after tapping it, or the text goes nowhere.

A real phone types one of the addresses the dialog lists.
