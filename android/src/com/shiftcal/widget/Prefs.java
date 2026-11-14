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

    String s(String k, String def) { return sp.getString(k, def); }
    int i(String k, int def) { return sp.getInt(k, def); }
    void put(String k, String v) { sp.edit().putString(k, v).apply(); }
    void put(String k, int v) { sp.edit().putInt(k, v).apply(); }

    String over() { return s("over", "start"); }          // start | every
    String allday() { return s("allday", "bottom"); }     // top | bottom
    String density() { return s("density", "detailed"); } // detailed | compact
    float textDp() { String z = s("size", "m"); return "s".equals(z) ? 10.5f : "l".equals(z) ? 13f : 11.5f; }
    int max() { return i("max", 0); }                     // 0 = fit to space
    String inc() { return s("inc", ""); }
    String and() { return s("and", ""); }
    String exc() { return s("exc", ""); }
    String cancel() { return s("cancel", "show"); }       // show | hide
    String colour() { return s("colour", "event"); }      // event | rules
    static final String DEFAULT_SUFFIX = "hmt=#E191C8\nrac=#59B09E\nstc=#FFA147\ncrown=#B4E665";
    String srules() { return ""; }
    String rules() { return s("rules", DEFAULT_RULES); }
    String theme() { return s("theme", "dark"); }         // legacy: dark | light
    String pal() { return s("pal", "light".equals(theme()) ? "light" : "bush"); }
    boolean sundayFirst() { return !"mon".equals(s("wk", "sun")); }
    int opacity() { return i("opacity", 85); }
    String tz() { return s("tz", "Australia/Perth"); }

    /** null = not chosen yet, which means "every calendar visible on the phone". */
    Set<String> cals() {
        if (!sp.contains("cals")) return null;
        return new HashSet<String>(sp.getStringSet("cals", new HashSet<String>()));
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

    int offset(int wid) { return sp.getInt("off_" + wid, 0); }
    void setOffset(int wid, int v) { sp.edit().putInt("off_" + wid, v).apply(); }
}
