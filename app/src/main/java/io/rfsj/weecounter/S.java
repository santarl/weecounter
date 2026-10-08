package io.rfsj.weecounter;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class S extends Activity implements View.OnClickListener {
    static final String[] DN = {"Off", "Priority only", "Alarms only"};
    static final String[] DX = {
        "Do Not Disturb is left alone.",
        "Turns on Do Not Disturb while this app is open. Only what your own Do Not Disturb rules allow "
            + "(starred contacts, repeat callers...) gets through. Your previous setting is restored when you leave.",
        "Turns on Do Not Disturb while this app is open. Everything is silenced, including calls, except "
            + "alarms. Your previous setting is restored when you leave."};

    SharedPreferences p;
    EditText[] e = new EditText[M.D.length];
    CheckBox aw, dk;
    TextView df, dd, dx;
    NotificationManager nm;
    int dm;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences("p", 0);
        nm = getSystemService(NotificationManager.class);
        int m = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(m, m, m, m);

        dd = tv("", 0xFFFFFFFF, m);
        dd.setOnClickListener(this);
        l.addView(dd);
        dx = tv("", 0xFF888888, m / 4);
        l.addView(dx);
        for (int i = 0; i < e.length; i++) {
            if (i == 6)
                l.addView(tv("Vibration notation: milliseconds, alternating buzz and pause. "
                        + "\"40 60 40\" = buzz 40, pause 60, buzz 40. Empty = off. "
                        + "Vibrations are sent as touch feedback, so they follow your phone's touch feedback setting.",
                        0xFF888888, m));
            l.addView(tv(M.D[i][2], 0xFFFFFFFF, m));
            e[i] = new EditText(this);
            e[i].setTextColor(0xFFFFFFFF);
            e[i].setSingleLine();
            if (i < 5) e[i].setInputType(2); // numbers only
            l.addView(e[i]);
        }
        aw = new CheckBox(this);
        aw.setText("Keep screen on");
        aw.setTextColor(0xFFFFFFFF);
        l.addView(aw);
        dk = new CheckBox(this);
        dk.setText("Start in counting view (not panel)");
        dk.setTextColor(0xFFFFFFFF);
        l.addView(dk);

        df = tv("RESET TO DEFAULTS", 0xFFFF5252, m);
        df.setOnClickListener(this);
        l.addView(df);

        ScrollView s = new ScrollView(this);
        s.setBackgroundColor(0xFF000000);
        s.setFitsSystemWindows(true);
        s.addView(l);
        setContentView(s);
        fill(false);
    }

    TextView tv(String s, int c, int m) {
        TextView x = new TextView(this);
        x.setText(s);
        x.setTextColor(c);
        x.setPadding(0, m, 0, 0);
        return x;
    }

    void fill(boolean d) {
        for (int i = 0; i < e.length; i++)
            e[i].setText(d ? M.D[i][1] : p.getString(M.D[i][0], M.D[i][1]));
        aw.setChecked(d || p.getBoolean("aw", true));
        dk.setChecked(!d && p.getBoolean("dk", false));
        dm = d ? 0 : p.getInt("dn", 0);
        dnd();
    }

    void dnd() {
        dd.setText("Do Not Disturb while open: " + DN[dm] + "  (tap to change)");
        dx.setText(DX[dm]);
    }

    // coming back from the system screen without granting access = Off
    @Override protected void onResume() {
        super.onResume();
        if (dm != 0 && !nm.isNotificationPolicyAccessGranted()) {
            dm = 0;
            dnd();
        }
    }

    @Override public void onClick(View x) {
        if (x == dd) {
            dm = (dm + 1) % DN.length;
            if (dm != 0 && !nm.isNotificationPolicyAccessGranted()) {
                try {
                    startActivity(new Intent("android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS"));
                } catch (Exception ex) {
                    dm = 0; // this phone has no such screen
                }
            }
            dnd();
        } else {
            fill(true);
        }
    }

    @Override protected void onPause() {
        super.onPause();
        SharedPreferences.Editor x = p.edit();
        for (int i = 0; i < e.length; i++)
            x.putString(M.D[i][0], e[i].getText().toString());
        x.putBoolean("aw", aw.isChecked()).putBoolean("dk", dk.isChecked()).putInt("dn", dm).apply();
    }
}
