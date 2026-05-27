# Number Station

An Android app that reads out groups of five digits using the device's built-in
text-to-speech engine — a software numbers station.

## Features

- **Five-digit grouping.** Type digits and the message field automatically
  formats them into space-separated groups of five (`12345 67890 …`). Non-digit
  input is ignored; the caret stays put as you type.
- **Play / Stop.** One button starts and stops playback. Digits are spoken one
  at a time, with a short pause between groups, in a British English (en-GB)
  voice — the engine's en-GB default, which Google ships as female. (Android's
  TTS API exposes no gender field, so this is locale-based, not a hard request.)
- **Repeat.** A toggle reveals two modes: *loop until stopped*, or *repeat a set
  number of times* (with a stepper for the count).

## Build & run

Requires JDK 17 and the Android SDK (platform 35). `local.properties` points the
build at the SDK and is git-ignored.

```sh
./gradlew assembleDebug                      # build the APK
./gradlew test                               # run JVM unit tests
./gradlew installDebug                       # install on a connected device/emulator
```

The APK lands in `app/build/outputs/apk/debug/`.

## Layout

| Path | Purpose |
|------|---------|
| `NumberFormatter.kt` | Pure digit-grouping / caret logic (unit-tested) |
| `NumberStationPlayer.kt` | Wraps `android.speech.tts.TextToSpeech`; pacing and repeats |
| `NumberStationViewModel.kt` | Transport state; owns the player |
| `ui/NumberStationScreen.kt` | Compose UI |
| `ui/theme/Theme.kt` | Material 3 theme |
