package io.rfsj.weecounter;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

// A guided tour: the screen goes dim, one part of it (or two) is lit, and a small card says in a
// sentence or two what it is. Steps with no part to light show a finger doing the gesture instead (a
// swipe or a tap). Next, Back and Skip are on the card; Back (the key) ends the tour.
// A step can ask the user to do the thing it is about (need): then the touches go through to the screen
// underneath, so the tap really counts, and the tour goes on by itself a moment after the thing was done
// (did) or the sheet it opens was closed. Next always works. On the other steps nothing underneath gets a
// touch. The tour is marked like a sheet (see Sh.isUp and Sh.back).
final class Tr extends FrameLayout {
    static final int NONE = 0, UP = 1, LEFT = 2, TAP = 3; // what the finger shows

    // One step: a title and a sentence or two; demo: the finger; need: what the user is asked to do (null: only
    // Next): "tap" (count), "minus" (take one back), "sheet" (open a sheet and close it), "advance" (the slide
    // moves on), "swipe" (to another slide), "view" (to another view); enter: runs when the step begins;
    // lit: the views to light (none: no part).
    static class Step {
        final String title, text;
        int demo = NONE;
        String need;
        Runnable enter;
        View[] lit = new View[0];

        Step(String title, String text) {
            this.title = title;
            this.text = text;
        }

        Step demo(int d) {
            demo = d;
            return this;
        }

        Step need(String n) {
            need = n;
            return this;
        }

        Step enter(Runnable r) {
            enter = r;
            return this;
        }

        Step lit(View... v) {
            lit = v;
            return this;
        }
    }

    final Activity a;
    final Th th;
    final ArrayList<Step> steps;
    final String lastLabel; // the button of the last step
    final Runnable lastAct; // what it does, after the tour has closed (null: only close)
    final Runnable onClose; // runs when the tour has closed, however it was closed
    final float u;
    final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
            fing = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path path = new Path();
    final ArrayList<RectF> holes = new ArrayList<>();
    final LinearLayout card;
    final TextView tt, tx, pg, no, back, nx;
    int at;
    float t; // the finger's progress, 0 to 1, over and over
    ValueAnimator anim;
    boolean closed;
    boolean seen; // a sheet has been up since this step began (a "sheet" step waits for it to close)
    Runnable poll; // while a "sheet" step waits: looks every so often whether the sheet has been closed

    Tr(Activity a, Th th, ArrayList<Step> steps, String lastLabel, Runnable lastAct, Runnable onClose) {
        super(a);
        this.a = a;
        this.th = th;
        this.steps = steps;
        this.lastLabel = lastLabel;
        this.lastAct = lastAct;
        this.onClose = onClose;
        u = a.getResources().getDisplayMetrics().density;
        setContentDescription(Sh.MARK);
        setWillNotDraw(false);
        dim.setColor((th.bg & 0xFFFFFF) | 0xDD000000);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2.5f * u);
        ring.setColor(th.ac);
        fing.setStyle(Paint.Style.FILL);

        card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        card.setPadding(Sh.dp(a, 20), Sh.dp(a, 18), Sh.dp(a, 20), Sh.dp(a, 8));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Th.mix(th.bg, th.fg, .08f));
        g.setCornerRadius(Sh.dp(a, 20));
        card.setBackground(g);
        tt = text(th.fg, 20);
        tt.getPaint().setFakeBoldText(true);
        tx = text(th.fg, 15);
        tx.setPadding(0, Sh.dp(a, 8), 0, 0);
        pg = text(th.dim, 12);
        pg.setPadding(0, Sh.dp(a, 12), 0, 0);
        card.addView(tt);
        card.addView(tx);
        card.addView(pg);
        LinearLayout bar = new LinearLayout(a);
        no = text(th.dim, 16);
        back = text(th.dim, 16);
        nx = text(th.ac, 16);
        back.setText("Back");
        for (TextView b : new TextView[]{no, back, nx}) {
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, Sh.dp(a, 12), 0, Sh.dp(a, 12));
            bar.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        card.addView(bar);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        lp.setMargins(Sh.dp(a, 24), Sh.dp(a, 56), Sh.dp(a, 24), Sh.dp(a, 56));
        addView(card, lp);

        poll = new Runnable() {
            @Override public void run() {
                if (closed || !"sheet".equals(steps.get(at).need)) return;
                boolean mine = Sh.top(a) == Tr.this;
                if (!mine) {
                    seen = true;
                } else if (seen) {
                    seen = false;
                    go(at + 1);
                    return;
                }
                postDelayed(this, 250);
            }
        };
        no.setOnClickListener(v -> close());
        back.setOnClickListener(v -> {
            if (at > 0) go(at - 1);
        });
        nx.setOnClickListener(v -> {
            if (at < steps.size() - 1) {
                go(at + 1);
            } else {
                close();
                if (lastAct != null) lastAct.run();
            }
        });
    }

    private TextView text(int col, float sp) {
        TextView v = new TextView(a);
        v.setTextColor(col);
        v.setTextSize(sp);
        return v;
    }

    // puts the tour over the screen and starts at the first step (once the screen is laid out)
    void show() {
        ((ViewGroup) a.findViewById(android.R.id.content)).addView(this, new FrameLayout.LayoutParams(-1, -1));
        post(() -> go(0));
    }

    // the step i: what is lit, where the card goes (on the other half of the screen), the finger
    void go(int i) {
        if (closed) return;
        if (i >= steps.size()) { // nothing after the last step
            close();
            return;
        }
        at = i;
        Step s = steps.get(i);
        boolean last = i == steps.size() - 1;
        removeCallbacks(poll);
        seen = false;
        setClickable(s.need == null); // a step that asks for something lets the touches through
        if (s.enter != null) s.enter.run();
        tt.setText(s.title);
        tx.setText(s.text);
        pg.setText((i + 1) + " of " + steps.size());
        back.setVisibility(i == 0 ? View.INVISIBLE : View.VISIBLE);
        no.setText(last ? "Close" : "Skip");
        nx.setText(last ? lastLabel : "Next");
        holes.clear();
        int[] me = new int[2], tp = new int[2];
        getLocationInWindow(me);
        float sy = 0;
        for (View v : s.lit) {
            if (v.getVisibility() != View.VISIBLE || v.getWidth() == 0) continue;
            v.getLocationInWindow(tp);
            float pad = 4 * u;
            RectF r = new RectF(tp[0] - me[0] - pad, tp[1] - me[1] - pad, tp[0] - me[0] + v.getWidth() + pad,
                    tp[1] - me[1] + v.getHeight() + pad);
            holes.add(r);
            sy += r.centerY();
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
        if (!holes.isEmpty()) lp.gravity = sy / holes.size() < getHeight() / 2f ? Gravity.BOTTOM : Gravity.TOP;
        else lp.gravity = s.demo != NONE ? Gravity.BOTTOM : Gravity.CENTER;
        card.setLayoutParams(lp);
        if (anim != null) anim.cancel();
        anim = null;
        t = 0;
        if ("sheet".equals(s.need)) postDelayed(poll, 250);
        if (s.demo != NONE) {
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1300);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.addUpdateListener(va -> {
                t = (Float) va.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }
        invalidate();
    }

    // the user did something (see Step.need): if the step is waiting for it, the next one comes in a moment
    void did(String ev) {
        if (closed || !ev.equals(steps.get(at).need)) return;
        final int me = at;
        postDelayed(() -> {
            if (!closed && at == me) go(me + 1);
        }, 700);
    }

    // do the touches go through to the screen underneath?
    boolean pass() {
        return !closed && steps.get(at).need != null;
    }

    static boolean passing(Activity a) {
        View v = Sh.top(a);
        return v instanceof Tr && ((Tr) v).pass();
    }

    void close() {
        if (closed) return;
        closed = true;
        removeCallbacks(poll);
        if (anim != null) anim.cancel();
        setVisibility(View.GONE);
        post(() -> {
            if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
        });
        if (onClose != null) onClose.run();
    }

    @Override protected void onDraw(Canvas c) {
        float rr = 10 * u;
        path.reset();
        path.setFillType(Path.FillType.EVEN_ODD); // the dim screen with a hole for every lit part
        path.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        for (RectF r : holes) path.addRoundRect(r, rr, rr, Path.Direction.CW);
        c.drawPath(path, dim);
        for (RectF r : holes) c.drawRoundRect(r, rr, rr, ring);
        Step s = steps.get(at);
        if (s.demo == NONE) return;
        float cx = getWidth() / 2f, cy = getHeight() * .36f, d = 90 * u;
        if (s.demo == TAP) { // a ring that spreads out of a dot
            fing.setColor((th.ac & 0xFFFFFF) | ((int) (200 * (1 - t)) << 24));
            c.drawCircle(cx, cy, (14 + 30 * t) * u, fing);
            fing.setColor(th.ac);
            c.drawCircle(cx, cy, 10 * u, fing);
            return;
        }
        // a finger that slides across and fades at the end, with a short trail
        for (int k = 3; k >= 0; k--) {
            float e = 1 - (1 - Math.max(0, t - .06f * k)) * (1 - Math.max(0, t - .06f * k)); // slows down at the end
            float x = s.demo == LEFT ? cx + d - 2 * d * e : cx, y = s.demo == UP ? cy + d - 2 * d * e : cy;
            float fade = t < .8f ? 1 : (1 - t) / .2f;
            fing.setColor((th.ac & 0xFFFFFF) | ((int) (255 * fade * (k == 0 ? 1 : .3f / k)) << 24));
            c.drawCircle(x, y, (k == 0 ? 14 : 11) * u, fing);
        }
    }
}
