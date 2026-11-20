package com.shiftcal.widget;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/** All user settings. One shared set for every widget; month offset is per widget. */
final class Prefs {
    static final String DEFAULT_RULES = "ACT=#D50000\nEPA=#F4511E\nVW=#039BE5\nGPH=#F6BF26";
    final SharedPreferences sp;
    Prefs(Context c) { sp = c.getSharedPreferences("cfg", Context.MODE_PRIVATE); }

    /** Type-tolerant reads: settings sent from the desktop panel may arrive as text or as numbers. */
    String s(String k, String def) {
        Object v = sp.getAll().get(k);
        if (v == null) return def;
        if (v instanceof Float || v instanceof Double) { double d = ((Number) v).doubleValue(); return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d); }
        return String.valueOf(v);
    }
    int i(String k, int def) {
        Object v = sp.getAll().get(k);
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof Boolean) return ((Boolean) v) ? 1 : 0;
        try { return (int) Math.round(Double.parseDouble(String.valueOf(v).trim())); } catch (Exception e) { return def; }
    }
    /** A size stored in tenths of a dp ("tdp10" = 115 means 11.5dp); 0 or missing = use the default. */
    float tenths(String k, float def) { int v = i(k, 0); return v > 0 ? v / 10f : def; }
    boolean on(String k, boolean def) { return i(k, def ? 1 : 0) != 0; }
    void put(String k, String v) { sp.edit().putString(k, v).apply(); }
    void put(String k, int v) { sp.edit().putInt(k, v).apply(); }

    String over() { return s("over", "start"); }          // start | every
    String allday() { return s("allday", "bottom"); }     // top | bottom
    String density() { return s("density", "detailed"); } // detailed | compact
    float textDp() { if (i("tdp10", 0) > 0) return Math.max(6f, Math.min(30f, i("tdp10", 0) / 10f)); String z = s("size", "m"); return "s".equals(z) ? 10.5f : "l".equals(z) ? 13f : 11.5f; }
    int max() { return i("max", 0); }                     // 0 = fit to space
    String inc() { return s("inc", ""); }
    String and() { return s("and", ""); }
    String exc() { return s("exc", ""); }
    /** True when a filter is hiding some events (the widget then shows a * after its title). */
    boolean filtered() { return !inc().trim().isEmpty() || !and().trim().isEmpty() || !exc().trim().isEmpty(); }
    /** The pairing code for "Receive from computer". Stays the same until "New code" is pressed (v3.6). */
    String pin() {
        String v = s("pin", "");
        if (!v.matches("\\d{4}")) { v = String.format(java.util.Locale.ROOT, "%04d", new java.security.SecureRandom().nextInt(10000)); put("pin", v); }
        return v;
    }
    void newPin() { sp.edit().remove("pin").commit(); pin(); }
    String cancel() { return s("cancel", "show"); }       // show | hide
    String colour() { return s("colour", "event"); }      // event | rules
    String rules() { return s("rules", DEFAULT_RULES); }
    String theme() { return s("theme", "dark"); }         // legacy: dark | light
    String pal() { return s("pal", "light".equals(theme()) ? "light" : "bush"); }
    boolean sundayFirst() { return !"mon".equals(s("wk", "sun")); }
    int opacity() { return i("opacity", 85); }
    String tz() { return s("tz", "Australia/Perth"); }

    /** null = not chosen yet, which means "every calendar visible on the phone". */
    Set<String> cals() {
        if (!sp.contains("cals")) return null;
        try { return new HashSet<String>(sp.getStringSet("cals", new HashSet<String>())); } catch (ClassCastException e) { return null; }
    }
    void setCals(Set<String> v) { sp.edit().putStringSet("cals", new HashSet<String>(v)).apply(); }

    String view(int wid) { return s("view", s("view_" + wid, "rolling")); }   // rolling | month, one choice for every widget
    void setView(int wid, String v) { put("view", v); }
    void resetOffsets() { SharedPreferences.Editor e = sp.edit(); for (String k : sp.getAll().keySet()) if (k.startsWith("off_") || k.startsWith("tmp_")) e.remove(k); e.apply(); }
    /** Opacity (0-100) of one kind of day box: op_past, op_future, op_today. */
    int op(String key) { return Math.max(0, Math.min(100, i(key, 95))); }
    static void logCrash(Context c, Throwable t) {
        try {
            java.io.StringWriter w = new java.io.StringWriter(); t.printStackTrace(new java.io.PrintWriter(w));
            String s = w.toString(); if (s.length() > 3000) s = s.substring(0, 3000);
            c.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putString("crash", new java.util.Date() + "\n" + s).commit();
        } catch (Throwable ignored) { }
    }
    static void watch(final Context c) {
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        if (old instanceof Watch) return;
        Thread.setDefaultUncaughtExceptionHandler(new Watch(c.getApplicationContext(), old));
    }
    private static final class Watch implements Thread.UncaughtExceptionHandler {
        final Context c; final Thread.UncaughtExceptionHandler old;
        Watch(Context c, Thread.UncaughtExceptionHandler old) { this.c = c; this.old = old; }
        public void uncaughtException(Thread t, Throwable e) { logCrash(c, e); if (old != null) old.uncaughtException(t, e); }
    }
    int past() { return i("past", 7); }                    // days before today in rolling view
    int rows() { return i("rows", 4); }                    // weeks shown in rolling view
    boolean timeline() { return !"list".equals(s("pos", "time")); } // time | list
    int dayStart() { return i("t0", -1); }                 // hour the timeline starts at, -1 = fit to that week
    String taper() { return s("taper", "some"); }          // off | some | lots
    String code() { return s("code", "accent"); }          // text | accent | event | custom
    boolean sideBySide() { return !"stack".equals(s("mk", "side")); } // side | stack
    boolean aligned() { return "aligned".equals(s("roll", "daily")); } // daily | aligned

    int offset(int wid) { return i("off_" + wid, 0); }
    void setOffset(int wid, int v) { sp.edit().putInt("off_" + wid, v).apply(); }
}
