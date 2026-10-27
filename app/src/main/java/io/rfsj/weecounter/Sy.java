package io.rfsj.weecounter;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;

// The looks of the floating counter: one function that draws the bubble in any of them, with the colours
// of the theme, so the bubble itself and the preview of the customising are the same drawing. Everything
// is drawn with shapes: no pictures. Two of them move (see animated); the progress ones show how far the
// slide (or the goal, or the lap) has come.
final class Sy {
    static final String[] NAMES = {"Classic", "Outline", "Mono", "Paper", "Glass", "Pearl", "Sweep", "Duotone", "Neon", "Halo",
            "Soft", "Comic", "Progress", "Dots", "Ripple", "Radar", "Badge", "Hexagon", "Seal", "Star", "Rosette", "Gem",
            "Bead", "Crescent", "Candy", "Dashed"};
    static final int COUNT = NAMES.length;

    // does the look move (it then has to be redrawn all the time)?
    static boolean animated(int st) {
        return st == 14 || st == 15;
    }

    private static int a(int col, int alpha) {
        return (col & 0xFFFFFF) | (alpha << 24);
    }

    // a colour part of the way (t, 0 to 1) from x to y, fully opaque
    private static int mix(int x, int y, float t) {
        int r = (int) (Color.red(x) + (Color.red(y) - Color.red(x)) * t);
        int g = (int) (Color.green(x) + (Color.green(y) - Color.green(x)) * t);
        int b = (int) (Color.blue(x) + (Color.blue(y) - Color.blue(x)) * t);
        return Color.rgb(r, g, b);
    }

    // the same colour with the hue turned by deg degrees
    private static int hue(int col, float deg) {
        float[] h = new float[3];
        Color.colorToHSV(col, h);
        h[0] = (h[0] + deg) % 360;
        return Color.HSVToColor(0xFF, h);
    }

    private static void fill(Canvas c, Paint q, float x, float y, float r, int col) {
        q.setShader(null);
        q.setStyle(Paint.Style.FILL);
        q.setColor(col);
        c.drawCircle(x, y, r, q);
    }

    private static void ring(Canvas c, Paint q, float x, float y, float r, float w, int col) {
        q.setShader(null);
        q.setStyle(Paint.Style.STROKE);
        q.setStrokeWidth(w);
        q.setColor(col);
        c.drawCircle(x, y, r, q);
    }

    // the text in the middle: as big as fits in maxw (a fraction of the width of the bubble)
    private static void label(Canvas c, Paint q, String s, float x, float y, float r, float maxw, int col, boolean bold) {
        q.setShader(null);
        q.setPathEffect(null);
        q.setStyle(Paint.Style.FILL);
        q.setColor(col);
        q.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        q.setTextAlign(Paint.Align.CENTER);
        q.setTextSize(r * .72f);
        float w = q.measureText(s), lim = 2 * r * maxw;
        if (w > lim) q.setTextSize(q.getTextSize() * lim / w);
        c.drawText(s, x, y - (q.descent() + q.ascent()) / 2, q);
    }

    // a regular polygon with n corners, the first at the angle rot
    private static Path poly(float x, float y, float r, int n, double rot) {
        Path p = new Path();
        for (int i = 0; i < n; i++) {
            double t = rot + 2 * Math.PI * i / n;
            float px = x + (float) (r * Math.cos(t)), py = y + (float) (r * Math.sin(t));
            if (i == 0) p.moveTo(px, py);
            else p.lineTo(px, py);
        }
        p.close();
        return p;
    }

    // Draws look st, with its middle at (cx, cy) and the radius r, the text s in it, in the colours of th. u is
    // one dp, ph (0 to 1, over and over) moves the animated looks, prog (0 to 1) is how far the count has come.
    // q is a paint to draw with (it is reset here).
    static void draw(Canvas c, int st, float cx, float cy, float r, String s, Th th, float u, float ph, float prog, Paint q) {
        q.reset();
        q.setAntiAlias(true);
        float R = r - 2 * u;
        int ac = th.ac, bg = th.bg, fg = th.fg, dim = th.dim, white = 0xFFFFFFFF, black = 0xFF000000;
        switch (st) {
            case 0: // Classic: a flat disc of the accent colour with a thin ring
                fill(c, q, cx, cy, R, ac);
                ring(c, q, cx, cy, R, 2 * u, a(fg, 0x99));
                label(c, q, s, cx, cy, R, .72f, bg, true);
                break;
            case 1: // Outline: only a ring, see-through inside
                fill(c, q, cx, cy, R, a(bg, 0xB0));
                ring(c, q, cx, cy, R - 1.5f * u, 3.5f * u, ac);
                label(c, q, s, cx, cy, R, .66f, ac, true);
                break;
            case 2: // Mono: the colour of the screen and a hairline, nothing else
                fill(c, q, cx, cy, R, bg);
                ring(c, q, cx, cy, R, u, fg);
                label(c, q, s, cx, cy, R, .66f, fg, false);
                break;
            case 3: // Paper: the colours turned round, lifted off the screen by a soft shadow
                for (int k = 1; k <= 4; k++) fill(c, q, cx, cy + k * .9f * u, R, a(black, 0x16));
                fill(c, q, cx, cy, R, fg);
                label(c, q, s, cx, cy, R, .7f, bg, true);
                break;
            case 4: // Glass: a see-through tint with a shine on it
                fill(c, q, cx, cy, R, a(ac, 0x55));
                ring(c, q, cx, cy, R, 1.5f * u, a(fg, 0xAA));
                q.setStyle(Paint.Style.STROKE);
                q.setStrokeWidth(3 * u);
                q.setStrokeCap(Paint.Cap.ROUND);
                q.setColor(a(white, 0xCC));
                c.drawArc(new RectF(cx - R * .8f, cy - R * .8f, cx + R * .8f, cy + R * .8f), 200, 55, false, q);
                label(c, q, s, cx, cy, R, .66f, fg, true);
                break;
            case 5: // Pearl: a sphere lit from the top left
                q.setStyle(Paint.Style.FILL);
                q.setShader(new RadialGradient(cx - R * .35f, cy - R * .4f, R * 1.5f,
                        new int[]{mix(ac, white, .55f), ac, mix(ac, black, .45f)}, new float[]{0, .5f, 1}, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, R, q);
                fill(c, q, cx - R * .36f, cy - R * .42f, R * .14f, a(white, 0x99));
                label(c, q, s, cx, cy, R, .66f, bg, true);
                break;
            case 6: // Sweep: the colour turns through the hues round the disc
                c.save();
                c.rotate(-90, cx, cy);
                q.setStyle(Paint.Style.FILL);
                q.setShader(new SweepGradient(cx, cy, new int[]{ac, hue(ac, 70), hue(ac, 140), ac}, null));
                c.drawCircle(cx, cy, R, q);
                c.restore();
                label(c, q, s, cx, cy, R, .66f, white, true);
                break;
            case 7: { // Duotone: cut in two across, the accent colour and its neighbour on the colour wheel
                Path clip = new Path();
                clip.addCircle(cx, cy, R, Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                q.setStyle(Paint.Style.FILL);
                q.setColor(ac);
                c.drawRect(cx - R, cy - R, cx + R, cy + R, q);
                Path tri = new Path();
                tri.moveTo(cx + R, cy - R);
                tri.lineTo(cx + R, cy + R);
                tri.lineTo(cx - R, cy + R);
                tri.close();
                q.setColor(hue(ac, 150));
                c.drawPath(tri, q);
                c.restore();
                label(c, q, s, cx, cy, R, .66f, white, true);
                break;
            }
            case 8: // Neon: a dark disc with a glowing ring
                fill(c, q, cx, cy, R, a(bg, 0xCC));
                for (int i = 4; i >= 1; i--) ring(c, q, cx, cy, R - 5 * u, (2 + i * 2.4f) * u, a(ac, 0x22));
                ring(c, q, cx, cy, R - 5 * u, 2 * u, ac);
                label(c, q, s, cx, cy, R, .58f, ac, true);
                break;
            case 9: // Halo: a small bright core in a soft glow
                q.setStyle(Paint.Style.FILL);
                q.setShader(new RadialGradient(cx, cy, R, new int[]{a(ac, 0xC0), a(ac, 0)}, new float[]{.5f, 1},
                        Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, R, q);
                fill(c, q, cx, cy, R * .6f, ac);
                label(c, q, s, cx, cy, R * .6f, .74f, bg, true);
                break;
            case 10: { // Soft: pressed out of the screen, one light edge and one dark
                int base = mix(bg, fg, .14f);
                fill(c, q, cx + 3 * u, cy + 3 * u, R, a(black, 0x66));
                fill(c, q, cx - 2.5f * u, cy - 2.5f * u, R, a(white, 0x33));
                fill(c, q, cx, cy, R, base);
                label(c, q, s, cx, cy, R, .66f, fg, true);
                break;
            }
            case 11: // Comic: white, a thick black outline and a hard shadow, like the speech bubble
                fill(c, q, cx + 3 * u, cy + 3 * u, R - 2 * u, a(black, 0x66));
                fill(c, q, cx, cy, R - 2 * u, white);
                ring(c, q, cx, cy, R - 2 * u, 2.5f * u, black);
                label(c, q, s, cx, cy, R, .66f, black, true);
                break;
            case 12: { // Progress: a ring that fills up as the count comes to its target
                float rr = R - 4 * u;
                fill(c, q, cx, cy, R, a(bg, 0xCC));
                ring(c, q, cx, cy, rr, 5 * u, a(dim, 0x66));
                q.setStrokeCap(Paint.Cap.ROUND);
                q.setColor(ac);
                c.drawArc(new RectF(cx - rr, cy - rr, cx + rr, cy + rr), -90, 360 * Math.max(.02f, prog), false, q);
                label(c, q, s, cx, cy, R, .56f, fg, true);
                break;
            }
            case 13: { // Dots: twelve dots round the edge that light up one after the other
                fill(c, q, cx, cy, R, a(bg, 0xCC));
                int lit = Math.round(prog * 12);
                for (int i = 0; i < 12; i++) {
                    double t = -Math.PI / 2 + i * Math.PI / 6;
                    fill(c, q, cx + (float) Math.cos(t) * (R - 5 * u), cy + (float) Math.sin(t) * (R - 5 * u), 2.6f * u,
                            i < lit ? ac : a(dim, 0x88));
                }
                label(c, q, s, cx, cy, R, .56f, fg, true);
                break;
            }
            case 14: { // Ripple (moves): rings spread out of a small disc
                float core = R * .58f;
                for (int k = 0; k < 3; k++) {
                    float t = (ph + k / 3f) % 1f;
                    ring(c, q, cx, cy, core + t * (R - core), 2 * u, a(ac, (int) (200 * (1 - t))));
                }
                fill(c, q, cx, cy, core, ac);
                label(c, q, s, cx, cy, core, .74f, bg, true);
                break;
            }
            case 15: // Radar (moves): a beam sweeps round a dark disc
                fill(c, q, cx, cy, R, a(bg, 0xD8));
                ring(c, q, cx, cy, R - u, 2 * u, ac);
                q.setStrokeWidth(u);
                q.setColor(a(ac, 0x44));
                c.drawLine(cx - R, cy, cx + R, cy, q);
                c.drawLine(cx, cy - R, cx, cy + R, q);
                c.save();
                c.rotate(ph * 360, cx, cy);
                q.setStyle(Paint.Style.FILL);
                q.setShader(new SweepGradient(cx, cy, new int[]{a(ac, 0), a(ac, 0), a(ac, 0x99)}, new float[]{0, .75f, 1}));
                c.drawCircle(cx, cy, R - 2 * u, q);
                c.restore();
                label(c, q, s, cx, cy, R, .56f, ac, true);
                break;
            case 16: { // Badge: a rounded square
                q.setStyle(Paint.Style.FILL);
                q.setColor(ac);
                c.drawRoundRect(new RectF(cx - R, cy - R, cx + R, cy + R), R * .5f, R * .5f, q);
                label(c, q, s, cx, cy, R, .72f, bg, true);
                break;
            }
            case 17: { // Hexagon
                Path hx = poly(cx, cy, R * 1.04f, 6, Math.PI / 6);
                q.setStyle(Paint.Style.FILL);
                q.setColor(ac);
                c.drawPath(hx, q);
                q.setStyle(Paint.Style.STROKE);
                q.setStrokeWidth(2 * u);
                q.setStrokeJoin(Paint.Join.ROUND);
                q.setColor(a(fg, 0x99));
                c.drawPath(hx, q);
                label(c, q, s, cx, cy, R, .66f, bg, true);
                break;
            }
            case 18: // Seal: a scalloped edge, like a stamp
                for (int k = 0; k < 12; k++) {
                    double t = k * Math.PI / 6;
                    fill(c, q, cx + (float) Math.cos(t) * R * .8f, cy + (float) Math.sin(t) * R * .8f, R * .2f, ac);
                }
                fill(c, q, cx, cy, R * .84f, ac);
                label(c, q, s, cx, cy, R, .62f, bg, true);
                break;
            case 19: { // Star: two squares, one turned (the eight pointed star of a hizb mark)
                q.setStyle(Paint.Style.FILL);
                q.setColor(ac);
                c.drawPath(poly(cx, cy, R * 1.02f, 4, 0), q);
                c.drawPath(poly(cx, cy, R * 1.02f, 4, Math.PI / 4), q);
                label(c, q, s, cx, cy, R, .6f, bg, true);
                break;
            }
            case 20: // Rosette: eight rings that cross, like the pattern of a tile
                fill(c, q, cx, cy, R, a(bg, 0xB0));
                for (int k = 0; k < 8; k++) {
                    double t = k * Math.PI / 4;
                    ring(c, q, cx + (float) Math.cos(t) * R * .5f, cy + (float) Math.sin(t) * R * .5f, R * .5f, 1.6f * u, a(ac, 0xE0));
                }
                fill(c, q, cx, cy, R * .3f, a(ac, 0x30));
                label(c, q, s, cx, cy, R, .5f, ac, true);
                break;
            case 21: { // Gem: cut in eight facets, light and dark, with a dark table on top
                int light = mix(ac, white, .3f), dark = mix(ac, black, .25f);
                double rot = Math.PI / 8;
                q.setStyle(Paint.Style.FILL);
                for (int k = 0; k < 8; k++) {
                    Path f = new Path();
                    f.moveTo(cx, cy);
                    f.lineTo(cx + (float) Math.cos(rot + k * Math.PI / 4) * R * 1.04f, cy + (float) Math.sin(rot + k * Math.PI / 4) * R * 1.04f);
                    f.lineTo(cx + (float) Math.cos(rot + (k + 1) * Math.PI / 4) * R * 1.04f, cy + (float) Math.sin(rot + (k + 1) * Math.PI / 4) * R * 1.04f);
                    f.close();
                    q.setColor(k % 2 == 0 ? light : dark);
                    c.drawPath(f, q);
                }
                q.setColor(a(bg, 0xE6));
                c.drawPath(poly(cx, cy, R * .6f, 8, rot), q);
                label(c, q, s, cx, cy, R, .5f, ac, true);
                break;
            }
            case 22: // Bead: a tasbih bead on its string
                q.setStyle(Paint.Style.STROKE);
                q.setStrokeWidth(2.2f * u);
                q.setColor(a(fg, 0xAA));
                c.drawLine(cx - r, cy, cx + r, cy, q);
                q.setStyle(Paint.Style.FILL);
                q.setShader(new RadialGradient(cx - R * .3f, cy - R * .34f, R * 1.3f,
                        new int[]{mix(ac, white, .5f), ac, mix(ac, black, .5f)}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, R * .88f, q);
                fill(c, q, cx - R * .3f, cy - R * .34f, R * .13f, a(white, 0x88));
                label(c, q, s, cx, cy, R * .88f, .66f, bg, true);
                break;
            case 23: { // Crescent: a crescent over the number
                fill(c, q, cx, cy, R, ac);
                Path cr = new Path(), cut = new Path();
                cr.addCircle(cx, cy - R * .5f, R * .2f, Path.Direction.CW);
                cut.addCircle(cx + R * .08f, cy - R * .54f, R * .17f, Path.Direction.CW);
                cr.op(cut, Path.Op.DIFFERENCE);
                q.setStyle(Paint.Style.FILL);
                q.setColor(bg);
                c.drawPath(cr, q);
                label(c, q, s, cx, cy + R * .14f, R, .56f, bg, true);
                break;
            }
            case 24: { // Candy: diagonal stripes
                Path clip = new Path();
                clip.addCircle(cx, cy, R, Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                fill(c, q, cx, cy, R * 1.5f, ac);
                q.setStyle(Paint.Style.STROKE);
                q.setStrokeWidth(5 * u);
                q.setColor(a(white, 0x40));
                for (float k = -2 * R; k < 2 * R; k += 11 * u) c.drawLine(cx + k - R, cy + R, cx + k + R, cy - R, q);
                c.restore();
                fill(c, q, cx, cy, R * .62f, ac);
                label(c, q, s, cx, cy, R * .62f, .78f, bg, true);
                break;
            }
            default: // Dashed: a dashed ring round a faint disc
                fill(c, q, cx, cy, R * .92f, a(ac, 0x30));
                q.setStyle(Paint.Style.STROKE);
                q.setStrokeWidth(3 * u);
                q.setColor(ac);
                q.setPathEffect(new DashPathEffect(new float[]{6 * u, 5 * u}, 0));
                c.drawCircle(cx, cy, R - 3 * u, q);
                label(c, q, s, cx, cy, R, .6f, ac, true);
        }
    }
}
