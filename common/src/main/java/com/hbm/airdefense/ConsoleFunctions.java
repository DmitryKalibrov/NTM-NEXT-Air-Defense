package com.hbm.airdefense;

import com.hbm.util.ChunkUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * What a panel control can do (mounting kit: remember a device, click a control). The part's kind decides the
 * role: a push button sends a command, a switch sets a state, a rotary picks a mode, a lamp shows a condition.
 * Lit parts always show the device's real state, not the switch position. A few functions belong to the console
 * itself (alarm acknowledge and alarm lamp) and need no device. A control may drive a group of devices.
 */
public final class ConsoleFunctions {

    private static final List<String> SAM_COMMANDS = List.of("fire", "drop");
    private static final List<String> SAM_STATES = List.of("power", "manual", "f_players", "f_animals", "f_mobs", "f_machines");
    private static final List<String> PANTSIR_STATES = List.of("m_players", "m_animals", "m_mobs", "m_machines");
    private static final List<String> SAM_MODES = List.of("mode");
    private static final List<String> SAM_LAMPS = List.of("ready", "lock", "launch", "noammo", "lowammo", "nopower", "power", "manual");
    private static final List<String> RADAR_LAMPS = List.of("contact", "missile");

    private ConsoleFunctions() {}

    /** Lamps and annunciators: their only job is showing something. */
    static boolean indicatorOnly(String element) {
        return switch (element) {
            case "lamp_round", "lamp_square", "annunciator", "annunciator_bank" -> true;
            default -> false;
        };
    }

    /** Dials and digit windows that show a quantity of the bound device instead of idling. */
    public static boolean meter(String element) {
        return element.startsWith("gauge_") || digits(element);
    }

    public static boolean digits(String element) {
        return element.equals("display_digits") || element.equals("counter");
    }

    /** A dial's reading, 0..1. */
    public static float fraction(String fn, @Nullable ConsoleFeed f) {
        if (f == null || f.kind().equals("lost")) return 0;
        return switch (fn) {
            case "ammo" -> f.capacity() <= 0 ? 0 : Math.min(1, f.ammo() / (float) f.capacity());
            case "charge" -> Math.min(1, f.charge() / 100f);
            default -> 0;
        };
    }

    /** A digit window's number, -1 for none. */
    public static int number(String fn, @Nullable ConsoleFeed f) {
        if (f == null || f.kind().equals("lost")) return -1;
        return switch (fn) {
            case "ammo" -> f.ammo();
            case "charge" -> f.charge();
            case "kills" -> f.kills();
            case "contacts" -> f.blips().size() / ConsoleFeed.BLIP;
            default -> -1;
        };
    }

    /** The console's own functions: no device behind them. */
    public static boolean internal(String fn) {
        return fn.equals("ack") || fn.equals("alarm");
    }

    /** Functions a part can take from a device ("radar", a launcher kind, "" = none remembered); empty when none. */
    public static List<String> choices(String element, String part, String device) {
        String b = ConsoleCatalog.base(part);
        boolean lights = ConsoleCatalog.lights(element, b), radar = device.equals("radar"),
                sam = !device.isEmpty() && !radar && !device.equals("lost") && !device.equals("eos");
        List<String> out = new ArrayList<>();
        if (element.equals("portrait")) return out;
        if (element.equals("speaker")) {  // the whole speaker voices one device's events; its knob is only the volume
            if (part.isEmpty() && (sam || radar)) out.add("announce");
            return out;
        }
        if (meter(element)) {  // the whole instrument shows one quantity
            if (sam) out.addAll(digits(element) ? List.of("ammo", "charge", "kills") : List.of("ammo", "charge"));
            if (radar && digits(element)) out.add("contacts");
            return out;
        }
        switch (ConsoleCatalog.kind(element, b)) {
            case PUSH -> {
                if (sam) out.addAll(SAM_COMMANDS);
                out.add("ack");
            }
            case TOGGLE -> {
                if (sam && !indicatorOnly(element)) {
                    out.addAll(SAM_STATES);
                    if (device.equals("pantsir")) out.addAll(PANTSIR_STATES);
                }
                if ((sam || radar) && !indicatorOnly(element)) out.add("speaker");
                if (lights) {
                    if (sam) SAM_LAMPS.stream().filter(f -> !out.contains(f)).forEach(out::add);
                    if (radar) out.addAll(RADAR_LAMPS);
                    out.add("alarm");
                }
            }
            case ROTARY -> {
                if (sam) out.addAll(SAM_MODES);
            }
            case NONE -> {}
        }
        return out;
    }

    /** Functions that only show (a press on the part does nothing). */
    public static boolean display(String element, String fn) {
        if (indicatorOnly(element)) return true;
        return !(fn.equals("ack") || fn.equals("speaker") || SAM_COMMANDS.contains(fn) || SAM_STATES.contains(fn) || PANTSIR_STATES.contains(fn) || SAM_MODES.contains(fn));
    }

    /** Is the condition behind a lit function true for what the device reports? (alarm: see the console) */
    public static boolean lit(String fn, @Nullable ConsoleFeed f) {
        if (f == null || f.kind().equals("lost")) return false;
        String s = f.state();
        return switch (fn) {
            case "ready" -> s.equals("ready") || s.equals("lock") || s.equals("launch");
            case "lock" -> s.equals("lock") || s.equals("launch");
            case "launch" -> s.equals("launch");
            case "noammo" -> s.equals("ammo");
            case "lowammo" -> f.capacity() > 0 && f.ammo() * 4 <= f.capacity();
            case "nopower" -> s.equals("power");
            case "power" -> !s.equals("off");
            case "manual" -> f.manual();
            case "f_players" -> (f.filter() & 1) != 0;
            case "f_animals" -> (f.filter() & 2) != 0;
            case "f_mobs" -> (f.filter() & 4) != 0;
            case "f_machines" -> (f.filter() & 8) != 0;
            case "m_players" -> (f.filter() & 16) != 0;
            case "m_animals" -> (f.filter() & 32) != 0;
            case "m_mobs" -> (f.filter() & 64) != 0;
            case "m_machines" -> (f.filter() & 128) != 0;
            case "contact" -> !f.blips().isEmpty();
            case "missile" -> missiles(f);
            default -> false;
        };
    }

    /** Any missile (blip level 0..10) among a radar's contacts. */
    public static boolean missiles(ConsoleFeed f) {
        List<Integer> bl = f.blips();
        for (int k = 4; k < bl.size(); k += ConsoleFeed.BLIP) if (bl.get(k) >= 0 && bl.get(k) <= 10) return true;
        return false;
    }

    /** A key that switches the speakers of its device on and off: the console keeps it, the device never hears of it. */
    public static boolean consoleOnly(String fn) {
        return fn.equals("speaker");
    }

    /** A control moved (switch / rotary position, or 1 for a push): pass it to the device; why it refused, or null. */
    static @Nullable String apply(Level level, BlockPos dev, String fn, int value) {
        if (ChunkUtil.blockEntityIfLoaded(BlockEntitySAMBase.class, level, dev) instanceof BlockEntitySAMBase sam) return sam.command(fn, value);
        return "lost";
    }
}
