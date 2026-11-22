package com.shiftcal.widget;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The window that opens when a day on the widget is tapped: a fixed header with the date and the all-day
 * events, then a 24-hour timeline that scrolls up and down. The days before and after show as slivers at
 * each side, events only, and a sideways swipe moves between days.
 */
public class DayActivity extends Activity {
    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final int HOUR_DP = 46, LABEL_DP = 46;
    private Prefs p; private Pal pal; private ZoneId zone;
    private LocalDate cur;
    private int keepY = -1;
    private ScrollView centre;
    // v3.19 day view style: fine-line pen drawing, print texture and fonts of its own
    private boolean pen, hatch; private int penInk; private float penW; private Typeface tfHead, tfEv, tfTxt; private int texGrain, texPaper, texHt;
    private void style() {
        String m = p.s("dv_pen", "follow");
        pen = "on".equals(m) || ("follow".equals(m) && p.on("ink", false));
        hatch = pen && p.on("dv_hatch", true);
        penInk = pal.ink != 0 ? pal.ink : pal.fg; penW = Math.max(0.4f, Math.min(2.5f, p.i("dv_penw10", 8) / 10f));
        String fh = p.s("dv_f_head", ""), fe = p.s("dv_f_ev", ""), ft = p.s("dv_f_txt", "");
        if (fh.isEmpty()) fh = p.s("tfont", "");
        tfHead = fh.isEmpty() ? null : Look.any(this, fh, !Look.isAsset(fh));
        tfEv = fe.isEmpty() ? null : Look.any(this, fe, !Look.isAsset(fe));
        tfTxt = ft.isEmpty() ? null : Look.any(this, ft, false);
        int g = p.i("dv_grain", -1), pa = p.i("dv_paper", -1), h = p.i("dv_halftone", -1);
        texGrain = g < 0 ? p.i("grain", 0) : g; texPaper = pa < 0 ? p.i("paper", 0) : pa; texHt = h < 0 ? p.i("halftone", 0) : h;
    }
    /** A rounded box, or with the pen style a hand-drawn one (outline = pen line round it). */
    private android.graphics.drawable.Drawable box(int color, float r, boolean outline, boolean hatched, long seed) {
        if (!pen) return round(color, r);
        return new PenBox(color, outline ? penInk : 0, penW, hatched && hatch, seed, getResources().getDisplayMetrics().density);
    }
    /** Grain, paper and halftone over a card, if any are set. Not clickable, so taps go through. */
    private void texture(FrameLayout card, int wPx, int hPx) {
        if (texGrain + texPaper + texHt == 0 || wPx <= 0 || hPx <= 0) return;
        try {
            float dn = getResources().getDisplayMetrics().density;
            android.graphics.Bitmap b = ShiftWidget.texLayer(this, p, pal, wPx / dn, hPx / dn, 6, texGrain, texPaper, texHt, 0);
            if (b == null) return;
            android.widget.ImageView iv = new android.widget.ImageView(this); iv.setImageBitmap(b); iv.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
            iv.setClickable(false); iv.setFocusable(false);
            card.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Throwable t) { Prefs.logCrash(this, t); }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.watch(this);
        getWindow().setWindowAnimations(0);
        overridePendingTransition(0, 0);
        show(getIntent());
    }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); show(i); }
    @Override protected void onRestart() { super.onRestart(); if (cur != null) { if (centre != null) keepY = centre.getScrollY(); show(null); } }
    @Override public void finish() { super.finish(); overridePendingTransition(0, 0); }

    private int dp(float v) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics())); }
    private GradientDrawable round(int color, float r) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(r)); return g; }
    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (tfTxt != null) t.setTypeface(bold ? Typeface.create(tfTxt, Typeface.BOLD) : tfTxt);
        else if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
    private static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    /** Sideways scroller holding three day timelines. Letting go past a fifth of the way changes day at once. */
    private final class Pager extends HorizontalScrollView {
        int step;
        Pager() { super(DayActivity.this); setHorizontalScrollBarEnabled(false); setOverScrollMode(View.OVER_SCROLL_NEVER); setFillViewport(true); }
        @Override public void fling(int v) { }
        @Override public boolean onTouchEvent(MotionEvent e) {
            boolean r = super.onTouchEvent(e);
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                int x = getScrollX();
                int dir = x < step * 0.8f ? -1 : x > step * 1.2f ? 1 : 0;
                // never rebuild the window from inside the touch itself: removing views mid-touch can crash Android
                final int d = dir;
                getHandler2().post(new Runnable() { public void run() { if (d != 0) go(d); else { try { scrollTo(step, 0); } catch (Exception ignored) { } } } });
            }
            return r;
        }
        @Override public boolean onInterceptTouchEvent(MotionEvent e) { try { return super.onInterceptTouchEvent(e); } catch (Exception x) { return false; } }
        @Override protected void onLayout(boolean c, int l, int t, int r, int b) {
            super.onLayout(c, l, t, r, b);
            try {   // keep the phone's own swipe-from-edge "back" gesture from closing the window mid-swipe (Android allows 200dp of edge)
                int h = b - t, band = Math.min(h, dp(200)), y0 = (h - band) / 2, w = r - l, e = dp(40);
                java.util.List<android.graphics.Rect> ex = new java.util.ArrayList<android.graphics.Rect>();
                ex.add(new android.graphics.Rect(0, y0, e, y0 + band)); ex.add(new android.graphics.Rect(w - e, y0, w, y0 + band));
                setSystemGestureExclusionRects(ex);
            } catch (Throwable ignored) { }
        }
    }

    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private android.os.Handler getHandler2() { return ui; }
    private void go(int dir) {
        if (isFinishing() || isDestroyed()) return;
        if (centre != null) keepY = centre.getScrollY();
        cur = cur.plusDays(dir);
        render();
    }
    private void render() {
        try { render0(); }
        catch (Throwable t) {
            Prefs.logCrash(this, t);
            TextView e = new TextView(this); e.setTextColor(0xFFFFFFFF); e.setBackgroundColor(0xFF101A16); e.setPadding(dp(16), dp(16), dp(16), dp(16));
            e.setText("Shift Calendar hit a problem showing this day:\n" + t + "\n\nA screenshot of this helps get it fixed.");
            try { setContentView(e); } catch (Throwable ignored) { }
        }
    }

    private void show(Intent in) {
        p = new Prefs(this); pal = Pal.of(p); zone = ZoneId.of(p.tz());
        ShiftWidget.H12 = "12".equals(p.s("clock", "24"));
        if (in != null) {
            try { cur = LocalDate.parse(in.getData().getLastPathSegment()); } catch (Exception e) { cur = LocalDate.now(zone); }
            keepY = -1;
        }
        if (cur == null) cur = LocalDate.now(zone);
        render();
    }

    private List<Events.Item> load(LocalDate day) {
        try {
            Map<LocalDate, List<Events.Item>> m = Events.load(this, p, day, day.plusDays(1), true);
            List<Events.Item> l = m.get(day);
            return l == null ? new ArrayList<Events.Item>() : l;
        } catch (Exception e) { return new ArrayList<Events.Item>(); }
    }

    private void render0() {
        getWindow().setBackgroundDrawable(new ColorDrawable(0));
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        final int screenW = dm.widthPixels, gap = dp(6), side = dp(30);
        final int panelW = screenW - 2 * side, height = (int) (dm.heightPixels * 0.88f);
        final int panel = Pal.mix(pal.bg, pal.future, 0.35f), soft = Pal.mix(panel, pal.fg, 0.10f);
        final LocalDate day = cur;
        style();
        List<Events.Item> items = load(day);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);

        // ----- header: date, buttons, all-day events (never scrolls) -----
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setBackground(box(panel, 6, true, false, day.toEpochDay() * 31));
        head.setPadding(dp(14), dp(12), dp(14), dp(10));
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        boolean today = day.equals(LocalDate.now(zone));
        TextView num = text(String.valueOf(day.getDayOfMonth()), 22, today ? (Pal.lum(pal.accent) > 0.5 ? 0xFF10140F : 0xFFFFFFFF) : pal.fg, true);
        num.setGravity(Gravity.CENTER); num.setBackground(box(today ? pal.accent : soft, 4, true, false, day.toEpochDay() * 31 + 1)); num.setMinWidth(dp(44)); num.setPadding(dp(6), dp(3), dp(6), dp(3));
        top.addView(num);
        LinearLayout names = new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL); names.setPadding(dp(10), 0, 0, 0);
        TextView dayName = text(day.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH), 18, pal.fg, true);
        if (tfHead != null) dayName.setTypeface(tfHead);
        // one line only: a wide title font shrinks to fit instead of wrapping onto the date (v3.10)
        dayName.setMaxLines(1); dayName.setSingleLine(true); dayName.setIncludeFontPadding(true);
        dayName.setAutoSizeTextTypeUniformWithConfiguration(10, 18, 1, TypedValue.COMPLEX_UNIT_SP);
        names.addView(dayName, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));
        TextView dateLine = text(day.getDayOfMonth() + " " + day.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + day.getYear(), 12, pal.muted, false);
        dateLine.setMaxLines(1); dateLine.setPadding(0, dp(1), 0, 0);
        names.addView(dateLine);
        top.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView add = text("+ Add", 14, pal.fg, true); add.setBackground(box(soft, 5, true, false, day.toEpochDay() * 31 + 2)); add.setPadding(dp(12), dp(7), dp(12), dp(7));
        add.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            long s = day.atStartOfDay(zone).plusHours(9).toInstant().toEpochMilli();
            start(new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, s).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, s + 3600000L)); } });
        top.addView(add);
        TextView cal = text("Calendar ›", 14, pal.accent, true); cal.setPadding(dp(12), dp(7), dp(2), dp(7));
        cal.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Uri u = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(String.valueOf(day.atStartOfDay(zone).plusHours(8).toInstant().toEpochMilli())).build();
            start(new Intent(Intent.ACTION_VIEW).setData(u)); } });
        top.addView(cal);
        head.addView(top);
        boolean anyAllDay = false;
        for (final Events.Item it : items) if (it.allDay) {
            anyAllDay = true;
            TextView chip = text(it.title.isEmpty() ? "(no title)" : Events.display(it.title, Events.keys(p), true), 14, Pal.lum(it.color) > 0.62 ? 0xFF10140F : 0xFFFFFFFF, true);
            chip.setMaxLines(1); chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setBackground(box(it.color, 3, true, false, it.begin / 60000 + 7));
            if (tfEv != null) chip.setTypeface(tfEv); chip.setPadding(dp(10), dp(5), dp(10), dp(5));
            chip.setOnClickListener(open(it));
            LinearLayout.LayoutParams clp = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); clp.topMargin = dp(6);
            head.addView(chip, clp);
        }
        if (!anyAllDay && items.isEmpty()) { TextView none = text("Nothing on.", 13, pal.muted, false); none.setPadding(0, dp(6), 0, 0); head.addView(none); }
        LinearLayout.LayoutParams hlp = lp(panelW, ViewGroup.LayoutParams.WRAP_CONTENT); hlp.leftMargin = side; hlp.bottomMargin = gap;
        outer.addView(head, hlp);

        if (texGrain + texPaper + texHt > 0) {   // texture over the header too: wrap it in a frame
            outer.removeView(head);
            FrameLayout hf = new FrameLayout(this); hf.addView(head, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            outer.addView(hf, hlp);
            final FrameLayout hf0 = hf;
            hf.addOnLayoutChangeListener(new View.OnLayoutChangeListener() { boolean done;
                public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) { if (done || r - l <= 0) return; done = true;
                    final int w = r - l, h = b - t; ui.post(new Runnable() { public void run() { texture(hf0, w, h); } }); } });
        }

        // ----- three timelines side by side -----
        final Pager pager = new Pager();
        pager.step = panelW + gap;
        LinearLayout strip = new LinearLayout(this);
        strip.setPadding(side, 0, side, 0);
        final ScrollView[] sv = new ScrollView[3];
        for (int k = -1; k <= 1; k++) {
            final int dir = k;
            LocalDate d = day.plusDays(k);
            List<Events.Item> its = k == 0 ? items : load(d);
            sv[k + 1] = new ScrollView(this);
            sv[k + 1].setVerticalScrollBarEnabled(false);
            sv[k + 1].addView(timeline(d, its, panelW, k == 0, panel));
            FrameLayout card = new FrameLayout(this);
            card.setBackground(box(panel, 6, true, false, d.toEpochDay() * 17));
            card.setClipToOutline(true);
            card.addView(sv[k + 1], new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (k != 0) {                       // a side day: tap its sliver to jump to it
                View tap = new View(this);
                tap.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { ui.post(new Runnable() { public void run() { go(dir); } }); } });
                card.addView(tap, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            texture(card, panelW, height);
            LinearLayout.LayoutParams plp = lp(panelW, ViewGroup.LayoutParams.MATCH_PARENT);
            if (k < 1) plp.rightMargin = gap;
            strip.addView(card, plp);
        }
        centre = sv[1];
        centre.setVerticalScrollBarEnabled(true);
        centre.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            public void onScrollChange(View v, int x, int y, int ox, int oy) { try { sv[0].scrollTo(0, y); sv[2].scrollTo(0, y); } catch (Exception ignored) { } } });
        pager.addView(strip, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams pglp = lp(screenW, 0); pglp.weight = 1;
        outer.addView(pager, pglp);

        FrameLayout root = new FrameLayout(this);
        root.addView(outer, new FrameLayout.LayoutParams(screenW, height));
        setContentView(root);

        // where to start: the same spot as before when swiping, otherwise just above the first event
        int first = 7 * 60;
        long ds = day.atStartOfDay(zone).toInstant().toEpochMilli();
        boolean found = false;
        for (Events.Item it : items) if (!it.allDay && !it.cont) { int m = (int) ((it.begin - ds) / 60000L); if (!found || m < first) first = m; found = true; }
        final int startY = keepY >= 0 ? keepY : Math.max(0, Math.round((first - 45) / 60f * dp(HOUR_DP)));
        pager.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                try {
                    pager.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    pager.scrollTo(pager.step, 0);
                    for (ScrollView s : sv) s.scrollTo(0, startY);
                } catch (Exception ignored) { }
            } });
    }

    /** One day's 24 hours, with each event placed and sized by its start and end. */
    private View timeline(LocalDate day, List<Events.Item> items, int panelW, boolean main, int panel) {
        int hourH = dp(HOUR_DP), labelW = main ? dp(LABEL_DP) : dp(4), pad = dp(4);
        FrameLayout tl = new FrameLayout(this);
        tl.setMinimumHeight(24 * hourH + dp(8));
        for (int h = 0; h <= 24; h++) {
            View line;
            FrameLayout.LayoutParams l;
            if (pen) {   // hour lines drawn with the pen: thin, a little wavy, not quite reaching the edge
                final long sd = day.toEpochDay() * 100 + h; final int inkA = Pal.alpha(penInk, 0x55); final float lw = penW * 0.7f;
                line = new View(this) { @Override protected void onDraw(Canvas c) {
                    android.graphics.Paint pp = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG); pp.setColor(inkA); pp.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                    float dn = getResources().getDisplayMetrics().density, y = getHeight() / 2f;
                    PenBox.pen(c, pp, new java.util.Random(sd), dn * 2, y, getWidth() - dn * (4 + (sd % 5)), y, lw * dn, dn); } };
                l = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
                l.topMargin = h * hourH + dp(2);
            } else {
                line = new View(this); line.setBackgroundColor(Pal.alpha(pal.fg, 0x1E));
                l = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f)));
                l.topMargin = h * hourH + dp(4);
            }
            l.leftMargin = main ? labelW - dp(4) : 0;
            tl.addView(line, l);
            if (main && h < 24) {
                String lab = ShiftWidget.H12 ? ((h % 12 == 0 ? 12 : h % 12) + (h < 12 ? "am" : "pm")) : String.format(Locale.ROOT, "%02d:00", h);
                TextView t = text(lab, 11, pal.muted, false); t.setGravity(Gravity.RIGHT);
                FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(labelW - dp(8), ViewGroup.LayoutParams.WRAP_CONTENT);
                tp.topMargin = h * hourH + dp(6); tl.addView(t, tp);
            }
        }
        long ds = day.atStartOfDay(zone).toInstant().toEpochMilli();
        List<Events.Item> timed = new ArrayList<Events.Item>();
        for (Events.Item it : items) if (!it.allDay) timed.add(it);
        int n = timed.size();
        float[] s = new float[n], e = new float[n]; int[] col = new int[n];
        List<Float> ends = new ArrayList<Float>();
        for (int i = 0; i < n; i++) {
            Events.Item it = timed.get(i);
            s[i] = it.cont ? 0 : Math.max(0, (it.begin - ds) / 60000f);
            e[i] = Math.min(1440f, (it.end - ds) / 60000f);
            if (e[i] < s[i] + 25) e[i] = Math.min(1440f, s[i] + 25);       // keep very short events readable
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        final float[] fs = s;
        java.util.Arrays.sort(order, new java.util.Comparator<Integer>() { public int compare(Integer a, Integer b) { return Float.compare(fs[a], fs[b]); } });
        for (int oi = 0; oi < n; oi++) {
            int i = order[oi]; int c = -1;
            for (int k = 0; k < ends.size(); k++) if (ends.get(k) <= s[i]) { c = k; break; }
            if (c < 0) { c = ends.size(); ends.add(0f); }
            ends.set(c, e[i]); col[i] = c;
        }
        int cols = Math.max(1, ends.size());
        int avail = panelW - labelW - pad;
        for (int i = 0; i < n; i++) {
            final Events.Item it = timed.get(i);
            int top = Math.round(s[i] / 60f * hourH) + dp(4), hgt = Math.max(dp(20), Math.round((e[i] - s[i]) / 60f * hourH) - dp(2));
            LinearLayout block = new LinearLayout(this);
            block.setBackground(box(Pal.mix(panel, it.color, it.cancelled ? 0.2f : 0.5f), 3, true, true, it.begin / 60000 + i));
            block.setClipToOutline(!pen);
            View bar = new View(this);
            if (pen) bar.setBackground(new PenBox(it.cancelled ? pal.muted : it.color, 0, penW, false, it.begin / 60000 + 3, getResources().getDisplayMetrics().density));
            else bar.setBackgroundColor(it.cancelled ? pal.muted : it.color);
            block.addView(bar, lp(dp(5), ViewGroup.LayoutParams.MATCH_PARENT));
            if (main) {
                LinearLayout txt = new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL); txt.setPadding(dp(8), dp(3), dp(6), dp(2));
                TextView t = text(it.title.isEmpty() ? "(no title)" : it.title, 14, it.cancelled ? pal.muted : pal.fg, true);
                if (tfEv != null) t.setTypeface(tfEv);
                t.setMaxLines(hgt > dp(64) ? 2 : 1); t.setEllipsize(TextUtils.TruncateAt.END);
                if (it.cancelled) t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                txt.addView(t);
                if (hgt >= dp(38)) {
                    String sub = range(it) + "  ·  " + dur(it);
                    if (it.loc != null && !it.loc.isEmpty()) sub += "  ·  " + it.loc;
                    TextView st = text(sub, 12, Pal.mix(pal.fg, panel, 0.25f), false);
                    st.setMaxLines(hgt > dp(80) ? 3 : 1); st.setEllipsize(TextUtils.TruncateAt.END);
                    txt.addView(st);
                }
                block.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
                block.setOnClickListener(open(it));
            }
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(avail / cols - dp(2), hgt);
            bl.topMargin = top; bl.leftMargin = labelW + col[i] * (avail / cols);
            tl.addView(block, bl);
        }
        if (main && day.equals(LocalDate.now(zone))) {
            ZonedDateTime now = ZonedDateTime.now(zone);
            View nowLine = new View(this); nowLine.setBackgroundColor(pal.accent);
            FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
            nl.topMargin = Math.round((now.getHour() * 60 + now.getMinute()) / 60f * hourH) + dp(4); nl.leftMargin = labelW - dp(4);
            tl.addView(nowLine, nl);
        }
        return tl;
    }

    private View.OnClickListener open(final Events.Item it) {
        return new View.OnClickListener() { public void onClick(View v) {
            start(new Intent(Intent.ACTION_VIEW).setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, it.id))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it.begin).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it.end)); } };
    }
    private void start(Intent i) { try { startActivity(i); } catch (Exception ignored) { } }
    private String range(Events.Item it) {
        ZonedDateTime a = Instant.ofEpochMilli(it.begin).atZone(zone), b = Instant.ofEpochMilli(it.end).atZone(zone);
        String s = ShiftWidget.H12 ? T.format(a).toLowerCase(Locale.ROOT) : ShiftWidget.hm(it.begin, zone);
        String e = ShiftWidget.H12 ? T.format(b).toLowerCase(Locale.ROOT) : ShiftWidget.hm(it.end, zone);
        if (!a.toLocalDate().equals(b.toLocalDate())) return ShiftWidget.shortDate(a.toLocalDate()) + " " + s + " - " + ShiftWidget.shortDate(b.toLocalDate()) + " " + e;
        return s + " - " + e;
    }
    private static String dur(Events.Item it) {
        double h = Math.round((it.end - it.begin) / 36000.0) / 100.0;
        return (h == Math.floor(h) ? String.valueOf((long) h) : String.valueOf(h)) + " h";
    }
}
