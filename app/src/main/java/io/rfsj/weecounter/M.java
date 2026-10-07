package io.rfsj.weecounter;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

public class M extends Activity implements View.OnClickListener, View.OnLongClickListener {
    // {pref key, default, settings label}; indices 0..7 are used below and in S
    static final String[][] D = {
        {"fs", "96", "Font size (sp)"},
        {"mx", "0", "Max count (0 = no limit)"},
        {"st", "100", "Change colour every (count)"},
        {"cl", "FFFFFF 00E676 40C4FF FFD740 FF5252 E040FB", "Colours (hex, space separated)"},
        {"vt", "12", "Vibration: tap"},
        {"vh", "40 60 40", "Vibration: hold (show/hide UI)"},
        {"vr", "80", "Vibration: reset"},
        {"vl", "150 80 150", "Vibration: max reached"}};

    SharedPreferences p;
    FrameLayout f;
    TextView t, se, dn, rs;
    View[] ui;
    Vibrator v;
    VibrationEffect vt, vh, vr, vl;
    int[] cl;
    int n, mx, st;
    boolean dark;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);
        p = getSharedPreferences("p", 0);
        n = p.getInt("n", 0);
        dark = p.getBoolean("dk", false);
        v = getSystemService(Vibrator.class);

        // the whole screen is the tap / hold target; labels below are non-clickable
        f = new FrameLayout(this);
        f.setBackgroundColor(0xFF000000);
        f.setOnClickListener(this);
        f.setOnLongClickListener(this);

        t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        f.addView(t, new FrameLayout.LayoutParams(-1, -1));

        se = b("settings", 14, Gravity.TOP | Gravity.START);
        se.setOnClickListener(this);
        dn = b("-1", 28, Gravity.TOP | Gravity.END);
        dn.setOnClickListener(this);
        rs = b("HOLD TO RESET", 14, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        rs.setOnLongClickListener(this);

        ui = new View[]{t, se, dn, rs};
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
            String[] a = g(3).trim().split("\\s+");
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
        t.setTextSize(Math.max(8, num(0)));
        cl = cs();
        vt = fx(4);
        vh = fx(5);
        vr = fx(6);
        vl = fx(7);
        if (p.getBoolean("aw", true))
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        draw();
    }

    @Override protected void onPause() {
        super.onPause();
        p.edit().putInt("n", n).apply();
    }

    // hide status + navigation bars; swipe from the edge shows them briefly
    @Override public void onWindowFocusChanged(boolean h) {
        super.onWindowFocusChanged(h);
        if (h) f.setSystemUiVisibility(0x1706);
    }

    void draw() {
        t.setText(Integer.toString(n));
        t.setTextColor(cl[(n / st) % cl.length]);
        for (View x : ui) x.setVisibility(dark ? View.INVISIBLE : View.VISIBLE);
    }

    @Override public void onClick(View x) {
        if (x == se) {
            startActivity(new Intent(this, S.class));
            return;
        }
        if (x == dn) {
            if (n > 0) n--;
            z(vt);
        } else if (mx > 0 && n >= mx) {
            z(vl);
            return;
        } else {
            n++;
            z(mx > 0 && n >= mx ? vl : vt);
        }
        draw();
    }

    @Override public boolean onLongClick(View x) {
        if (x == rs) {
            n = 0;
            z(vr);
        } else {
            dark = !dark;
            z(vh);
        }
        draw();
        return true;
    }
}
