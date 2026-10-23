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
    Th th;          // colours
    boolean dflt;   // this screen shows the default theme (after you changed it here), not the one of the profile
    ScrollView sv;
    TextView tm;    // the default theme
    EditText[] e = new EditText[M.D.length];
    CheckBox aw, dk, hp, wk, fa;
    TextView df, dd, dx, vv, vz, dr, rl, ver, lnk, rep, chk, res;
    NotificationManager nm;
    int dm, vm;

    @Override public void onBackPressed() {
        if (!Sh.back(this)) super.onBackPressed(); // a dialog that is up closes first
    }

    @Override protected void onCreate(Bundle b) {
        SharedPreferences q = getSharedPreferences("p", 0);
        dflt = b != null && b.getBoolean("d");
        th = dflt ? new Th(this, q.getString("th", "0,0")) : Th.get(this, q, q.getString("pf", "")); // the theme of the profile this screen was opened from
        setTheme(th.theme());
        super.onCreate(b);
        th.bars(this);
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
        ver = tv("weecounter " + vn + (test() ? " (test build)" : ""), th.fg, 0);
        ver.setTextSize(18);
        l.addView(ver);
        lnk = tv("github.com/" + REPO, th.ac, m / 4);
        lnk.setOnClickListener(this);
        l.addView(lnk);
        rep = tv("REPORT A BUG OR REQUEST A FEATURE", th.ac, m);
        rep.setOnClickListener(this);
        l.addView(rep);
        chk = tv("CHECK FOR UPDATE", th.ac, m);
        chk.setOnClickListener(this);
        l.addView(chk);
        res = tv("", th.dim, 0);
        l.addView(res);

        tm = tv("", th.fg, m * 2);
        tm.setOnClickListener(this);
        tm.setOnLongClickListener(this);
        l.addView(tm);
        l.addView(tv("The default look of the whole app: a palette and its mode: dark, light, vibrant (a light one with a coloured background), neon (a dark one with glowing colours) or auto (follows the phone). It shows on this screen at once. Menu > Theme tries them on the main screen, for one profile or as the default.", th.dim, m / 4));

        dd = tv("", th.fg, m * 2);
        dd.setOnClickListener(this);
        l.addView(dd);
        dx = tv("", th.dim, m / 4);
        l.addView(dx);
        hp = cb("Vibrate as touch feedback");
        l.addView(hp);
        l.addView(tv("Only turn this on if Do Not Disturb mutes the vibrations on your phone. "
                + "They then follow your phone's touch feedback setting, so turning that off silences them.",
                th.dim, 0));
        vv = tv("", th.fg, m);
        vv.setOnClickListener(this);
        l.addView(vv);
        vz = tv("", th.dim, m / 4);
        l.addView(vz);
        dr = tv("", th.fg, m);
        dr.setOnClickListener(this);
        l.addView(dr);
        l.addView(tv("Count a list of dhikr slides one after another, shown above the number. "
                + "Pick, import, export or delete profiles there.", th.dim, m / 4));
        for (int i = 0; i < e.length; i++) {
            if (i == 6)
                l.addView(tv("Vibration notation: milliseconds, alternating buzz and pause. "
                        + "\"40 60 40\" = buzz 40, pause 60, buzz 40. Empty = off.", th.dim, m));
            if (i == 10)
                l.addView(tv("The floating counter is in the menu. 0 is the automatic size, a fifth bigger than it used to be.", th.dim, m));
            l.addView(tv(M.D[i][2], th.fg, m));
            e[i] = new EditText(this);
            e[i].setTextColor(th.fg);
            e[i].setSingleLine();
            if (i < 5 || i == 10) e[i].setInputType(2); // numbers only
            l.addView(e[i]);
        }
        aw = cb("Keep screen on");
        l.addView(aw);
        dk = cb("Start in counting view (not panel)");
        l.addView(dk);
        wk = cb("Check for new profiles weekly");
        l.addView(wk);
        fa = cb("Float the counter when the app is minimized");
        l.addView(fa);
        l.addView(tv("Needs \"Display over other apps\": the Floating counter item of the menu asks for it the first time.", th.dim, m));

        df = tv("RESET TO DEFAULTS", th.bad, m);
        df.setOnClickListener(this);
        df.setOnLongClickListener(this);
        l.addView(df);
        l.addView(tv("Hold to restore the defaults. Also forgets the text sizes you set by pinching on dhikr slides.", th.dim, 0));
        rl = tv("RESET LAYOUT", th.bad, m);
        rl.setOnClickListener(this);
        rl.setOnLongClickListener(this);
        l.addView(rl);
        l.addView(tv("Hold to put the menu, buttons, dhikr text, title, counter and footer back at their default places.", th.dim, 0));

        ScrollView s = new ScrollView(this);
        s.setBackgroundColor(th.bg);
        s.setFitsSystemWindows(true);
        s.addView(l);
        setContentView(s);
        sv = s;
        final int y = b != null ? b.getInt("y") : 0;
        if (y > 0) s.post(() -> s.scrollTo(0, y)); // where you were, after the screen was redone in a new theme
        fill(false);
        themeRow();
    }

    @Override protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        o.putBoolean("d", dflt);
        o.putInt("y", sv.getScrollY());
    }

    void themeRow() {
        int[] v = Th.parse(p.getString("th", "0,0"));
        tm.setText("Theme: " + new Th(this, v[0] + "," + v[1]).title() + ", " + Th.MODES[v[1]] + "  (tap: next palette, hold: next mode)");
    }

    // white tick box so it stays visible on black when checked
    CheckBox cb(String s) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextColor(th.fg);
        c.setButtonTintList(ColorStateList.valueOf(th.fg));
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
        fa.setChecked(d || p.getBoolean("fa", true));
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
        for (String k : p.getAll().keySet()) if (k.startsWith("fz:") || k.startsWith("fx:")) e.remove(k);
        e.apply();
    }

    @Override public void onClick(View x) {
        if (x == tm) {
            int[] v = Th.parse(p.getString("th", "0,0"));
            p.edit().putString("th", (v[0] + 1) % Th.NAMES.length + "," + v[1]).apply();
            dflt = true;
            recreate();
        } else if (x == dd) {
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
        } else if (x == rep) {
            view("https://github.com/" + REPO + "/issues"); // a GitHub account is needed to write there
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
        Sh.of(this, th).setTitle("Update available").setMessage(r[1])
                .setPositiveButton(test() ? "Open" : "Download", (d, w) -> view(u))
                .setNegativeButton("Later", null).show();
    }

    @Override public boolean onLongClick(View x) {
        if (x == tm) {
            int[] v = Th.parse(p.getString("th", "0,0"));
            p.edit().putString("th", v[0] + "," + Th.nextMode(v[1])).apply();
            dflt = true;
            recreate();
            return true;
        }
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
        x.putBoolean("aw", aw.isChecked()).putBoolean("dk", dk.isChecked()).putBoolean("hp", hp.isChecked()).putBoolean("wk", wk.isChecked()).putBoolean("fa", fa.isChecked())
                .putInt("dn", dm).putInt("vk", vm).apply();
    }
}
