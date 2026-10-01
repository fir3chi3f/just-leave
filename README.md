# Flip Flash

An Android 16+ app for the Motorola Razr: launch it from the small cover screen, flip the
phone open, and a CMY light show plays. It cycles through icons and colors on the
cyan → magenta → yellow wheel. Press and hold to freeze the frame. Let go to close.

## How it works

| State     | What you see                                    | What moves it on                       |
|-----------|-------------------------------------------------|----------------------------------------|
| `ARMED`   | Cover screen: a breathing CMY disc, "Flip open" | Flip the phone open (or tap to preview) |
| `PLAYING` | Full-screen show on the main display             | Put your thumb down                    |
| `HELD`    | The frame freezes, "Release to close"           | Lift your thumb                        |
| `CLOSED`  | The app finishes and leaves Recents             | n/a                                    |

**Flip-open detection** (`FlipShowActivity`) uses two signals and acts on whichever comes first:

1. Jetpack WindowManager reports a `FoldingFeature`. Only the main display has a hinge.
2. The window's `smallestScreenWidthDp` grows by more than 25%, which means it moved from
   the cover screen to the main screen.

If the app is launched while the phone is already open, the show starts right away.

## Code map

- `app/src/main/java/com/potentiallamp/flipflash/`
  - `ShowScript.kt`: **the animation script**. Maps show time to a frame: colors, icon,
    pulse, spin and orbit. Change `COLOR_LOOP_MS`, `BEAT_MS`, the spin speeds or the `Icon`
    list to change how the show feels.
  - `ShowController.kt`: the armed → playing → held → closed state machine, plus a show
    clock that stops while you hold.
  - `FlipShowView.kt`: draws the frames on a `Canvas` and turns touches into events.
  - `FlipShowActivity.kt`: flip detection, full-screen setup, and closing.
- `app/src/test/`: JVM unit tests for the script and state machine (pure Kotlin, no device
  needed).

## Build & install

You need Android Studio (or the Android SDK with platform 36) and JDK 17+.

```sh
./gradlew :app:testDebugUnitTest     # unit tests
./gradlew :app:installDebug          # install on a phone with USB debugging enabled
```

## Razr setup

1. **Let the app run on the cover screen:** Settings → *External display* → *Apps on
   external display*, then turn on **Flip Flash**. (Motorola renames this menu between
   releases. Search Settings for "external display" if you can't find it.)
2. **Keep it going when you open the phone:** in the same area, set Flip Flash to
   *continue on the main display when opened*. If this is off, opening the phone sends the
   app to the background and you'll land on the home screen instead of the show.
3. On the cover screen, open Flip Flash. You'll see "Flip open". Flip it!
