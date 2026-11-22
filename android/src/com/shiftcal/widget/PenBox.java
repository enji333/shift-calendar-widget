package com.shiftcal.widget;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import java.util.Random;

/**
 * A box drawn with a fine pen (v3.19, for the day view): colour laid on by hand, a touch uneven and not quite
 * to the line, a thin wobbly outline that overshoots its corners a little, and optional light hatching near the edges.
 */
final class PenBox extends Drawable {
    private final int fill, ink; private final long seed; private final float lw, density; private final boolean hatch;
    private final Paint pf = new Paint(Paint.ANTI_ALIAS_FLAG), pp = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** fill or ink 0 = none. lw in dp. */
    PenBox(int fill, int ink, float lw, boolean hatch, long seed, float density) {
        this.fill = fill; this.ink = ink; this.lw = lw; this.hatch = hatch; this.seed = seed; this.density = density;
        pp.setStrokeCap(Paint.Cap.ROUND); pp.setStyle(Paint.Style.STROKE);
    }
    @Override public void draw(Canvas c) {
        Rect b = getBounds(); if (b.width() < 2 || b.height() < 2) return;
        Random r = new Random(seed);
        float d = density, x0 = b.left + 1.2f * d, y0 = b.top + 1.2f * d, x1 = b.right - 1.2f * d, y1 = b.bottom - 1.2f * d;
        if (fill != 0) {
            // flat colour, a little wobbly at its edges
            Path p = new Path(); float j = 0.9f * d;
            p.moveTo(x0 + r.nextFloat() * j, y0 + r.nextFloat() * j);
            p.lineTo(x1 - r.nextFloat() * j, y0 + r.nextFloat() * j);
            p.lineTo(x1 - r.nextFloat() * j, y1 - r.nextFloat() * j);
            p.lineTo(x0 + r.nextFloat() * j, y1 - r.nextFloat() * j);
            p.close(); pf.setColor(fill); c.drawPath(p, pf);
        }
        if (ink == 0) return;
        if (hatch) {
            c.save(); c.clipRect(x0, y0, x1, y1);
            pp.setColor(ink); pp.setAlpha(70); pp.setStrokeWidth(0.35f * d);
            float w = x1 - x0, h = y1 - y0, gap = 3.2f * d;
            for (float v = -h; v < w; v += gap) {          // short diagonal strokes, only along the bottom and right edges
                float sx = x0 + v, sy = y1;
                if (r.nextFloat() < 0.55f) c.drawLine(sx, sy, sx + 5 * d, sy - 5 * d - r.nextFloat() * 4 * d, pp);
            }
            for (float u = y0 + gap; u < y1; u += gap) if (r.nextFloat() < 0.45f) c.drawLine(x1, u, x1 - 4 * d - r.nextFloat() * 3 * d, u - 4 * d, pp);
            c.restore();
        }
        pp.setColor(ink); pp.setAlpha(220);
        line(c, r, x0, y0, x1, y0); line(c, r, x1, y0, x1, y1); line(c, r, x1, y1, x0, y1); line(c, r, x0, y1, x0, y0);
    }
    /** One pen stroke: overshoots a little, wobbles, uneven pressure. */
    private void line(Canvas c, Random r, float ax, float ay, float bx, float by) { pen(c, pp, r, ax, ay, bx, by, lw * density, density); }
    static void pen(Canvas c, Paint pp, Random r, float ax, float ay, float bx, float by, float lw, float d) {
        float len = (float) Math.hypot(bx - ax, by - ay); if (len < 1) return;
        float ux = (bx - ax) / len, uy = (by - ay) / len, nx = -uy, ny = ux, o0 = (r.nextFloat() - 0.3f) * 2 * d, o1 = (r.nextFloat() - 0.3f) * 2 * d;
        ax -= ux * o0; ay -= uy * o0; bx += ux * o1; by += uy * o1;
        int n = Math.max(2, Math.round(len / (9 * d))); float dr = 0;
        for (int i = 0; i < n; i++) {
            float t0 = (float) i / n, t1 = (float) (i + 1) / n, d0 = dr; dr = dr * 0.6f + (r.nextFloat() - 0.5f) * 0.8f * d; if (i == n - 1) dr = 0;
            pp.setStrokeWidth(lw * (0.7f + r.nextFloat() * 0.5f));
            c.drawLine(ax + (bx - ax) * t0 + nx * d0, ay + (by - ay) * t0 + ny * d0, ax + (bx - ax) * t1 + nx * dr, ay + (by - ay) * t1 + ny * dr, pp);
        }
    }
    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
