package io.rfsj.weecounter;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.graphics.drawable.GradientDrawable;
import android.view.ContextThemeWrapper;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.Normalizer;
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
        {"vt", "15", "Vibration: tap"},
        {"vh", "40 60 40", "Vibration: hold (panel / counting view)"},
        {"vr", "80", "Vibration: reset"},
        {"vl", "500 50 500", "Vibration: count reached"},
        {"vc", "50 50 50 50 50", "Vibration: lap"},
        {"bs", "0", "Floating counter: diameter in px (0 = automatic)"}};

    // counting views: 0 blackout, 1 dots, 2 tally, 3 game (a self-playing snake)
    static final String[] NM = {"blackout", "dots", "tally", "game"};

    // Do Not Disturb warning above the bottom buttons: 0 = not shown, 1 = priority only, 2 = alarms only
    static final String[] DT = {"", "DND ON - only priority notifications get through",
        "DND ON - notifications silenced, alarms only"};

    // shown when a dhikr profile is finished (check mark, thumbs up, trophy, hands, watermelon, party, star, 100)
    static final String[] EM = {"\u2705", "\uD83D\uDC4D", "\uD83C\uDFC6", "\uD83D\uDE4C",
        "\uD83C\uDF49", "\uD83C\uDF89", "\uD83C\uDF1F", "\uD83D\uDCAF"};

    SharedPreferences p;
    boolean sm;     // simple mode: a plain counter, the selected profile is kept but not used
    String pin;     // the profile of the shortcut this window was opened with (or null); pf is the window's current profile
    boolean chain;  // the settings / profile screens were opened from here
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
    View[] ev;       // their views: menu, adjust, -1, view, dhikr text, counter, footer, DND warning, title
    Ed ed;           // the editor overlay
    int vi;          // chosen view of the slides (clamped per slide)
    long rt;         // time of the last tap on the completed screen
    int rn, rg;      // taps so far / taps needed (3 to 5, random) to start a finished profile over
    boolean rb;      // the restart is about to happen
    int shx, shy;    // current pixel-shift offset in px
    Runnable pend;   // slide change waiting for the slide-out animation
    Pd ind;          // view dots: a row above the slide title, shown for a moment after a slide or view change
    int dsk = -2;    // the dhikr text (slide * 64 + view) the dots were last shown for
    final Runnable dfd = () -> ind.animate().alpha(0).setDuration(400); // fades the dots out
    Th th;           // colours: the theme of this window's profile
    LinearLayout sp; // the theme strip (menu > Theme), null until first opened
    GestureDetector sg; // swipes on the strip
    HorizontalScrollView hs; // its row of palettes
    FrameLayout fo;  // the focus sheet (the focus button), null until first opened
    ScaleGestureDetector sd; // pinch to resize the dhikr text of a slide / view
    // The floating counter (Fb) counts the plain counter or the slides of the profile in the settings
    // file: take over what it counted. live: the counts are written down at every redraw.
    boolean live;
    final SharedPreferences.OnSharedPreferenceChangeListener nl = (sp, key) -> {
        if (key != null && (pr == null ? key.equals("n") : key.equals("cn:" + pf) || key.equals("cu:" + pf)))
            takeOver();
    };
    float zs;        // text scale while pinching
    boolean ign;     // this touch is no swipe: it began on the focus sheet or the theme strip, or in focus mode
    boolean bz;      // the finger went down near the bottom edge, where an upward swipe is the system's home gesture
    float zmx = 1;   // the largest scale at which the text still fits its rectangle (the limit of a pinch)
    boolean ful;     // the text fills its rectangle (cannot grow): the expand button shows
    Gl xb;           // the expand button, at the bottom right of the text rectangle
    // expanded reading mode: the dhikr text over the whole screen
    FrameLayout xo;
    ScrollView xs;
    TextView xt, xc;             // the text, and the count in the middle button
    ScaleGestureDetector xsd;    // pinch in the expanded mode
    float xz, xbs;               // its pinch scale and standard size
    int xkey = -9;               // the dhikr text (slide * 64 + view) it shows
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
        int[] c, cu, c0; // c0: the repeats written in the profile, c: with your own (see ov)
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
    // a header line of a profile text, "@key value", before its first slide; repeated lines are
    // joined with line breaks (@id, @version, @author, @desc)
    static String hd(String s, String k) {
        StringBuilder b = new StringBuilder();
        for (String ln : s.split("\r?\n")) {
            String l = ln.trim();
            if (l.matches("-{2,}\\s*[0-9]+")) break;
            if (l.startsWith("@" + k + " ")) b.append(b.length() > 0 ? "\n" : "").append(l.substring(k.length() + 2).trim());
        }
        return b.toString();
    }

    // the counts of the profile with your own repeats (setting rp:<profile>: "*N" = N for every
    // slide, else one number per slide separated by commas, empty = the profile's own)
    static void ov(Pr r, String s) {
        int all = 0;
        if (s.startsWith("*")) {
            try {
                all = Integer.parseInt(s.substring(1).trim());
            } catch (Exception e) {
                // none
            }
        }
        String[] a = s.split(",", -1);
        for (int i = 0; i < r.c.length; i++) {
            int v = all;
            if (all <= 0) {
                try {
                    v = Integer.parseInt(a[i].trim());
                } catch (Exception e) {
                    // none
                }
            }
            r.c[i] = v > 0 ? v : r.c0[i];
            r.cu[i + 1] = sat(r.cu[i], r.c[i]);
        }
        r.tot = r.cu[r.c.length];
    }

    // a sum that stops at the largest int
    static int sat(int a, int b) {
        return (int) Math.min(Integer.MAX_VALUE, (long) a + b);
    }

    // description, author and version of a profile
    static void about(Activity a, Th th, String name, String tx) {
        String au = hd(tx, "author"), v = hd(tx, "version"), ds = hd(tx, "desc");
        String m = (au.isEmpty() ? "" : "By " + au) + (v.isEmpty() ? "" : (au.isEmpty() ? "" : "  -  ") + "version " + v);
        Sh.of(a, th).setTitle(name).setMessage(m + (m.isEmpty() ? "" : "\n\n")
                + (ds.isEmpty() ? "No description" : ds)).setPositiveButton("OK", null).show();
    }

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
                    String dg = l.replaceAll("[^0-9]", "").replaceFirst("^0+(?=.)", "");
                    cnt = dg.length() > 9 ? 999999999 : Integer.parseInt(dg); // no limit but the size of an int
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
            r.cu[i + 1] = sat(r.cu[i], r.c[i]);
        }
        r.c0 = r.c.clone();
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
        p = getSharedPreferences("p", 0);
        // every window has its own profile: from its state, or the shortcut it was opened with
        // (wee://profile/<name>), or else the one chosen last
        Uri u = getIntent().getData();
        boolean simp = u != null && "wee".equals(u.getScheme()) && "simple".equals(u.getHost()); // a reminder of the plain counter
        pin = u != null && "wee".equals(u.getScheme()) ? u.getLastPathSegment() : null;
        pf = b != null && b.containsKey("pf") ? b.getString("pf") : simp ? "" : pin != null ? pin : p.getString("pf", "");
        sm = b != null && b.containsKey("sm") ? b.getBoolean("sm") : simp || pin == null && !p.getBoolean("pm", false); // start in simple mode; a profile shortcut in profile mode
        th = Th.get(this, p, pf);
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
        xb = new Gl(this, 0);             // shown only when the text fills its rectangle
        xb.setVisibility(View.GONE);
        xb.setOnClickListener(v -> expand());
        lay.addView(xb, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP | Gravity.START));
        bot = new LinearLayout(this);
        bot.setOrientation(LinearLayout.VERTICAL);
        bot.setGravity(Gravity.CENTER);
        t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        bot.addView(t, new LinearLayout.LayoutParams(-1, 0, 1f));
        lay.addView(bot);
        f.addView(lay, new FrameLayout.LayoutParams(-1, -1));

        se = b("menu");
        se.setOnClickListener(this);
        rs = b("adjust");
        rs.setOnClickListener(this);      // opens the adjust dialog (a hold does too)
        rs.setOnLongClickListener(this);
        dn = b("-1");
        dn.setOnClickListener(this);
        vw = b("");
        vw.setOnClickListener(this);
        pg = new TextView(this);          // status footer: slide progress
        pg.setTextColor(0xFF888888);
        pg.setOnClickListener(this);      // a tap still counts, hold toggles the counting view like anywhere else
        pg.setOnLongClickListener(this);
        f.addView(pg, new FrameLayout.LayoutParams(-2, -2));
        dt = new TextView(this);          // Do Not Disturb warning (not clickable)
        dt.setTextColor(0xFFFFB74D);
        f.addView(dt, new FrameLayout.LayoutParams(-2, -2));

        ttl = new TextView(this);         // slide title: an element of its own
        ttl.setTextColor(0xFF999999);
        f.addView(ttl, new FrameLayout.LayoutParams(-2, -2));

        // one dot per view of the slide in a row, the current one lit; above the elements
        ind = new Pd(this);
        f.addView(ind, new FrameLayout.LayoutParams(dp(160), dp(16), Gravity.TOP | Gravity.START));

        cf = new Cf(this);
        f.addView(cf, new FrameLayout.LayoutParams(-1, -1));

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
        if (chain) { // back from the settings or profile screens opened here: they may have changed the profile
            chain = false;
            String np = p.getString("pf", "");
            if (!np.isEmpty() && !np.equals(pf)) setMode(false); // choosing a profile turns profile mode on
            pf = np;
        }
        th = Th.get(this, p, pf);
        mx = num(1);
        st = Math.max(1, num(2));
        c.sz = dp(Math.max(6, num(3)));
        ps = Math.max(0, num(4));
        hp = p.getBoolean("hp", false);
        vk = p.getInt("vk", 0);
        cl = th.lap;
        cf.cl = cl;
        vt = fx(5);
        vh = fx(6);
        vr = fx(7);
        vl = fx(8);
        vc = fx(9);
        load(); // also takes over what another window counted meanwhile
        if (pr == null) n = p.getInt("n", 0);
        p.registerOnSharedPreferenceChangeListener(nl);
        skin(); // again: load() may have dropped a profile that is gone
        weekly();
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
        // the first start of a new install (nothing written down yet) shows the help once; an install
        // that is updated never does
        boolean tour = !p.getBoolean("hs", false) && !p.contains("md");
        if (!p.getBoolean("hs", false)) p.edit().putBoolean("hs", true).apply();
        live = true; // from here on the counts are written down at every redraw
        draw();
        f.removeCallbacks(this);
        run();
        if (tour) f.post(() -> help(true));
    }

    @Override protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        o.putString("pf", pf);
        o.putBoolean("sm", sm);
    }

    @Override protected void onPause() {
        super.onPause();
        live = false;
        // what the floating counter will count if it comes up by itself: the window that was left last
        if (!Fb.on) p.edit().putString("bt", pr != null ? pf : "").putString("bw", pin != null ? pin : "").apply();
        p.unregisterOnSharedPreferenceChangeListener(nl);
        f.removeCallbacks(this);
        land();
        undo();
        keep();
    }

    // takes over what the floating counter counted: the plain counter, or the slide counts of the profile
    void takeOver() {
        if (pr == null) {
            int c2 = p.getInt("n", 0);
            if (c2 != n) {
                n = c2;
                draw();
            }
            return;
        }
        int k = pr.c.length;
        int[] a = new int[k];
        try {
            String sv = p.getString("cn:" + pf, null);
            if (sv != null) {
                String[] q = sv.split(",");
                for (int i = 0; i < k && i < q.length; i++) a[i] = Math.max(0, Integer.parseInt(q[i]));
            }
        } catch (Exception e) {
            return;
        }
        int cu = Math.max(0, Math.min(k, p.getInt("cu:" + pf, 0)));
        if (cu != cur || !Arrays.equals(a, cn)) {
            cn = a;
            cur = cu;
            draw();
        }
    }

    // The plain counter is written down at once, so this window and the floating counter always see
    // the same number (draw() writes everything down while the window is live, see keep()).
    void keepN() {
        if (pr == null) p.edit().putInt("n", n).apply();
    }

    // writes down the counts (the plain counter, or the profile's)
    void keep() {
        SharedPreferences.Editor e = p.edit().putInt("md", mode).putInt("vi", vi);
        if (pr == null) e.putInt("n", n); // a profile counts on its own (cn:), only a plain window writes the plain counter
        else {
            StringBuilder sb = new StringBuilder();
            for (int c : cn) sb.append(c).append(',');
            e.putString("cn:" + pf, sb.toString()).putInt("cu:" + pf, cur);
        }
        e.apply();
    }

    void setMode(boolean simple) {
        sm = simple;
        if (pin == null) p.edit().putBoolean("pm", !simple).apply(); // a plain window remembers it
    }

    // a heading and a few words, in the help sheet
    void hrow(LinearLayout card, String head, String body) {
        TextView h = new TextView(this);
        h.setText(head);
        h.setTextSize(16);
        h.setTextColor(th.fg);
        h.getPaint().setFakeBoldText(true);
        h.setPadding(0, dp(14), 0, 0);
        card.addView(h);
        TextView b = new TextView(this);
        b.setText(body);
        b.setTextSize(14);
        b.setTextColor(th.dim);
        card.addView(b);
    }

    // The help: a short tour of what the app does, one heading and a few words each. It shows once on the
    // first start of a new install (first), and is in the menu as Help.
    void help(boolean first) {
        LinearLayout card = sheet("Help", first ? "A short tour. You can open it again from the menu: Help." : null, true, false);
        hrow(card, "Counting", "Tap anywhere on the counting screen to count. The -1 button takes one back.");
        hrow(card, "Slides and views", "In a profile a slide moves on by itself when its count is done. Swipe up for the next slide and down for the "
                + "previous one; swipe left or right for the other views of a slide (the Arabic, the transliteration, the meaning). "
                + "Pinch the text to make it bigger or smaller. When it fills its box, the expand button at its corner shows it "
                + "over the whole screen, to scroll and read.");
        hrow(card, "Focus", "The focus button counts on a view of your choice with nothing else on the screen: blackout, dots or tally. "
                + "Hold the knob and push it to start; a tap counts and a hold anywhere leaves it.");
        hrow(card, "Adjust", "The adjust button, top right, sets the count and the target by typing, resets the slide, and has Undo. "
                + "Hold a reset and push the red slider from the right to the left to do it; the whole profile resets only that way.");
        hrow(card, "Menu", "Settings, Profiles, Edit layout (move and resize every part of the screen), Goto (jump to a slide), "
                + "Theme, Reminders, Floating counter, and this Help.");
        hrow(card, "Profiles", "Menu, Profiles, then BROWSE PROFILES downloads ready-made dhikr, such as the morning and evening adhkar. "
                + "Profiles can also be imported, exported and shared.");
        hrow(card, "Reminders", "Menu, Reminders: a reminder at a time you choose for a profile. A kept reminder stays in your "
                + "notifications until you have counted the profile.");
        hrow(card, "Floating counter", "Menu, Floating counter: a bubble over other apps (it asks for a permission once). Tap it to count, "
                + "drag it anywhere, fling it at an edge to make it stick, drop it on the X to close it or on the i to see the current "
                + "slide. It also comes up by itself when you minimize the app; Settings can turn that off.");
        hrow(card, "Home screen shortcuts", "Profiles, then shortcut under a profile, puts it on your home screen, in its own window. "
                + "The icon can be an emoji, not only a letter: try \uD83C\uDF04 for the morning adhkar and \uD83C\uDF06 for the evening ones.");
        hrow(card, "A bug or an idea", "Settings has a link to the GitHub issues page: report a bug or ask for a feature there.");
        TextView close = sb("Close", th.ac, v -> closeSheet());
        close.setTextSize(18);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(7), dp(18), dp(7), dp(6));
        card.addView(close);
    }

    // The floating counter (Fb): a bubble over other apps that counts the plain counter, or the current
    // slide of the profile in use. Needs the
    // "Display over other apps" permission; the menu item turns it on and off.
    void bubble() {
        if (Fb.on) {
            stopService(new Intent(this, Fb.class));
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow \"Display over other apps\" for weecounter, then choose Floating counter again",
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        // it counts what this window is on, and a tap on its notification comes back to this window
        p.edit().putString("bt", pr != null ? pf : "").putString("bw", pin != null ? pin : "").apply();
        startForegroundService(new Intent(this, Fb.class));
    }

    // menu: simple mode (a plain counter) or profile mode (the selected dhikr profile)
    void toggleMode() {
        keep();
        setMode(!sm);
        load();
        if (pr == null) n = p.getInt("n", 0);
        skin();
        draw();
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
            f.setSystemUiVisibility(0x1706 | (th.light ? 0x2010 : 0));
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

    // default rectangle of element i: 0 menu, 1 adjust, 2 -1, 3 view, 4 dhikr text, 5 counter,
    // 6 footer, 7 Do Not Disturb warning, 8 slide title. Symmetric about the middle of the screen, top to
    // bottom: menu and reset in the corners, warning, dhikr text, counter, (the view dots), slide title,
    // and a bottom row of -1, the footer and the focus button.
    // default layout as fractions of the screen: left, top, right, bottom of every element
    static final float[][] DF = {
        {0, 0, .25f, .0526f},             // menu
        {.75f, 0, 1, .0526f},             // adjust
        {0, .95f, .25f, .985f},           // -1
        {.75f, .95f, 1, .985f},           // view (the focus button)
        {0, .1064f, 1, .7440f},           // dhikr text
        {0, .7466f, 1, .875f},            // counter
        {.25f, .95f, .75f, .985f},        // footer
        {0, .0560f, 1, .1064f},           // Do Not Disturb warning
        {.04f, .9f, .96f, .94f}};         // title

    RectF def(int i) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float W = dm.widthPixels, H = dm.heightPixels;
        float[] f = DF[Math.min(i, DF.length - 1)];
        return new RectF(f[0] * W, f[1] * H, f[2] * W, f[3] * H);
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
        if (i == 4) { // the expand button sits in the bottom right corner of the text
            FrameLayout.LayoutParams xp = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP | Gravity.START);
            xp.leftMargin = (int) (r.right - dp(46));
            xp.topMargin = (int) (r.bottom - dp(46));
            xb.setLayoutParams(xp);
        }
        if (i == 8) { // the view dots sit just above the slide title, centred on it
            FrameLayout.LayoutParams ip = (FrameLayout.LayoutParams) ind.getLayoutParams();
            ip.leftMargin = (int) Math.max(0, r.centerX() - ip.width / 2f);
            ip.topMargin = (int) Math.max(0, r.top - ip.height);
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

    // all the element rectangles in one array, and back (for undo)
    float[] snapshot() {
        float[] a = new float[er.length * 4];
        for (int i = 0; i < er.length; i++) {
            a[4 * i] = er[i].left;
            a[4 * i + 1] = er[i].top;
            a[4 * i + 2] = er[i].right;
            a[4 * i + 3] = er[i].bottom;
        }
        return a;
    }

    void restore(float[] a) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float W = dm.widthPixels, H = dm.heightPixels;
        SharedPreferences.Editor e = p.edit();
        for (int i = 0; i < er.length; i++) {
            er[i].set(a[4 * i], a[4 * i + 1], a[4 * i + 2], a[4 * i + 3]);
            e.putString("el" + i, a[4 * i] / W + "," + a[4 * i + 1] / H + "," + a[4 * i + 2] / W + "," + a[4 * i + 3] / H);
        }
        e.apply();
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
        if (xo != null && xo.getVisibility() == View.VISIBLE) {
            collapse();
            return;
        }
        if (Sh.back(this)) return; // a sheet or a dialog is up: Back closes it
        if (sp != null && sp.getVisibility() == View.VISIBLE) {
            sp.setVisibility(View.GONE);
            return;
        }
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
        int W = (int) r.width() - dp(16);
        float area = r.height() - dp(16);
        float[] o = new float[1];
        CharSequence use = block(tx, w, W, area, 34 * spx, o);
        bs = o[0];
        dl.setText(use);
        float top = fit(use, W, area, bs * 2.5f); // the largest size that still fits: the limit of a pinch
        zmx = Math.max(1f, top / bs);
        float fs = fit(use, W, area, bs * z);
        dl.setTextSize(TypedValue.COMPLEX_UNIT_PX, fs);
        ful = fs >= top * .98f; // the text fills its rectangle and cannot grow: offer the expanded view
        lay.requestLayout();
    }

    // The text block of a view for a box of W x area px: the variant that is used (hard line breaks
    // kept, or the lines run together) and, in size[0], the largest size (at most cap) it has there.
    CharSequence block(String[] tx, float[] w, int W, float area, float cap, float[] size) {
        int last = -1;
        float wmax = 1;
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
        size[0] = keep ? sb : sf;
        return keep ? brk : flo;
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
        if (live) keep(); // the floating counter counts the same numbers
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
        xb.setVisibility(ful && pr != null && !edit && slide() < pr.c.length ? View.VISIBLE : View.GONE);
        pg.setText(prog);
        ttl.setText(title);
        dl.setVisibility(dk ? View.VISIBLE : View.GONE);
        int co = cl[ci % cl.length];
        t.setText(big);
        t.setTextColor(co);
        if (xo != null && xo.getVisibility() == View.VISIBLE) { // the expanded view follows the slide
            if (pr == null || slide() >= pr.c.length) collapse();
            else {
                xc.setText(big);
                xc.setTextColor(co);
                xfill(key, tx, tw, ve);
            }
        }
        vw.setText("focus");
        dt.setText(DT[dnOn ? p.getInt("dn", 0) : (edit ? 1 : 0)]);
        for (View x : ui) x.setVisibility(cv ? View.INVISIBLE : View.VISIBLE);
        f.setBackgroundColor(cv && mode == 0 ? 0xFF000000 : th.bg); // blackout stays black
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
        boolean nw = key != dsk; // a new slide or view: show the dots for a moment
        dsk = key;
        if (nw && vn > 1) {
            ind.removeCallbacks(dfd);
            ind.animate().cancel();
            ind.setAlpha(1);
            ind.postDelayed(dfd, 1500);
        }
        cf.setVisibility(cv ? View.INVISIBLE : View.VISIBLE);
        c.mode = mode;
        c.n = cnt;
        c.st = lap;
        c.col = co;
        c.bad = th.bad;
        c.nx = cl[(ci + 1) % cl.length];
        c.setVisibility(cv && mode > 0 ? View.VISIBLE : View.INVISIBLE);
        c.invalidate();
    }

    // (re)loads the active dhikr profile; a missing or broken one falls back to the normal counter
    // puts the theme of this window's profile on the screen
    void skin() {
        th = Th.get(this, p, pf);
        th.bars(this);
        cl = th.lap;
        cf.cl = cl;
        f.setBackgroundColor(th.bg);
        dl.setTextColor(th.fg);
        pg.setTextColor(th.dim);
        ttl.setTextColor(th.dim);
        dt.setTextColor(th.warn);
        for (TextView x : new TextView[]{se, rs, dn, vw}) x.setTextColor(th.dim);
        // the elements have the colour of the screen behind them, so a sliding slide text goes under them
        for (View x : new View[]{se, rs, dn, vw, pg, ttl, dt, bot}) x.setBackgroundColor(th.bg);
        xb.col = th.dim;
        xb.setBackground(chip(0xE0, 20));
        xb.invalidate();
        ind.off = (th.dim & 0xFFFFFF) | 0x66000000;
    }

    // once a week (and not more than every 6 hours after a failure) look for new profiles:
    // one small request for the manifest, none if the setting is off
    void weekly() {
        long now = System.currentTimeMillis();
        if (!p.getBoolean("wk", true) || now - p.getLong("mt", 0) < 604800000L || now - p.getLong("ma", 0) < 21600000L)
            return;
        p.edit().putLong("ma", now).apply();
        new Thread(() -> B.refresh(p)).start();
    }

    void load() {
        pr = null;
        if (pf.length() > 0 && !sm) {
            String tx = getSharedPreferences("d", 0).getString(pf, null);
            pr = tx == null ? null : parse(tx);
            if (pr == null) {
                if (pf.equals(pin)) { // a shortcut of a profile that is gone
                    Toast.makeText(this, "Profile not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
                pf = "";
            } else {
                ov(pr, p.getString("rp:" + pf, ""));
                if (!p.getBoolean("in:" + pf, false) && !hd(tx, "desc").isEmpty()) { // first open: the description
                    p.edit().putBoolean("in:" + pf, true).apply();
                    about(this, th, pf, tx);
                }
                int k = pr.c.length;
                cn = new int[k];
                String sv = p.getString("cn:" + pf, null);
                if (sv != null) {
                    try {
                        String[] a = sv.split(",");
                        for (int i = 0; i < k && i < a.length; i++)
                            cn[i] = Math.max(0, Integer.parseInt(a[i]));
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
        setTaskDescription(new ActivityManager.TaskDescription(pf.isEmpty() ? null : pf)); // the name in the recent apps
    }

    // Swipes (dhikr only), like reels: up = next slide (counts as done), down = previous slide,
    // left / right = next / previous view. A recognised swipe never counts as a tap or a hold.
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (Sh.isUp(this)) { // a sheet or a dialog: only it gets touches, and this touch is no swipe
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) ign = true;
            return super.dispatchTouchEvent(e);
        }
        if (sp != null && sp.getVisibility() == View.VISIBLE) { // the theme strip is up: only it gets touches
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) ign = true;
            if (e.getY() >= sp.getTop()) return super.dispatchTouchEvent(e);
            if (e.getActionMasked() == MotionEvent.ACTION_UP) sp.setVisibility(View.GONE); // a tap outside closes it
            return true;
        }
        if (xo != null && xo.getVisibility() == View.VISIBLE) { // expanded: scrolling, pinching and view swipes only
            xsd.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    tx = e.getX();
                    ty = e.getY();
                    sw = false;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    sw = true; // a second finger is a pinch: no view swipe
                    break;
                case MotionEvent.ACTION_MOVE: {
                    if (sw || xsd.isInProgress()) break;
                    float dx = e.getX() - tx, dy = e.getY() - ty;
                    if (Math.abs(dx) > dp(72) && Math.abs(dx) > 2 * Math.abs(dy)) { // mostly sideways
                        sw = true;
                        xview(dx < 0 ? 2 : 3);
                        e.setAction(MotionEvent.ACTION_CANCEL); // the scroll view drops the touch
                    }
                    break;
                }
            }
            return super.dispatchTouchEvent(e);
        }
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
                    ign = cv; // in focus mode (and for the rest of the touch that leaves it) nothing is a swipe
                    bz = ty > getResources().getDisplayMetrics().heightPixels - dp(56);
                    break;
                case MotionEvent.ACTION_MOVE: {
                    if (sw) return true;
                    if (ign) break;
                    float dx = e.getX() - tx, dy = e.getY() - ty;
                    if (Math.max(Math.abs(dx), Math.abs(dy)) < dp(64)) break;
                    if (bz && dy < 0 && Math.abs(dy) > Math.abs(dx)) break; // up from the bottom edge: not the next slide
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

    // A translucent rounded background for the floating buttons (alpha 0..255, corner radius in dp)
    GradientDrawable chip(int alpha, int rdp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor((Th.mix(th.bg, th.fg, .12f) & 0xFFFFFF) | (alpha << 24));
        g.setCornerRadius(dp(rdp));
        return g;
    }

    // Expanded reading mode: the text of the current slide and view over the whole screen, so a long
    // text can be read larger. One finger scrolls, two pinch, a swipe left / right shows the other
    // views; the buttons at the bottom go to the previous slide, count and go to the next slide
    // (a tap on the text does not count here); the glyph at the top right, or Back, leaves it.
    void expand() {
        if (pr == null || slide() >= pr.c.length) return;
        if (xo != null) f.removeView(xo);
        xo = new FrameLayout(this);
        xo.setBackgroundColor(th.bg);
        xo.setClickable(true); // nothing under it gets a touch
        xs = new ScrollView(this);
        xs.setFillViewport(true); // a short text is centred
        xs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        xs.setScrollbarFadingEnabled(false); // the bar stays while the text is longer than the screen
        if (Build.VERSION.SDK_INT >= 29) { // a thin bar in the colour of the theme
            GradientDrawable g = new GradientDrawable();
            g.setColor((th.dim & 0xFFFFFF) | 0x99000000);
            g.setCornerRadius(dp(2));
            xs.setVerticalScrollbarThumbDrawable(g);
            xs.setScrollBarSize(dp(3));
        }
        xt = new TextView(this);
        xt.setGravity(Gravity.CENTER);
        xt.setTextColor(th.fg);
        xt.setPadding(dp(16), dp(64), dp(16), dp(110)); // room for the glyph and the buttons
        xs.addView(xt, new FrameLayout.LayoutParams(-1, -2));
        xo.addView(xs, new FrameLayout.LayoutParams(-1, -1));

        Gl ex = new Gl(this, 1); // leave: the corners point inwards
        ex.col = th.fg;
        ex.setBackground(chip(0xE0, 22));
        ex.setOnClickListener(v -> collapse());
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.END);
        ep.topMargin = dp(12);
        ep.rightMargin = dp(12);
        xo.addView(ex, ep);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        Gl up = new Gl(this, 2), down = new Gl(this, 3); // the slide goes up / down, like a swipe
        up.col = th.fg;
        down.col = th.fg;
        up.setBackground(chip(0xF0, 28));
        down.setBackground(chip(0xF0, 28));
        up.setOnClickListener(v -> step(1));
        down.setOnClickListener(v -> step(0));
        xc = new TextView(this);
        xc.setGravity(Gravity.CENTER);
        xc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        xc.getPaint().setFakeBoldText(true);
        xc.setMinWidth(dp(128));
        xc.setPadding(dp(20), 0, dp(20), 0);
        xc.setBackground(chip(0xF0, 28));
        xc.setOnClickListener(v -> inc());
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(dp(56), dp(56));
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(-2, dp(56));
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(dp(56), dp(56));
        p1.setMargins(dp(8), 0, dp(8), 0);
        p2.setMargins(dp(8), 0, dp(8), 0);
        p3.setMargins(dp(8), 0, dp(8), 0);
        bar.addView(up, p1);
        bar.addView(xc, p2);
        bar.addView(down, p3);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bp.bottomMargin = dp(28);
        xo.addView(bar, bp);

        xsd = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                xz = Math.max(.5f, Math.min(3f, xz * d.getScaleFactor()));
                xt.setTextSize(TypedValue.COMPLEX_UNIT_PX, xbs * xz);
                return true;
            }

            @Override public void onScaleEnd(ScaleGestureDetector d) {
                p.edit().putFloat(xk(), xz).apply();
            }
        });
        xkey = -9;
        f.addView(xo, new FrameLayout.LayoutParams(-1, -1));
        draw(); // fills it
    }

    void collapse() {
        if (xo != null) xo.setVisibility(View.GONE);
        xkey = -9;
    }

    // the saved pinch scale of the expanded view, per slide and view
    String xk() {
        return "fx:" + zk().substring(3);
    }

    // the text of the current slide and view in the expanded view: as big as fits the screen (at most
    // 72sp) times the scale pinched there; a longer text scrolls
    void xfill(int key, String[] tx, float[] tw, int ve) {
        if (key == xkey) return;
        xkey = key;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int W = dm.widthPixels - 2 * dp(16);
        float area = dm.heightPixels - dp(64) - dp(110);
        float[] o = new float[1];
        CharSequence use = block(tx, tw, W, area, 72 * spx, o);
        xbs = o[0];
        xz = p.getFloat(xk(), 1f);
        xt.setText(use);
        xt.setTextSize(TypedValue.COMPLEX_UNIT_PX, xbs * xz);
        xs.scrollTo(0, 0);
    }

    // the expanded view's buttons: 0 next slide, 1 previous slide (the same as a swipe up / down)
    void step(int t) {
        if (pr == null || slide() >= pr.c.length) return;
        z(t == 0 && slide() + 1 >= pr.c.length ? vl : vt);
        land();
        apply(t);
        draw();
    }

    // a swipe left / right in the expanded view: the next / previous view of the slide
    void xview(int t) {
        if (pr == null || slide() >= pr.c.length || pr.v[slide()].length < 2) return;
        z(vt);
        land();
        apply(t);
        draw();
    }

    void swipe(float dx, float dy) {
        boolean h = Math.abs(dx) > Math.abs(dy);
        final int t = h ? (dx < 0 ? 2 : 3) : (dy < 0 ? 0 : 1); // 0 next slide, 1 previous slide, 2 next view, 3 previous view
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
        else flip(0, dy < 0 ? -1 : 1, r);
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
                N.done(this, pf);
                em = EM[new Random().nextInt(EM.length)];
                if (!cv) cf.go();
            }
        } else if (s < k) {                 // next / previous view
            int nv = pr.v[s].length;
            vi = (Math.min(vi, nv - 1) + (t == 2 ? 1 : nv - 1)) % nv;
        }
    }

    // Slides the dhikr text out the way the finger went (fx / fy = -1, 0 or 1), applies the change
    // and slides the new text in from the opposite side. Only the text block moves: the number, the
    // title, the footer and the buttons stay where they are (they have a background, so the text
    // slides under them).
    void flip(float fx, float fy, Runnable change) {
        final float w = dl.getWidth() * .3f * fx, h = dl.getHeight() * .3f * fy;
        pend = change;
        dl.animate().translationX(w).translationY(h).alpha(0).setDuration(100).withEndAction(() -> {
            Runnable r = pend;
            pend = null;
            if (r != null) r.run();
            dl.setTranslationX(-w);
            dl.setTranslationY(-h);
            dl.setAlpha(0);
            dl.animate().translationX(0).translationY(0).alpha(1).setDuration(140);
        });
    }

    // finishes a running slide animation right away, including its pending change
    void land() {
        dl.animate().cancel();
        Runnable r = pend;
        pend = null;
        if (r != null) r.run();
        dl.setTranslationX(0);
        dl.setTranslationY(0);
        dl.setAlpha(1);
    }

    // hold on the footer (slide x of y): list of the slides to jump to; the title is the profile name,
    // tap it to pick another profile
    // normalised text for searching: lower case, without accents or Arabic vowel marks
    static String nz(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }

    // approximate substring match of q in t (both normalised): -1 if q is further away than a
    // typo or two (none for short words), else 1000 minus 300 per mistake
    static int fz(String q, String t) {
        int n = q.length(), tol = n < 4 ? 0 : n < 8 ? 1 : 2, best = n;
        int[] r = new int[n + 1];
        for (int i = 0; i <= n; i++) r[i] = i;
        for (int j = 0; j < t.length(); j++) {
            int prev = 0;
            for (int i = 1; i <= n; i++) {
                int x = r[i];
                r[i] = Math.min(prev + (q.charAt(i - 1) == t.charAt(j) ? 0 : 1), Math.min(x, r[i - 1]) + 1);
                prev = x;
            }
            best = Math.min(best, r[n]);
        }
        return best > tol ? -1 : 1000 - 300 * best;
    }

    // The focus button: a sheet with the selector of the counting view and a slider; hold the knob
    // and push it to the end to go into focus (hold anywhere to leave it again).
    // The sheet of the focus, menu and adjust buttons: the card of Sh (the one every dialog of the app
    // has) with a title, and a sub title if there is one. scroll: the card may be taller than the screen;
    // fields: it has text fields. The title is the first child of the card.
    LinearLayout sheet(String title, String sub, boolean scroll, boolean fields) {
        if (fo != null) Sh.hide(this, fo);
        Sh.Shell sh = Sh.shell(this, th, fields, scroll);
        fo = sh.root;
        LinearLayout card = sh.card;
        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextSize(22);
        t1.setTextColor(th.fg);
        card.addView(t1);
        if (sub != null) {
            TextView t2 = new TextView(this);
            t2.setText(sub);
            t2.setTextSize(14);
            t2.setTextColor(th.dim);
            t2.setPadding(0, dp(6), 0, dp(10));
            card.addView(t2);
        }
        return card;
    }

    void closeSheet() {
        if (fo != null) Sh.hide(this, fo);
    }

    void focusSheet() {
        LinearLayout card = sheet("focus", "Counts on the view you pick, nothing else on the screen. Hold anywhere to leave it.",
                false, false);
        final TextView vb = sb("view: " + NM[mode], th.ac, null);
        vb.setTextSize(18);
        vb.setOnClickListener(v -> {
            mode = (mode + 1) % NM.length;
            z(vt);
            vb.setText("view: " + NM[mode]);
            draw();
        });
        card.addView(vb);
        card.addView(new Sl(this, () -> {
            closeSheet();
            cv = true;
            z(vh);
            draw();
        }), new LinearLayout.LayoutParams(-1, dp(64)));
        TextView no = sb("cancel", th.dim, v -> closeSheet());
        no.setGravity(Gravity.CENTER);
        card.addView(no);
    }

    // "hold and push" slider: drag the knob from the left end to the right end to run go
    // A slider with a knob that has to be held and pushed all the way: left to right for the focus, and
    // (rtl) right to left in red for a reset, so the two are never mixed up. go runs when it is all the way.
    static class Sl extends View {
        final M m;
        final Runnable go;
        final String label;
        final boolean rtl;
        final int col;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float x; // how far the knob is pushed from where it starts, 0 to 1
        boolean drag;

        Sl(M m, Runnable go) {
            this(m, "hold the knob and push to focus", false, m.th.ac, go);
        }

        Sl(M m, String label, boolean rtl, int col, Runnable go) {
            super(m);
            this.m = m;
            this.go = go;
            this.label = label;
            this.rtl = rtl;
            this.col = col;
        }

        // where the knob is, 0 to 1 from the left
        float vx() {
            return rtl ? 1 - x : x;
        }

        @Override protected void onDraw(Canvas c) {
            float h = getHeight(), r = h / 2 - m.dp(6), w = getWidth() - 2 * r - m.dp(12), cx = r + m.dp(6) + vx() * w, cy = h / 2;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Th.mix(m.th.bg, m.th.fg, .15f));
            c.drawRoundRect(m.dp(2), cy - r - m.dp(4), getWidth() - m.dp(2), cy + r + m.dp(4), h, h, p);
            p.setColor((col & 0xFFFFFF) | 0x66000000);
            if (rtl) c.drawRoundRect(cx - r - m.dp(4), cy - r - m.dp(4), getWidth() - m.dp(2), cy + r + m.dp(4), h, h, p);
            else c.drawRoundRect(m.dp(2), cy - r - m.dp(4), cx + r + m.dp(4), cy + r + m.dp(4), h, h, p);
            p.setColor(m.th.dim);
            p.setAlpha((int) (255 * (1 - x)));
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(15 * m.spx);
            c.drawText(label, getWidth() / 2f + (rtl ? -r : r), cy + m.dp(5), p);
            p.setColor(col);
            c.drawCircle(cx, cy, r, p);
            p.setColor(m.th.bg);
            p.setTextSize(22 * m.spx);
            c.drawText(rtl ? "\u2039" : "\u203A", cx, cy + m.dp(7), p);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float r = getHeight() / 2f - m.dp(6), w = getWidth() - 2 * r - m.dp(12), cx = r + m.dp(6) + vx() * w;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    drag = Math.abs(e.getX() - cx) < r * 1.7f; // only the knob
                    if (drag) getParent().requestDisallowInterceptTouchEvent(true);
                    return drag;
                case MotionEvent.ACTION_MOVE:
                    if (!drag) return false;
                    float at = Math.max(0, Math.min(1, (e.getX() - r - m.dp(6)) / w)); // from the left
                    x = rtl ? 1 - at : at;
                    invalidate();
                    if (x >= .97f) { // all the way
                        drag = false;
                        x = 0;
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                        go.run();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (drag) { // not far enough: the knob goes back
                        drag = false;
                        ValueAnimator a = ValueAnimator.ofFloat(x, 0).setDuration(150);
                        a.addUpdateListener(v -> {
                            x = (float) v.getAnimatedValue();
                            invalidate();
                        });
                        a.start();
                    }
                    return true;
                default:
                    return drag;
            }
        }
    }

    // menu "Theme": a strip at the bottom over the live screen. Tap a palette, or swipe the strip left
    // or right, to try it; it is applied at once, to this profile (or to the default without one).
    void strip() {
        if (sp != null) f.removeView(sp);
        sg = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a != null && a.getY() > hs.getBottom() && Math.abs(vx) > 1000 && Math.abs(vx) > 2 * Math.abs(vy))
                    pick(th.pal + (vx < 0 ? 1 : -1), th.mode); // a swipe below the chips (they scroll by themselves)
                return false;
            }
        });
        sp = new LinearLayout(this) {
            @Override public boolean dispatchTouchEvent(MotionEvent e) {
                sg.onTouchEvent(e);
                return super.dispatchTouchEvent(e);
            }
        };
        sp.setOrientation(LinearLayout.VERTICAL);
        sp.setPadding(dp(8), dp(8), dp(8), dp(8));
        f.addView(sp, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        stripUi();
    }

    // theme of this profile (or the default): saved, shown on the screen and on the strip
    void pick(int a, int m) {
        a = (a + Th.NAMES.length) % Th.NAMES.length;
        p.edit().putString(pf.isEmpty() ? "th" : "th:" + pf, a + "," + m).apply();
        skin();
        draw();
        stripUi();
    }

    TextView sb(String s, int col, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(col);
        t.setTextSize(14);
        t.setPadding(dp(7), dp(10), dp(7), dp(10));
        t.setOnClickListener(l);
        return t;
    }

    void stripUi() {
        sp.removeAllViews();
        sp.setBackgroundColor(Th.mix(th.bg, th.fg, .1f));
        hs = new HorizontalScrollView(this); // the palettes, each chip in its own colours
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout r = new LinearLayout(this);
        for (int i = 0; i < Th.NAMES.length; i++) {
            final int k = i;
            Th c = new Th(this, i + "," + th.mode);
            TextView t = new TextView(this);
            t.setText(c.title());
            t.setTextSize(11);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(c.ac);
            GradientDrawable g = new GradientDrawable();
            g.setColor(c.bg);
            g.setCornerRadius(dp(8));
            g.setStroke(dp(i == th.pal ? 3 : 1), i == th.pal ? th.fg : th.dim);
            t.setBackground(g);
            t.setOnClickListener(v -> pick(k, th.mode));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(76), dp(44));
            lp.setMargins(dp(3), 0, dp(3), 0);
            r.addView(t, lp);
        }
        hs.addView(r);
        sp.addView(hs);
        hs.post(() -> hs.scrollTo(Math.max(0, (int) (dp(82) * (th.pal + .5f) - hs.getWidth() / 2f)), 0)); // the chosen one in the middle
        LinearLayout m = new LinearLayout(this); // dark / light / auto, done
        m.setGravity(Gravity.CENTER_VERTICAL);
        for (int i : Th.ORDER) {
            final int k = i;
            m.addView(sb(i == th.mode ? "[" + Th.MODES[i] + "]" : Th.MODES[i], i == th.mode ? th.ac : th.dim,
                    v -> pick(th.pal, k)));
        }
        m.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        m.addView(sb("done", th.fg, v -> sp.setVisibility(View.GONE)));
        sp.addView(m);
        if (!pf.isEmpty()) { // a profile can have its own theme or follow the default
            TextView h = new TextView(this);
            h.setText(p.contains("th:" + pf) ? pf + " has its own theme" : pf + " uses the default theme");
            h.setTextColor(th.dim);
            h.setPadding(dp(10), 0, dp(10), 0);
            sp.addView(h);
            LinearLayout d = new LinearLayout(this);
            d.addView(sb("use default", th.fg, v -> {
                p.edit().remove("th:" + pf).apply();
                skin();
                draw();
                stripUi();
            }));
            d.addView(sb("make default", th.fg, v -> {
                p.edit().putString("th", th.pal + "," + th.mode).remove("th:" + pf).apply();
                stripUi();
                Toast.makeText(this, "Default theme set", Toast.LENGTH_SHORT).show();
            }));
            sp.addView(d);
        }
    }

    // menu "Goto": a search box and the slide list (every word must be found, in the title first,
    // else in the text of the slide); tap a slide to go there. Advanced: the repeats.
    void goTo() {
        if (pr == null) return;
        final int at = slide(), k = pr.c.length;
        final String[] it = new String[k], nt = new String[k], nc = new String[k];
        for (int i = 0; i < k; i++) {
            it[i] = (i == at ? "\u25B6 " : "    ") + (i + 1) + ". " + pr.ti[i]
                    + (pr.c[i] > 1 ? "  " + cn[i] + "/" + pr.c[i] : cn[i] > 0 ? "  \u2713" : "");
            StringBuilder b = new StringBuilder();
            for (Vw w : pr.v[i]) for (String x : w.t) b.append(x).append(' ');
            nt[i] = nz(pr.ti[i]);
            nc[i] = nz(b.toString());
        }
        final EditText q = new EditText(this);
        q.setSingleLine();
        q.setHint("Search title and text");
        q.setHintTextColor(th.dim);
        q.setTextColor(th.fg);
        final ListView lv = new ListView(this);
        final ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1) {
            @Override public View getView(int at, View old, android.view.ViewGroup par) { // the colours of the theme
                TextView t = (TextView) super.getView(at, old, par);
                t.setTextColor(th.fg);
                t.setTextSize(15);
                return t;
            }
        };
        final ArrayList<Integer> ix = new ArrayList<>(); // the slides shown, best first
        final Runnable fl = () -> {
            String qs = nz(q.getText().toString()).trim();
            String[] ts = qs.isEmpty() ? new String[0] : qs.split("\\s+");
            final int[] sc = new int[k];
            ix.clear();
            for (int i = 0; i < k; i++) {
                int sum = 0;
                for (String w : ts) {
                    int m = fz(w, nt[i]);
                    m = m >= 0 ? m + 5000 : fz(w, nc[i]); // a title match beats any match in the text
                    if (m < 0) {
                        sum = -1;
                        break;
                    }
                    sum += m;
                }
                sc[i] = sum;
                if (sum >= 0) ix.add(i);
            }
            ix.sort((x, y) -> sc[y] - sc[x]);
            ad.clear();
            for (int i : ix) ad.add(it[i]);
        };
        lv.setAdapter(ad);
        fl.run();
        q.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence x, int s1, int s2, int s3) {
            }

            @Override public void onTextChanged(CharSequence x, int s1, int s2, int s3) {
                fl.run();
            }

            @Override public void afterTextChanged(Editable x) {
            }
        });
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), 0, dp(16), 0);
        l.addView(q);
        l.addView(lv, new LinearLayout.LayoutParams(-1, getResources().getDisplayMetrics().heightPixels / 2));
        TextView h = new TextView(this);
        h.setText(pf);
        h.setTextSize(18);
        h.setTextColor(th.fg);
        h.setPadding(dp(24), dp(20), dp(24), dp(8));
        final Sh d = Sh.of(this, th).setCustomTitle(h).setView(l)
                .setNeutralButton("Advanced", (dg, w) -> repeats()).create();
        lv.setOnItemClickListener((pa, pv, pos, pid) -> {
            d.dismiss();
            land();
            cur = ix.get(pos);
            cf.t0 = 0;
            z(vt);
            draw();
        });
        d.show();
        lv.setSelection(Math.max(0, at - 2));
    }

    static int num(EditText e) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (Exception x) {
            return 0; // empty or not a number
        }
    }

    // Goto > Advanced: one repeat for all the slides of this profile, or, with "Individual repeats",
    // one for every slide; kept per profile (rp:<profile>, "*N" for all, else one number per slide)
    void repeats() {
        if (pr == null) return;
        final int k = pr.c.length;
        String sv = p.getString("rp:" + pf, "");
        boolean one = sv.startsWith("*");
        String[] a = (one ? "" : sv).split(",", -1);
        final EditText all = new EditText(this);
        all.setInputType(InputType.TYPE_CLASS_NUMBER);
        all.setHint("as in the profile");
        all.setHintTextColor(th.dim);
        all.setTextColor(th.fg);
        if (one) all.setText(sv.substring(1));
        final EditText[] ed = new EditText[k];
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < k; i++) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            TextView tt = new TextView(this);
            tt.setText((i + 1) + ". " + pr.ti[i]);
            tt.setTextColor(th.fg);
            tt.setSingleLine();
            tt.setEllipsize(TextUtils.TruncateAt.END);
            r.addView(tt, new LinearLayout.LayoutParams(0, -2, 1f));
            ed[i] = new EditText(this);
            ed[i].setInputType(InputType.TYPE_CLASS_NUMBER);
            ed[i].setHint("" + pr.c0[i]);
            ed[i].setHintTextColor(th.dim);
            ed[i].setTextColor(th.fg);
            ed[i].setGravity(Gravity.CENTER);
            if (i < a.length) ed[i].setText(a[i].trim());
            r.addView(ed[i], new LinearLayout.LayoutParams(dp(90), -2));
            l.addView(r);
        }
        final ScrollView sc = new ScrollView(this);
        sc.addView(l);
        final CheckBox ck = new CheckBox(this);
        ck.setText("Individual repeats");
        ck.setTextColor(th.fg);
        ck.setChecked(!one && !sv.isEmpty());
        sc.setVisibility(ck.isChecked() ? View.VISIBLE : View.GONE);
        ck.setOnCheckedChangeListener((cb, on) -> {
            sc.setVisibility(on ? View.VISIBLE : View.GONE);
            if (on) for (EditText x : ed) if (x.getText().length() == 0) x.setText(all.getText()); // start from the common value
        });
        TextView hint = new TextView(this);
        hint.setText("Same repeat for all slides (empty keeps the profile's own):");
        hint.setTextColor(th.dim);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(8), dp(20), dp(4));
        root.addView(hint);
        root.addView(all);
        root.addView(ck);
        root.addView(sc, new LinearLayout.LayoutParams(-1, getResources().getDisplayMetrics().heightPixels * 2 / 5));
        Sh.of(this, th).setTitle("Repeats").setView(root)
                .setPositiveButton("Save", (dg, w) -> {
                    String res;
                    if (ck.isChecked()) {
                        StringBuilder b = new StringBuilder();
                        boolean any = false;
                        for (int i = 0; i < k; i++) {
                            int v = num(ed[i]);
                            boolean own = v < 1 || v == pr.c0[i];
                            any |= !own;
                            b.append(i > 0 ? "," : "").append(own ? "" : "" + v);
                        }
                        res = any ? b.toString() : "";
                    } else {
                        int v = num(all);
                        res = v < 1 ? "" : "*" + v;
                    }
                    setRepeats(res);
                })
                .setNeutralButton("Reset", (dg, w) -> setRepeats(""))
                .setNegativeButton("Cancel", null).show();
    }

    void setRepeats(String csv) {
        if (csv.isEmpty()) p.edit().remove("rp:" + pf).apply();
        else p.edit().putString("rp:" + pf, csv).apply();
        ov(pr, csv);
        draw();
    }

    // the counts, for Undo: the slide and the count of every slide, or the plain count
    int[] counts() {
        if (pr == null) return new int[]{n};
        int[] a = new int[cn.length + 1];
        a[0] = cur;
        System.arraycopy(cn, 0, a, 1, cn.length);
        return a;
    }

    void putCounts(int[] a) {
        if (pr == null) {
            n = a[0];
            keepN();
        } else {
            cur = a[0];
            System.arraycopy(a, 1, cn, 0, cn.length);
        }
        cf.t0 = 0;
    }

    // Sets the target of one slide (kept in the repeats, rp:<profile>). Empty, 0 or the profile's own
    // value goes back to the profile's own.
    void setTarget(int s, int v) {
        int k = pr.c.length;
        String sv = p.getString("rp:" + pf, "");
        String[] a = new String[k];
        Arrays.fill(a, "");
        if (sv.startsWith("*")) {
            Arrays.fill(a, sv.substring(1).trim());
        } else {
            String[] q = sv.split(",", -1);
            for (int i = 0; i < k && i < q.length; i++) a[i] = q[i].trim();
        }
        a[s] = v < 1 || v == pr.c0[s] ? "" : "" + v;
        StringBuilder b = new StringBuilder();
        boolean any = false;
        for (int i = 0; i < k; i++) {
            any |= !a[i].isEmpty();
            b.append(i > 0 ? "," : "").append(a[i]);
        }
        setRepeats(any ? b.toString() : "");
    }

    TextView dtx(String s, int col) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(col);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        return v;
    }

    LinearLayout drow(TextView l, EditText e) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(e, new LinearLayout.LayoutParams(dp(110), -2));
        return r;
    }

    // The adjust dialog (the "adjust" button): the count and the target (of this slide, or the goal of
    // the plain counter) to set, a reset and Undo. A reset and Undo happen at once and the dialog stays
    // open, so a slip can be undone; Save applies the two fields. Resetting the whole profile needs a hold.
    void adjust() {
        if (edit || rb) return;
        final boolean prof = pr != null;
        final int k = prof ? pr.c.length : 0;
        final ArrayList<int[]> ust = new ArrayList<>();
        final LinearLayout card = sheet("adjust", null, true, true);
        final TextView tt = (TextView) card.getChildAt(0); // the title: the slide
        final EditText ec = new EditText(this), et = new EditText(this);
        for (EditText e : new EditText[]{ec, et}) {
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
            e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
            e.setTextColor(th.fg);
            e.setHintTextColor(th.dim);
            e.setGravity(Gravity.CENTER);
            e.setSelectAllOnFocus(true);
            e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(th.dim));
        }
        et.setHint(prof ? "as in the profile" : "off");
        final TextView ud = dtx("Undo", th.ac);
        final TextView rc = dtx(prof ? "Reset this slide" : "Reset to 0", th.fg);
        final TextView rp = dtx("Hold to reset the whole profile", th.warn);
        for (TextView v : new TextView[]{ud, rc, rp}) v.setPadding(0, dp(14), 0, dp(14));
        // the red sliders of the two resets: not shown until a reset is held (see below)
        final Runnable[] act = new Runnable[2];
        final Sl slSlide = new Sl(this, prof ? "push to reset this slide" : "push to reset to 0", true, th.bad, () -> act[0].run());
        final Sl slProf = new Sl(this, "push to reset the whole profile", true, th.bad, () -> act[1].run());
        slSlide.setVisibility(View.GONE);
        slProf.setVisibility(View.GONE);
        final LinearLayout fl = new LinearLayout(this);
        fl.setOrientation(LinearLayout.VERTICAL);
        fl.setPadding(0, dp(8), 0, 0);
        fl.addView(drow(dtx("Count", th.fg), ec));
        fl.addView(drow(dtx(prof ? "Target of this slide" : "Goal (empty = off)", th.fg), et));
        fl.addView(rc);
        fl.addView(slSlide, new LinearLayout.LayoutParams(-1, dp(64)));
        card.addView(fl);
        card.addView(ud);
        if (prof) {
            card.addView(rp);
            card.addView(slProf, new LinearLayout.LayoutParams(-1, dp(64)));
        }
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        final TextView sv = sb("Save", th.ac, null), cb = sb("Close", th.dim, v -> closeSheet());
        for (TextView b2 : new TextView[]{sv, cb}) {
            b2.setTextSize(18);
            b2.setGravity(Gravity.CENTER);
            bar.addView(b2, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        card.addView(bar);

        // Resets and Undo happen at once and the sheet stays open, so a slip can be undone; Save applies
        // the two fields. This shows the current state: the title, the two fields, whether there is
        // something to undo.
        final Runnable rf = () -> {
            slSlide.setVisibility(View.GONE);
            slProf.setVisibility(View.GONE);
            boolean fin = prof && cur >= k;
            int s = prof ? Math.min(cur, k - 1) : 0;
            tt.setText(!prof ? "Counter" : fin ? "Profile completed" : (s + 1) + ". " + pr.ti[s]);
            fl.setVisibility(fin ? View.GONE : View.VISIBLE);
            if (!fin) {
                ec.setText("" + (prof ? cn[s] : n));
                et.setText(prof ? "" + pr.c[s] : mx > 0 ? "" + mx : "");
            }
            ud.setEnabled(!ust.isEmpty());
            ud.setTextColor(ust.isEmpty() ? th.dim : th.ac);
        };
        // The resets. A tap on the one of the slide acts at once (there is Undo); a hold on either one shows
        // its red slider, to be pushed from the right to the left, so a reset is never an accident.
        act[0] = () -> {
            ust.add(counts());
            if (prof) cn[Math.min(cur, k - 1)] = 0; // stays on the slide
            else {
                n = 0;
                keepN();
            }
            cf.t0 = 0;
            z(vr);
            draw();
            rf.run();
        };
        act[1] = () -> {
            ust.add(counts());
            cur = 0;
            Arrays.fill(cn, 0);
            cf.t0 = 0;
            z(vr);
            draw();
            rf.run();
        };
        rc.setOnClickListener(v -> act[0].run());
        rc.setOnLongClickListener(v -> {
            slProf.setVisibility(View.GONE);
            slSlide.setVisibility(View.VISIBLE);
            z(vh);
            return true;
        });
        rp.setOnClickListener(v -> Toast.makeText(this, "Hold to reset the whole profile", Toast.LENGTH_SHORT).show());
        rp.setOnLongClickListener(v -> {
            slSlide.setVisibility(View.GONE);
            slProf.setVisibility(View.VISIBLE);
            z(vh);
            return true;
        });
        ud.setOnClickListener(v -> {
            if (ust.isEmpty()) return;
            putCounts(ust.remove(ust.size() - 1));
            z(vt);
            draw();
            rf.run();
        });
        sv.setOnClickListener(v -> {
            if (!(prof && cur >= k)) {
                int s = prof ? cur : 0;
                if (ec.getText().length() > 0) { // an empty count is left as it is
                    if (prof) {
                        cn[s] = num(ec);
                    } else {
                        n = num(ec);
                        keepN();
                    }
                }
                if (prof) {
                    setTarget(s, num(et));
                } else {
                    mx = num(et);
                    p.edit().putString("mx", "" + mx).apply();
                }
            }
            draw();
            closeSheet();
        });
        rf.run();
    }

    // top-left menu
    void menu(View anchor) {
        LinearLayout card = sheet("menu", null, true, false);
        if (!pf.isEmpty()) mrow(card, sm ? "Profile mode: " + pf : "Simple mode", () -> toggleMode());
        mrow(card, "Settings", () -> {
            p.edit().putString("pf", pf).apply(); // the screens show this window's profile and may change it
            chain = true;
            startActivity(new Intent(this, S.class));
        });
        mrow(card, "Profiles", () -> {
            p.edit().putString("pf", pf).apply();
            chain = true;
            startActivity(new Intent(this, P.class));
        });
        mrow(card, "Edit layout", () -> enterEdit());
        if (pr != null) mrow(card, "Goto", () -> goTo());
        mrow(card, "Theme", () -> strip());
        mrow(card, "Reminders", () -> {
            p.edit().putString("pf", pf).apply(); // the screen has the theme of this window
            startActivity(new Intent(this, N.class));
        });
        mrow(card, Fb.on ? "Floating counter: on" : "Floating counter", () -> bubble());
        mrow(card, "Help", () -> help(false));
        TextView no = sb("cancel", th.dim, v -> closeSheet());
        no.setGravity(Gravity.CENTER);
        card.addView(no);
    }

    // a row of the menu sheet: the sheet closes, then the action runs
    void mrow(LinearLayout card, String label, Runnable act) {
        TextView t = sb(label, th.fg, v -> {
            closeSheet();
            act.run();
        });
        t.setTextSize(18);
        t.setPadding(dp(7), dp(12), dp(7), dp(12));
        card.addView(t);
    }

    // Pinch (dhikr panel only): resizes the text of the current slide and view; the scale is
    // kept for good and only forgotten by "reset to defaults" in the settings.
    String zk() {
        int s = slide();
        return "fz:" + pf + ":" + s + ":" + Math.min(vi, pr.v[s].length - 1);
    }

    @Override public boolean onScaleBegin(ScaleGestureDetector d) {
        if (pr == null || cv || slide() >= pr.c.length) return false;
        zs = Math.min(p.getFloat(zk(), 1f), zmx);
        return true;
    }

    @Override public boolean onScale(ScaleGestureDetector d) {
        zs = Math.max(.4f, Math.min(Math.min(2.5f, zmx), zs * d.getScaleFactor()));
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
            adjust();
            return;
        }
        if (x == vw) {
            focusSheet();
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
                N.done(this, pf); // a kept reminder of this profile goes away
                em = EM[new Random().nextInt(EM.length)];
                if (!cv) cf.go();
            }
        } else {
            n++;
            keepN();
            z(mx > 0 && n == mx ? vl : n % st == 0 ? vc : vt);
            if (n % st == 0 || mx > 0 && n == mx) N.done(this, ""); // a lap (or the goal) of the plain counter
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
            keepN();
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
        if (x == rs) { // nothing is reset by a hold any more: it opens the same dialog
            z(vt);
            adjust();
            return true;
        } else if (cv) { // a hold leaves focus; the way in is the slider of the focus sheet
            cv = false;
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
        int col, mode, n, st, sz, cols, rows, bad = 0xFFFF5252;
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
        // Its size follows the count q = n % st: it grows to a third of the board, then each apple
        // evolves it (two tail blocks go, one block of the next lap colour joins at the head).
        // Taps made while the panel was up, a new lap or slide, or -1 are caught up on in sync().
        final ArrayList<Integer> ap = new ArrayList<>();
        final Random rnd = new Random();
        final Runnable tk = () -> invalidate();
        final int[] DX = {1, 0, -1, 0}, DY = {0, 1, 0, -1};
        int[] sn; // cells (y * cols + x), head first
        int len, dir, gc, nb, gr, ev, pq = -1, nx; // nb: head blocks in the next lap colour, gr / ev: growth / evolutions still to apply, pq: the count it is at
        long t0;

        @Override protected void onVisibilityChanged(View v, int vis) {
            super.onVisibilityChanged(v, vis);
            pq = -1; // catch up on the count when shown again
        }

        // one apple eaten: grow, or evolve once it is as long as the board allows
        void unit() {
            if (nb + ev == 0 && len + gr < sn.length / 3) gr++;
            else if (nb + ev < sn.length / 6) ev++;
        }

        void drop() {
            if (ap.size() > 19) return;
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
                if (nc[d] < 0 || oc[nc[d]] || gr > 0 && nc[d] == sn[len - 1]) {
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
            if (ap.remove(Integer.valueOf(nc[pick]))) unit();
            int nl = len + 1; // the new head, minus what leaves at the tail
            if (gr > 0) gr--;
            else if (ev > 0) {
                ev--;
                nb++;
                nl -= 2;
            } else nl--;
            System.arraycopy(sn, 0, sn, 1, len);
            sn[0] = nc[pick];
            len = nl;
        }

        void game(Canvas c, float ox, float oy) {
            if (cols < 5 || rows < 3) return;
            if (sn == null || gc != cols || sn.length != cols * rows) {
                sn = new int[cols * rows];
                gc = cols;
                len = 3;
                dir = 0;
                nb = gr = ev = 0;
                pq = -1;
                ap.clear();
                for (int i = 0; i < 3; i++) sn[i] = rows / 2 * cols + cols / 2 - i;
            }
            int q = n % st, d = q - pq, big = sn.length / 3, g = big - 3;
            if (pq < 0 || d < 0 || d > 5) { // back from the panel, a new lap or slide, a jump: become what this count makes
                nb = Math.max(0, Math.min(q - g, big / 2));
                int tl = q <= g ? 3 + q : big - nb;
                ev = 0;
                len = Math.min(len, tl);
                gr = tl - len;
            } else {
                for (int i = 0; i < d; i++) drop();
            }
            pq = q;
            long now = System.currentTimeMillis();
            if (now - t0 >= 90) {
                t0 = now;
                step();
            }
            if (sn != null) {
                for (int i = 0; i < len + ap.size(); i++) {
                    int z = i < len ? sn[i] : ap.get(i - len);
                    p.setColor(i >= len ? bad : i < nb ? nx : col);
                    float x = ox + z % cols * sz, y = oy + z / cols * sz;
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
        float lx, ly, tick;
        boolean moved, undo;
        final Runnable hold; // the undo button was held: reset the layout
        final RectF raw = new RectF(); // where the finger puts the selected element, before it snaps
        final ArrayList<float[]> hist = new ArrayList<>(); // the layouts before every change: undo
        float[] snap; // the layout when the current gesture began
        final ArrayList<Float> gx = new ArrayList<>(), gy = new ArrayList<>(); // guide lines shown while something snaps

        Ed(M m) {
            super(m);
            this.m = m;
            sd = new ScaleGestureDetector(m, this);
            hold = () -> {
                undo = false;
                sel = -1;
                push(m.snapshot());
                m.resetLayout();
                invalidate();
                Toast.makeText(m, "Layout reset (tap undo to bring it back)", Toast.LENGTH_SHORT).show();
            };
        }

        void push(float[] a) {
            if (a == null) return;
            hist.add(a);
            if (hist.size() > 50) hist.remove(0);
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

        // the lines (x or y) things snap to: screen edges and centre, edges and centres of the other elements
        float[] gl(boolean x) {
            float[] a = new float[3 + 3 * (m.er.length - 1)];
            float W = getWidth(), H = getHeight();
            int n = 0;
            a[n++] = 0;
            a[n++] = (x ? W : H) / 2;
            a[n++] = x ? W : H;
            for (int i = 0; i < m.er.length; i++) {
                if (i == sel) continue;
                RectF r = m.er[i];
                a[n++] = x ? r.left : r.top;
                a[n++] = x ? r.centerX() : r.centerY();
                a[n++] = x ? r.right : r.bottom;
            }
            return a;
        }

        // how far to move so that the nearest of the positions f lands on a line, if one is within 8dp
        float shift(float[] f, boolean x) {
            float lim = m.dp(8), sh = 0;
            for (float q : gl(x))
                for (float v : f)
                    if (Math.abs(q - v) < lim) {
                        lim = Math.abs(q - v);
                        sh = q - v;
                    }
            return sh;
        }

        // remembers the lines that the positions f sit on, to draw them
        void mark(float[] f, boolean x) {
            for (float q : gl(x))
                for (float v : f)
                    if (Math.abs(q - v) < 1) (x ? gx : gy).add(q);
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
                    gx.clear();
                    gy.clear();
                    float cx = getWidth() / 2f, cy = m.dp(26), rad = m.dp(24);
                    if (Math.hypot(x - (cx + m.dp(30)), y - cy) < rad) { // check: leave the editor
                        m.exitEdit();
                        return true;
                    }
                    if (Math.hypot(x - (cx - m.dp(30)), y - cy) < rad) { // undo: tap = a step back, hold = reset
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
                    if (mode != 0) {
                        snap = m.snapshot();
                        raw.set(m.er[sel]);
                    }
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (undo || e.getPointerCount() > 1 || sel < 0 || mode == 0 || mode == 3) return true;
                    float dx = x - lx, dy = y - ly;
                    lx = x;
                    ly = y;
                    RectF r = m.er[sel];
                    gx.clear();
                    gy.clear();
                    if (mode == 1) {
                        raw.offset(dx, dy);
                        r.set(raw);
                        m.fix(sel, true);
                        r.offset(shift(new float[]{r.left, r.centerX(), r.right}, true),
                                shift(new float[]{r.top, r.centerY(), r.bottom}, false));
                    } else {
                        if ((sides & 1) != 0) raw.left += dx;
                        if ((sides & 2) != 0) raw.top += dy;
                        if ((sides & 4) != 0) raw.right += dx;
                        if ((sides & 8) != 0) raw.bottom += dy;
                        r.set(raw);
                        m.fix(sel, false);
                        if ((sides & 1) != 0) r.left += shift(new float[]{r.left}, true);
                        if ((sides & 4) != 0) r.right += shift(new float[]{r.right}, true);
                        if ((sides & 2) != 0) r.top += shift(new float[]{r.top}, false);
                        if ((sides & 8) != 0) r.bottom += shift(new float[]{r.bottom}, false);
                    }
                    mark(new float[]{r.left, r.centerX(), r.right}, true);
                    mark(new float[]{r.top, r.centerY(), r.bottom}, false);
                    float t = (gx.isEmpty() ? 0 : gx.get(0)) + 4000 * (gy.isEmpty() ? 0 : gy.get(0));
                    if (t != 0 && t != tick) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); // a snap
                    tick = t;
                    m.place1(sel);
                    moved = true;
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (undo) {
                        removeCallbacks(hold);
                        undo = false;
                        if (hist.isEmpty()) {
                            Toast.makeText(m, "Nothing to undo (hold the button to reset the layout)", Toast.LENGTH_SHORT).show();
                        } else {
                            sel = -1;
                            m.restore(hist.remove(hist.size() - 1));
                            invalidate();
                        }
                        return true;
                    }
                    if (moved && sel >= 0 && mode != 3) {
                        m.save(sel);
                        push(snap);
                    }
                    snap = null;
                    mode = 0;
                    moved = false;
                    gx.clear();
                    gy.clear();
                    tick = 0;
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }

        @Override public boolean onScaleBegin(ScaleGestureDetector d) {
            if (sel < 0) return false;
            mode = 3;
            if (snap == null) snap = m.snapshot();
            return true;
        }

        @Override public boolean onScale(ScaleGestureDetector d) {
            RectF r = m.er[sel];
            float s = d.getScaleFactor();
            r.inset(-r.width() * (s - 1) / 2, -r.height() * (s - 1) / 2);
            m.fix(sel, false);
            raw.set(r);
            m.place1(sel);
            moved = true;
            invalidate();
            return true;
        }

        @Override public void onScaleEnd(ScaleGestureDetector d) {
            if (moved && sel >= 0) {
                m.save(sel);
                push(snap);
            }
            snap = null;
            mode = 0;
            moved = false;
        }

        @Override protected void onDraw(Canvas c) {
            float u = m.dp(1);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u);
            p.setColor((m.th.fg & 0xFFFFFF) | 0x88000000);
            for (int i = 0; i < m.er.length; i++) if (i != sel) c.drawRect(m.er[i], p);
            p.setColor(m.th.warn); // the guide lines of a snap
            for (float q : gx) c.drawLine(q, 0, q, getHeight(), p);
            for (float q : gy) c.drawLine(0, q, getWidth(), q, p);
            if (sel >= 0) {
                RectF r = m.er[sel];
                p.setColor(m.th.ac);
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
            p.setColor(m.th.ac);
            c.drawCircle(cx + 30 * u, cy, 20 * u, p);
            p.setColor(0xFF000000);
            c.drawText("\u2713", cx + 30 * u, cy + 8 * u, p);
            p.setColor(0xFF444444);
            c.drawCircle(cx - 30 * u, cy, 20 * u, p);
            p.setColor(0xFFFFFFFF);
            c.drawText("\u21BA", cx - 30 * u, cy + 8 * u, p);
            p.setColor(m.th.dim);
            p.setTextSize(12 * m.spx);
            c.drawText("tap an element, drag to move, pinch to scale, it snaps to guides", cx, 62 * u, p);
            c.drawText("drag an edge or corner to resize; undo: tap = step back, hold = reset", cx, 78 * u, p);
        }
    }

    // view dots at the left edge, like a launcher's page indicator: one per view of the
    // slide, the current one lit in the slide colour, centred in the view's height
    static class Pd extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int n, cur, col, off = 0xFF444444;

        Pd(Context x) {
            super(x);
        }

        @Override protected void onDraw(Canvas c) {
            float u = getResources().getDisplayMetrics().density;
            float pitch = Math.min(16 * u, (getWidth() - 8 * u) / Math.max(1, n));
            float x = (getWidth() - (n - 1) * pitch) / 2f;
            for (int i = 0; i < n; i++) {
                p.setColor(i == cur ? col : off);
                c.drawCircle(x + i * pitch, getHeight() / 2f, (i == cur ? 4 : 3) * u, p);
            }
        }
    }

    // Small line glyphs: 0 expand (corners pointing out), 1 leave the expanded view (corners pointing
    // in), 2 chevron up, 3 chevron down. Colour in col, size from the view.
    static class Gl extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path q = new Path();
        final int type;
        int col = 0xFFFFFFFF;

        Gl(Context c, int t) {
            super(c);
            type = t;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override protected void onDraw(Canvas c) {
            float u = getResources().getDisplayMetrics().density, s = Math.min(getWidth(), getHeight());
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            p.setColor(col);
            p.setStrokeWidth(2.2f * u);
            q.reset();
            if (type < 2) {
                float b = type == 0 ? s * .2f : s * .08f, l = s * .14f, d = type == 0 ? -1 : 1;
                for (int sx = -1; sx <= 1; sx += 2)
                    for (int sy = -1; sy <= 1; sy += 2) {
                        float px = cx + sx * b, py = cy + sy * b;
                        q.moveTo(px, py + sy * d * l);
                        q.lineTo(px, py);
                        q.lineTo(px + sx * d * l, py);
                    }
            } else {
                float w = s * .2f, h = s * .1f, v = type == 2 ? -1 : 1; // up: the tip is at the top
                q.moveTo(cx - w, cy - v * h);
                q.lineTo(cx, cy + v * h);
                q.lineTo(cx + w, cy - v * h);
            }
            c.drawPath(q, p);
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
