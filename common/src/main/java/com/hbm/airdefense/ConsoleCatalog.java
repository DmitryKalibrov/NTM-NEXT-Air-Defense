package com.hbm.airdefense;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The panel elements of the control room: footprint and moving parts come from control_parts.json (written by
 * tools/blender/console_export.py next to the meshes), how each part reacts to a press is decided here by name.
 */
public final class ConsoleCatalog {

    /** How a part answers a press. */
    public enum Kind { NONE, PUSH, TOGGLE, ROTARY }

    public record Element(String name, int w, int h, boolean levelOnly, List<String> parts, Map<String, float[]> pivots) {}

    public static final Map<String, Element> ELEMENTS = load();

    private ConsoleCatalog() {}

    public static @Nullable Element get(String name) {
        return ELEMENTS.get(name);
    }

    private static Map<String, Element> load() {
        String path = "/assets/" + AirDefense.MOD_ID + "/models/control_parts.json";
        try (Reader r = new InputStreamReader(Objects.requireNonNull(ConsoleCatalog.class.getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            Map<String, Element> out = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : JsonParser.parseReader(r).getAsJsonObject().entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                List<String> parts = new ArrayList<>();
                Map<String, float[]> pivots = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> p : o.getAsJsonObject("parts").entrySet()) {
                    parts.add(p.getKey());
                    JsonObject po = p.getValue().getAsJsonObject();
                    if (po.has("pivot")) {
                        var a = po.getAsJsonArray("pivot");
                        pivots.put(p.getKey(), new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()});
                    }
                }
                var size = o.getAsJsonArray("size");
                out.put(e.getKey(), new Element(e.getKey(), size.get(0).getAsInt(), size.get(1).getAsInt(),
                        "top".equals(o.get("mount").getAsString()), List.copyOf(parts), Map.copyOf(pivots)));
            }
            return Collections.unmodifiableMap(out);
        } catch (Exception ex) {
            throw new IllegalStateException("can't read " + path, ex);
        }
    }

    /** A colour stripe moves with the part it sits on: "lever_tint" is pressed as "lever"; the portrait's
     *  pictures "pic0".."pic4" are one part "pic" whose state picks the one shown. */
    public static String base(String part) {
        if (part.startsWith("pic")) return "pic";
        return part.endsWith("_tint") ? part.substring(0, part.length() - 5) : part;
    }

    /** Desk portrait: the leaders in the order of their pictures (tools/gen_portraits.py). */
    public static final String[] LEADERS = {"putin", "trump", "xi", "modi", "kim"};

    public static Kind kind(String element, String part) {
        String p = base(part);
        return switch (element) {
            case "btn_square", "btn_round", "btn_mushroom", "btn_guarded" -> p.equals("cap") ? Kind.PUSH : Kind.NONE;
            case "btn_pair" -> p.startsWith("cap") ? Kind.PUSH : Kind.NONE;
            case "keypad" -> p.startsWith("key") ? Kind.PUSH : Kind.NONE;
            case "btn_lit" -> p.equals("cap") ? Kind.TOGGLE : Kind.NONE;
            case "toggle", "toggle_covered" -> p.equals("lever") || p.equals("cover") ? Kind.TOGGLE : Kind.NONE;
            case "rocker" -> p.equals("rocker") ? Kind.TOGGLE : Kind.NONE;
            case "key_switch" -> p.equals("key") ? Kind.TOGGLE : Kind.NONE;
            case "slider" -> p.equals("knob") ? Kind.TOGGLE : Kind.NONE;
            case "desk_lamp" -> p.equals("switch") ? Kind.TOGGLE : Kind.NONE;
            case "lamp_round", "lamp_square", "annunciator", "annunciator_bank" -> p.startsWith("lens") ? Kind.TOGGLE : Kind.NONE;
            case "lit_matrix", "key_strip" -> p.startsWith("key") ? Kind.TOGGLE : Kind.NONE;
            case "knob_pointer", "knob_small", "galette_switch" -> p.equals("knob") ? Kind.ROTARY : Kind.NONE;
            case "control_key" -> p.equals("handle") ? Kind.ROTARY : Kind.NONE;
            case "speaker" -> p.equals("knob") ? Kind.ROTARY : Kind.NONE;  // volume
            case "portrait" -> p.equals("pic") ? Kind.ROTARY : Kind.NONE;  // next leader
            default -> Kind.NONE;
        };
    }

    /** Positions of a rotary part. */
    public static int positions(String element) {
        return switch (element) {
            case "knob_pointer" -> 7;
            case "control_key" -> 3;
            case "portrait" -> LEADERS.length;
            default -> 6;
        };
    }

    public static int initial(String element, String part) {
        if (kind(element, part) != Kind.ROTARY) return 0;
        return switch (element) {
            case "knob_pointer" -> 3;
            case "control_key" -> 1;
            case "galette_switch" -> 2;
            case "speaker" -> 3;
            default -> 0;
        };
    }

    /** Clockwise turn (degrees, seen from the front) of a rotary part at a position. */
    public static float angle(String element, int pos) {
        return switch (element) {
            case "knob_pointer" -> -135 + 45 * pos;
            case "control_key" -> -45 + 45 * pos;
            case "galette_switch" -> -120 + 48 * pos;
            case "portrait" -> 0;  // the picture changes, nothing turns
            default -> -100 + 40 * pos;
        };
    }

    /** Parts that glow when on. */
    public static boolean lights(String element, String part) {
        return part.startsWith("lens") || part.equals("bulb")
                || (element.equals("btn_lit") && part.equals("cap"))
                || ((element.equals("lit_matrix") || element.equals("key_strip")) && part.startsWith("key"));
    }

    /** Parts modelled white that take the element's colour (default white). */
    public static boolean tinted(String element, String part) {
        return part.endsWith("_tint") || part.startsWith("lens") || part.equals("ball")
                || (part.equals("cap") && (element.equals("btn_square") || element.equals("btn_round")
                || element.equals("btn_lit") || element.equals("btn_guarded")));
    }
}
