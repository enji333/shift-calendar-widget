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
    /** Weekend day boxes (when tinted), days outside the month, and the grid lines between boxes. */
    int wkend, out, grid; boolean hasExtras;
    /** Pen colour for hand-drawn linework. */
    int ink;
    /** Colour wash (top and bottom) and lettering effect colours (v3.11). */
    int wash1, wash2, tfill, tline; boolean tfillAuto = true;
    boolean timeAuto = true, mkAuto = true;
    /** Keys the desktop panel can set to override any single colour, whatever palette is chosen. */
    static final String[] OVERRIDES = { "x_bg", "x_past", "x_future", "x_today", "x_accent", "x_fg", "x_muted", "x_sun",
        "x_now", "x_dow", "x_sunday", "x_head", "x_num", "x_evtext", "x_time", "x_cancel", "x_more", "x_mktext",
        "x_wkend", "x_grid", "x_out", "x_ink", "x_wash1", "x_wash2", "x_tfill", "x_tline" };

    static final String[][] PRESETS = {
        { "bush", "Bush green" }, { "dots", "Dot painting (slate and ochre)" }, { "desert", "Red desert" },
        { "fern", "Rainforest fern" }, { "gum", "Gum tree and sky" }, { "bark", "Pale bark on dark rock" },
        { "light", "Light" },
        { "oday", "WA outback: Day (bone, sky, pindan red)" }, { "odusk", "WA outback: Dusk (night indigo, lilac, spinifex)" },
        { "oscrub", "WA outback: Scrub (saltbush, mulga, Pilbara iron)" }, { "osalt", "WA outback: Salt lake (pink, lilac, open sky)" },
        { "opilbara", "WA outback: Pilbara night (iron red, spinifex)" },
        { "psea", "Ink & pastel: Sea (aqua, cream, lilac, orange)" }, { "plilac", "Ink & pastel: Lilac (lilac, pink, cream, orange)" },
        { "psand", "Ink & pastel: Sand (cream, coral, sky)" }, { "pnight", "Ink & pastel: Night (dusty blue, lilac, gold)" },
        { "cjungle", "Comic: Jungle (moss and sage greens, pink, red)" }, { "cwave", "Comic: Wave (cream, peach, mint, teal, orange)" },
        { "cviolet", "Comic: Violet city (violet, mauve, sand, gold)" }, { "cmauve", "Comic: Mauve (sand, mauve, plum)" },
        { "cstair", "Comic: Blue stair (deep steel blue, cream)" }, { "cice", "Comic: Ice (ice blue, mint, red, blue)" },
        { "csea", "Comic: Bright sea (sky blue, white, orange)" },
        { "sfteal", "Sci-fi: Teal night (muted teal, salmon, cream)" }, { "sfsalmon", "Sci-fi: Salmon dust (peach, teal, mint)" },
        { "sflilac", "Comic page: Lilac (lavender, red-orange)" }, { "sfline", "Clean line (white paper, sand)" },
        { "riso", "Riso poster (cream, orange, cyan, lime)" }, { "sfspace", "Space print (dark brown, lime, rust)" },
        { "bubble", "Bubble letters (cream, green, black)" }
    };
    /** The WA bush and outback palette, by name, in the order of Ben's palette sheet. */
    static final String[][] OUTBACK = {
        { "#B5452C", "Pindan" }, { "#7A2E22", "Pilbara iron" }, { "#E3A587", "Salmon gum" }, { "#D9B45A", "Spinifex" },
        { "#9DAA94", "Saltbush" }, { "#6E7352", "Mulga" }, { "#EDE3D0", "Wandoo bone" }, { "#F0C6C0", "Salt lake pink" },
        { "#B9A3C9", "Dusk lilac" }, { "#6FC3C4", "Open sky" }, { "#2B2F55", "Night indigo" } };
    /** v3.6: the old "My own colours" base colours (c_*) became ordinary colour overrides (x_*), so one set of
     *  colour controls does everything. Runs once; force = again (settings just arrived from an older panel). */
    static final String[][] OLD_CUSTOM = { { "c_bg", "x_bg" }, { "c_past", "x_past" }, { "c_future", "x_future" },
        { "c_today", "x_today" }, { "c_accent", "x_accent" }, { "c_text", "x_fg" } };
    static void migrate(Prefs p, boolean force) {
        if (!force && p.i("mig36", 0) == 1) return;
        android.content.SharedPreferences.Editor e = p.sp.edit();
        if ("custom".equals(p.s("pal", ""))) {
            Pal b = preset("bush");
            int fut = hex(p.s("c_future", ""), b.future), fg = hex(p.s("c_text", ""), b.fg);
            for (String[] m : OLD_CUSTOM) if (p.s(m[1], "").trim().isEmpty() && !p.s(m[0], "").trim().isEmpty()) e.putString(m[1], p.s(m[0], ""));
            boolean dk = lum(fut) < 0.5;
            if (p.s("x_muted", "").trim().isEmpty()) e.putString("x_muted", str(mix(fg, fut, 0.4f)));
            if (p.s("x_sunday", "").trim().isEmpty()) e.putString("x_sunday", str(dk ? 0xFFFF6B5F : 0xFFC2352B));
            if (p.s("x_now", "").trim().isEmpty()) e.putString("x_now", str(dk ? 0xFFFF6B5F : 0xFFC2352B));
            e.putString("pal", "bush");
        }
        for (String[] m : OLD_CUSTOM) e.remove(m[0]);
        e.putInt("mig36", 1).commit();
    }

    private static Pal mk(int bg, int past, int future, int today, int accent, int fg, int muted, int sun) {
        Pal p = new Pal(); p.bg = bg; p.past = past; p.future = future; p.today = today; p.accent = accent;
        p.fg = fg; p.muted = muted; p.sun = sun; return p;
    }
    private static Pal ex(Pal p, boolean dark, int wkend, int out, int grid) { p.dark = dark; p.wkend = wkend; p.out = out; p.grid = grid; p.hasExtras = true; return p; }
    static Pal preset(String id) {
        // WA bush and outback palettes, built from the pairings on Ben's palette sheet
        if ("oday".equals(id)) return ex(mk(0xFFEDE3D0, 0xFFDED6C2, 0xFFF6F0E4, 0xFFD8EEEC, 0xFFB5452C, 0xFF2B2F55, 0xFF6E7352, 0xFFB5452C), false, 0xFFF0C6C0, 0xFFD2CFBC, 0xFF2B2F55);
        if ("odusk".equals(id)) return ex(mk(0xFF2B2F55, 0xFF20234A, 0xFF3A3E6A, 0xFF51497A, 0xFFD9B45A, 0xFFEDE3D0, 0xFFB9A3C9, 0xFFF0C6C0), true, 0xFF4F4370, 0xFF2E3152, 0xFF181A38);
        if ("oscrub".equals(id)) return ex(mk(0xFFDCDCC8, 0xFFCFD2C0, 0xFFEDE3D0, 0xFFF7F1E3, 0xFF7A2E22, 0xFF3A3D2A, 0xFF6E7352, 0xFF7A2E22), false, 0xFFE9DCB0, 0xFFC9CCBA, 0xFF6E7352);
        if ("osalt".equals(id)) return ex(mk(0xFFF0C6C0, 0xFFE6D6DA, 0xFFF7EFE8, 0xFFD6EEEE, 0xFF2B2F55, 0xFF2B2F55, 0xFF7E6B92, 0xFF8A6FA6), false, 0xFFE6DCEE, 0xFFDCCFD3, 0xFF2B2F55);
        if ("psea".equals(id)) return ex(mk(0xFFBFE3E4, 0xFFA9D3D6, 0xFFEAF5F2, 0xFFFFF6E8, 0xFFE2763A, 0xFF1D1A1F, 0xFF4F6E78, 0xFFC65A3A), false, 0xFFDAD3EC, 0xFFB7CDD3, 0xFF1D1A1F);
        if ("plilac".equals(id)) return ex(mk(0xFFD8CDE6, 0xFFC6BAD8, 0xFFF2ECF4, 0xFFFFF1DC, 0xFFE58A3A, 0xFF1D1A1F, 0xFF6B5E80, 0xFFC9567A), false, 0xFFF3D2D6, 0xFFCFC6DB, 0xFF1D1A1F);
        if ("psand".equals(id)) return ex(mk(0xFFF6E7D8, 0xFFEAD8C6, 0xFFFBF4EA, 0xFFE4F4F3, 0xFFE2763A, 0xFF1D1A1F, 0xFF7E6A5A, 0xFFD0603F), false, 0xFFF3D2D6, 0xFFE2D6C8, 0xFF1D1A1F);
        if ("pnight".equals(id)) return ex(mk(0xFF3E4466, 0xFF30355A, 0xFF4C5379, 0xFF5F5A86, 0xFFF2A65A, 0xFFF6F1E6, 0xFFC3B5DD, 0xFFF3B9C2), true, 0xFF5A4F7E, 0xFF3A3F60, 0xFF15131A);
        if ("cjungle".equals(id)) return ex(mk(0xFF2F3A2E, 0xFF242926, 0xFF4A5A47, 0xFF607C59, 0xFFE8B4C8, 0xFFE2E0C7, 0xFFAAC18B, 0xFFE86A5A), true, 0xFF566B4E, 0xFF2E352C, 0xFF151A14);
        if ("cwave".equals(id)) return ex(mk(0xFFF6DBAB, 0xFFE5E9CF, 0xFFF4EABD, 0xFFADD8C5, 0xFF156773, 0xFF1B1D24, 0xFF2B585B, 0xFFE8743B), false, 0xFFBFE3EC, 0xFFE2D9BE, 0xFF1B1D24);
        if ("cviolet".equals(id)) return ex(mk(0xFF4F4B5A, 0xFF3E3A48, 0xFF6F6985, 0xFF8C82A5, 0xFFE3B87A, 0xFFF3EBE3, 0xFFCAB19F, 0xFFF0A98A), true, 0xFF796A82, 0xFF4A4556, 0xFF2B292F);
        if ("cmauve".equals(id)) return ex(mk(0xFFD6C7B7, 0xFFC5BFC8, 0xFFE7D7BC, 0xFFF2E6D2, 0xFF473340, 0xFF2B222A, 0xFF67515A, 0xFFA8574C), false, 0xFFD5B6A2, 0xFFC8C1C8, 0xFF2B222A);
        if ("cstair".equals(id)) return ex(mk(0xFF39637C, 0xFF1A3447, 0xFF4E8197, 0xFF618E9C, 0xFFF2E6B8, 0xFFF0F3F4, 0xFFADB0B4, 0xFFE8C77A), true, 0xFF3E7496, 0xFF40565F, 0xFF0F1E29);
        if ("cice".equals(id)) return ex(mk(0xFFBDE1E9, 0xFFB5D5D9, 0xFFDAE9EB, 0xFFFFFFFF, 0xFF3A5BA8, 0xFF1D1A1F, 0xFF8087A0, 0xFFD9583B), false, 0xFFB2DCCC, 0xFFC6D4DF, 0xFF1D1A1F);
        if ("csea".equals(id)) return ex(mk(0xFF53B0DB, 0xFF8CC3DD, 0xFFDCEFF7, 0xFFFFFFFF, 0xFFE58A3A, 0xFF12303F, 0xFF4E7286, 0xFFD9583B), false, 0xFFB6D0DD, 0xFFADBFCB, 0xFF12303F);
        // print and poster palettes (v3.11), muted flat colours like European sci-fi comics and risograph posters
        if ("sfteal".equals(id)) return ex(mk(0xFF1E3434, 0xFF172828, 0xFF2C4A47, 0xFF3E6560, 0xFFE4A28A, 0xFFEFE3C8, 0xFF9DB5A6, 0xFFE07A5F), true, 0xFF35524C, 0xFF263B39, 0xFF0F1E1D);
        if ("sfsalmon".equals(id)) return ex(mk(0xFFE9C7AE, 0xFFDDB9A0, 0xFFF3E1CC, 0xFFCFE3D6, 0xFF2F5D5A, 0xFF1F2A2A, 0xFF5E7068, 0xFFC4553E), false, 0xFFEAD0C0, 0xFFDCC7B3, 0xFF1F2A2A);
        if ("sflilac".equals(id)) return ex(mk(0xFFC9C2E6, 0xFFBAB1D8, 0xFFF1EEF8, 0xFFFBE6D2, 0xFFC8462E, 0xFF1D1A24, 0xFF6A5F86, 0xFFC8462E), false, 0xFFE6D3E8, 0xFFCFC8E3, 0xFF1D1A24);
        if ("sfline".equals(id)) return ex(mk(0xFFF7F1E6, 0xFFEDE4D3, 0xFFFFFDF8, 0xFFF3E3C6, 0xFFB8743E, 0xFF2A2420, 0xFF8A7B68, 0xFFB8743E), false, 0xFFF4EADA, 0xFFEFE8DC, 0xFF2A2420);
        if ("riso".equals(id)) { Pal r = ex(mk(0xFFF4E9C8, 0xFFE7DCB8, 0xFFFAF4E0, 0xFFD8F0EE, 0xFFE8603C, 0xFF1C1C1C, 0xFF5E6B5A, 0xFFE8603C), false, 0xFFF6D9B5, 0xFFECE3C4, 0xFF1C1C1C); r.wash1 = 0xFFF6A97A; r.wash2 = 0xFFCFE58A; return r; }
        if ("sfspace".equals(id)) return ex(mk(0xFF241B16, 0xFF1A1310, 0xFF33271F, 0xFF4A3A2A, 0xFFC9D85A, 0xFFEDE2CF, 0xFFA8957E, 0xFFD9744A), true, 0xFF3D2C22, 0xFF2A201A, 0xFF0D0907);
        if ("bubble".equals(id)) return ex(mk(0xFFFBF5E4, 0xFFEFE7D0, 0xFFFFFBF0, 0xFFE2F0DA, 0xFF6DAA62, 0xFF161616, 0xFF6B6B5E, 0xFFE2553E), false, 0xFFF1EAD2, 0xFFEDE6D2, 0xFF161616);
        if ("opilbara".equals(id)) return ex(mk(0xFF3A1913, 0xFF26100C, 0xFF5A2820, 0xFF7A2E22, 0xFFD9B45A, 0xFFEDE3D0, 0xFFE3A587, 0xFF6FC3C4), true, 0xFF7F3424, 0xFF3F1D17, 0xFF1E0C09);
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
        { "fern", "Rainforest fern" }, { "gum", "Gum tree and sky" }, { "bark", "Pale bark" }, { "bold", "Bold (strongest contrast)" }, { "cyber", "Cyberpunk" },
        { "outback", "WA outback (all 11 colours)" }, { "oday", "WA outback: Day" }, { "odusk", "WA outback: Dusk" },
        { "oscrub", "WA outback: Scrub" }, { "osalt", "WA outback: Salt lake" }, { "opilbara", "WA outback: Pilbara night" },
        { "pastel", "Ink & pastel" }, { "comic", "Comic colours (from your art pictures)" },
        { "sfteal", "Sci-fi: Teal night" }, { "sfsalmon", "Sci-fi: Salmon dust" }, { "sflilac", "Comic page: Lilac" }, { "sfline", "Clean line" },
        { "riso", "Riso poster" }, { "sfspace", "Space print" }, { "bubble", "Bubble letters" }
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
    /** Colours picked from Ben's comic-art pictures (jungle, wave, violet city, mauve, blue stair, ice, bright sea). */
    static final String[] COMIC = {
        "#242926", "#4A5142", "#527053", "#607C59", "#7D9F70", "#AAC18B", "#E2E0C7", "#E8B4C8", "#E86A5A",
        "#F4EABD", "#F6DBAB", "#ADD8C5", "#8EB2A0", "#49A6AD", "#156773", "#2B585B", "#E8743B", "#3A6FC4",
        "#4F4B5A", "#6F6985", "#8C82A5", "#6D5D6D", "#B39A8F", "#CAB19F", "#E3B87A", "#473340", "#67515A", "#AC857D", "#D5B6A2", "#E7D7BC",
        "#1A3447", "#39637C", "#4E8197", "#618E9C", "#ADB0B4", "#BDE1E9", "#B2DCCC", "#DAE9EB", "#8087A0", "#D9583B",
        "#53B0DB", "#7CBFDA", "#B6D0DD", "#748998", "#E58A3A", "#FDFDFC" };
    static final String[] COMIC_ORDER = { "#E8743B", "#156773", "#E8B4C8", "#3A6FC4", "#AAC18B", "#AC857D", "#53B0DB", "#E3B87A",
        "#8C82A5", "#E86A5A", "#49A6AD", "#607C59", "#D9583B", "#39637C", "#CAB19F", "#473340" };
    static int[] events(String id) {
        String[] h;
        if ("dots".equals(id)) h = new String[] { "#E8B84A", "#6FA8DC", "#D9654A", "#F0E6D2", "#9FC27A", "#C78BD1", "#5FBFB0", "#E89A7A" };
        else if ("desert".equals(id)) h = new String[] { "#4FC3D9", "#F5C04E", "#8FD18A", "#F0EAD6", "#B79AE8", "#FF8A5C", "#5F9BE8", "#E89AC0" };
        else if ("fern".equals(id)) h = new String[] { "#F29E4A", "#6FC6E8", "#E86A8A", "#F2E27A", "#C29AE8", "#A9E07E", "#4FD1B0", "#F0C9A0" };
        else if ("gum".equals(id)) h = new String[] { "#F2A65A", "#7FC8F2", "#E87A6A", "#E8D36A", "#9AD48A", "#C6A0E8", "#5FCFC0", "#F0B7C8" };
        else if ("bark".equals(id)) h = new String[] { "#7FB8E0", "#E8845A", "#F0D9A8", "#9FCF8F", "#D9A0D0", "#F2C04E", "#6FCFC8", "#C9B8F0" };
        else if ("light".equals(id)) h = new String[] { "#C62828", "#0277BD", "#EF6C00", "#2E7D32", "#6A1B9A", "#F9A825", "#00838F", "#5D4037" };
        else if ("cyber".equals(id)) h = CYBER_ORDER;
        else if ("outback".equals(id)) h = new String[] { "#B5452C", "#6FC3C4", "#D9B45A", "#B9A3C9", "#6E7352", "#E3A587", "#9DAA94", "#7A2E22", "#F0C6C0", "#2B2F55", "#EDE3D0" };
        else if ("oday".equals(id)) h = new String[] { "#B5452C", "#6FC3C4", "#D9B45A", "#6E7352", "#B9A3C9", "#7A2E22", "#9DAA94", "#E3A587", "#2B2F55" };
        else if ("odusk".equals(id)) h = new String[] { "#D9B45A", "#6FC3C4", "#F0C6C0", "#9DAA94", "#E3A587", "#B9A3C9", "#EDE3D0", "#B5452C" };
        else if ("oscrub".equals(id)) h = new String[] { "#7A2E22", "#D9B45A", "#6E7352", "#6FC3C4", "#B5452C", "#9DAA94", "#B9A3C9", "#E3A587" };
        else if ("osalt".equals(id)) h = new String[] { "#6FC3C4", "#B9A3C9", "#2B2F55", "#B5452C", "#D9B45A", "#6E7352", "#E3A587", "#9DAA94" };
        else if ("pastel".equals(id) || "psea".equals(id) || "plilac".equals(id) || "psand".equals(id)) h = new String[] { "#E2763A", "#5FB8C2", "#9C86C9", "#E8B44E", "#6E9C8E", "#D9667E", "#4F5C8F", "#C98A5E", "#86B6D9" };
        else if ("comic".equals(id)) h = COMIC_ORDER;
        else if ("cjungle".equals(id)) h = new String[] { "#AAC18B", "#E8B4C8", "#F2D16B", "#E86A5A", "#E2E0C7", "#7FC6C0", "#F2A65A", "#C9A0DC" };
        else if ("cwave".equals(id)) h = new String[] { "#156773", "#E8743B", "#49A6AD", "#3A6FC4", "#8EB2A0", "#2B585B", "#D9583B", "#646159" };
        else if ("cviolet".equals(id)) h = new String[] { "#E3B87A", "#F0A98A", "#A9C7E3", "#CAB19F", "#E191C8", "#8FD1B0", "#F2E6B8", "#B39A8F" };
        else if ("cmauve".equals(id)) h = new String[] { "#473340", "#AC857D", "#5B7FA6", "#A8574C", "#7A7384", "#8C6D3E", "#6B8F71", "#67515A" };
        else if ("cstair".equals(id)) h = new String[] { "#F2E6B8", "#E8C77A", "#ADD8C5", "#F0A98A", "#BDE1E9", "#E191C8", "#CAB19F", "#95B6C9" };
        else if ("cice".equals(id)) h = new String[] { "#3A5BA8", "#D9583B", "#49A6AD", "#E8B44E", "#6E5AA8", "#8087A0", "#2B585B", "#95B6C9" };
        else if ("csea".equals(id)) h = new String[] { "#1A6FA0", "#E58A3A", "#156773", "#D9583B", "#6E5AA8", "#2B585B", "#E8B44E", "#748998" };
        else if ("sfteal".equals(id)) h = new String[] { "#E4A28A", "#EAD9B8", "#8FC1B5", "#D9B45A", "#C98A9B", "#9DB27A", "#F0C6A8", "#7FA6C9" };
        else if ("sfsalmon".equals(id)) h = new String[] { "#2F5D5A", "#C4553E", "#6F8A5B", "#D99A3E", "#4E6E8E", "#A8574C", "#3E7F78", "#8C6D3E" };
        else if ("sflilac".equals(id)) h = new String[] { "#C8462E", "#D98A3E", "#4F7FB0", "#7A9A5A", "#B8743E", "#8E5AA8", "#2F7F86", "#D9B45A" };
        else if ("sfline".equals(id)) h = new String[] { "#B8743E", "#5F8FA8", "#C9A25A", "#8A6F5A", "#A8574C", "#6E8A6A", "#D9B48A", "#4F5C78" };
        else if ("riso".equals(id)) h = new String[] { "#E8603C", "#2FA4C8", "#F2C14E", "#3E9E6A", "#E86A9A", "#4F5CC8", "#F29E4A", "#6FC3C4" };
        else if ("sfspace".equals(id)) h = new String[] { "#C9D85A", "#D9744A", "#E8D6B0", "#7FB8B0", "#E0A85A", "#B98AC0", "#9FC27A", "#F0B7A0" };
        else if ("bubble".equals(id)) h = new String[] { "#6DAA62", "#E2553E", "#F2C14E", "#4F8FD0", "#E88BB0", "#F29E4A", "#5FC0B0", "#A07ACF" };
        else if ("pnight".equals(id)) h = new String[] { "#F2A65A", "#7FCFD6", "#F3B9C2", "#B4CFC4", "#C3B5DD", "#EBCB86", "#F1C3A6", "#9FB3E8" };
        else if ("opilbara".equals(id)) h = new String[] { "#6FC3C4", "#D9B45A", "#EDE3D0", "#9DAA94", "#B9A3C9", "#F0C6C0", "#E3A587", "#6E7352" };
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
    /** Colours for all-day events. "same" = like timed events, "auto" = the palette's set turned half way round,
     *  so all-day markers and timed events differ; any set id; or "custom" (x_set_ad). */
    static int[] eventsAllDay(Prefs p) {
        String e = p.s("eset_ad", "same");
        if ("same".equals(e) || e.isEmpty()) return null;
        if ("custom".equals(e)) {
            List<Integer> out = new ArrayList<Integer>();
            for (String h : p.s("x_set_ad", "").split("[,\\s]+")) { int c = hex(h, 0); if (!h.trim().isEmpty() && c != 0) out.add(c); }
            if (out.isEmpty()) return null;
            int[] a = new int[out.size()]; for (int i = 0; i < a.length; i++) a[i] = out.get(i); return a;
        }
        if ("auto".equals(e)) {
            int[] b = events(p), r = new int[b.length];
            for (int i = 0; i < b.length; i++) r[i] = b[(i + b.length / 2) % b.length];
            return r;
        }
        return events(e);
    }
    static Pal of(Prefs p) {
        Pal c = base(p);
        // a hand-picked day box colour decides whether the derived colours are worked out for a dark or a light widget
        if (!p.s("x_future", "").trim().isEmpty()) { int f0 = c.future; c.future = hex(p.s("x_future", ""), c.future); if (c.future != f0) c.dark = lum(c.future) < 0.5; }
        c.now = c.sun; c.dow = c.muted; c.sunday = c.sun; c.head = c.fg; c.num = c.fg; c.evText = c.fg;
        c.cancel = 0xFF7D8290; c.more = c.muted; c.time = c.dark ? 0xFFFFFFFF : 0xFF000000; c.mkText = 0xFFFFFFFF;
        c.ink = c.dark ? mix(c.fg, c.future, 0.45f) : 0xFF1D1A1F;
        if (c.wash1 == 0) { c.wash1 = c.accent; c.wash2 = c.today; }
        c.tline = c.dark ? 0xFF111111 : c.ink;
        c.tfill = c.dark ? c.fg : (lum(c.accent) < 0.75 ? c.accent : c.today);
        if (!c.hasExtras) { c.wkend = mix(c.future, c.sun, c.dark ? 0.18f : 0.12f); c.out = mix(c.future, c.past, 0.5f); c.grid = mix(c.bg, c.fg, c.dark ? 0.25f : 0.6f); }
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
            c.wkend = hex(p.s("x_wkend", ""), c.wkend); c.grid = hex(p.s("x_grid", ""), c.grid); c.out = mix(c.future, c.accent, c.dark ? 0.22f : 0.16f);   // next month's days: a light tint of the palette highlight, not a faded grey (v3.5)
            c.out = hex(p.s("x_out", ""), c.out); c.ink = hex(p.s("x_ink", ""), c.ink);
            c.wash1 = hex(p.s("x_wash1", ""), c.wash1); c.wash2 = hex(p.s("x_wash2", ""), c.wash2); c.tline = hex(p.s("x_tline", ""), c.dark ? c.tline : c.ink);
            if (!p.s("x_tfill", "").trim().isEmpty()) { c.tfill = hex(p.s("x_tfill", ""), c.tfill); c.tfillAuto = false; }
            if (!p.on("sunred", true)) { c.sunday = c.dow; }
        } catch (Exception ignored) { }
        return c;
    }
    private static Pal base(Prefs p) {
        String id = p.pal();
        return preset("custom".equals(id) ? "bush" : id);   // "custom" only exists before the v3.6 migration
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
