package io.rfsj.weecounter;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.res.ColorStateList;
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

    static final String[] VN = {"OFF", "both buttons +1", "up +1, down -1", "down +1, up -1"};
    static final String[] VX = {
        "The volume buttons change the volume as usual.",
        "Both volume buttons add one to the count.",
        "Volume up adds one, volume down subtracts one.",
        "Volume down adds one, volume up subtracts one."};

    SharedPreferences p;
    EditText[] e = new EditText[M.D.length];
    CheckBox aw, dk, hp;
    TextView df, dd, dx, vv, vz;
    NotificationManager nm;
    int dm, vm;

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
        hp = cb("Vibrate as touch feedback");
        l.addView(hp);
        l.addView(tv("Only turn this on if Do Not Disturb mutes the vibrations on your phone. "
                + "They then follow your phone's touch feedback setting, so turning that off silences them.",
                0xFF888888, 0));
        vv = tv("", 0xFFFFFFFF, m);
        vv.setOnClickListener(this);
        l.addView(vv);
        vz = tv("", 0xFF888888, m / 4);
        l.addView(vz);
        for (int i = 0; i < e.length; i++) {
            if (i == 6)
                l.addView(tv("Vibration notation: milliseconds, alternating buzz and pause. "
                        + "\"40 60 40\" = buzz 40, pause 60, buzz 40. Empty = off.", 0xFF888888, m));
            l.addView(tv(M.D[i][2], 0xFFFFFFFF, m));
            e[i] = new EditText(this);
            e[i].setTextColor(0xFFFFFFFF);
            e[i].setSingleLine();
            if (i < 5) e[i].setInputType(2); // numbers only
            l.addView(e[i]);
        }
        aw = cb("Keep screen on");
        l.addView(aw);
        dk = cb("Start in counting view (not panel)");
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

    // white tick box so it stays visible on black when checked
    CheckBox cb(String s) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextColor(0xFFFFFFFF);
        c.setButtonTintList(ColorStateList.valueOf(0xFFFFFFFF));
        return c;
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
        hp.setChecked(!d && p.getBoolean("hp", false));
        dm = d ? 0 : p.getInt("dn", 0);
        vm = d ? 0 : p.getInt("vk", 0);
        dnd();
        vol();
    }

    void dnd() {
        dd.setText("Do Not Disturb while open: " + DN[dm] + "  (tap to change)");
        dx.setText(DX[dm]);
    }

    void vol() {
        vv.setText("Volume buttons: " + VN[vm] + "  (tap to change)");
        vz.setText(VX[vm]);
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
        } else if (x == vv) {
            vm = (vm + 1) % VN.length;
            vol();
        } else {
            fill(true);
        }
    }

    @Override protected void onPause() {
        super.onPause();
        SharedPreferences.Editor x = p.edit();
        for (int i = 0; i < e.length; i++)
            x.putString(M.D[i][0], e[i].getText().toString());
        x.putBoolean("aw", aw.isChecked()).putBoolean("dk", dk.isChecked()).putBoolean("hp", hp.isChecked())
                .putInt("dn", dm).putInt("vk", vm).apply();
    }
}
