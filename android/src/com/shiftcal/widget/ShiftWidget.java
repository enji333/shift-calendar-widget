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
import android.text.style.TypefaceSpan;
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
    static float SMALL = 0.45f;
    static final int[] ROW_LAYOUTS = { R.layout.week_row_40, R.layout.week_row_45, R.layout.week_row_50, R.layout.week_row_55,
        R.layout.week_row_60, R.layout.week_row_65, R.layout.week_row_70, R.layout.week_row_75, R.layout.week_row_80,
        R.layout.week_row_85, R.layout.week_row_90, R.layout.week_row_95, R.layout.week_row_100 };
    /** Row height for a week that is i weeks after the current one. */
    static String TAPC = "";
    static float taper(String mode, int i) {
        if ("custom".equals(mode)) {
            List<Float> l = new ArrayList<Float>();
            for (String x : TAPC.split("[,\\s]+")) try { if (!x.isEmpty()) l.add(Math.max(0.4f, Math.min(1f, Float.parseFloat(x) / 100f))); } catch (Exception ignored) { }
            if (!l.isEmpty()) return l.get(Math.max(0, Math.min(i, l.size() - 1)));
        }
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
        // finer controls from the desktop panel (defaults match the phone settings page)
        int maxLines = 3, maxMarks = 3, minWin = 8; float mdp, numDp, headBig = 19, headSmall = 14, todayPad = 2.5f, nowH = 1.5f, barW = 3;
        String font = ""; boolean timeBold = true, hrsSmall = true, upper, showLoc, codeBold = true, nowLine = true;
        // WA outback look (v3.1)
        boolean wkTint, satRed, grid, numLeft; float gridW = 1;
        float head(boolean small) { return small ? headSmall : headBig; }
        List<String> keys = new ArrayList<String>();
        List<Pattern> sufP = new ArrayList<Pattern>(); List<Integer> sufC = new ArrayList<Integer>();
    }

    static RemoteViews build(Context c, int wid, Bundle opts) {
        G g = new G();
        g.c = c; g.p = new Prefs(c); g.pal = Pal.of(g.p); g.zone = ZoneId.of(g.p.tz());
        Prefs p = g.p; Pal pal = g.pal;
        H12 = "12".equals(p.s("clock", "24"));
        SMALL = Math.max(0.4f, Math.min(1f, p.i("small_pct", 45) / 100f));
        TAPC = p.s("tapc", "");
        g.maxLines = Math.max(1, Math.min(8, p.i("maxl", 3)));
        g.maxMarks = Math.max(1, Math.min(6, p.i("maxmk", 3)));
        g.minWin = Math.max(1, Math.min(24, p.i("minwin", 8)));
        g.font = p.s("font", "").trim();
        g.timeBold = p.on("tbold", true); g.hrsSmall = p.on("hrs", true); g.upper = p.on("upper", false);
        g.showLoc = p.on("loc", false); g.codeBold = p.on("cbold", true); g.nowLine = p.on("nowline", true);
        g.numDp = p.tenths("ndp10", 0);
        if (g.numDp > 0) { g.headBig = Math.max(10, g.numDp + 3.5f); g.headSmall = Math.max(8, g.numDp * 0.74f + 2.5f); }
        g.todayPad = Math.max(0, Math.min(8, p.i("tpad10", 25) / 10f));
        g.nowH = Math.max(0.5f, Math.min(6, p.i("nowh10", 15) / 10f));
        g.barW = Math.max(0, Math.min(10, p.i("barw10", 30) / 10f));
        g.wkTint = p.on("wktint", false); g.satRed = p.on("satred", false); g.grid = p.on("grid", false);
        g.gridW = Math.max(0.5f, Math.min(6, p.i("gridw10", 10) / 10f)); g.numLeft = "left".equals(p.s("numpos", "center"));
        float hh = Math.max(24, Math.min(90, p.i("hh", 30)));

        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
        rv.setInt(R.id.bg, "setColorFilter", pal.bg);
        rv.setInt(R.id.bg, "setImageAlpha", Math.round(p.opacity() * 2.55f));
        for (int id : new int[] { R.id.prev, R.id.next, R.id.title, R.id.add, R.id.today, R.id.settings }) rv.setTextColor(id, pal.head);
        if (!p.on("h_prev", true)) rv.setViewVisibility(R.id.prev, View.GONE);
        if (!p.on("h_next", true)) rv.setViewVisibility(R.id.next, View.GONE);
        if (!p.on("h_add", true)) rv.setViewVisibility(R.id.add, View.GONE);
        if (!p.on("h_today", true)) rv.setViewVisibility(R.id.today, View.GONE);
        if (!p.on("h_set", true)) rv.setViewVisibility(R.id.settings, View.GONE);
        if (!p.on("h_head", true)) rv.setViewVisibility(R.id.header, View.GONE);
        if (!p.on("h_dow", true)) rv.setViewVisibility(R.id.dow, View.GONE);
        if (p.tenths("hdp10", 0) > 0) rv.setTextViewTextSize(R.id.title, DIP, p.tenths("hdp10", 15));
        if (hh != 30) rv.setViewLayoutHeight(R.id.header, hh, DIP);
        // landscape header in the outback palette
        int sceneRes = Look.sceneRes(p.s("scene", "")); float sceneH = 0;
        if (sceneRes != 0) {
            sceneH = Math.max(24, Math.min(240, p.i("scene_h", 72)));
            int sid = "bottom".equals(p.s("scene_pos", "top")) ? R.id.scene_bot : R.id.scene_top;
            rv.setViewVisibility(sid, View.VISIBLE);
            rv.setImageViewResource(sid, sceneRes);
            rv.setViewLayoutHeight(sid, sceneH, DIP);
            rv.setInt(sid, "setImageAlpha", Math.round(Math.max(10, Math.min(100, p.i("scene_op", 100))) * 2.55f));
            rv.setOnClickPendingIntent(sid, bcast(c, ACT_TOGGLE, wid, wid * 10 + 8));
            sceneH += 2;
        }

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
        String tail = "";
        if (g.month && p.on("tyear", false)) {
            title = g.monthFirst.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase(Locale.ROOT);
            tail = " " + g.monthFirst.getYear();
        }
        SpannableStringBuilder tsb = new SpannableStringBuilder(title);
        if (!tail.isEmpty()) { int t0 = tsb.length(); tsb.append(tail); tsb.setSpan(new ForegroundColorSpan(pal.sun), t0, tsb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); }
        if (p.on("h_arrow", true)) tsb.append(" ▾");
        if (!g.font.isEmpty()) tsb.setSpan(new TypefaceSpan(g.font), 0, tsb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        rv.setTextViewText(R.id.title, tsb);
        rv.setOnClickPendingIntent(R.id.title, bcast(c, ACT_TOGGLE, wid, wid * 10 + 7));
        final String titleMain = title, titleTail = tail;
        LocalDate colStart = rows.get(rows.size() - 1).start;
        for (int i = 0; i < 7; i++) {
            DayOfWeek dw = colStart.plusDays(i).getDayOfWeek();
            String dl = DOW_TXT[dw.getValue() - 1], ds = p.s("dows", "upper3");
            if ("title3".equals(ds)) dl = dl.charAt(0) + dl.substring(1).toLowerCase(Locale.ROOT);
            else if ("one".equals(ds)) dl = dl.substring(0, 1);
            else if ("two".equals(ds)) dl = dl.charAt(0) + dl.substring(1, 2).toLowerCase(Locale.ROOT);
            rv.setTextViewText(DOW_IDS[i], font(g, dl));
            rv.setTextColor(DOW_IDS[i], dw == DayOfWeek.SUNDAY || (g.satRed && dw == DayOfWeek.SATURDAY) ? pal.sunday : pal.dow);
            if (p.tenths("dowdp10", 0) > 0) rv.setTextViewTextSize(DOW_IDS[i], DIP, p.tenths("dowdp10", 10.5f));
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
        // retro title font: drawn as a picture, since widget text can only use the phone's fonts
        Typeface tf = p.on("h_head", true) ? Look.font(c, p.s("tfont", "")) : null;
        if (tf != null) {
            try {
                float tsz = p.tenths("hdp10", 15);
                rv.setImageViewBitmap(R.id.title_img, Look.title(c, tf, titleMain, titleTail, p.on("h_arrow", true), pal.head, pal.sun, tsz, hh - 4, Math.max(120, wDp - 190)));
                rv.setViewVisibility(R.id.title_img, View.VISIBLE);
                rv.setViewVisibility(R.id.title, View.GONE);
                rv.setOnClickPendingIntent(R.id.title_img, bcast(c, ACT_TOGGLE, wid, wid * 10 + 7));
            } catch (Throwable ignored) { }
        }
        float units = 0;
        for (Row r : rows) { r.w = Math.round(r.w * 20) / 20f; r.small = r.w < 0.58f; units += r.w; }
        float gridPad = g.grid ? g.gridW : 0;
        float unitH = (hDp - 5 - (p.on("h_head", true) ? hh : 0) - (p.on("h_dow", true) ? 14 : 0) - sceneH - gridPad) / units;
        if (g.grid) {
            rv.setViewVisibility(R.id.gridbg, View.VISIBLE);
            rv.setInt(R.id.gridbg, "setColorFilter", pal.grid);
            rv.setInt(R.id.gridbg, "setImageAlpha", Math.round(g.p.op("op_future") * 2.55f));
            int pad = Math.round(g.gridW / 2f * c.getResources().getDisplayMetrics().density);
            rv.setViewPadding(R.id.weeks, pad, pad, pad, pad);
        }

        g.tdp = p.textDp(); g.lineH = g.tdp * Math.max(0.9f, Math.min(2f, p.i("lh", 118) / 100f));
        g.mdp = p.tenths("mdp10", Math.max(9f, g.tdp - 1f));
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
            float cellW = g.grid ? (wDp - 4 - g.gridW) / r.n - g.gridW : (wDp - 4) / r.n - 1;
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
        if (day.equals(g.today) && g.nowLine) {
            try {
                ZonedDateTime now = ZonedDateTime.now(g.zone);
                float min = now.getHour() * 60 + now.getMinute(), head = g.head(r.small);
                float area = rowH - 1 - 2 - head - 3 - g.lastBand;
                float frac = Math.max(0f, Math.min(1f, (min - r.win0) / Math.max(1f, r.win1 - r.win0)));
                float y = 2.5f + head + (g.bandTop ? g.lastBand : 0) + frac * Math.max(0, area - 1.5f);
                cell.setViewVisibility(R.id.now, View.VISIBLE);
                cell.setInt(R.id.now, "setColorFilter", g.pal.now);
                if (g.nowH != 1.5f) cell.setViewLayoutHeight(R.id.now, g.nowH, DIP);
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
        RemoteViews cell = new RemoteViews(c.getPackageName(), g.numLeft ? (small ? R.layout.cell_small_l : R.layout.cell_l) : (small ? R.layout.cell_small : R.layout.cell));
        DayOfWeek dw = day.getDayOfWeek();
        boolean weekend = dw == DayOfWeek.SATURDAY || dw == DayOfWeek.SUNDAY;
        if (g.grid) {
            // square boxes with the grid colour showing in the gaps between them
            float m = g.gridW / 2f;
            cell.setViewLayoutMargin(R.id.cell, RemoteViews.MARGIN_LEFT, m, DIP);
            cell.setViewLayoutMargin(R.id.cell, RemoteViews.MARGIN_RIGHT, m, DIP);
            cell.setViewLayoutMargin(R.id.cell, RemoteViews.MARGIN_TOP, m, DIP);
            cell.setViewLayoutMargin(R.id.cell, RemoteViews.MARGIN_BOTTOM, m, DIP);
            cell.setImageViewResource(R.id.tile, R.drawable.rect);
            cell.setImageViewResource(R.id.tile_in, R.drawable.rect);
        }

        // tile colours: darker for past, lighter for upcoming, outlined for today
        if (isToday) {
            cell.setInt(R.id.tile, "setColorFilter", pal.accent);
            cell.setViewVisibility(R.id.tile_in, View.VISIBLE);
            cell.setInt(R.id.tile_in, "setColorFilter", pal.today);
            g.curTile = pal.today;
            cell.setInt(R.id.tile_in, "setImageAlpha", Math.round(g.p.op("op_today") * 2.55f));
            int pad = Math.round(g.todayPad * c.getResources().getDisplayMetrics().density);
            cell.setViewPadding(R.id.inner, pad, pad, pad, pad);
        } else {
            int tile = past ? pal.past : pal.future;
            if (g.wkTint && weekend) tile = past ? Pal.mix(pal.past, pal.wkend, 0.45f) : pal.wkend;
            if (out && !past) tile = pal.out;
            cell.setInt(R.id.tile, "setColorFilter", tile);
            g.curTile = tile;
            cell.setInt(R.id.tile, "setImageAlpha", Math.round(g.p.op(past ? "op_past" : "op_future") * 2.55f));
        }

        // date label
        String num = String.valueOf(day.getDayOfMonth());
        if (!g.month && r.n != 7) num = DOW_TXT[day.getDayOfWeek().getValue() - 1] + " " + num;
        else if (!g.month && day.getDayOfMonth() == 1) num = num + " " + day.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT);
        cell.setTextViewText(R.id.num, font(g, num));
        if (g.numDp > 0) {
            cell.setTextViewTextSize(R.id.num, DIP, small ? g.numDp * 0.74f : g.numDp);
            cell.setViewLayoutHeight(R.id.num, g.head(small), DIP);
        }
        int numCol = dw == DayOfWeek.SUNDAY || (g.satRed && dw == DayOfWeek.SATURDAY) ? pal.sunday : pal.num;
        if (isToday) cell.setTextColor(R.id.num, pal.accent);
        else cell.setTextColor(R.id.num, dim ? Pal.alpha(numCol, out ? 0x55 : Math.max(0x80, g.dimA)) : numCol);

        Intent open = new Intent(c, DayActivity.class).setData(Uri.parse("shiftcal://day/" + day));
        cell.setOnClickPendingIntent(R.id.cell, act(c, open, 0));
        if (items == null || items.isEmpty()) return cell;

        float innerW = cellW - 2 - (isToday ? g.todayPad + 0.5f : 0);
        float innerH = rowH - 1 - 2 - g.head(small) - (isToday ? g.todayPad + 0.5f : 0);
        List<Events.Item> marks = new ArrayList<Events.Item>(), timed = new ArrayList<Events.Item>();
        for (Events.Item it : items) (it.allDay ? marks : timed).add(it);

        // ----- all-day markers: one fixed band per day -----
        float bandH = 0;
        if (!marks.isEmpty()) {
            float mdp = g.mdp, mLine = mdp * 1.18f;
            int bandId = g.bandTop ? R.id.band_top : R.id.band_bot;
            float maxBand = Math.max(mLine + 3, innerH * (timed.isEmpty() ? 0.9f : 0.45f));
            // every marker is exactly one line at the same height, so the band never competes with the timed events
            float one = mLine + 3;
            if (small || g.side || marks.size() == 1) {
                bandH = one;
                addBandRow(g, cell, bandId, marks.subList(0, Math.min(marks.size(), g.maxMarks)), mdp, 1, one, dim);
            } else {
                int n = Math.max(1, Math.min(Math.min(marks.size(), g.maxMarks), (int) Math.floor(maxBand / one)));
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
            if (!g.pal.mkAuto) m.setTextColor(R.id.txt, dim ? Pal.alpha(g.pal.mkText, Math.max(0xB3, g.dimA)) : g.pal.mkText);
            else m.setTextColor(R.id.txt, Pal.lum(it.color) > 0.62 ? (dim ? Pal.alpha(0xFF101410, Math.max(0xB3, g.dimA)) : 0xFF101410) : (dim ? Pal.alpha(0xFFFFFFFF, Math.max(0xB3, g.dimA)) : 0xFFFFFFFF));
            String mt = it.title.isEmpty() ? "(no title)" : Events.display(it.title, g.keys, true);
            if (g.upper) mt = mt.toUpperCase(Locale.ROOT);
            m.setTextViewText(R.id.txt, font(g, mt));
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
        float mw = g.minWin * 60f;
        if (hi - lo < mw) hi = Math.min(24 * 60f, lo + mw);
        if (hi - lo < mw) lo = Math.max(0, hi - mw);
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
            int need = g.detailed ? Math.max(1, Math.min(g.maxLines, (int) Math.ceil(text[i].length() / cpl))) : 1;
            top[i] = y0; h[i] = Math.max(y1 - y0, need * g.lineH + 2);
        }
        for (int i = 1; i < n; i++) top[i] = Math.max(top[i], top[i - 1] + h[i - 1] + gap);
        float limit = area;
        for (int i = n - 1; i >= 0; i--) {
            if (top[i] + h[i] > limit) top[i] = limit - h[i];
            limit = top[i] - gap;
        }
        if (top[0] < 0) {                       // too tall: trim the long blocks back to their text height
            for (int i = 0; i < n; i++) { int need = Math.max(1, Math.min(Math.min(2, g.maxLines), (int) Math.ceil(text[i].length() / cpl))); h[i] = Math.min(h[i], need * g.lineH + 2); }
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
            int need = !g.detailed || small ? 1 : Math.min(g.maxLines, (int) Math.ceil(text.length() / cpl));
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
            more.setTextColor(R.id.txt, g.pal.more);
            more.setTextViewTextSize(R.id.txt, DIP, g.tdp);
            cell.addView(R.id.evs, more);
        }
    }

    private static RemoteViews chip(G g, Events.Item it, CharSequence text, boolean dim) {
        RemoteViews chip = new RemoteViews(g.c.getPackageName(), R.layout.chip);
        int fg = inv(g, it) ? 0xFF101410 : g.pal.evText;
        chip.setInt(R.id.bar, "setBackgroundColor", Pal.alpha(it.color, dim ? g.dimA : 0xFF));
        chip.setInt(R.id.chip, "setBackgroundColor", Pal.alpha(it.color, Math.round(g.evPct * 2.55f * (dim ? g.dimA / 255f : 1f))));
        chip.setTextColor(R.id.txt, dim ? Pal.alpha(fg, g.dimA) : fg);
        chip.setTextViewText(R.id.txt, text);
        chip.setTextViewTextSize(R.id.txt, DIP, g.tdp);
        if (g.barW <= 0) chip.setViewVisibility(R.id.bar, View.GONE);
        else if (g.barW != 3) chip.setViewLayoutWidth(R.id.bar, g.barW, DIP);
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
        if (g.upper) t = t.toUpperCase(Locale.ROOT);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int flag = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        if (withTime || it.cont) {
            String time = it.cont ? (withTime ? "→" + hm(it.end, g.zone) : "→") : hm(it.begin, g.zone);
            sb.append(time);
            if (g.timeBold) sb.setSpan(new StyleSpan(Typeface.BOLD), 0, time.length(), flag);
            if (!dim && !it.cancelled) sb.setSpan(new ForegroundColorSpan(g.pal.timeAuto ? (g.pal.dark && !inv ? 0xFFFFFFFF : 0xFF000000) : (inv ? 0xFF000000 : g.pal.time)), 0, time.length(), flag);
            sb.append(' ');
        }
        int s = sb.length();
        sb.append(t);
        if (g.showLoc && withTime && it.loc != null && !it.loc.trim().isEmpty()) {
            int l0 = sb.length(); sb.append(" · ").append(it.loc.trim());
            sb.setSpan(new ForegroundColorSpan(Pal.alpha(muted, dim ? g.dimA : 0xFF)), l0, sb.length(), flag);
            sb.setSpan(new RelativeSizeSpan(0.85f), l0, sb.length(), flag);
        }
        if (!g.font.isEmpty()) sb.setSpan(new TypefaceSpan(g.font), 0, sb.length(), flag);
        if (it.cancelled) {
            sb.setSpan(new StrikethroughSpan(), s, sb.length(), flag);
            sb.setSpan(new ForegroundColorSpan(Pal.alpha(muted, dim ? g.dimA : 0xFF)), 0, sb.length(), flag);
            return sb;
        }
        Matcher code = CODE.matcher(t);
        if (code.find()) {
            if (g.codeBold) sb.setSpan(new StyleSpan(Typeface.BOLD), s, s + code.end(), flag);
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
        while (g.hrsSmall && hrs.find()) {
            sb.setSpan(new RelativeSizeSpan(0.82f), s + hrs.start(), s + hrs.end(), flag);
            sb.setSpan(new ForegroundColorSpan(Pal.alpha(muted, dim ? g.dimA : 0xFF)), s + hrs.start(), s + hrs.end(), flag);
        }
        return sb;
    }

    /** Applies the chosen font family (if any) to a piece of widget text. */
    static CharSequence font(G g, String text) {
        if (g == null || g.font.isEmpty()) return text;
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        sb.setSpan(new TypefaceSpan(g.font), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
