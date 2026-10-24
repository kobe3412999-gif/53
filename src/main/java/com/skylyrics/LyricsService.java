package com.skylyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Downloads time-synced (LRC) lyrics from the free LRCLIB service. */
public final class LyricsService {
    public record Line(double start, double end, String text) { }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final Pattern LRC = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)]\\s*(.*)");

    private static volatile List<Line> lines = List.of();
    private static volatile String loadedKey = "";
    private static volatile String status = "";

    public static List<Line> lines() { return lines; }
    public static String status() { return status; }

    /** Called every tick; starts a background fetch whenever the song changes. */
    public static void tick() {
        MediaPoller.Track t = MediaPoller.track();
        String key = t == null ? "" : t.key();
        if (key.equals(loadedKey)) return;
        loadedKey = key;
        lines = List.of();
        if (t == null) { status = ""; return; }
        status = "Searching lyrics...";
        Thread th = new Thread(() -> fetch(t, key), "SkyLyrics-Fetch");
        th.setDaemon(true);
        th.start();
    }

    private static void fetch(MediaPoller.Track t, String key) {
        try {
            String title = cleanTitle(t.title());
            String artist = t.artist().split("[,&;]")[0].trim();
            String url = "https://lrclib.net/api/get?track_name=" + enc(title) + "&artist_name=" + enc(artist)
                    + (t.duration() > 0 ? "&duration=" + Math.round(t.duration()) : "");
            String synced = syncedFrom(get(url));
            if (synced == null) { // fallback: search
                String body = get("https://lrclib.net/api/search?q=" + enc(title + " " + artist));
                if (body != null) {
                    JsonElement el = JsonParser.parseString(body);
                    if (el.isJsonArray()) {
                        JsonArray arr = el.getAsJsonArray();
                        for (JsonElement e : arr) {
                            JsonObject o = e.getAsJsonObject();
                            if (o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull()) {
                                synced = o.get("syncedLyrics").getAsString();
                                break;
                            }
                        }
                    }
                }
            }
            if (!key.equals(loadedKey)) return; // song changed while fetching
            if (synced == null) { status = "No synced lyrics found"; return; }
            lines = parse(synced);
            status = lines.isEmpty() ? "No synced lyrics found" : "";
        } catch (Exception e) {
            SkyLyricsClient.LOG.warn("Lyrics fetch failed", e);
            if (key.equals(loadedKey)) status = "Lyrics unavailable";
        }
    }

    private static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("User-Agent", "SkyLyrics/1.0 (Minecraft Fabric mod)").GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return res.statusCode() == 200 ? res.body() : null;
    }

    private static String syncedFrom(String body) {
        if (body == null) return null;
        JsonElement el = JsonParser.parseString(body);
        if (!el.isJsonObject()) return null;
        JsonObject o = el.getAsJsonObject();
        return o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull() ? o.get("syncedLyrics").getAsString() : null;
    }

    static List<Line> parse(String lrc) {
        List<double[]> times = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            Matcher m = LRC.matcher(raw.trim());
            if (!m.matches()) continue;
            double s = Integer.parseInt(m.group(1)) * 60.0 + Double.parseDouble(m.group(2));
            times.add(new double[]{s});
            texts.add(m.group(3).trim());
        }
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            double start = times.get(i)[0];
            double end = i + 1 < texts.size() ? times.get(i + 1)[0] : start + 5;
            if (!texts.get(i).isEmpty()) out.add(new Line(start, Math.min(end, start + 12), texts.get(i)));
        }
        return out;
    }

    private static String cleanTitle(String t) {
        return t.replaceAll("(?i)\\s*[(\\[][^)\\]]*(feat|ft\\.|with|remaster|live|version|edit)[^)\\]]*[)\\]]", "")
                .replaceAll("(?i)\\s+-\\s+(remaster.*|\\d{4}.*|single version|radio edit)$", "").trim();
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    /** Index of the line active at the given time, or -1. */
    public static int indexAt(double pos) {
        List<Line> l = lines;
        int idx = -1;
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).start() <= pos) idx = i; else break;
        }
        if (idx >= 0 && pos > l.get(idx).end() + 0.5) return -1; // instrumental gap
        return idx;
    }
}
