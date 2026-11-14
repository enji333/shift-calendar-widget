package com.shiftcal.widget;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends Activity {
    private Prefs p;
    private LinearLayout box;
    private ScrollView sv;
    private EditText fInc, fAnd, fExc;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = new Prefs(this);
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[] { Manifest.permission.READ_CALENDAR }, 1);
        build();
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); if (sv != null) out.putInt("y", sv.getScrollY()); }
    @Override public void onRequestPermissionsResult(int rc, String[] perms, int[] res) {
        build(); ShiftWidget.refreshAll(this);
    }
    @Override protected void onPause() { super.onPause(); ShiftWidget.refreshAll(this); }
    @Override protected void onRestart() { super.onRestart(); p = new Prefs(this); build(); }

    private int dp(float v) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics())); }

    /** Copies the chosen colour set into the code colours, in list order, so each one can then be changed by hand. */
    private void fillFromSet() {
        int[] set = Pal.events(p);
        StringBuilder sb = new StringBuilder(); int i = 0;
        for (String line : p.rules().split("\n")) {
            int eq = line.lastIndexOf('='); if (eq < 0) continue;
            sb.append(line.substring(0, eq).trim()).append('=').append(Pal.str(set[i++ % set.length])).append('\n');
        }
        p.put("rules", sb.toString());
    }
    private void build() {
        if ("palette".equals(p.colour()) && p.i("mig26", 0) == 0) fillFromSet();   // earlier versions took these colours from the set on the fly
        p.put("mig26", 1);
        rules.clear();
        final int keepY = sv == null ? 0 : sv.getScrollY();
        sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(40));
        box.setFocusableInTouchMode(true);
        box.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        sv.addView(box);
        setContentView(sv);
        if (keepY > 0) { final ScrollView keep = sv; keep.post(new Runnable() { public void run() { keep.scrollTo(0, keepY); } }); }

        TextView h = text("Shift Calendar", 26); h.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        box.addView(h);
        String ver = "?";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        hint("Version " + ver);
        boolean ok = checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
        if (!ok) {
            hint("The widget needs to read your phone's calendar. Nothing is sent anywhere.");
            Button g = new Button(this); g.setText("Allow calendar access");
            g.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                requestPermissions(new String[] { Manifest.permission.READ_CALENDAR }, 1); } });
            box.addView(g);
        } else {
            hint("Changes apply to the widget straight away. To add it: long-press an empty home screen, tap Widgets, find Shift Calendar, and drag it onto its own page. Long-press it and drag the edges to fill the page.");
        }

        Button rc = new Button(this); rc.setText("Receive settings from computer"); rc.setAllCaps(false);
        rc.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            startActivity(new android.content.Intent(SettingsActivity.this, ReceiveActivity.class)); } });
        box.addView(rc);
        hint("Open the Shift Calendar control panel on your computer, then tap this to pair and press Send there.");
        outback();

        final String crash = p.s("crash", "");
        if (!crash.isEmpty()) {
            section("Last problem");
            hint("The app hit an error. A screenshot of this helps get it fixed.");
            TextView ct = text(crash.length() > 900 ? crash.substring(0, 900) : crash, 11); ct.setTextIsSelectable(true); ct.setTypeface(Typeface.MONOSPACE); box.addView(ct);
            Button clr = new Button(this); clr.setText("Clear this");
            clr.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { p.put("crash", ""); build(); } });
            box.addView(clr);
        }

        section("View");
        sub("What the widget shows");
        radio("view", p.view(-1), new String[][] { { "rolling", "Rolling: a few past days, then the weeks ahead from today" }, { "month", "Full month, month by month (arrows move a month at a time)" } });
        hint("Tapping the dates at the top of the widget also switches between the two. In the rolling view the arrows jump to the next or previous whole month, and the date button brings you back to today.");
        sub("Rolling view: weeks shown from today");
        radioInt("rows", p.rows(), new String[][] { { "2", "2" }, { "3", "3" }, { "4", "4" }, { "5", "5" }, { "6", "6" } });
        sub("Later weeks get shorter, so the days nearest today have the most room");
        radio("taper", p.taper(), new String[][] { { "some", "Yes" }, { "lots", "Yes, a lot" }, { "off", "No, all the same height" } });
        sub("Rolling view: past days in the small strip at the top");
        radioInt("past", p.past(), new String[][] { { "0", "None" }, { "3", "3" }, { "7", "7" } });
        sub("Rolling view: how it moves");
        radio("roll", p.s("roll", "daily"), new String[][] { { "daily", "One day at a time (today is always the first big box)" }, { "aligned", "A week at a time (rows are whole weeks)" } });
        sub("First day of the week (month view and whole-week rows)");
        radio("wk", p.s("wk", "sun"), new String[][] { { "sun", "Sunday" }, { "mon", "Monday" } });

        section("Timed events");
        radio("pos", p.s("pos", "time"), new String[][] { { "time", "Placed and sized by time of day (when the day box is tall enough)" }, { "list", "Simple list in time order" } });
        sub("Clock");
        radio("clock", p.s("clock", "24"), new String[][] { { "24", "24-hour (17:00)" }, { "12", "12-hour (5pm)" } });
        sub("Top of each day box is");
        radioInt("t0", p.dayStart(), new String[][] { { "-1", "Fit to that week's events" }, { "0", "Midnight" }, { "4", "4 am" }, { "6", "6 am" } });
        sub("Colour of the abbreviation at the start of a title (ACT, EPA and so on)");
        radio("code", p.code(), new String[][] { { "accent", "Palette highlight colour" }, { "event", "The event's own colour" }, { "text", "Same as the rest of the text" }, { "custom", "My own colour:" } });
        {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(32), 0, 0, dp(4));
            final int cur = Pal.hex(p.s("c_code", ""), Pal.of(p).accent);
            row.addView(swatch(cur));
            TextView lab = text("Tap to choose", 14); lab.setPadding(dp(12), 0, 0, 0); row.addView(lab);
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                pick(cur, new Picked() { public void set(int c) { p.put("c_code", Pal.str(c)); p.put("code", "custom"); build(); ShiftWidget.refreshAll(SettingsActivity.this); } }); } });
            box.addView(row);
        }

        section("Overnight events");
        hint("Events that cross midnight show on the day they start, in time order with the rest of that day.");
        radio("over", p.over(), new String[][] { { "start", "Start day only" }, { "every", "Start day, plus a \"→ until\" line on the next day" } });

        section("All-day markers (venue events and the like)");
        hint("These sit in their own band in each day box, so they never move around with your timed events.");
        radio("allday", p.allday(), new String[][] { { "bottom", "Band at the bottom" }, { "top", "Band at the top" } });
        sub("Several markers on one day");
        radio("mk", p.s("mk", "side"), new String[][] { { "side", "Side by side" }, { "stack", "Stacked, full width (easier to read)" } });

        section("Layout");
        radio("density", p.density(), new String[][] { { "detailed", "Detailed: start time, wraps to 3 lines" }, { "compact", "Compact: title only, 1 line" } });
        sub("Text size");
        radio("size", p.s("size", "m"), new String[][] { { "s", "Small" }, { "m", "Medium" }, { "l", "Large" } });
        sub("Events per day box");
        radioInt("max", p.max(), new String[][] { { "0", "As many as fit" }, { "3", "3" }, { "4", "4" }, { "6", "6" } });

        section("Filter");
        LinearLayout presets = new LinearLayout(this); presets.setOrientation(LinearLayout.HORIZONTAL);
        preset(presets, "Everything", "", "", "");
        preset(presets, "Shifts only", "ACT, EPA, VW, GPH", "", "");
        preset(presets, "RAC Arena shifts", "ACT, EPA, VW, GPH", "RAC", "");
        box.addView(presets);
        sub("Show only events containing (comma separated, blank = all)");
        fInc = edit("inc", p.inc(), "e.g. ACT, EPA, VW, GPH", false);
        sub("…and also containing");
        fAnd = edit("and", p.and(), "e.g. RAC", false);
        sub("Hide events containing");
        fExc = edit("exc", p.exc(), "e.g. Halloween", false);
        hint("Words match the event title and location, ignoring case.");
        sub("Cancelled events");
        radio("cancel", p.cancel(), new String[][] { { "show", "Show greyed and struck through" }, { "hide", "Hide" } });

        section("Calendars");
        calendars(ok);

        section("Event colours");
        rebuildRadio("colour", p.colour(), new CharSequence[][] { { "event", "Use each event's own calendar colour" }, { "rules", "Use my code colours below (other events keep their calendar colour)" }, { "palette", "Use my code colours below, and a matched colour set for all other events" } });
        hint("With a matched colour set, your codes below are filled from the set in the order listed, and other events are recoloured to suit. The sets use hues spaced well apart, and opposite the tile colour, so events stand out from each other and from the background.");
        sub("Colour set");
        hint("Picking a set fills your code colours below from it. After that, tap any square to change that one colour.");
        RadioGroup sg = new RadioGroup(this);
        String curSet = p.s("eset", "auto");
        for (int k = 0; k < Pal.SETS.length; k++) {
            RadioButton rb = new RadioButton(this); rb.setId(View.generateViewId()); rb.setText(Pal.SETS[k][1]); rb.setTag(Pal.SETS[k][0]);
            sg.addView(rb); if (Pal.SETS[k][0].equals(curSet)) rb.setChecked(true);
            // preview strip: real coloured blocks, so it shows the same on every phone
            int[] cols = Pal.events("auto".equals(Pal.SETS[k][0]) ? p.pal() : Pal.SETS[k][0]);
            LinearLayout strip = new LinearLayout(this);
            for (int c : cols) { View blk = new View(this); blk.setBackgroundColor(c); strip.addView(blk, new LinearLayout.LayoutParams(0, dp(16), 1)); }
            RadioGroup.LayoutParams slp = new RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16));
            slp.setMargins(dp(32), 0, 0, dp(8));
            sg.addView(strip, slp);
        }
        sg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup gr, int id) {
                View rb = gr.findViewById(id); if (rb == null || rb.getTag() == null) return;
                p.put("eset", (String) rb.getTag()); p.put("colour", "palette"); fillFromSet();
                ShiftWidget.refreshAll(SettingsActivity.this);
                box.post(new Runnable() { public void run() { build(); } });
            }
        });
        box.addView(sg);
        hint("Give any code its own colour, such as RAC, VEN, ACT, GPH or EPA. An event whose title starts with the code gets that colour. You can also put the code at the very end of the title instead, such as \"Fern Society picnic GPH\": the event gets the colour and the code is hidden on the widget so it takes no room. This is the way to recolour all-day events, and it works whichever option is chosen above. Type the end code in capitals. If none starts with one, a matching word anywhere in the title or location is used. Add as many as you like." + " Tap a square to change its colour.");
        rulesBox = new LinearLayout(this); rulesBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(rulesBox);
        buildRules();
        Button addRule = new Button(this); addRule.setText("Add a prefix colour"); addRule.setAllCaps(false);
        addRule.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            int[] set = Pal.events(p);
            rules.add(new String[] { "", "palette".equals(p.colour()) ? Pal.str(set[rules.size() % set.length]) : SWATCHES[rules.size() % SWATCHES.length] }); saveRules(); buildRules(); } });
        box.addView(addRule);

        section("Colour palette");
        rebuildRadio("pal", p.pal(), Pal.PRESETS);
        hint("\"My own colours\" uses the six colours below. Tap one to change it.");
        for (final String[] cc : Pal.CUSTOM) {
            final Pal base = Pal.preset("bush");
            int def = "c_bg".equals(cc[0]) ? base.bg : "c_past".equals(cc[0]) ? base.past : "c_future".equals(cc[0]) ? base.future
                : "c_today".equals(cc[0]) ? base.today : "c_accent".equals(cc[0]) ? base.accent : base.fg;
            final int cur = Pal.hex(p.s(cc[0], ""), def);
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, dp(4), 0, dp(4));
            final View sw = swatch(cur);
            row.addView(sw);
            TextView lab = text(cc[1], 15); lab.setPadding(dp(12), 0, 0, 0); row.addView(lab);
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                pick(Pal.hex(p.s(cc[0], ""), cur), new Picked() { public void set(int c) {
                    p.put(cc[0], Pal.str(c)); p.put("pal", "custom"); build(); ShiftWidget.refreshAll(SettingsActivity.this); } }); } });
            box.addView(row);
        }

        section("Opacity of each colour");
        hint("Slide left to let more of your wallpaper show through that colour.");
        slider("Widget background", "opacity", p.opacity());
        slider("Upcoming days", "op_future", p.op("op_future"));
        slider("Today", "op_today", p.op("op_today"));
        slider("Past days", "op_past", p.op("op_past"));
        hint("Events are drawn on top of the day boxes, so the sliders above do not fade them. These two set the events themselves.");
        slider("Event blocks (how solid the colour behind each event is)", "op_ev", p.i("op_ev", 28));
        slider("Events on past days (100% = as strong as upcoming days)", "op_pastev", p.i("op_pastev", 60));

        Button done = new Button(this); done.setText("Done");
        done.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(24); box.addView(done, lp);
    }

    private void calendars(boolean ok) {
        if (!ok) { hint("Allow calendar access to choose calendars."); return; }
        Set<String> chosen = p.cals();
        final Set<String> all = new HashSet<String>();
        String[] proj = { CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.VISIBLE };
        Cursor c = getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, proj, null, null,
            CalendarContract.Calendars.ACCOUNT_NAME + ", " + CalendarContract.Calendars.CALENDAR_DISPLAY_NAME);
        if (c == null) return;
        final Set<String> sel = new HashSet<String>();
        try {
            while (c.moveToNext()) {
                final String id = String.valueOf(c.getLong(0));
                all.add(id);
                String name = c.getString(1); if (name == null) name = "Calendar";
                if (name.startsWith("webcal://") && name.contains("humanforce")) name = "Humanforce roster";
                String acct = c.getString(2);
                boolean on = chosen == null ? c.getInt(4) == 1 : chosen.contains(id);
                if (on) sel.add(id);
                CheckBox cb = new CheckBox(this);
                cb.setText(name + (acct != null && !acct.equals(name) ? "\n" + acct : ""));
                cb.setChecked(on);
                cb.setButtonTintList(ColorStateList.valueOf(c.getInt(3) | 0xFF000000));
                cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        if (v) sel.add(id); else sel.remove(id);
                        p.setCals(sel);
                    }
                });
                box.addView(cb);
            }
        } finally { c.close(); }
        hint("Starts with the calendars your phone shows. Roster calendars may repeat shifts you've entered yourself; exact repeats are only shown once.");
    }

    static final String[] SWATCHES = { "#D50000", "#E67C73", "#F4511E", "#F6BF26", "#E8C77A", "#33B679", "#0B8043", "#A9C47E",
        "#039BE5", "#3F51B5", "#7986CB", "#A9C7E3", "#8E24AA", "#B39DDB", "#F06292", "#AD1457",
        "#795548", "#A1887F", "#DDD1BB", "#616161", "#9E9E9E", "#FFFFFF", "#00897B", "#C0CA33" };
    interface Picked { void set(int c); }
    private LinearLayout rulesBox;
    private final List<String[]> rules = new ArrayList<String[]>();

    private View swatch(int color) {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(8)); g.setStroke(dp(1), 0x66888888);
        v.setBackground(g); v.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(34)));
        return v;
    }
    private LinearLayout sufBox;
    private final List<String[]> sufs = new ArrayList<String[]>();
    private void saveSufs() {
        StringBuilder sb = new StringBuilder();
        for (String[] r : sufs) sb.append(r[0].trim()).append('=').append(r[1]).append('\n');
        p.put("srules", sb.toString());
    }
    private void buildSufs() {
        sufBox.removeAllViews();
        for (final String[] r : sufs) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            View sw = swatch(Pal.hex(r[1], 0xFF888888));
            sw.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                pick(Pal.hex(r[1], 0xFF888888), new Picked() { public void set(int c) { r[1] = Pal.str(c); saveSufs(); buildSufs(); ShiftWidget.refreshAll(SettingsActivity.this); } }); } });
            row.addView(sw);
            EditText e = new EditText(this); e.setText(r[0]); e.setHint("Suffix, e.g. hmt");
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            e.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                public void onTextChanged(CharSequence s, int a, int b, int c) { }
                public void afterTextChanged(Editable s) { r[0] = s.toString(); saveSufs(); }
            });
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); elp.leftMargin = dp(10);
            row.addView(e, elp);
            Button x = new Button(this); x.setText("Remove"); x.setAllCaps(false); x.setTextSize(12);
            x.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { sufs.remove(r); saveSufs(); buildSufs(); ShiftWidget.refreshAll(SettingsActivity.this); } });
            row.addView(x);
            sufBox.addView(row);
        }
    }
    private void saveRules() {
        StringBuilder sb = new StringBuilder();
        for (String[] r : rules) sb.append(r[0].trim()).append('=').append(r[1]).append('\n');
        p.put("rules", sb.toString());
    }
    private void buildRules() {
        if (rules.isEmpty() && rulesBox.getChildCount() == 0) {
            for (String line : p.rules().split("\n")) {
                int eq = line.lastIndexOf('='); if (eq < 0) continue;
                rules.add(new String[] { line.substring(0, eq).trim(), line.substring(eq + 1).trim() });
            }
        }
        rulesBox.removeAllViews();
        for (final String[] r : rules) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            View sw = swatch(Pal.hex(r[1], 0xFF888888));
            sw.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                pick(Pal.hex(r[1], 0xFF888888), new Picked() { public void set(int c) { r[1] = Pal.str(c); saveRules(); buildRules(); ShiftWidget.refreshAll(SettingsActivity.this); } }); } });
            row.addView(sw);
            EditText e = new EditText(this); e.setText(r[0]); e.setHint("Prefix, e.g. RAC");
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            e.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                public void onTextChanged(CharSequence s, int a, int b, int c) { }
                public void afterTextChanged(Editable s) { r[0] = s.toString(); saveRules(); }
            });
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); elp.leftMargin = dp(10);
            row.addView(e, elp);
            Button x = new Button(this); x.setText("Remove"); x.setAllCaps(false); x.setTextSize(12);
            x.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { rules.remove(r); saveRules(); buildRules(); ShiftWidget.refreshAll(SettingsActivity.this); } });
            row.addView(x);
            rulesBox.addView(row);
        }
    }
    /** Colour chooser: a grid of swatches plus a box for any hex code. */
    private void pick(int current, final Picked cb) {
        final AlertDialog[] dlg = new AlertDialog[1];
        LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(20), dp(12), dp(20), 0);
        LinearLayout line = null;
        TextView pt = text("From your palette", 13); pt.setPadding(0, 0, 0, dp(2)); v.addView(pt);
        Pal pp = Pal.of(p);
        int[] ev = Pal.events(p.pal());
        int[] fromPal = new int[ev.length + 4];
        System.arraycopy(ev, 0, fromPal, 0, ev.length);
        fromPal[ev.length] = pp.accent; fromPal[ev.length + 1] = pp.fg; fromPal[ev.length + 2] = pp.muted; fromPal[ev.length + 3] = pp.today;
        for (int i = 0; i < fromPal.length; i++) {
            if (i % 6 == 0) { line = new LinearLayout(this); v.addView(line); }
            final int col = fromPal[i];
            View sw = swatch(col);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1); lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            sw.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { cb.set(col); dlg[0].dismiss(); } });
            line.addView(sw, lp);
        }
        TextView cy = text("Cyberpunk", 13); cy.setPadding(0, dp(10), 0, dp(2)); v.addView(cy);
        for (int i = 0; i < Pal.CYBER.length; i++) {
            if (i % 10 == 0) { line = new LinearLayout(this); v.addView(line); }
            final int col = Pal.hex(Pal.CYBER[i], 0);
            View sw = swatch(col);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1); lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            sw.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { cb.set(col); dlg[0].dismiss(); } });
            line.addView(sw, lp);
        }
        TextView at = text("Any colour", 13); at.setPadding(0, dp(10), 0, dp(2)); v.addView(at);
        for (int i = 0; i < SWATCHES.length; i++) {
            if (i % 6 == 0) { line = new LinearLayout(this); v.addView(line); }
            final int col = Pal.hex(SWATCHES[i], 0);
            View sw = swatch(col);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1); lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            sw.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { cb.set(col); dlg[0].dismiss(); } });
            line.addView(sw, lp);
        }
        TextView t = text("Or type a colour code", 13); t.setPadding(0, dp(12), 0, 0); v.addView(t);
        final EditText hex = new EditText(this); hex.setText(Pal.str(current)); hex.setHint("#RRGGBB");
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        v.addView(hex);
        ScrollView wrap = new ScrollView(this); wrap.addView(v);
        dlg[0] = new AlertDialog.Builder(this).setTitle("Pick a colour").setView(wrap)
            .setPositiveButton("Use code", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                int c = Pal.hex(hex.getText().toString(), 0); if (c != 0) cb.set(c); } })
            .setNegativeButton("Cancel", null).show();
    }

    private void preset(LinearLayout row, String label, final String inc, final String and, final String exc) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(13);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            fInc.setText(inc); fAnd.setText(and); fExc.setText(exc); } });
        row.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }

    private TextView text(String s, float sp) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); return t; }
    private void slider(final String label, final String key, int cur) {
        final TextView t = text(label + ": " + cur + "%", 14); t.setPadding(0, dp(10), 0, dp(2)); box.addView(t);
        SeekBar sb = new SeekBar(this); sb.setMax(100); sb.setProgress(cur);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean user) { if (user) { p.put(key, v); t.setText(label + ": " + v + "%"); } }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { ShiftWidget.refreshAll(SettingsActivity.this); }
        });
        box.addView(sb);
    }
    private void section(String s) {
        TextView t = text(s.toUpperCase(), 13); t.setTypeface(Typeface.DEFAULT_BOLD); t.setLetterSpacing(0.08f);
        t.setAlpha(0.7f); t.setPadding(0, dp(22), 0, dp(4)); box.addView(t);
    }
    private void sub(String s) { TextView t = text(s, 14); t.setPadding(0, dp(10), 0, dp(2)); box.addView(t); }
    private void hint(String s) { TextView t = text(s, 13); t.setAlpha(0.7f); t.setPadding(0, dp(4), 0, dp(4)); box.addView(t); }

    private void radio(final String key, String cur, String[][] opts) {
        RadioGroup g = new RadioGroup(this);
        for (int i = 0; i < opts.length; i++) {
            RadioButton r = new RadioButton(this); r.setId(View.generateViewId()); r.setText(opts[i][1]); r.setTag(opts[i][0]);
            g.addView(r); if (opts[i][0].equals(cur)) r.setChecked(true);
        }
        g.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup gr, int id) { p.put(key, (String) gr.findViewById(id).getTag()); if ("view".equals(key)) p.resetOffsets(); if ("size".equals(key)) p.sp.edit().remove("tdp10").apply(); ShiftWidget.refreshAll(SettingsActivity.this); }
        });
        box.addView(g);
    }
    /** A radio list that redraws the page afterwards, for choices that change what other rows show. */
    private void rebuildRadio(final String key, String cur, CharSequence[][] opts) {
        RadioGroup g = new RadioGroup(this);
        for (int i = 0; i < opts.length; i++) {
            RadioButton r = new RadioButton(this); r.setId(View.generateViewId()); r.setText(opts[i][1]); r.setTag(opts[i][0].toString());
            g.addView(r); if (opts[i][0].toString().equals(cur)) r.setChecked(true);
        }
        g.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup gr, int id) {
                p.put(key, (String) gr.findViewById(id).getTag());
                if ("eset".equals(key)) { p.put("colour", "palette"); fillFromSet(); }
                if ("pal".equals(key) && "palette".equals(p.colour()) && "auto".equals(p.s("eset", "auto"))) fillFromSet();
                ShiftWidget.refreshAll(SettingsActivity.this);
                box.post(new Runnable() { public void run() { build(); } });
            }
        });
        box.addView(g);
    }
    private void radioInt(final String key, int cur, String[][] opts) {
        RadioGroup g = new RadioGroup(this);
        boolean wide = false; for (String[] o : opts) if (o[1].length() > 8) wide = true;
        g.setOrientation(wide ? RadioGroup.VERTICAL : RadioGroup.HORIZONTAL);
        for (int i = 0; i < opts.length; i++) {
            RadioButton r = new RadioButton(this); r.setId(View.generateViewId()); r.setText(opts[i][1]); r.setTag(opts[i][0]);
            r.setPadding(0, 0, dp(12), 0);
            g.addView(r); if (Integer.parseInt(opts[i][0]) == cur) r.setChecked(true);
        }
        g.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup gr, int id) { p.put(key, Integer.parseInt((String) gr.findViewById(id).getTag())); ShiftWidget.refreshAll(SettingsActivity.this); }
        });
        box.addView(g);
    }
    private EditText edit(final String key, String cur, String hintText, boolean multi) {
        EditText e = new EditText(this); e.setText(cur); e.setHint(hintText);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | (multi ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        if (multi) { e.setMinLines(4); e.setGravity(Gravity.TOP); e.setTypeface(Typeface.MONOSPACE); }
        e.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { p.put(key, s.toString()); }
        });
        box.addView(e);
        return e;
    }

    // ------------------------------------------------------------ WA outback look
    private void outback() {
        section("WA outback look");
        hint("One tap sets a whole look from the WA bush and outback palette: colours, a landscape picture, a retro title, tinted weekends and grid lines. Your codes, filters, calendars and text sizes stay as they are.");
        LinearLayout line = null;
        for (int k = 0; k < Look.THEMES.length; k++) {
            final String[] t = Look.THEMES[k];
            if (k % 3 == 0) { line = new LinearLayout(this); box.addView(line); }
            LinearLayout cardBox = new LinearLayout(this); cardBox.setOrientation(LinearLayout.VERTICAL);
            cardBox.setPadding(dp(3), dp(3), dp(3), dp(6));
            ImageView pic = new ImageView(this); pic.setImageResource(Look.sceneRes(t[3])); pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cardBox.addView(pic, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
            Pal tp = Pal.preset(t[2]);
            LinearLayout strip = new LinearLayout(this);
            for (int col : new int[] { tp.bg, tp.future, tp.wkend, tp.accent, tp.fg }) { View blk = new View(this); blk.setBackgroundColor(col); strip.addView(blk, new LinearLayout.LayoutParams(0, dp(8), 1)); }
            cardBox.addView(strip);
            Button b = new Button(this); b.setText(t[1]); b.setAllCaps(false); b.setTextSize(13);
            Typeface tf = Look.font(this, t[4]); if (tf != null) b.setTypeface(tf);
            View.OnClickListener go = new View.OnClickListener() { public void onClick(View v) {
                Look.apply(SettingsActivity.this, p, t[0]); ShiftWidget.refreshAll(SettingsActivity.this); build(); } };
            b.setOnClickListener(go); pic.setOnClickListener(go);
            cardBox.addView(b);
            line.addView(cardBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        if (line != null) for (int k = Look.THEMES.length % 3; k > 0 && k < 3; k++) line.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        if (Look.canUndo(p)) {
            Button u = new Button(this); u.setText("Undo last theme"); u.setAllCaps(false);
            u.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Look.undo(p); ShiftWidget.refreshAll(SettingsActivity.this); build(); } });
            box.addView(u);
        }
        sub("Picture at the top");
        String sc = p.s("scene", "");
        if (Look.sceneRes(sc) != 0) {
            ImageView pv = new ImageView(this); pv.setImageResource(Look.sceneRes(sc)); pv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            box.addView(pv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70)));
        }
        rebuildRadio("scene", sc, Look.SCENES);
        sub("Where the picture goes");
        radio("scene_pos", p.s("scene_pos", "top"), new String[][] { { "top", "Above the calendar" }, { "bottom", "Below the calendar" } });
        sub("Picture height");
        radioInt("scene_h", p.i("scene_h", 72), new String[][] { { "48", "Short" }, { "72", "Medium" }, { "100", "Tall" }, { "140", "Very tall" } });
        sub("Title font");
        hint("Retro display fonts for the month name at the top. The phone's own font is used everywhere else.");
        RadioGroup fg = new RadioGroup(this);
        String curF = p.s("tfont", "");
        for (String[] f : Look.FONTS) {
            RadioButton rb = new RadioButton(this); rb.setId(View.generateViewId()); rb.setText(f[0].isEmpty() ? f[1] : "NOVEMBER 2026  ·  " + f[1]); rb.setTag(f[0]);
            Typeface tf = Look.font(this, f[0]); if (tf != null) { rb.setTypeface(tf); rb.setTextSize(15); }
            fg.addView(rb); if (f[0].equals(curF)) rb.setChecked(true);
        }
        fg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() { public void onCheckedChanged(RadioGroup gr, int id) {
            p.put("tfont", (String) gr.findViewById(id).getTag()); ShiftWidget.refreshAll(SettingsActivity.this); } });
        box.addView(fg);
        sub("Header height");
        radioInt("hh", p.i("hh", 30), new String[][] { { "30", "Normal" }, { "40", "Big" }, { "52", "Bigger" } });
        sub("Calendar sheet details");
        toggle("tyear", "Show the year after the month name, in the highlight colour", false);
        toggle("wktint", "Tint Saturday and Sunday boxes", false);
        toggle("satred", "Saturday in the Sunday colour too", false);
        toggle("grid", "Grid lines between the day boxes", false);
        toggle("numpos", "Date numbers in the top-left corner", false);
    }
    private void toggle(final String key, String label, boolean def) {
        CheckBox cb = new CheckBox(this); cb.setText(label);
        cb.setChecked("numpos".equals(key) ? "left".equals(p.s("numpos", "center")) : p.on(key, def));
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { public void onCheckedChanged(CompoundButton b, boolean v) {
            if ("numpos".equals(key)) p.put(key, v ? "left" : "center"); else p.put(key, v ? 1 : 0);
            ShiftWidget.refreshAll(SettingsActivity.this); } });
        box.addView(cb);
    }
}
