package io.rfsj.weecounter;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

public class M extends Activity implements View.OnClickListener, View.OnLongClickListener, Runnable {
    // {pref key, default, settings label}; indices 0..9 are used below and in S
    // (the first five are numeric fields)
    static final String[][] D = {
        {"fs", "96", "Font size (sp)"},
        {"mx", "0", "Max count (0 = no limit)"},
        {"st", "100", "Change number colour every (count)"},
        {"sz", "24", "Dot / tally size (dp)"},
        {"ps", "6", "Pixel shift against burn-in (dp, 0 = off)"},
        {"cl", "FFFFFF 00E676 40C4FF FFD740 FF5252 E040FB", "Colours (hex, space separated)"},
        {"vt", "12", "Vibration: tap"},
        {"vh", "40 60 40", "Vibration: hold (panel / counting view)"},
        {"vr", "80", "Vibration: reset"},
        {"vl", "150 80 150", "Vibration: max reached / lap complete"}};

    // counting views: 0 blackout, 1 dots, 2 tally
    static final String[] NM = {"blackout", "dots", "tally"};

    // Do Not Disturb warning above the bottom buttons: 0 = not shown, 1 = priority only, 2 = alarms only
    static final String[] DT = {"", "DND ON - only priority notifications get through",
        "DND ON - notifications silenced, alarms only"};

    SharedPreferences p;
    FrameLayout f;
    V c;
    TextView t, dt, se, rs, dn, vw;
    View[] ui;
    Vibrator v;
    NotificationManager nm;
    VibrationEffect vt, vh, vr, vl;
    int[] cl;
    int n, mx, st, mode, ps, sh;
    boolean dnOn; // Do Not Disturb currently applied by us
    boolean hp;   // send vibrations as touch feedback
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
        nm = getSystemService(NotificationManager.class);

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

        // Do Not Disturb warning, just above the -1 and view buttons (not clickable)
        dt = new TextView(this);
        dt.setTextSize(12);
        dt.setTextColor(0xFFFFB74D);
        dt.setGravity(Gravity.CENTER);
        dt.setPadding(dp(24), dp(8), dp(24), dp(8));
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dl.bottomMargin = dp(112);
        f.addView(dt, dl);

        ui = new View[]{t, dt, se, rs, dn, vw};
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
            String[] a = g(5).trim().split("\\s+");
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

    // optionally sent as touch (haptic) feedback, which Do Not Disturb doesn't mute
    void z(VibrationEffect e) {
        if (e == null) return;
        if (!hp)
            v.vibrate(e);
        else if (Build.VERSION.SDK_INT >= 33)
            v.vibrate(e, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH));
        else
            v.vibrate(e, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build());
    }

    @Override protected void onResume() {
        super.onResume();
        mx = num(1);
        st = Math.max(1, num(2));
        c.sz = dp(Math.max(6, num(3)));
        ps = Math.max(0, num(4));
        hp = p.getBoolean("hp", false);
        t.setTextSize(Math.max(8, num(0)));
        cl = cs();
        c.cl = cl;
        vt = fx(6);
        vh = fx(7);
        vr = fx(8);
        vl = fx(9);
        if (p.getBoolean("aw", true))
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        undo(); // clean up after a crash, then (re)apply
        dnd();
        sync();
        draw();
        f.removeCallbacks(this);
        run();
    }

    @Override protected void onPause() {
        super.onPause();
        f.removeCallbacks(this);
        undo();
        p.edit().putInt("n", n).putInt("md", mode).apply();
    }

    // Do Not Disturb while the app is in front (opt-in, chosen in settings).
    // "ds" = filter to go back to, "da" = filter we applied; both live on disk so a
    // crash can be cleaned up on the next launch.
    void dnd() {
        int m = p.getInt("dn", 0);
        if (m == 0 || !nm.isNotificationPolicyAccessGranted()) return;
        int cur = nm.getCurrentInterruptionFilter();
        int want = m == 1 ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                : NotificationManager.INTERRUPTION_FILTER_ALARMS;
        p.edit().putInt("ds", cur == 0 ? 1 : cur).putInt("da", want).commit();
        nm.setInterruptionFilter(want);
    }

    // is our Do Not Disturb still the active one?
    void sync() {
        dnOn = p.contains("ds") && nm.getCurrentInterruptionFilter() == p.getInt("da", 0);
    }

    // restore the previous filter, but only if nobody changed it since we set it
    void undo() {
        if (p.contains("ds") && nm.isNotificationPolicyAccessGranted()
                && nm.getCurrentInterruptionFilter() == p.getInt("da", 0))
            nm.setInterruptionFilter(p.getInt("ds", 1));
        p.edit().remove("ds").remove("da").commit();
    }

    // burn-in protection: every 15 s nudge everything 1dp along a square loop
    // of side 2*ps dp (a full loop takes a few minutes)
    @Override public void run() {
        int l = Math.max(1, 2 * ps), k = sh++ % (4 * l), s = k / l, q = k % l;
        int x = s == 0 ? q - ps : s == 1 ? ps : s == 2 ? ps - q : -ps;
        int y = s == 0 ? -ps : s == 1 ? q - ps : s == 2 ? ps : ps - q;
        int d = dp(1);
        for (View w : ui) {
            w.setTranslationX(x * d);
            w.setTranslationY(y * d);
        }
        c.setTranslationX(x * d);
        c.setTranslationY(y * d);
        if (ps > 0) f.postDelayed(this, 15000);
    }

    // hide status + navigation bars; swipe from the edge shows them briefly
    @Override public void onWindowFocusChanged(boolean h) {
        super.onWindowFocusChanged(h);
        if (h) {
            f.setSystemUiVisibility(0x1706);
            if (cl != null) { // re-check DND, the user may have changed it from the shade
                sync();
                draw();
            }
        }
    }

    void draw() {
        int col = cl[(n / st) % cl.length];
        t.setText(Integer.toString(n));
        t.setTextColor(col);
        vw.setText("view: " + NM[mode]);
        dt.setText(DT[dnOn ? p.getInt("dn", 0) : 0]);
        for (View x : ui) x.setVisibility(cv ? View.INVISIBLE : View.VISIBLE);
        if (!dnOn) dt.setVisibility(View.INVISIBLE);
        c.mode = mode;
        c.n = n;
        c.setVisibility(cv && mode > 0 ? View.VISIBLE : View.INVISIBLE);
        c.invalidate();
    }

    // true when the max count is reached (dots / tally just start a new lap instead)
    boolean full() {
        return mx > 0 && n >= mx;
    }

    // true when the count just filled the screen exactly
    boolean lap() {
        int k = mode > 0 ? c.cap() : 0;
        return k > 0 && n % k == 0;
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
            z(full() || lap() ? vl : vt);
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

    // draws the dots / tally counting views. When the screen is full the next
    // marks overwrite the earliest ones in the next colour of the palette.
    static class V extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] cl;
        int mode, n, sz, cols, rows;
        float pw, ph; // grid pitch

        V(Context x) {
            super(x);
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        // marks that fit on screen (also sets the grid geometry)
        int cap() {
            float m = sz / 2f;
            pw = mode == 1 ? sz : sz * 2.1f;  // tally: one cell per group of five
            ph = mode == 1 ? sz : sz * 1.7f;
            cols = Math.max(0, (int) ((getWidth() - 2 * m) / pw));
            rows = Math.max(0, (int) ((getHeight() - 2 * m) / ph));
            return cols * rows * (mode == 1 ? 1 : 5);
        }

        @Override protected void onDraw(Canvas c) {
            int cap = cap();
            if (mode == 0 || cap == 0 || cl == null) return;
            float ox = (getWidth() - cols * pw) / 2f, oy = (getHeight() - rows * ph) / 2f;
            int lap = n / cap, pos = n % cap;
            p.setStrokeWidth(sz * .13f);
            for (int i = 0, e = Math.min(n, cap); i < e; i++) {
                // marks before pos belong to the current lap, the rest to the previous one
                p.setColor(cl[(i < pos ? lap : lap - 1) % cl.length]);
                if (mode == 1) {
                    c.drawCircle(ox + (i % cols + .5f) * pw, oy + (i / cols + .5f) * ph, sz * .3f, p);
                } else {
                    // four bars, the fifth mark is the diagonal strike
                    int g = i / 5, k = i % 5;
                    float gw = sz * 1.5f, bh = sz * 1.2f;
                    float x = ox + (g % cols) * pw + (pw - gw) / 2f;
                    float y = oy + (g / cols) * ph + (ph - bh) / 2f;
                    if (k < 4) {
                        float bx = x + gw * (.1f + .267f * k);
                        c.drawLine(bx, y, bx, y + bh, p);
                    } else {
                        c.drawLine(x - gw * .03f, y + bh * .8f, x + gw * 1.03f, y + bh * .2f, p);
                    }
                }
            }
        }
    }
}
