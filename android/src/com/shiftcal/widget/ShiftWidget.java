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
        // hand-drawn linework and shapes (v3.2)
        boolean ink, inkBoxes, stip; String hatch = "none"; float inkW = 0.9f, wobble = 4, rTile = 2, rEv = 0, rMk = 0;
        // finer hand-drawn controls and fonts per kind of text (v3.3)
        int rough = 1; float hang = 45, hgap = 4.6f, stipD = 1; String hwhere = "past", stipOn = "wkend"; boolean inkEv;
        String fontMk = "", fontNum = "", fontDow = ""; float evSx = 1, numLs = 0, dowLs = 0; boolean numBold = true, numFake, dowBold = true;
        Typeface numTf, dowTf;
        Look.Fx fx, dowFx;   // lettering effects for the title and weekday names (v3.11)
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
        if (Look.isAsset(fn) || g.numLs != 0 || !g.numBold) { g.numTf = Look.any(c, fn.isEmpty() ? base : fn, g.numBold && !Look.isAsset(fn)); g.numFake = g.numBold && Look.isAsset(fn); }
        else g.fontNum = fn.isEmpty() ? g.font : fn;
        String fd = p.s("f_dow", "").trim(); g.dowLs = p.i("dowls", 0) / 100f; g.dowBold = p.on("dowbold", true);
        g.fx = Look.fx(p, g.pal); g.dowFx = g.fx != null && p.on("teffw", false) ? g.fx : null;
        if (Look.isAsset(fd) || g.dowLs != 0 || !g.dowBold || g.dowFx != null) g.dowTf = Look.any(c, fd.isEmpty() ? base : fd, g.dowBold && !Look.isAsset(fd));
        else g.fontDow = fd.isEmpty() ? g.font : fd;
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
        g.ink = p.on("ink", false); g.inkBoxes = "boxes".equals(p.s("inkbox", "grid")); g.stip = p.on("stip", false); g.hatch = p.s("hatch", "none");
        g.inkW = Math.max(0.3f, Math.min(4, p.i("inkw10", 9) / 10f)); g.wobble = Math.max(0, Math.min(10, p.i("wobble", 4)));
        g.rough = Math.max(1, Math.min(3, p.i("rough", 1))); g.hang = p.i("hang", 45); g.hgap = Math.max(2, Math.min(14, p.i("hgap10", 46) / 10f));
        g.hwhere = p.s("hwhere", "past"); g.stipOn = p.s("stipon", "wkend"); g.stipD = Math.max(0.25f, Math.min(4, p.i("stipd", 100) / 100f));
        g.inkEv = g.ink && p.on("inkev", false);
        g.rTile = Math.max(0, p.i("r_tile", 2)); g.rEv = Math.max(0, p.i("r_ev", 0)); g.rMk = Math.max(0, p.i("r_mk", 0));
        if (g.ink) g.grid = false;
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
        if (!p.on("h_dow", true)) rv.setViewVisibility(R.id.dow_box, View.GONE);
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
        try { map = Events.load(c, p, from, to); }
        catch (Exception e) { map = Collections.emptyMap(); }

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
        if (g.dowTf != null && p.on("h_dow", true)) {
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
        float unitH = (hDp - 5 - (p.on("h_head", true) ? hh : 0) - (p.on("h_dow", true) ? 14 : 0) - sceneH - earnH - gridPad) / units;
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
        for (Row r : rows) {
            RemoteViews row = new RemoteViews(c.getPackageName(), ROW_LAYOUTS[Math.max(0, Math.min(12, Math.round(r.w * 20) - 8))]);
            float rowH = unitH * r.w;
            window(g, r, map);
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
        if (isToday) cell.setTextColor(R.id.num, pal.accent);
        else cell.setTextColor(R.id.num, dim ? Pal.alpha(numCol, Math.max(0x80, g.dimA)) : numCol);
        if (g.numTf != null) {
            // the date drawn as a picture, so it can use any font and letter spacing
            try {
                int col = isToday ? pal.accent : dim ? Pal.alpha(numCol, Math.max(0x80, g.dimA)) : numCol;
                float nsz = g.numDp > 0 ? (small ? g.numDp * 0.74f : g.numDp) : (small ? 11.5f : 15.5f);
                cell.setImageViewBitmap(R.id.num_img, Look.label(c, g.numTf, clashes ? num + " •" : num, col, nsz, g.head(small), Math.max(8, cellW - 6), g.numLs, g.numFake));
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
            m.setTextViewText(R.id.txt, width(g, font(g.fontMk, mt)));
            if (g.inkEv) { m.setViewVisibility(R.id.mkln, View.VISIBLE); m.setInt(R.id.mkln, "setColorFilter", g.pal.ink); if (dim) m.setInt(R.id.mkln, "setImageAlpha", g.dimA); }
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
        chip.setInt(R.id.bar, "setColorFilter", it.color | 0xFF000000);
        if (dim) chip.setInt(R.id.bar, "setImageAlpha", g.dimA);
        chip.setInt(R.id.chipbg, "setColorFilter", it.color | 0xFF000000);
        chip.setInt(R.id.chipbg, "setImageAlpha", Math.round(g.evPct * 2.55f * (dim ? g.dimA / 255f : 1f)));
        if (g.rEv > 0) { int rr = Look.rr(g.rEv); chip.setImageViewResource(R.id.chipbg, rr); chip.setImageViewResource(R.id.bar, rr); }
        chip.setTextColor(R.id.txt, dim ? Pal.alpha(fg, g.dimA) : fg);
        chip.setTextViewText(R.id.txt, width(g, text));
        if (g.inkEv) { chip.setViewVisibility(R.id.chipln, View.VISIBLE); chip.setInt(R.id.chipln, "setColorFilter", g.pal.ink); if (dim) chip.setInt(R.id.chipln, "setImageAlpha", g.dimA); }
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
                inkBox(cv, pen, rnd, cx - side / 2f, y0, cx + side / 2f, y0 + bh, Math.min(g.rTile, 6) * d, amp, g.inkW * d);
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
                if (g.inkBoxes) inkBox(cv, pen, rnd, x0 + 1.5f * d, y0 + 1.5f * d, x1 - 1.5f * d, y1 - 1.5f * d, g.rTile * d, amp, g.inkW * d);
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
        int grain = clamp(p.i("grain", 0), 0, 100), paper = clamp(p.i("paper", 0), 0, 100), ht = clamp(p.i("halftone", 0), 0, 100), wash = clamp(p.i("wash", 0), 0, 60);
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
