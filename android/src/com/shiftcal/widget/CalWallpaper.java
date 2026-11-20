package com.shiftcal.widget;

import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.service.wallpaper.WallpaperService;
import android.util.DisplayMetrics;
import android.util.SizeF;
import android.view.ContextThemeWrapper;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import java.util.ArrayList;

/**
 * Live wallpaper (v3.8): the same calendar the widget draws, filling the home screen.
 * It builds the widget's RemoteViews, inflates them into ordinary views and draws those onto
 * the wallpaper, so every look, colour, font and setting matches the widget exactly.
 * Wallpaper-only settings use the "wp_" keys (see WallpaperSettings).
 */
public class CalWallpaper extends WallpaperService {
    /** The wallpaper's own "widget id": keeps its own month arrows (off_7777) apart from any widget. */
    static final int WID = 7777;

    @Override public Engine onCreateEngine() { return new Cal(); }

    final class Cal extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        final Handler h = new Handler(Looper.getMainLooper());
        int w, hgt; boolean visible, dirty = true;
        View root; Rect area = new Rect();
        long lockAt; int lockHash;
        final Runnable draw = new Runnable() { public void run() { if (visible) paint(); else dirty = true; } };
        final Runnable tick = new Runnable() { public void run() { later(0); h.postDelayed(this, 600000L - System.currentTimeMillis() % 600000L + 2000); } };
        final ContentObserver cal = new ContentObserver(h) { @Override public void onChange(boolean self) { later(1500); } };
        final BroadcastReceiver time = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { later(300); } };
        SharedPreferences sp;

        void later(long ms) { h.removeCallbacks(draw); h.postDelayed(draw, ms); }

        @Override public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setTouchEventsEnabled(false);
            sp = getSharedPreferences("cfg", MODE_PRIVATE);
            sp.registerOnSharedPreferenceChangeListener(this);
            try { getContentResolver().registerContentObserver(CalendarContract.CONTENT_URI, true, cal); } catch (Exception ignored) { }
            IntentFilter f = new IntentFilter(Intent.ACTION_DATE_CHANGED);
            f.addAction(Intent.ACTION_TIMEZONE_CHANGED); f.addAction(Intent.ACTION_TIME_CHANGED); f.addAction(Intent.ACTION_SCREEN_ON);
            try { registerReceiver(time, f); } catch (Exception ignored) { }
            h.postDelayed(tick, 600000L - System.currentTimeMillis() % 600000L + 2000);
        }
        @Override public void onDestroy() {
            h.removeCallbacksAndMessages(null);
            try { sp.unregisterOnSharedPreferenceChangeListener(this); } catch (Exception ignored) { }
            try { getContentResolver().unregisterContentObserver(cal); } catch (Exception ignored) { }
            try { unregisterReceiver(time); } catch (Exception ignored) { }
            super.onDestroy();
        }
        @Override public void onSharedPreferenceChanged(SharedPreferences s, String key) {
            if (key != null && (key.startsWith("crash") || key.startsWith("hist"))) return;
            later(250);
        }
        @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            w = width; hgt = height; later(0);
        }
        @Override public void onVisibilityChanged(boolean v) {
            visible = v;
            if (v && dirty) later(0);
        }
        @Override public void onSurfaceDestroyed(SurfaceHolder holder) { visible = false; super.onSurfaceDestroyed(holder); }

        /** A tap on the home screen (where no icon is): press whatever the calendar shows there. */
        @Override public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean result) {
            if (WallpaperManager.COMMAND_TAP.equals(action) && root != null && new Prefs(CalWallpaper.this).on("wp_tap", true)) {
                try {
                    View v = hit(root, x - area.left, y - area.top);
                    if (v != null) v.performClick();
                } catch (Throwable t) { Prefs.logCrash(CalWallpaper.this, t); }
            }
            return null;
        }

        void paint() {
            dirty = false;
            if (w <= 0 || hgt <= 0) return;
            SurfaceHolder sh = getSurfaceHolder();
            Canvas cv = null;
            try {
                Bitmap pic = render(CalWallpaper.this, w, hgt, area, this);
                cv = sh.lockCanvas();
                if (cv == null) return;
                cv.drawBitmap(pic, 0, 0, null);
                maybeLock(pic);
            } catch (Throwable t) {
                Prefs.logCrash(CalWallpaper.this, t);
                if (cv == null) try { cv = sh.lockCanvas(); } catch (Throwable ignored) { }
                if (cv != null) cv.drawColor(0xFF101A16);
            } finally {
                if (cv != null) try { sh.unlockCanvasAndPost(cv); } catch (Throwable ignored) { }
            }
        }

        /** "Keep the lock screen picture up to date": copy the drawing to the lock screen now and then. */
        void maybeLock(Bitmap pic) {
            Prefs p = new Prefs(CalWallpaper.this);
            if (!p.on("wp_lock", false) || isPreview()) return;
            int hash = sp.getAll().hashCode() * 31 + java.time.LocalDate.now().hashCode();
            long now = System.currentTimeMillis();
            if (hash == lockHash && now - lockAt < 30 * 60000L) return;
            lockHash = hash; lockAt = now;
            final Bitmap copy = pic.copy(Bitmap.Config.ARGB_8888, false);
            new Thread(new Runnable() { public void run() {
                try { WallpaperManager.getInstance(CalWallpaper.this).setBitmap(copy, null, true, WallpaperManager.FLAG_LOCK); }
                catch (Throwable t) { Prefs.logCrash(CalWallpaper.this, t); }
            } }).start();
        }
    }

    /** Deepest view under (x, y) that does something when pressed. */
    static View hit(View v, int x, int y) {
        if (v.getVisibility() != View.VISIBLE || x < 0 || y < 0 || x >= v.getWidth() || y >= v.getHeight()) return null;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View ch = g.getChildAt(i);
                View got = hit(ch, x - ch.getLeft() + ch.getScrollX(), y - ch.getTop() + ch.getScrollY());
                if (got != null) return got;
            }
        }
        return v.hasOnClickListeners() ? v : null;
    }

    /**
     * Draws the calendar at the screen's size. The gaps at the top (status bar, clock), bottom (dock)
     * and sides are the wp_top / wp_bot / wp_side settings in dp; the rest of the screen is filled with
     * the calendar's background colour (wp_fill on) or a colour of its own (wp_bg).
     */
    static Bitmap render(Context c, int w, int h, Rect area, Cal eng) {
        Prefs p = new Prefs(c);
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        float d = dm.density;
        int top = Math.round(Math.max(0, Math.min(400, p.i("wp_top", 56))) * d);
        int bot = Math.round(Math.max(0, Math.min(600, p.i("wp_bot", 110))) * d);
        int side = Math.round(Math.max(0, Math.min(80, p.i("wp_side", 6))) * d);
        if (top + bot > h * 0.7f) { top = Math.round(h * 0.08f); bot = Math.round(h * 0.12f); }
        area.set(side, top, w - side, h - bot);
        float wDp = area.width() / d, hDp = area.height() / d;

        Bundle opts = new Bundle();
        ArrayList<SizeF> sizes = new ArrayList<SizeF>(); sizes.add(new SizeF(wDp, hDp));
        opts.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, sizes);
        RemoteViews rv = ShiftWidget.safeBuild(c, WID, opts);

        Context themed = new ContextThemeWrapper(c, android.R.style.Theme_DeviceDefault);
        FrameLayout host = new FrameLayout(themed);
        View v = rv.apply(themed, host);
        host.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        host.measure(View.MeasureSpec.makeMeasureSpec(area.width(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(area.height(), View.MeasureSpec.EXACTLY));
        host.layout(0, 0, area.width(), area.height());
        if (eng != null) eng.root = host;

        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Pal pal = Pal.of(p);
        int fill = p.on("wp_fill", true) ? (pal.bg | 0xFF000000) : Pal.hex(p.s("wp_bg", ""), Color.BLACK);
        cv.drawColor(fill);
        cv.save();
        cv.translate(area.left, area.top);
        host.draw(cv);
        cv.restore();
        return b;
    }

    /** Puts the calendar on the home and/or lock screen as a still picture (for phones whose lock screen can't show live wallpapers). */
    static void setStill(Context c, int which) throws Exception {
        DisplayMetrics dm = new DisplayMetrics();
        android.view.WindowManager wm = c.getSystemService(android.view.WindowManager.class);
        Rect r = wm.getMaximumWindowMetrics().getBounds();
        Bitmap b = render(c, r.width(), r.height(), new Rect(), null);
        WallpaperManager.getInstance(c).setBitmap(b, null, true, which);
    }
}
