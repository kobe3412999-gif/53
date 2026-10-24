# Sky Lyrics (Fabric, Minecraft 26.2)

Original mod inspired by the "lyrics in the sky" look: a now-playing pill at the top of the screen
(with the current line and a highlighted word), lyric words floating in the sky, a sky glow that pulses
with the music, and a settings screen (press **J**).

* Song info: Windows media controls (Spotify, YouTube in a browser, Apple Music, etc.) via PowerShell.
  Linux: install `playerctl`. macOS isn't supported yet.
* Lyrics: time-synced lyrics from lrclib.net (free, no key).
* Settings are saved to `config/skylyrics.json`.

## Build
Needs JDK 25.
1. Create a fresh project at https://fabricmc.net/develop/template/ for Minecraft 26.2 (this gives you the Gradle wrapper).
2. Copy `src/main/java/com/skylyrics`, `src/main/resources/fabric.mod.json` and `src/main/resources/assets` from here into it
   (or use this folder's `build.gradle`/`gradle.properties` and run `gradle wrapper --gradle-version 9.5.1` once).
3. `./gradlew build` -> jar in `build/libs/` -> put it in `.minecraft/mods` with Fabric API.

## If it doesn't compile
26.2 is very new and this was written without being able to compile it. The likely trouble spots are all
in `SkyLyricsClient.java` (HUD/keybind registration) and `LyricsHud.java` (the `GuiGraphics` calls).

## Get the jar without installing anything (GitHub)
1. Make a free GitHub account, create a new repository, and upload everything in this folder (including the hidden `.github` folder).
2. Open the **Actions** tab, wait for the "build" run to finish (a few minutes), click it.
3. Download **skylyrics-jar** from Artifacts, unzip it, and use the jar that does NOT end in `-sources`.
4. Put that jar and Fabric API in `.minecraft/mods`.
If the run fails, open it, copy the red error text and send it to Claude.
