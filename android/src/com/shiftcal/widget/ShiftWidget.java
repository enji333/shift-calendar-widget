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
import android.text.StaticLayout;
import android.text.TextPaint;
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
            // move the current-time line: redraw on each 5-minute mark (v3.27, so today's box settles smoothly) while the phone is awake
            long tick = 300000L, at = (System.currentTimeMillis() / tick + 1) * tick + 2000;
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
        int evPct, dimA, curTile; boolean inv; float lastBand; Map<LocalDate, List<Events.Item>> slots;
        // finer controls from the desktop panel (defaults match the phone settings page)
        // v3.27 today's box: squish the past (option D from the layout preview)
        boolean sq = true, sqMid = true, sqStrip; float sqPast = 0.2f, sqFont = 0.75f, sqRamp = 45, nowY = -1; int sqMinL = 2;
        int maxLines = 3, maxMarks = 3, minWin = 8; float mdp, numDp, headBig = 19, headSmall = 14, todayPad = 2.5f, nowH = 1.5f, barW = 3;
        String font = ""; boolean timeBold = true, hrsSmall = true, upper, showLoc, codeBold = true, nowLine = true;
        // current-time line look (v3.24): plain|pen|marker|dash|dot, soft shadow, dot at the start, colour
        String nowStyle = "plain", nowCol = "theme"; boolean nowShadow, nowDot;
        // WA outback look (v3.1)
        boolean wkTint, satRed, grid, numLeft; float gridW = 1;
        // hand-drawn linework and shapes (v3.2)
        boolean ink, inkBoxes, stip; String hatch = "none"; float inkW = 0.9f, wobble = 4, rTile = 2, rEv = 0, rMk = 0;
        // finer hand-drawn controls and fonts per kind of text (v3.3)
        int rough = 1; float hang = 45, hgap = 4.6f, stipD = 1; String hwhere = "past", stipOn = "wkend"; boolean inkEv;
        String fontMk = "", fontNum = "", fontDow = ""; float evSx = 1, numLs = 0, dowLs = 0; boolean numBold = true, numFake, dowBold = true;
        Typeface numTf, dowTf;
        Look.Fx fx, dowFx, numFx;
        boolean toon;   // cartoon boxes: thick outline and offset shadow on events, day boxes and buttons (v3.15)   // lettering effects for the title and weekday names (v3.11)
        float head(boolean small) { return small ? headSmall : headBig; }
        List<String> keys = new ArrayList<String>();
        boolean clash;
    }

    static RemoteViews build(Context c, int wid, Bundle opts) {
        G g = new G();
        g.c = c; g.p = new Prefs(c); Pal.migrate(g.p, false); g.pal = Pal.of(g.p); g.zone = ZoneId.of(g.p.tz());
        Prefs p = g.p; Pal pal = g.pal;
        H12 = "12".equals(p.s("clock", "24"));
        SMALL = Math.max(0.4f, Math.min(1f, p.i("small_pct", 45) / 100f));
        TAPC = p.s("tapc", "");
        g.maxLines = Math.max(1, Math.min(8, p.i("maxl", 3)));
        g.maxMarks = Math.max(1, Math.min(6, p.i("maxmk", 3)));
        g.minWin = Math.max(1, Math.min(24, p.i("minwin", 8)));
        g.font = p.s("font", "").trim();
        String base = g.font.isEmpty() ? "sans-serif-condensed" : g.font;
        g.fontMk = p.s("f_mk", "").trim(); if (g.fontMk.isEmpty()) g.fontMk = g.font;
        g.evSx = Math.max(70, Math.min(140, p.i("evsx", 100))) / 100f;
        // dates and weekday names: the phone's fonts as text, or any font (and letter spacing) drawn as a picture
        String fn = p.s("f_num", "").trim(); g.numLs = p.i("nls", 0) / 100f; g.numBold = p.on("nbold", true);
        g.fx = Look.fx(p, g.pal); g.dowFx = g.fx != null && p.on("teffw", false) ? g.fx : null;
        g.numFx = g.fx != null && p.on("teffn", false) ? Look.forDates(g.fx) : null;
        g.toon = p.on("toon", false);
        String tfid0 = p.s("tfont", "").trim();   // with an effect, dates and weekday names with no font of their own use the title font
        if (g.numFx != null && fn.isEmpty()) fn = tfid0;

        if (Look.isAsset(fn) || g.numLs != 0 || !g.numBold || g.numFx != null) { g.numTf = Look.any(c, fn.isEmpty() ? base : fn, g.numBold && !Look.isAsset(fn)); g.numFake = g.numBold && Look.isAsset(fn); }
        else g.fontNum = fn.isEmpty() ? g.font : fn;
        String fd = p.s("f_dow", "").trim(); if (g.dowFx != null && fd.isEmpty()) fd = tfid0; g.dowLs = p.i("dowls", 0) / 100f; g.dowBold = p.on("dowbold", true);
        if (Look.isAsset(fd) || g.dowLs != 0 || !g.dowBold || g.dowFx != null) g.dowTf = Look.any(c, fd.isEmpty() ? base : fd, g.dowBold && !Look.isAsset(fd));
        else g.fontDow = fd.isEmpty() ? g.font : fd;
        g.timeBold = p.on("tbold", true); g.hrsSmall = p.on("hrs", true); g.upper = p.on("upper", false);
        g.showLoc = p.on("loc", false); g.codeBold = p.on("cbold", true); g.nowLine = p.on("nowline", true);
        g.numDp = p.tenths("ndp10", 0);
        if (g.numDp > 0) { g.headBig = Math.max(10, g.numDp + 3.5f); g.headSmall = Math.max(8, g.numDp * 0.74f + 2.5f); }
        g.todayPad = Math.max(0, Math.min(8, p.i("tpad10", 25) / 10f));
        g.nowH = Math.max(0.5f, Math.min(6, p.i("nowh10", 15) / 10f));
        g.sq = p.on("sq", true); g.sqMid = p.on("sq_mid", true); g.sqStrip = "strip".equals(p.s("sq_fin", "line"));
        g.sqPast = Math.max(5, Math.min(100, p.i("sq_past", 20))) / 100f; g.sqFont = Math.max(40, Math.min(100, p.i("sq_font", 75))) / 100f;
        g.sqRamp = Math.max(5, Math.min(180, p.i("sq_ramp", 45))); g.sqMinL = Math.max(1, Math.min(3, p.i("sq_minl", 2)));
        g.nowStyle = p.s("now_style", "plain"); g.nowCol = p.s("now_col", "theme"); g.nowShadow = p.on("now_shadow", false); g.nowDot = p.on("now_dot", false);
        if (!p.on("mig_bar0", false)) { p.put("barw10", 0); p.put("mig_bar0", 1); }   // v3.21: Ben asked for no colour bands down the left of events
        if (!p.on("mig_mbe", false)) {   // v3.26: codes keep their case, so Ben's "maybe" code is MBe (it was saved as MBE before)
            String rs = p.rules(); boolean has = false, upper = false;
            for (String line : rs.split("\n")) { int eq = line.lastIndexOf('='); if (eq < 0) continue; String k = line.substring(0, eq).trim(); if (k.equals("MBe")) has = true; if (k.equals("MBE")) upper = true; }
            if (!has) {
                if (upper) { StringBuilder sb = new StringBuilder(); for (String line : rs.split("\n")) { int eq = line.lastIndexOf('='); if (eq >= 0 && line.substring(0, eq).trim().equals("MBE")) line = "MBe" + line.substring(eq); if (!line.trim().isEmpty()) sb.append(line).append('\n'); } rs = sb.toString(); }
                else rs = rs.replaceAll("\\s+$", "") + "\nMBe=#B39DDB";
                p.put("rules", rs);
            }
            p.put("mig_mbe", 1);
        }
        g.barW = Math.max(0, Math.min(10, p.i("barw10", 0) / 10f));
        g.wkTint = p.on("wktint", false); g.satRed = p.on("satred", false); g.grid = p.on("grid", false);
        g.gridW = Math.max(0.5f, Math.min(6, p.i("gridw10", 10) / 10f)); g.numLeft = "left".equals(p.s("numpos", "center"));
        float hh = Math.max(24, Math.min(90, p.i("hh", 30)));
        g.ink = p.on("ink", false); g.inkBoxes = "boxes".equals(p.s("inkbox", "grid")); g.stip = p.on("stip", false); g.hatch = p.s("hatch", "none");
        g.inkW = Math.max(0.3f, Math.min(4, p.i("inkw10", 9) / 10f)); g.wobble = Math.max(0, Math.min(10, p.i("wobble", 4)));
        g.rough = Math.max(1, Math.min(3, p.i("rough", 1))); g.hang = p.i("hang", 45); g.hgap = Math.max(2, Math.min(14, p.i("hgap10", 46) / 10f));
        g.hwhere = p.s("hwhere", "past"); g.stipOn = p.s("stipon", "wkend"); g.stipD = Math.max(0.25f, Math.min(4, p.i("stipd", 100) / 100f));
        g.inkEv = g.ink && p.on("inkev", false);
        g.rTile = Math.max(0, p.i("r_tile", 2)); g.rEv = Math.max(0, p.i("r_ev", 0)); g.rMk = Math.max(0, p.i("r_mk", 0));
        if (g.ink) g.grid = false;
        final boolean comic = ComicPage.on(p);   // v3.16: the month as a hand-inked comic page
        if (comic) { g.ink = false; g.grid = false; }
        float rBg = Math.max(0, p.i("r_bg", 8));

        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
        rv.setInt(R.id.bg, "setColorFilter", pal.bg);
        rv.setInt(R.id.bg, "setImageAlpha", Math.round(p.opacity() * 2.55f));
        if (rBg != 8) rv.setImageViewResource(R.id.bg, Look.rr(rBg));
        for (int id : new int[] { R.id.prev, R.id.next, R.id.title, R.id.add, R.id.today, R.id.settings }) rv.setTextColor(id, pal.head);
        if (!p.on("h_prev", true)) rv.setViewVisibility(R.id.prev, View.GONE);
        if (!p.on("h_next", true)) rv.setViewVisibility(R.id.next, View.GONE);
        if (!p.on("h_add", true)) rv.setViewVisibility(R.id.add, View.GONE);
        if (!p.on("h_today", true)) rv.setViewVisibility(R.id.today, View.GONE);
        if (!p.on("h_set", true)) rv.setViewVisibility(R.id.settings, View.GONE);
        if (!p.on("h_head", true)) rv.setViewVisibility(R.id.header, View.GONE);
        if (!p.on("h_dow", true) || comic) rv.setViewVisibility(R.id.dow_box, View.GONE);
        if (p.tenths("hdp10", 0) > 0) rv.setTextViewTextSize(R.id.title, DIP, p.tenths("hdp10", 15));
        if (hh != 30) rv.setViewLayoutHeight(R.id.header, hh, DIP);
        // landscape header in the outback palette
        int sceneRes = p.on("scene_off", false) ? 0 : Look.sceneRes(p.s("scene", ""), p.on("scene_ink", false)); float sceneH = 0;   // "no picture" sticks even when a look is picked
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
            rv.setViewVisibility(R.id.dow_box, View.GONE);
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
                if (!first.equals(today.withDayOfMonth(1))) r.w = 1f;   // another month: every week the same height (v3.5)
                else if (r.start.plusDays(6).isBefore(today)) r.w = SMALL;     // whole week already gone
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
        if (p.filtered() && p.on("filtstar", true)) title += " *";   // a filter is hiding some events (v3.6)
        SpannableStringBuilder tsb = new SpannableStringBuilder(title);
        if (!tail.isEmpty()) { int t0 = tsb.length(); tsb.append(tail); tsb.setSpan(new ForegroundColorSpan(pal.sun), t0, tsb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); }
        if (p.on("h_arrow", true)) tsb.append(" ▾");
        if (!g.font.isEmpty()) tsb.setSpan(new TypefaceSpan(g.font), 0, tsb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        rv.setTextViewText(R.id.title, tsb);
        TITLE_TXT = tsb.toString().replace(" ▾", "");
        rv.setOnClickPendingIntent(R.id.title, bcast(c, ACT_TOGGLE, wid, wid * 10 + 7));
        final String titleMain = title, titleTail = tail;
        LocalDate colStart = rows.get(rows.size() - 1).start;
        String[] dowTxt = new String[7]; int[] dowCol = new int[7];
        for (int i = 0; i < 7; i++) {
            DayOfWeek dw = colStart.plusDays(i).getDayOfWeek();
            String dl = DOW_TXT[dw.getValue() - 1], ds = p.s("dows", "upper3");
            if ("title3".equals(ds)) dl = dl.charAt(0) + dl.substring(1).toLowerCase(Locale.ROOT);
            else if ("one".equals(ds)) dl = dl.substring(0, 1);
            else if ("two".equals(ds)) dl = dl.charAt(0) + dl.substring(1, 2).toLowerCase(Locale.ROOT);
            dowTxt[i] = dl; dowCol[i] = dw == DayOfWeek.SUNDAY || (g.satRed && dw == DayOfWeek.SATURDAY) ? pal.sunday : pal.dow;
            rv.setTextViewText(DOW_IDS[i], g.dowTf != null ? "" : font(g.fontDow, dl));
            rv.setTextColor(DOW_IDS[i], dowCol[i]);
            if (p.tenths("dowdp10", 0) > 0) rv.setTextViewTextSize(DOW_IDS[i], DIP, p.tenths("dowdp10", 10.5f));
        }

        LocalDate from = rows.get(0).start, to = colStart.plusDays(7);
        Map<LocalDate, List<Events.Item>> map;
        FILLS = new java.util.HashMap<LocalDate, Integer>(); HFILLS = new java.util.HashMap<LocalDate, Integer>();
        try { map = Events.load(c, p, from, to, false, FILLS, HFILLS); }
        catch (Exception e) { map = Collections.emptyMap(); }
        if (p.on("evmono", false))   // black-and-white events: each colour becomes a grey of the same brightness (v3.15)
            for (List<Events.Item> l : map.values()) for (Events.Item it : l) { if (it.mono) continue; it.mono = true; int v = Math.round(90 + (float) Pal.lum(it.color) * 165); it.color = 0xFF000000 | (v << 16) | (v << 8) | v; }

        // ----- space available, in dp -----
        float wDp = 0, hDp = 0; HEAD_W = 0;
        if (opts != null) {
            ArrayList<SizeF> sizes = opts.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
            if (sizes != null) for (SizeF s : sizes) if (s.getHeight() > hDp) { hDp = s.getHeight(); wDp = s.getWidth(); }
            if (sizes != null) for (SizeF s : sizes) if (s.getWidth() > 0) HEAD_W = HEAD_W <= 0 ? s.getWidth() : Math.min(HEAD_W, s.getWidth());   // narrowest = portrait
            if (hDp <= 0) { wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0); hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0); }
        }
        if (wDp <= 0) wDp = 340; if (hDp <= 0) hDp = 480;
        // retro title font: drawn as a picture, since widget text can only use the phone's fonts
        if (g.dowTf != null && p.on("h_dow", true) && !comic) {
            try {
                rv.setImageViewBitmap(R.id.dow_img, Look.strip(c, g.dowTf, dowTxt, dowCol, p.tenths("dowdp10", 10.5f), wDp - 4, 14, g.dowLs, g.dowFx));
                rv.setViewVisibility(R.id.dow_img, View.VISIBLE);
            } catch (Throwable ignored) { }
        }
        float earnH = 0; EARN_W = 0;
        try { earnH = earnings(c, rv, p, pal, g.font, wDp); } catch (Throwable t) { Prefs.logCrash(c, t); }
        float titleMax = 120;
        try { titleMax = headerButtons(g, rv, wDp, hh); } catch (Throwable t) { Prefs.logCrash(c, t); }
        String tfid = p.s("tfont", ""); float tls = p.i("tls", 0) / 100f;
        Typeface tf = null;
        if (p.on("h_head", true)) {
            if (!tfid.isEmpty()) tf = Look.any(c, tfid, !Look.isAsset(tfid));
            else if (tls != 0 || g.fx != null) tf = Look.any(c, g.font.isEmpty() ? "sans-serif-condensed" : g.font, true);
        }
        if (tf != null) {
            try {
                float tsz = p.tenths("hdp10", 15);
                {   // same size every month: scale for the longest month name, then draw the real one at that size
                    android.graphics.Paint m = new android.graphics.Paint(); m.setTypeface(tf); m.setTextSize(tsz); m.setLetterSpacing(tls);
                    float ww = m.measureText(widest(titleMain) + titleTail) + (p.on("h_arrow", true) ? tsz * 0.6f : 0) + 2;
                    if (ww > titleMax) tsz = tsz * titleMax / ww;
                }
                rv.setImageViewBitmap(R.id.title_img, Look.title(c, tf, titleMain, titleTail, p.on("h_arrow", true), pal.head, pal.sun, tsz, hh - 4, titleMax, tls, g.fx));
                rv.setViewVisibility(R.id.title_img, View.VISIBLE);
                rv.setViewVisibility(R.id.title, View.GONE);
                rv.setOnClickPendingIntent(R.id.title_img, bcast(c, ACT_TOGGLE, wid, wid * 10 + 7));
            } catch (Throwable ignored) { }
        }
        float units = 0;
        for (Row r : rows) { r.w = Math.round(r.w * 20) / 20f; r.small = r.w < 0.58f; units += r.w; }
        float gridPad = g.grid ? g.gridW : 0;
        float unitH = (hDp - 5 - (p.on("h_head", true) ? hh : 0) - (p.on("h_dow", true) && !comic ? 14 : 0) - sceneH - earnH - gridPad) / units;
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
        g.keys = Events.keys(p);
        g.evPct = Math.max(0, Math.min(100, p.i("op_ev", 28)));
        g.dimA = Math.round(Math.max(20, Math.min(100, p.i("op_pastev", 60))) * 2.55f);
        g.codeMode = p.code(); g.codeCol = "custom".equals(g.codeMode) ? Pal.hex(p.s("c_code", ""), pal.accent) : pal.accent;
        g.side = p.sideBySide(); g.bandTop = "top".equals(p.allday());
        g.clash = p.on("clash", true);

        rv.removeAllViews(R.id.weeks);
        if (comic) {
            try { comicPage(g, rv, rows, map, wDp - 4, unitH * units, wDp, hDp, rBg); return rv; }
            catch (Throwable t) { Prefs.logCrash(c, t); rv.removeAllViews(R.id.weeks); }
        }
        for (Row r : rows) {
            RemoteViews row = new RemoteViews(c.getPackageName(), ROW_LAYOUTS[Math.max(0, Math.min(12, Math.round(r.w * 20) - 8))]);
            float rowH = unitH * r.w;
            window(g, r, map);
            try { g.slots = slots(g, r, map); } catch (Throwable t) { g.slots = null; }
            float cellW = g.grid ? (wDp - 4 - g.gridW) / r.n - g.gridW : (wDp - 4) / r.n - 1;
            for (int d = 0; d < r.n; d++) {
                LocalDate day = r.start.plusDays(d);
                row.addView(R.id.cells, cell(g, day, map.get(day), r, cellW, rowH));
            }
            if (r.n == 7 && r.start.plusDays(6).isBefore(today)) {
                try { watermark(g, row, r, rowH, wDp); } catch (Throwable t) { Prefs.logCrash(c, t); }
            }
            rv.addView(R.id.weeks, row);
        }
        try {   // print texture over the whole widget: colour wash, paper, halftone dots and grain (v3.11)
            android.graphics.Bitmap tex = texLayer(c, p, pal, wDp, hDp, rBg);
            if (tex != null) { rv.setImageViewBitmap(R.id.tex_ov, tex); rv.setViewVisibility(R.id.tex_ov, View.VISIBLE); }
        } catch (Throwable t) { Prefs.logCrash(c, t); }
        if (g.ink) {
            try {
                rv.setImageViewBitmap(R.id.ink_ov, inkLayer(g, rows, wDp - 4, unitH * units));
                rv.setInt(R.id.ink_ov, "setColorFilter", pal.ink);
                rv.setViewVisibility(R.id.ink_ov, View.VISIBLE);
            } catch (Throwable ignored) { }
        }
        return rv;
    }

    /** v3.16 comic page: one drawing over the weeks area, with invisible tap areas laid out the same, so each panel opens its day. */
    private static void comicPage(G g, RemoteViews rv, List<Row> rows, Map<LocalDate, List<Events.Item>> map, float aw, float ah, float wDp, float hDp, float rBg) {
        Context c = g.c;
        LocalDate[] st = new LocalDate[rows.size()]; int[] n = new int[rows.size()]; float[] w = new float[rows.size()];
        for (int i = 0; i < st.length; i++) {
            Row r = rows.get(i); st[i] = r.start; n[i] = r.n; int most = 0;
            for (int d = 0; d < r.n; d++) most = Math.max(most, ComicPage.busy(map, r.start.plusDays(d)));
            w[i] = 1f + 0.18f * Math.min(4, most);
        }
        List<ComicPage.Tier> tiers = ComicPage.layout(g.p, st, n, w, map, g.today, aw, ah);
        rv.setImageViewBitmap(R.id.ink_ov, ComicPage.draw(c, g.p, g.pal, tiers, map, g.today, g.zone, aw, ah, st[0].toEpochDay()));
        rv.setInt(R.id.ink_ov, "setColorFilter", 0);
        rv.setViewVisibility(R.id.ink_ov, View.VISIBLE);
        rv.setImageViewBitmap(R.id.bg, ComicPage.fade(g.p, g.pal, wDp, hDp, rBg));
        rv.setInt(R.id.bg, "setColorFilter", 0);
        int code = 0;
        for (int ti = 0; ti < tiers.size(); ti++) {
            ComicPage.Tier t = tiers.get(ti);
            float th = ti + 1 < tiers.size() ? tiers.get(ti + 1).y - t.y : Math.max(1, ah - t.y);
            RemoteViews row = new RemoteViews(c.getPackageName(), R.layout.comic_row);
            row.setViewLayoutHeight(R.id.comic_row, th, DIP);
            if (t.narration) {
                RemoteViews tap = new RemoteViews(c.getPackageName(), R.layout.comic_tap);
                tap.setViewLayoutWidth(R.id.tap, aw, DIP);
                row.addView(R.id.comic_row, tap);
            } else for (int k = 0; k < t.panels.size(); k++) {
                ComicPage.Panel pn = t.panels.get(k);
                float pw = k + 1 < t.panels.size() ? t.panels.get(k + 1).x - pn.x : aw - pn.x;
                if (k == 0) pw += pn.x;
                RemoteViews tap = new RemoteViews(c.getPackageName(), R.layout.comic_tap);
                tap.setViewLayoutWidth(R.id.tap, pw, DIP);
                Intent open = new Intent(c, DayActivity.class).setData(Uri.parse("shiftcal://day/" + pn.days.get(0)));
                tap.setOnClickPendingIntent(R.id.tap, act(c, open, 0));
                row.addView(R.id.comic_row, tap);
                code++;
            }
            rv.addView(R.id.weeks, row);
        }
    }

    /** Days coloured by a holiday or a trip in the last redraw (from Events.load). */
    static Map<LocalDate, Integer> FILLS = new java.util.HashMap<LocalDate, Integer>(), HFILLS = new java.util.HashMap<LocalDate, Integer>();
    /** The colour that fills this whole day: the HOL (trip or own holiday) first, else the public holiday; or 0. */
    static int holiday(Prefs p, LocalDate day, List<Events.Item> items) {
        if (!p.on("holfill", true)) return 0;
        Integer f = HFILLS.get(day); if (f != null) return f;
        f = FILLS.get(day); if (f != null) return f;
        if (items != null) for (Events.Item it : items) if (it.holiday && !it.cancelled) return it.color | 0xFF000000;
        return 0;
    }
    /** The public holiday's colour on a day that also has a HOL, so the fill runs from one to the other (v3.23); or 0. */
    static int holiday2(Prefs p, LocalDate day) {
        if (!p.on("holfill", true) || !HFILLS.containsKey(day)) return 0;
        Integer f = FILLS.get(day); return f == null ? 0 : f;
    }
    /** A day box filled with a fade: strong holiday colour at the top melting into the normal box colour below (v3.18). */
    static android.graphics.Bitmap fadeTile(int base, int baseA, int hol, int a, float wDp, float hDp, float rDp) { return fadeTile(base, baseA, hol, 0, a, false, wDp, hDp, rDp); }
    /** With a second colour, the box runs from the first (top) to the second (bottom); solid keeps both strong. */
    static android.graphics.Bitmap fadeTile(int base, int baseA, int hol, int hol2, int a, boolean solid, float wDp, float hDp, float rDp) {
        int W = Math.max(2, Math.round(wDp)), H = Math.max(2, Math.round(hDp));
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(W, H, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(b);
        float r = rDp >= 25 ? Math.min(W, H) / 2f : Math.min(rDp, Math.min(W, H) / 2f);
        android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        pt.setColor(Pal.alpha(base, baseA)); cv.drawRoundRect(0, 0, W, H, r, r, pt);
        int[] cols = hol2 == 0 ? new int[] { Pal.alpha(hol, a), Pal.alpha(hol, Math.round(a * 0.75f)), Pal.alpha(hol, Math.round(a * 0.3f)) }
            : new int[] { Pal.alpha(hol, a), Pal.alpha(Pal.mix(hol, hol2, 0.5f), Math.round(a * (solid ? 1f : 0.85f))), Pal.alpha(hol2, Math.round(a * (solid ? 1f : 0.7f))) };
        pt.setShader(new android.graphics.LinearGradient(0, 0, 0, H, cols, new float[] { 0, 0.55f, 1 }, android.graphics.Shader.TileMode.CLAMP));
        cv.drawRoundRect(0, 0, W, H, r, r, pt);
        return b;
    }

    /** A day box, plus a thin line across today's box at the current time. */
    private static RemoteViews cell(G g, LocalDate day, List<Events.Item> items, Row r, float cellW, float rowH) {
        g.lastBand = 0; g.nowY = -1;
        RemoteViews cell = cell0(g, day, items, r, cellW, rowH);
        if (day.equals(g.today) && g.nowLine) {
            try {
                ZonedDateTime now = ZonedDateTime.now(g.zone);
                float min = now.getHour() * 60 + now.getMinute(), head = g.head(r.small);
                float area = rowH - 1 - 2 - head - 3 - g.lastBand;
                float frac = Math.max(0f, Math.min(1f, (min - r.win0) / Math.max(1f, r.win1 - r.win0)));
                float y = 2.5f + head + (g.bandTop ? g.lastBand : 0) + frac * Math.max(0, area - 1.5f);
                if (g.nowY >= 0) y = g.todayPad + head + (g.bandTop ? g.lastBand : 0) + Math.min(g.nowY, Math.max(0, area - 1.5f));   // on the squish scale (v3.27)
                cell.setViewVisibility(R.id.now, View.VISIBLE);
                int col = nowColour(g);
                if ("plain".equals(g.nowStyle) && !g.nowShadow && !g.nowDot) {
                    cell.setInt(R.id.now, "setColorFilter", col);
                    if (g.nowH != 1.5f) cell.setViewLayoutHeight(R.id.now, g.nowH, DIP);
                } else {
                    // drawn line: room above and below for the pen wobble, the shadow and the start dot
                    float pad = Math.max(2.5f, g.nowH * (g.nowDot ? 1.6f : 1f) + (g.nowShadow ? 2.5f : 1f));
                    float bw = Math.max(8, cellW - 5), bh = g.nowH + pad * 2;
                    cell.setImageViewBitmap(R.id.now, nowBitmap(g, col, bw, bh, pad, day.toEpochDay()));
                    cell.setViewLayoutHeight(R.id.now, bh, DIP);
                    y -= pad;
                }
                cell.setViewLayoutMargin(R.id.now, RemoteViews.MARGIN_TOP, y, DIP);
                cell.setViewLayoutMargin(R.id.now, RemoteViews.MARGIN_LEFT, 2.5f, DIP);
                cell.setViewLayoutMargin(R.id.now, RemoteViews.MARGIN_RIGHT, 2.5f, DIP);
            } catch (Exception ignored) { }
        }
        return cell;
    }
    static int nowColour(G g) {
        switch (g.nowCol) {
            case "red": return 0xFFE53935;
            case "black": return 0xFF1A1A1A;
            case "white": return 0xFFFFFFFF;
            case "ink": return g.pal.num;
            default: return g.pal.now;
        }
    }
    /** The current-time line drawn as a picture: pen, marker, dashes or dots, with an optional shadow and start dot (v3.24). */
    static android.graphics.Bitmap nowBitmap(G g, int col, float wDp, float hDp, float padDp, long seed) {
        float d = g.c.getResources().getDisplayMetrics().density;
        int W = Math.max(4, Math.round(wDp * d)), H = Math.max(2, Math.round(hDp * d));
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(W, H, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(b);
        float lw = g.nowH * d, cy = padDp * d + lw / 2f, x0 = lw / 2f + (g.nowDot ? lw * 1.2f : 0), x1 = W - lw / 2f;
        if (g.nowShadow) {
            android.graphics.Bitmap sh = android.graphics.Bitmap.createBitmap(W, H, android.graphics.Bitmap.Config.ARGB_8888);
            drawNow(g, new android.graphics.Canvas(sh), 0xFF000000, lw, x0, x1, cy, d, seed);
            android.graphics.Paint sp = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            sp.setMaskFilter(new android.graphics.BlurMaskFilter(Math.max(1f, 1.4f * d), android.graphics.BlurMaskFilter.Blur.NORMAL));
            sp.setAlpha(120);
            cv.drawBitmap(sh.extractAlpha(), 0.6f * d, 1.1f * d, sp);
            sh.recycle();
        }
        drawNow(g, cv, col, lw, x0, x1, cy, d, seed);
        return b;
    }
    private static void drawNow(G g, android.graphics.Canvas cv, int col, float lw, float x0, float x1, float cy, float d, long seed) {
        android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        pt.setColor(col); pt.setStrokeCap(android.graphics.Paint.Cap.ROUND); pt.setStrokeWidth(lw);
        java.util.Random rnd = new java.util.Random(seed * 31L + 7);
        switch (g.nowStyle) {
            case "pen":      // fineliner: one wobbly stroke with uneven pressure
                PenBox.pen(cv, pt, rnd, x0, cy, x1, cy, lw, d * 0.6f);
                break;
            case "marker":   // felt marker: a few rough passes, slightly see-through
                pt.setAlpha(150);
                for (int i = 0; i < 3; i++) PenBox.pen(cv, pt, rnd, x0, cy + (i - 1) * lw * 0.25f, x1, cy + (i - 1) * lw * 0.25f, lw * 1.1f, d * 0.5f);
                break;
            case "dash":
                pt.setPathEffect(new android.graphics.DashPathEffect(new float[] { Math.max(3 * d, lw * 3), Math.max(2.5f * d, lw * 2.2f) }, 0));
                cv.drawLine(x0, cy, x1, cy, pt);
                break;
            case "dot":
                pt.setPathEffect(new android.graphics.DashPathEffect(new float[] { 0.01f, Math.max(2.5f * d, lw * 2.2f) }, 0));
                pt.setStrokeWidth(lw * 1.3f);
                cv.drawLine(x0, cy, x1, cy, pt);
                break;
            default:
                cv.drawLine(x0, cy, x1, cy, pt);
        }
        if (g.nowDot) {
            android.graphics.Paint dp = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG); dp.setColor(col);
            cv.drawCircle(lw * 1.6f, cy, lw * 1.6f, dp);
        }
    }
    private static RemoteViews cell0(G g, LocalDate day, List<Events.Item> items, Row r, float cellW, float rowH) {
        Context c = g.c; Pal pal = g.pal;
        boolean small = r.small;
        boolean out = g.month && day.getMonthValue() != g.monthFirst.getMonthValue();
        boolean past = day.isBefore(g.today), isToday = day.equals(g.today);
        // next month's days keep full-strength text; only their box colour differs. A month already over is shown
        // in the palette's greyed "past" box colour with readable text, not faded (v3.5)
        boolean pastMonth = g.month && g.monthFirst.isBefore(g.today.withDayOfMonth(1));
        boolean dim = past && !pastMonth;
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
        } else if (g.ink) {
            // hand-drawn lines go on top: boxes touch (shared lines) or sit apart (each box outlined)
            float m = g.inkBoxes ? 1.5f : 0f;
            cell.setViewLayoutMargin(R.id.cell, RemoteViews.MARGIN_LEFT, m, DIP);
            cell.setViewLayoutMargin(R.id.cell, RemoteViews.MARGIN_RIGHT, m, DIP);
            cell.setViewLayoutMargin(R.id.cell, RemoteViews.MARGIN_TOP, m, DIP);
            cell.setViewLayoutMargin(R.id.cell, RemoteViews.MARGIN_BOTTOM, m, DIP);
        }
        if (g.rTile != 2 || g.grid || g.ink) {
            cell.setImageViewResource(R.id.tile, Look.rr(g.ink && !g.inkBoxes ? 0 : g.rTile));
            cell.setImageViewResource(R.id.tile_in, Look.rr(Math.max(0, (g.ink && !g.inkBoxes ? 0 : g.rTile) - 1.5f)));
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
        // a holiday colours the whole day in its event colour (v3.17); today keeps its outline
        int hol = holiday(g.p, day, items), hol2 = holiday2(g.p, day);
        if (hol != 0) {
            int a = Math.round(Math.max(20, Math.min(100, g.p.i("hol_op", 100))) * 2.55f);
            int id = isToday ? R.id.tile_in : R.id.tile;
            boolean fadeOn = "fade".equals(g.p.s("hol_style", "solid"));
            if (fadeOn || hol2 != 0) {
                try {
                    int baseA = isToday ? Math.round(g.p.op("op_today") * 2.55f) : Math.round(g.p.op(past ? "op_past" : "op_future") * 2.55f);
                    float inset = isToday ? 3 : 0, rr = g.ink && !g.inkBoxes ? 0 : g.rTile;
                    cell.setImageViewBitmap(id, fadeTile(g.curTile, baseA, hol, hol2, a, !fadeOn, cellW - 1 - inset, rowH - 1 - inset, Math.max(0, rr - (isToday ? 1.5f : 0))));
                    cell.setInt(id, "setColorFilter", 0);
                    cell.setInt(id, "setImageAlpha", 255);
                    g.curTile = Pal.mix(g.curTile, hol, a / 255f * 0.85f);   // text sits near the top, where the colour is strongest
                } catch (Throwable t) { cell.setInt(id, "setColorFilter", hol); cell.setInt(id, "setImageAlpha", a); g.curTile = Pal.mix(g.curTile, hol, a / 255f); }
            } else {
                cell.setInt(id, "setColorFilter", hol);
                cell.setInt(id, "setImageAlpha", a);
                g.curTile = Pal.mix(g.curTile, hol, a / 255f);
            }
        }

        // date label
        String num = String.valueOf(day.getDayOfMonth());
        if (!g.month && r.n != 7) num = DOW_TXT[day.getDayOfWeek().getValue() - 1] + " " + num;
        else if (!g.month && day.getDayOfMonth() == 1) num = num + " " + day.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT);
        boolean clashes = g.clash && !past && clash(items);
        if (clashes) {
            // two timed events overlap on this day: a red dot after the date (v3.6)
            SpannableStringBuilder nb = new SpannableStringBuilder(font(g.fontNum, num));
            int a0 = nb.length(); nb.append(" ●");
            nb.setSpan(new ForegroundColorSpan(0xFFE53935), a0, nb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            nb.setSpan(new RelativeSizeSpan(0.7f), a0, nb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            cell.setTextViewText(R.id.num, nb);
        } else cell.setTextViewText(R.id.num, font(g.fontNum, num));
        if (g.numDp > 0) {
            cell.setTextViewTextSize(R.id.num, DIP, small ? g.numDp * 0.74f : g.numDp);
            cell.setViewLayoutHeight(R.id.num, g.head(small), DIP);
        }
        int numCol = dw == DayOfWeek.SUNDAY || (g.satRed && dw == DayOfWeek.SATURDAY) ? pal.sunday : pal.num;
        if (hol != 0 && Math.abs(Pal.lum(numCol) - Pal.lum(g.curTile)) < 0.35) numCol = Pal.lum(g.curTile) > 0.55 ? 0xFF1A1A1A : 0xFFFFFFFF;   // keep the date readable on the holiday colour
        if (isToday) cell.setTextColor(R.id.num, pal.accent);
        else cell.setTextColor(R.id.num, dim ? Pal.alpha(numCol, Math.max(0x80, g.dimA)) : numCol);
        if (g.numTf != null) {
            // the date drawn as a picture, so it can use any font and letter spacing
            try {
                int col = isToday ? pal.accent : dim ? Pal.alpha(numCol, Math.max(0x80, g.dimA)) : numCol;
                float nsz = g.numDp > 0 ? (small ? g.numDp * 0.74f : g.numDp) : (small ? 11.5f : 15.5f);
                cell.setImageViewBitmap(R.id.num_img, Look.label(c, g.numTf, clashes ? num + " •" : num, col, nsz, g.head(small), Math.max(8, cellW - 6), g.numLs, g.numFake, g.numFx));
                cell.setViewLayoutHeight(R.id.num_img, g.head(small), DIP);
                cell.setViewVisibility(R.id.num, View.GONE);
                cell.setViewVisibility(R.id.num_img, View.VISIBLE);
            } catch (Throwable ignored) { }
        }

        Intent open = new Intent(c, DayActivity.class).setData(Uri.parse("shiftcal://day/" + day));
        cell.setOnClickPendingIntent(R.id.cell, act(c, open, 0));
        if (items == null || items.isEmpty()) return cell;

        float innerW = cellW - 2 - (isToday ? g.todayPad + 0.5f : 0);
        float innerH = rowH - 1 - 2 - g.head(small) - (isToday ? g.todayPad + 0.5f : 0);
        List<Events.Item> marks = new ArrayList<Events.Item>(), timed = new ArrayList<Events.Item>();
        for (Events.Item it : items) (it.allDay ? marks : timed).add(it);
        // a public holiday's name goes top right beside the date, not in the all-day band (v3.22)
        Events.Item corner = hol != 0 ? cornerItem(g, items) : null;   // the HOL first, else the public holiday (v3.23)
        if (corner != null && holTop(g, cell, corner, num, cellW, small, dim)) marks.remove(corner);
        // all-day events over several days keep the same line on every day they cover (v3.22)
        List<Events.Item> slotted = g.slots == null ? null : g.slots.get(day);
        if (slotted != null) { List<Events.Item> keep = new ArrayList<Events.Item>(); for (Events.Item it : slotted) keep.add(it == null || marks.contains(it) ? it : null);
            while (!keep.isEmpty() && keep.get(keep.size() - 1) == null) keep.remove(keep.size() - 1);
            for (Events.Item it : marks) if (!keep.contains(it)) keep.add(it);   // anything not lined up goes underneath
            slotted = keep; }

        // ----- all-day markers: one fixed band per day -----
        float bandH = 0;
        if (!marks.isEmpty()) {
            float mdp = g.mdp, mLine = mdp * 1.18f;
            int bandId = g.bandTop ? R.id.band_top : R.id.band_bot;
            float maxBand = Math.max(mLine + 3, innerH * (timed.isEmpty() ? 0.9f : 0.45f));
            // every marker is exactly one line at the same height, so the band never competes with the timed events
            float one = mLine + 3;
            if (!small && !g.side && slotted != null && slotted.size() > 0 && (slotted.size() > 1 || marks.size() == 1)) {
                int n = Math.max(1, Math.min(Math.min(slotted.size(), g.maxMarks), (int) Math.floor(maxBand / one)));
                for (int i = 0; i < n; i++) addBandRow(g, cell, bandId, slotted.get(i) == null ? new ArrayList<Events.Item>() : slotted.subList(i, i + 1), mdp, 1, one, dim);
                bandH = n * one;
            } else if (small || g.side || marks.size() == 1) {
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
        if (!small && g.timeline && isToday && g.sq && area >= 40) {
            try { if (squish(g, cell, timed, day, area, innerW, cpl)) return cell; } catch (Throwable t) { Prefs.logCrash(c, t); g.nowY = -1; }
        }
        if (!small && g.timeline && area >= 64 && timeline(g, cell, timed, day, area, cpl, dim, r)) return cell;
        list(g, cell, timed, area, cpl, dim, small);
        return cell;
    }

    /** A single-day public holiday that shows top right when holidays colour their day. */
    static boolean topHol(G g, Events.Item it) { return (it.pub || it.hl) && !it.cancelled && g.p.on("holfill", true); }
    /** What goes in the top-right corner: Ben's HOL (trip or own holiday) before a public holiday. */
    static Events.Item cornerItem(G g, List<Events.Item> items) {
        if (items == null || !g.p.on("holfill", true)) return null;
        for (Events.Item it : items) if (it.hl && !it.cancelled) return it;
        for (Events.Item it : items) if (it.pub && !it.cancelled && it.allDay) return it;
        return null;
    }
    /** "King's Birthday" -> "King", "Uki trip. NSW" -> "Uki": the first word, at most four letters, no dots. */
    static String short4(String t) {
        String w = t.trim().split("[\\s.,;:–—-]+")[0].replaceAll("['’]s$", "");
        return w.length() > 4 ? w.substring(0, 4) : w;
    }
    /** The holiday's name in a small block top right, as wide as the date leaves room for. False when there's no room. */
    private static boolean holTop(G g, RemoteViews cell, Events.Item it, String num, float cellW, boolean small, boolean dim) {
        float nd = g.numDp > 0 ? (small ? g.numDp * 0.74f : g.numDp) : (small ? 11.5f : 15.5f);
        float numW = nd * 0.56f * num.length() + (g.numLeft ? 7 : 2);   // as much room as the date leaves (v3.23)
        float w = g.numLeft ? cellW - 4 - numW : (cellW - numW) / 2f - 3;
        if (w < 14) return false;
        float h = Math.max(11, g.head(small) - 3);
        cell.setViewVisibility(R.id.hol, View.VISIBLE);
        cell.setViewLayoutWidth(R.id.hol, w, DIP); cell.setViewLayoutHeight(R.id.hol, h, DIP);
        cell.setInt(R.id.holbg, "setColorFilter", it.color | 0xFF000000);
        if (dim) cell.setInt(R.id.holbg, "setImageAlpha", g.dimA);
        if (g.rMk > 0) cell.setImageViewResource(R.id.holbg, Look.rr(g.rMk));
        int tc = !g.pal.mkAuto ? g.pal.mkText : Pal.lum(it.color) > 0.62 ? 0xFF101410 : 0xFFFFFFFF;
        cell.setTextColor(R.id.holtxt, dim ? Pal.alpha(tc, Math.max(0xB3, g.dimA)) : tc);
        String mt = it.title.isEmpty() ? "HOL" : short4(Events.display(it.title, g.keys, true));
        if (g.upper) mt = mt.toUpperCase(Locale.ROOT);
        cell.setTextViewText(R.id.holtxt, font(g.fontMk, mt));
        cell.setTextViewTextSize(R.id.holtxt, DIP, Math.max(8, Math.min(Math.min(h - 3, g.mdp + 3), (w - 4) / Math.max(2.2f, mt.length() * 0.55f))));
        if (g.inkEv || g.toon) { cell.setViewVisibility(R.id.holln, View.VISIBLE); cell.setInt(R.id.holln, "setColorFilter", g.pal.ink); }
        if (g.toon) cell.setImageViewResource(R.id.holln, R.drawable.toon_box);
        return true;
    }
    /** Which line of the all-day band each all-day event takes on each day of a row: events over several days first,
     *  each on the lowest line free on all its days, so they line up; one-day events fill the gaps (v3.22). */
    static Map<LocalDate, List<Events.Item>> slots(G g, Row r, Map<LocalDate, List<Events.Item>> map) {
        java.util.LinkedHashMap<String, int[]> span = new java.util.LinkedHashMap<String, int[]>();
        Map<String, Events.Item[]> per = new java.util.HashMap<String, Events.Item[]>();
        for (int k = 0; k < r.n; k++) {
            List<Events.Item> l = map.get(r.start.plusDays(k)); if (l == null) continue;
            for (Events.Item it : l) {
                if (!it.allDay || it == cornerItem(g, l) && holiday(g.p, r.start.plusDays(k), l) != 0) continue;
                String key = it.title + "|" + it.begin + "|" + it.end;
                int[] sp = span.get(key);
                if (sp == null) { span.put(key, new int[] { k, k }); per.put(key, new Events.Item[r.n]); } else sp[1] = k;
                per.get(key)[k] = it;
            }
        }
        List<String> keys = new ArrayList<String>(span.keySet());
        final Map<String, int[]> sp0 = span;
        java.util.Collections.sort(keys, new java.util.Comparator<String>() { public int compare(String a, String b) {
            int[] x = sp0.get(a), y = sp0.get(b); boolean mx = x[1] > x[0], my = y[1] > y[0];
            if (mx != my) return mx ? -1 : 1;
            if (mx && x[0] != y[0]) return x[0] - y[0];
            if (mx) return (y[1] - y[0]) - (x[1] - x[0]);
            return 0; } });
        List<boolean[]> used = new ArrayList<boolean[]>();
        Map<LocalDate, List<Events.Item>> out = new java.util.HashMap<LocalDate, List<Events.Item>>();
        for (String key : keys) {
            int[] sp = span.get(key); int slot = 0;
            for (;; slot++) {
                if (slot == used.size()) used.add(new boolean[r.n]);
                boolean free = true; for (int k = sp[0]; k <= sp[1]; k++) if (used.get(slot)[k]) { free = false; break; }
                if (free) break;
            }
            Events.Item[] its = per.get(key);
            for (int k = sp[0]; k <= sp[1]; k++) {
                used.get(slot)[k] = true;
                if (its[k] == null) continue;
                List<Events.Item> l = out.get(r.start.plusDays(k)); if (l == null) { l = new ArrayList<Events.Item>(); out.put(r.start.plusDays(k), l); }
                while (l.size() <= slot) l.add(null);
                l.set(slot, its[k]);
            }
        }
        return out;
    }

    /** True when two timed events on the day overlap. Cancelled events and reminders such as the
     *  yearly "burmese food fete" never count. */
    static boolean clash(List<Events.Item> items) {
        if (items == null || items.size() < 2) return false;
        List<Events.Item> t = new ArrayList<Events.Item>();
        for (Events.Item it : items) {
            if (it.allDay || it.cancelled || it.cont || it.title == null) continue;
            if (it.title.toLowerCase(Locale.ROOT).contains("burmese food fete")) continue;
            t.add(it);
        }
        for (int i = 0; i < t.size(); i++) for (int j = i + 1; j < t.size(); j++)
            if (t.get(i).begin < t.get(j).end && t.get(j).begin < t.get(i).end) return true;
        return false;
    }
    private static void addBandRow(G g, RemoteViews cell, int bandId, List<Events.Item> marks, float mdp, int lines, float h, boolean dim) {
        RemoteViews row = new RemoteViews(g.c.getPackageName(), R.layout.band_row);
        row.setViewLayoutHeight(R.id.row, h - 1, DIP);
        row.setViewLayoutMargin(R.id.row, g.bandTop ? RemoteViews.MARGIN_BOTTOM : RemoteViews.MARGIN_TOP, 1, DIP);
        for (Events.Item it : marks) {
            RemoteViews m = new RemoteViews(g.c.getPackageName(), R.layout.marker);
            m.setInt(R.id.mkbg, "setColorFilter", it.color | 0xFF000000);
            if (dim) m.setInt(R.id.mkbg, "setImageAlpha", g.dimA);
            if (g.rMk > 0) m.setImageViewResource(R.id.mkbg, Look.rr(g.rMk));
            if (!g.pal.mkAuto) m.setTextColor(R.id.txt, dim ? Pal.alpha(g.pal.mkText, Math.max(0xB3, g.dimA)) : g.pal.mkText);
            else m.setTextColor(R.id.txt, Pal.lum(it.color) > 0.62 ? (dim ? Pal.alpha(0xFF101410, Math.max(0xB3, g.dimA)) : 0xFF101410) : (dim ? Pal.alpha(0xFFFFFFFF, Math.max(0xB3, g.dimA)) : 0xFFFFFFFF));
            String mt = it.title.isEmpty() ? "(no title)" : Events.display(it.title, g.keys, true);
            if (g.upper) mt = mt.toUpperCase(Locale.ROOT);
            m.setTextViewText(R.id.txt, width(g, lines <= 1 ? nbsp(font(g.fontMk, mt)) : font(g.fontMk, mt)));   // cut at the edge, no "…" (v3.27)
            if (g.inkEv || g.toon) { m.setViewVisibility(R.id.mkln, View.VISIBLE); m.setInt(R.id.mkln, "setColorFilter", g.pal.ink); if (dim) m.setInt(R.id.mkln, "setImageAlpha", g.dimA); }
            if (g.toon) m.setImageViewResource(R.id.mkln, R.drawable.toon_box);
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
            if (it.cont && !it.hl) {
                // last night's shift carrying on past midnight (v3.25): a short tail (3 hours or less) is one small line;
                // a longer one is as tall as its length on the day's time scale
                float span = Math.max(0, e);
                h[i] = span <= 180 ? g.lineH + 2 : Math.max(g.lineH + 2, Math.min(area * 0.6f, span / winLen * area));
            }
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
            int ml = Math.max(1, (int) Math.floor((h[i] - 0.5f) / g.lineH));
            RemoteViews chip = chip(g, timed.get(i), clip(g, text[i], textW(g, cpl * g.tdp * 0.47f + 6), g.tdp, ml), dim);
            chip.setViewLayoutMargin(R.id.chip, RemoteViews.MARGIN_TOP, Math.max(0, top[i] - prev), DIP);
            chip.setViewLayoutHeight(R.id.chip, h[i], DIP);
            chip.setInt(R.id.txt, "setMaxLines", ml);
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
            if (it.cont && !it.hl && !it.allDay && contMins(g, it) <= 180) need = 1;   // short overnight tail: one small line (v3.25)
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
            RemoteViews chip = chip(g, it, clip(g, text, textW(g, cpl * g.tdp * 0.47f + 6), g.tdp, maxLines), dim);
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

    /** Minutes an overnight event runs on past midnight into this day. */
    static float contMins(G g, Events.Item it) {
        long ds = java.time.Instant.ofEpochMilli(it.end).atZone(g.zone).toLocalDate().atStartOfDay(g.zone).toInstant().toEpochMilli();
        return (it.end - ds) / 60000f;
    }
    private static RemoteViews chip(G g, Events.Item it, CharSequence text, boolean dim) { return chip(g, it, text, dim ? g.dimA : 255); }
    /** An event block; a below 255 fades it (past days use the past-event strength; today's finished events fade into it gradually). */
    private static RemoteViews chip(G g, Events.Item it, CharSequence text, int a) {
        RemoteViews chip = new RemoteViews(g.c.getPackageName(), R.layout.chip);
        boolean dim = a < 255;
        int fg = inv(g, it) ? 0xFF101410 : g.pal.evText;
        chip.setInt(R.id.bar, "setColorFilter", it.color | 0xFF000000);
        if (dim) chip.setInt(R.id.bar, "setImageAlpha", a);
        chip.setInt(R.id.chipbg, "setColorFilter", it.color | 0xFF000000);
        chip.setInt(R.id.chipbg, "setImageAlpha", Math.round(g.evPct * 2.55f * (a / 255f)));
        if (g.rEv > 0) { int rr = Look.rr(g.rEv); chip.setImageViewResource(R.id.chipbg, rr); chip.setImageViewResource(R.id.bar, rr); }
        chip.setTextColor(R.id.txt, dim ? Pal.alpha(fg, a) : fg);
        chip.setTextViewText(R.id.txt, width(g, text));
        if (g.inkEv || g.toon) { chip.setViewVisibility(R.id.chipln, View.VISIBLE); chip.setInt(R.id.chipln, "setColorFilter", g.pal.ink); if (dim) chip.setInt(R.id.chipln, "setImageAlpha", a); }
        if (g.toon) chip.setImageViewResource(R.id.chipln, R.drawable.toon_box);
        chip.setTextViewTextSize(R.id.txt, DIP, g.tdp);
        if (g.barW <= 0) { chip.setViewVisibility(R.id.bar, View.GONE); if (g.inkEv || g.toon) { int pd = Math.round(2 * g.c.getResources().getDisplayMetrics().density); chip.setViewPadding(R.id.chip_in, pd, 0, 0, 0); } }
        else if (g.barW != 3) chip.setViewLayoutWidth(R.id.bar, g.barW, DIP);
        return chip;
    }

    /** True when the event block is solid and light enough that dark text reads better on it. */
    private static boolean inv(G g, Events.Item it) {
        return g.evPct >= 50 && Pal.lum(Pal.mix(g.curTile, it.color, g.evPct / 100f)) > 0.58;
    }
    /** Spaces that can't break, so a line that doesn't fit is cut mid-word at the box edge and shows as many letters as fit. */
    static CharSequence nbsp(CharSequence t) {
        SpannableStringBuilder sb = new SpannableStringBuilder(t);
        for (int i = 0; i < sb.length(); i++) if (sb.charAt(i) == ' ') sb.replace(i, i + 1, " ");
        return sb;
    }
    /** Room for text inside an event block of this width (padding and the colour bar taken off). */
    static float textW(G g, float blockW) { return blockW - 2 - 3 - (g.barW > 0 ? g.barW : (g.inkEv || g.toon ? 2 : 0)) - 1; }
    /** Text that doesn't fit its lines is cut at the edge, never ended with "…" (Ben, 26 Nov 2026): lines wrap at words as
     *  usual, and the last line that shows carries straight on, so the first letters of the next words still show. */
    static CharSequence clip(G g, CharSequence t, float wDp, float tdp, int lines) {
        try {
            float d = g.c.getResources().getDisplayMetrics().density;
            TextPaint tp = new TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            tp.setTextSize(tdp * d); tp.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
            CharSequence w = width(g, t);
            StaticLayout sl = StaticLayout.Builder.obtain(w, 0, w.length(), tp, Math.max(1, Math.round(wDp * d))).setIncludePad(false).build();
            if (sl.getLineCount() <= lines) return t;
            SpannableStringBuilder sb = new SpannableStringBuilder(t);
            int keep = Math.max(0, lines - 1), from = keep > 0 ? sl.getLineEnd(keep - 1) : 0;
            for (int i = from; i < sb.length(); i++) if (sb.charAt(i) == ' ') sb.replace(i, i + 1, " ");
            for (int k = keep - 1; k >= 0; k--) {
                int e = sl.getLineEnd(k);
                if (e > 0 && e <= sb.length() && sb.charAt(e - 1) == ' ') sb.replace(e - 1, e, "\n"); else if (e < sb.length()) sb.insert(e, "\n");
            }
            return sb;
        } catch (Throwable ignored) { return t; }
    }
    private static float ease(float x) { return x <= 0 ? 0 : x >= 1 ? 1 : x * x * (3 - 2 * x); }
    private static float lerp(float a, float b, float f) { return a + (b - a) * f; }

    /** One stretch of today's time: a group of events that overlap, or the empty time between them. */
    private static final class Seg { float t0, t1, w, m, h, top; List<Integer> ev; }

    /** Today's box (v3.27, option D "squish the past"). Each minute has a weight: finished time shrinks to sq_past, gradually
     *  over sq_ramp minutes after each event ends; an event that is on keeps its true scale, and empty time after the last
     *  event (when the box runs on to midnight) is a thin strip. Every block keeps room for its text, the rest of the height is
     *  shared by weight, and the current-time line sits on the same scale, so it is always in the right place between or
     *  inside the events. Finished events also fade and their text gets smaller, gradually. Other days are unchanged. */
    private static boolean squish(G g, RemoteViews cell, List<Events.Item> timed, LocalDate day, float area, float innerW, float cpl) {
        int n = timed.size();
        long ds = day.atStartOfDay(g.zone).toInstant().toEpochMilli();
        ZonedDateTime zn = ZonedDateTime.now(g.zone);
        final float now = zn.getHour() * 60 + zn.getMinute() + zn.getSecond() / 60f;
        float[] s = new float[n], e = new float[n], f = new float[n]; boolean[] tail = new boolean[n]; CharSequence[] text = new CharSequence[n];
        float lo = 1440, hi = 0, lastEnd = 0; boolean any = false;
        for (int i = 0; i < n; i++) {
            Events.Item it = timed.get(i);
            s[i] = it.cont ? 0 : (it.begin - ds) / 60000f; e[i] = Math.max(s[i], Math.min(1440, (it.end - ds) / 60000f));
            f[i] = ease((now - e[i]) / g.sqRamp);
            tail[i] = it.cont && !it.hl && e[i] <= 180;
            text[i] = label(g, it, f[i] >= 0.5f);
            if (tail[i]) continue;
            any = true; hi = Math.max(hi, e[i]); lastEnd = Math.max(lastEnd, e[i]);
            if (!it.cont) lo = Math.min(lo, s[i]);
        }
        if (!any) return false;
        if (lo >= hi) lo = Math.max(0, (float) Math.floor(hi / 60f) * 60f - 60);
        lo = (float) Math.floor(lo / 60f) * 60f; hi = (float) Math.ceil(hi / 60f) * 60f;
        float mw = g.minWin * 60f;
        if (hi - lo < mw) hi = Math.min(1440, lo + mw);
        if (hi - lo < mw) lo = Math.max(0, hi - mw);
        boolean toMid = g.sqMid && hi < 1440;
        if (toMid) hi = 1440;
        // minute weights and their running total
        double[] cum = new double[1442];
        for (int i = 0; i < 1441; i++) {
            float t = i + 0.5f, w = 1;
            if (t < now) {
                float shr = -1;
                for (int k = 0; k < n; k++) if (!tail[k] && s[k] <= t && t < e[k]) shr = shr < 0 ? f[k] : Math.min(shr, f[k]);
                if (shr < 0) shr = ease((now - t) / g.sqRamp);
                w = lerp(1, g.sqPast, shr);
            }
            if (toMid && t > lastEnd) w *= 0.12f;
            cum[i + 1] = cum[i] + w;
        }
        // tails of last night's shifts: one line each at the top, outside the time scale
        float y0 = 0, tailEnd = 0;
        List<Integer> order = new ArrayList<Integer>();
        for (int i = 0; i < n; i++) if (!tail[i]) order.add(i);
        final float[] fs = s;
        Collections.sort(order, new java.util.Comparator<Integer>() { public int compare(Integer x, Integer y) { return Float.compare(fs[x], fs[y]); } });   // no lambdas: the build does not desugar them
        // overlapping events make one group; a later overlapping event is drawn over the earlier one, indented
        List<Seg> segs = new ArrayList<Seg>(), groups = new ArrayList<Seg>();
        int[] col = new int[n];
        for (int i : order) {
            float a = Math.max(lo, s[i]), b = Math.min(hi, e[i]);
            Seg c = groups.isEmpty() ? null : groups.get(groups.size() - 1);
            if (c != null && a < c.t1) { c.ev.add(i); c.t1 = Math.max(c.t1, b); }
            else { c = new Seg(); c.t0 = a; c.t1 = Math.max(a, b); c.ev = new ArrayList<Integer>(); c.ev.add(i); groups.add(c); }
        }
        for (Seg c : groups) {
            List<Float> ends = new ArrayList<Float>();
            double r = 0;
            for (int i : c.ev) { int k = 0; while (k < ends.size() && ends.get(k) > s[i]) k++; if (k == ends.size()) ends.add(0f); ends.set(k, e[i]); col[i] = Math.min(2, k); }
            for (int x = 0; x < c.ev.size(); x++) {
                int i = c.ev.get(x);
                float lh = g.lineH * lerp(1, g.sqFont, f[i]), shrink = 1 - 0.35f * col[i];
                int need = g.detailed ? Math.max(1, Math.min(Math.min(g.sqMinL, g.maxLines), (int) Math.ceil(text[i].length() / (cpl * shrink)))) : 1;
                float m = lerp(need * lh + 2, g.sqStrip ? 4 : lh + 2, f[i]);
                double we = Math.max(1e-6, W(cum, Math.max(lo, s[i]), Math.min(hi, e[i])));
                r = Math.max(r, m / we);
                if (x + 1 < c.ev.size()) { int nx = c.ev.get(x + 1); if (s[nx] < e[i]) r = Math.max(r, m / Math.max(1e-6, W(cum, s[i], s[nx]))); }
            }
            c.w = (float) W(cum, c.t0, c.t1); c.m = (float) (c.w * r);
        }
        float t = lo;
        for (int k = 0; k < groups.size(); k++) { Seg c = groups.get(k), gp = new Seg(); gp.t0 = t; gp.t1 = c.t0; gp.w = (float) W(cum, t, c.t0); gp.m = k > 0 ? 1.5f : 0; segs.add(gp); segs.add(c); t = c.t1; }
        Seg end = new Seg(); end.t0 = t; end.t1 = hi; end.w = (float) W(cum, t, hi); end.m = toMid && hi - t > 30 ? 6 : 0; segs.add(end);
        // tails
        List<Integer> tails = new ArrayList<Integer>();
        for (int i = 0; i < n; i++) if (tail[i]) { tails.add(i); y0 += g.lineH * lerp(1, g.sqFont, f[i]) + 2 + 1.5f; tailEnd = Math.max(tailEnd, e[i]); }
        float A = Math.max(10, area - y0), sumM = 0, sumW = 0;
        for (Seg sg : segs) { sumM += sg.m; sumW += sg.w; }
        if (sumM >= A) { for (Seg sg : segs) sg.h = sg.m * A / sumM; }
        else {
            double ka = 0, kb = Math.max(1e-3, A / Math.max(1e-6, sumW));
            while (fill(segs, kb) < A) kb *= 2;
            for (int it = 0; it < 50; it++) { double mid = (ka + kb) / 2; if (fill(segs, mid) < A) ka = mid; else kb = mid; }
            for (Seg sg : segs) sg.h = (float) Math.max(sg.m, kb * sg.w);
        }
        float y = y0; for (Seg sg : segs) { sg.top = y; y += sg.h; }

        RemoteViews frame = new RemoteViews(g.c.getPackageName(), R.layout.tl_frame);
        frame.setViewLayoutHeight(R.id.tlf, area, DIP);
        float laneW = innerW - 2, ty = 0;
        for (int i : tails) {
            float fz = lerp(1, g.sqFont, f[i]), lh = g.lineH * fz;
            frame.addView(R.id.tlf, lane(g, timed.get(i), text[i], 0, ty, lh + 2, laneW, fz, f[i]));
            ty += lh + 2 + 1.5f;
        }
        for (int i : order) {
            float a = Y(segs, cum, Math.max(lo, s[i])), b = Y(segs, cum, Math.min(hi, e[i])), hh = b - a;
            for (int k : order) if (k != i && col[k] == col[i] && Math.abs(s[k] - e[i]) < 1) { hh -= 1.5f; break; }   // a hairline between back-to-back blocks
            float ind = laneW * 0.35f * col[i];
            frame.addView(R.id.tlf, lane(g, timed.get(i), text[i], ind, a, Math.max(3, hh), laneW - ind, lerp(1, g.sqFont, f[i]), f[i]));
        }
        cell.addView(R.id.evs, frame);
        g.nowY = !tails.isEmpty() && now < tailEnd ? now / tailEnd * Math.max(0, y0 - 1.5f) : Y(segs, cum, Math.max(lo, Math.min(hi, now)));
        return true;
    }
    private static double W(double[] cum, float a, float b) { return Math.max(0, C(cum, b) - C(cum, a)); }
    private static double C(double[] cum, float t) { t = Math.max(0, Math.min(1440, t)); int i = (int) Math.floor(t); double fr = t - i; return cum[i] + (cum[Math.min(1441, i + 1)] - cum[i]) * fr; }
    private static double fill(List<Seg> segs, double k) { double a = 0; for (Seg sg : segs) a += Math.max(sg.m, k * sg.w); return a; }
    private static float Y(List<Seg> segs, double[] cum, float t) {
        for (int k = 0; k < segs.size(); k++) { Seg sg = segs.get(k); if (t <= sg.t1 || k == segs.size() - 1) return sg.top + (sg.w > 1e-9 ? (float) (sg.h * W(cum, sg.t0, Math.min(t, sg.t1)) / sg.w) : 0); }
        return 0;
    }
    /** One event of today's box in its own lane: left indent, top and height in dp, text size and fade by how finished it is. */
    private static RemoteViews lane(G g, Events.Item it, CharSequence text, float left, float top, float h, float w, float fz, float fin) {
        RemoteViews lane = new RemoteViews(g.c.getPackageName(), R.layout.lane);
        if (left > 0) lane.setViewLayoutMargin(R.id.lane, RemoteViews.MARGIN_LEFT, left, DIP);
        float lh = g.lineH * fz;
        int ml = Math.max(1, (int) Math.floor((h - 0.5f) / lh));
        RemoteViews chip = chip(g, it, clip(g, text, textW(g, w), g.tdp * fz, ml), Math.round(lerp(255, g.dimA, fin)));
        chip.setTextViewTextSize(R.id.txt, DIP, g.tdp * fz);
        chip.setViewLayoutMargin(R.id.chip, RemoteViews.MARGIN_TOP, Math.max(0, top), DIP);
        chip.setViewLayoutHeight(R.id.chip, h, DIP);
        chip.setInt(R.id.txt, "setMaxLines", ml);
        lane.addView(R.id.lane, chip);
        return lane;
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
        if (withTime || (it.cont && !it.allDay)) {
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
            if (!"text".equals(g.codeMode)) {
                int cc = "event".equals(g.codeMode) ? Pal.mix(it.color, g.pal.dark && !inv ? 0xFFFFFFFF : 0xFF000000, 0.45f) : g.codeCol;
                // on a light, solid event block the code still takes its colour, darkened until it reads (v3.20)
                int bg = Pal.mix(g.curTile, it.color, g.evPct / 100f);
                for (int k = 0; k < 4 && Math.abs(Pal.lum(cc) - Pal.lum(bg)) < 0.3; k++) cc = Pal.mix(cc, Pal.lum(bg) > 0.5 ? 0xFF000000 : 0xFFFFFFFF, 0.3f);
                sb.setSpan(new ForegroundColorSpan(Pal.alpha(cc, dim ? g.dimA : 0xFF)), s, s + code.end(), flag);
            }
        }
        Matcher hrs = HOURS.matcher(t);
        while (g.hrsSmall && hrs.find()) {
            sb.setSpan(new RelativeSizeSpan(0.82f), s + hrs.start(), s + hrs.end(), flag);
            sb.setSpan(new ForegroundColorSpan(Pal.alpha(muted, dim ? g.dimA : 0xFF)), s + hrs.start(), s + hrs.end(), flag);
        }
        return sb;
    }

    /** Hand-drawn pen lines over the day boxes, plus hatching on past days and dots on weekends.
     *  Drawn as one alpha-only picture and tinted with the ink colour. */
    /** Weekly earnings: a thin line under the grid (or a short one in the header). Returns the dp it takes from the grid. */
    private static float earnings(Context c, RemoteViews rv, Prefs p, Pal pal, String font, float wDp) {
        String mode = p.s("earn", "head");
        if ("off".equals(mode)) return 0;
        boolean head = "head".equals(mode) && p.on("h_head", true);
        Earn.Week w = Earn.week(c, p);
        double tgt = Earn.target(p), rt = Earn.rateTarget(p), ht = Earn.hoursTarget(p);
        int[] sc = { Pal.hex(p.s("e_cr", ""), 0xFFD2412F), Pal.hex(p.s("e_ca", ""), 0xFFE7A531), Pal.hex(p.s("e_cg", ""), 0xFF3F9A5A) };
        int st = sc[w.status], low = sc[0];
        int txt = Pal.hex(p.s("e_ctext", ""), pal.head), dim = (txt & 0x00FFFFFF) | 0x99000000;
        SpannableStringBuilder sb = new SpannableStringBuilder();
        span(sb, "\u25CF ", new ForegroundColorSpan(st));
        if (head) {   // two short lines so it fits the header without taking height: "● $3,068" over "$47/h"
            span(sb, money(w.est), new ForegroundColorSpan(w.est + 0.5 >= tgt ? txt : low));
            sb.append("\n");
            span(sb, money(w.rate) + "/h", new ForegroundColorSpan(w.rate + 0.005 >= rt ? txt : low));
        } else {
            span(sb, "WEEK  ", new ForegroundColorSpan(dim), new RelativeSizeSpan(0.82f), new StyleSpan(Typeface.BOLD));
            span(sb, money(w.est), new ForegroundColorSpan(w.est + 0.5 >= tgt ? txt : low), new StyleSpan(Typeface.BOLD));
            span(sb, " / " + whole(tgt), new ForegroundColorSpan(dim), new RelativeSizeSpan(0.82f));
            span(sb, "  ·  ", new ForegroundColorSpan(dim));
            span(sb, money(w.rate) + "/h", new ForegroundColorSpan(w.rate + 0.005 >= rt ? txt : low), new StyleSpan(Typeface.BOLD));
            span(sb, " / " + whole(rt), new ForegroundColorSpan(dim), new RelativeSizeSpan(0.82f));
            span(sb, "  ·  ", new ForegroundColorSpan(dim));
            span(sb, hrs(w.hours) + "h", new ForegroundColorSpan(w.hours <= ht + 0.05 ? txt : low), new StyleSpan(Typeface.BOLD));
            span(sb, " / " + hrs(ht), new ForegroundColorSpan(dim), new RelativeSizeSpan(0.82f));
        }
        if (!font.isEmpty()) sb.setSpan(new TypefaceSpan(font), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (head) {
            rv.setTextViewText(R.id.earn_head, sb);
            rv.setViewVisibility(R.id.earn_head, View.VISIBLE);
            if (p.tenths("edp10", 0) > 0) rv.setTextViewTextSize(R.id.earn_head, DIP, p.tenths("edp10", 10.5f));
            {   // how wide the two lines are, so the title can be sized to leave room for every button
                android.graphics.Paint m = new android.graphics.Paint(); m.setTypeface(Typeface.DEFAULT_BOLD); m.setTextSize(p.tenths("edp10", 10.5f));
                EARN_W = Math.max(m.measureText("\u25CF " + money(w.est)), m.measureText(money(w.rate) + "/h")) + 8;
            }
            // make room: slimmer header buttons
            rv.setViewLayoutWidth(R.id.prev, 26, DIP); rv.setViewLayoutWidth(R.id.next, 26, DIP);
            rv.setViewLayoutWidth(R.id.add, 30, DIP); rv.setViewLayoutWidth(R.id.settings, 28, DIP);
            return 0;
        }
        rv.setTextViewText(R.id.earn_txt, sb);
        if (p.tenths("edp10", 0) > 0) rv.setTextViewTextSize(R.id.earn_txt, DIP, p.tenths("edp10", 11));
        rv.setViewVisibility(R.id.earn_box, View.VISIBLE);
        if (p.on("e_bar", true)) {
            float dens = c.getResources().getDisplayMetrics().density;
            int W = Math.max(40, Math.round((wDp - 16) * dens)), Hh = Math.max(2, Math.round(3 * dens));
            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(W, Hh, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(b);
            android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            float r = Hh / 2f, full = (float) Math.max(tgt, w.est);
            if (full <= 0) full = 1;
            pt.setColor((st & 0x00FFFFFF) | 0x30000000); cv.drawRoundRect(0, 0, W, Hh, r, r, pt);
            pt.setColor((st & 0x00FFFFFF) | 0x70000000); cv.drawRoundRect(0, 0, W * (float) (w.est / full), Hh, r, r, pt);
            pt.setColor(st); cv.drawRoundRect(0, 0, W * (float) (w.done / full), Hh, r, r, pt);
            float tx = Math.min(W - dens, W * (float) (tgt / full));
            pt.setColor(txt); cv.drawRect(tx - dens * 0.75f, 0, tx + dens * 0.75f, Hh, pt);
            rv.setImageViewBitmap(R.id.earn_bar, b);
        } else rv.setViewVisibility(R.id.earn_bar, View.GONE);
        return 20;
    }
    /** A week that is over: its pay and $/hour as faint text across the row (v3.7). */
    private static void watermark(G g, RemoteViews row, Row r, float rowH, float wDp) {
        Prefs p = g.p;
        if (!p.on("e_wm", true) || "off".equals(p.s("earn", "head"))) return;
        Earn.Week w = Earn.week(g.c, p, r.start);
        if (w.shifts == 0) return;
        float size = p.tenths("wmdp10", 0) > 0 ? p.tenths("wmdp10", 14) : Math.max(11f, Math.min(20f, rowH * 0.36f));
        int op = p.i("e_wmop", 80); if (op == 45) op = 80;   // 45 was the old default, which was too dim to read (v3.9)
        int a = Math.round(Math.max(10, Math.min(100, op)) * 2.55f);
        int col = Pal.hex(p.s("e_wmcol", ""), g.pal.fg);
        String txt = money(w.rate) + "/hr  ·  " + hrs(w.hours) + "h  ·  " + money(w.est);
        { android.graphics.Paint m = new android.graphics.Paint(); m.setTypeface(Typeface.DEFAULT_BOLD); m.setTextSize(size);
          float tw = m.measureText(txt); if (tw > wDp * 0.92f) size = size * wDp * 0.92f / tw; }   // keep it on one line
        SpannableStringBuilder sb = new SpannableStringBuilder(txt);
        if (!g.font.isEmpty()) sb.setSpan(new TypefaceSpan(g.font), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        row.setTextViewText(R.id.wm, sb);
        row.setTextViewTextSize(R.id.wm, DIP, size);
        row.setTextColor(R.id.wm, (col & 0x00FFFFFF) | (a << 24));
        row.setViewVisibility(R.id.wm, View.VISIBLE);
    }
    static float EARN_W = 0;
    /**
     * Header buttons: with the hand-drawn look, the +, today and settings buttons get pen-line boxes like the calendar.
     * Returns the width (dp) left for the month title so every button stays on screen (v3.9).
     */
    private static float headerButtons(G g, RemoteViews rv, float wDp, float hh) {
        Prefs p = g.p; boolean slim = EARN_W > 0;
        float wPrev = p.on("h_prev", true) ? (slim ? 26 : 34) : 0, wNext = p.on("h_next", true) ? (slim ? 26 : 34) : 0;
        float wAdd = p.on("h_add", true) ? (slim ? 30 : 38) : 0, wToday = p.on("h_today", true) ? 30 : 0, wSet = p.on("h_set", true) ? (slim ? 28 : 38) : 0;
        float hw = HEAD_W > 0 ? Math.min(wDp, HEAD_W) : wDp;
        float left = hw - 4 - 12 - wPrev - wNext - wAdd - wToday - wSet - EARN_W - 6;
        float titleMax = Math.max(60, left);
        // the phone's own title text: shrink it to fit as well
        if (g.p.on("h_head", true)) {
            android.graphics.Paint m = new android.graphics.Paint(); m.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            float sz = p.tenths("hdp10", 15); m.setTextSize(sz);
            // sized for the longest month name, so the title is the same size every month
            float tw = m.measureText(widest(TITLE_TXT) + (p.on("h_arrow", true) ? " ▾" : ""));
            if (tw > titleMax && tw > 0) rv.setTextViewTextSize(R.id.title, DIP, Math.max(8, sz * titleMax / tw));
        }
        if (g.ink && p.on("hink", true) && (wAdd + wToday + wSet) > 0) {
            float d = g.c.getResources().getDisplayMetrics().density;
            int W = Math.round((wAdd + wToday + wSet) * d), H = Math.round(hh * d);
            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(Math.max(1, W), Math.max(1, H), android.graphics.Bitmap.Config.ALPHA_8);
            android.graphics.Canvas cv = new android.graphics.Canvas(b);
            android.graphics.Paint pen = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            pen.setStyle(android.graphics.Paint.Style.STROKE); pen.setStrokeCap(android.graphics.Paint.Cap.ROUND); pen.setColor(0xFF000000);
            java.util.Random rnd = new java.util.Random(4242);
            ROUGH = g.rough;
            float amp = g.wobble * 0.13f * d, bh = Math.min(hh - 6, 24) * d, y0 = (H - bh) / 2f, x = 0;
            for (float bw : new float[] { wAdd, wToday, wSet }) {
                if (bw <= 0) continue;
                float side = Math.min(bw - 4, 26) * d, cx = x + bw * d / 2f;
                float sh = g.toon ? 2.2f * d : 0;
                inkBox(cv, pen, rnd, cx - side / 2f, y0, cx + side / 2f - sh, y0 + bh - sh, Math.min(g.rTile, 6) * d, amp, g.inkW * d);
                if (g.toon) toonShadow(cv, cx - side / 2f, y0, cx + side / 2f - sh, y0 + bh - sh, sh);
                x += bw * d;
            }
            rv.setImageViewBitmap(R.id.btn_ink, b);
            rv.setInt(R.id.btn_ink, "setColorFilter", g.pal.ink);
            rv.setViewVisibility(R.id.btn_ink, View.VISIBLE);
            rv.setInt(R.id.today, "setBackgroundResource", 0);   // the pen box replaces the plain outline
        }
        return titleMax;
    }
    static String TITLE_TXT = "";
    static float HEAD_W = 0;
    /** The title with its month swapped for the longest one, for sizing that does not change month to month. */
    static String widest(String t) {
        for (String m : new String[] { "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER" })
            if (t.startsWith(m)) return "SEPTEMBER" + t.substring(m.length());
        return t;
    }
    private static void span(SpannableStringBuilder sb, String s, Object... spans) {
        int a = sb.length(); sb.append(s);
        for (Object o : spans) sb.setSpan(o, a, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    static String money(double v) { return "$" + String.format(Locale.ENGLISH, "%,d", Math.round(v)); }
    static String whole(double v) { return String.format(Locale.ENGLISH, "%,d", Math.round(v)); }
    static String hrs(double v) { double r = Math.round(v * 2) / 2.0; return r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r); }

    private static android.graphics.Bitmap inkLayer(G g, List<Row> rows, float wDp, float hDp) {
        float d = Math.min(1.5f, g.c.getResources().getDisplayMetrics().density);
        int W = Math.max(1, Math.round(wDp * d)), H = Math.max(1, Math.round(hDp * d));
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(W, H, android.graphics.Bitmap.Config.ALPHA_8);
        android.graphics.Canvas cv = new android.graphics.Canvas(b);
        java.util.Random rnd = new java.util.Random(rows.get(0).start.toEpochDay() * 7919L);
        android.graphics.Paint pen = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        pen.setStyle(android.graphics.Paint.Style.STROKE); pen.setStrokeCap(android.graphics.Paint.Cap.ROUND); pen.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        pen.setColor(0xFF000000);
        android.graphics.Paint fine = new android.graphics.Paint(pen); fine.setStrokeWidth(Math.max(0.6f, g.inkW * 0.45f) * d); fine.setAlpha(66);
        android.graphics.Paint dot = new android.graphics.Paint(pen); dot.setStrokeWidth(1.1f * d); dot.setAlpha(102);
        float units = 0; for (Row r : rows) units += r.w;
        float amp = g.wobble * 0.13f * d, y = 0;
        ROUGH = g.rough;
        for (int ri = 0; ri < rows.size(); ri++) {
            Row r = rows.get(ri);
            float rh = H * r.w / units, cw = (float) W / r.n;
            for (int k = 0; k < r.n; k++) {
                LocalDate day = r.start.plusDays(k);
                boolean past = day.isBefore(g.today), wk = day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
                float x0 = k * cw, x1 = x0 + cw, y0 = y, y1 = y + rh, in = (g.inkBoxes ? 3f : 1.5f) * d;
                cv.save(); cv.clipRect(x0 + in, y0 + in, x1 - in, y1 - in);
                boolean hatchHere = "all".equals(g.hwhere) || ("wkend".equals(g.hwhere) ? wk : "both".equals(g.hwhere) ? past || wk : past);
                if (hatchHere && !"none".equals(g.hatch)) {
                    hatchLines(cv, fine, rnd, x0, y0, x1, y1, g.hgap * d, g.hang);
                    if ("cross".equals(g.hatch)) hatchLines(cv, fine, rnd, x0, y0, x1, y1, g.hgap * d, g.hang + 90);
                }
                boolean dotsHere = "all".equals(g.stipOn) || ("past".equals(g.stipOn) ? past : "today".equals(g.stipOn) ? day.equals(g.today) : wk && !past);
                if (g.stip && dotsHere) {
                    int n = Math.round((x1 - x0) * (y1 - y0) * g.stipD / (d * d * 40f));
                    for (int i = 0; i < n; i++) cv.drawPoint(x0 + rnd.nextFloat() * (x1 - x0), y0 + rnd.nextFloat() * (y1 - y0), dot);
                }
                cv.restore();
                if (g.inkBoxes) {
                    float sh = g.toon ? Math.min(2.2f * d, (x1 - x0) / 8) : 0;
                    inkBox(cv, pen, rnd, x0 + 1.5f * d, y0 + 1.5f * d, x1 - 1.5f * d - sh, y1 - 1.5f * d - sh, g.rTile * d, amp, g.inkW * d);
                    if (g.toon) toonShadow(cv, x0 + 1.5f * d, y0 + 1.5f * d, x1 - 1.5f * d - sh, y1 - 1.5f * d - sh, sh);
                }
            }
            if (!g.inkBoxes) {
                if (ri == 0) inkLine(cv, pen, rnd, 0, y, W, y, amp, g.inkW * d * 1.3f);
                inkLine(cv, pen, rnd, 0, y + rh, W, y + rh, amp, g.inkW * d * (ri == rows.size() - 1 ? 1.3f : 1f));
                for (int k = 0; k <= r.n; k++) {
                    float x = k == 0 ? 0 : k == r.n ? W : k * cw;
                    inkLine(cv, pen, rnd, x, y, x, y + rh, amp, g.inkW * d * (k == 0 || k == r.n ? 1.3f : 1f));
                }
            }
            y += rh;
        }
        return b;
    }
    /** Print texture for the whole widget, kept light so text stays readable: a colour wash from top to bottom,
     *  paper blotches and fibres, halftone dots (in the pen colour) and film grain. Null when all are off. */
    static android.graphics.Bitmap texLayer(Context c, Prefs p, Pal pal, float wDp, float hDp, float rBg) {
        return texLayer(c, p, pal, wDp, hDp, rBg, p.i("grain", 0), p.i("paper", 0), p.i("halftone", 0), p.i("wash", 0));
    }
    static android.graphics.Bitmap texLayer(Context c, Prefs p, Pal pal, float wDp, float hDp, float rBg, int grain0, int paper0, int ht0, int wash0) {
        int grain = clamp(grain0, 0, 100), paper = clamp(paper0, 0, 100), ht = clamp(ht0, 0, 100), wash = clamp(wash0, 0, 60);
        if (grain + paper + ht + wash == 0) return null;
        float d = Math.min(1.25f, c.getResources().getDisplayMetrics().density);
        int W = Math.max(1, Math.round(wDp * d)), H = Math.max(1, Math.round(hDp * d));
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(W, H, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(b);
        float r = rBg >= 25 ? Math.min(W, H) / 2f : Math.min(rBg * d, Math.min(W, H) / 2f);
        android.graphics.Path clip = new android.graphics.Path(); clip.addRoundRect(0, 0, W, H, r, r, android.graphics.Path.Direction.CW);
        cv.clipPath(clip);
        java.util.Random rnd = new java.util.Random(90210);
        android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        if (wash > 0) {
            int a = Math.round(wash * 2.55f);
            pt.setShader(new android.graphics.LinearGradient(0, 0, 0, H, Pal.alpha(pal.wash1, a), Pal.alpha(pal.wash2, a), android.graphics.Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, W, H, pt); pt.setShader(null);
        }
        int dark = pal.dark ? 0xFF000000 : pal.ink, light = 0xFFFFFFFF;
        if (paper > 0) {
            for (int i = 0; i < 70; i++) {   // soft blotches, like uneven ink take-up on rough paper
                float cx = rnd.nextFloat() * W, cy = rnd.nextFloat() * H, rad = (14 + rnd.nextFloat() * 60) * d;
                int col = rnd.nextBoolean() ? dark : light, a = Math.round(paper * (0.10f + rnd.nextFloat() * 0.16f));
                pt.setShader(new android.graphics.RadialGradient(cx, cy, rad, Pal.alpha(col, a), Pal.alpha(col, 0), android.graphics.Shader.TileMode.CLAMP));
                cv.drawCircle(cx, cy, rad, pt);
            }
            pt.setShader(null); pt.setStyle(android.graphics.Paint.Style.STROKE); pt.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            int n = Math.round(W * H / (d * d * 160f) * paper / 100f);
            for (int i = 0; i < n; i++) {   // little paper fibres
                float x = rnd.nextFloat() * W, y = rnd.nextFloat() * H, ln = (2 + rnd.nextFloat() * 7) * d, an = rnd.nextFloat() * 6.283f;
                pt.setColor(Pal.alpha(rnd.nextFloat() < 0.6f ? dark : light, Math.round(paper * (0.3f + rnd.nextFloat() * 0.5f))));
                pt.setStrokeWidth((0.4f + rnd.nextFloat() * 0.5f) * d);
                cv.drawLine(x, y, x + (float) Math.cos(an) * ln, y + (float) Math.sin(an) * ln, pt);
            }
            pt.setStyle(android.graphics.Paint.Style.FILL);
        }
        if (ht > 0) {   // halftone: a 45-degree dot screen that fades from one end, like riso shading
            float gap = Math.max(2.5f, Math.min(12, p.i("htgap10", 45) / 10f)) * d, k = 0.4f + 0.6f * ht / 100f;
            String fade = p.s("htfade", "top");
            pt.setColor(Pal.alpha(pal.ink, Math.round(40 + ht * 0.8f)));
            float cx = W / 2f, cy = H / 2f, R = (float) Math.hypot(W, H) / 2f + gap, cs = 0.7071f;
            for (float u = -R; u <= R; u += gap) for (float v = -R; v <= R; v += gap) {
                float x = cx + (u - v) * cs, y = cy + (u + v) * cs;
                if (x < -gap || y < -gap || x > W + gap || y > H + gap) continue;
                float t = Math.max(0, Math.min(1, y / H)), f = "bottom".equals(fade) ? t : "even".equals(fade) ? 0.55f : 1 - t;
                float rad = gap * 0.46f * f * k;
                if (rad > 0.35f) cv.drawCircle(x, y, rad, pt);
            }
        }
        if (grain > 0) {   // film grain: scattered dark and light specks
            int[] px = new int[W * H]; long sd = 0x9E3779B97F4A7C15L;
            float pd = grain / 100f * 0.22f, pl = grain / 100f * 0.12f; int amax = Math.round(30 + grain * 0.9f);
            int dk = dark & 0xFFFFFF;
            for (int i = 0; i < px.length; i++) {
                sd ^= sd << 13; sd ^= sd >>> 7; sd ^= sd << 17;
                float q = ((sd >>> 40) & 0xFFFF) / 65536f;
                if (q < pd) px[i] = ((int) (amax * (0.35f + 0.65f * q / pd)) << 24) | dk;
                else if (q < pd + pl) px[i] = ((int) (amax * 0.8f * (q - pd) / pl) << 24) | 0xFFFFFF;
            }
            android.graphics.Bitmap gb = android.graphics.Bitmap.createBitmap(px, W, H, android.graphics.Bitmap.Config.ARGB_8888);
            cv.drawBitmap(gb, 0, 0, null); gb.recycle();
        }
        return b;
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    /** A solid shadow along the right and bottom of a box, offset down and right (cartoon look, v3.15). */
    private static void toonShadow(android.graphics.Canvas cv, float x0, float y0, float x1, float y1, float sh) {
        android.graphics.Paint f = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG); f.setColor(0xFF000000);
        cv.drawRoundRect(x0 + sh, y1, x1 + sh, y1 + sh, sh / 2, sh / 2, f);
        cv.drawRoundRect(x1, y0 + sh, x1 + sh, y1 + sh, sh / 2, sh / 2, f);
    }
    /** How many times each line is gone over: 1 neat, 2 sketchy, 3 scratchy. */
    private static volatile int ROUGH = 1;
    private static void inkLine(android.graphics.Canvas cv, android.graphics.Paint pen, java.util.Random rnd, float x0, float y0, float x1, float y1, float amp, float w) {
        inkLine1(cv, pen, rnd, x0, y0, x1, y1, amp, w);
        float len = (float) Math.hypot(x1 - x0, y1 - y0); if (len < 1) return;
        float nx = -(y1 - y0) / len, ny = (x1 - x0) / len;
        for (int i = 1; i < ROUGH; i++) {
            float o0 = (rnd.nextFloat() - 0.5f) * (1.6f * w + amp), o1 = (rnd.nextFloat() - 0.5f) * (1.6f * w + amp);
            inkLine1(cv, pen, rnd, x0 + nx * o0, y0 + ny * o0, x1 + nx * o1, y1 + ny * o1, amp * 1.3f, w * 0.6f);
        }
    }
    /** One pen stroke from (x0,y0) to (x1,y1): slightly overshot ends, gentle wobble, uneven pressure. */
    private static void inkLine1(android.graphics.Canvas cv, android.graphics.Paint pen, java.util.Random rnd, float x0, float y0, float x1, float y1, float amp, float w) {
        float len = (float) Math.hypot(x1 - x0, y1 - y0); if (len < 1) return;
        float ux = (x1 - x0) / len, uy = (y1 - y0) / len, nx = -uy, ny = ux;
        float o0 = (rnd.nextFloat() - 0.3f) * amp * 2.5f, o1 = (rnd.nextFloat() - 0.3f) * amp * 2.5f;
        x0 -= ux * o0; y0 -= uy * o0; x1 += ux * o1; y1 += uy * o1; len += o0 + o1;
        int n = Math.max(2, Math.round(len / (14 * Math.max(1, w))));
        android.graphics.Path path = new android.graphics.Path(); path.moveTo(x0, y0);
        float px = x0, py = y0, drift = 0;
        for (int i = 1; i <= n; i++) {
            float t = (float) i / n;
            drift = drift * 0.6f + (rnd.nextFloat() - 0.5f) * amp * 1.6f;
            if (i == n) drift = 0;
            float qx = x0 + (x1 - x0) * t + nx * drift, qy = y0 + (y1 - y0) * t + ny * drift;
            path.quadTo(px, py, (px + qx) / 2, (py + qy) / 2); px = qx; py = qy;
        }
        path.lineTo(px, py);
        pen.setStrokeWidth(Math.max(0.5f, w * (0.85f + rnd.nextFloat() * 0.35f)));
        cv.drawPath(path, pen);
    }
    /** A box drawn by hand: four strokes, rounded at the corners when asked. */
    private static void inkBox(android.graphics.Canvas cv, android.graphics.Paint pen, java.util.Random rnd, float x0, float y0, float x1, float y1, float r, float amp, float w) {
        r = Math.min(r, Math.min(x1 - x0, y1 - y0) / 2);
        if (r < 1) {
            inkLine(cv, pen, rnd, x0, y0, x1, y0, amp, w); inkLine(cv, pen, rnd, x1, y0, x1, y1, amp, w);
            inkLine(cv, pen, rnd, x1, y1, x0, y1, amp, w); inkLine(cv, pen, rnd, x0, y1, x0, y0, amp, w);
            return;
        }
        inkLine(cv, pen, rnd, x0 + r, y0, x1 - r, y0, amp * 0.6f, w); inkLine(cv, pen, rnd, x1, y0 + r, x1, y1 - r, amp * 0.6f, w);
        inkLine(cv, pen, rnd, x1 - r, y1, x0 + r, y1, amp * 0.6f, w); inkLine(cv, pen, rnd, x0, y1 - r, x0, y0 + r, amp * 0.6f, w);
        pen.setStrokeWidth(w);
        cv.drawArc(x0, y0, x0 + 2 * r, y0 + 2 * r, 180, 90, false, pen); cv.drawArc(x1 - 2 * r, y0, x1, y0 + 2 * r, 270, 90, false, pen);
        cv.drawArc(x1 - 2 * r, y1 - 2 * r, x1, y1, 0, 90, false, pen); cv.drawArc(x0, y1 - 2 * r, x0 + 2 * r, y1, 90, 90, false, pen);
    }
    /** Short broken strokes, like pen hatching, at any angle (45 = down to the right). */
    private static void hatchLines(android.graphics.Canvas cv, android.graphics.Paint p, java.util.Random rnd, float x0, float y0, float x1, float y1, float gap, float angle) {
        float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, R = (float) Math.hypot(x1 - x0, y1 - y0) / 2 + gap;
        cv.save(); cv.rotate(angle, cx, cy);
        for (float yy = cy - R; yy < cy + R; yy += gap * (0.85f + rnd.nextFloat() * 0.3f)) {
            float t = cx - R;
            while (t < cx + R) {
                float ln = (4 + rnd.nextFloat() * 10) * gap / 4.6f;
                if (rnd.nextFloat() > 0.2f) cv.drawLine(t, yy, t + ln, yy, p);
                t += ln + gap * (0.3f + rnd.nextFloat());
            }
        }
        cv.restore();
    }

    /** Applies the chosen font family (if any) to a piece of widget text. */
    static CharSequence font(G g, String text) { return g == null ? text : font(g.font, text); }
    static CharSequence font(String fam, String text) {
        if (fam == null || fam.isEmpty()) return text;
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        sb.setSpan(new TypefaceSpan(fam), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }
    /** Narrower or wider event text. */
    static CharSequence width(G g, CharSequence t) {
        if (g.evSx == 1f) return t;
        SpannableStringBuilder sb = new SpannableStringBuilder(t);
        sb.setSpan(new android.text.style.ScaleXSpan(g.evSx), 0, sb.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
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
