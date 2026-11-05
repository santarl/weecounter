package io.rfsj.weecounter;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AlignmentSpan;
import android.text.style.RelativeSizeSpan;
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

// The Quran: a page to download it (once), the list of the surahs, and a reader with two ways to read.
//
// An ayah at a time: the ayah as big as fits. Swipe up for the next ayah and down for the one before (like the
// slides of a profile, and on into the next surah), left or right for the view: the Arabic, the Arabic with the English
// under it, or the English.
//
// A page of the mushaf: the 604 pages of the Madani mushaf, each with the same first and last ayah as the printed
// page (Qm), set in the Amiri Quran font as big as fits the screen, justified, with the end sign and the number after
// every ayah and the name and the basmala where a surah begins. The words are set again to the width of the screen, so
// the lines are not those of the printed page. Swipe up or down, or right for the next page (as a page is turned in
// an Arabic book) and left for the one before.
//
// Tap the top to go to an ayah or a page, to change the way to read, or to see the sources. Where you stopped is kept.
// The data is in Qd, where it is also told why the basmala is shown on a slide of its own.
public class Q extends Activity {
    SharedPreferences p;
    Th th;
    float u;
    Typeface font;               // Amiri Quran (assets), null if it could not be loaded
    LinearLayout main;           // what the screen holds: the download page, the list or the reader
    ArrayList<Qd.Sura> suras;
    Qd.Sura sura;                // the surah that is open in the reader (in the page mode: the one the page begins in), null in the list
    ArrayList<Qd.Ayah> ayahs;    // its ayahs
    int mode;                    // 0 an ayah at a time, 1 a page of the mushaf
    int n, pos, view, pg;        // the surah, the place in it (0 is the basmala when it has one), the view (0 Arabic, 1 both, 2 English), the page
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
        try {
            font = Typeface.createFromAsset(getAssets(), "AmiriQuran-Regular.ttf");
        } catch (RuntimeException e) {
            font = null;
        }
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
        mode = p.getInt("qm", 0);
        pg = p.getInt("qpg", 1);
        if (!p.contains("qs")) list();
        else if (mode == 1) openPage(pg);
        else open(p.getInt("qs", 1), p.getInt("qp", 0));
    }

    // a line with the sources, that opens them in full when it is tapped
    TextView credit() {
        TextView t = txt("Arabic: Tanzil Project, tanzil.net  \u00B7  English: Pickthall  \u00B7  Font: Amiri Quran", th.dim, 12);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(20), dp(10), dp(20), dp(14));
        t.setOnClickListener(v -> sources());
        return t;
    }

    // the sources of everything in the Quran screen, as Tanzil's terms and the font's licence ask
    void sources() {
        Sh.of(this, th).setTitle("Sources")
                .setMessage("Arabic text and the tables of pages, juz and ruku: the Tanzil Project, tanzil.net, licensed under "
                        + "Creative Commons Attribution 3.0. The text is not changed.\n\n"
                        + "English: the translation of Marmaduke Pickthall.\n\n"
                        + "Font: Amiri Quran, by Khaled Hosny, under the SIL Open Font License 1.1 (the licence text is in the app's assets).")
                .setNeutralButton("tanzil.net", (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://tanzil.net")));
                    } catch (RuntimeException e) {
                        // no browser
                    }
                })
                .setPositiveButton("OK", null).show();
    }

    // ---- the page to download it ----------------------------------------------------------------------------

    void offer() {
        sura = null;
        body = null;
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
        main.addView(credit());
    }

    // ---- the list of the surahs -------------------------------------------------------------------------------

    void list() {
        sura = null;
        body = null;
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
                mid.addView(txt(s.meaning + " \u00B7 " + s.ayahs + " ayahs \u00B7 page " + Qm.pageOf(s.n, 1), th.dim, 13));
                TextView name = txt(s.ar, th.fg, 20);
                if (font != null) name.setTypeface(font);
                r.addView(num);
                r.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
                r.addView(name);
                return r;
            }
        });
        lv.setOnItemClickListener((pa, v, i, id) -> {
            if (mode == 1) openPage(Qm.pageOf(i + 1, 1));
            else open(i + 1, 0);
        });
        main.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));
        main.addView(credit());
    }

    // ---- the reader ------------------------------------------------------------------------------------------------

    // the screen of the reader, the same for the two ways to read
    void build() {
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
        ar.setLineSpacing(0, 1.3f);
        ar.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE); // the same as in Qd.fit, which measures the text
        ar.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        if (font != null) ar.setTypeface(font);
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
    }

    // opens surah s, an ayah at a time, at place at (-1: the last one)
    void open(int s, int at) {
        mode = 0;
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
        build();
        render();
    }

    // opens page pg of the mushaf
    void openPage(int page) {
        mode = 1;
        pg = Math.max(1, Math.min(Qm.PAGES, page));
        n = Qm.sura(pg);
        sura = suras.get(n - 1);
        ayahs = Qd.ayahs(this, n);
        build();
        render();
    }

    // a page: its text, the surahs on it, the juz it starts in
    static class Pg {
        SpannableStringBuilder text = new SpannableStringBuilder();
        String names = "";
        int juz;
    }

    // the number in the Arabic-Indic digits that go in the end sign of an ayah
    static String indic(int v) {
        StringBuilder b = new StringBuilder();
        for (char c : String.valueOf(v).toCharArray()) b.append((char) ('\u0660' + (c - '0')));
        return b.toString();
    }

    // The text of page pg: from the ayah the page starts with (Qm) up to where the next page starts, every ayah followed by
    // its end sign and number (the font draws the number inside the sign); where a surah begins the name of the surah is in
    // front, in the middle and bigger, and the basmala under it (taken off ayah 1 as everywhere, see Qd).
    Pg page(int page) {
        Pg r = new Pg();
        int s = Qm.sura(page), a = Qm.ayah(page), es = Qm.sura(page + 1), ea = Qm.ayah(page + 1), loaded = 0;
        ArrayList<Qd.Ayah> list = null;
        while (s < es || s == es && a < ea) {
            if (s != loaded) {
                list = Qd.ayahs(this, s);
                loaded = s;
                if (list.isEmpty()) break;
                String nm = suras.get(s - 1).name;
                r.names = r.names.isEmpty() ? nm : r.names + " \u00B7 " + nm;
            }
            if (a == 1) {
                int from = r.text.length();
                r.text.append(suras.get(s - 1).ar).append('\n');
                if (Qd.hasBasmala(s)) r.text.append(Qd.BASMALA).append('\n');
                r.text.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), from, r.text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                r.text.setSpan(new RelativeSizeSpan(1.2f), from, r.text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            Qd.Ayah y = list.get(a - 1);
            if (r.juz == 0) r.juz = y.juz;
            r.text.append(Qd.strip(s, a, y.ar)).append(" \u06DD").append(indic(a)).append(' ');
            a++;
            if (a > list.size()) {
                s++;
                a = 1;
            }
        }
        return r;
    }

    // shows the ayah (or the basmala) or the page, and remembers the place
    void render() {
        if (mode == 1) {
            Pg r = page(pg);
            head.setText("Page " + pg);
            sub.setText(r.names + "   \u00B7   juz " + r.juz);
            ar.setText(r.text);
            ar.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD);
            ar.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            ar.setVisibility(View.VISIBLE);
            en.setVisibility(View.GONE);
            foot.setText(pg + " / " + Qm.PAGES + "   \u00B7   swipe for the next page");
            p.edit().putInt("qs", n).putInt("qm", 1).putInt("qpg", pg).apply();
            fit();
            return;
        }
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
        ar.setJustificationMode(Layout.JUSTIFICATION_MODE_NONE);
        ar.setGravity(Gravity.CENTER);
        ar.setVisibility(view == 2 ? View.GONE : View.VISIBLE);
        en.setVisibility(view == 0 ? View.GONE : View.VISIBLE);
        foot.setText((pos + 1) + " / " + total + "   \u00B7   swipe up for the next, sideways for the view");
        p.edit().putInt("qs", n).putInt("qp", pos).putInt("qv", view).putInt("qm", 0).apply();
        fit();
    }

    // the text as big as fits: the Arabic and the English share the room by the view
    void fit() {
        if (sura == null || body == null || body.getWidth() == 0) return;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        int w = body.getWidth() - body.getPaddingLeft() - body.getPaddingRight();
        float h = body.getHeight() - body.getPaddingTop() - body.getPaddingBottom();
        if (mode == 1) {
            ar.setTextSize(TypedValue.COMPLEX_UNIT_PX, Qd.fit(ar.getText(), w, h * .98f, 9 * sp, 30 * sp, 1.3f, font));
        } else if (view == 0) {
            ar.setTextSize(TypedValue.COMPLEX_UNIT_PX, Qd.fit(ar.getText(), w, h * .94f, 18 * sp, 56 * sp, 1.3f, font));
        } else if (view == 2) {
            en.setTextSize(TypedValue.COMPLEX_UNIT_PX, Qd.fit(en.getText(), w, h * .94f, 14 * sp, 34 * sp, 1f, null));
        } else {
            ar.setTextSize(TypedValue.COMPLEX_UNIT_PX, Qd.fit(ar.getText(), w, h * .6f, 16 * sp, 46 * sp, 1.3f, font));
            en.setTextSize(TypedValue.COMPLEX_UNIT_PX, Qd.fit(en.getText(), w, h * .32f, 12 * sp, 24 * sp, 1f, null));
        }
    }

    // one ayah on (d = 1) or back (-1), past the end of a surah on into the next and back into the one before; or, in the
    // page mode, one page
    void go(int d) {
        if (mode == 1) {
            int np = pg + d;
            if (np < 1 || np > Qm.PAGES) return;
            pg = np;
            n = Qm.sura(pg);
            sura = suras.get(n - 1);
            ayahs = Qd.ayahs(this, n);
            render();
            return;
        }
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

    // from the ayah that is shown to the page it is on, or from the page to its first ayah
    void switchMode() {
        if (mode == 0) {
            int a = Math.max(1, pos - (Qd.hasBasmala(n) ? 1 : 0) + 1);
            openPage(Qm.pageOf(n, a));
        } else {
            int s = Qm.sura(pg), a = Qm.ayah(pg);
            boolean bas = Qd.hasBasmala(s);
            open(s, bas && a == 1 ? 0 : (bas ? 1 : 0) + a - 1);
        }
    }

    // A number to go to (an ayah of this surah, or a page), the other way to read, the list of the surahs, the sources.
    void gotoSheet() {
        final Sh[] dg = new Sh[1];
        final EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setTextColor(th.fg);
        e.setHintTextColor(th.dim);
        e.setGravity(Gravity.CENTER);
        e.setHint(mode == 1 ? "page 1 to " + Qm.PAGES : "ayah 1 to " + ayahs.size());
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.addView(e);
        TextView md = txt(mode == 1 ? "Read an ayah at a time" : "Read a page of the mushaf", th.ac, 16);
        md.setPadding(0, dp(16), 0, dp(6));
        md.setOnClickListener(v -> {
            dg[0].dismiss();
            switchMode();
        });
        TextView so = txt("Sources", th.ac, 16);
        so.setPadding(0, dp(10), 0, dp(6));
        so.setOnClickListener(v -> {
            dg[0].dismiss();
            sources();
        });
        l.addView(md);
        l.addView(so);
        dg[0] = Sh.of(this, th).setTitle(mode == 1 ? "Page " + pg : sura.name).setView(l)
                .setPositiveButton("Go", (d, w) -> {
                    int v = M.num(e);
                    if (mode == 1) {
                        if (v >= 1 && v <= Qm.PAGES) openPage(v);
                    } else if (v >= 1 && v <= ayahs.size()) {
                        pos = (Qd.hasBasmala(n) ? 1 : 0) + v - 1;
                        render();
                    }
                })
                .setNeutralButton("Surahs", (d, w) -> list())
                .setNegativeButton("Close", null).show();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (sura != null && body != null && !Sh.isUp(this)) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                x0 = e.getX();
                y0 = e.getY();
            } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = e.getX() - x0, dy = e.getY() - y0;
                boolean edge = y0 > getResources().getDisplayMetrics().heightPixels - dp(56); // the home gesture, not a swipe
                if (Math.max(Math.abs(dx), Math.abs(dy)) > dp(64)) {
                    if (Math.abs(dy) >= Math.abs(dx)) {
                        if (!(edge && dy < 0)) go(dy < 0 ? 1 : -1);
                    } else if (mode == 1) {
                        go(dx > 0 ? 1 : -1); // as a page is turned in an Arabic book
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
