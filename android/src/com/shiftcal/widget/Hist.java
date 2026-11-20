package com.shiftcal.widget;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** v3.7: going back. Earlier copies of every setting (to undo or restore), and colour schemes kept by name or
 *  remembered automatically. Saved schemes are shared with the control panel ("schemes"); history stays on the phone. */
final class Hist {
    static final int MAX_HIST = 12, MAX_RECENT = 8;

    // ------------------------------------------------------------ settings history
    /** Keeps a copy of all settings as they are now. why = shown in the list, such as "Before settings from computer". */
    static void snapshot(Context c, String why) {
        try {
            Prefs p = new Prefs(c);
            JSONObject now = Sync.export(c);
            JSONArray h = arr(p.s("hist", "[]"));
            if (h.length() > 0 && same(h.getJSONObject(0).getJSONObject("s"), now)) return;   // nothing changed since the last copy
            JSONObject e = new JSONObject();
            e.put("t", System.currentTimeMillis()); e.put("why", why); e.put("s", now);
            JSONArray out = new JSONArray(); out.put(e);
            for (int i = 0; i < h.length() && out.length() < MAX_HIST; i++) out.put(h.get(i));
            p.put("hist", out.toString());
        } catch (Exception ignored) { }
    }
    static JSONArray history(Prefs p) { return arr(p.s("hist", "[]")); }
    /** Puts every setting back exactly as in a copy (settings added since then go back to their defaults). */
    static void restore(Context c, JSONObject s) throws Exception {
        snapshot(c, "Before restoring an earlier copy");
        Prefs p = new Prefs(c);
        SharedPreferences.Editor e = p.sp.edit();
        for (String k : p.sp.getAll().keySet()) if (!Sync.local(k)) e.remove(k);
        e.commit();
        Sync.apply(c, s);
    }
    static boolean same(JSONObject a, JSONObject b) {
        try {
            if (a.length() != b.length()) return false;
            for (Iterator<String> it = a.keys(); it.hasNext(); ) { String k = it.next(); if (!b.has(k) || !String.valueOf(a.get(k)).equals(String.valueOf(b.get(k)))) return false; }
            return true;
        } catch (Exception e) { return false; }
    }

    // ------------------------------------------------------------ colour schemes
    /** The colour scheme as it is now: the palette plus every single colour picked by hand. */
    static JSONObject scheme(Prefs p, String name) {
        JSONObject o = new JSONObject();
        try {
            o.put("n", name); o.put("pal", p.pal());
            JSONObject x = new JSONObject();
            for (String k : Pal.OVERRIDES) { String v = p.s(k, "").trim(); if (!v.isEmpty()) x.put(k, v); }
            o.put("x", x);
        } catch (Exception ignored) { }
        return o;
    }
    static void useScheme(Context c, Prefs p, JSONObject o) {
        remember(p);
        SharedPreferences.Editor e = p.sp.edit();
        for (String k : Pal.OVERRIDES) e.remove(k);
        e.putString("pal", o.optString("pal", "bush"));
        JSONObject x = o.optJSONObject("x");
        if (x != null) for (Iterator<String> it = x.keys(); it.hasNext(); ) { String k = it.next(); e.putString(k, x.optString(k)); }
        e.commit();
    }
    /** Adds the scheme as it is now to the "recent" list, so changing palette never loses hand-picked colours. */
    static void remember(Prefs p) {
        try {
            JSONObject now = scheme(p, label(p));
            JSONArray r = arr(p.s("schemes_recent", "[]")), out = new JSONArray();
            out.put(now);
            for (int i = 0; i < r.length() && out.length() < MAX_RECENT; i++) if (!sameScheme(r.getJSONObject(i), now)) out.put(r.get(i));
            p.put("schemes_recent", out.toString());
        } catch (Exception ignored) { }
    }
    static void save(Prefs p, String name) {
        try {
            JSONArray s = arr(p.s("schemes", "[]")), out = new JSONArray();
            for (int i = 0; i < s.length(); i++) if (!name.equalsIgnoreCase(s.getJSONObject(i).optString("n"))) out.put(s.get(i));
            out.put(scheme(p, name));
            p.put("schemes", out.toString());
        } catch (Exception ignored) { }
    }
    static void delete(Prefs p, String key, int i) {
        JSONArray s = arr(p.s(key, "[]")), out = new JSONArray();
        for (int k = 0; k < s.length(); k++) if (k != i) try { out.put(s.get(k)); } catch (Exception ignored) { }
        p.put(key, out.toString());
    }
    static boolean sameScheme(JSONObject a, JSONObject b) {
        return a.optString("pal").equals(b.optString("pal")) && String.valueOf(a.optJSONObject("x")).equals(String.valueOf(b.optJSONObject("x")));
    }
    static String label(Prefs p) {
        String n = p.pal();
        for (String[] r : Pal.PRESETS) if (r[0].equals(n)) n = r[1];
        int k = 0; for (String o : Pal.OVERRIDES) if (!p.s(o, "").trim().isEmpty()) k++;
        if (n.contains(" (")) n = n.substring(0, n.indexOf(" ("));
        return k == 0 ? n : n + " + " + k + " own colour" + (k == 1 ? "" : "s");
    }
    /** Five colours to show a scheme at a glance. */
    static int[] strip(JSONObject o) {
        Pal b = Pal.preset(o.optString("pal", "bush"));
        JSONObject x = o.optJSONObject("x");
        int bg = b.bg, fut = b.future, acc = b.accent, sun = b.sun, fg = b.fg;
        if (x != null) { bg = Pal.hex(x.optString("x_bg"), bg); fut = Pal.hex(x.optString("x_future"), fut); acc = Pal.hex(x.optString("x_accent"), acc); sun = Pal.hex(x.optString("x_sunday", x.optString("x_sun")), sun); fg = Pal.hex(x.optString("x_fg"), fg); }
        return new int[] { bg, fut, acc, sun, fg };
    }
    static JSONArray arr(String s) { try { return new JSONArray(s); } catch (Exception e) { return new JSONArray(); } }
    static String when(long t) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("EEE d MMM, h:mm a", java.util.Locale.ENGLISH);
        return f.format(new java.util.Date(t));
    }
}
