package com.skylyrics;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * (1) the now-playing pill and (2) lyric words anchored to directions in the sky.
 * Sky words are projected with a real perspective camera model every frame, so they stay
 * locked in the world while you turn. Lines never overlap: a new line fades the old one out
 * quickly and alternates between a top and a lower slot.
 */
public final class LyricsHud {
    private static final class Word {
        String text; FormattedCharSequence seq;
        float yaw, pitch, xpx, ypx, tilt, scale, bobPhase, fade = 0.8f;
        double appear, end;
    }

    private static final List<Word> words = new ArrayList<>();
    private static final Random RNG = new Random();
    private static int lastIdx = -2;
    private static String lastKey = "";
    private static int slot = 0;
    private static float lastBlockH = 0;

    public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        LyricsConfig c = LyricsConfig.get();
        Font font = mc.font;
        int W = g.guiWidth(), H = g.guiHeight();
        float pt = dt.getGameTimeDeltaPartialTick(true);

        MediaPoller.Track tr = MediaPoller.track();
        boolean playing = tr != null && MediaPoller.playing();
        double pos = MediaPoller.position() + c.offset;
        List<LyricsService.Line> lines = LyricsService.lines();
        double now = System.nanoTime() / 1e9;

        String key = tr == null ? "" : tr.key();
        if (!key.equals(lastKey)) { words.clear(); lastIdx = -2; lastKey = key; slot = 0; }

        // camera, interpolated every frame (NOT the 20-per-second tick value)
        float cyaw = mc.player.getViewYRot(pt);
        float cpitch = mc.player.getViewXRot(pt);
        float fov = mc.options.fov().get().floatValue();
        float focal = (H / 2f) / (float) Math.tan(Math.toRadians(fov / 2.0));

        int idx = LyricsService.indexAt(pos);
        if (c.skyLyrics && playing && !lines.isEmpty()) {
            if (idx != lastIdx) {
                if (idx >= 0) spawn(font, W, H, c, focal, cyaw, cpitch, lines.get(idx), pos);
                lastIdx = idx;
            }
        }
        if (c.skyLyrics && !words.isEmpty()) renderWords(g, font, W, H, c, pos, now, cyaw, cpitch, focal);

        if (c.showPill) drawPill(g, font, W, tr, playing, pos, lines, idx, now, c);
    }

    // ------------------------------------------------------------------ sky words

    private static void spawn(Font font, int W, int H, LyricsConfig c, float focal, float cyaw, float cpitch,
                              LyricsService.Line ln, double pos) {
        // 1) the previous line fades out quickly so two lines never sit on top of each other
        for (Word w : words) {
            if (w.end > pos + 0.4) { w.end = pos + 0.4; w.fade = 0.4f; }
        }

        String[] parts = ln.text().trim().split("\\s+");
        int n = parts.length;
        float s = c.size * 1.7f;
        FormattedCharSequence[] seqs = new FormattedCharSequence[n];
        float[] ww = new float[n];
        for (int i = 0; i < n; i++) { seqs[i] = Ui.seq(parts[i], c.bold); ww[i] = font.width(seqs[i]) * s; }
        float spaceW = font.width(Ui.seq(" ", c.bold)) * s * c.wordSpacing;

        // 2) wrap into centred rows
        float maxRow = W * 0.5f;
        List<List<Integer>> rows = new ArrayList<>();
        List<Float> rowW = new ArrayList<>();
        List<Integer> cur = new ArrayList<>();
        float curW = 0;
        for (int i = 0; i < n; i++) {
            if (!cur.isEmpty() && curW + spaceW + ww[i] > maxRow) {
                rows.add(cur); rowW.add(curW); cur = new ArrayList<>(); curW = 0;
            }
            curW += (cur.isEmpty() ? 0 : spaceW) + ww[i];
            cur.add(i);
        }
        if (!cur.isEmpty()) { rows.add(cur); rowW.add(curW); }

        // 3) alternate between a top slot and a slot just below the previous block
        float rowPx = font.lineHeight * s * 1.5f * c.lineSpacing;
        float blockH = rows.size() * rowPx;
        float baseTop = -H * c.height;
        float top = slot == 0 ? baseTop : baseTop + lastBlockH + rowPx * 0.5f;
        slot = 1 - slot;
        lastBlockH = blockH;

        double dur = Math.max(0.6, ln.end() - ln.start());
        double stagger = Math.min(dur * 0.7, 1.4);

        for (int r = 0; r < rows.size(); r++) {
            float x = -rowW.get(r) / 2f;
            float ypx = top + r * rowPx + rowPx / 2f;
            for (int i : rows.get(r)) {
                float xpx = x + ww[i] / 2f;
                Word wd = new Word();
                wd.text = parts[i];
                wd.seq = seqs[i];
                wd.xpx = xpx;
                wd.ypx = ypx;
                wd.yaw = cyaw + (float) Math.toDegrees(Math.atan(xpx / focal));
                wd.pitch = Math.max(-85f, Math.min(85f, cpitch + (float) Math.toDegrees(Math.atan(ypx / focal))));
                wd.tilt = (float) Math.toRadians((RNG.nextFloat() - 0.5f) * 2f * c.tilt);
                wd.scale = s;                       // identical scale -> clean alignment
                wd.bobPhase = RNG.nextFloat() * 6.28f;
                wd.appear = ln.start() + (i / (double) n) * stagger;
                wd.end = ln.end() + 0.6;
                words.add(wd);
                x += ww[i] + spaceW;
            }
        }
    }

    private static void renderWords(GuiGraphicsExtractor g, Font font, int W, int H, LyricsConfig c,
                                    double pos, double now, float cyaw, float cpitch, float focal) {
        // camera basis (Minecraft: yaw 0 faces +Z, pitch > 0 looks down)
        double cy = Math.toRadians(cyaw), cp = Math.toRadians(cpitch);
        double fx = -Math.sin(cy) * Math.cos(cp), fy = -Math.sin(cp), fz = Math.cos(cy) * Math.cos(cp);
        double rx = -Math.cos(cy), ry = 0, rz = -Math.sin(cy);
        double ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx;

        Iterator<Word> it = words.iterator();
        while (it.hasNext()) {
            Word w = it.next();
            if (pos > w.end + 0.3 || pos < w.appear - 4) { it.remove(); continue; }
            if (pos < w.appear) continue;

            float in = Ui.clamp01((float) ((pos - w.appear) / 0.3));
            float ease = 1f - (1f - in) * (1f - in) * (1f - in);
            float out = Ui.clamp01((float) ((w.end - pos) / w.fade));
            float alpha = ease * out * c.opacity;
            if (alpha <= 0.02f) continue;

            // Gentle floating motion like the reference: each word drifts independently,
            // bobs vertically, and breathes slightly in size instead of looking static.
            float phase = (float) (now * 0.72 + w.bobPhase);
            float bob = (float) Math.sin(now * 1.3 + w.bobPhase) * 0.55f;
            float driftX = (float) Math.sin(phase * 0.63f) * 5.0f;
            float driftY = (float) Math.cos(phase) * 2.5f;
            float sx, sy;
            if (false) {
                // Screen-relative anchor: camera turns won't leave words behind in the world.
                sx = W / 2f + w.xpx + W * c.horizontal + driftX;
                sy = H / 2f + w.ypx + W * 0f + (bob + driftY) * 3f + (1f - ease) * 8f;
            } else {
                double wy = Math.toRadians(w.yaw), wp = Math.toRadians(w.pitch + bob);
                double dx = -Math.sin(wy) * Math.cos(wp), dy = -Math.sin(wp), dz = Math.cos(wy) * Math.cos(wp);
                double zc = dx * fx + dy * fy + dz * fz;
                if (zc < 0.08) continue; // behind the camera
                double xc = dx * rx + dy * ry + dz * rz;
                double yc = dx * ux + dy * uy + dz * uz;
                sx = W / 2f + (float) (xc / zc * focal) + driftX;
                sy = H / 2f - (float) (yc / zc * focal) + driftY + (1f - ease) * 8f;
            }
            if (sx < -300 || sx > W + 300 || sy < -120 || sy > H + 120) continue;

            float breathe = 1.0f + (float) Math.sin(now * 1.1 + w.bobPhase) * 0.025f;
            float sc = w.scale * (0.9f + 0.1f * ease) * breathe;
            int tw = font.width(w.seq);
            g.pose().pushMatrix();
            g.pose().translate(sx, sy);
            if (w.tilt != 0f) g.pose().rotate(w.tilt);
            g.pose().scale(sc, sc);
            if (c.glow) {
                int glow = Ui.rgba(alpha * 0.28f, c.accent);
                g.text(font, w.seq, -tw / 2 + 1, -4, glow, false);
                g.text(font, w.seq, -tw / 2 - 1, -4, glow, false);
                g.text(font, w.seq, -tw / 2, -3, glow, false);
                g.text(font, w.seq, -tw / 2, -5, glow, false);
            }
            g.text(font, w.seq, -tw / 2, -4, Ui.rgba(alpha, c.textColor), false);
            g.pose().popMatrix();
        }
    }

    // ------------------------------------------------------------------ pill

    private static void drawPill(GuiGraphicsExtractor g, Font font, int W, MediaPoller.Track tr,
                                 boolean playing, double pos, List<LyricsService.Line> lines, int idx,
                                 double now, LyricsConfig c) {
        int pw = 280, ph = 24, px = (W - pw) / 2, py = 6;
        int ac = c.accent;

        Ui.roundRect(g, px - 2, py - 1, pw + 4, ph + 4, 14, 0x30000000);
        Ui.roundRect(g, px - 1, py - 1, pw + 2, ph + 2, 13, Ui.withAlpha(ac, 0xA0));
        Ui.roundRect(g, px, py, pw, ph, 12, 0xF2120D1E);

        String t1 = Ui.fmt(pos);
        g.text(font, t1, px - font.width(t1) - 8, py + 8, 0xFFFFFFFF, true);
        if (tr != null && tr.duration() > 0) g.text(font, Ui.fmt(tr.duration()), px + pw + 8, py + 8, 0xFF9A90B8, true);

        Ui.vinyl(g, px + 14, py + ph / 2, 8, now, playing);
        for (int i = 0; i < 3; i++) {
            int h = playing ? 3 + (int) (6 * (0.5 + 0.5 * Math.sin(now * (5 + i * 1.7) + i * 1.3))) : 3;
            int bx = px + pw - 19 + i * 4;
            g.fill(bx, py + ph / 2 + 5 - h, bx + 2, py + ph / 2 + 5, Ui.accentLight());
        }

        if (tr != null && tr.duration() > 0) {
            int bx0 = px + 16, bx1 = px + pw - 16;
            int fill = (int) ((bx1 - bx0) * Ui.clamp01((float) (pos / tr.duration())));
            g.fill(bx0, py + ph - 3, bx1, py + ph - 2, 0x30FFFFFF);
            g.fill(bx0, py + ph - 3, bx0 + fill, py + ph - 2, Ui.accent());
        }

        int x0 = px + 30, x1 = px + pw - 28, tw = x1 - x0;
        g.enableScissor(x0, py, x1, py + ph);
        if (tr == null) {
            String s = "Nothing playing";
            g.text(font, s, x0 + (tw - font.width(s)) / 2, py + 7, 0xFF9A90B8, false);
        } else if (c.pillLyrics && idx >= 0 && idx < lines.size() && playing) {
            drawLyricLine(g, font, x0, x1, py, ph, lines.get(idx), pos);
        } else {
            String s = tr.title() + (tr.artist().isEmpty() ? "" : "  \u00B7  " + tr.artist());
            int sw = font.width(s);
            g.text(font, s, sw <= tw ? x0 + (tw - sw) / 2 : x0, py + 7, 0xFFEDE7FF, false);
        }
        g.disableScissor();
    }

    private static void drawLyricLine(GuiGraphicsExtractor g, Font font, int x0, int x1, int py, int ph,
                                      LyricsService.Line ln, double pos) {
        String[] parts = ln.text().trim().split("\\s+");
        int n = parts.length, space = font.width(" ");
        int[] w = new int[n], start = new int[n];
        int total = 0;
        for (int i = 0; i < n; i++) { w[i] = font.width(parts[i]); start[i] = total; total += w[i] + space; }
        total -= space;

        // continuous position through the line -> the highlight glides left to right
        float prog = Ui.clamp01((float) ((pos - ln.start()) / Math.max(0.4, ln.end() - ln.start())));
        double fp = Math.min(n - 0.001, prog * 1.1 * n);
        int i0 = (int) Math.floor(fp);
        int i1 = Math.min(n - 1, i0 + 1);
        float fr = (float) (fp - i0);
        fr = fr * fr * (3f - 2f * fr);
        float hx = start[i0] + (start[i1] - start[i0]) * fr;
        float hw = w[i0] + (w[i1] - w[i0]) * fr;
        int activeWord = fr < 0.5f ? i0 : i1;

        float fadeIn = Ui.clamp01((float) ((pos - ln.start()) / 0.3));
        int a = (int) (150 + 105 * fadeIn);

        int tw = x1 - x0;
        int sx;
        if (total <= tw) {
            sx = x0 + (tw - total) / 2;                       // fits: text stays put, only the highlight moves
        } else {
            // too long: slide the text slowly so the highlight still travels left -> right
            float center = hx + hw / 2f;
            float target = x0 + tw * (0.12f + 0.76f * prog);
            sx = Math.round(target - center);
            sx = Math.min(x0, Math.max(x1 - total, sx));
        }

        Ui.roundRect(g, sx + Math.round(hx) - 3, py + 4, Math.round(hw) + 6, ph - 9, 5, Ui.accent());
        for (int i = 0; i < n; i++) {
            int x = sx + start[i];
            g.text(font, parts[i], x, py + 7, i == activeWord ? 0xFFFFFFFF : Ui.withAlpha(0xC4BBDD, a), false);
        }
    }
}
