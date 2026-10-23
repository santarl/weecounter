# weecounter

> **AI disclosure:** this app was written with AI-assisted coding (Claude by Anthropic). I direct the design, review the code and test it on my own device.

A tiny tap counter for AMOLED screens. Pure Java, no libraries, tiny APK. The only network use is the update check you start yourself in the settings.

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
- **Panel:** `-1` bottom left, `adjust` top right (opens the adjust sheet: set the count and the target, reset, undo), `menu` top left (settings, profiles, edit layout, reset layout), `view` bottom right cycles the counting view. The `-1` and `view` buttons reach down to the screen edge.
- **Focus:** the **focus** button on the panel opens a sheet with the selector of the counting view and a slider: hold the knob and push it to the end to go into focus; hold anywhere to leave it. (A hold on the panel does not go into focus any more.)
- **Counting views:** blackout (pure black), dots, tally, game (a snake that plays itself: every tap drops an apple, it eats it and grows a block). Dots and tally take the number's colour: they fill one mark per tap, then drain one mark per tap (earliest first) until the next lap (every 100 by default), which changes the colour, buzzes and starts over. Counting never stops; an optional "count reached" target buzzes once when you hit it.
- **Volume buttons** (optional, off by default): count with the volume keys, e.g. both add one, or up adds and down subtracts. Set in settings.
- **Vibration patterns** are plain milliseconds, alternating buzz and pause: `40 60 40` = buzz 40, pause 60, buzz 40. Empty = off.

The display drifts a few pixels every 15 seconds to protect AMOLED screens from burn-in (configurable, can be turned off).

Optional (off by default): turn on Do Not Disturb while the app is open, either "priority only" or "alarms only". The previous setting is restored when you leave. Needs Do Not Disturb access, which you grant in Android's settings.

## Install and updates

Get the APK from the [latest release](https://github.com/santarl/weecounter/releases/latest), or let [Obtainium](https://obtainium.imranr.dev) keep it updated: [add weecounter to Obtainium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/santarl/weecounter) (or paste `obtainium://add/https://github.com/santarl/weecounter` into the browser of that phone). Settings has an About section with the version, this project's link and a manual "check for update" that opens the APK download of a newer release in the browser. Test builds compare the latest commit on `main` with the one they were built from.

## Layout editor

Menu, then **Edit layout**: tap an element (menu, adjust, -1, view, dhikr text, slide title, counter, footer, Do Not Disturb warning) to select it and show its bounding box. Drag inside to move it, drag an edge or a corner to resize it, or pinch to scale it. Text follows the height of its box. The layout is saved as you go. The check button (or back) leaves the editor; hold the undo button to reset the layout. **Reset layout** in the settings does the same.

## Dhikr profiles

Optional (settings, "Dhikr profile"). A profile is a UTF-8 text file, one file per profile, any language. A line like `---33` starts a slide that is counted 33 times (`0` skips it, `1` shows it once). The lines below it, up to the next `---N` line (or a bare `---`), are shown above the number.

```
---33
# Subhanallah
[70]Arabic text
[30]Subhanallah
---33
# Three views
Arabic | Transliteration | Meaning
a line without a bar is in every view
---1
# The end
[10]
[100]the end
```

- The text of a view is one block, centred in the dhikr rectangle. `[70]` in front of a line gives it a relative text size (the biggest line gets the standard size, `[70]` and `[30]` make a 70:30 ratio). `[_]` (or `[*]`) takes whatever is left of 100, `[10]` alone on a line is a gap, `[]` hides a line. Lines without a prefix get the average of the numbered ones (equal if none has one). Blank lines are dropped.
- Hard line breaks are kept while the block still gets a text size close to what it would get with the lines run together (within 15%). Otherwise the lines are joined with the glyph `↵` and the text just wraps.
- `|` writes alternative views of a line. A slide with `A | B | C` has three views: view 1 shows A, view 2 shows B, view 3 shows C, while lines without `|` stay on screen in every view.
- The number shows `17/33`, with `slide 2 of 4` under it, and a row of dots above the slide title shows which view of the slide you are on (they appear for a moment after a slide or view change, then fade out, and only on slides that have more than one view). A finished slide gives the lap buzz (the tap buzz if its count is 1); the last tap gives the "count reached" buzz and a completed screen with confetti.
- Gestures work on the counting screen only: not on the focus sheet, and not in focus mode (where a tap counts and a hold leaves).
- Gestures, like reels: swipe **up** to pull in the next slide, **down** to go back to the previous one (the counts of every slide are kept, so you can look at another slide and come back to 5/10; swiping up past the last slide completes the profile), **left** / **right** to switch between the views of a slide.
- Pinch to resize the dhikr text of the current slide and view. The size is saved per slide and view and only forgotten by "reset to defaults" in the settings. Text is as big as fits the rectangle, at most 34sp, and a pinch never makes it bigger than that. When the text fills its rectangle a small expand button appears at the bottom right corner of it: it opens a reading mode with the text over the whole screen. There one finger scrolls (a thin bar shows how far), two fingers pinch (saved per slide and view, apart from the normal size), a swipe left or right shows the other views of the slide, the three buttons at the bottom go to the previous slide, count (it shows the count; a tap on the text does not count here) and go to the next slide, and the glyph at the top right, or Back, leaves it. The slide title is its own element and is never scaled by pinching (resize it in the layout editor).
- A line `# Title` inside a slide is the slide title: it is shown in its own element and in the slide list (menu, **Goto**, lists the slides to jump to; its search box finds slides by title first, then by their text, and forgives a typo or two). Without a title, the start of the slide's first line is used.
- A slide that is counted once shows a ring instead of `0/1` (filled once it is done). When a slide is finished the next unfinished one shows up.
- Profiles are `.dhikr` files. Share one to weecounter from any app (for example WhatsApp's share sheet) to import it without opening the app first; pasted text works too if its first line is `# Name`. Tapping a `.dhikr` file in WhatsApp lists weecounter under "Open with" too (WhatsApp calls the file type BIN); files that are not profiles are ignored.
- **Simple mode / profile mode** (menu, shown when a profile is selected): simple mode is the plain counter and is where the app starts; profile mode uses the selected profile. Choosing a profile turns profile mode on, and a profile shortcut always opens in profile mode.
- **Reminders** (menu, **Reminders**): a notification at a time of day, on chosen days or daily, for a profile or for the plain counter (a profile or the plain counter can have several). "Keep until done" keeps it in the notification bar, and brings it back if it is swiped away, until the profile is finished (for the plain counter: until a lap or the goal is done). Edit, copy, alert and delete (hold the cross) are on every entry; every reminder has a notification channel of its own, so "alert" opens its sound, vibration and importance in the phone's settings. Alarms are set again after a reboot, an update and a change of the clock or time zone. The phone may ask to allow notifications, and exact alarms for on-the-minute reminders.
- **Several profiles at once:** each profile keeps its own counts, so you can switch between them freely. On the profile screen, "shortcut" asks for a few characters for its icon (a letter, an emoji, any glyph or your initials; it is drawn in the colours of the profile's theme) and puts a home screen shortcut for the profile; it opens that profile in a window of its own (a separate entry in the recent apps, named after the profile), next to the normal window. Opening the same shortcut again just brings its window back. If two windows have the same profile open they share its counts: each one takes over what the other counted when you come back to it.
- **Repeats** (menu, **Goto**, then ADVANCED, with a profile selected): one repeat count for all the slides of that profile, or, with "individual repeats", one for each slide; kept per profile. RESET goes back to the counts in the profile.
- The view dots sit just above the slide title, centred on it (move the title in the layout editor and they follow).
- **Themes:** a palette (Midnight, Ocean, Rose, Sand, Forest, Violet, Sunset, Mint, Teal, Slate, Coffee, Lime, Graphite, Orchid; the light mode of Midnight is called myeyes) in dark, light, vibrant (a light mode with a strongly coloured background), neon (a dark mode with glowing colours) or auto (follows the phone), colours the whole app: the counting screen, settings, profiles, browse and the dialogs. Settings > Theme sets the default (tap: next palette, hold: dark / light / auto); a profile can have its own: menu, **Theme** opens a strip over the live screen. Tap a palette, or swipe the strip left or right, to try it; it applies at once to the profile (or to the default when no profile is selected). dark / light / auto switch the mode, "use default" gives the profile the default theme back and "make default" makes the current theme the default. Every colour is worked out from the palette's hue so that it reads on its background (at least 4.6:1 contrast), and so are the colours of the laps (the counter changes colour every lap); the blackout view always stays black.
- **Export** (profile screen, "export"): Share sends the profile as text through the share dialog (this app imports shared text again), Save as file writes a `.dhikr` file.
- **Header lines** before the first slide are optional: `@id` (unique short name), `@version` (a whole number), `@author` and `@desc` (a description; repeat the line for more). The description pops up once when the profile is first opened; hold a profile on the profile screen to read it again.
- **Browse profiles** (profile screen, "BROWSE PROFILES") lists the profiles of [santarl/dhikr-profile-repo](https://github.com/santarl/dhikr-profile-repo) with author, version and description, and downloads or updates them (your counts are kept). It fetches a single small `index.json` (cached, only re-sent when it changed), once a week by itself (settings, "Check for new profiles weekly", on by default) or by hand with CHECK NOW. A profile file is only fetched when you tap DOWNLOAD / UPDATE. The only other network use is the manual update check.
- **Adjust** (the button at the top right; a hold on it opens the same sheet, nothing is reset by a hold): **Count** and **Target** can be typed in (in a profile they are those of the current slide, and the target is saved like a repeat; in the plain counter the target is the goal of the settings, "Count reached at"). Setting a count does not move the slide on by itself. **Reset this slide** (or **Reset to 0**) acts at once with a tap, and **Undo** takes the last reset back; the sheet stays open, so a slip can be undone. Holding either reset shows a red slider: push the knob from the right to the left to do it. **Hold to reset the whole profile** only works that way (also undoable until the sheet is closed). The menu, the adjust and focus sheets and every dialog are the same kind of card, in the middle of the screen. A count or a target can be any whole number up to 999,999,999.
- **Help** (menu): a short tour of the screen, the gestures, the focus modes, the floating counter, shortcuts and where to report a bug. It shows once on the first start of a new install. Settings has a link to the issues page of the project.
- **Floating counter** (menu): a round bubble over other apps that counts with a tap, with the same buzz, laps and goal as in the app. In simple mode it shows and counts the plain counter; in profile mode it shows and counts the current slide of the profile of the window whose menu you started it from (when it comes up by itself, the window you left last) and, when that slide is full, moves on to the next one with the lap buzz (a completed profile shows a check mark and ignores taps: start it over in the app). It shows the count and the target (21/33; in the plain counter the goal, if there is one) and takes the theme of that profile. While the app is not on screen, a comic speech bubble with the new slide (all of its text, smaller if it is long) fades in slowly beside the bubble whenever the slide changes, stays a while, and fades out slowly. In a profile, drop the bubble on the i next to the X to see the current slide in the same kind of bubble: it goes back where it was and the bubble stays until you touch anything. It needs the "Display over other apps" permission (the menu item opens that setting the first time) and runs as a service with a small notification, which has a Close button too. Drag the bubble where you want it (it stays where you let go, and remembers its place); fling it at an edge to make it stick there; drop it on the X that appears at the bottom to close it (one buzz when it gets over the X, a different one when it closes). Its size is a setting, in pixels (0 = automatic). It stays until you close it, and a tap on its notification opens the window of what it counts. The app and the bubble always show the same counts. It also comes up by itself when you minimize the app (a setting, on by default: "Float the counter when the app is minimized"; it needs the permission, which the menu item asks for the first time) and goes away when you come back; closing the app with Back does not bring it up, and one started from the menu stays.
- Counts are never cleared by finishing a slide or the profile. You can go back to a finished slide and keep counting past its target (shown as `40/33`); a slide only moves on by itself the first time it reaches its target.
- On the completed screen, tap 3 to 5 times (a random number; the toast just says to keep tapping): every tap buzzes and shakes the emoji, and the last one starts the profile over. `-1` goes back into the last slide and "Hold to reset the whole profile" in the adjust sheet also starts over. Import, export and delete profiles on the profile screen (hold the red x to delete).

## Build

```
./gradlew assembleRelease
```

CI builds are in `.github/workflows` (`test.yml` for a test APK, `release.yml` for tagged releases). Releases are signed with the author's key, so the APKs of different releases update each other.

## License

GPL-3.0, see [LICENSE](LICENSE).
