# Just Leave
## You can do it runnah

An Android 16+ app for the Motorola Razr: launch it from the small cover screen, flip the
phone open, and the bottom half of the screen becomes a control panel. Press and hold the big
asterisk button to show a checkmark in the top half. Let go to close.

The control panel has the asterisk button (right of centre, under your right thumb), three
smaller buttons arcing around its left side, and an on/off toggle in the upper left that
starts off every launch. The small buttons and toggle respond to touch but don't do anything
yet.

## How it works

| State     | What you see                                    | What moves it on                       |
|-----------|-------------------------------------------------|----------------------------------------|
| `ARMED`   | Cover screen: the app icon                      | Flip the phone open (or tap to preview) |
| `PLAYING` | Intro flash, then a spinning line fills a red disc | Press the asterisk button              |
| `HELD`    | Black pixel checkmark on highlighter green, top | Lift your thumb off the asterisk       |
| `CLOSED`  | The app finishes and leaves Recents             | n/a                                    |

**Flip-open detection** (`FlipShowActivity`) uses two signals and acts on whichever comes first:

1. Jetpack WindowManager reports a `FoldingFeature`. Only the main display has a hinge.
2. The window's `smallestScreenWidthDp` grows by more than 25%, which means it moved from
   the cover screen to the main screen.

If the app is launched while the phone is already open, the control panel appears right away.

## Code map

- `app/src/main/java/com/fir3chi3f/justleave/`
  - `ShowController.kt`: the armed → playing → held → closed state machine, plus a show
    clock that stops while you hold.
  - `FlipShowView.kt`: draws the cover screen, checkmark and control panel on a `Canvas`
    and turns touches into events.
  - `FlipShowActivity.kt`: flip detection, full-screen setup, and closing.
- `app/src/test/`: JVM unit tests for the color loop and state machine (pure Kotlin, no device
  needed).

## Build & install

You need Android Studio (or the Android SDK with platform 36) and JDK 25.

```sh
./gradlew :app:testDebugUnitTest     # unit tests
./gradlew :app:installDebug          # install on a phone with USB debugging enabled
./gradlew :app:assembleRelease       # signed release APK (needs the signing properties below)
```

### Release signing

Release builds are signed when these Gradle properties are set, usually in
`~/.gradle/gradle.properties` (never in the repo). Without them, `assembleRelease` makes an
unsigned APK that won't install.

```properties
justLeaveStoreFile=/path/to/just-leave-release.jks
justLeaveStorePassword=...
justLeaveKeyAlias=just-leave
justLeaveKeyPassword=...
```

Publishing a GitHub release runs `.github/workflows/build.yml`, which builds a signed APK and
attaches it to the release. It reads the keystore and password from the repository secrets
`JUST_LEAVE_KEYSTORE_BASE64` (the `.jks` file, base64-encoded) and
`JUST_LEAVE_KEYSTORE_PASSWORD`.

## Razr setup

1. **Let the app run on the cover screen:** Settings → *External display* → *Apps on
   external display*, then turn on **Just Leave**. (Motorola renames this menu between
   releases. Search Settings for "external display" if you can't find it.)
2. **Keep it going when you open the phone:** in the same area, set Just Leave to
   *continue on the main display when opened*. If this is off, opening the phone sends the
   app to the background and you'll land on the home screen instead of Just Leave.
3. On the cover screen, open Just Leave. You'll see the app icon. Flip it!
