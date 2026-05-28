# Number Station — AI-assisted development context

A Kotlin / Jetpack Compose Android app that reads out groups of five digits via
the system text-to-speech engine. Designed to be fed into a **VOX-keyed cheap
handheld radio**, which constrains the audio layout — see "Radio / VOX
behaviour" in `README.md`.

## Common commands

```sh
./gradlew assembleDebug          # build APK
./gradlew test                   # unit tests (JVM)
./gradlew lintDebug              # Android lint
./gradlew installDebug           # install on connected device/emulator
```

## Architecture

- **UI** — single Compose screen (`ui/NumberStationScreen.kt`) backed by
  `NumberStationViewModel`. The ViewModel holds repeat settings (toggle / loop /
  count); the message text lives in the composable via `rememberSaveable`.
- **Playback** — the ViewModel issues play/stop intents to
  `NumberStationService`, a foreground `mediaPlayback` service that owns the
  TTS engine (`NumberStationPlayer`), audio focus, a partial wake lock, and
  the ongoing notification. State flows back through `PlaybackState`
  (`StateFlow<Boolean>`), so playback survives Activity destruction
  (rotation, backgrounding) and screen-off.
- **Audio sequencing** — `NumberStationPlayer` queues utterances on the TTS
  engine and triggers `TonePlayer` (a parallel `AudioTrack`) from
  utterance-onStart callbacks, so sine tones play in sequence with voice
  utterances without modifying the speech audio. Tones and TTS mix at the
  system audio mixer.
- **Pure logic** — `NumberFormatter`, `RepeatPlan`, and `Greeting` have no
  Android imports and are JVM-unit-tested.

## Radio / VOX is the design driver

The audio cadence (700 ms lead tone, 200 ms tone-to-voice gap, 400 ms
post-greeting silence, 500 / 700 ms end tones, 800 / 432 Hz split,
half-amplitude tones, greeting-only-once) is **tuned against cheap handheld
radios** whose VOX has slow attack and short hang. If you change a timing
constant in `NumberStationPlayer.kt` or `TonePlayer.kt`, expect it to need
verification on a real radio — unit tests don't catch VOX-driven regressions.

## Key conventions

- **British (en-GB) voice by default.** Android's TTS API has no gender field,
  so we set the locale and rely on the en-GB default being female (which
  Google ships).
- **System theme follows the device.** Compose colors switch via
  `isSystemInDarkTheme()`; `values/themes.xml` + `values-night/themes.xml`
  handle window chrome and status-bar icon colour per mode.
- **Foreground service is required.** TTS in the background without a
  `mediaPlayback` foreground service gets reaped when the screen sleeps. Do
  not move playback back into the ViewModel.

## Module path

`dev.wntrmute.ans` — both `applicationId` and `namespace`.

## Not here yet

- No settings UI for tuning lead-in / preamble / signoff text (the greeting
  and goodbye are hardcoded constants in `NumberStationPlayer.kt` — a text-box
  hook is expected later).
- No release signing config; debug builds only.
- No CI.
