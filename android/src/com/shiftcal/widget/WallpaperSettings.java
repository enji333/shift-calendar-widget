package com.shiftcal.widget;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** Wallpaper page (v3.8): turn the calendar into the phone's wallpaper and set the gaps around it. */
public class WallpaperSettings extends Activity {
    Prefs p; LinearLayout box; ImageView prev;
    final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
    final Runnable redraw = new Runnable() { public void run() { preview(); } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = new Prefs(this);
        ScrollView sv = new ScrollView(this); sv.setFitsSystemWindows(true);
        box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(40));
        sv.addView(box); setContentView(sv);

        TextView t = text("Calendar wallpaper", 24); t.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        hint("Your whole calendar as the phone's wallpaper, drawn exactly like the widget: same look, colours, fonts and events. It updates by itself when your calendar changes and as the day goes on. Change the look in the normal Shift Calendar settings (or from the computer).");

        button("Set as wallpaper", new View.OnClickListener() { public void onClick(View v) {
            try {
                Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(WallpaperSettings.this, CalWallpaper.class));
                startActivity(i);
            } catch (Exception e) {
                try { startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)); }
                catch (Exception e2) { toast("Open your phone's wallpaper settings and pick Shift Calendar from the live wallpapers."); }
            } } });
        hint("Pick \"Home screen\" (or \"Home and lock screens\") when the phone asks. Home screen icons and widgets sit on top of it, so a clear home page works best.");

        prev = new ImageView(this); prev.setAdjustViewBounds(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(380));
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL; lp.topMargin = dp(10); lp.bottomMargin = dp(6);
        box.addView(prev, lp);

        sub("Space around the calendar");
        slider("Gap at the top (status bar and clock)", "wp_top", 56, 300);
        slider("Gap at the bottom (dock and app bar)", "wp_bot", 110, 400);
        slider("Gap at the sides", "wp_side", 6, 60);

        sub("Background");
        check("Fill the rest of the screen with the calendar's background colour", "wp_fill", true);
        sub("…otherwise this colour (like #000000)");
        final EditText bg = new EditText(this); bg.setSingleLine(true); bg.setText(p.s("wp_bg", "#000000"));
        bg.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            public void afterTextChanged(Editable s) { p.put("wp_bg", s.toString().trim()); later(); } });
        box.addView(bg);

        sub("Taps");
        check("Tapping the wallpaper works like the widget (arrows, title, days)", "wp_tap", true);
        hint("Only where no app icon or widget is in the way. Some phones don't pass taps to the wallpaper, or block it from opening a day, in which case nothing happens.");

        sub("Lock screen");
        hint("Some phones (Samsung, for one) can't show a live wallpaper on the lock screen. These put the calendar there as a picture instead.");
        check("Keep the lock screen picture up to date (while the calendar is your home wallpaper)", "wp_lock", false);
        button("Put it on the lock screen now (as a picture)", new View.OnClickListener() { public void onClick(View v) { still(WallpaperManager.FLAG_LOCK); } });
        button("Put it on home and lock as a still picture (no live updates)", new View.OnClickListener() { public void onClick(View v) { still(WallpaperManager.FLAG_LOCK | WallpaperManager.FLAG_SYSTEM); } });

        Button done = button("Done", new View.OnClickListener() { public void onClick(View v) { finish(); } });
        ((LinearLayout.LayoutParams) done.getLayoutParams()).topMargin = dp(24);
        preview();
    }

    void still(final int which) {
        toast("Setting the picture…");
        try { CalWallpaper.setStill(this, which); toast("Done"); }
        catch (Throwable t) { Prefs.logCrash(this, t); toast("Couldn't set it: " + t.getMessage()); }
    }

    void later() { h.removeCallbacks(redraw); h.postDelayed(redraw, 200); }
    void preview() {
        try {
            Rect r = getSystemService(android.view.WindowManager.class).getMaximumWindowMetrics().getBounds();
            Bitmap full = CalWallpaper.render(this, r.width(), r.height(), new Rect(), null);
            int ph = dp(380), pw = Math.round(ph * full.getWidth() / (float) full.getHeight());
            prev.setImageBitmap(Bitmap.createScaledBitmap(full, pw, ph, true));
            prev.setBackgroundColor(0xFF888888); prev.setPadding(1, 1, 1, 1);
        } catch (Throwable t) { Prefs.logCrash(this, t); }
    }

    // ----- small helpers -----
    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    TextView text(String s, float sp) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); box.addView(t); return t; }
    void hint(String s) { TextView t = text(s, 14); t.setAlpha(0.75f); t.setPadding(0, dp(4), 0, dp(4)); }
    void sub(String s) { TextView t = text(s, 15); t.setTypeface(Typeface.DEFAULT_BOLD); t.setPadding(0, dp(14), 0, dp(2)); }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    Button button(String s, View.OnClickListener l) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setOnClickListener(l);
        box.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }
    void check(String label, final String key, boolean def) {
        CheckBox cb = new CheckBox(this); cb.setText(label); cb.setChecked(p.on(key, def));
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { public void onCheckedChanged(CompoundButton b, boolean v) { p.put(key, v ? 1 : 0); later(); } });
        box.addView(cb);
    }
    void slider(final String label, final String key, int def, int max) {
        final TextView t = text(label + ": " + p.i(key, def) + " dp", 14);
        SeekBar sb = new SeekBar(this); sb.setMax(max); sb.setProgress(p.i(key, def));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean user) { if (user) { t.setText(label + ": " + v + " dp"); p.put(key, v); later(); } }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { } });
        box.addView(sb);
    }
}
