package com.skylyrics;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads "what is playing" from the operating system's media controls.
 *  - Windows: System Media Transport Controls (Spotify, browsers, Apple Music, etc.) through PowerShell
 *  - Linux:   MPRIS through `playerctl` (must be installed)
 */
public final class MediaPoller {
    public record Track(String title, String artist, double duration) {
        public String key() { return title + "\u0000" + artist; }
    }

    private static volatile Track track;
    private static volatile double basePos;
    private static volatile long baseNanos;
    private static volatile boolean playing;
    private static volatile boolean started;
    private static final Path CMD_FILE = Path.of(System.getProperty("java.io.tmpdir"), "skylyrics-cmd.txt");

    /** Control the player: "previous", "toggle" (play/pause) or "next". */
    public static void command(String cmd) {
        try {
            if (cmd.equals("toggle")) { // update the UI instantly instead of waiting for the next poll
                double p = position();
                basePos = p;
                baseNanos = System.nanoTime();
                playing = !playing;
            }
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                Files.writeString(CMD_FILE, cmd, StandardCharsets.UTF_8);
            } else {
                String arg = cmd.equals("toggle") ? "play-pause" : cmd;
                new ProcessBuilder("playerctl", arg).start();
            }
        } catch (Exception e) {
            SkyLyricsClient.LOG.warn("Could not send media command", e);
        }
    }

    public static Track track() { return track; }
    public static boolean playing() { return playing; }

    /** Current song position in seconds, smoothly extrapolated between polls. */
    public static double position() {
        double p = basePos;
        if (playing) p += (System.nanoTime() - baseNanos) / 1e9;
        Track t = track;
        if (t != null && t.duration() > 0) p = Math.min(p, t.duration());
        return Math.max(0, p);
    }

    private static void update(String title, String artist, double pos, double dur, boolean isPlaying) {
        if (title == null || title.isBlank()) { track = null; playing = false; return; }
        Track cur = track;
        Track next = new Track(title.trim(), artist == null ? "" : artist.trim(), dur);
        if (cur == null || !cur.key().equals(next.key()) || Math.abs(cur.duration() - dur) > 0.5) track = next;
        // blend towards the reported position instead of snapping, so lyrics don't jitter
        double current = position();
        boolean sameSong = cur != null && cur.key().equals(next.key());
        if (sameSong && playing && isPlaying && Math.abs(pos - current) < 1.0) pos = current + (pos - current) * 0.2;
        basePos = pos;
        baseNanos = System.nanoTime();
        playing = isPlaying;
    }

    public static synchronized void start() {
        if (started) return;
        started = true;
        String os = System.getProperty("os.name", "").toLowerCase();
        Thread t = new Thread(os.contains("win") ? MediaPoller::runWindows : MediaPoller::runLinux, "SkyLyrics-Media");
        t.setDaemon(true);
        t.start();
    }

    // ---------------------------------------------------------------- Windows

    private static final String PS_SCRIPT = """
        param([string]$CmdFile)
        [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
        Add-Type -AssemblyName System.Runtime.WindowsRuntime
        $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
        function Await($op, $type) { $m = $asTask.MakeGenericMethod($type); $t = $m.Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }
        $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
        $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime]
        $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
        while ($true) {
          try {
            if ($CmdFile -and (Test-Path $CmdFile)) {
              $cmd = (Get-Content $CmdFile -Raw).Trim()
              Remove-Item $CmdFile -ErrorAction SilentlyContinue
              $cs = $mgr.GetCurrentSession()
              if ($cs -ne $null) {
                if ($cmd -eq 'next') { $null = Await ($cs.TrySkipNextAsync()) ([bool]) }
                elseif ($cmd -eq 'previous') { $null = Await ($cs.TrySkipPreviousAsync()) ([bool]) }
                elseif ($cmd -eq 'toggle') { $null = Await ($cs.TryTogglePlayPauseAsync()) ([bool]) }
              }
            }
            $s = $mgr.GetCurrentSession()
            if ($s -ne $null) {
              $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
              $tl = $s.GetTimelineProperties()
              $playing = ([int]$s.GetPlaybackInfo().PlaybackStatus -eq 4)
              $pos = $tl.Position.TotalSeconds
              if ($playing) { $pos += ((Get-Date).ToUniversalTime() - $tl.LastUpdatedTime.UtcDateTime).TotalSeconds }
              $dur = $tl.EndTime.TotalSeconds - $tl.StartTime.TotalSeconds
              $line = ($p.Title -replace "`t", ' ') + "`t" + ($p.Artist -replace "`t", ' ') + "`t" + [math]::Round($pos, 2) + "`t" + [math]::Round($dur, 2) + "`t" + [int]$playing
              [Console]::Out.WriteLine($line)
            } else { [Console]::Out.WriteLine("`t`t0`t0`t0") }
          } catch { [Console]::Out.WriteLine("`t`t0`t0`t0") }
          [Console]::Out.Flush()
          Start-Sleep -Milliseconds 400
        }
        """;

    private static void runWindows() {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                try { Files.deleteIfExists(CMD_FILE); } catch (Exception ignored) { }
                Path script = Files.createTempFile("skylyrics-media", ".ps1");
                Files.writeString(script, PS_SCRIPT, StandardCharsets.UTF_8);
                script.toFile().deleteOnExit();
                Process proc = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                        "-ExecutionPolicy", "Bypass", "-File", script.toString(), "-CmdFile", CMD_FILE.toString())
                        .redirectErrorStream(false).start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) parseLine(line, 1.0);
                }
                proc.destroy();
            } catch (Exception e) {
                SkyLyricsClient.LOG.warn("Media poller (Windows) failed", e);
            }
            sleep(3000);
        }
    }

    // ------------------------------------------------------------------ Linux

    private static void runLinux() {
        String fmt = "{{title}}\t{{artist}}\t{{position}}\t{{mpris:length}}\t{{status}}";
        while (true) {
            try {
                Process p = new ProcessBuilder("playerctl", "metadata", "--format", fmt).start();
                String out;
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    out = r.readLine();
                }
                p.waitFor();
                if (out == null) update(null, null, 0, 0, false);
                else parseLine(out, 1e-6); // playerctl reports microseconds
            } catch (Exception e) {
                SkyLyricsClient.LOG.warn("playerctl not available - media detection disabled on this OS");
                return;
            }
            sleep(1000);
        }
    }

    private static void parseLine(String line, double timeScale) {
        String[] f = line.split("\t", -1);
        if (f.length < 5) return;
        try {
            double pos = Double.parseDouble(f[2].isBlank() ? "0" : f[2]) * timeScale;
            double dur = Double.parseDouble(f[3].isBlank() ? "0" : f[3]) * timeScale;
            boolean pl = f[4].trim().equals("1") || f[4].trim().equalsIgnoreCase("Playing");
            update(f[0], f[1], pos, dur, pl);
        } catch (NumberFormatException ignored) { }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
