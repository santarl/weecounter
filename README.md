# weecounter

> **AI disclosure:** this app was written with AI-assisted coding (Claude by Anthropic). I direct the design, review the code and test it on my own device.

A tiny tap counter for AMOLED screens. Pure Java, no libraries, no network, tiny APK.

<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg" width="200">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg" width="200">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpg" width="200">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.jpg" width="200">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.jpg" width="200">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.jpg" width="200">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.jpg" width="200">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/8.jpg" width="200">
</p>

(yes the third screenshot is a feature, you can count with ur screen off ish on amoled displays)

## Use

- **Tap** anywhere: count up, short vibration.
- **Hold** anywhere: switch between the panel (number and buttons) and the counting view.
- **Panel:** `-1` bottom left, `HOLD TO RESET` top right, `settings` top left, `view` bottom right cycles the counting view.
- **Counting views:** blackout (pure black), dots, tally. When the screen is full, dots and tally buzz and keep going: new marks overwrite the oldest ones in the next colour.
- **Volume buttons** (optional, off by default): count with the volume keys, e.g. both add one, or up adds and down subtracts. Set in settings.
- **Vibration patterns** are plain milliseconds, alternating buzz and pause: `40 60 40` = buzz 40, pause 60, buzz 40. Empty = off.

The display drifts a few pixels every 15 seconds to protect AMOLED screens from burn-in (configurable, can be turned off).

Optional (off by default): turn on Do Not Disturb while the app is open, either "priority only" or "alarms only". The previous setting is restored when you leave. Needs Do Not Disturb access, which you grant in Android's settings.

## Build

```
./gradlew assembleRelease
```

CI builds are in `.github/workflows` (`test.yml` for a test APK, `release.yml` for tagged releases). Releases are signed with the author's key; the F-Droid build is signed by F-Droid, so the two cannot update each other.

## License

GPL-3.0, see [LICENSE](LICENSE).
