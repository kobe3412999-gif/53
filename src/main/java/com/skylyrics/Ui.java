package com.skylyrics;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

/** Small drawing helpers shared by the HUD and the settings screen. */
public final class Ui {
    /** Accent colour chosen in the settings (opaque ARGB). */
    public static int accent() { return 0xFF000000 | LyricsConfig.get().accent; }

    /** A lighter version of the accent colour. */
    public static int accentLight() {
        int a = LyricsConfig.get().accent;
        int r = (a >> 16) & 255, g = (a >> 8) & 255, b = a & 255;
        r += (int) ((255 - r) * 0.35); g += (int) ((255 - g) * 0.35); b += (int) ((255 - b) * 0.35);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    public static int rgba(float alpha, int rgb) { return argb(alpha, (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255); }

    public static FormattedCharSequence seq(String s, boolean bold) {
        MutableComponent c = Component.literal(s);
        if (bold) c = c.withStyle(ChatFormatting.BOLD);
        return c.getVisualOrderText();
    }

    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        for (int i = 0; i < r; i++) {
            int dy = r - i;
            int inset = r - (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    public static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.sqrt((double) r * r - (double) dy * dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /** A little vinyl record with grooves and a light that orbits while playing. */
    public static void vinyl(GuiGraphicsExtractor g, int cx, int cy, int r, double time, boolean playing) {
        disc(g, cx, cy, r, 0xFF0C0816);
        if (r >= 6) {
            disc(g, cx, cy, (int) (r * 0.92), 0xFF1C1532);
            disc(g, cx, cy, (int) (r * 0.82), 0xFF110C20);
            disc(g, cx, cy, (int) (r * 0.72), 0xFF1C1532);
            disc(g, cx, cy, (int) (r * 0.62), 0xFF110C20);
        }
        disc(g, cx, cy, Math.max(2, (int) (r * 0.44)), accent());
        disc(g, cx, cy, Math.max(1, (int) (r * 0.28)), accentLight());
        disc(g, cx, cy, Math.max(1, (int) (r * 0.10)), 0xFF0C0816);
        double a = playing ? time * 2.4 : 0.8;
        for (int k = 0; k < 2; k++) {
            double ang = a + k * Math.PI;
            int hx = cx + (int) Math.round(Math.cos(ang) * r * 0.78);
            int hy = cy + (int) Math.round(Math.sin(ang) * r * 0.78);
            int s = r >= 20 ? 2 : 1;
            g.fill(hx - s, hy - s, hx + s, hy + s, 0x70FFFFFF);
        }
    }

    public static String fmt(double sec) {
        int s = (int) Math.max(0, sec);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    public static int argb(float a, int r, int gr, int b) {
        int ai = Math.max(5, Math.min(255, (int) (a * 255)));
        return ai << 24 | r << 16 | gr << 8 | b;
    }

    public static int withAlpha(int rgb, int a) {
        return (Math.max(5, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    public static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    /** Solid triangle pointing right (or left if flip) inside a w x h box. */
    public static void triangle(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean flip, int color) {
        for (int c = 0; c < w; c++) {
            int col = flip ? w - 1 - c : c;
            int ht = Math.max(1, Math.round(h * (1f - col / (float) w)));
            g.fill(x + c, y + (h - ht) / 2, x + c + 1, y + (h - ht) / 2 + ht, color);
        }
    }

    /** Thick line made of small squares (used for the tonearm). */
    public static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int thick, int color) {
        int steps = (int) Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) + 1;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = Math.round(x0 + (x1 - x0) * t), y = Math.round(y0 + (y1 - y0) * t);
            g.fill(x, y, x + thick, y + thick, color);
        }
    }
}
