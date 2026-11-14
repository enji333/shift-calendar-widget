package com.shiftcal.widget;

import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.json.JSONArray;
import org.json.JSONObject;

/** Moves settings between the phone and the desktop control panel, as JSON. */
final class Sync {
    /** Keys that belong to this phone only and are never sent or overwritten. */
    static boolean local(String k) {
        return k.equals("crash") || k.equals("mig26") || k.startsWith("off_") || k.startsWith("tmp_") || k.startsWith("view_");
    }

    static JSONObject export(Context c) throws Exception {
        Prefs p = new Prefs(c);
        JSONObject o = new JSONObject();
        for (Map.Entry<String, ?> e : p.sp.getAll().entrySet()) {
            String k = e.getKey(); Object v = e.getValue();
            if (local(k) || v == null) continue;
            if (v instanceof Set) { JSONArray a = new JSONArray(); for (Object x : (Set<?>) v) a.put(String.valueOf(x)); o.put(k, a); }
            else o.put(k, v);
        }
        return o;
    }

    /** Applies settings from the panel. A null value removes that setting so the phone default is used again. */
    static int apply(Context c, JSONObject settings) throws Exception {
        Prefs p = new Prefs(c);
        SharedPreferences.Editor ed = p.sp.edit();
        int n = 0;
        for (Iterator<String> it = settings.keys(); it.hasNext(); ) {
            String k = it.next();
            if (local(k) || k.length() > 40) continue;
            Object v = settings.get(k);
            ed.remove(k);
            if (v == null || v == JSONObject.NULL) { n++; continue; }
            if (v instanceof JSONArray) {
                JSONArray a = (JSONArray) v; Set<String> s = new HashSet<String>();
                for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
                ed.putStringSet(k, s);
            } else if (v instanceof Boolean) ed.putInt(k, ((Boolean) v) ? 1 : 0);
            else if (v instanceof Number) ed.putInt(k, (int) Math.round(((Number) v).doubleValue()));
            else { String sv = String.valueOf(v); if (sv.length() > 20000) continue; ed.putString(k, sv); }
            n++;
        }
        ed.commit();
        p.resetOffsets();
        ShiftWidget.refreshAll(c);
        return n;
    }

    /** Everything the panel needs for a true preview: settings, calendars and the events around today. */
    static JSONObject state(Context c) throws Exception {
        Prefs p = new Prefs(c);
        JSONObject o = new JSONObject();
        o.put("app", "Shift Calendar");
        try { o.put("version", c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName); } catch (Exception ignored) { }
        o.put("settings", export(c));
        JSONArray cals = new JSONArray(), evs = new JSONArray();
        try {
            Cursor cur = c.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
                new String[] { CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.VISIBLE },
                null, null, null);
            if (cur != null) try {
                while (cur.moveToNext()) {
                    JSONObject k = new JSONObject();
                    k.put("id", String.valueOf(cur.getLong(0)));
                    String name = cur.getString(1); if (name == null) name = "Calendar";
                    if (name.startsWith("webcal://") && name.contains("humanforce")) name = "Humanforce roster";
                    k.put("name", name); k.put("account", cur.getString(2) == null ? "" : cur.getString(2));
                    k.put("color", Pal.str(cur.getInt(3))); k.put("visible", cur.getInt(4) == 1);
                    cals.put(k);
                }
            } finally { cur.close(); }

            ZoneId zone = ZoneId.of(p.tz());
            LocalDate today = LocalDate.now(zone);
            Uri.Builder ub = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(ub, today.minusDays(21).atStartOfDay(zone).toInstant().toEpochMilli());
            ContentUris.appendId(ub, today.plusDays(84).atStartOfDay(zone).toInstant().toEpochMilli());
            cur = c.getContentResolver().query(ub.build(), new String[] {
                CalendarContract.Instances.TITLE, CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.STATUS }, null, null, CalendarContract.Instances.BEGIN + " ASC");
            if (cur != null) try {
                while (cur.moveToNext() && evs.length() < 2000) {
                    if (!cur.isNull(7) && cur.getInt(7) == CalendarContract.Events.STATUS_CANCELED) continue;
                    JSONObject e = new JSONObject();
                    e.put("t", cur.getString(0) == null ? "" : cur.getString(0));
                    e.put("l", cur.getString(1) == null ? "" : cur.getString(1));
                    e.put("b", cur.getLong(2)); e.put("e", cur.getLong(3)); e.put("a", cur.getInt(4) == 1);
                    e.put("c", cur.isNull(5) ? "#4285F4" : Pal.str(cur.getInt(5))); e.put("k", String.valueOf(cur.getLong(6)));
                    evs.put(e);
                }
            } finally { cur.close(); }
        } catch (SecurityException noPermission) { o.put("note", "Calendar access is off on the phone."); }
        o.put("calendars", cals); o.put("events", evs);
        o.put("today", LocalDate.now(ZoneId.of(p.tz())).toString());
        return o;
    }

    /** Settings code for copy/paste or a QR link: "SC1" + base64url(deflate(JSON)). */
    static String encode(JSONObject settings) throws Exception {
        byte[] in = settings.toString().getBytes("UTF-8");
        Deflater d = new Deflater(9, true); d.setInput(in); d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[4096];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return "SC1" + Base64.encodeToString(out.toByteArray(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
    static JSONObject decode(String code) throws Exception {
        code = code.trim();
        int q = code.indexOf("d="); if (code.startsWith("shiftcal:") && q >= 0) code = Uri.decode(code.substring(q + 2));
        code = code.replaceAll("\\s+", "");
        if (code.startsWith("{")) return unwrap(new JSONObject(code));
        if (!code.startsWith("SC1")) throw new IllegalArgumentException("That isn't a Shift Calendar settings code.");
        byte[] z = Base64.decode(code.substring(3), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        Inflater inf = new Inflater(true); inf.setInput(z);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[4096];
        while (!inf.finished()) { int n = inf.inflate(buf); if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break; out.write(buf, 0, n); if (out.size() > 500000) break; }
        inf.end();
        return unwrap(new JSONObject(out.toString("UTF-8")));
    }
    private static JSONObject unwrap(JSONObject o) throws Exception { return o.has("settings") ? o.getJSONObject("settings") : o; }
}
