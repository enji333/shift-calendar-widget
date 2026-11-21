package com.shiftcal.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The month drawn as a page of a hand-inked comic (v3.16). Each week is a tier of panels: busy days get wide panels,
 * quiet days thin ones, and a run of empty days shares one panel. Shifts sit in caption boxes, other plans in
 * hand-drawn pen shapes. Past weeks fold into one "Previously..." tier with their pay. Fine wobbly pen lines,
 * light hatching and stipple, flat colour laid on by hand (soft or vibrant), over a poster-sky fade.
 * The layout is worked out once and shared by the drawing and the invisible tap areas, so taps land on the right day.
 */
final class ComicPage {
    static final class Panel { float x, w; List<LocalDate> days = new ArrayList<LocalDate>(); boolean pastWeek; LocalDate weekStart; int weekDays; }
    static final class Tier { float y, h; boolean past, narration; List<Panel> panels = new ArrayList<Panel>(); }
    static final float GAP = 5, SIDE = 3;

    static boolean on(Prefs p) { return "comic".equals(p.s("page", "")); }
    static boolean vivid(Prefs p) { return "vivid".equals(p.s("page_col", "soft")); }

    /** starts[i], counts[i], weights[i]: the rows of days the widget shows. */
    static List<Tier> layout(Prefs p, LocalDate[] starts, int[] counts, float[] weights, Map<LocalDate, List<Events.Item>> map, LocalDate today, float wDp, float hDp) {
        List<Tier> out = new ArrayList<Tier>();
        float y = 0, narr = Math.min(30, hDp * 0.07f);
        Tier n = new Tier(); n.narration = true; n.y = 0; n.h = narr; out.add(n); y += narr + GAP;
        List<Integer> past = new ArrayList<Integer>(), fut = new ArrayList<Integer>();
        for (int i = 0; i < starts.length; i++) (starts[i].plusDays(counts[i] - 1).isBefore(today) ? past : fut).add(i);
        float pastH = past.isEmpty() ? 0 : Math.min(40, hDp * 0.09f);
        if (!past.isEmpty()) {
            Tier t = new Tier(); t.past = true; t.y = y; t.h = pastH;
            float pw = (wDp - 2 * SIDE - GAP * (past.size() - 1)) / past.size(), x = SIDE;
            for (int i : past) {
                Panel pn = new Panel(); pn.x = x; pn.w = pw; pn.pastWeek = true; pn.weekStart = starts[i]; pn.weekDays = counts[i];
                for (int k = 0; k < counts[i]; k++) pn.days.add(starts[i].plusDays(k));
                t.panels.add(pn); x += pw + GAP;
            }
            out.add(t); y += pastH + GAP;
        }
        float units = 0; for (int i : fut) units += weights[i];
        float room = hDp - y - GAP * Math.max(0, fut.size() - 1);
        for (int i : fut) {
            Tier t = new Tier(); t.y = y; t.h = Math.max(30, room * weights[i] / Math.max(0.01f, units));
            List<List<LocalDate>> groups = new ArrayList<List<LocalDate>>();
            for (int k = 0; k < counts[i]; k++) {
                LocalDate d = starts[i].plusDays(k);
                List<LocalDate> last = groups.isEmpty() ? null : groups.get(groups.size() - 1);
                if (last != null && busy(map, d) == 0 && busy(map, last.get(0)) == 0 && !d.equals(today) && !last.get(0).equals(today)) last.add(d);
                else { List<LocalDate> g = new ArrayList<LocalDate>(); g.add(d); groups.add(g); }
            }
            float tot = 0; float[] wt = new float[groups.size()];
            for (int g = 0; g < wt.length; g++) { LocalDate d0 = groups.get(g).get(0); int b = busy(map, d0);
                wt[g] = b > 0 ? (1.1f + 0.35f * Math.min(4, b)) * (d0.equals(today) ? 1.35f : 1f) : 0.55f + 0.2f * (groups.get(g).size() - 1); tot += wt[g]; }
            float avail = wDp - 2 * SIDE - GAP * (wt.length - 1), x = SIDE;
            for (int g = 0; g < wt.length; g++) { Panel pn = new Panel(); pn.x = x; pn.w = avail * wt[g] / tot; pn.days = groups.get(g); t.panels.add(pn); x += pn.w + GAP; }
            out.add(t); y += t.h + GAP;
        }
        return out;
    }
    /** Events worth showing on a day (not the carried-over half of an overnight shift). */
    static int busy(Map<LocalDate, List<Events.Item>> map, LocalDate d) { return shown(map, d).size(); }
    static List<Events.Item> shown(Map<LocalDate, List<Events.Item>> map, LocalDate d) {
        List<Events.Item> l = map.get(d), out = new ArrayList<Events.Item>();
        if (l != null) for (Events.Item it : l) if (!it.cont) out.add(it);
        return out;
    }

    // ------------------------------------------------------------------ drawing
    private final Canvas cv; private final Random rnd; private final Typeface tf; private final boolean viv; private final Pal pal;
    private static final int K = 0xFF15120F, PAPER = 0xFFFFFDF6;
    private ComicPage(Canvas cv, long seed, Typeface tf, boolean viv, Pal pal) { this.cv = cv; this.rnd = new Random(seed); this.tf = tf; this.viv = viv; this.pal = pal; }
    private float r() { return rnd.nextFloat(); }

    int colour(String kind, boolean any) {
        if ("EPA".equals(kind)) return viv ? 0xFFE5573D : 0xFFEE9C86;
        if ("VW".equals(kind)) return viv ? 0xFF2FA3B3 : 0xFF93C9CC;
        if ("ACT".equals(kind)) return viv ? 0xFFE9799A : 0xFFEFB9C6;
        if ("GPH".equals(kind)) return viv ? 0xFFEDB232 : 0xFFF0D28E;
        return any ? (viv ? 0xFF8DC27E : 0xFFBFD8BC) : (viv ? 0xFFB9A3DA : 0xFFDDD3E6);
    }
    int yellow() { return viv ? 0xFFF7D33E : 0xFFF4E3A6; }

    /** The whole-widget background: a poster-sky fade (or plain paper), rounded like the widget. Drawn small; it is smooth anyway. */
    static Bitmap fade(Prefs p, Pal pal, float wDp, float hDp, float rBg) {
        float d = 0.5f; int W = Math.max(2, Math.round(wDp * d)), H = Math.max(2, Math.round(hDp * d));
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(b);
        float rr = rBg >= 25 ? Math.min(W, H) / 2f : Math.min(rBg * d, Math.min(W, H) / 2f);
        Path clip = new Path(); clip.addRoundRect(0, 0, W, H, rr, rr, Path.Direction.CW); c.clipPath(clip);
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (p.on("page_fade", true)) {
            boolean v = vivid(p);
            int[] cols = v ? new int[] { 0xFFF6A77A, 0xFFF4E07A, 0xFFBFE6A8, 0xFF7FD3D8 } : new int[] { 0xFFF8D2BC, 0xFFF8EDBE, 0xFFDDEFD0, 0xFFC3E8EA };
            pt.setShader(new LinearGradient(0, 0, 0, H, cols, new float[] { 0, 0.35f, 0.7f, 1 }, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, pt); pt.setShader(null);
            pt.setColor(Pal.alpha(0xFFF7F3EA, 46)); c.drawRect(0, 0, W, H, pt);
        } else { pt.setColor(pal.bg); c.drawRect(0, 0, W, H, pt); }
        return b;
    }

    static Bitmap draw(Context c, Prefs p, Pal pal, List<Tier> tiers, Map<LocalDate, List<Events.Item>> map, LocalDate today, java.time.ZoneId zone, float wDp, float hDp, long seed) {
        float d = Math.min(2f, c.getResources().getDisplayMetrics().density);
        int W = Math.max(1, Math.round(wDp * d)), H = Math.max(1, Math.round(hDp * d));
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b); cv.scale(d, d);
        Typeface tf = Look.font(c, "architectsdaughter"); if (tf == null) tf = Typeface.create("casual", Typeface.NORMAL);
        ComicPage pg = new ComicPage(cv, seed, tf, vivid(p), pal);
        for (Tier t : tiers) {
            if (t.narration) { pg.narration(c, p, map, today, wDp, t.h); continue; }
            for (int i = 0; i < t.panels.size(); i++) pg.panel(c, p, t, t.panels.get(i), i, map, today, zone);
        }
        return b;
    }

    private void narration(Context c, Prefs p, Map<LocalDate, List<Events.Item>> map, LocalDate today, float wDp, float h) {
        String line;
        try {
            Earn.Week w = Earn.week(c, p);
            int left = 0; LocalDate mon = today.minusDays(today.getDayOfWeek().getValue() - 1);
            for (LocalDate dd = today; dd.isBefore(mon.plusDays(7)); dd = dd.plusDays(1))
                for (Events.Item it : shown(map, dd)) if (!it.cancelled && !it.allDay && Earn.kind(it.title) != null) left++;
            line = (left == 0 ? "No more shifts this week." : left == 1 ? "One shift still to go this week." : (left < 10 ? WORDS[left] : String.valueOf(left)) + " shifts still to go this week.")
                + " " + ShiftWidget.money(w.done) + " earned so far, about " + ShiftWidget.money(w.est) + " by Sunday at " + ShiftWidget.money(w.rate) + " an hour...";
        } catch (Throwable t) { line = "Meanwhile, the month goes on..."; }
        Paint pm = text(9.5f);
        List<String> l = wrap(line.toUpperCase(Locale.ROOT), wDp - 10, pm);
        if (l.size() > 2) { pm.setTextSize(pm.getTextSize() * 0.86f); l = wrap(line.toUpperCase(Locale.ROOT), wDp - 10, pm); }
        float sz = pm.getTextSize();
        for (int i = 0; i < Math.min(3, l.size()); i++) cv.drawText(l.get(i), 5, sz + 2 + i * sz * 1.25f, pm);
    }
    static final String[] WORDS = { "No", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine" };

    private void panel(Context c, Prefs p, Tier t, Panel pn, int idx, Map<LocalDate, List<Events.Item>> map, LocalDate today, java.time.ZoneId zone) {
        float x0 = pn.x, y0 = t.y, w = pn.w, h = t.h;
        if (pn.pastWeek) {
            wash(x0, y0, w, h, viv ? 0xFFE6D3B0 : 0xFFEDE3D1);
            stipple(x0, y0, w, h, (int) (w * h / 60), 0x40151210);
            pen4(x0, y0, w, h, 0.75f);
            String range = pn.days.get(0).getDayOfMonth() + " " + mon(pn.days.get(0)) + " – " + pn.days.get(pn.days.size() - 1).getDayOfMonth() + " " + mon(pn.days.get(pn.days.size() - 1));
            String pay = "";
            try { Earn.Week wk = Earn.week(c, p, pn.weekStart); if (wk.shifts > 0) pay = ShiftWidget.money(wk.est) + " · " + wk.shifts + (wk.shifts == 1 ? " shift" : " shifts"); } catch (Throwable ignored) { }
            float ch = caption(x0 + 3, y0 + 3, Math.min(w - 6, idx == 0 ? 66 : w - 6), idx == 0 ? "Previously..." : range, 7.2f, idx == 0 ? yellow() : PAPER);
            Paint pm = text(7.6f); pm.setTextAlign(Paint.Align.CENTER);
            float ty = y0 + 3 + ch + (h - 6 - ch) / 2f + 3;
            if (idx == 0) { cv.drawText(fit(range, w - 6, pm), x0 + w / 2, ty - (pay.isEmpty() ? 0 : 4), pm); if (!pay.isEmpty()) cv.drawText(fit(pay.toUpperCase(Locale.ROOT), w - 6, pm), x0 + w / 2, ty + 6, pm); }
            else if (!pay.isEmpty()) cv.drawText(fit(pay.toUpperCase(Locale.ROOT), w - 6, pm), x0 + w / 2, ty, pm);
            return;
        }
        LocalDate d0 = pn.days.get(0);
        List<Events.Item> ev = shown(map, d0);
        String kind = null; for (Events.Item it : ev) if (!it.cancelled && Earn.kind(it.title) != null) { kind = Earn.kind(it.title); break; }
        boolean isToday = d0.equals(today), past = d0.isBefore(today);
        int hol = ShiftWidget.holiday(p, ev);   // v3.17: a holiday panel takes the holiday colour
        wash(x0, y0, w, h, hol != 0 ? hol : colour(kind, !ev.isEmpty()));
        if (ev.isEmpty()) { hatch(x0, y0, w, h, 2.6f, 1.6f, -0.6f); stipple(x0, y0, w, h, (int) (w * h / 40), 0x4D151210); }
        else { hatch(x0, y0, w, h, 3.2f, 1f, 0.7f + idx * 0.3f); stipple(x0, y0 + h * 0.6f, w, h * 0.4f, (int) (w * h / 90), 0x80FFFDF6); }
        if (past) { Paint dim = new Paint(); dim.setColor(0x33F7F3EA); cv.drawRect(x0, y0, x0 + w, y0 + h, dim); }
        pen4(x0, y0, w, h, isToday ? 1.3f : 0.8f);
        if (isToday) pen4(x0 + 0.8f, y0 + 0.8f, w - 1.6f, h - 1.6f, 0.5f);
        String dl = pn.days.size() > 1 ? dow(d0) + "–" + dow(pn.days.get(pn.days.size() - 1)) + " " + d0.getDayOfMonth() + "–" + pn.days.get(pn.days.size() - 1).getDayOfMonth()
            : dow(d0) + " " + d0.getDayOfMonth();
        if (d0.getDayOfMonth() == 1 && pn.days.size() == 1) dl += " " + mon(d0);
        float top;
        if (w > 34) top = 3 + caption(x0 + 3, y0 + 3, Math.min(w - 6, 64), (isToday ? "Today, " : "") + dl, 7.8f, isToday ? yellow() : PAPER) + 5;
        else {
            Paint pm = text(9f); cv.save(); cv.translate(x0 + w / 2f + 3.5f, y0 + 5); cv.rotate(90); cv.drawText(fit(dl, h - 10, pm), 0, 0, pm); cv.restore(); top = 6;
        }
        if (ev.isEmpty()) {
            if (w > 34 && pn.days.size() > 1 && h > 50) { Paint pm = text(8.5f); pm.setTextAlign(Paint.Align.CENTER); cv.drawText("NOTHING", x0 + w / 2, y0 + h / 2, pm); cv.drawText("ON.", x0 + w / 2, y0 + h / 2 + 10, pm); }
            return;
        }
        if (w <= 34) return;
        float ey = y0 + top, bottom = y0 + h - 4; int shownN = 0;
        for (int i = 0; i < ev.size(); i++) {
            Events.Item it = ev.get(i);
            String tx = it.title == null ? "" : it.title;
            if (it.cancelled) tx = "Cancelled: " + tx.replaceFirst("(?i)^\\s*cancel+ed\\s*[-–—:]*\\s*", "");
            if (!it.allDay) tx = ShiftWidget.hm(it.begin, zone) + " · " + tx;
            float bw = Math.min(w - 8, 98), size = 8.6f;
            boolean shift = Earn.kind(it.title) != null;
            float need = estimate(tx, bw, size, !shift);
            if (ey + need > bottom) {
                int rest = ev.size() - shownN;
                Paint pm = text(8f); pm.setTextAlign(Paint.Align.CENTER); cv.drawText("+" + rest + " MORE", x0 + w / 2, Math.min(bottom, ey + 9), pm);
                break;
            }
            if (shift) { float bx = i % 2 == 1 ? x0 + w - bw - 4 : x0 + 4; ey += caption(bx, ey, bw, tx, size, it.cancelled ? 0xFFDCD9D2 : PAPER) + 6; }
            else ey += shape(x0 + w / 2 + (i % 2 == 1 ? 3 : -2), ey, bw, tx, size) + 5;
            shownN++;
        }
    }
    private float estimate(String t, float w, float size, boolean shape) {
        Paint pm = text(size); int n = wrap(t.toUpperCase(Locale.ROOT), w - (shape ? 16 : 8), pm).size();
        return n * size * 1.1f + (shape ? 14 : 6);
    }
    static String dow(LocalDate d) { return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT); }
    static String mon(LocalDate d) { return d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT); }

    private Paint text(float size) { Paint pm = new Paint(Paint.ANTI_ALIAS_FLAG); pm.setTypeface(tf); pm.setTextSize(size); pm.setColor(K); return pm; }
    private static String fit(String s, float w, Paint pm) {
        if (pm.measureText(s) <= w) return s;
        while (s.length() > 1 && pm.measureText(s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
    private static List<String> wrap(String t, float w, Paint pm) {
        List<String> out = new ArrayList<String>(); String cur = "";
        for (String wd : t.split("\\s+")) {
            if (wd.isEmpty()) continue;
            String tt = cur.isEmpty() ? wd : cur + " " + wd;
            if (pm.measureText(tt) > w && !cur.isEmpty()) { out.add(cur); cur = wd; } else cur = tt;
        }
        if (!cur.isEmpty()) out.add(cur);
        for (int i = 0; i < out.size(); i++) out.set(i, fit(out.get(i), w, pm));
        return out;
    }
    /** A square caption box with hand lettering. Returns its height. */
    private float caption(float x0, float y0, float w, String text, float size, int bg) {
        Paint pm = text(size); List<String> l = wrap(text.toUpperCase(Locale.ROOT), w - 8, pm);
        float h = l.size() * size * 1.1f + 6;
        Paint f = new Paint(); f.setColor(bg); cv.drawRect(x0, y0, x0 + w, y0 + h, f);
        pen4(x0, y0, w, h, 0.65f);
        pm.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < l.size(); i++) cv.drawText(l.get(i), x0 + w / 2, y0 + size + 1.5f + i * size * 1.1f, pm);
        return h;
    }
    /** A free hand-drawn shape (pebble, leaf or lumpy stone) with the lettering inside. y0 = its top. Returns its height. */
    private float shape(float cx, float y0, float w, String text, float size) {
        Paint pm = text(size); List<String> l = wrap(text.toUpperCase(Locale.ROOT), w - 16, pm);
        float h = l.size() * size * 1.1f + 12, rx = w / 2, ry = h / 2 + 2, cy = y0 + ry;
        int N = 26; float ph = r() * 6.28f, amp = 0.06f + r() * 0.06f; int k = 2 + rnd.nextInt(3);
        float[][] pts = new float[N + 1][2];
        for (int i = 0; i <= N; i++) { double t = i / (double) N * 6.283; double rr = 1 + amp * Math.sin(t * k + ph) + 0.025 * Math.sin(t * 7 + ph * 2);
            pts[i][0] = (float) (cx + rx * rr * Math.cos(t) * (1 + 0.08 * Math.sin(t + ph))); pts[i][1] = (float) (cy + ry * rr * Math.sin(t)); }
        Path pa = new Path(); pa.moveTo(pts[0][0], pts[0][1]); for (int i = 1; i <= N; i++) pa.lineTo(pts[i][0], pts[i][1]); pa.close();
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG); f.setColor(PAPER); cv.drawPath(pa, f);
        for (int pass = 0; pass < 2; pass++) {
            float off = pass == 1 ? (r() - 0.5f) * 1.4f : 0; int end = pass == 1 ? N - 3 - rnd.nextInt(4) : N;
            for (int i = 0; i < end; i++) pen(pts[i][0] + off, pts[i][1] + off, pts[i + 1][0] + off, pts[i + 1][1] + off, pass == 1 ? 0.35f : 0.55f);
        }
        cv.save(); cv.clipPath(pa);
        Paint ht = new Paint(Paint.ANTI_ALIAS_FLAG); ht.setColor(K); ht.setAlpha(90); ht.setStrokeWidth(0.3f);
        for (float i = -rx; i < rx; i += 3) cv.drawLine(cx + i, cy + ry * 0.55f, cx + i + 4, cy + ry * 1.05f, ht);
        cv.restore();
        pm.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < l.size(); i++) cv.drawText(l.get(i), cx, cy - h / 2 + size + 5 + i * size * 1.1f, pm);
        return h + 4;
    }
    /** One fine pen stroke: overshoots a little, wobbles, uneven pressure. */
    private void pen(float ax, float ay, float bx, float by, float lw) {
        float len = (float) Math.hypot(bx - ax, by - ay); if (len < 1) return;
        float ux = (bx - ax) / len, uy = (by - ay) / len, nx = -uy, ny = ux, o0 = (r() - 0.3f) * 2, o1 = (r() - 0.3f) * 2;
        ax -= ux * o0; ay -= uy * o0; bx += ux * o1; by += uy * o1;
        int n = Math.max(2, Math.round(len / 9)); float dr = 0;
        Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG); pp.setColor(K); pp.setAlpha(217); pp.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < n; i++) {
            float t0 = (float) i / n, t1 = (float) (i + 1) / n, d0 = dr; dr = dr * 0.6f + (r() - 0.5f) * 0.9f; if (i == n - 1) dr = 0;
            pp.setStrokeWidth(lw * (0.7f + r() * 0.5f));
            cv.drawLine(ax + (bx - ax) * t0 + nx * d0, ay + (by - ay) * t0 + ny * d0, ax + (bx - ax) * t1 + nx * dr, ay + (by - ay) * t1 + ny * dr, pp);
        }
    }
    private void pen4(float x0, float y0, float w, float h, float lw) {
        pen(x0, y0, x0 + w, y0, lw); pen(x0 + w, y0, x0 + w, y0 + h, lw); pen(x0 + w, y0 + h, x0, y0 + h, lw); pen(x0, y0 + h, x0, y0, lw);
    }
    /** Flat colour laid on by hand: a touch uneven, not quite to the line, letting the fade glow through a little. */
    private void wash(float x0, float y0, float w, float h, int col) {
        Paint f = new Paint(); f.setColor(Pal.alpha(col, 220)); cv.drawRect(x0 + 1, y0 + 1, x0 + w - 1, y0 + h - 1, f);
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 8; i++) {
            float rr = 10 + r() * 30, cx = x0 + r() * w, cy = y0 + r() * h;
            g.setShader(new RadialGradient(cx, cy, rr, r() < 0.5f ? 0x24FFFDF6 : 0x08785A3C, 0, Shader.TileMode.CLAMP));
            cv.save(); cv.clipRect(x0, y0, x0 + w, y0 + h); cv.drawCircle(cx, cy, rr, g); cv.restore();
        }
    }
    /** Fine pen hatching, only towards the panel edges. */
    private void hatch(float x0, float y0, float w, float h, float gap, float a, float ang) {
        cv.save(); cv.clipRect(x0, y0, x0 + w, y0 + h);
        Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG); pp.setColor(K); pp.setAlpha(90); pp.setStrokeWidth(0.3f);
        float cx = x0 + w / 2, cy = y0 + h / 2, R = (float) Math.hypot(w, h), cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
        cv.translate(cx, cy); cv.rotate((float) Math.toDegrees(ang));
        for (float v = -R; v < R; v += gap) for (float u = -R; u < R; u += 6 + r() * 10) {
            float ax = u * cs - v * sn, ay = u * sn + v * cs, edge = Math.max(Math.abs(ax) / (w / 2), Math.abs(ay) / (h / 2));
            if (r() < (edge - 0.62f) * a * 2.4f) cv.drawLine(u, v, u + 4 + r() * 6, v + (r() - 0.5f) * 0.8f, pp);
        }
        cv.restore();
    }
    private void stipple(float x0, float y0, float w, float h, int n, int col) {
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG); f.setColor(col);
        for (int i = 0; i < n; i++) cv.drawCircle(x0 + r() * w, y0 + r() * h, 0.25f + r() * 0.6f, f);
    }
}
