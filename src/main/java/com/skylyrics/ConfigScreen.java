package com.skylyrics;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Settings screen with Display, Style and Layout tabs plus sky visual presets. Vanilla widgets handle clicking and dragging
 * (hidden under the panel); everything you see is drawn by this class on top of them.
 */
public class ConfigScreen extends Screen {
    private static final int PW = 350, PH = 310;

    private static final int[] TEXT_COLORS = {0xE6DAFF, 0xFFFFFF, 0xFF9AD5, 0x7FE8FF, 0x8CFFC1, 0xFFE27A, 0xFFA467, 0xFF6B6B};
    private static final int[] ACCENT_COLORS = {0x7B5CFF, 0x4C8DFF, 0x22D3EE, 0x34D399, 0xF472B6, 0xF43F5E, 0xFB923C, 0xFACC15};

    private static int tab = 0;

    private final Screen parent;
    private int px, py;
    private boolean rebuild = false;

    private record Toggle(Button button, String label, Supplier<Boolean> get, float[] anim) { }
    private record SwatchRow(String label, int y, int x, int[] palette, Button[] buttons, Supplier<Integer> get) { }

    private final List<Toggle> toggles = new ArrayList<>();
    private final List<FloatSlider> sliders = new ArrayList<>();
    private final List<SwatchRow> swatchRows = new ArrayList<>();
    private final Button[] tabButtons = new Button[3];
    private final List<Button> presetButtons = new ArrayList<>();
    private Button doneButton, prevBtn, playBtn, nextBtn;
    private float armAnim = 0f;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Sky Lyrics"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        toggles.clear(); sliders.clear(); swatchRows.clear(); presetButtons.clear();
        LyricsConfig c = LyricsConfig.get();
        px = (this.width - PW) / 2;
        py = (this.height - PH) / 2;
        int cx = px + 128, cw = PW - 128 - 14;

        // tabs
        int tw = (cw - 8) / 3;
        String[] tabNames = {"Display", "Style", "Layout"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            tabButtons[i] = addRenderableWidget(Button.builder(Component.literal(tabNames[i]), b -> switchTab(idx))
                    .bounds(cx + i * (tw + 4), py + 40, tw, 18).build());
        }

        int y = py + 64;
        if (tab == 0) {
            toggle(cx, y, cw, "Now-playing pill", () -> c.showPill, v -> c.showPill = v);
            toggle(cx, y + 22, cw, "Lyrics in the pill", () -> c.pillLyrics, v -> c.pillLyrics = v);
            toggle(cx, y + 44, cw, "Lyrics in the sky", () -> c.skyLyrics, v -> c.skyLyrics = v);
            toggle(cx, y + 66, cw, "Word glow", () -> c.glow, v -> c.glow = v);
            slider(cx, y + 118, cw, "Sync offset (s)", -3f, 3f, c.offset, v -> c.offset = v);
        } else if (tab == 1) {
            toggle(cx, y, cw, "Bold sky text", () -> c.bold, v -> c.bold = v);
            swatchRow(cx, y + 28, "Text colour", TEXT_COLORS, () -> c.textColor, v -> c.textColor = v);
            swatchRow(cx, y + 64, "Accent colour (pill, glow)", ACCENT_COLORS, () -> c.accent, v -> c.accent = v);
            slider(cx, y + 100, cw, "Opacity", 0.2f, 1.0f, c.opacity, v -> c.opacity = v);
            String[] presets = {"Inferno", "Matrix", "Prism", "Pulsar", "Watercolor", "Sunwave"};
            int[] presetAccents = {0xFFFF7A24, 0xFF34D399, 0xFF9B8CFF, 0xFF43D9FF, 0xFFFFB4D5, 0xFFFF5CBE};
            int[] presetTexts = {0xFFFFD9B0, 0xFFB9FFD9, 0xFFE6DAFF, 0xFFC9F8FF, 0xFFFFE7F1, 0xFFFFD9F5};
            for (int i = 0; i < presets.length; i++) {
                final int preset = i;
                Button b = addRenderableWidget(Button.builder(Component.literal(presets[i]), btn -> {
                    c.skyPreset = preset; c.accent = presetAccents[preset]; c.textColor = presetTexts[preset]; LyricsConfig.save();
                }).bounds(cx + (i % 3) * ((cw - 8) / 3 + 4), y + 132 + (i / 3) * 22, (cw - 8) / 3, 18).build());
                presetButtons.add(b);
            }
        } else {
            slider(cx, y, cw, "Text size", 0.5f, 2.5f, c.size, v -> c.size = v);
            slider(cx, y + 30, cw, "Word spacing", 0.2f, 3.0f, c.wordSpacing, v -> c.wordSpacing = v);
            slider(cx, y + 60, cw, "Line spacing", 0.6f, 2.5f, c.lineSpacing, v -> c.lineSpacing = v);
            slider(cx, y + 90, cw, "Random tilt (deg)", 0f, 15f, c.tilt, v -> c.tilt = v);
            slider(cx, y + 120, cw, "Lyric height", -0.3f, 0.45f, c.height, v -> c.height = v);
            slider(cx, y + 150, cw, "Lyric left / right", -0.45f, 0.45f, c.horizontal, v -> c.horizontal = v);
        }

        int vcx = px + 60, mby = py + PH - 34;
        prevBtn = addRenderableWidget(Button.builder(Component.empty(), b -> MediaPoller.command("previous"))
                .bounds(vcx - 38, mby, 20, 20).build());
        playBtn = addRenderableWidget(Button.builder(Component.empty(), b -> MediaPoller.command("toggle"))
                .bounds(vcx - 10, mby, 20, 20).build());
        nextBtn = addRenderableWidget(Button.builder(Component.empty(), b -> MediaPoller.command("next"))
                .bounds(vcx + 18, mby, 20, 20).build());

        doneButton = addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx, py + PH - 34, cw, 22).build());
    }

    private void switchTab(int t) {
        if (tab != t) { tab = t; rebuild = true; }
    }

    private void toggle(int x, int y, int w, String label, Supplier<Boolean> get, Consumer<Boolean> set) {
        Button b = Button.builder(Component.literal(label), btn -> set.accept(!get.get()))
                .bounds(x, y, w, 20).build();
        addRenderableWidget(b);
        toggles.add(new Toggle(b, label, get, new float[]{get.get() ? 1f : 0f}));
    }

    private void slider(int x, int y, int w, String label, float min, float max, float value, Consumer<Float> set) {
        sliders.add(addRenderableWidget(new FloatSlider(x, y, w, 26, label, min, max, value, set)));
    }

    private void swatchRow(int x, int y, String label, int[] palette, Supplier<Integer> get, Consumer<Integer> set) {
        Button[] bs = new Button[palette.length];
        for (int i = 0; i < palette.length; i++) {
            final int col = palette[i];
            bs[i] = addRenderableWidget(Button.builder(Component.empty(), b -> set.accept(col))
                    .bounds(x + i * 26, y + 12, 20, 18).build());
        }
        swatchRows.add(new SwatchRow(label, y, x, palette, bs, get));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        if (rebuild) { rebuild = false; clearWidgets(); init(); }
        super.extractRenderState(g, mouseX, mouseY, partial); // dim background + (hidden) widgets
        LyricsConfig c = LyricsConfig.get();
        double now = System.nanoTime() / 1e9;
        int acc = Ui.accent(), accL = Ui.accentLight();

        // ---- panel
        Ui.roundRect(g, px - 2, py - 2, PW + 4, PH + 4, 14, 0x70000000);
        Ui.roundRect(g, px - 1, py - 1, PW + 2, PH + 2, 13, Ui.withAlpha(c.accent, 0xC0));
        Ui.roundRect(g, px, py, PW, PH, 12, 0xFF130E21);
        Ui.roundRect(g, px + 6, py + 6, 108, PH - 12, 9, 0xFF1A1330);

        // ---- header
        g.pose().pushMatrix();
        g.pose().translate(px + 128, py + 12);
        g.pose().scale(1.5f, 1.5f);
        g.text(this.font, "Sky Lyrics", 0, 0, 0xFFFFFFFF, false);
        g.pose().popMatrix();
        g.text(this.font, "Press J to open this menu", px + 128, py + 28, 0xFF8F85B0, false);

        // ---- tabs
        String[] names = {"Display", "Style", "Layout"};
        for (int i = 0; i < 3; i++) {
            Button b = tabButtons[i];
            boolean sel = tab == i;
            boolean hover = inside(mouseX, mouseY, b.getX(), b.getY(), b.getWidth(), b.getHeight());
            Ui.roundRect(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), 6, sel ? acc : (hover ? 0xFF2A2145 : 0xFF1E1836));
            g.text(this.font, names[i], b.getX() + (b.getWidth() - this.font.width(names[i])) / 2, b.getY() + 5,
                    sel ? 0xFFFFFFFF : 0xFFA59CC4, false);
        }

        // ---- vinyl + current song
        MediaPoller.Track tr = MediaPoller.track();
        boolean playing = tr != null && MediaPoller.playing();
        int vx = px + 60, vy = py + 78;
        Ui.vinyl(g, vx, vy, 42, now, playing);

        // tonearm: rests on the record while playing, lifts away when paused
        armAnim += ((playing ? 1f : 0f) - armAnim) * 0.12f;
        float pivX = vx + 42, pivY = vy - 42;
        float tipX = (vx + 34) + ((vx + 14) - (vx + 34)) * armAnim;
        float tipY = (vy - 24) + ((vy - 6) - (vy - 24)) * armAnim;
        Ui.line(g, pivX, pivY, tipX, tipY, 2, 0xFFC9C2DD);
        Ui.disc(g, (int) pivX, (int) pivY, 4, 0xFF8F85B0);
        Ui.disc(g, (int) pivX, (int) pivY, 2, 0xFF2A2145);
        g.fill((int) tipX - 2, (int) tipY - 1, (int) tipX + 3, (int) tipY + 3, 0xFFE5E0F5);
        g.enableScissor(px + 8, py + 130, px + 112, py + 170);
        String title = tr == null ? "Nothing playing" : tr.title();
        String artist = tr == null ? "Start a song to begin" : tr.artist();
        g.text(this.font, title, vx - this.font.width(title) / 2, py + 138, 0xFFFFFFFF, false);
        g.text(this.font, artist, vx - this.font.width(artist) / 2, py + 150, 0xFF9A90B8, false);
        g.disableScissor();
        String st = LyricsService.status();
        if (!st.isEmpty()) {
            g.enableScissor(px + 8, py + 172, px + 112, py + 196);
            g.text(this.font, st, vx - this.font.width(st) / 2, py + 176, accL, false);
            g.disableScissor();
        }

        // ---- live preview of the sky text style
        FormattedCharSequence prev = Ui.seq("Lyrics", c.bold);
        int pw = this.font.width(prev);
        g.pose().pushMatrix();
        g.pose().translate(vx, py + 212);
        g.pose().scale(c.size * 1.7f, c.size * 1.7f);
        if (c.tilt > 0f) g.pose().rotate((float) Math.toRadians(c.tilt * 0.5));
        if (c.glow) {
            int glow = Ui.rgba(0.3f, c.accent);
            g.text(this.font, prev, -pw / 2 + 1, -4, glow, false);
            g.text(this.font, prev, -pw / 2 - 1, -4, glow, false);
            g.text(this.font, prev, -pw / 2, -3, glow, false);
            g.text(this.font, prev, -pw / 2, -5, glow, false);
        }
        g.text(this.font, prev, -pw / 2, -4, Ui.rgba(c.opacity, c.textColor), false);
        g.pose().popMatrix();

        // ---- toggles
        for (Toggle t : toggles) {
            Button b = t.button();
            int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
            boolean hover = inside(mouseX, mouseY, x, y, w, h);
            boolean on = t.get().get();
            t.anim()[0] += ((on ? 1f : 0f) - t.anim()[0]) * 0.35f;

            Ui.roundRect(g, x, y, w, h, 6, hover ? 0xFF2A2145 : 0xFF1E1836);
            g.text(this.font, t.label(), x + 8, y + 6, on ? 0xFFEDE7FF : 0xFFA59CC4, false);

            int sw = 26, sh = 12, sx = x + w - sw - 8, sy = y + (h - sh) / 2;
            Ui.roundRect(g, sx, sy, sw, sh, 6, on ? acc : 0xFF3A3250);
            int kx = sx + 2 + Math.round((sw - sh) * t.anim()[0]);
            Ui.roundRect(g, kx, sy + 2, 8, 8, 4, 0xFFFFFFFF);
        }

        // ---- sky preset buttons
        String[] presetNames = {"Inferno", "Matrix", "Prism", "Pulsar", "Watercolor", "Sunwave"};
        for (Button b : presetButtons) {
            boolean hover = inside(mouseX, mouseY, b.getX(), b.getY(), b.getWidth(), b.getHeight());
            Ui.roundRect(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), 5, hover ? accL : 0xFF2A2145);
            String label = b.getMessage().getString();
            g.text(this.font, label, b.getX() + (b.getWidth() - this.font.width(label)) / 2, b.getY() + 5, 0xFFEDE7FF, false);
        }

        // ---- colour swatches
        for (SwatchRow row : swatchRows) {
            g.text(this.font, row.label(), row.x(), row.y(), 0xFFEDE7FF, false);
            int sel = row.get().get();
            for (int i = 0; i < row.palette().length; i++) {
                Button b = row.buttons()[i];
                boolean hover = inside(mouseX, mouseY, b.getX(), b.getY(), b.getWidth(), b.getHeight());
                boolean chosen = row.palette()[i] == sel;
                if (chosen || hover) Ui.roundRect(g, b.getX() - 2, b.getY() - 2, b.getWidth() + 4, b.getHeight() + 4, 7,
                        chosen ? 0xFFFFFFFF : 0x80FFFFFF);
                Ui.roundRect(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), 5, 0xFF000000 | row.palette()[i]);
            }
        }

        // ---- sliders
        for (FloatSlider s : sliders) {
            int x = s.getX(), y = s.getY(), w = s.getWidth(), h = s.getHeight();
            boolean hover = inside(mouseX, mouseY, x, y, w, h);
            Ui.roundRect(g, x, y, w, h, 6, hover ? 0xFF2A2145 : 0xFF1E1836);
            g.text(this.font, s.label, x + 8, y + 4, 0xFFEDE7FF, false);
            String val = String.format("%.2f", s.real());
            g.text(this.font, val, x + w - 8 - this.font.width(val), y + 4, accL, false);
            int tx0 = x + 8, tx1 = x + w - 8, ty = y + h - 7;
            g.fill(tx0, ty, tx1, ty + 3, 0xFF3A3250);
            int kx = tx0 + Math.round((tx1 - tx0) * (float) s.frac());
            g.fill(tx0, ty, kx, ty + 3, acc);
            Ui.roundRect(g, kx - 3, ty - 2, 7, 7, 3, 0xFFFFFFFF);
        }

        // ---- player buttons (previous / play-pause / next)
        Button[] mb = {prevBtn, playBtn, nextBtn};
        for (int i = 0; i < 3; i++) {
            Button b = mb[i];
            boolean hover = inside(mouseX, mouseY, b.getX(), b.getY(), b.getWidth(), b.getHeight());
            int mx = b.getX() + b.getWidth() / 2, my = b.getY() + b.getHeight() / 2;
            int bg = i == 1 ? (hover ? accL : acc) : (hover ? 0xFF2A2145 : 0xFF1E1836);
            Ui.disc(g, mx, my, 10, bg);
            int ic = 0xFFFFFFFF;
            if (i == 0) { g.fill(mx - 5, my - 4, mx - 3, my + 4, ic); Ui.triangle(g, mx - 3, my - 4, 8, 8, true, ic); }
            else if (i == 2) { Ui.triangle(g, mx - 5, my - 4, 8, 8, false, ic); g.fill(mx + 3, my - 4, mx + 5, my + 4, ic); }
            else if (playing) { g.fill(mx - 3, my - 4, mx - 1, my + 4, ic); g.fill(mx + 1, my - 4, mx + 3, my + 4, ic); }
            else Ui.triangle(g, mx - 3, my - 5, 8, 10, false, ic);
        }

        // ---- done button
        Button d = doneButton;
        boolean dh = inside(mouseX, mouseY, d.getX(), d.getY(), d.getWidth(), d.getHeight());
        Ui.roundRect(g, d.getX(), d.getY(), d.getWidth(), d.getHeight(), 7, dh ? accL : acc);
        String done = "Done";
        g.text(this.font, done, d.getX() + (d.getWidth() - this.font.width(done)) / 2, d.getY() + 7, 0xFFFFFFFF, false);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void onClose() {
        LyricsConfig.save();
        super.onClose();
    }

    private static final class FloatSlider extends AbstractSliderButton {
        final String label; private final float min, max; private final Consumer<Float> set;

        FloatSlider(int x, int y, int w, int h, String label, float min, float max, float value, Consumer<Float> set) {
            super(x, y, w, h, Component.empty(), (value - min) / (max - min));
            this.label = label; this.min = min; this.max = max; this.set = set;
        }

        double frac() { return this.value; }
        float real() { return min + (float) this.value * (max - min); }

        @Override protected void updateMessage() { setMessage(Component.empty()); }
        @Override protected void applyValue() { set.accept(real()); }
    }
}
