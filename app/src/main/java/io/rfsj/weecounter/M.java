package io.rfsj.weecounter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

public class M extends Activity implements View.OnClickListener, View.OnLongClickListener {
    // {pref key, default, settings label}; indices 0..8 are used below and in S
    static final String[][] D = {
        {"fs", "96", "Font size (sp)"},
        {"mx", "0", "Max count (0 = no limit)"},
        {"st", "100", "Change colour every (count)"},
        {"sz", "24", "Dot / tally size (dp)"},
        {"cl", "FFFFFF 00E676 40C4FF FFD740 FF5252 E040FB", "Colours (hex, space separated)"},
        {"vt", "12", "Vibration: tap"},
        {"vh", "40 60 40", "Vibration: hold (panel / counting view)"},
        {"vr", "80", "Vibration: reset"},
        {"vl", "150 80 150", "Vibration: max reached / screen full"}};

    // counting views: 0 blackout, 1 dots, 2 tally
    static final String[] NM = {"blackout", "dots", "tally"};

    SharedPreferences p;
    FrameLayout f;
    V c;
    TextView t, se, rs, dn, vw;
    View[] ui;
    Vibrator v;
    VibrationEffect vt, vh, vr, vl;
    int[] cl;
    int n, mx, st, mode;
    boolean cv; // true = counting view shown, false = panel (number + buttons)

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);
        p = getSharedPreferences("p", 0);
        n = p.getInt("n", 0);
        mode = p.getInt("md", 0);
        cv = p.getBoolean("dk", false);
        v = getSystemService(Vibrator.class);

        // the whole screen is the tap / hold target; everything else is non-clickable
        f = new FrameLayout(this);
        f.setBackgroundColor(0xFF000000);
        f.setOnClickListener(this);
        f.setOnLongClickListener(this);

        c = new V(this);
        f.addView(c, new FrameLayout.LayoutParams(-1, -1));

        t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        f.addView(t, new FrameLayout.LayoutParams(-1, -1));

        se = b("settings", 14, Gravity.TOP | Gravity.START);
        se.setOnClickListener(this);
        rs = b("HOLD TO RESET", 14, Gravity.TOP | Gravity.END);
        rs.setOnLongClickListener(this);
        dn = b("-1", 28, Gravity.BOTTOM | Gravity.START);
        dn.setOnClickListener(this);
        vw = b("", 14, Gravity.BOTTOM | Gravity.END);
        vw.setOnClickListener(this);

        ui = new View[]{t, se, rs, dn, vw};
        setContentView(f);
    }

    TextView b(String s, int sp, int g) {
        TextView x = new TextView(this);
        x.setText(s);
        x.setTextSize(sp);
        x.setTextColor(0xFF666666);
        x.setPadding(dp(24), dp(16), dp(24), dp(16));
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(-2, -2, g);
        l.setMargins(0, dp(40), 0, dp(40));
        f.addView(x, l);
        return x;
    }

    int dp(int x) {
        return (int) (x * getResources().getDisplayMetrics().density + .5f);
    }

    String g(int i) {
        return p.getString(D[i][0], D[i][1]);
    }

    int num(int i) {
        try {
            return Integer.parseInt(g(i).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    int[] cs() {
        try {
            String[] a = g(4).trim().split("\\s+");
            int[] r = new int[a.length];
            for (int j = 0; j < a.length; j++)
                r[j] = 0xFF000000 | (int) Long.parseLong(a[j].replace("#", ""), 16);
            return r;
        } catch (Exception e) {
            return new int[]{0xFFFFFFFF};
        }
    }

    // "40 60 40" = buzz 40ms, pause 60ms, buzz 40ms. Invalid/empty = off.
    VibrationEffect fx(int i) {
        try {
            String[] a = g(i).trim().split("[^0-9]+");
            long[] w = new long[a.length + 1];
            for (int j = 0; j < a.length; j++) w[j + 1] = Long.parseLong(a[j]);
            return VibrationEffect.createWaveform(w, -1);
        } catch (Exception e) {
            return null;
        }
    }

    void z(VibrationEffect e) {
        if (e != null) v.vibrate(e);
    }

    @Override protected void onResume() {
        super.onResume();
        mx = num(1);
        st = Math.max(1, num(2));
        c.sz = dp(Math.max(6, num(3)));
        t.setTextSize(Math.max(8, num(0)));
        cl = cs();
        vt = fx(5);
        vh = fx(6);
        vr = fx(7);
        vl = fx(8);
        if (p.getBoolean("aw", true))
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        draw();
    }

    @Override protected void onPause() {
        super.onPause();
        p.edit().putInt("n", n).putInt("md", mode).apply();
    }

    // hide status + navigation bars; swipe from the edge shows them briefly
    @Override public void onWindowFocusChanged(boolean h) {
        super.onWindowFocusChanged(h);
        if (h) f.setSystemUiVisibility(0x1706);
    }

    void draw() {
        int col = cl[(n / st) % cl.length];
        t.setText(Integer.toString(n));
        t.setTextColor(col);
        vw.setText("view: " + NM[mode]);
        for (View x : ui) x.setVisibility(cv ? View.INVISIBLE : View.VISIBLE);
        c.mode = mode;
        c.n = n;
        c.p.setColor(col);
        c.setVisibility(cv && mode > 0 ? View.VISIBLE : View.INVISIBLE);
        c.invalidate();
    }

    // true when the next tap must not count: max reached, or no room left on screen
    boolean full() {
        return (mx > 0 && n >= mx) || (mode > 0 && !c.put(n + 1, null));
    }

    @Override public void onClick(View x) {
        if (x == se) {
            startActivity(new Intent(this, S.class));
            return;
        }
        if (x == vw) {
            mode = (mode + 1) % 3;
            z(vt);
            draw();
            return;
        }
        if (x == dn) {
            if (n > 0) n--;
            z(vt);
        } else if (full()) {
            z(vl);
            return;
        } else {
            n++;
            z(full() ? vl : vt);
        }
        draw();
    }

    @Override public boolean onLongClick(View x) {
        if (x == rs) {
            n = 0;
            z(vr);
        } else {
            cv = !cv;
            z(vh);
        }
        draw();
        return true;
    }

    // draws the dots / tally counting views
    static class V extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int mode, n, sz;

        V(Context x) {
            super(x);
            p.setTextAlign(Paint.Align.CENTER);
        }

        @Override protected void onDraw(Canvas c) {
            if (mode > 0) put(n, c);
        }

        // lays out k marks and draws them when c != null; true if they all fit
        boolean put(int k, Canvas c) {
            float m = sz / 2f, w = getWidth() - 2 * m, h = getHeight() - 2 * m;
            p.setTextSize(sz);
            if (mode == 1) { // dots: left to right, top to bottom
                int cols = Math.max(1, (int) (w / sz)), rows = (int) (h / sz);
                if (c != null) {
                    float ox = (getWidth() - cols * sz) / 2f, oy = (getHeight() - rows * sz) / 2f;
                    int d = Math.min(k, cols * rows);
                    for (int i = 0; i < d; i++)
                        c.drawCircle(ox + (i % cols + .5f) * sz, oy + (i / cols + .5f) * sz, sz * .3f, p);
                }
                return k <= cols * rows;
            }
            // tally: every 5th mark turns the 4 bars into one glyph, then a gap
            float lh = sz * 1.4f, x = 0;
            int line = 0, rows = (int) (h / lh), g = k / 5;
            for (int i = 0; i < g * 2 + k % 5; i++) {
                boolean five = i < 2 * g && i % 2 == 0, gap = i < 2 * g && i % 2 == 1;
                float tw = five ? sz : gap ? sz * .5f : sz * .35f;
                if (gap) {
                    if (x > 0) x += tw; // a gap never starts a line
                    continue;
                }
                if (x + tw > w) {
                    line++;
                    x = 0;
                }
                if (line >= rows) return false;
                if (c != null)
                    c.drawText(five ? "\u534c" : "|", m + x + tw / 2, m + line * lh + sz, p);
                x += tw;
            }
            return true;
        }
    }
}
