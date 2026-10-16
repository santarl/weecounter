package io.rfsj.weecounter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class S extends Activity implements View.OnClickListener, View.OnLongClickListener {
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

    static final String REPO = "santarl/weecounter";

    SharedPreferences p;
    EditText[] e = new EditText[M.D.length];
    CheckBox aw, dk, hp, wk;
    TextView df, dd, dx, vv, vz, dr, rl, ver, lnk, chk, res;
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

        // about: version, project link and a manual update check
        String vn = "?";
        try {
            vn = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ex) {
            // keep the question mark
        }
        ver = tv("weecounter " + vn + (test() ? " (test build)" : ""), 0xFFFFFFFF, 0);
        ver.setTextSize(18);
        l.addView(ver);
        lnk = tv("github.com/" + REPO, 0xFF00E676, m / 4);
        lnk.setOnClickListener(this);
        l.addView(lnk);
        chk = tv("CHECK FOR UPDATE", 0xFF00E676, m);
        chk.setOnClickListener(this);
        l.addView(chk);
        res = tv("", 0xFF888888, 0);
        l.addView(res);

        dd = tv("", 0xFFFFFFFF, m * 2);
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
        dr = tv("", 0xFFFFFFFF, m);
        dr.setOnClickListener(this);
        l.addView(dr);
        l.addView(tv("Count a list of dhikr slides one after another, shown above the number. "
                + "Pick, import, export or delete profiles there.", 0xFF888888, m / 4));
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
        wk = cb("Check for new profiles weekly");
        l.addView(wk);

        df = tv("RESET TO DEFAULTS", 0xFFFF5252, m);
        df.setOnClickListener(this);
        df.setOnLongClickListener(this);
        l.addView(df);
        l.addView(tv("Hold to restore the defaults. Also forgets the text sizes you set by pinching on dhikr slides.", 0xFF888888, 0));
        rl = tv("RESET LAYOUT", 0xFFFF5252, m);
        rl.setOnClickListener(this);
        rl.setOnLongClickListener(this);
        l.addView(rl);
        l.addView(tv("Hold to put the menu, buttons, dhikr text, title, counter and footer back at their default places.", 0xFF888888, 0));

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
        wk.setChecked(d || p.getBoolean("wk", true));
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
        String k = p.getString("pf", "");
        dr.setText("Dhikr profile: " + (k.isEmpty() ? "Off" : k) + "  (tap to open)");
        if (dm != 0 && !nm.isNotificationPolicyAccessGranted()) {
            dm = 0;
            dnd();
        }
    }

    // forget the text sizes pinched on dhikr slides
    void clearZ() {
        SharedPreferences.Editor e = p.edit();
        for (String k : p.getAll().keySet()) if (k.startsWith("fz:")) e.remove(k);
        e.apply();
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
        } else if (x == dr) {
            startActivity(new Intent(this, P.class));
        } else if (x == lnk) {
            view("https://github.com/" + REPO);
        } else if (x == chk) {
            check();
        } else if (x == rl) {
            Toast.makeText(this, "Hold to reset the layout", Toast.LENGTH_SHORT).show();
        } else if (x == vv) {
            vm = (vm + 1) % VN.length;
            vol();
        } else if (x == df) {
            Toast.makeText(this, "Hold to reset to defaults", Toast.LENGTH_SHORT).show();
        }
    }

    boolean test() {
        return getPackageName().endsWith(".test");
    }

    void view(String u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
        } catch (Exception ex) {
            Toast.makeText(this, "No app found to open " + u, Toast.LENGTH_SHORT).show();
        }
    }

    HttpURLConnection conn(String u, String accept) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "weecounter");
        if (accept != null) c.setRequestProperty("Accept", accept);
        return c;
    }

    // Manual update check (the only network use of the app). Release builds: the latest GitHub
    // release against our version code (release N has version code N). Test builds: the latest
    // commit on main against the commit the build was made from (version name "test-<commit>").
    void check() {
        res.setText("Checking...");
        new Thread(() -> {
            String[] r = {"Could not check, is there a connection?", null, null}; // status, dialog text, link
            try {
                PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                if (test()) {
                    HttpURLConnection c = conn("https://api.github.com/repos/" + REPO + "/commits/main",
                            "application/vnd.github.sha");
                    String sha = new BufferedReader(new InputStreamReader(c.getInputStream())).readLine().trim();
                    String mine = pi.versionName.startsWith("test-") ? pi.versionName.substring(5) : "";
                    if (!mine.isEmpty() && sha.startsWith(mine)) {
                        r[0] = "This test build is made from the latest commit (" + mine + ")";
                    } else {
                        r[0] = "main has newer commits: " + sha.substring(0, 7) + (mine.isEmpty() ? "" : " (you have " + mine + ")");
                        r[1] = r[0] + ". Run the test build workflow and download its artifact from the Actions page.";
                        r[2] = "https://github.com/" + REPO + "/actions/workflows/test.yml";
                    }
                } else {
                    HttpURLConnection c = conn("https://github.com/" + REPO + "/releases/latest", null);
                    c.setInstanceFollowRedirects(false);
                    String loc = c.getHeaderField("Location"); // .../releases/tag/v7
                    int i = loc == null ? -1 : loc.lastIndexOf("/tag/v");
                    int latest = i < 0 ? -1 : Integer.parseInt(loc.substring(i + 6).trim());
                    if (latest < 0) {
                        r[0] = "No release found";
                    } else if (latest > pi.versionCode) {
                        r[0] = "New update available: v" + latest;
                        r[1] = "Version v" + latest + " is available, you have v" + pi.versionCode
                                + ". Open the APK download in your browser?";
                        r[2] = "https://github.com/" + REPO + "/releases/download/v" + latest + "/weecounter-v" + latest + ".apk";
                    } else {
                        r[0] = "You are on the latest version (v" + pi.versionCode + ")";
                    }
                }
            } catch (Exception ex) {
                // keep the default message
            }
            runOnUiThread(() -> show(r));
        }).start();
    }

    void show(String[] r) {
        if (isFinishing()) return;
        res.setText(r[0]);
        if (r[1] == null) return;
        final String u = r[2];
        new AlertDialog.Builder(this).setTitle("Update available").setMessage(r[1])
                .setPositiveButton(test() ? "Open" : "Download", (d, w) -> view(u))
                .setNegativeButton("Later", null).show();
    }

    @Override public boolean onLongClick(View x) {
        if (x == df) {
            fill(true);
            clearZ();
            Toast.makeText(this, "Defaults restored", Toast.LENGTH_SHORT).show();
            return true;
        }
        if (x != rl) return false;
        SharedPreferences.Editor e = p.edit();
        for (int i = 0; i < 9; i++) e.remove("el" + i);
        e.apply();
        Toast.makeText(this, "Layout reset", Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override protected void onPause() {
        super.onPause();
        SharedPreferences.Editor x = p.edit();
        for (int i = 0; i < e.length; i++)
            x.putString(M.D[i][0], e[i].getText().toString());
        x.putBoolean("aw", aw.isChecked()).putBoolean("dk", dk.isChecked()).putBoolean("hp", hp.isChecked()).putBoolean("wk", wk.isChecked())
                .putInt("dn", dm).putInt("vk", vm).apply();
    }
}
