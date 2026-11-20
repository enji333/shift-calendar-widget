package com.shiftcal.widget;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estimated pay for the current week (Monday to Sunday), from shifts in the calendar.
 * Only timed events whose title starts with EPA, VW, ACT or GPH count. Cancelled ones and everything else are skipped.
 * Rates and targets are settings, stored in cents (rates, money) and tenths of an hour (hours).
 */
final class Earn {
    static final class Week { double est, done, hours, rate; int status; int shifts; }   // status: 2 on track, 1 low rate, 0 short

    static final Pattern HRS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*hrs?\\b", Pattern.CASE_INSENSITIVE);
    private static final long H = 3600000L;

    private static final class Shift {
        String kind; long b, e; double paid; double ratio; boolean rigger; String role = "";
    }

    static double money(Prefs p, String k, int defCents) { return p.i(k, defCents) / 100.0; }
    static double target(Prefs p) { return money(p, "e_target", 221200); }
    static double rateTarget(Prefs p) { return money(p, "e_rtarget", 5820); }
    static double hoursTarget(Prefs p) { return p.i("e_htarget", 380) / 10.0; }

    static Week week(Context c, Prefs p) {
        LocalDate today = LocalDate.now(ZoneId.of(p.tz()));
        return week(c, p, today.minusDays(today.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue()));
    }
    /** The 7 days from mon (any start day, so a Sunday-first row works too). */
    static Week week(Context c, Prefs p, LocalDate mon) {
        ZoneId zone = ZoneId.of(p.tz());
        long a = mon.atStartOfDay(zone).toInstant().toEpochMilli();
        long z = mon.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli();
        long now = System.currentTimeMillis();

        double epa = money(p, "e_epa", 6150), epaMin = p.i("e_epamin", 40) / 10.0;
        // EPA pay rise (payslips): basic rigger $58.00 -> $61.50 and intermediate $64.48 -> $65.52 from the week of 24 Oct 2026
        double epaOld = money(p, "e_epaold", 5800), epaInt = money(p, "e_epaint", 6552), epaIntOld = money(p, "e_epaintold", 6448);
        int rf = p.i("e_epafrom", 20261024);
        long epaFrom = LocalDate.of(rf / 10000, Math.max(1, Math.min(12, rf / 100 % 100)), Math.max(1, Math.min(28, rf % 100))).atStartOfDay(zone).toInstant().toEpochMilli();
        double gph = money(p, "e_gph", 4700), gphMin = p.i("e_gphmin", 0) / 10.0;
        double vw = money(p, "e_vw", 4487), vwRig = money(p, "e_vwrig", 4925), vwNight = money(p, "e_vwnight", 6140);
        double act = money(p, "e_act", 4015), actSen = money(p, "e_actsen", 4402), actSup = money(p, "e_actsup", 4944);
        double vwNightF = money(p, "e_vwnightf", 5544), vwBoh = money(p, "e_vwboh", 3907), vwBohN = money(p, "e_vwbohn", 4925);
        int nightEnd = Math.max(0, Math.min(12, p.i("e_nightend", 6)));

        List<Shift> list = new ArrayList<Shift>();
        Uri.Builder ub = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(ub, a - 24 * H);
        ContentUris.appendId(ub, z + 24 * H);
        String[] proj = { CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.STATUS,
            CalendarContract.Instances.VISIBLE, CalendarContract.Instances.DESCRIPTION };
        Set<String> cals = p.cals();
        Set<String> seen = new HashSet<String>();
        Cursor cur = c.getContentResolver().query(ub.build(), proj, null, null, CalendarContract.Instances.BEGIN + " ASC");
        if (cur != null) try {
            while (cur.moveToNext()) {
                String calId = String.valueOf(cur.getLong(4));
                if (cals == null) { if (cur.getInt(6) == 0) continue; } else if (!cals.contains(calId)) continue;
                if (cur.getInt(3) == 1) continue;                                                          // all-day: never a shift
                if (!cur.isNull(5) && cur.getInt(5) == CalendarContract.Events.STATUS_CANCELED) continue;
                String title = cur.getString(0); if (title == null) continue;
                String t = title.trim();
                if (t.toLowerCase(Locale.ROOT).startsWith("cancel")) continue;                            // "CANCELLED - ..." earns nothing
                String kind = kind(t); if (kind == null) continue;
                long b = cur.getLong(1), e = cur.getLong(2);
                if (b < a || b >= z || e <= b) continue;                                                   // the week a shift starts in
                if (!seen.add(t + "|" + b + "|" + e)) continue;                                            // same shift on two calendars
                Shift s = new Shift(); s.kind = kind; s.b = b; s.e = e;
                double len = (e - b) / (double) H, hrs = len;
                Matcher m = HRS.matcher(t);
                boolean titled = false;
                if (m.find()) try { hrs = Double.parseDouble(m.group(1)); titled = true; } catch (NumberFormatException ignored) { }
                if (!titled && "GPH".equals(kind) && len > 5) hrs = len - 0.5;                           // GPH: unpaid 30 min break on long days
                s.ratio = len > 0 ? Math.min(1.5, hrs / len) : 1;                                            // title hours leave out unpaid breaks
                s.paid = hrs;
                String d = cur.isNull(7) ? "" : cur.getString(7);
                String dl = d == null ? "" : d.toLowerCase(Locale.ROOT);
                s.rigger = dl.contains("rigger");
                // role from the description (contract rates, v3.13): VW rigger / BOH / forklift, ACT supervisor / senior / staging tech
                s.role = dl.contains("intermediate") ? "int" : s.rigger ? "rig" : (dl.contains("boh") || dl.contains("back of house")) ? "boh"
                    : dl.contains("supervisor") ? "sup" : dl.contains("senior staging") ? "sen" : "";
                list.add(s);
            }
        } finally { cur.close(); }

        // Pay: every shift is paid its booked hours, at least its minimum call (EPA 4, ACT 4, VW 3, GPH none set), even when
        // he leaves early or arrives late to fit another shift in. VW pays what he attends, so when shifts overlap
        // he is taken to be at the VW one: VW keeps all its hours and the others lose the overlapping time from
        // the hours worked (not from the pay). $/hour = pay / hours actually worked.
        double actMin = p.i("e_actmin", 40) / 10.0, vwMin = p.i("e_vwmin", 30) / 10.0;
        List<Shift> order = new ArrayList<Shift>();
        for (Shift s : list) if ("VW".equals(s.kind)) order.add(s);
        for (Shift s : list) if (!"VW".equals(s.kind)) order.add(s);
        List<long[]> claimed = new ArrayList<long[]>();
        Week w = new Week();
        for (Shift s : order) {
            double worked = 0;
            for (long[] f : free(s.b, s.e, claimed)) worked += (f[1] - f[0]) / (double) H;
            worked *= s.ratio;
            double pay;
            if ("EPA".equals(s.kind)) pay = Math.max(epaMin, s.paid) * ("int".equals(s.role) ? (s.b >= epaFrom ? epaInt : epaIntOld) : (s.b >= epaFrom ? epa : epaOld));
            else if ("GPH".equals(s.kind)) pay = Math.max(gphMin, s.paid) * gph;
            else if ("ACT".equals(s.kind)) pay = Math.max(actMin, s.paid) * ("sup".equals(s.role) ? actSup : "sen".equals(s.role) ? actSen : act);
            else {
                double night = nightMs(s.b, s.e, zone, nightEnd) / (double) H * s.ratio, day = Math.max(0, s.paid - night);
                double extra = Math.max(0, vwMin - s.paid);          // a short call is topped up at the day rate
                double dr = "rig".equals(s.role) ? vwRig : "boh".equals(s.role) ? vwBoh : vw;
                double nr = "rig".equals(s.role) ? vwNight : "boh".equals(s.role) ? vwBohN : vwNightF;
                pay = night * nr + (day + extra) * dr;
            }
            claimed.add(new long[] { s.b, s.e });
            w.hours += worked; w.est += pay; if (s.e <= now) w.done += pay; w.shifts++;
        }
        w.rate = w.hours > 0 ? w.est / w.hours : 0;
        w.status = w.est + 0.5 >= target(p) ? (w.rate + 0.005 >= rateTarget(p) ? 2 : 1) : 0;
        return w;
    }


    /** The code at the start of a shift title, or null when the event is not a paid shift. */
    static String kind(String t) {
        String u = t.toUpperCase(Locale.ROOT);
        for (String k : new String[] { "EPA", "VW", "ACT", "GPH" })
            if (u.startsWith(k) && (u.length() == k.length() || !Character.isLetterOrDigit(u.charAt(k.length())))) return k;
        return null;
    }

    /** Parts of [b, e) not covered by any claimed range. */
    static List<long[]> free(long b, long e, List<long[]> claimed) {
        List<long[]> out = new ArrayList<long[]>();
        out.add(new long[] { b, e });
        for (long[] c : claimed) {
            List<long[]> next = new ArrayList<long[]>();
            for (long[] f : out) {
                if (c[1] <= f[0] || c[0] >= f[1]) { next.add(f); continue; }
                if (c[0] > f[0]) next.add(new long[] { f[0], c[0] });
                if (c[1] < f[1]) next.add(new long[] { c[1], f[1] });
            }
            out = next;
        }
        return out;
    }

    /** Milliseconds of [b, e) that fall between midnight and the end of the overnight rate (6am). */
    static long nightMs(long b, long e, ZoneId zone, int endHour) {
        long tot = 0;
        LocalDate d = Instant.ofEpochMilli(b).atZone(zone).toLocalDate();
        LocalDate last = Instant.ofEpochMilli(e).atZone(zone).toLocalDate();
        for (; !d.isAfter(last); d = d.plusDays(1)) {
            long n0 = d.atStartOfDay(zone).toInstant().toEpochMilli();
            long n1 = ZonedDateTime.of(d.atStartOfDay(), zone).plusHours(endHour).toInstant().toEpochMilli();
            long s = Math.max(b, n0), f = Math.min(e, n1);
            if (f > s) tot += f - s;
        }
        return tot;
    }
}
