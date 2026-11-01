package io.rfsj.weecounter;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;

// The Quran: a page to download it (once), the list of the surahs, and a reader. The reader shows one ayah at
// a time, as big as fits: swipe up for the next ayah and down for the one before (like the slides of a profile,
// and on to the next surah), swipe left or right for the view: the Arabic, the Arabic with the English under it,
// or the English. Tap the top to go to an ayah or to the list. Where you stopped is kept. The data is in Qd,
// where it is also told why the basmala is shown on a slide of its own.
public class Q extends Activity {
    SharedPreferences p;
    Th th;
    float u;
    LinearLayout main;           // what the screen holds: the download page, the list or the reader
    ArrayList<Qd.Sura> suras;
    Qd.Sura sura;                // the surah that is open in the reader, null in the list
    ArrayList<Qd.Ayah> ayahs;
    int n, pos, view;            // the surah, the place in it (0 is the basmala when it has one), the view (0 Arabic, 1 both, 2 English)
    LinearLayout body;
    TextView head, sub, ar, en, foot;
    float x0, y0;

    @Override protected void onCreate(Bundle b) {
        p = getSharedPreferences("p", 0);
        th = Th.get(this, p, "");
        setTheme(th.theme());
        super.onCreate(b);
        th.bars(this);
        u = getResources().getDisplayMetrics().density;
        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundColor(th.bg);
        setContentView(main);
        start();
    }

    int dp(float v) {
        return (int) (v * u + .5f);
    }

    TextView txt(String s, int col, float sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(col);
        t.setTextSize(sp);
        return t;
    }

    // where to begin: the download page if the text is not here yet, else where you stopped, else the list
    void start() {
        if (!Qd.have(this)) {
            offer();
            return;
        }
        suras = Qd.suras(this);
        if (suras.size() != 114) {
            offer();
            return;
        }
        view = p.getInt("qv", 1);
        if (p.contains("qs")) open(p.getInt("qs", 1), p.getInt("qp", 0));
        else list();
    }

    // ---- the page to download it ----------------------------------------------------------------------------

    void offer() {
        sura = null;
        main.removeAllViews();
        main.setPadding(dp(24), dp(48), dp(24), dp(24));
        main.addView(txt("Quran", th.fg, 26));
        TextView d = txt("The text is not on this phone yet. It is one download of about 2.4 MB: the Arabic (Tanzil, Uthmani) "
                + "with the English of Pickthall. It is kept on the phone, so it works without a connection.", th.dim, 15);
        d.setPadding(0, dp(12), 0, dp(24));
        main.addView(d);
        final TextView st = txt("", th.fg, 15);
        st.setPadding(0, dp(16), 0, 0);
        final TextView go = txt("Download", th.ac, 20);
        go.setPadding(0, dp(12), 0, dp(12));
        go.setOnClickListener(v -> {
            go.setEnabled(false);
            st.setText("Downloading...");
            new Thread(() -> {
                final boolean ok = Qd.download(this);
                runOnUiThread(() -> {
                    if (ok) {
                        main.setPadding(0, 0, 0, 0);
                        start();
                    } else {
                        st.setText("The download did not work. Is there a connection?");
                        go.setEnabled(true);
                    }
                });
            }).start();
        });
        main.addView(go);
        main.addView(st);
    }

    // ---- the list of the surahs -------------------------------------------------------------------------------

    void list() {
        sura = null;
        main.removeAllViews();
        main.setPadding(0, 0, 0, 0);
        TextView t = txt("Quran", th.fg, 26);
        t.setPadding(dp(20), dp(24), dp(20), dp(8));
        main.addView(t);
        ListView lv = new ListView(this);
        lv.setDividerHeight(0);
        lv.setAdapter(new ArrayAdapter<Qd.Sura>(this, 0, suras) {
            @Override public View getView(int i, View old, ViewGroup par) {
                Qd.Sura s = getItem(i);
                LinearLayout r = new LinearLayout(Q.this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(dp(20), dp(10), dp(20), dp(10));
                TextView num = txt("" + s.n, th.dim, 14);
                num.setMinWidth(dp(36));
                LinearLayout mid = new LinearLayout(Q.this);
                mid.setOrientation(LinearLayout.VERTICAL);
                mid.addView(txt(s.name, th.fg, 17));
                mid.addView(txt(s.meaning + " \u00B7 " + s.ayahs + " ayahs", th.dim, 13));
                r.addView(num);
                r.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
                r.addView(txt(s.ar, th.fg, 20));
                return r;
            }
        });
        lv.setOnItemClickListener((pa, v, i, id) -> open(i + 1, 0));
        main.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    // ---- the reader ------------------------------------------------------------------------------------------------

    // opens surah s at place at (-1: the last one)
    void open(int s, int at) {
        n = Math.max(1, Math.min(114, s));
        sura = suras.get(n - 1);
        ayahs = Qd.ayahs(this, n);
        if (ayahs.isEmpty()) { // the files are damaged: download again
            Qd.rm(Qd.dir(this));
            offer();
            return;
        }
        int total = (Qd.hasBasmala(n) ? 1 : 0) + ayahs.size();
        pos = at < 0 ? total - 1 : Math.max(0, Math.min(total - 1, at));
        main.removeAllViews();
        main.setPadding(0, 0, 0, 0);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(20), dp(24), dp(20), dp(12));
        head = txt("", th.fg, 20);
        sub = txt("", th.dim, 14);
        top.addView(head);
        top.addView(sub);
        top.setOnClickListener(v -> gotoSheet());
        main.addView(top);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER);
        body.setPadding(dp(20), dp(8), dp(20), dp(8));
        ar = txt("", th.fg, 30);
        ar.setGravity(Gravity.CENTER);
        ar.setLineSpacing(0, 1.3f);
        en = txt("", th.fg, 18);
        en.setGravity(Gravity.CENTER);
        body.addView(ar, new LinearLayout.LayoutParams(-1, -2));
        body.addView(en, new LinearLayout.LayoutParams(-1, -2));
        body.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fit());
        main.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        foot = txt("", th.dim, 12);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(dp(20), dp(8), dp(20), dp(20));
        main.addView(foot);
        render();
    }

    // shows the ayah (or the basmala) at the place, and remembers the place
    void render() {
        boolean bas = Qd.hasBasmala(n);
        int total = (bas ? 1 : 0) + ayahs.size();
        String a, e, ref;
        int juz = ayahs.get(0).juz;
        if (bas && pos == 0) {
            a = Qd.BASMALA;
            e = Qd.BASMALA_EN;
            ref = Qd.BASMALA_TITLE;
        } else {
            Qd.Ayah y = ayahs.get(pos - (bas ? 1 : 0));
            a = Qd.strip(n, y.n, y.ar);
            e = y.en;
            ref = n + ":" + y.n;
            juz = y.juz;
        }
        head.setText(sura.name + "   " + sura.ar);
        sub.setText(ref + "   \u00B7   juz " + juz);
        ar.setText(a);
        en.setText(e);
        ar.setVisibility(view == 2 ? View.GONE : View.VISIBLE);
        en.setVisibility(view == 0 ? View.GONE : View.VISIBLE);
        foot.setText((pos + 1) + " / " + total + "   \u00B7   swipe up for the next, sideways for the view");
        p.edit().putInt("qs", n).putInt("qp", pos).putInt("qv", view).apply();
        fit();
    }

    // the text as big as fits: the Arabic and the English share the room by the view
    void fit() {
        if (sura == null || body == null || body.getWidth() == 0) return;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        int w = body.getWidth() - body.getPaddingLeft() - body.getPaddingRight();
        float h = body.getHeight() - body.getPaddingTop() - body.getPaddingBottom();
        if (view == 0) {
            ar.setTextSize(TypedValue.COMPLEX_UNIT_PX, Qd.fit(ar.getText(), w, h * .94f, 18 * sp, 56 * sp, 1.3f));
        } else if (view == 2) {
            en.setTextSize(TypedValue.COMPLEX_UNIT_PX, Qd.fit(en.getText(), w, h * .94f, 14 * sp, 34 * sp, 1f));
        } else {
            ar.setTextSize(TypedValue.COMPLEX_UNIT_PX, Qd.fit(ar.getText(), w, h * .6f, 16 * sp, 46 * sp, 1.3f));
            en.setTextSize(TypedValue.COMPLEX_UNIT_PX, Qd.fit(en.getText(), w, h * .32f, 12 * sp, 24 * sp, 1f));
        }
    }

    // one ayah on (d = 1) or back (-1); past the end of a surah it goes on into the next, and back into the one before
    void go(int d) {
        int total = (Qd.hasBasmala(n) ? 1 : 0) + ayahs.size();
        int np = pos + d;
        if (np >= total) {
            if (n < 114) open(n + 1, 0);
        } else if (np < 0) {
            if (n > 1) open(n - 1, -1);
        } else {
            pos = np;
            render();
        }
    }

    // a number to go to an ayah of this surah, or to the list of the surahs
    void gotoSheet() {
        final EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setTextColor(th.fg);
        e.setHintTextColor(th.dim);
        e.setGravity(Gravity.CENTER);
        e.setHint("ayah 1 to " + ayahs.size());
        Sh.of(this, th).setTitle(sura.name).setView(e)
                .setPositiveButton("Go", (d, w) -> {
                    int a = M.num(e);
                    if (a >= 1 && a <= ayahs.size()) {
                        pos = (Qd.hasBasmala(n) ? 1 : 0) + a - 1;
                        render();
                    }
                })
                .setNeutralButton("Surahs", (d, w) -> list())
                .setNegativeButton("Close", null).show();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (sura != null && !Sh.isUp(this)) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                x0 = e.getX();
                y0 = e.getY();
            } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = e.getX() - x0, dy = e.getY() - y0;
                boolean edge = y0 > getResources().getDisplayMetrics().heightPixels - dp(56); // the home gesture, not a swipe
                if (Math.max(Math.abs(dx), Math.abs(dy)) > dp(64)) {
                    if (Math.abs(dy) >= Math.abs(dx)) {
                        if (!(edge && dy < 0)) go(dy < 0 ? 1 : -1);
                    } else {
                        view = Math.floorMod(view + (dx < 0 ? 1 : -1), 3);
                        render();
                    }
                }
            }
        }
        return super.dispatchTouchEvent(e);
    }

    @Override public void onBackPressed() {
        if (Sh.back(this)) return;
        if (sura != null) list(); // from the reader to the list; from the list out of the Quran
        else super.onBackPressed();
    }
}
