package com.shiftcal.widget;

import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;

/** A colour palette for the widget and the day window. */
final class Pal {
    int bg, past, future, today, accent, fg, muted, sun;
    boolean dark = true;
    /** Finer colours, normally worked out from the ones above; each can be set from the desktop panel. */
    int now, dow, sunday, head, num, evText, time, cancel, more, mkText;
    boolean timeAuto = true, mkAuto = true;
    /** Keys the desktop panel can set to override any single colour, whatever palette is chosen. */
    static final String[] OVERRIDES = { "x_bg", "x_past", "x_future", "x_today", "x_accent", "x_fg", "x_muted", "x_sun",
        "x_now", "x_dow", "x_sunday", "x_head", "x_num", "x_evtext", "x_time", "x_cancel", "x_more", "x_mktext" };

    static final String[][] PRESETS = {
        { "bush", "Bush green" }, { "dots", "Dot painting (slate and ochre)" }, { "desert", "Red desert" },
        { "fern", "Rainforest fern" }, { "gum", "Gum tree and sky" }, { "bark", "Pale bark on dark rock" },
        { "light", "Light" }, { "custom", "My own colours" }
    };
    static final String[][] CUSTOM = {
        { "c_bg", "Background" }, { "c_past", "Past days" }, { "c_future", "Upcoming days" },
        { "c_today", "Today" }, { "c_accent", "Today outline" }, { "c_text", "Text" }
    };

    private static Pal mk(int bg, int past, int future, int today, int accent, int fg, int muted, int sun) {
        Pal p = new Pal(); p.bg = bg; p.past = past; p.future = future; p.today = today; p.accent = accent;
        p.fg = fg; p.muted = muted; p.sun = sun; return p;
    }
    static Pal preset(String id) {
        if ("dots".equals(id)) return mk(0xFF1C1918, 0xFF100D0C, 0xFF3B3D44, 0xFF5A463A, 0xFFE8C77A, 0xFFF2ECE2, 0xFFA69D92, 0xFFE08F6E);
        if ("desert".equals(id)) return mk(0xFF1A100F, 0xFF0D0807, 0xFF3D211C, 0xFF5E2A1F, 0xFFF0623F, 0xFFF5E8DC, 0xFFB79B8C, 0xFFF2B56B);
        if ("fern".equals(id)) return mk(0xFF0E130B, 0xFF070A05, 0xFF2A3420, 0xFF3D4D2A, 0xFFA9C47E, 0xFFECF2E0, 0xFF99A784, 0xFFEB906B);
        if ("gum".equals(id)) return mk(0xFF181712, 0xFF0D0C08, 0xFF2F363F, 0xFF3F4B59, 0xFFA9C7E3, 0xFFEDEFF1, 0xFF8F959B, 0xFFE59C7C);
        if ("bark".equals(id)) return mk(0xFF1A1816, 0xFF0E0C0B, 0xFF3B3129, 0xFF524232, 0xFFDDD1BB, 0xFFF3EEE5, 0xFFA1998A, 0xFFE2936F);
        if ("light".equals(id)) { Pal p = mk(0xFFE3EAE5, 0xFFC9D3CD, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF2E7D57, 0xFF14201A, 0xFF5A6B62, 0xFFC2352B); p.dark = false; return p; }
        return mk(0xFF101A16, 0xFF070B09, 0xFF243029, 0xFF2F4238, 0xFF8FE3B0, 0xFFE6EEE9, 0xFF8FA39A, 0xFFFF6B5F);
    }
    /** Event colour sets. Each is built around hues opposite or evenly spaced from its palette's tile colour,
     *  at similar brightness, so neighbouring events are easy to tell apart but still sit well together. */
    static final String[][] SETS = {
        { "auto", "Follow my calendar palette" }, { "bush", "Bush green" }, { "dots", "Dot painting" }, { "desert", "Red desert" },
        { "fern", "Rainforest fern" }, { "gum", "Gum tree and sky" }, { "bark", "Pale bark" }, { "bold", "Bold (strongest contrast)" }, { "cyber", "Cyberpunk" }
    };
    /** The 30 Cyberpunk swatches in the order of Ben's reference image (3 rows of 10). */
    static final String[] CYBER = {
        "#B4E665", "#729C63", "#A6D4A2", "#6FE254", "#36752A", "#82EA92", "#68DC80", "#454B49", "#44926B", "#FFA147",
        "#ECB17B", "#59B09E", "#265551", "#6A96BE", "#446FB2", "#A0B3D9", "#1A22C8", "#150F52", "#6B58E4", "#30127F",
        "#681FD9", "#62189A", "#9A2BE0", "#CB3BE3", "#D75FD3", "#A44694", "#E191C8", "#782A4C", "#49142A", "#DF6E80" };
    /** Same colours, reordered so neighbouring prefixes get clearly different hues and the darkest come last. */
    static final String[] CYBER_ORDER = {
        "#B4E665", "#CB3BE3", "#FFA147", "#6B58E4", "#6FE254", "#DF6E80", "#6A96BE", "#9A2BE0", "#59B09E", "#E191C8",
        "#ECB17B", "#446FB2", "#82EA92", "#D75FD3", "#A0B3D9", "#681FD9", "#68DC80", "#A44694", "#44926B", "#1A22C8",
        "#A6D4A2", "#62189A", "#729C63", "#782A4C", "#36752A", "#30127F", "#265551", "#454B49", "#49142A", "#150F52" };
    static int[] events(String id) {
        String[] h;
        if ("dots".equals(id)) h = new String[] { "#E8B84A", "#6FA8DC", "#D9654A", "#F0E6D2", "#9FC27A", "#C78BD1", "#5FBFB0", "#E89A7A" };
        else if ("desert".equals(id)) h = new String[] { "#4FC3D9", "#F5C04E", "#8FD18A", "#F0EAD6", "#B79AE8", "#FF8A5C", "#5F9BE8", "#E89AC0" };
        else if ("fern".equals(id)) h = new String[] { "#F29E4A", "#6FC6E8", "#E86A8A", "#F2E27A", "#C29AE8", "#A9E07E", "#4FD1B0", "#F0C9A0" };
        else if ("gum".equals(id)) h = new String[] { "#F2A65A", "#7FC8F2", "#E87A6A", "#E8D36A", "#9AD48A", "#C6A0E8", "#5FCFC0", "#F0B7C8" };
        else if ("bark".equals(id)) h = new String[] { "#7FB8E0", "#E8845A", "#F0D9A8", "#9FCF8F", "#D9A0D0", "#F2C04E", "#6FCFC8", "#C9B8F0" };
        else if ("light".equals(id)) h = new String[] { "#C62828", "#0277BD", "#EF6C00", "#2E7D32", "#6A1B9A", "#F9A825", "#00838F", "#5D4037" };
        else if ("cyber".equals(id)) h = CYBER_ORDER;
        else if ("bold".equals(id)) h = new String[] { "#FF5252", "#40C4FF", "#FFD740", "#69F0AE", "#E040FB", "#FF9100", "#18FFFF", "#F48FB1" };
        else h = new String[] { "#E0604A", "#4FB3D9", "#F2C14E", "#B58BE0", "#7FD49B", "#F29E6B", "#5FD0C5", "#E88BB0" };
        int[] out = new int[h.length];
        for (int i = 0; i < h.length; i++) out[i] = hex(h[i], 0xFF888888);
        return out;
    }
    static int[] events(Prefs p) {
        String e = p.s("eset", "auto");
        if ("custom".equals(e)) {   // the desktop panel's own list, e.g. "#FF0000,#00FF00"
            List<Integer> out = new ArrayList<Integer>();
            for (String h : p.s("x_set", "").split("[,\\s]+")) { int c = hex(h, 0); if (!h.trim().isEmpty() && c != 0) out.add(c); }
            if (!out.isEmpty()) { int[] a = new int[out.size()]; for (int i = 0; i < a.length; i++) a[i] = out.get(i); return a; }
            e = "auto";
        }
        return events("auto".equals(e) ? p.pal() : e);
    }
    static Pal of(Prefs p) {
        Pal c = base(p);
        c.now = c.sun; c.dow = c.muted; c.sunday = c.sun; c.head = c.fg; c.num = c.fg; c.evText = c.fg;
        c.cancel = 0xFF7D8290; c.more = c.muted; c.time = c.dark ? 0xFFFFFFFF : 0xFF000000; c.mkText = 0xFFFFFFFF;
        try {
            c.bg = hex(p.s("x_bg", ""), c.bg); c.past = hex(p.s("x_past", ""), c.past); c.future = hex(p.s("x_future", ""), c.future);
            c.today = hex(p.s("x_today", ""), c.today); c.accent = hex(p.s("x_accent", ""), c.accent);
            int fg0 = c.fg; c.fg = hex(p.s("x_fg", ""), c.fg);
            if (c.fg != fg0) { c.head = c.fg; c.num = c.fg; c.evText = c.fg; }
            c.muted = hex(p.s("x_muted", ""), c.muted); c.dow = c.muted; c.more = c.muted;
            int sun0 = c.sun; c.sun = hex(p.s("x_sun", ""), c.sun);
            if (c.sun != sun0) { c.now = c.sun; c.sunday = c.sun; }
            c.now = hex(p.s("x_now", ""), c.now); c.dow = hex(p.s("x_dow", ""), c.dow); c.sunday = hex(p.s("x_sunday", ""), c.sunday);
            c.head = hex(p.s("x_head", ""), c.head); c.num = hex(p.s("x_num", ""), c.num); c.evText = hex(p.s("x_evtext", ""), c.evText);
            c.cancel = hex(p.s("x_cancel", ""), c.cancel); c.more = hex(p.s("x_more", ""), c.more);
            if (!p.s("x_time", "").trim().isEmpty()) { c.time = hex(p.s("x_time", ""), c.time); c.timeAuto = false; }
            if (!p.s("x_mktext", "").trim().isEmpty()) { c.mkText = hex(p.s("x_mktext", ""), c.mkText); c.mkAuto = false; }
            if (!p.on("sunred", true)) { c.sunday = c.dow; }
        } catch (Exception ignored) { }
        return c;
    }
    private static Pal base(Prefs p) {
        String id = p.pal();
        if (!"custom".equals(id)) return preset(id);
        Pal b = preset("bush"), c = new Pal();
        c.bg = hex(p.s("c_bg", ""), b.bg); c.past = hex(p.s("c_past", ""), b.past); c.future = hex(p.s("c_future", ""), b.future);
        c.today = hex(p.s("c_today", ""), b.today); c.accent = hex(p.s("c_accent", ""), b.accent); c.fg = hex(p.s("c_text", ""), b.fg);
        c.dark = lum(c.future) < 0.5;
        c.muted = mix(c.fg, c.future, 0.4f);
        c.sun = c.dark ? 0xFFFF6B5F : 0xFFC2352B;
        return c;
    }
    static int hex(String s, int def) {
        try { s = s.trim(); return Color.parseColor(s.startsWith("#") ? s : "#" + s) | 0xFF000000; }
        catch (Exception e) { return def; }
    }
    static String str(int c) { return String.format("#%06X", c & 0xFFFFFF); }
    static double lum(int c) { return (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0; }
    static int mix(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) * (1 - t) + Color.red(b) * t), Math.round(Color.green(a) * (1 - t) + Color.green(b) * t),
            Math.round(Color.blue(a) * (1 - t) + Color.blue(b) * t));
    }
    static int alpha(int c, int a) { return (c & 0x00FFFFFF) | (a << 24); }
}
