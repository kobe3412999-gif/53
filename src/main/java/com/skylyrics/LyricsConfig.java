package com.skylyrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Simple JSON config stored in config/skylyrics.json */
public final class LyricsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skylyrics.json");
    private static LyricsConfig instance;

    public boolean showPill = true;       // top "now playing" pill
    public boolean pillLyrics = true;     // show the current lyric line inside the pill
    public boolean skyLyrics = true;      // floating words in the sky
    public boolean glow = true;           // glow behind the sky words
    public float size = 1.0f;             // sky text size multiplier
    public float offset = 0.0f;           // lyric sync offset in seconds (+ = lyrics later)
    public int textColor = 0xE6DAFF;      // sky word colour (RGB)
    public int accent = 0x7B5CFF;         // pill / highlight / glow colour (RGB)
    public boolean bold = false;          // bold sky words
    public float opacity = 0.95f;         // sky word opacity
    public float wordSpacing = 0.6f;      // gap between words (1 = one normal space)
    public float lineSpacing = 1.0f;      // gap between rows of words
    public boolean followScreen = false;  // lyrics are anchored in the world, like the reference video
    public float height = 0.12f;          // vertical offset from screen centre
    public float horizontal = 0.0f;       // horizontal offset from screen centre
    public int skyPreset = 0;             // selected visual preset (also applies its palette)
    public float tilt = 0.0f;             // max random tilt in degrees (0 = perfectly straight)

    public static LyricsConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) instance = GSON.fromJson(Files.readString(FILE), LyricsConfig.class);
        } catch (Exception e) {
            SkyLyricsClient.LOG.warn("Could not read config, using defaults", e);
        }
        if (instance == null) instance = new LyricsConfig();
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(get()));
        } catch (Exception e) {
            SkyLyricsClient.LOG.warn("Could not save config", e);
        }
    }
}
