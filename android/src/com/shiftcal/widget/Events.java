package com.shiftcal.widget;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads the phone's calendar and lays events out per day, overnight shifts on their start day. */
final class Events {

    static final class Item {
        String title, loc; long id, begin, end; boolean allDay, cont, overnight, cancelled; int color; long sort;
    }

    static Map<LocalDate, List<Item>> load(Context c, Prefs p, LocalDate from, LocalDate to) { return load(c, p, from, to, false); }
    static Map<LocalDate, List<Item>> load(Context c, Prefs p, LocalDate from, LocalDate to, boolean forceEvery) {
        final ZoneId zone = ZoneId.of(p.tz());
        Map<LocalDate, List<Item>> map = new HashMap<LocalDate, List<Item>>();
        long a = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long b = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        Uri.Builder ub = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(ub, a);
        ContentUris.appendId(ub, b);
        String[] proj = {
            CalendarContract.Instances.TITLE, CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.STATUS,
            CalendarContract.Instances.VISIBLE, CalendarContract.Instances.EVENT_ID
        };
        Set<String> cals = p.cals();
        List<Pattern> inc = words(p.inc()), and = words(p.and()), exc = words(p.exc());
        List<Object[]> rules = rules(p.rules());
        boolean useRules = "rules".equals(p.colour());
        boolean usePal = "palette".equals(p.colour());
        int[] palSet = Pal.events(p);
        boolean hideCancelled = "hide".equals(p.cancel());
        int cancelCol = Pal.of(p).cancel;
        boolean every = forceEvery || "every".equals(p.over());
        Set<String> seen = new HashSet<String>();

        Cursor cur = c.getContentResolver().query(ub.build(), proj, null, null, CalendarContract.Instances.BEGIN + " ASC");
        if (cur == null) return map;
        try {
            while (cur.moveToNext()) {
                String calId = String.valueOf(cur.getLong(6));
                if (cals == null) { if (cur.getInt(8) == 0) continue; }
                else if (!cals.contains(calId)) continue;
                if (!cur.isNull(7) && cur.getInt(7) == CalendarContract.Events.STATUS_CANCELED) continue;
                String title = cur.getString(0); if (title == null) title = "";
                String loc = cur.getString(1); if (loc == null) loc = "";
                String hay = title + " " + loc;
                boolean cancelled = title.trim().toLowerCase(Locale.ROOT).startsWith("cancel");
                if (cancelled && hideCancelled) continue;
                if (!inc.isEmpty() && !any(inc, hay)) continue;
                if (!and.isEmpty() && !any(and, hay)) continue;
                if (!exc.isEmpty() && any(exc, hay)) continue;
                long begin = cur.getLong(2), end = cur.getLong(3);
                curLoc = loc; curId = cur.getLong(9);
                boolean allDay = cur.getInt(4) == 1;
                if (!seen.add(title + "|" + begin + "|" + end)) continue; // same event on two calendars
                int color = cur.isNull(5) ? 0xFF4285F4 : (cur.getInt(5) | 0xFF000000);
                if (usePal) {
                    int idx = -1;
                    String up = title.trim().toUpperCase(Locale.ROOT);
                    if (allDay) for (int k = 0; k < rules.size() && idx < 0; k++) if (endsWithCode(title, (String) rules.get(k)[2])) idx = k;   // all-day: end code wins
                    for (int k = 0; k < rules.size() && idx < 0; k++) if (up.startsWith((String) rules.get(k)[2])) idx = k;
                    for (int k = 0; k < rules.size() && idx < 0; k++) if (endsWithCode(title, (String) rules.get(k)[2])) idx = k;   // then a code at the end
                    for (int k = 0; k < rules.size() && idx < 0; k++) if (((Pattern) rules.get(k)[0]).matcher(hay).find()) idx = k;
                    if (idx < 0) idx = rules.size() + ((color & 0x7FFFFFFF) % 7);   // other events: steady colour per calendar colour
                    color = idx < rules.size() ? (Integer) rules.get(idx)[1] : palSet[idx % palSet.length];   // codes keep their own (editable) colour
                } else if (useRules) {
                    boolean hit = false;
                    String up = title.trim().toUpperCase(Locale.ROOT);
                    if (allDay) for (Object[] r : rules) if (endsWithCode(title, (String) r[2])) { color = (Integer) r[1]; hit = true; break; }   // all-day: end code wins
                    if (!hit) for (Object[] r : rules) if (up.startsWith((String) r[2])) { color = (Integer) r[1]; hit = true; break; }  // prefix first
                    if (!hit) for (Object[] r : rules) if (endsWithCode(title, (String) r[2])) { color = (Integer) r[1]; hit = true; break; }
                    if (!hit) for (Object[] r : rules) if (((Pattern) r[0]).matcher(hay).find()) { color = (Integer) r[1]; break; }
                } else {
                    // calendar-colour mode: a code at the end of the title still recolours that one event
                    boolean pre = false;
                    String up = title.trim().toUpperCase(Locale.ROOT);
                    if (!allDay) for (Object[] r : rules) if (up.startsWith((String) r[2])) pre = true;
                    if (!pre) for (Object[] r : rules) if (endsWithCode(title, (String) r[2])) { color = (Integer) r[1]; break; }
                }
                if (cancelled) color = cancelCol;

                if (allDay) {
                    LocalDate s = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate();
                    LocalDate e = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate();
                    if (!e.isAfter(s)) e = s.plusDays(1);
                    for (LocalDate d = s; d.isBefore(e); d = d.plusDays(1)) {
                        Item it = item(title, begin, end, true, cancelled, color); it.sort = -1; add(map, d, it);
                    }
                } else {
                    LocalDate s = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate();
                    LocalDate e = Instant.ofEpochMilli(Math.max(begin, end - 1)).atZone(zone).toLocalDate();
                    Item it = item(title, begin, end, false, cancelled, color);
                    it.overnight = e.isAfter(s);
                    it.sort = begin - s.atStartOfDay(zone).toInstant().toEpochMilli();
                    add(map, s, it);
                    if (it.overnight && every) {
                        for (LocalDate d = s.plusDays(1); !d.isAfter(e); d = d.plusDays(1)) {
                            Item ct = item(title, begin, end, false, cancelled, color);
                            ct.cont = true; ct.overnight = true; ct.sort = 0; add(map, d, ct);
                        }
                    }
                }
            }
        } finally { cur.close(); }

        final boolean top = !"bottom".equals(p.allday());
        Comparator<Item> cmp = new Comparator<Item>() {
            @Override public int compare(Item x, Item y) {
                long px = rank(x, top), py = rank(y, top);
                if (px != py) return px < py ? -1 : 1;
                return x.title.compareToIgnoreCase(y.title);
            }
        };
        for (List<Item> l : map.values()) Collections.sort(l, cmp);
        return map;
    }

    private static String curLoc = ""; private static long curId;
    private static long rank(Item i, boolean top) {
        if (i.allDay) return top ? Long.MIN_VALUE : Long.MAX_VALUE;
        if (i.cont) return Long.MIN_VALUE + 1;
        return i.sort;
    }
    private static Item item(String t, long b, long e, boolean ad, boolean cn, int col) {
        Item i = new Item(); i.title = t; i.loc = curLoc; i.id = curId; i.begin = b; i.end = e; i.allDay = ad; i.cancelled = cn; i.color = col; return i;
    }
    private static void add(Map<LocalDate, List<Item>> m, LocalDate d, Item i) {
        List<Item> l = m.get(d); if (l == null) { l = new ArrayList<Item>(); m.put(d, l); } l.add(i);
    }
    static List<Pattern> words(String csv) {
        List<Pattern> out = new ArrayList<Pattern>();
        for (String w : csv.split(",")) {
            w = w.trim(); if (w.isEmpty()) continue;
            out.add(Pattern.compile("(^|[^\\p{L}\\p{N}])" + Pattern.quote(w) + "($|[^\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
        return out;
    }
    private static boolean any(List<Pattern> ps, String s) { for (Pattern p : ps) if (p.matcher(s).find()) return true; return false; }
    /** True when the title finishes with the code as its own word, e.g. "Fern Society picnic GPH". */
    static boolean endsWithCode(String title, String key) {
        String t = title.trim();   // capitals only, so an ordinary last word such as "rac" in "VW rac" is left alone
        if (key == null || key.isEmpty() || t.length() <= key.length() || !t.endsWith(key)) return false;
        return !Character.isLetterOrDigit(t.charAt(t.length() - key.length() - 1));
    }
    /** The title as drawn: a colour code at the very end has done its job, so it is left off. */
    static String display(String title, List<String> keys, boolean allDay) {
        String ut = title.trim().toUpperCase(Locale.ROOT);
        if (!allDay) for (String k : keys) if (ut.startsWith(k)) return title;   // the code at the front is the one in charge
        for (String k : keys) if (endsWithCode(title, k)) {
            String tt = title.trim();
            String out = tt.substring(0, tt.length() - k.length()).replaceFirst("[\\s\\-–—:,|/(\\[]+$", "");
            return out.isEmpty() ? title : out;
        }
        return title;
    }
    static List<String> keys(Prefs p) {
        List<String> out = new ArrayList<String>();
        for (Object[] r : rules(p.rules())) out.add((String) r[2]);
        return out;
    }
    static List<Object[]> rules(String text) {
        List<Object[]> out = new ArrayList<Object[]>();
        for (String line : text.split("\n")) {
            int eq = line.lastIndexOf('='); if (eq <= 0) continue;
            String w = line.substring(0, eq).trim(), col = line.substring(eq + 1).trim();
            try {
                int c = android.graphics.Color.parseColor(col.startsWith("#") ? col : "#" + col) | 0xFF000000;
                List<Pattern> ps = words(w);
                if (!ps.isEmpty()) out.add(new Object[] { ps.get(0), c, w.toUpperCase(Locale.ROOT) });
            } catch (IllegalArgumentException ignored) { }
        }
        return out;
    }
}
