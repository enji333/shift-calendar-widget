package com.shiftcal.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/** WA outback look: header scenes, retro title fonts and one-tap themes. */
final class Look {
    /** Header landscapes, drawn in the outback palette (res/drawable/scene_*.xml, made by tools/scenes.py). */
    static final String[][] SCENES = {
        { "", "No picture" }, { "grass", "Grass trees and gums" }, { "pinnacles", "Pinnacles at night" },
        { "saltlake", "Salt lake sunset" }, { "mesas", "Pilbara mesas" } };
    static int sceneRes(String id) { return sceneRes(id, false); }
    /** ink = the hand-inked pastel version (thin wobbly pen lines, hatching, stipple). */
    static int sceneRes(String id, boolean ink) {
        if ("grass".equals(id)) return ink ? R.drawable.scene_grass_ink : R.drawable.scene_grass;
        if ("pinnacles".equals(id)) return ink ? R.drawable.scene_pinnacles_ink : R.drawable.scene_pinnacles;
        if ("saltlake".equals(id)) return ink ? R.drawable.scene_saltlake_ink : R.drawable.scene_saltlake;
        if ("mesas".equals(id)) return ink ? R.drawable.scene_mesas_ink : R.drawable.scene_mesas;
        return 0;
    }
    /** Rounded-corner shapes come in fixed sizes (a widget can't change a corner radius on the fly). */
    static final int[] RADII = { 0, 2, 4, 6, 8, 12, 16, 24, 99 };
    static int rr(float dp) {
        int[] res = { R.drawable.rr_0, R.drawable.rr_2, R.drawable.rr_4, R.drawable.rr_6, R.drawable.rr_8, R.drawable.rr_12, R.drawable.rr_16, R.drawable.rr_24, R.drawable.rr_99 };
        if (dp >= 25) return res[RADII.length - 1];
        int best = 0;
        for (int i = 0; i < RADII.length; i++) if (Math.abs(RADII[i] - dp) < Math.abs(RADII[best] - dp)) best = i;
        return res[best];
    }

    /** Retro display fonts for the widget title (all SIL Open Font License, see assets/fonts/LICENSES.txt). */
    static final String[][] FONTS = {
        { "", "Phone font (as before)" }, { "archivoblack", "Archivo Black (bold poster sans)" }, { "shrikhand", "Shrikhand (70s retro)" },
        { "righteous", "Righteous (rounded retro)" }, { "alfaslabone", "Alfa Slab One (vintage slab)" }, { "limelight", "Limelight (art deco)" },
        { "bungee", "Bungee (signage)" }, { "monoton", "Monoton (neon lines)" }, { "patrickhand", "Patrick Hand (hand lettering)" },
        // hand-lettered and comic fonts (v3.3)
        { "comicneue", "Comic Neue (comic lettering)" }, { "gloriahallelujah", "Gloria Hallelujah (comic hand)" },
        { "architectsdaughter", "Architects Daughter (drafting hand)" }, { "caveat", "Caveat (quick handwriting)" },
        { "indieflower", "Indie Flower (round handwriting)" }, { "gochihand", "Gochi Hand (felt pen)" },
        { "shadowsintolight", "Shadows Into Light (thin pen)" }, { "permanentmarker", "Permanent Marker (thick marker)" } };
    /** The phone's own font families. Widget text can use these directly; the fonts above are drawn as pictures. */
    static final String[][] SYS = {
        { "sans-serif-condensed", "Phone: condensed (as before)" }, { "sans-serif-condensed-light", "Phone: condensed light" },
        { "sans-serif", "Phone: regular" }, { "sans-serif-light", "Phone: light" }, { "sans-serif-thin", "Phone: thin" },
        { "sans-serif-medium", "Phone: medium" }, { "sans-serif-black", "Phone: black (heavy)" }, { "serif", "Phone: serif" },
        { "monospace", "Phone: typewriter" }, { "casual", "Phone: hand-lettered" }, { "cursive", "Phone: script" },
        { "sans-serif-smallcaps", "Phone: small capitals" } };
    static boolean isAsset(String id) {
        if (id == null || id.isEmpty()) return false;
        for (String[] f : FONTS) if (f[0].equals(id)) return true;
        return false;
    }
    /** Any font id (picture font or phone family) as a Typeface for drawing. */
    static Typeface any(Context c, String id, boolean bold) {
        Typeface t = isAsset(id) ? font(c, id) : Typeface.create(id == null || id.isEmpty() ? "sans-serif-condensed" : id, Typeface.NORMAL);
        if (t == null) t = Typeface.DEFAULT;
        return bold ? Typeface.create(t, Typeface.BOLD) : t;
    }
    /** A short label (a date, a weekday) drawn as a picture in any font. Shrinks to fit maxWDp. */
    static Bitmap label(Context c, Typeface tf, String text, int col, float sizeDp, float hDp, float maxWDp, float spacing, boolean fakeBold) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        float d = dm.density;
        Paint pm = new Paint(Paint.ANTI_ALIAS_FLAG); pm.setTypeface(tf); pm.setTextSize(sizeDp * d); pm.setColor(col);
        pm.setLetterSpacing(spacing); pm.setFakeBoldText(fakeBold && !tf.isBold());
        float w = pm.measureText(text) + 2 * d, maxW = Math.max(4, maxWDp * d);
        if (w > maxW) { pm.setTextSize(pm.getTextSize() * maxW / w); w = maxW; }
        int W = Math.max(1, (int) Math.ceil(w)), H = Math.max(1, Math.round(hDp * d));
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        b.setDensity(dm.densityDpi);
        Paint.FontMetrics fm = pm.getFontMetrics();
        new Canvas(b).drawText(text, d, H / 2f - (fm.ascent + fm.descent) / 2f, pm);
        return b;
    }
    /** The weekday names as one picture strip: equal columns, each name centred. */
    static Bitmap strip(Context c, Typeface tf, String[] text, int[] cols, float sizeDp, float wDp, float hDp, float spacing) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        float d = Math.min(2.5f, dm.density);
        int W = Math.max(1, Math.round(wDp * d)), H = Math.max(1, Math.round(hDp * d));
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint pm = new Paint(Paint.ANTI_ALIAS_FLAG); pm.setTypeface(tf); pm.setTextSize(sizeDp * d); pm.setLetterSpacing(spacing); pm.setTextAlign(Paint.Align.CENTER);
        float cw = (float) W / text.length, widest = 0;
        for (String t : text) widest = Math.max(widest, pm.measureText(t));
        if (widest > cw - 2 * d) pm.setTextSize(pm.getTextSize() * (cw - 2 * d) / widest);
        Paint.FontMetrics fm = pm.getFontMetrics();
        float y = H / 2f - (fm.ascent + fm.descent) / 2f;
        for (int i = 0; i < text.length; i++) { pm.setColor(cols[i]); cv.drawText(text[i], cw * i + cw / 2, y, pm); }
        return b;
    }
    private static final Map<String, Typeface> CACHE = new HashMap<String, Typeface>();
    static Typeface font(Context c, String id) {
        if (id == null || id.isEmpty()) return null;
        synchronized (CACHE) {
            if (CACHE.containsKey(id)) return CACHE.get(id);
            Typeface t = null;
            try { t = Typeface.createFromAsset(c.getAssets(), "fonts/" + id + ".ttf"); } catch (Exception ignored) { }
            CACHE.put(id, t);
            return t;
        }
    }

    /** The title drawn as a picture, because widgets can only use the phone's own fonts for text.
     *  main in colour col, then tail (the year) in tailCol, then an optional small arrow. */
    static Bitmap title(Context c, Typeface tf, String main, String tail, boolean arrow, int col, int tailCol, float sizeDp, float hDp, float maxWDp) {
        return title(c, tf, main, tail, arrow, col, tailCol, sizeDp, hDp, maxWDp, 0);
    }
    static Bitmap title(Context c, Typeface tf, String main, String tail, boolean arrow, int col, int tailCol, float sizeDp, float hDp, float maxWDp, float spacing) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        float d = dm.density;
        Paint pm = new Paint(Paint.ANTI_ALIAS_FLAG); pm.setTypeface(tf); pm.setTextSize(sizeDp * d); pm.setColor(col); pm.setLetterSpacing(spacing);
        Paint pt = new Paint(pm); pt.setColor(tailCol);
        Paint pa = new Paint(Paint.ANTI_ALIAS_FLAG); pa.setTypeface(Typeface.DEFAULT_BOLD); pa.setTextSize(sizeDp * d * 0.55f); pa.setColor(col);
        String a = arrow ? " ▾" : "";
        float wm = pm.measureText(main), wt = tail.isEmpty() ? 0 : pt.measureText(tail), wa = a.isEmpty() ? 0 : pa.measureText(a);
        float total = wm + wt + wa + 2 * d;
        float maxW = maxWDp * d;
        float scale = total > maxW ? maxW / total : 1f;
        if (scale < 1f) { pm.setTextSize(pm.getTextSize() * scale); pt.setTextSize(pt.getTextSize() * scale); pa.setTextSize(pa.getTextSize() * scale); wm *= scale; wt *= scale; wa *= scale; total = maxW; }
        {   // keep the letters inside the header's height too, so tall fonts are not cut off at the top (v3.9)
            Paint.FontMetrics f0 = pm.getFontMetrics(); float th = f0.descent - f0.ascent, room = hDp * d * 0.98f;
            if (th > room) { float k = room / th; pm.setTextSize(pm.getTextSize() * k); pt.setTextSize(pt.getTextSize() * k); pa.setTextSize(pa.getTextSize() * k); wm *= k; wt *= k; wa *= k; total = wm + wt + wa + 2 * d; }
        }
        int w = Math.max(1, (int) Math.ceil(total)), h = Math.max(1, Math.round(hDp * d));
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.setDensity(dm.densityDpi);
        Canvas cv = new Canvas(b);
        Paint.FontMetrics fm = pm.getFontMetrics();
        float y = h / 2f - (fm.ascent + fm.descent) / 2f;
        float x = d;
        cv.drawText(main, x, y, pm); x += wm;
        if (!tail.isEmpty()) { cv.drawText(tail, x, y, pt); x += wt; }
        if (!a.isEmpty()) cv.drawText(a, x, y, pa);
        return b;
    }

    // ----------------------------------------------------------------- themes
    /** One-tap looks built from the four pairings on the palette sheet, plus a dark red night. */
    static final String[][] THEMES = {
        { "day", "Day", "oday", "grass", "archivoblack", "flat" },
        { "dusk", "Dusk", "odusk", "pinnacles", "shrikhand", "flat" },
        { "scrub", "Scrub", "oscrub", "mesas", "alfaslabone", "flat" },
        { "salt", "Salt lake", "osalt", "saltlake", "righteous", "flat" },
        { "pilbara", "Pilbara night", "opilbara", "mesas", "bungee", "flat" },
        { "isea", "Ink: Sea", "psea", "saltlake", "patrickhand", "ink" },
        { "ililac", "Ink: Lilac", "plilac", "pinnacles", "patrickhand", "ink" },
        { "isand", "Ink: Sand", "psand", "grass", "patrickhand", "ink" },
        { "inight", "Ink: Night", "pnight", "mesas", "patrickhand", "ink" },
        { "cjungle", "Comic: Jungle", "cjungle", "grass", "patrickhand", "ink" },
        { "cwave", "Comic: Wave", "cwave", "saltlake", "patrickhand", "ink" },
        { "cviolet", "Comic: Violet city", "cviolet", "pinnacles", "patrickhand", "ink" },
        { "cice", "Comic: Ice", "cice", "mesas", "patrickhand", "ink" } };
    /** Flat themes: crisp shapes. Ink themes: hand-drawn pen lines, hatching, dots and softer corners. */
    static final Object[][] FLAT = { { "ink", 0 }, { "inkev", 0 }, { "scene_ink", 0 }, { "grid", 1 }, { "r_bg", 8 }, { "r_tile", 0 }, { "r_ev", 0 }, { "r_mk", 0 } };
    static final Object[][] INK = { { "ink", 1 }, { "inkev", 1 }, { "scene_ink", 1 }, { "grid", 0 }, { "inkbox", "grid" }, { "inkw10", 9 }, { "wobble", 4 },
        { "hatch", "lines" }, { "stip", 1 }, { "r_bg", 16 }, { "r_tile", 0 }, { "r_ev", 4 }, { "r_mk", 4 } };
    /** Settings every theme sets the same way: the wall-calendar look from the mock-up. */
    static final Object[][] COMMON = {
        { "wk", "mon" }, { "dows", "upper3" }, { "wktint", 1 }, { "satred", 1 }, { "sunred", 1 }, { "grid", 1 }, { "gridw10", 10 },
        { "numpos", "left" }, { "tyear", 1 }, { "hh", 40 }, { "hdp10", 220 }, { "scene_pos", "top" }, { "scene_h", 72 }, { "scene_op", 100 },
        { "opacity", 100 }, { "op_future", 100 }, { "op_today", 100 }, { "op_past", 100 }, { "eset", "auto" }, { "eset_ad", "auto" }, { "font", "sans-serif" } };
    /** "Keep my …" ticks: settings a look leaves alone (v3.6). Week start, opacity, fonts and code colours are kept
     *  unless the tick is taken off; hand-picked colours are replaced unless "keep my colours" is ticked. */
    static boolean kept(Prefs p, String k) {
        if ("wk".equals(k)) return p.on("keep_wk", true);
        if ("opacity".equals(k) || k.startsWith("op_")) return p.on("keep_op", true);
        if ("font".equals(k) || "hdp10".equals(k) || "dows".equals(k)) return p.on("keep_font", true);
        if ("eset".equals(k) || "eset_ad".equals(k)) return p.on("keep_codes", true);
        return false;
    }
    /** A random look, for fun: any theme, any picture, flat or hand-drawn. */
    static void surprise(Context c, Prefs p) {
        java.util.Random r = new java.util.Random();
        apply(c, p, THEMES[r.nextInt(THEMES.length)][0]);
        p.put("scene", SCENES[1 + r.nextInt(SCENES.length - 1)][0]);
        p.put("scene_off", 0);
    }
    /** Colour overrides a theme clears, so the theme's own colours show. */
    static final String[] CLEARS = Pal.OVERRIDES;

    static void apply(Context c, Prefs p, String id) {
        String[] t = null;
        for (String[] x : THEMES) if (x[0].equals(id)) t = x;
        if (t == null) return;
        Hist.remember(p);   // the colour scheme before the look goes to Recent
        SharedPreferences.Editor e = p.sp.edit();
        // keep what was there, so "Undo theme" can put it back
        try {
            JSONObject undo = new JSONObject();
            Map<String, ?> all = p.sp.getAll();
            java.util.List<String> keys = new java.util.ArrayList<String>();
            for (Object[] kv : COMMON) keys.add((String) kv[0]);
            for (Object[] kv : INK) keys.add((String) kv[0]);
            for (String k : CLEARS) keys.add(k);
            keys.add("pal"); keys.add("scene"); keys.add("tfont"); keys.add("rules");
            for (String k : keys) undo.put(k, all.containsKey(k) ? all.get(k) : JSONObject.NULL);
            e.putString("undo_theme", undo.toString());
        } catch (Exception ignored) { }
        for (Object[][] set : new Object[][][] { COMMON, "ink".equals(t[5]) ? INK : FLAT })
            for (Object[] kv : set) {
                if (kept(p, (String) kv[0])) continue;
                if (kv[1] instanceof Integer) e.putInt((String) kv[0], (Integer) kv[1]); else e.putString((String) kv[0], (String) kv[1]);
            }
        if (!p.on("keep_cols", false)) for (String k : CLEARS) e.remove(k);
        e.putString("pal", t[2]).putString("scene", t[3]).putString("tfont", t[4]);
        e.commit();
        // code colours follow the new palette when they were matched to a set (unless "keep my code colours")
        if ("palette".equals(p.colour()) && !p.on("keep_codes", true)) {
            int[] set = Pal.events(p);
            StringBuilder sb = new StringBuilder(); int i = 0;
            for (String line : p.rules().split("\n")) {
                int eq = line.lastIndexOf('='); if (eq < 0) continue;
                sb.append(line.substring(0, eq).trim()).append('=').append(Pal.str(set[i++ % set.length])).append('\n');
            }
            p.put("rules", sb.toString());
        }
    }
    static boolean canUndo(Prefs p) { return !p.s("undo_theme", "").isEmpty(); }
    static void undo(Prefs p) {
        try {
            JSONObject o = new JSONObject(p.s("undo_theme", ""));
            SharedPreferences.Editor e = p.sp.edit();
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next(); Object v = o.get(k);
                if (v == JSONObject.NULL) e.remove(k);
                else if (v instanceof Integer) e.putInt(k, (Integer) v);
                else if (v instanceof Boolean) e.putBoolean(k, (Boolean) v);
                else if (v instanceof Number) e.putInt(k, ((Number) v).intValue());
                else e.putString(k, String.valueOf(v));
            }
            e.remove("undo_theme").commit();
        } catch (Exception ignored) { }
    }
}
