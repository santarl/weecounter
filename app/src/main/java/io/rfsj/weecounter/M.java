package io.rfsj.weecounter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M extends Activity implements View.OnClickListener, View.OnLongClickListener, ScaleGestureDetector.OnScaleGestureListener, Runnable {
    // {pref key, default, settings label}; indices 0..10 are used below and in S
    // (the first five are numeric fields)
    static final String[][] D = {
        {"fs", "96", "Font size (sp)"},
        {"mx", "0", "Count reached at (0 = off)"},
        {"st", "100", "Lap length (count, changes colour)"},
        {"sz", "24", "Dot / tally / game size (dp)"},
        {"ps", "6", "Pixel shift against burn-in (dp, 0 = off)"},
        {"cl", "FFFFFF 00E676 40C4FF FFD740 FF5252 E040FB", "Colours (hex, space separated)"},
        {"vt", "15", "Vibration: tap"},
        {"vh", "40 60 40", "Vibration: hold (panel / counting view)"},
        {"vr", "80", "Vibration: reset"},
        {"vl", "500 50 500", "Vibration: count reached"},
        {"vc", "50 50 50 50 50", "Vibration: lap"}};

    // counting views: 0 blackout, 1 dots, 2 tally, 3 game (a self-playing snake)
    static final String[] NM = {"blackout", "dots", "tally", "game"};

    // Do Not Disturb warning above the bottom buttons: 0 = not shown, 1 = priority only, 2 = alarms only
    static final String[] DT = {"", "DND ON - only priority notifications get through",
        "DND ON - notifications silenced, alarms only"};

    // shown when a dhikr profile is finished (check mark, thumbs up, trophy, hands, watermelon, party, star, 100)
    static final String[] EM = {"\u2705", "\uD83D\uDC4D", "\uD83C\uDFC6", "\uD83D\uDE4C",
        "\uD83C\uDF49", "\uD83C\uDF89", "\uD83C\uDF1F", "\uD83D\uDCAF"};

    SharedPreferences p;
    FrameLayout f;
    FrameLayout lay;
    LinearLayout bot;
    TextView dl, ttl; // the dhikr text block and the slide title
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
    int[] cn = new int[0]; // dhikr: count done in every slide (kept when you move between slides)
    int cur;         // dhikr: current slide (== slide count: completed)
    boolean edit;    // layout editor on
    RectF[] er = new RectF[9]; // rectangles (px) of the editable elements, see def()
    View[] ev;       // their views: menu, reset, -1, view, dhikr text, counter, footer, DND warning, title
    Ed ed;           // the editor overlay
    int vi;          // chosen view of the slides (clamped per slide)
    long rt;         // time of the last tap on the completed screen
    int rn, rg;      // taps so far / taps needed (3 to 5, random) to start a finished profile over
    boolean rb;      // the restart is about to happen
    int shx, shy;    // current pixel-shift offset in px
    Runnable pend;   // slide change waiting for the slide-out animation
    Pd ind;          // view dots at the left edge
    ScaleGestureDetector sd; // pinch to resize the dhikr text of a slide / view
    float zs;        // text scale while pinching
    float spx;       // px per sp
    float bs;        // standard size (px, before scaling) of the shown dhikr text block
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
        String[] ti; // slide titles, for the slide list
        boolean[] ex; // slide has its own "# Title" line (only those show the title element)
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
        ArrayList<String> tis = new ArrayList<>();
        ArrayList<Boolean> exs = new ArrayList<>();
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
                        tis.add(title(b.toString(), cs.size() - 1));
                        exs.add(Pattern.compile("(?m)^\\s*#\\s*\\S").matcher(b).find());
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
        r.ti = new String[k];
        r.ex = new boolean[k];
        for (int i = 0; i < k; i++) {
            r.ti[i] = tis.get(i);
            r.ex[i] = exs.get(i);
            r.c[i] = cs.get(i);
            r.v[i] = vs.get(i);
            r.cu[i + 1] = r.cu[i] + r.c[i];
        }
        r.tot = r.cu[k];
        return r;
    }

    // Slide title for the slide list: a line "# Title" in the slide, else the start of its
    // first line (first alternative, without a [n] prefix), cut at 28 characters.
    static String title(String block, int idx) {
        String first = "";
        for (String ln : block.split("\n")) {
            String l = ln.trim();
            if (l.startsWith("#") && l.length() > 1) return l.substring(1).trim();
            if (first.isEmpty() && !l.isEmpty() && !l.startsWith("#")) first = l;
        }
        first = first.split("\\|")[0].replaceFirst("^\\[[0-9_*]*\\]\\s*", "").trim();
        if (first.isEmpty()) return "Slide " + (idx + 1);
        return first.length() > 28 ? first.substring(0, 28).trim() + "..." : first;
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
        String ti = ""; // the slide's own title: a text line equal to it is not shown again
        for (String ln : block.split("\n")) {
            String q = ln.trim();
            if (q.startsWith("#") && q.length() > 1) {
                ti = q.substring(1).trim();
                break;
            }
        }
        for (String ln : block.split("\n")) {
            if (ln.trim().startsWith("#")) continue; // a title line, see title()
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
                if (l.equals(ti)) continue;
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

        c = new V(this);
        f.addView(c, new FrameLayout.LayoutParams(-1, -1));

        // Every element sits at its own rectangle (see def() and place()), so all of them can be
        // moved and resized in the layout editor. Default, top to bottom: top bar buttons, the
        // dhikr text with the title line, the counter (20% of the screen), the Do Not Disturb
        // warning, the bottom buttons and the status footer.
        spx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1, getResources().getDisplayMetrics());
        sd = new ScaleGestureDetector(this, this);

        lay = new FrameLayout(this);
        dl = new TextView(this);          // the whole text of a view is one block
        dl.setGravity(Gravity.CENTER);
        dl.setTextColor(0xFFEEEEEE);
        dl.setPadding(dp(8), dp(8), dp(8), dp(8));
        dl.setOnClickListener(this);     // a tap still counts, hold toggles the counting view like anywhere else
        dl.setOnLongClickListener(this);
        lay.addView(dl);
        bot = new LinearLayout(this);
        bot.setOrientation(LinearLayout.VERTICAL);
        bot.setGravity(Gravity.CENTER);
        t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        bot.addView(t, new LinearLayout.LayoutParams(-1, 0, 1f));
        lay.addView(bot);
        f.addView(lay, new FrameLayout.LayoutParams(-1, -1));

        // one dot per view of the slide at the left edge, the current one lit
        ind = new Pd(this);
        f.addView(ind, new FrameLayout.LayoutParams(dp(24), dp(100), Gravity.TOP | Gravity.START));

        cf = new Cf(this);
        f.addView(cf, new FrameLayout.LayoutParams(-1, -1));

        se = b("menu");
        se.setOnClickListener(this);
        rs = b("reset");
        rs.setOnClickListener(this);      // a tap explains, hold resets
        rs.setOnLongClickListener(this);
        dn = b("-1");
        dn.setOnClickListener(this);
        vw = b("");
        vw.setOnClickListener(this);
        pg = new TextView(this);          // status footer: slide progress
        pg.setTextColor(0xFF888888);
        pg.setOnClickListener(this);      // a tap still counts, hold opens the slide list
        pg.setOnLongClickListener(this);
        f.addView(pg, new FrameLayout.LayoutParams(-2, -2));
        dt = new TextView(this);          // Do Not Disturb warning (not clickable)
        dt.setTextColor(0xFFFFB74D);
        f.addView(dt, new FrameLayout.LayoutParams(-2, -2));

        ttl = new TextView(this);         // slide title: an element of its own
        ttl.setTextColor(0xFF999999);
        f.addView(ttl, new FrameLayout.LayoutParams(-2, -2));

        ev = new View[]{se, rs, dn, vw, dl, bot, pg, dt, ttl};
        ui = new View[]{lay, dt, se, rs, dn, vw, ind, pg, ttl};

        ed = new Ed(this);
        ed.setVisibility(View.GONE);
        f.addView(ed, new FrameLayout.LayoutParams(-1, -1));
        setContentView(f);
    }

    // a button: its rectangle and text size are set in place1()
    TextView b(String s) {
        TextView x = new TextView(this);
        x.setText(s);
        x.setTextColor(0xFF666666);
        f.addView(x, new FrameLayout.LayoutParams(-2, -2));
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
        load();
        em = EM[new Random().nextInt(EM.length)];
        shown = -2;
        t.setAutoSizeTextTypeUniformWithConfiguration(6, Math.max(8, num(0)), 2, TypedValue.COMPLEX_UNIT_SP);
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
        land();
        undo();
        SharedPreferences.Editor e = p.edit().putInt("n", n).putInt("md", mode).putInt("vi", vi);
        if (pr != null) {
            StringBuilder sb = new StringBuilder();
            for (int c : cn) sb.append(c).append(',');
            e.putString("cn:" + pf, sb.toString()).putInt("cu:" + pf, cur);
        }
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
        shx = x * d;
        shy = y * d;
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

    // the slide being counted (== slide count when the profile is completed)
    int slide() {
        return pr == null ? 0 : Math.min(cur, pr.c.length);
    }

    // first slide at or after i that is not finished yet (slide count if there is none)
    int open(int i) {
        while (i < pr.c.length && cn[i] >= pr.c[i]) i++;
        return i;
    }

    // ---- layout: every element has a rectangle (px) that the layout editor can change ----

    static final String[] LB = {"menu", "reset", "-1", "view: blackout"}; // widest labels, for the default widths

    float tw(int i) {
        Paint q = new Paint();
        q.setTextSize(16 * spx);
        return q.measureText(LB[i]) + dp(40);
    }

    // default rectangle of element i: 0 menu, 1 reset, 2 -1, 3 view, 4 dhikr text, 5 counter,
    // 6 footer, 7 Do Not Disturb warning, 8 slide title. Top to bottom: top bar, dhikr text with the title
    // line, counter (20% of the screen), warning, bottom buttons, footer.
    RectF def(int i) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float W = dm.widthPixels, H = dm.heightPixels, c0 = H - dp(124) - H * .2f;
        switch (i) {
            case 0: return new RectF(0, 0, tw(0), dp(41));
            case 1: return new RectF(W - tw(1), 0, W, dp(41));
            case 2: return new RectF(0, H - dp(86), tw(2), H - dp(45));
            case 3: return new RectF(W - tw(3), H - dp(86), W, H - dp(45));
            case 4: return new RectF(0, dp(44), W, c0 - dp(36));
            case 5: return new RectF(0, c0, W, H - dp(124));
            case 6: return new RectF(W / 2 - dp(100), H - dp(36), W / 2 + dp(100), H - dp(8));
            case 7: return new RectF(0, H - dp(124), W, H - dp(88));
            default: return new RectF(0, c0 - dp(36), W, c0);
        }
    }

    // saved rectangle of element i (stored as fractions of the screen), else its default
    RectF rect(int i) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float W = dm.widthPixels, H = dm.heightPixels;
        if (i == 5 && pr == null && !edit) return new RectF(0, 0, W, H); // normal counter: the whole screen
        try {
            String[] a = p.getString("el" + i, "").split(",");
            return new RectF(Float.parseFloat(a[0]) * W, Float.parseFloat(a[1]) * H,
                    Float.parseFloat(a[2]) * W, Float.parseFloat(a[3]) * H);
        } catch (Exception e) {
            return def(i);
        }
    }

    // puts element i at its rectangle; text sizes follow the height of the rectangle
    void place1(int i) {
        float H = getResources().getDisplayMetrics().heightPixels;
        RectF r = er[i];
        View x = ev[i];
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) r.width(), (int) r.height(),
                Gravity.TOP | Gravity.START);
        lp.leftMargin = (int) r.left;
        lp.topMargin = (int) r.top;
        if (i == 2 || i == 3) { // the bottom buttons also react below their text, down to the screen edge
            lp.height = (int) (H - r.top);
            x.setPadding(0, 0, 0, (int) (H - r.bottom));
        }
        if (i < 4 || i > 5) {
            TextView tv = (TextView) x;
            tv.setGravity(Gravity.CENTER);
            float sz = (i < 4 ? 16 : i == 8 ? 14 : 12) * spx * r.height() / def(i).height();
            if (i == 8) // the title shrinks to fit its box and grows with the box
                tv.setAutoSizeTextTypeUniformWithConfiguration((int) (6 * spx),
                        (int) Math.max(6 * spx + 1, sz), 1, TypedValue.COMPLEX_UNIT_PX);
            else
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, sz);
        }
        x.setLayoutParams(lp);
        if (i == 4) { // the view dots follow the dhikr text
            FrameLayout.LayoutParams ip = (FrameLayout.LayoutParams) ind.getLayoutParams();
            ip.topMargin = (int) r.top;
            ip.height = (int) r.height();
            ind.setLayoutParams(ip);
        }
    }

    void place() {
        for (int i = 0; i < er.length; i++) {
            er[i] = rect(i);
            place1(i);
        }
    }

    // keeps a rectangle on the screen and at least a finger wide
    void fix(int i, boolean move) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float W = dm.widthPixels, H = dm.heightPixels, mn = dp(32);
        RectF r = er[i];
        if (r.width() < mn) {
            r.left = r.centerX() - mn / 2;
            r.right = r.left + mn;
        }
        if (r.height() < mn) {
            r.top = r.centerY() - mn / 2;
            r.bottom = r.top + mn;
        }
        if (move) {
            r.offset(Math.max(0, -r.left) - Math.max(0, r.right - W), Math.max(0, -r.top) - Math.max(0, r.bottom - H));
        } else {
            r.left = Math.max(0, r.left);
            r.top = Math.max(0, r.top);
            r.right = Math.min(W, r.right);
            r.bottom = Math.min(H, r.bottom);
        }
    }

    // remembers the rectangle of element i for good and lays everything out again
    void save(int i) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float W = dm.widthPixels, H = dm.heightPixels;
        RectF r = er[i];
        p.edit().putString("el" + i, r.left / W + "," + r.top / H + "," + r.right / W + "," + r.bottom / H).apply();
        shown = -2;
        draw();
    }

    void enterEdit() {
        edit = true;
        cv = false;
        ed.sel = -1;
        shown = -2;
        draw();
        ed.setVisibility(View.VISIBLE);
        ed.invalidate();
        Toast.makeText(this, "Tap an element to select it", Toast.LENGTH_SHORT).show();
    }

    void exitEdit() {
        edit = false;
        ed.setVisibility(View.GONE);
        shown = -2;
        draw();
    }

    void resetLayout() {
        SharedPreferences.Editor e = p.edit();
        for (int i = 0; i < er.length; i++) e.remove("el" + i);
        e.apply();
        shown = -2;
        draw();
    }

    @Override public void onBackPressed() {
        if (edit) exitEdit();
        else super.onBackPressed();
    }

    // The dhikr text of a view is ONE block with a relative size per line ([n] prefixes). Hard
    // line breaks are kept while the block still gets a size within 15% of the one it gets with
    // the lines run together; otherwise the lines are joined with the glyph \u21B5 and wrap freely.
    // The size is the largest that fits the rectangle, at most 34sp, times the scale the user
    // pinched for this slide and view (as far as it fits).
    void lines(String[] tx, float[] w, float z) {
        RectF r = er[4];
        int W = (int) r.width() - dp(16), last = -1;
        float area = r.height() - dp(16), cap = 34 * spx, wmax = 1;
        for (float x : w) wmax = Math.max(wmax, x);
        for (int i = 0; i < tx.length; i++) if (!tx[i].isEmpty()) last = i;
        SpannableStringBuilder brk = new SpannableStringBuilder(), flo = new SpannableStringBuilder();
        for (int i = 0; i < tx.length; i++) {
            if (i > 0) brk.append('\n');
            int a = brk.length(), b = flo.length();
            brk.append(tx[i].isEmpty() ? " " : tx[i]);
            if (!tx[i].isEmpty()) {
                flo.append(tx[i]);
                if (i < last) flo.append(" \u21B5 ");
            }
            if (w[i] < wmax) {
                RelativeSizeSpan sp = new RelativeSizeSpan(w[i] / wmax);
                brk.setSpan(sp, a, brk.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (flo.length() > b)
                    flo.setSpan(new RelativeSizeSpan(w[i] / wmax), b, flo.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        float sb = fit(brk, W, area, cap), sf = fit(flo, W, area, cap);
        boolean keep = tx.length < 2 || sb >= sf * .85f;
        CharSequence use = keep ? brk : flo;
        bs = keep ? sb : sf;
        dl.setText(use);
        dl.setTextSize(TypedValue.COMPLEX_UNIT_PX, fit(use, W, area, bs * z));
        lay.requestLayout();
    }

    // largest size (px, at most max) at which s, wrapped to w px, stays within h px (3% spare)
    float fit(CharSequence s, int w, float h, float max) {
        TextPaint tp = new TextPaint();
        float lo = 8 * spx, hi = Math.max(lo, max);
        tp.setTextSize(hi);
        if (height(s, tp, w) <= h * .97f) return hi;
        for (int k = 0; k < 8; k++) {
            float mid = (lo + hi) / 2;
            tp.setTextSize(mid);
            if (height(s, tp, w) <= h * .97f) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    int height(CharSequence s, TextPaint tp, int w) {
        return StaticLayout.Builder.obtain(s, 0, s.length(), tp, w).build().getHeight();
    }

    void draw() {
        int cnt, lap, ci;         // count shown / lap length / colour index
        String big, prog = "", title = ""; // the big text, the slide progress line and the slide title
        int key = -1;             // which dhikr text is shown: slide * 64 + view
        String[] tx = new String[0];
        int vn = 1, ve = 0;       // views of this slide / the current one
        float zz = 1;             // text scale the user pinched for this slide and view
        float[] tw = new float[0];
        boolean demo = edit && pr == null; // the layout editor shows a sample dhikr panel
        if (demo) {
            cnt = 12;
            lap = 33;
            ci = 0;
            big = "12/33";
            key = -3;
            tx = new String[]{"Dhikr text"};
            tw = new float[]{1};
            title = "Slide title";
            prog = "slide 2 of 4";
        } else if (pr == null) {
            cnt = n;
            lap = st;
            ci = n / st;
            big = Integer.toString(n);
        } else if (cur >= pr.c.length) {
            int k = pr.c.length;
            cnt = 0;
            lap = 1;
            ci = k - 1;
            big = em;
            key = k * 64;
            tx = new String[]{"completed"};
            tw = new float[]{1};
            title = pf;
            prog = "all " + k + " slides done";
        } else {
            int s = cur, nv = pr.v[s].length, ev = Math.min(vi, nv - 1);
            cnt = cn[s];
            lap = pr.c[s];
            ci = s;
            big = lap > 1 ? cnt + "/" + lap : cnt > 1 ? "\u25CF\u00D7" + cnt : cnt > 0 ? "\u25CF" : "\u25CB"; // a slide counted once: ring, filled when done
            key = s * 64 + ev;
            tx = pr.v[s][ev].t;
            tw = pr.v[s][ev].w;
            title = pr.ex[s] ? pr.ti[s] : ""; // the fallback title (first text line) is only for the slide list
            vn = nv;
            ve = ev;
            zz = p.getFloat("fz:" + pf + ":" + s + ":" + ev, 1f);
            prog = "slide " + (s + 1) + " of " + pr.c.length;
        }
        if (key != shown) { // only rebuild and re-fit the text when the slide or view changes
            shown = key;
            place();
            lines(tx, tw, zz);
        }
        boolean dk = pr != null || demo;
        pg.setText(prog);
        ttl.setText(title);
        dl.setVisibility(dk ? View.VISIBLE : View.GONE);
        int co = cl[ci % cl.length];
        t.setText(big);
        t.setTextColor(co);
        vw.setText("view: " + NM[mode]);
        dt.setText(DT[dnOn ? p.getInt("dn", 0) : (edit ? 1 : 0)]);
        for (View x : ui) x.setVisibility(cv ? View.INVISIBLE : View.VISIBLE);
        if (!dnOn && !edit) dt.setVisibility(View.INVISIBLE);
        if (vn < 2) ind.setVisibility(View.INVISIBLE);
        if (!dk) {
            pg.setVisibility(View.INVISIBLE);
            ttl.setVisibility(View.INVISIBLE);
        }
        ind.n = vn;
        ind.cur = ve;
        ind.col = co;
        ind.invalidate();
        cf.setVisibility(cv ? View.INVISIBLE : View.VISIBLE);
        c.mode = mode;
        c.n = cnt;
        c.st = lap;
        c.col = co;
        c.setVisibility(cv && mode > 0 ? View.VISIBLE : View.INVISIBLE);
        c.invalidate();
    }

    // (re)loads the active dhikr profile; a missing or broken one falls back to the normal counter
    void load() {
        pf = p.getString("pf", "");
        pr = null;
        if (pf.length() > 0) {
            String tx = getSharedPreferences("d", 0).getString(pf, null);
            pr = tx == null ? null : parse(tx);
            if (pr == null) {
                pf = "";
            } else {
                int k = pr.c.length;
                cn = new int[k];
                String sv = p.getString("cn:" + pf, null);
                if (sv != null) {
                    try {
                        String[] a = sv.split(",");
                        for (int i = 0; i < k && i < a.length; i++)
                            cn[i] = Math.max(0, Math.min(1000000, Integer.parseInt(a[i])));
                    } catch (Exception e) {
                        Arrays.fill(cn, 0);
                    }
                    cur = Math.max(0, Math.min(k, p.getInt("cu:" + pf, 0)));
                } else { // progress saved by an older version: one position counted in taps
                    int g0 = 0;
                    try {
                        g0 = p.getInt("g:" + pf, 0);
                    } catch (Exception e) {
                        // none
                    }
                    for (int i = 0; i < k; i++) cn[i] = Math.max(0, Math.min(pr.c[i], g0 - pr.cu[i]));
                    cur = open(0);
                }
            }
        }
    }

    // Swipes (dhikr only): left = next slide (counts as done), right = previous slide,
    // up / down = next / previous view. A recognised swipe never counts as a tap or a hold.
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (pr != null && !edit) {
            sd.onTouchEvent(e);
            if (e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN && !sw) {
                sw = true; // a second finger is a pinch: no tap, hold or swipe
                e.setAction(MotionEvent.ACTION_CANCEL);
                return super.dispatchTouchEvent(e);
            }
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    tx = e.getX();
                    ty = e.getY();
                    sw = false;
                    break;
                case MotionEvent.ACTION_MOVE: {
                    if (sw) return true;
                    float dx = e.getX() - tx, dy = e.getY() - ty;
                    if (Math.max(Math.abs(dx), Math.abs(dy)) < dp(64)) break;
                    sw = true;
                    swipe(dx, dy);
                    e.setAction(MotionEvent.ACTION_CANCEL); // views drop their pending tap / hold
                    break;
                }
                default:
                    if (sw) return true;
            }
        }
        return super.dispatchTouchEvent(e);
    }

    void swipe(float dx, float dy) {
        boolean h = Math.abs(dx) > Math.abs(dy);
        final int t = h ? (dx < 0 ? 0 : 1) : (dy < 0 ? 2 : 3); // 0 next slide, 1 previous slide, 2 next view, 3 previous view
        int s = slide(), k = pr.c.length;
        if (t == 0 && s >= k || t > 1 && (s >= k || pr.v[s].length < 2)) return;
        z(t == 0 && s + 1 >= k ? vl : vt);
        land();
        Runnable r = () -> {
            apply(t);
            draw();
        };
        if (cv) r.run();
        else if (h) flip(dx < 0 ? -1 : 1, 0, r);
        else flipV(dy < 0 ? -1 : 1, r);
    }

    // slide changes keep the counts of every slide
    void apply(int t) {
        int s = slide(), k = pr.c.length;
        rt = 0;
        if (t == 1) {                       // previous slide
            cur = Math.max(0, s - 1);
            cf.t0 = 0;
        } else if (s < k && t == 0) {       // next slide; past the last one the profile is completed
            cur = s + 1;
            if (cur >= k) {
                em = EM[new Random().nextInt(EM.length)];
                if (!cv) cf.go();
            }
        } else if (s < k) {                 // next / previous view
            int nv = pr.v[s].length;
            vi = (Math.min(vi, nv - 1) + (t == 2 ? 1 : nv - 1)) % nv;
        }
    }

    // Slides the panel out the way the finger went (fx / fy = -1, 0 or 1), applies the change
    // and slides the new content in from the opposite side.
    void flip(float fx, float fy, Runnable change) {
        final float w = lay.getWidth() * fx, h = lay.getHeight() * .4f * fy;
        pend = change;
        lay.animate().translationX(shx + w).translationY(shy + h).alpha(0).setDuration(100)
                .withEndAction(() -> {
                    Runnable r = pend;
                    pend = null;
                    if (r != null) r.run();
                    lay.setTranslationX(shx - w);
                    lay.setTranslationY(shy - h);
                    lay.animate().translationX(shx).translationY(shy).alpha(1).setDuration(140);
                });
    }

    // finishes a running slide animation right away, including its pending change
    void land() {
        lay.animate().cancel();
        dl.animate().cancel();
        Runnable r = pend;
        pend = null;
        if (r != null) r.run();
        lay.setAlpha(1);
        lay.setTranslationX(shx);
        lay.setTranslationY(shy);
        dl.setTranslationY(0);
        dl.setAlpha(1);
    }

    // Vertical swipe: only the dhikr text block moves; the title, the number and the footer
    // stay where they are.
    void flipV(float fy, Runnable change) {
        final float h = dl.getHeight() * .3f * fy;
        pend = change;
        dl.animate().translationY(h).alpha(0).setDuration(100).withEndAction(() -> {
            Runnable r = pend;
            pend = null;
            if (r != null) r.run();
            dl.setTranslationY(-h);
            dl.setAlpha(0);
            dl.animate().translationY(0).alpha(1).setDuration(140);
        });
    }

    // hold on the footer (slide x of y): list of the slides to jump to; the title is the profile name,
    // tap it to pick another profile
    void picker() {
        if (pr == null) return;
        int at = slide(), k = pr.c.length;
        String[] it = new String[k];
        for (int i = 0; i < k; i++)
            it[i] = (i == at ? "\u25B6 " : "    ") + (i + 1) + ". " + pr.ti[i]
                    + (pr.c[i] > 1 ? "  " + cn[i] + "/" + pr.c[i] : cn[i] > 0 ? "  \u2713" : "");
        TextView h = new TextView(this);
        h.setText(pf);
        h.setTextSize(18);
        h.setTextColor(0xFFFFFFFF);
        h.setPadding(dp(24), dp(20), dp(24), dp(8));
        final AlertDialog d = new AlertDialog.Builder(this).setCustomTitle(h).setItems(it, (dg, i) -> {
            land();
            cur = i;
            cf.t0 = 0;
            z(vt);
            draw();
        }).create();
        d.show();
        d.getListView().setSelection(Math.max(0, at - 2));
    }

    // top-left menu
    void menu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "Settings");
        m.getMenu().add(0, 2, 1, "Profiles");
        m.getMenu().add(0, 3, 2, "Edit layout");
        m.setOnMenuItemClickListener(i -> {
            int id = i.getItemId();
            if (id == 3) enterEdit();
            else startActivity(new Intent(this, id == 1 ? S.class : P.class));
            return true;
        });
        m.show();
    }

    // Pinch (dhikr panel only): resizes the text of the current slide and view; the scale is
    // kept for good and only forgotten by "reset to defaults" in the settings.
    String zk() {
        int s = slide();
        return "fz:" + pf + ":" + s + ":" + Math.min(vi, pr.v[s].length - 1);
    }

    @Override public boolean onScaleBegin(ScaleGestureDetector d) {
        if (pr == null || cv || slide() >= pr.c.length) return false;
        zs = p.getFloat(zk(), 1f);
        return true;
    }

    @Override public boolean onScale(ScaleGestureDetector d) {
        zs = Math.max(.4f, Math.min(2.5f, zs * d.getScaleFactor()));
        dl.setTextSize(TypedValue.COMPLEX_UNIT_PX, bs * zs);
        return true;
    }

    @Override public void onScaleEnd(ScaleGestureDetector d) {
        if (pr == null || slide() >= pr.c.length) return;
        p.edit().putFloat(zk(), zs).apply();
        shown = -2; // lay everything out again with the new scale
        draw();
    }

    @Override public void onClick(View x) {
        if (x == se) {
            menu(x);
            return;
        }
        if (x == rs) {
            Toast.makeText(this, pr != null ? "Hold the reset button to start the profile over"
                    : "Hold the reset button to set the count back to zero", Toast.LENGTH_SHORT).show();
            return;
        }
        if (x == vw) {
            mode = (mode + 1) % 4;
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
    // the "count reached" buzz and the completed screen, where a double tap starts over.
    void inc() {
        if (edit || rb) return;
        c.tap();
        if (pr != null) {
            int k = pr.c.length;
            if (cur >= k) { // completed: 3 to 5 taps start over
                again();
                return;
            }
            rt = 0;
            int c0 = pr.c[cur];
            cn[cur]++; // counting past the target is allowed (40/33); the slide moves on once, at the target
            boolean fin = cn[cur] == c0;
            int nx = fin ? open(cur + 1) : cur;
            boolean done = fin && nx >= k;
            z(done ? vl : c0 > 1 && cn[cur] % c0 == 0 ? vc : vt);
            cur = nx;
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

    // Completed screen: a random 3 to 5 taps (counted as one series, at most 4 s apart) start the
    // profile over. Every tap buzzes and shakes the emoji; the last, stronger shake runs before the
    // counts are cleared. Nothing else clears the counts of a finished profile.
    void again() {
        long now = SystemClock.uptimeMillis();
        if (rt == 0 || now - rt > 4000) {
            rn = 0;
            rg = 3 + new Random().nextInt(3);
            Toast.makeText(this, "Keep tapping to start over", Toast.LENGTH_SHORT).show();
        }
        rt = now;
        boolean last = ++rn >= rg;
        z(last ? vr : vt);
        shake(last ? 2f : 1f);
        if (last) {
            rb = true;
            f.postDelayed(() -> {
                rb = false;
                rt = 0;
                cur = 0;
                Arrays.fill(cn, 0);
                cf.t0 = 0;
                draw();
            }, 280);
        }
    }

    // a quick random shake of the emoji
    void shake(float k) {
        Random r = new Random();
        float u = dp(1) * k;
        t.animate().cancel();
        t.animate().translationX((r.nextFloat() - .5f) * 28 * u).translationY((r.nextFloat() - .5f) * 20 * u)
                .rotation((r.nextFloat() - .5f) * 24 * k).setDuration(50)
                .withEndAction(() -> t.animate().translationX(0).translationY(0).rotation(0).setDuration(120));
    }

    void dec() {
        if (edit) return;
        rt = 0;
        if (pr != null) {
            int k = pr.c.length;
            if (cur >= k) cur = k - 1;                 // from the completed screen back into the last slide
            else if (cn[cur] == 0 && cur > 0) cur--;   // undo the last tap of the previous slide
            if (cn[cur] > 0) cn[cur]--;
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
        if (edit || vk == 0 || !up && k != KeyEvent.KEYCODE_VOLUME_DOWN) return super.onKeyDown(k, e);
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
        if (x == pg) {
            z(vh);
            picker();
            return true;
        }
        if (x == rs) {
            if (pr != null) {
                cur = 0;
                Arrays.fill(cn, 0);
            } else {
                n = 0;
            }
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
            pw = mode != 2 ? sz : sz * 2.1f;  // tally: one cell per group of five
            ph = mode != 2 ? sz : sz * 1.7f;
            cols = Math.max(0, (int) ((getWidth() - 2 * m) / pw));
            rows = Math.max(0, (int) ((getHeight() - 2 * m) / ph));
            return cols * rows * (mode != 2 ? 1 : 5);
        }

        // "game" view: a snake plays itself. Every tap drops an apple, it goes for the nearest
        // one (not always, and not by the shortest way, so it wanders a bit), eats and grows a
        // block. It only steps where its own tail can still be reached, so it never traps itself.
        final ArrayList<Integer> ap = new ArrayList<>();
        final Random rnd = new Random();
        final Runnable tk = () -> invalidate();
        final int[] DX = {1, 0, -1, 0}, DY = {0, 1, 0, -1};
        int[] sn; // cells (y * cols + x), head first
        int len, dir, gc;
        long t0;

        void tap() {
            if (mode != 3 || sn == null || !isShown() || ap.size() > 19) return;
            for (int i = 0; i < 30; i++) {
                int a = rnd.nextInt(sn.length);
                if (!ap.contains(a) && !hit(a)) {
                    ap.add(a);
                    return;
                }
            }
        }

        boolean hit(int a) {
            for (int i = 0; i < len; i++) if (sn[i] == a) return true;
            return false;
        }

        int at(int x, int y) {
            return x < 0 || y < 0 || x >= cols || y >= rows ? -1 : y * cols + x;
        }

        // free cells reachable from s, plus a big bonus if the tail t is among them
        int room(boolean[] oc, int s, int t) {
            boolean[] v = oc.clone();
            int[] st = new int[v.length];
            int k = 0, r = s == t ? 1 << 20 : 0;
            st[k++] = s;
            v[s] = true;
            while (k > 0) {
                int q = st[--k];
                r++;
                for (int d = 0; d < 4; d++) {
                    int c = at(q % cols + DX[d], q / cols + DY[d]);
                    if (c >= 0 && !v[c]) {
                        v[c] = true;
                        st[k++] = c;
                        if (c == t) r += 1 << 20;
                    }
                }
            }
            return r;
        }

        void step() {
            boolean[] oc = new boolean[sn.length];
            for (int i = 0; i < len - 1; i++) oc[sn[i]] = true; // the tail moves away
            int hx = sn[0] % cols, hy = sn[0] / cols, top = -1;
            int[] nc = new int[4], fr = new int[4];
            for (int d = 0; d < 4; d++) {
                nc[d] = at(hx + DX[d], hy + DY[d]);
                if (nc[d] < 0 || oc[nc[d]]) {
                    nc[d] = -1;
                    continue;
                }
                fr[d] = room(oc, nc[d], sn[len - 1]);
                if (top < 0 || fr[d] > fr[top]) top = d;
            }
            if (top < 0) { // boxed in (the room check should prevent it): start over
                sn = null;
                return;
            }
            int a = -1, ad = 1 << 30, pick = -1, bd = 1 << 30;
            for (int q : ap) {
                int dd = Math.abs(q % cols - hx) + Math.abs(q / cols - hy);
                if (dd < ad) {
                    ad = dd;
                    a = q;
                }
            }
            boolean aim = a >= 0 && rnd.nextInt(4) > 0, keep = rnd.nextInt(5) > 0;
            int need = Math.min(fr[top], 1 << 20); // any move that keeps the tail reachable, else the roomiest
            for (int d = 0; d < 4; d++) {
                if (nc[d] < 0 || fr[d] < need) continue;
                int sc = aim ? 2 * (Math.abs(nc[d] % cols - a % cols) + Math.abs(nc[d] / cols - a / cols)) + rnd.nextInt(2)
                        : d == dir && keep ? 0 : 1 + rnd.nextInt(9);
                if (sc < bd) {
                    bd = sc;
                    pick = d;
                }
            }
            dir = pick;
            if (ap.remove(Integer.valueOf(nc[pick])) && len < sn.length / 3) len++;
            System.arraycopy(sn, 0, sn, 1, len - 1);
            sn[0] = nc[pick];
        }

        void game(Canvas c, float ox, float oy) {
            if (cols < 5 || rows < 3) return;
            if (sn == null || gc != cols || sn.length != cols * rows) {
                sn = new int[cols * rows];
                gc = cols;
                len = 3;
                dir = 0;
                ap.clear();
                for (int i = 0; i < 3; i++) sn[i] = rows / 2 * cols + cols / 2 - i;
            }
            long now = System.currentTimeMillis();
            if (now - t0 >= 90) {
                t0 = now;
                step();
            }
            if (sn != null) {
                p.setColor(col);
                for (int i = 0; i < len + ap.size(); i++) {
                    int q = i < len ? sn[i] : ap.get(i - len);
                    if (i == len) p.setColor(0xFFFF5252);
                    float x = ox + q % cols * sz, y = oy + q / cols * sz;
                    if (i < len) c.drawRect(x + 1, y + 1, x + sz - 1, y + sz - 1, p);
                    else c.drawCircle(x + sz / 2f, y + sz / 2f, sz * .4f, p);
                }
            }
            removeCallbacks(tk);
            postDelayed(tk, 100);
        }

        @Override protected void onDraw(Canvas c) {
            int cap = cap();
            if (mode == 0 || cap == 0) return;
            float ox = (getWidth() - cols * pw) / 2f, oy = (getHeight() - rows * ph) / 2f;
            if (mode == 3) {
                game(c, ox, oy);
                return;
            }
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

    // Layout editor overlay: tap an element to select it (bounding box), drag inside to move it,
    // drag an edge or corner to resize it, pinch to scale it. Changes are saved when you let go.
    static class Ed extends View implements ScaleGestureDetector.OnScaleGestureListener {
        final M m;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final ScaleGestureDetector sd;
        int sel = -1, mode, sides; // mode: 0 idle, 1 move, 2 resize, 3 pinch; sides: 1 left, 2 top, 4 right, 8 bottom
        float lx, ly;
        boolean moved, undo;
        final Runnable hold; // the undo button was held: reset the layout

        Ed(M m) {
            super(m);
            this.m = m;
            sd = new ScaleGestureDetector(m, this);
            hold = () -> {
                undo = false;
                sel = -1;
                m.resetLayout();
                invalidate();
                Toast.makeText(m, "Layout reset", Toast.LENGTH_SHORT).show();
            };
        }

        // the smallest element under the finger
        int pick(float x, float y) {
            int b = -1;
            float best = Float.MAX_VALUE;
            for (int i = 0; i < m.er.length; i++) {
                RectF r = m.er[i];
                if (r.contains(x, y) && r.width() * r.height() < best) {
                    best = r.width() * r.height();
                    b = i;
                }
            }
            return b;
        }

        // which sides of r the finger is on (a corner is two sides)
        int edge(RectF r, float x, float y) {
            float zx = Math.min(m.dp(24), r.width() / 3), zy = Math.min(m.dp(24), r.height() / 3);
            boolean iy = y >= r.top - zy && y <= r.bottom + zy, ix = x >= r.left - zx && x <= r.right + zx;
            int s = 0;
            if (iy && Math.abs(x - r.left) < zx) s |= 1;
            if (ix && Math.abs(y - r.top) < zy) s |= 2;
            if (iy && Math.abs(x - r.right) < zx) s |= 4;
            if (ix && Math.abs(y - r.bottom) < zy) s |= 8;
            return s;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            sd.onTouchEvent(e);
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lx = x;
                    ly = y;
                    mode = 0;
                    moved = false;
                    float cx = getWidth() / 2f, cy = m.dp(26), rad = m.dp(24);
                    if (Math.hypot(x - (cx + m.dp(30)), y - cy) < rad) { // check: leave the editor
                        m.exitEdit();
                        return true;
                    }
                    if (Math.hypot(x - (cx - m.dp(30)), y - cy) < rad) { // undo: reset on hold
                        undo = true;
                        postDelayed(hold, ViewConfiguration.getLongPressTimeout());
                        return true;
                    }
                    if (sel >= 0) {
                        sides = edge(m.er[sel], x, y);
                        if (sides != 0) mode = 2;
                        else if (m.er[sel].contains(x, y)) mode = 1;
                    }
                    if (mode == 0) {
                        sel = pick(x, y);
                        mode = sel >= 0 ? 1 : 0;
                    }
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (undo || e.getPointerCount() > 1 || sel < 0 || mode == 0 || mode == 3) return true;
                    float dx = x - lx, dy = y - ly;
                    lx = x;
                    ly = y;
                    RectF r = m.er[sel];
                    if (mode == 1) {
                        r.offset(dx, dy);
                    } else {
                        if ((sides & 1) != 0) r.left += dx;
                        if ((sides & 2) != 0) r.top += dy;
                        if ((sides & 4) != 0) r.right += dx;
                        if ((sides & 8) != 0) r.bottom += dy;
                    }
                    m.fix(sel, mode == 1);
                    m.place1(sel);
                    moved = true;
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (undo) { // released before the hold was over
                        removeCallbacks(hold);
                        undo = false;
                        Toast.makeText(m, "Hold the undo button to reset the layout", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    if (moved && sel >= 0 && mode != 3) m.save(sel);
                    mode = 0;
                    moved = false;
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }

        @Override public boolean onScaleBegin(ScaleGestureDetector d) {
            if (sel < 0) return false;
            mode = 3;
            return true;
        }

        @Override public boolean onScale(ScaleGestureDetector d) {
            RectF r = m.er[sel];
            float s = d.getScaleFactor();
            r.inset(-r.width() * (s - 1) / 2, -r.height() * (s - 1) / 2);
            m.fix(sel, false);
            m.place1(sel);
            moved = true;
            invalidate();
            return true;
        }

        @Override public void onScaleEnd(ScaleGestureDetector d) {
            if (moved && sel >= 0) m.save(sel);
            mode = 0;
            moved = false;
        }

        @Override protected void onDraw(Canvas c) {
            float u = m.dp(1);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u);
            p.setColor(0x88FFFFFF);
            for (int i = 0; i < m.er.length; i++) if (i != sel) c.drawRect(m.er[i], p);
            if (sel >= 0) {
                RectF r = m.er[sel];
                p.setColor(0xFF00E676);
                p.setStrokeWidth(2 * u);
                c.drawRect(r, p);
                p.setStyle(Paint.Style.FILL);
                float[] hx = {r.left, r.centerX(), r.right}, hy = {r.top, r.centerY(), r.bottom};
                for (int a = 0; a < 3; a++)
                    for (int b = 0; b < 3; b++)
                        if (a != 1 || b != 1) c.drawCircle(hx[a], hy[b], 5 * u, p);
            }
            p.setStyle(Paint.Style.FILL);
            float cx = getWidth() / 2f, cy = 26 * u;
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(22 * m.spx);
            p.setColor(0xFF00E676);
            c.drawCircle(cx + 30 * u, cy, 20 * u, p);
            p.setColor(0xFF000000);
            c.drawText("\u2713", cx + 30 * u, cy + 8 * u, p);
            p.setColor(0xFF444444);
            c.drawCircle(cx - 30 * u, cy, 20 * u, p);
            p.setColor(0xFFFFFFFF);
            c.drawText("\u21BA", cx - 30 * u, cy + 8 * u, p);
            p.setColor(0xFFAAAAAA);
            p.setTextSize(12 * m.spx);
            c.drawText("tap an element, drag to move, pinch to scale", cx, 62 * u, p);
            c.drawText("drag an edge or corner to resize, hold undo to reset", cx, 78 * u, p);
        }
    }

    // view dots at the left edge, like a launcher's page indicator: one per view of the
    // slide, the current one lit in the slide colour, centred in the view's height
    static class Pd extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int n, cur, col;

        Pd(Context x) {
            super(x);
        }

        @Override protected void onDraw(Canvas c) {
            float u = getResources().getDisplayMetrics().density, pitch = 16 * u;
            float y = (getHeight() - (n - 1) * pitch) / 2f;
            for (int i = 0; i < n; i++) {
                p.setColor(i == cur ? col : 0xFF444444);
                c.drawCircle(10 * u, y + i * pitch, (i == cur ? 4 : 3) * u, p);
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
