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
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M extends Activity implements View.OnClickListener, View.OnLongClickListener, View.OnTouchListener, Runnable {
    // {pref key, default, settings label}; indices 0..10 are used below and in S
    // (the first five are numeric fields)
    static final String[][] D = {
        {"fs", "96", "Font size (sp)"},
        {"mx", "0", "Count reached at (0 = off)"},
        {"st", "100", "Lap length (count, changes colour)"},
        {"sz", "24", "Dot / tally size (dp)"},
        {"ps", "6", "Pixel shift against burn-in (dp, 0 = off)"},
        {"cl", "FFFFFF 00E676 40C4FF FFD740 FF5252 E040FB", "Colours (hex, space separated)"},
        {"vt", "15", "Vibration: tap"},
        {"vh", "40 60 40", "Vibration: hold (panel / counting view)"},
        {"vr", "80", "Vibration: reset"},
        {"vl", "500 50 500", "Vibration: count reached"},
        {"vc", "50 50 50 50 50", "Vibration: lap"}};

    // counting views: 0 blackout, 1 dots, 2 tally
    static final String[] NM = {"blackout", "dots", "tally"};

    // Do Not Disturb warning above the bottom buttons: 0 = not shown, 1 = priority only, 2 = alarms only
    static final String[] DT = {"", "DND ON - only priority notifications get through",
        "DND ON - notifications silenced, alarms only"};

    // shown when a dhikr profile is finished (check mark, thumbs up, trophy, hands, watermelon, party, star, 100)
    static final String[] EM = {"\u2705", "\uD83D\uDC4D", "\uD83C\uDFC6", "\uD83D\uDE4C",
        "\uD83C\uDF49", "\uD83C\uDF89", "\uD83C\uDF1F", "\uD83D\uDCAF"};

    SharedPreferences p;
    FrameLayout f;
    LinearLayout lay, bot, dl;
    V c;
    Cf cf;
    TextView t, pg, dt, se, rs, dn, vw;
    View[] ui;
    Vibrator v;
    NotificationManager nm;
    VibrationEffect vt, vh, vr, vl, vc;
    int[] cl;
    int n, mx, st, mode, ps, sh, vk; // vk: volume keys 0 off, 1 both +1, 2 up +1 / down -1, 3 down +1 / up -1
    Pr pr;           // active dhikr profile, null = normal counter
    String pf = "";  // its name
    String em = "";  // emoji for the completed screen
    int gp;          // dhikr progress in taps
    int vi;          // chosen view of the slides (clamped per slide)
    float tx, ty;    // where the current touch started
    boolean sw;      // a swipe was recognised, the rest of the touch is swallowed
    int shown = -2;  // which dhikr text is on screen (-1 none, slide index, slide count = completed)
    boolean dnOn; // Do Not Disturb currently applied by us
    boolean hp;   // send vibrations as touch feedback
    boolean cv; // true = counting view shown, false = panel (number + buttons)

    // one view of a slide: its lines and the share of the text area each one gets
    static class Vw {
        String[] t;
        float[] w;
    }

    // a dhikr profile: slides with a repeat count and one or more views. c = counts,
    // v = views of each slide, cu = cumulative counts (cu[i] = taps before slide i),
    // tot = all taps.
    static class Pr {
        int[] c, cu;
        Vw[][] v;
        int tot;
    }

    // line prefix: [70] share, [_] or [*] the remaining share, [] hides the text
    static final Pattern LN = Pattern.compile("^\\[([0-9]{0,4}|_|\\*)\\]\\s*(.*)$");

    // Text format: a line "---33" starts a slide that is counted 33 times (0 skips it,
    // 1 shows it once); the lines below it, up to the next "---N" line (or a bare "---"),
    // are its text, see views(). Anything before the first "---N" line is ignored.
    // Returns null if no slide is left.
    static Pr parse(String s) {
        ArrayList<Integer> cs = new ArrayList<>();
        ArrayList<Vw[]> vs = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        int cnt = -1;
        boolean open = false;
        try {
            for (String ln : (s + "\n---0").split("\r?\n")) {
                String l = ln.trim();
                if (l.matches("-{2,}\\s*[0-9]+")) {
                    if (cnt > 0) {
                        cs.add(cnt);
                        vs.add(views(b.toString()));
                    }
                    cnt = Integer.parseInt(l.replaceAll("[^0-9]", ""));
                    if (cnt > 100000) return null;
                    b.setLength(0);
                    open = true;
                } else if (l.matches("-{2,}")) {
                    open = false; // a bare --- ends the text of the slide
                } else if (open) {
                    b.append(ln).append('\n');
                }
            }
        } catch (Exception e) {
            return null;
        }
        if (cs.isEmpty()) return null;
        Pr r = new Pr();
        int k = cs.size();
        r.c = new int[k];
        r.v = new Vw[k][];
        r.cu = new int[k + 1];
        for (int i = 0; i < k; i++) {
            r.c[i] = cs.get(i);
            r.v[i] = vs.get(i);
            r.cu[i + 1] = r.cu[i] + r.c[i];
        }
        r.tot = r.cu[k];
        return r;
    }

    // Slide text -> views. Every non-blank line is shown in every view, unless it has
    // alternatives written with "|" ("Arabic | translit | meaning"): then view 1 shows the
    // first part of it, view 2 the second one and so on (a line with fewer parts shows
    // nothing in the later views). A part can start with
    //   [70]   its share of the text area (each text is auto-fitted into its share)
    //   [_]    (or [*]) whatever is left of the 100
    //   [10]   alone: an empty spacer of that share
    //   []     hides the part
    // Parts without a prefix get the average of the numbered ones (equal if there are none).
    static Vw[] views(String block) {
        ArrayList<String[]> tx = new ArrayList<>();
        ArrayList<int[]> wt = new ArrayList<>();
        int nv = 1;
        for (String ln : block.split("\n")) {
            ArrayList<String> ps = new ArrayList<>();
            ArrayList<Integer> ws = new ArrayList<>();
            for (String part : ln.split("\\|")) {
                String l = part.trim();
                int w = -1;
                Matcher m = LN.matcher(l);
                if (m.matches()) {
                    String g = m.group(1);
                    l = m.group(2);
                    if (g.equals("_") || g.equals("*")) w = -2;
                    else if (g.isEmpty() || Integer.parseInt(g) == 0) continue;
                    else w = Integer.parseInt(g);
                } else if (l.isEmpty()) {
                    continue;
                }
                ps.add(l);
                ws.add(w);
            }
            if (ps.isEmpty()) continue;
            String[] pt = ps.toArray(new String[0]);
            int[] pw = new int[pt.length];
            for (int i = 0; i < pw.length; i++) pw[i] = ws.get(i);
            tx.add(pt);
            wt.add(pw);
            nv = Math.min(32, Math.max(nv, pt.length));
        }
        Vw[] out = new Vw[nv];
        for (int v = 0; v < nv; v++) {
            ArrayList<String> t = new ArrayList<>();
            ArrayList<Integer> w = new ArrayList<>();
            for (int i = 0; i < tx.size(); i++) {
                int j = tx.get(i).length == 1 ? 0 : v;
                if (j < tx.get(i).length) {
                    t.add(tx.get(i)[j]);
                    w.add(wt.get(i)[j]);
                }
            }
            out[v] = shares(t, w);
        }
        return out;
    }

    // weights -> final shares: unnumbered texts get the average of the numbered ones (50 if
    // there are none), [_] lines split what is left of 100 (at least 10 each)
    static Vw shares(ArrayList<String> t, ArrayList<Integer> w) {
        int sum = 0, k = 0, fills = 0;
        for (int i = 0; i < w.size(); i++) {
            if (w.get(i) > 0 && !t.get(i).isEmpty()) {
                sum += w.get(i);
                k++;
            }
            if (w.get(i) == -2) fills++;
        }
        int def = k == 0 ? 50 : Math.max(1, sum / k), total = 0;
        float[] r = new float[w.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = w.get(i) > 0 ? w.get(i) : w.get(i) == -1 ? def : 0;
            total += r[i];
        }
        for (int i = 0; i < r.length; i++)
            if (w.get(i) == -2) r[i] = Math.max(10, (100 - total) / fills);
        Vw o = new Vw();
        o.t = t.toArray(new String[0]);
        o.w = r;
        return o;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);
        p = getSharedPreferences("p", 0);
        n = p.getInt("n", 0);
        mode = p.getInt("md", 0);
        vi = p.getInt("vi", 0);
        cv = p.getBoolean("dk", false);
        v = getSystemService(Vibrator.class);
        nm = getSystemService(NotificationManager.class);

        // the whole screen is the tap / hold target; everything else is non-clickable
        f = new FrameLayout(this);
        f.setBackgroundColor(0xFF000000);
        f.setOnClickListener(this);
        f.setOnLongClickListener(this);
        f.setOnTouchListener(this);

        c = new V(this);
        f.addView(c, new FrameLayout.LayoutParams(-1, -1));

        // top half: dhikr text (only with a profile), bottom half: the number with the slide
        // progress under it. Without a profile the text is gone and the number is centered.
        lay = new LinearLayout(this);
        lay.setOrientation(LinearLayout.VERTICAL);
        dl = new LinearLayout(this);
        dl.setOrientation(LinearLayout.VERTICAL);
        dl.setPadding(dp(24), dp(96), dp(24), dp(8));
        lay.addView(dl, new LinearLayout.LayoutParams(-1, 0, .5f));
        bot = new LinearLayout(this);
        bot.setOrientation(LinearLayout.VERTICAL);
        bot.setGravity(Gravity.CENTER);
        t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        bot.addView(t, new LinearLayout.LayoutParams(-1, 0, 1f));
        pg = new TextView(this);
        pg.setTextSize(14);
        pg.setTextColor(0xFF888888);
        pg.setGravity(Gravity.CENTER);
        pg.setPadding(0, dp(4), 0, dp(24));
        bot.addView(pg, new LinearLayout.LayoutParams(-1, -2));
        lay.addView(bot, new LinearLayout.LayoutParams(-1, 0, .5f));
        f.addView(lay, new FrameLayout.LayoutParams(-1, -1));

        cf = new Cf(this);
        f.addView(cf, new FrameLayout.LayoutParams(-1, -1));

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

        ui = new View[]{lay, dt, se, rs, dn, vw};
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
        vk = p.getInt("vk", 0);
        cl = cs();
        cf.cl = cl;
        vt = fx(6);
        vh = fx(7);
        vr = fx(8);
        vl = fx(9);
        vc = fx(10);
        // dhikr profile (a missing or broken one falls back to the normal counter)
        pf = p.getString("pf", "");
        pr = null;
        if (pf.length() > 0) {
            String tx = getSharedPreferences("d", 0).getString(pf, null);
            pr = tx == null ? null : parse(tx);
            if (pr == null) pf = "";
            else gp = Math.min(p.getInt("g:" + pf, 0), pr.tot);
        }
        em = EM[new Random().nextInt(EM.length)];
        shown = -2;
        split(new String[0], new float[0]);
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
        SharedPreferences.Editor e = p.edit().putInt("n", n).putInt("md", mode).putInt("vi", vi);
        if (pr != null) e.putInt("g:" + pf, gp);
        e.apply();
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

    // index of the slide the dhikr progress is in (== slide count when finished)
    int slide() {
        int s = 0;
        while (s < pr.c.length && pr.cu[s + 1] <= gp) s++;
        return s;
    }

    // Normally the dhikr text and the number get half of the screen each. If a text would not
    // fit in its share at a comfortable size (28sp) the dhikr area gets up to 70% of the
    // screen, and the number, being just a number, shrinks a little along with its room.
    void split(String[] tx, float[] w) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float f = .5f, sum = 0, area = 0;
        for (float x : w) sum += x;
        TextPaint tp = new TextPaint();
        tp.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 28, dm));
        for (int i = 0; i < tx.length; i++) {
            if (tx[i].isEmpty()) continue;
            int h = StaticLayout.Builder.obtain(tx[i], 0, tx[i].length(), tp, dm.widthPixels - dp(48))
                    .build().getHeight();
            area = Math.max(area, h * sum / w[i]);
        }
        if (area > 0) f = Math.min(.7f, Math.max(.5f, (area + dp(104)) / dm.heightPixels));
        ((LinearLayout.LayoutParams) dl.getLayoutParams()).weight = f;
        ((LinearLayout.LayoutParams) bot.getLayoutParams()).weight = 1 - f;
        t.setAutoSizeTextTypeUniformWithConfiguration(6,
                Math.max(8, (int) (num(0) * (1 - f) * 2)), 2, TypedValue.COMPLEX_UNIT_SP);
        lay.requestLayout();
    }

    // one auto-fitting text per line, each in its share of the dhikr area
    void lines(String[] tx, float[] w) {
        dl.removeAllViews();
        for (int i = 0; i < tx.length; i++) {
            TextView x = new TextView(this);
            x.setGravity(Gravity.CENTER);
            x.setTextColor(0xFFEEEEEE);
            x.setAutoSizeTextTypeUniformWithConfiguration(10, 80, 2, TypedValue.COMPLEX_UNIT_SP);
            x.setText(tx[i]);
            dl.addView(x, new LinearLayout.LayoutParams(-1, 0, w[i]));
        }
        split(tx, w);
    }

    void draw() {
        int cnt, lap, ci;         // count shown / lap length / colour index
        String big, prog = "";    // the big text and the slide progress line
        int key = -1;             // which dhikr text is shown: slide * 64 + view
        String[] tx = new String[0];
        float[] tw = new float[0];
        if (pr == null) {
            cnt = n;
            lap = st;
            ci = n / st;
            big = Integer.toString(n);
        } else if (gp >= pr.tot) {
            int k = pr.c.length;
            cnt = 0;
            lap = 1;
            ci = k - 1;
            big = em;
            key = k * 64;
            tx = new String[]{pf + "\ncompleted"};
            tw = new float[]{1};
            prog = "all " + k + " slides done";
        } else {
            int s = slide(), nv = pr.v[s].length, ev = Math.min(vi, nv - 1);
            cnt = gp - pr.cu[s];
            lap = pr.c[s];
            ci = s;
            big = cnt + "/" + lap;
            key = s * 64 + ev;
            tx = pr.v[s][ev].t;
            tw = pr.v[s][ev].w;
            prog = "slide " + (s + 1) + " of " + pr.c.length + (nv > 1 ? "  -  view " + (ev + 1) + " of " + nv : "");
        }
        if (key != shown) { // only rebuild and re-fit the text when the slide or view changes
            shown = key;
            lines(tx, tw);
        }
        pg.setText(prog);
        pg.setVisibility(pr == null ? View.GONE : View.VISIBLE);
        dl.setVisibility(pr == null ? View.GONE : View.VISIBLE);
        int co = cl[ci % cl.length];
        t.setText(big);
        t.setTextColor(co);
        vw.setText("view: " + NM[mode]);
        dt.setText(DT[dnOn ? p.getInt("dn", 0) : 0]);
        for (View x : ui) x.setVisibility(cv ? View.INVISIBLE : View.VISIBLE);
        if (!dnOn) dt.setVisibility(View.INVISIBLE);
        cf.setVisibility(cv ? View.INVISIBLE : View.VISIBLE);
        c.mode = mode;
        c.n = cnt;
        c.st = lap;
        c.col = co;
        c.setVisibility(cv && mode > 0 ? View.VISIBLE : View.INVISIBLE);
        c.invalidate();
    }

    // Swipes (dhikr only; the rest of a recognised swipe never counts as a tap):
    // right = next slide (counts as done), left = previous slide, up / down = next / previous view
    @Override public boolean onTouch(View x, MotionEvent e) {
        if (pr == null) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tx = e.getX();
                ty = e.getY();
                sw = false;
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (sw) return true;
                float dx = e.getX() - tx, dy = e.getY() - ty;
                if (Math.max(Math.abs(dx), Math.abs(dy)) < dp(64)) return false;
                sw = true;
                swipe(dx, dy);
                e.setAction(MotionEvent.ACTION_CANCEL); // the view drops its pending tap / long press
                return false;
            }
            default:
                return sw;
        }
    }

    void swipe(float dx, float dy) {
        int s = slide(), k = pr.c.length;
        if (Math.abs(dx) > Math.abs(dy)) {
            if (dx > 0) {                       // next slide
                if (s >= k) return;
                gp = pr.cu[s + 1];
                if (gp >= pr.tot) {
                    em = EM[new Random().nextInt(EM.length)];
                    if (!cv) cf.go();
                    z(vl);
                } else {
                    z(vt);
                }
            } else {                            // previous slide, from its start
                gp = pr.cu[Math.max(0, s - 1)];
                cf.t0 = 0;
                z(vt);
            }
        } else {
            if (s >= k) return;
            int nv = pr.v[s].length;
            if (nv < 2) return;
            vi = (Math.min(vi, nv - 1) + (dy < 0 ? 1 : nv - 1)) % nv;
            z(vt);
        }
        draw();
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
        if (x == dn) dec();
        else inc();
    }

    // Normal counter: never stops. "Count reached" buzzes once when n hits the target,
    // every lap (multiple of st, which also changes the colour) buzzes, other taps are plain.
    // Dhikr: a finished slide buzzes like a lap (unless its count is 1), the last tap gives
    // the "count reached" buzz and the completed screen, after that taps do nothing.
    void inc() {
        if (pr != null) {
            if (gp >= pr.tot) return;
            int s = slide();
            gp++;
            boolean done = gp == pr.tot;
            z(done ? vl : gp == pr.cu[s + 1] && pr.c[s] > 1 ? vc : vt);
            if (done) {
                em = EM[new Random().nextInt(EM.length)];
                if (!cv) cf.go();
            }
        } else {
            n++;
            z(mx > 0 && n == mx ? vl : n % st == 0 ? vc : vt);
        }
        draw();
    }

    void dec() {
        if (pr != null) {
            if (gp > 0) gp--;
            cf.t0 = 0;
        } else if (n > 0) {
            n--;
        }
        z(vt);
        draw();
    }

    // optional volume-key counting; the keys are swallowed while it is on (no repeat while held)
    @Override public boolean onKeyDown(int k, KeyEvent e) {
        boolean up = k == KeyEvent.KEYCODE_VOLUME_UP;
        if (vk == 0 || !up && k != KeyEvent.KEYCODE_VOLUME_DOWN) return super.onKeyDown(k, e);
        if (e.getRepeatCount() == 0) {
            if (vk == 1 || vk == 2 && up || vk == 3 && !up) inc();
            else dec();
        }
        return true;
    }

    @Override public boolean onKeyUp(int k, KeyEvent e) {
        if (vk != 0 && (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN)) return true;
        return super.onKeyUp(k, e);
    }

    @Override public boolean onLongClick(View x) {
        if (x == rs) {
            if (pr != null) gp = 0;
            else n = 0;
            cf.t0 = 0;
            z(vr);
        } else {
            cv = !cv;
            z(vh);
        }
        draw();
        return true;
    }

    // draws the dots / tally counting views in colour col. Within a lap of st taps the
    // screen fills one mark per tap; once it is full, each tap removes the earliest
    // remaining mark, and when it is empty it fills again, until the next lap starts over.
    static class V extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int col, mode, n, st, sz, cols, rows;
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
            if (mode == 0 || cap == 0) return;
            float ox = (getWidth() - cols * pw) / 2f, oy = (getHeight() - rows * ph) / 2f;
            int q = n % st % (2 * cap);            // position inside the fill / drain cycle
            int lo = q <= cap ? 0 : q - cap;       // marks before lo have been removed
            int hi = Math.min(q, cap);             // marks from hi on are not drawn yet
            p.setStrokeWidth(sz * .13f);
            p.setColor(col);
            for (int i = lo; i < hi; i++) {
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

    // short confetti burst (about 4 seconds) when a dhikr profile is completed
    static class Cf extends View {
        final Paint p = new Paint();
        final float[] x = new float[60], y = new float[60], vx = new float[60], vy = new float[60];
        int[] cl;
        long t0; // start time, 0 = not running

        Cf(Context k) {
            super(k);
        }

        void go() {
            Random r = new Random();
            for (int i = 0; i < x.length; i++) {
                x[i] = r.nextFloat();
                y[i] = -r.nextFloat() * .4f;
                vx[i] = (r.nextFloat() - .5f) * .15f;
                vy[i] = .2f + r.nextFloat() * .3f;
            }
            t0 = SystemClock.uptimeMillis();
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            if (t0 == 0) return;
            float s = (SystemClock.uptimeMillis() - t0) / 1000f;
            if (s > 4) {
                t0 = 0;
                return;
            }
            float w = getWidth(), h = getHeight(), u = w * .025f;
            for (int i = 0; i < x.length; i++) {
                p.setColor(cl[i % cl.length]);
                float px = (x[i] + vx[i] * s) * w, py = (y[i] + vy[i] * s) * h;
                c.drawRect(px, py, px + u, py + u * 1.6f, p);
            }
            postInvalidateOnAnimation();
        }
    }
}
