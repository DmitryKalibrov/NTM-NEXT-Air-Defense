package com.hbm.airdefense.client;

import com.hbm.airdefense.AirDefense;
import com.hbm.airdefense.ConsoleCatalog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The mounting kit's element catalogue, drawn like a Soviet instrument data sheet: riveted olive steel, a black
 * header plate, lit section keys, a grid of element pictures and a green phosphor card with the chosen one's data.
 */
public class ScreenElementPicker extends Screen {

    private static final Identifier ICONS = AirDefense.id("textures/gui/console_icons.png");
    private static final int ICON = 64, ICON_COLS = 8;
    private static final int W = 360, H = 226, CELL = 34, GRID_COLS = 5;

    private static final int STEEL = 0xFF3A4436, STEEL_HI = 0xFF5A6650, STEEL_LO = 0xFF1C231B, PLATE = 0xFF0E120E,
            AMBER = 0xFFE8A53A, AMBER_DIM = 0xFF6A4A16, PHOSPHOR = 0xFF8CFFA0, PHOSPHOR_DIM = 0xFF3F8F52,
            SCREEN = 0xFF0A170D, GREY = 0xFF9AA294;

    private static final String[] SECTIONS = {"buttons", "switches", "lamps", "meters", "screens", "other"};

    private final List<String> names = List.copyOf(ConsoleCatalog.ELEMENTS.keySet());
    private int section;
    private String chosen = ConsoleClient.selected;
    private int left, top;

    public ScreenElementPicker() {
        super(Component.translatable("gui.ntm_air_defense.catalog.title"));
        section = section(chosen);
    }

    static int section(String e) {
        if (e.startsWith("btn_") || e.equals("keypad") || e.equals("key_strip") || e.equals("lit_matrix")) return 0;
        if (e.startsWith("toggle") || e.startsWith("knob") || e.equals("rocker") || e.equals("galette_switch")
                || e.equals("key_switch") || e.equals("control_key") || e.equals("slider")) return 1;
        if (e.startsWith("lamp") || e.equals("readiness") || e.startsWith("annunciator") || e.equals("display_digits") || e.equals("counter")) return 2;
        if (e.startsWith("gauge") || e.equals("recorder") || e.equals("seismograph") || e.equals("clock")) return 3;
        if (e.startsWith("crt") || e.equals("tv_screen")) return 4;
        return 5;
    }

    private List<String> shown() {
        List<String> out = new ArrayList<>();
        for (String n : names) if (section(n) == section) out.add(n);
        return out;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    // ---- layout ----
    private int tabX(int i) {
        return left + 8 + i * 57;
    }

    private int tabY() {
        return top + 26;
    }

    private int cellX(int i) {
        return left + 10 + i % GRID_COLS * CELL;
    }

    private int cellY(int i) {
        return top + 50 + i / GRID_COLS * CELL;
    }

    private int acceptX() {
        return left + W - 90;
    }

    private int acceptY() {
        return top + H - 26;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractRenderState(g, mx, my, partial);
        // riveted steel plate with a bevel
        g.fill(left, top, left + W, top + H, STEEL);
        g.fill(left, top, left + W, top + 1, STEEL_HI);
        g.fill(left, top, left + 1, top + H, STEEL_HI);
        g.fill(left, top + H - 1, left + W, top + H, STEEL_LO);
        g.fill(left + W - 1, top, left + W, top + H, STEEL_LO);
        for (int[] r : new int[][]{{4, 4}, {W - 6, 4}, {4, H - 6}, {W - 6, H - 6}}) {
            g.fill(left + r[0], top + r[1], left + r[0] + 2, top + r[1] + 2, STEEL_LO);
            g.fill(left + r[0], top + r[1], left + r[0] + 1, top + r[1] + 1, STEEL_HI);
        }
        // header plate
        g.fill(left + 8, top + 7, left + W - 8, top + 21, PLATE);
        g.text(font, title, left + 13, top + 10, AMBER, false);
        Component form = Component.translatable("gui.ntm_air_defense.catalog.form");
        g.text(font, form, left + W - 13 - font.width(form), top + 10, GREY, false);
        // section keys
        for (int i = 0; i < SECTIONS.length; i++) {
            int x = tabX(i), y = tabY();
            boolean on = i == section, hover = mx >= x && mx < x + 55 && my >= y && my < y + 16;
            g.fill(x, y, x + 55, y + 16, STEEL_LO);
            g.fill(x + 1, y + 1, x + 54, y + 15, on ? AMBER_DIM : hover ? 0xFF2A3227 : PLATE);
            g.centeredText(font, Component.translatable("gui.ntm_air_defense.catalog.section." + SECTIONS[i]), x + 28, y + 4,
                    on ? AMBER : GREY);
        }
        // element grid
        List<String> list = shown();
        g.fill(left + 8, top + 48, left + 12 + GRID_COLS * CELL, top + 52 + 4 * CELL, STEEL_LO);
        String hovered = null;
        for (int i = 0; i < list.size(); i++) {
            String n = list.get(i);
            int x = cellX(i), y = cellY(i);
            boolean hover = mx >= x && mx < x + CELL - 2 && my >= y && my < y + CELL - 2;
            g.fill(x, y, x + CELL - 2, y + CELL - 2, n.equals(chosen) ? 0xFF2C2414 : PLATE);
            icon(g, n, x + 1, y + 1, CELL - 4);
            if (n.equals(chosen)) g.outline(x, y, CELL - 2, CELL - 2, AMBER);
            else if (hover) g.outline(x, y, CELL - 2, CELL - 2, PHOSPHOR);
            if (hover) hovered = n;
        }
        if (hovered != null)  // name of the element under the cursor, along the bottom of the grid
            g.text(font, Component.translatable("element.ntm_air_defense." + hovered), left + 12, top + 40 + 4 * CELL, PHOSPHOR, false);
        // data card: a green phosphor screen
        int cx = left + 20 + GRID_COLS * CELL, cy = top + 48, cw = left + W - 8 - cx, ch = 4 * CELL + 4;
        g.fill(cx, cy, cx + cw, cy + ch, STEEL_LO);
        g.fill(cx + 2, cy + 2, cx + cw - 2, cy + ch - 2, SCREEN);
        for (int y = cy + 3; y < cy + ch - 2; y += 2) g.fill(cx + 2, y, cx + cw - 2, y + 1, 0x22000000);
        ConsoleCatalog.Element e = ConsoleCatalog.get(chosen);
        if (e != null) {
            icon(g, chosen, cx + (cw - 64) / 2, cy + 6, 64);
            int ty = cy + 74;
            g.centeredText(font, Component.translatable("element.ntm_air_defense." + chosen), cx + cw / 2, ty, PHOSPHOR);
            for (Component line : card(e)) {
                ty += 11;
                g.text(font, line, cx + 7, ty, PHOSPHOR_DIM, false);
            }
        }
        // accept key and hint
        int ax = acceptX(), ay = acceptY();
        boolean hover = mx >= ax && mx < ax + 82 && my >= ay && my < ay + 18;
        g.fill(ax, ay, ax + 82, ay + 18, STEEL_LO);
        g.fill(ax + 1, ay + 1, ax + 81, ay + 17, hover ? 0xFF1F5A2C : 0xFF163F20);
        g.centeredText(font, Component.translatable("gui.ntm_air_defense.catalog.accept"), ax + 41, ay + 5, PHOSPHOR);
        g.text(font, Component.translatable("gui.ntm_air_defense.catalog.hint"), left + 10, ay + 5, GREY, false);
    }

    /** What the data card says about an element, worked out from its parts. */
    private static List<Component> card(ConsoleCatalog.Element e) {
        List<Component> out = new ArrayList<>();
        String p = "gui.ntm_air_defense.catalog.";
        out.add(Component.translatable(p + "size", e.w(), e.h()));
        out.add(Component.translatable(p + (e.levelOnly() ? "mount.level" : "mount.any")));
        String action = "none";
        int positions = 0;
        boolean lights = false, tint = false;
        for (String part : e.parts()) {
            switch (ConsoleCatalog.kind(e.name(), part)) {
                case ROTARY -> {
                    action = "rotary";
                    positions = ConsoleCatalog.positions(e.name());
                }
                case TOGGLE -> {
                    if (!action.equals("rotary")) action = "toggle";
                }
                case PUSH -> {
                    if (action.equals("none")) action = "push";
                }
                case NONE -> {}
            }
            lights |= ConsoleCatalog.lights(e.name(), part);
            tint |= ConsoleCatalog.tinted(e.name(), part);
        }
        if (action.equals("none") && lights) action = "lamp";
        if (e.name().equals("seismograph")) action = "seismic";
        out.add(Component.translatable(p + "action." + action, positions));
        out.add(Component.translatable(p + (lights ? "light.yes" : "light.no")));
        out.add(Component.translatable(p + (tint ? "tint.yes" : "tint.no")));
        return out;
    }

    private void icon(GuiGraphicsExtractor g, String name, int x, int y, int size) {
        int i = names.indexOf(name);
        if (i < 0) return;
        int rows = (names.size() + ICON_COLS - 1) / ICON_COLS;
        g.blit(RenderPipelines.GUI_TEXTURED, ICONS, x, y, i % ICON_COLS * ICON, i / ICON_COLS * ICON, size, size, ICON, ICON,
                ICON_COLS * ICON, rows * ICON);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        for (int i = 0; i < SECTIONS.length; i++) {
            if (mx >= tabX(i) && mx < tabX(i) + 55 && my >= tabY() && my < tabY() + 16) {
                section = i;
                return true;
            }
        }
        List<String> list = shown();
        for (int i = 0; i < list.size(); i++) {
            if (mx >= cellX(i) && mx < cellX(i) + CELL - 2 && my >= cellY(i) && my < cellY(i) + CELL - 2) {
                chosen = list.get(i);
                if (doubleClick) accept();
                return true;
            }
        }
        if (mx >= acceptX() && mx < acceptX() + 82 && my >= acceptY() && my < acceptY() + 18) {
            accept();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void accept() {
        ConsoleClient.selected = chosen;
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
