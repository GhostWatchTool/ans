  # Number Station

An Android app that reads out groups of five digits using the device's built-in
text-to-speech engine — a software numbers station, designed to be fed into a
**VOX-keyed cheap handheld radio**.

## Features

- **Five-digit grouping.** Type digits and the message field auto-formats them
  into space-separated groups of five (`12345 67890 …`). Non-digit input is
  ignored; the caret stays put as you type.
- **Play / Stop.** One button starts and stops playback. Digits are read from
  pre-recorded en-GB audio clips bundled in the APK (~124 KB of Opus). Default
  voice is **Fiona** (macOS-rendered, Enhanced quality); **Tessa** is also
  shipped as a hidden alt for a future settings toggle. No system text-to-speech
  engine is required, so the app works on de-Googled Android (e.g. GrapheneOS).
- **Repeat.** A toggle reveals two modes: *loop until stopped*, or *repeat a
  fixed number of times* (with a `−` / `+` stepper).
- **Background-robust playback.** Reading runs in a foreground `mediaPlayback`
  service with an ongoing notification, holds audio focus (pauses other media),
  and holds a partial wake lock so a long loop keeps reading with the screen
  off and through Activity recreation (rotation, etc.).
- **Monochrome theme** ported from the GWTBC app, following the system
  light / dark setting.

## Radio / VOX behaviour

The app is intended to drive **cheap handheld radios** whose VOX (voice-operated
transmit) has noticeably slower attack and shorter hang than higher-end
transceivers. Plain TTS into VOX would clip the first syllable of every
utterance and re-clip after each pause. The playback sequence is tuned around
that:

- **Lead-in tone before every pass** — 800 Hz / 700 ms — keys VOX before any
  content arrives. A 200 ms gap (kept inside typical VOX hang) follows the
  tone before the voice.
- **Spoken greeting on the first pass only** — "Good morning" / "Good
  afternoon" / "Good evening" / "Good night", chosen by local time-of-day.
  Subsequent repeats jump from the lead tone straight to digits.
- **End-of-repeat tone** — 432 Hz / 500 ms — between passes, followed by a
  short pause; the next pass's lead tone re-keys VOX.
- **Final signoff** — 432 Hz / **700 ms** tone (longer than the between-repeats
  tone — the cheap handhelds need more time on the carrier to avoid clipping
  the start of the signoff), then "Good bye for now". Loop-until-stopped mode
  has no signoff, since the user determines the end.

Tones play at -12 dBFS, half the amplitude of typical TTS peaks, so a pure sine
doesn't dominate the speech. Tones and TTS share a single audio-focus session
and mix at the system audio mixer.

## Build & run

Requires JDK 17 and the Android SDK (platform 35). `local.properties` points
the build at the SDK and is git-ignored.

```sh
./gradlew assembleDebug          # build the APK
./gradlew test                   # run JVM unit tests
./gradlew installDebug           # install on a connected device/emulator
./gradlew lintDebug              # Android lint
```

The APK lands in `app/build/outputs/apk/debug/`.

## Releases

Pushing a `v*` tag (e.g. `v1.0.0`) triggers `.github/workflows/release.yml`,
which builds the **unsigned** release APK on a GitHub-hosted runner and
attaches it to the GitHub release as `ans-<version>-release-unsigned.apk`. The
workflow passes `-PansVersion=<tag without v>` to Gradle so the APK's
`versionName` matches the tag.

Signing is deliberately kept off CI — the keystore stays on the maintainer's
machine (same policy as the `kbc` repo). To install the artifact, sign it
locally first:

```sh
# zipalign first (paranoid; assembleRelease already aligns, but apksigner needs
# it before signing v2+):
$ANDROID_HOME/build-tools/35.0.0/zipalign -v -p 4 \
    ans-1.0.0-release-unsigned.apk ans-1.0.0-aligned.apk

# Then sign with your local keystore:
$ANDROID_HOME/build-tools/35.0.0/apksigner sign \
    --ks ~/.ans/release.jks \
    --out ans-1.0.0.apk \
    ans-1.0.0-aligned.apk

adb install -r ans-1.0.0.apk
```

To cut a release:

```sh
git tag v1.2.3
git push origin v1.2.3
# CI runs, creates the GitHub release (if missing), attaches the APK.
```

## Layout

| Path | Purpose |
|------|---------|
| `NumberFormatter.kt` | Pure digit-grouping / caret logic (unit-tested) |
| `RepeatPlan.kt` | Pure repeat / pass-count accounting (unit-tested) |
| `Greeting.kt` | Time-of-day greeting selection (unit-tested) |
| `TonePlayer.kt` | Sine-wave tone generation + playback via `AudioTrack` |
| `NumberStationPlayer.kt` | Step sequencer for greeting / digits / tones / signoff; plays clips via `MediaPlayer`, tones via `TonePlayer` |
| `res/raw/*.ogg` | Pre-recorded voice clips — Fiona by default, `tessa_*.ogg` as a hidden alt |
| `NumberStationService.kt` | Foreground `mediaPlayback` service; owns the player, audio focus, wake lock, notification |
| `PlaybackState.kt` | Shared `StateFlow` bridging service ↔ ViewModel |
| `NumberStationViewModel.kt` | Transport + repeat-settings state; sends play/stop intents to the service |
| `ui/NumberStationScreen.kt` | Compose UI |
| `ui/theme/Theme.kt` | Monochrome Material 3 theme (ported from GWTBC), follows system dark mode |
