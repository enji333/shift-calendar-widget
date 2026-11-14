package com.shiftcal.widget;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.util.SizeF;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShiftWidget extends AppWidgetProvider {
    static final String ACT_PREV = "com.shiftcal.widget.PREV", ACT_NEXT = "com.shiftcal.widget.NEXT",
        ACT_TODAY = "com.shiftcal.widget.TODAY", ACT_REFRESH = "com.shiftcal.widget.REFRESH",
        ACT_TOGGLE = "com.shiftcal.widget.TOGGLE";
    static final int[] DOW_IDS = { R.id.d0, R.id.d1, R.id.d2, R.id.d3, R.id.d4, R.id.d5, R.id.d6 };
    static final String[] DOW_TXT = { "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN" };
    static final int DIP = TypedValue.COMPLEX_UNIT_DIP;
    static final float SMALL = 0.45f;
    static final int[] ROW_LAYOUTS = { R.layout.week_row_40, R.layout.week_row_45, R.layout.week_row_50, R.layout.week_row_55,
        R.layout.week_row_60, R.layout.week_row_65, R.layout.week_row_70, R.layout.week_row_75, R.layout.week_row_80,
        R.layout.week_row_85, R.layout.week_row_90, R.layout.week_row_95, R.layout.week_row_100 };
    /** Row height for a week that is i weeks after the current one. */
    static float taper(String mode, int i) {
        float[] t = "off".equals(mode) ? new float[] { 1f } : "lots".equals(mode) ? new float[] { 1f, 0.7f, 0.5f, 0.4f }
            : new float[] { 1f, 0.8f, 0.65f, 0.5f, 0.45f, 0.4f };
        return t[Math.max(0, Math.min(i, t.length - 1))];
    }
    static final Pattern HOURS = Pattern.compile("\\d+(?:\\.\\d+)?\\s*hrs?\\b", Pattern.CASE_INSENSITIVE);
    static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9]{1,4}\\b");

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, safeBuild(c, id, m.getAppWidgetOptions(id)));
        schedule(c);
    }
    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        m.updateAppWidget(id, safeBuild(c, id, o));
    }
    @Override public void onReceive(Context c, Intent in) {
        super.onReceive(c, in);
        String a = in.getAction();
        if (a == null) return;
        int wid = in.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        Prefs p = new Prefs(c);
        if ((ACT_PREV.equals(a) || ACT_NEXT.equals(a)) && wid >= 0) {
            int d = ACT_NEXT.equals(a) ? 1 : -1;
            boolean monthNow = "month".equals(p.view(wid)) || p.i("tmp_" + wid, 0) == 1;
            // from the rolling view the arrows jump to whole months; the date button brings the rolling view back
            if (!monthNow) { p.put("tmp_" + wid, 1); p.setOffset(wid, d); }
            else p.setOffset(wid, p.offset(wid) + d);
        }
        else if (ACT_TODAY.equals(a) && wid >= 0) { p.setOffset(wid, 0); p.put("tmp_" + wid, 0); }
        else if (ACT_TOGGLE.equals(a) && wid >= 0) {
            boolean monthNow = "month".equals(p.view(wid)) || p.i("tmp_" + wid, 0) == 1;
            p.setView(wid, monthNow ? "rolling" : "month"); p.setOffset(wid, 0); p.put("tmp_" + wid, 0);
        }
        else if (!ACT_REFRESH.equals(a)) return;
        refreshAll(c);
    }

    static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, ShiftWidget.class));
        for (int id : ids) m.updateAppWidget(id, safeBuild(c, id, m.getAppWidgetOptions(id)));
        schedule(c);
    }

    /** Redraw when the calendar changes, and just after midnight. */
    static void schedule(Context c) { schedule(c, false); }
    static void schedule(Context c, boolean force) {
        try {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (force || js.getPendingJob(1) == null) {
                JobInfo job = new JobInfo.Builder(1, new ComponentName(c, UpdateJob.class))
                    .addTriggerContentUri(new JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                    .setTriggerContentUpdateDelay(1500).setTriggerContentMaxDelay(8000).build();
                js.schedule(job);
            }
        } catch (Exception ignored) { }
        try {
            ZoneId z = ZoneId.of(new Prefs(c).tz());
            long next = LocalDate.now(z).plusDays(1).atStartOfDay(z).toInstant().toEpochMilli() + 20000;
            AlarmManager am = c.getSystemService(AlarmManager.class);
            am.set(AlarmManager.RTC, next, bcast(c, ACT_REFRESH, -1, 9));
            // move the current-time line: redraw on each 10-minute mark while the phone is awake
            long tick = 600000L, at = (System.currentTimeMillis() / tick + 1) * tick + 2000;
            am.set(AlarmManager.RTC, at, bcast(c, ACT_REFRESH, -1, 8));
        } catch (Exception ignored) { }
    }

    private static PendingIntent bcast(Context c, String action, int wid, int req) {
        Intent i = new Intent(c, ShiftWidget.class).setAction(action).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid);
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
    private static PendingIntent act(Context c, Intent i, int req) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static RemoteViews safeBuild(Context c, int wid, Bundle opts) {
        try { return build(c, wid, opts); }
        catch (Exception e) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
            rv.setViewVisibility(R.id.weeks, View.GONE);
            rv.setViewVisibility(R.id.msg, View.VISIBLE);
            rv.setTextColor(R.id.msg, 0xFFFFFFFF);
            rv.setInt(R.id.bg, "setColorFilter", 0xFF101A16);
            rv.setTextViewText(R.id.msg, "Shift Calendar hit a problem drawing this view:\n" + e + "\nTap to open settings.");
            rv.setOnClickPendingIntent(R.id.msg, act(c, new Intent(c, SettingsActivity.class), wid * 10 + 6));
            return rv;
        }
    }

    /** One row of day boxes. */
    private static final class Row { LocalDate start; int n; boolean small; float w = 1f; float win0 = 4 * 60f, win1 = 24 * 60f; }
    /** Shared numbers for one redraw. */
    private static final class G {
        Context c; Prefs p; Pal pal; ZoneId zone; LocalDate today; LocalDate monthFirst; boolean month, detailed, timeline, side, bandTop;
        float tdp, lineH; int fixedMax, t0, codeCol; String codeMode;
        int evPct, dimA, curTile; boolean inv; float lastBand;
        List<String> keys = new ArrayList<String>();
        List<Pattern> sufP = new ArrayList<Pattern>(); List<Integer> sufC = new ArrayList<Integer>();
    }

    static RemoteViews build(Context c, int wid, Bundle opts) {
        G g = new G();
        g.c = c; g.p = new Prefs(c); g.pal = Pal.of(g.p); g.zone = ZoneId.of(g.p.tz());
        Prefs p = g.p; Pal pal = g.pal;
        H12 = "12".equals(p.s("clock", "24"));

        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
        rv.setInt(R.id.bg, "setColorFilter", pal.bg);
        rv.setInt(R.id.bg, "setImageAlpha", Math.round(p.opacity() * 2.55f));
        for (int id : new int[] { R.id.prev, R.id.next, R.id.title, R.id.add, R.id.today, R.id.settings }) rv.setTextColor(id, pal.fg);

        LocalDate today = g.today = LocalDate.now(g.zone);
        rv.setTextViewText(R.id.today, String.valueOf(today.getDayOfMonth()));
        rv.setInt(R.id.today, "setBackgroundResource", pal.dark ? R.drawable.outline_light : R.drawable.outline_dark);
        rv.setOnClickPendingIntent(R.id.prev, bcast(c, ACT_PREV, wid, wid * 10 + 1));
        rv.setOnClickPendingIntent(R.id.next, bcast(c, ACT_NEXT, wid, wid * 10 + 2));
        rv.setOnClickPendingIntent(R.id.today, bcast(c, ACT_TODAY, wid, wid * 10 + 3));
        rv.setOnClickPendingIntent(R.id.settings, act(c, new Intent(c, SettingsActivity.class), wid * 10 + 4));
        Intent add = new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI);
        rv.setOnClickPendingIntent(R.id.add, act(c, add, wid * 10 + 5));

        if (c.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            rv.setViewVisibility(R.id.weeks, View.GONE);
            rv.setViewVisibility(R.id.dow, View.GONE);
            rv.setViewVisibility(R.id.msg, View.VISIBLE);
            rv.setTextColor(R.id.msg, pal.fg);
            rv.setTextViewText(R.id.msg, "Tap here to let Shift Calendar read your calendar");
            rv.setOnClickPendingIntent(R.id.msg, act(c, new Intent(c, SettingsActivity.class), wid * 10 + 6));
            rv.setTextViewText(R.id.title, "Shifts");
            return rv;
        }

        // ----- which days to show -----
        g.month = "month".equals(p.view(wid)) || p.i("tmp_" + wid, 0) == 1;
        DayOfWeek weekStart = p.sundayFirst() ? DayOfWeek.SUNDAY : DayOfWeek.MONDAY;
        List<Row> rows = new ArrayList<Row>();
        String title;
        if (g.month) {
            LocalDate first = g.monthFirst = today.withDayOfMonth(1).plusMonths(p.offset(wid));
            title = first.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase(Locale.ROOT);
            if (first.getYear() != today.getYear()) title += " " + first.getYear();
            int lead = (first.getDayOfWeek().getValue() - weekStart.getValue() + 7) % 7;
            int weeks = (lead + first.lengthOfMonth() + 6) / 7;
            LocalDate start = first.minusDays(lead);
            for (int w = 0; w < weeks; w++) {
                Row r = new Row(); r.start = start.plusDays(w * 7L); r.n = 7;
                if (r.start.plusDays(6).isBefore(today)) r.w = SMALL;     // whole week already gone
                else r.w = taper(p.taper(), (int) Math.max(0, java.time.temporal.ChronoUnit.WEEKS.between(today, r.start.plusDays(6))));
                rows.add(r);
            }
        } else {
            boolean aligned = p.aligned();
            LocalDate start = today;
            if (aligned) start = start.minusDays((start.getDayOfWeek().getValue() - weekStart.getValue() + 7) % 7);
            start = start.plusWeeks(p.offset(wid));
            int past = Math.max(0, Math.min(7, p.past()));
            if (aligned && past > 0) past = 7;
            if (past > 0) { Row r = new Row(); r.start = start.minusDays(past); r.n = past; r.w = SMALL; rows.add(r); }
            int n = Math.max(1, Math.min(6, p.rows()));
            for (int w = 0; w < n; w++) {
                Row r = new Row(); r.start = start.plusDays(w * 7L); r.n = 7;
                long ahead = java.time.temporal.ChronoUnit.WEEKS.between(today, r.start.plusDays(6));
                r.w = r.start.plusDays(6).isBefore(today) ? SMALL : taper(p.taper(), (int) Math.max(0, ahead));
                rows.add(r);
            }
            title = shortDate(start) + " – " + shortDate(start.plusDays(n * 7L - 1));
        }
        rv.setTextViewText(R.id.title, title + " ▾");
        rv.setOnClickPendingIntent(R.id.title, bcast(c, ACT_TOGGLE, wid, wid * 10 + 7));
        LocalDate colStart = rows.get(rows.size() - 1).start;
        for (int i = 0; i < 7; i++) {
            DayOfWeek dw = colStart.plusDays(i).getDayOfWeek();
            rv.setTextViewText(DOW_IDS[i], DOW_TXT[dw.getValue() - 1]);
            rv.setTextColor(DOW_IDS[i], dw == DayOfWeek.SUNDAY ? pal.sun : pal.muted);
        }

        LocalDate from = rows.get(0).start, to = colStart.plusDays(7);
        Map<LocalDate, List<Events.Item>> map;
        try { map = Events.load(c, p, from, to); }
        catch (Exception e) { map = Collections.emptyMap(); }

        // ----- space available, in dp -----
        float wDp = 0, hDp = 0;
        if (opts != null) {
            ArrayList<SizeF> sizes = opts.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
            if (sizes != null) for (SizeF s : sizes) if (s.getHeight() > hDp) { hDp = s.getHeight(); wDp = s.getWidth(); }
            if (hDp <= 0) { wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0); hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0); }
        }
        if (wDp <= 0) wDp = 340; if (hDp <= 0) hDp = 480;
        float units = 0;
        for (Row r : rows) { r.w = Math.round(r.w * 20) / 20f; r.small = r.w < 0.58f; units += r.w; }
        float unitH = (hDp - 5 - 30 - 14) / units;

        g.tdp = p.textDp(); g.lineH = g.tdp * 1.18f;
        g.detailed = "detailed".equals(p.density());
        g.fixedMax = p.max(); g.timeline = p.timeline(); g.t0 = Math.min(12, p.dayStart());
        for (String line : p.srules().split("\n")) {
            int eq = line.lastIndexOf('='); if (eq < 1) continue;
            String k = line.substring(0, eq).trim(); if (k.isEmpty()) continue;
            try { g.sufP.add(Pattern.compile("(?i)(?<![A-Za-z0-9])" + Pattern.quote(k) + "(?![A-Za-z0-9])")); g.sufC.add(Pal.hex(line.substring(eq + 1), pal.accent)); } catch (Exception ignored) { }
        }
        g.keys = Events.keys(p);
        g.evPct = Math.max(0, Math.min(100, p.i("op_ev", 28)));
        g.dimA = Math.round(Math.max(20, Math.min(100, p.i("op_pastev", 60))) * 2.55f);
        g.codeMode = p.code(); g.codeCol = "custom".equals(g.codeMode) ? Pal.hex(p.s("c_code", ""), pal.accent) : pal.accent;
        g.side = p.sideBySide(); g.bandTop = "top".equals(p.allday());

        rv.removeAllViews(R.id.weeks);
        for (Row r : rows) {
            RemoteViews row = new RemoteViews(c.getPackageName(), ROW_LAYOUTS[Math.max(0, Math.min(12, Math.round(r.w * 20) - 8))]);
            float rowH = unitH * r.w;
            window(g, r, map);
            float cellW = (wDp - 4) / r.n - 1;
            for (int d = 0; d < r.n; d++) {
                LocalDate day = r.start.plusDays(d);
                row.addView(R.id.cells, cell(g, day, map.get(day), r, cellW, rowH));
            }
            rv.addView(R.id.weeks, row);
        }
        return rv;
    }

    /** A day box, plus a thin line across today's box at the current time. */
    private static RemoteViews cell(G g, LocalDate day, List<Events.Item> items, Row r, float cellW, float rowH) {
        g.lastBand = 0;
        RemoteViews cell = cell0(g, day, items, r, cellW, rowH);
        if (day.equals(g.today)) {
            try {
                ZonedDateTime now = ZonedDateTime.now(g.zone);
                float min = now.getHour() * 60 + now.getMinute(), head = r.small ? 14 : 19;
                float area = rowH - 1 - 2 - head - 3 - g.lastBand;
                float frac = Math.max(0f, Math.min(1f, (min - r.win0) / Math.max(1f, r.win1 - r.win0)));
                float y = 2.5f + head + (g.bandTop ? g.lastBand : 0) + frac * Math.max(0, area - 1.5f);
                cell.setViewVisibility(R.id.now, View.VISIBLE);
                cell.setInt(R.id.now, "setColorFilter", g.pal.sun);
                cell.setViewLayoutMargin(R.id.now, RemoteViews.MARGIN_TOP, y, DIP);
                cell.setViewLayoutMargin(R.id.now, RemoteViews.MARGIN_LEFT, 2.5f, DIP);
                cell.setViewLayoutMargin(R.id.now, RemoteViews.MARGIN_RIGHT, 2.5f, DIP);
            } catch (Exception ignored) { }
        }
        return cell;
    }
    private static RemoteViews cell0(G g, LocalDate day, List<Events.Item> items, Row r, float cellW, float rowH) {
        Context c = g.c; Pal pal = g.pal;
        boolean small = r.small;
        boolean out = g.month && day.getMonthValue() != g.monthFirst.getMonthValue();
        boolean past = day.isBefore(g.today), isToday = day.equals(g.today);
        boolean dim = out || past;
        RemoteViews cell = new RemoteViews(c.getPackageName(), small ? R.layout.cell_small : R.layout.cell);

        // tile colours: darker for past, lighter for upcoming, outlined for today
        if (isToday) {
            cell.setInt(R.id.tile, "setColorFilter", pal.accent);
            cell.setViewVisibility(R.id.tile_in, View.VISIBLE);
            cell.setInt(R.id.tile_in, "setColorFilter", pal.today);
            g.curTile = pal.today;
            cell.setInt(R.id.tile_in, "setImageAlpha", Math.round(g.p.op("op_today") * 2.55f));
            int pad = Math.round(2.5f * c.getResources().getDisplayMetrics().density);
            cell.setViewPadding(R.id.inner, pad, pad, pad, pad);
        } else {
            int tile = past ? pal.past : pal.future;
            if (out && !past) tile = Pal.mix(pal.future, pal.past, 0.5f);
            cell.setInt(R.id.tile, "setColorFilter", tile);
            g.curTile = tile;
            cell.setInt(R.id.tile, "setImageAlpha", Math.round(g.p.op(past ? "op_past" : "op_future") * 2.55f));
        }

        // date label
        String num = String.valueOf(day.getDayOfMonth());
        if (!g.month && r.n != 7) num = DOW_TXT[day.getDayOfWeek().getValue() - 1] + " " + num;
        else if (!g.month && day.getDayOfMonth() == 1) num = num + " " + day.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT);
        cell.setTextViewText(R.id.num, num);
        int numCol = day.getDayOfWeek() == DayOfWeek.SUNDAY ? pal.sun : pal.fg;
        if (isToday) cell.setTextColor(R.id.num, pal.accent);
        else cell.setTextColor(R.id.num, dim ? Pal.alpha(numCol, out ? 0x55 : Math.max(0x80, g.dimA)) : numCol);

        Intent open = new Intent(c, DayActivity.class).setData(Uri.parse("shiftcal://day/" + day));
        cell.setOnClickPendingIntent(R.id.cell, act(c, open, 0));
        if (items == null || items.isEmpty()) return cell;

        float innerW = cellW - 2 - (isToday ? 3 : 0);
        float innerH = rowH - 1 - 2 - (small ? 14 : 19) - (isToday ? 3 : 0);
        List<Events.Item> marks = new ArrayList<Events.Item>(), timed = new ArrayList<Events.Item>();
        for (Events.Item it : items) (it.allDay ? marks : timed).add(it);

        // ----- all-day markers: one fixed band per day -----
        float bandH = 0;
        if (!marks.isEmpty()) {
            float mdp = Math.max(9f, g.tdp - 1f), mLine = mdp * 1.18f;
            int bandId = g.bandTop ? R.id.band_top : R.id.band_bot;
            float maxBand = Math.max(mLine + 3, innerH * (timed.isEmpty() ? 0.9f : 0.45f));
            // every marker is exactly one line at the same height, so the band never competes with the timed events
            float one = mLine + 3;
            if (small || g.side || marks.size() == 1) {
                bandH = one;
                addBandRow(g, cell, bandId, marks.subList(0, Math.min(marks.size(), 3)), mdp, 1, one, dim);
            } else {
                int n = Math.max(1, Math.min(Math.min(marks.size(), 3), (int) Math.floor(maxBand / one)));
                for (int i = 0; i < n; i++) addBandRow(g, cell, bandId, marks.subList(i, i + 1), mdp, 1, one, dim);
                bandH = n * one;
            }
        }
        g.lastBand = bandH;
        if (timed.isEmpty()) return cell;

        // ----- timed events -----
        float area = innerH - bandH;
        float cpl = Math.max(3f, (innerW - 6) / (g.tdp * 0.47f));
        if (!small && g.timeline && area >= 64 && timeline(g, cell, timed, day, area, cpl, dim, r)) return cell;
        list(g, cell, timed, area, cpl, dim, small);
        return cell;
    }

    private static void addBandRow(G g, RemoteViews cell, int bandId, List<Events.Item> marks, float mdp, int lines, float h, boolean dim) {
        RemoteViews row = new RemoteViews(g.c.getPackageName(), R.layout.band_row);
        row.setViewLayoutHeight(R.id.row, h - 1, DIP);
        row.setViewLayoutMargin(R.id.row, g.bandTop ? RemoteViews.MARGIN_BOTTOM : RemoteViews.MARGIN_TOP, 1, DIP);
        for (Events.Item it : marks) {
            RemoteViews m = new RemoteViews(g.c.getPackageName(), R.layout.marker);
            m.setInt(R.id.txt, "setBackgroundColor", Pal.alpha(it.color, dim ? g.dimA : 0xFF));
            m.setTextColor(R.id.txt, Pal.lum(it.color) > 0.62 ? (dim ? Pal.alpha(0xFF101410, Math.max(0xB3, g.dimA)) : 0xFF101410) : (dim ? Pal.alpha(0xFFFFFFFF, Math.max(0xB3, g.dimA)) : 0xFFFFFFFF));
            m.setTextViewText(R.id.txt, it.title.isEmpty() ? "(no title)" : Events.display(it.title, g.keys, true));
            m.setTextViewTextSize(R.id.txt, DIP, mdp);
            m.setInt(R.id.txt, "setMaxLines", lines);
            row.addView(R.id.row, m);
        }
        cell.addView(bandId, row);
    }

    /** Works out which hours the time-of-day layout covers for one row, so empty early hours do not waste space. */
    private static void window(G g, Row r, Map<LocalDate, List<Events.Item>> map) {
        if (g.t0 >= 0) { r.win0 = g.t0 * 60f; r.win1 = 24 * 60f; return; }
        float lo = 24 * 60f, hi = 0;
        for (int d = 0; d < r.n; d++) {
            LocalDate day = r.start.plusDays(d);
            List<Events.Item> items = map.get(day);
            if (items == null) continue;
            long ds = day.atStartOfDay(g.zone).toInstant().toEpochMilli();
            for (Events.Item it : items) {
                if (it.allDay) continue;
                float s = it.cont ? 0 : (it.begin - ds) / 60000f, e = Math.min(24 * 60f, (it.end - ds) / 60000f);
                if (it.cont) { hi = Math.max(hi, e); continue; }   // a carry-over from last night does not drag the start back to midnight
                lo = Math.min(lo, s); hi = Math.max(hi, e);
            }
        }
        if (lo >= hi) { lo = 8 * 60f; hi = 18 * 60f; }
        lo = (float) Math.floor(lo / 60f) * 60f; hi = (float) Math.ceil(hi / 60f) * 60f;
        if (hi - lo < 8 * 60f) hi = Math.min(24 * 60f, lo + 8 * 60f);
        if (hi - lo < 8 * 60f) lo = hi - 8 * 60f;
        r.win0 = lo; r.win1 = hi;
    }

    /** Places each event by its time of day and length. Returns false if they cannot all fit. */
    private static boolean timeline(G g, RemoteViews cell, List<Events.Item> timed, LocalDate day, float area, float cpl, boolean dim, Row r) {
        int n = timed.size();
        long dayStart = day.atStartOfDay(g.zone).toInstant().toEpochMilli();
        float winStart = r.win0, winLen = r.win1 - r.win0, gap = 1.5f;
        float[] top = new float[n], h = new float[n];
        CharSequence[] text = new CharSequence[n];
        for (int i = 0; i < n; i++) {
            Events.Item it = timed.get(i);
            float s = it.cont ? 0 : (it.begin - dayStart) / 60000f;
            float e = Math.min(24 * 60f, (it.end - dayStart) / 60000f);
            float y0 = Math.max(0, Math.min(1, (s - winStart) / winLen)) * area, y1 = Math.max(0, Math.min(1, (e - winStart) / winLen)) * area;
            text[i] = label(g, it, dim);
            int need = g.detailed ? Math.max(1, Math.min(3, (int) Math.ceil(text[i].length() / cpl))) : 1;
            top[i] = y0; h[i] = Math.max(y1 - y0, need * g.lineH + 2);
        }
        for (int i = 1; i < n; i++) top[i] = Math.max(top[i], top[i - 1] + h[i - 1] + gap);
        float limit = area;
        for (int i = n - 1; i >= 0; i--) {
            if (top[i] + h[i] > limit) top[i] = limit - h[i];
            limit = top[i] - gap;
        }
        if (top[0] < 0) {                       // too tall: trim the long blocks back to their text height
            for (int i = 0; i < n; i++) { int need = Math.max(1, Math.min(2, (int) Math.ceil(text[i].length() / cpl))); h[i] = Math.min(h[i], need * g.lineH + 2); }
            limit = area;
            for (int i = n - 1; i >= 0; i--) { if (top[i] + h[i] > limit) top[i] = limit - h[i]; limit = top[i] - gap; }
            if (top[0] < 0) return false;
        }
        float prev = 0;
        for (int i = 0; i < n; i++) {
            RemoteViews chip = chip(g, timed.get(i), text[i], dim);
            chip.setViewLayoutMargin(R.id.chip, RemoteViews.MARGIN_TOP, Math.max(0, top[i] - prev), DIP);
            chip.setViewLayoutHeight(R.id.chip, h[i], DIP);
            chip.setInt(R.id.txt, "setMaxLines", Math.max(1, (int) Math.floor((h[i] - 0.5f) / g.lineH)));
            cell.addView(R.id.evs, chip);
            prev = top[i] + h[i];
        }
        return true;
    }

    /** Simple top-down list, with "+N" when they do not all fit. */
    private static void list(G g, RemoteViews cell, List<Events.Item> timed, float area, float cpl, boolean dim, boolean small) {
        float used = 0; int shown = 0, n = timed.size();
        for (int i = 0; i < n; i++) {
            Events.Item it = timed.get(i);
            CharSequence text = label(g, it, dim);
            if (small) text = label(g, it, dim, false);
            int need = !g.detailed || small ? 1 : Math.min(3, (int) Math.ceil(text.length() / cpl));
            int maxLines = need;
            boolean moreAfter = i < n - 1;
            if (g.fixedMax > 0 && !small) {
                if (shown >= g.fixedMax) break;
            } else {
                float left = area - used - (moreAfter ? g.lineH : 0);
                int room = (int) Math.floor((left - 2) / g.lineH);
                if (room <= 0) break;
                if (need > room) maxLines = room;
            }
            RemoteViews chip = chip(g, it, text, dim);
            chip.setViewLayoutMargin(R.id.chip, RemoteViews.MARGIN_TOP, 2, DIP);
            chip.setInt(R.id.txt, "setMaxLines", maxLines);
            cell.addView(R.id.evs, chip);
            used += maxLines * g.lineH + 2; shown++;
        }
        if (shown < n) {
            RemoteViews more = new RemoteViews(g.c.getPackageName(), R.layout.more);
            more.setTextViewText(R.id.txt, "+" + (n - shown));
            more.setTextColor(R.id.txt, g.pal.muted);
            more.setTextViewTextSize(R.id.txt, DIP, g.tdp);
            cell.addView(R.id.evs, more);
        }
    }

    private static RemoteViews chip(G g, Events.Item it, CharSequence text, boolean dim) {
        RemoteViews chip = new RemoteViews(g.c.getPackageName(), R.layout.chip);
        int fg = inv(g, it) ? 0xFF101410 : g.pal.fg;
        chip.setInt(R.id.bar, "setBackgroundColor", Pal.alpha(it.color, dim ? g.dimA : 0xFF));
        chip.setInt(R.id.chip, "setBackgroundColor", Pal.alpha(it.color, Math.round(g.evPct * 2.55f * (dim ? g.dimA / 255f : 1f))));
        chip.setTextColor(R.id.txt, dim ? Pal.alpha(fg, g.dimA) : fg);
        chip.setTextViewText(R.id.txt, text);
        chip.setTextViewTextSize(R.id.txt, DIP, g.tdp);
        return chip;
    }

    /** True when the event block is solid and light enough that dark text reads better on it. */
    private static boolean inv(G g, Events.Item it) {
        return g.evPct >= 50 && Pal.lum(Pal.mix(g.curTile, it.color, g.evPct / 100f)) > 0.58;
    }
    /** Time stands out, a leading code such as ACT or EPA is bold, and "7.5hrs" is smaller. */
    private static CharSequence label(G g, Events.Item it, boolean dim) { return label(g, it, dim, g.detailed); }
    private static CharSequence label(G g, Events.Item it, boolean dim, boolean withTime) {
        final boolean inv = inv(g, it);
        final int muted = inv ? 0xFF3A3F3C : g.pal.muted;
        String t = it.title;
        if (it.cancelled) t = t.replaceFirst("(?i)^\\s*cancel+ed\\s*[-–—:]*\\s*", "");
        // a colour code at the end of the title has done its job by colouring the event, so it is not drawn
        t = Events.display(t, g.keys, it.allDay);
        if (t.isEmpty()) t = "(no title)";
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int flag = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        if (withTime || it.cont) {
            String time = it.cont ? (withTime ? "→" + hm(it.end, g.zone) : "→") : hm(it.begin, g.zone);
            sb.append(time);
            sb.setSpan(new StyleSpan(Typeface.BOLD), 0, time.length(), flag);
            if (!dim && !it.cancelled) sb.setSpan(new ForegroundColorSpan(g.pal.dark && !inv ? 0xFFFFFFFF : 0xFF000000), 0, time.length(), flag);
            sb.append(' ');
        }
        int s = sb.length();
        sb.append(t);
        if (it.cancelled) {
            sb.setSpan(new StrikethroughSpan(), s, sb.length(), flag);
            sb.setSpan(new ForegroundColorSpan(Pal.alpha(muted, dim ? g.dimA : 0xFF)), 0, sb.length(), flag);
            return sb;
        }
        Matcher code = CODE.matcher(t);
        if (code.find()) {
            sb.setSpan(new StyleSpan(Typeface.BOLD), s, s + code.end(), flag);
            if (!"text".equals(g.codeMode) && !inv) {
                int cc = "event".equals(g.codeMode) ? Pal.mix(it.color, g.pal.dark ? 0xFFFFFFFF : 0xFF000000, 0.45f) : g.codeCol;
                sb.setSpan(new ForegroundColorSpan(Pal.alpha(cc, dim ? g.dimA : 0xFF)), s, s + code.end(), flag);
            }
        }
        // suffix abbreviations (venue codes such as hmt, rac): their own colour, after the prefix
        int from = code.find(0) ? code.end() : 0;
        for (int k = 0; k < g.sufP.size(); k++) {
            Matcher m = g.sufP.get(k).matcher(t);
            if (m.find(from)) {
                sb.setSpan(new StyleSpan(Typeface.BOLD), s + m.start(), s + m.end(), flag);
                sb.setSpan(new ForegroundColorSpan(Pal.alpha(g.sufC.get(k), dim ? g.dimA : 0xFF)), s + m.start(), s + m.end(), flag);
            }
        }
        Matcher hrs = HOURS.matcher(t);
        while (hrs.find()) {
            sb.setSpan(new RelativeSizeSpan(0.82f), s + hrs.start(), s + hrs.end(), flag);
            sb.setSpan(new ForegroundColorSpan(Pal.alpha(muted, dim ? g.dimA : 0xFF)), s + hrs.start(), s + hrs.end(), flag);
        }
        return sb;
    }

    static String shortDate(LocalDate d) {
        return d.getDayOfMonth() + " " + d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT);
    }
    static volatile boolean H12;
    static String hm(long ms, ZoneId z) {
        ZonedDateTime t = Instant.ofEpochMilli(ms).atZone(z);
        if (!H12) return String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
        int h = t.getHour() % 12; if (h == 0) h = 12;
        String ap = t.getHour() < 12 ? "am" : "pm";
        return t.getMinute() == 0 ? h + ap : String.format(Locale.ROOT, "%d:%02d%s", h, t.getMinute(), ap);
    }
}
