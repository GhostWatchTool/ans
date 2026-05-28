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
- **Audio sequencing** — `NumberStationPlayer` builds a flat list of `Step`s
  (tone / silence / clip) per pass and runs them one at a time on the main
  thread. Tones advance after their fixed duration via `Handler.postDelayed`;
  audio clips advance on `MediaPlayer.OnCompletionListener`. A session-id
  guard discards callbacks from a flushed queue after `stop()`.
- **Voices** — pre-recorded `.ogg` files in `res/raw/` (digits 0–9, four
  time-of-day greetings, signoff). Two voice sets ship: `Fiona` (default,
  no prefix) and `Tessa` (`tessa_*` prefix, hidden). `NumberStationPlayer.voice`
  picks one; resolved at clip time via `Resources.getIdentifier`. No system
  TTS engine is required, so the app runs on GrapheneOS.
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

- **British (en-GB) voice by default** — Fiona, rendered by macOS `say` at
  `-r 165`, encoded to ~32 kbps mono Opus. Re-generate with
  `say -v <voice>` → `ffmpeg -ar 24000 -ac 1 -c:a libopus -b:a 32k` if you
  want a different voice. New voices go in `res/raw/` with a unique prefix
  and a corresponding `NumberStationPlayer.Voice` entry.
- **System theme follows the device.** Compose colors switch via
  `isSystemInDarkTheme()`; `values/themes.xml` + `values-night/themes.xml`
  handle window chrome and status-bar icon colour per mode.
- **Foreground service is required.** Audio playback in the background
  without a `mediaPlayback` foreground service gets reaped when the screen
  sleeps. Do not move playback back into the ViewModel.

## Module path

`dev.wntrmute.ans` — both `applicationId` and `namespace`.

## Releases

`.github/workflows/release.yml` triggers on `v*` tag push (or manual dispatch
against an existing tag) and builds the **unsigned** release APK on
ubuntu-latest, then attaches it to the GitHub release. The keystore stays off
CI (matching the `kbc` repo's policy); the maintainer signs the artifact
locally with `apksigner` before installing — see "Releases" in `README.md`.

CI passes `-PansVersion=<tag without v>` to Gradle so the APK's `versionName`
matches the tag. Local builds default to `1.0.0-dev` if the property isn't set.

## Not here yet

- No settings UI for the voice selection — `Tessa` clips ship in the APK
  but `NumberStationPlayer.voice` is hardcoded to `Fiona`. Wire a setting,
  flip the property.
- No settings UI for tuning the lead-in / preamble / signoff phrases (the
  greeting and goodbye are fixed bundled clips today; making them user-typed
  text would mean putting TTS back, or shipping more clip variants).
- No in-repo signing config (matches kbc's stance — local signing only).
