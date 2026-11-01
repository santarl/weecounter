package io.rfsj.weecounter;

import android.content.Context;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The Quran as data: downloaded once, kept in the files of the app as one small file per surah (so a surah is
// read in a moment and nothing of it sits in the preferences), and read from there. Q is the screen.
final class Qd {
    // Where it comes from: the profile of the whole Quran in the profile repo, one slide a verse, the
    // Arabic (Tanzil, Uthmani) and the English (Pickthall) as two views. If the file is moved, change this.
    static final String SRC = B.BASE + "profiles/quran-full.dhikr";

    // ---- the basmala --------------------------------------------------------------------------------------
    //
    // WHY THE TEXT IS LEFT AS IT IS AND THE BASMALA IS TAKEN OFF WHEN IT IS SHOWN
    //
    // In the Hafs numbering the basmala is an ayah only in Al-Fatiha (1:1). At the head of every other surah,
    // except At-Tawba (9), which has none, it is a line of its own that is not counted. Tanzil's Uthmani text
    // (which the downloaded file holds) has it in front of ayah 1 of surahs 2 to 114 (except 9), in the same
    // line: 2:1 reads "basmala + Alif Lam Mim". Shown as it is, the basmala would be numbered, recited and
    // repeated (in hifz) as part of ayah 1, and the Arabic of the verse would not match its English.
    //
    // Tanzil's terms allow its text to be copied as it is ("changing it is not allowed"), so the text is NOT edited
    // anywhere: not in the repo, not in the files made from it here (split() copies it byte for byte). The app
    // takes the basmala off ayah 1 when it SHOWS it (strip), and shows it on a slide of its own in front of ayah 1,
    // with no number, as in the printed mushaf. If Tanzil's file ever changes (the basmala as a line of its own,
    // say), strip() finds nothing to take off and nothing else has to change.
    //
    // Two spellings are in the file: the usual one, as in 1:1, and, in surahs 95 and 97, one with a shadda on the
    // ba (the connected form after the last letter of the surah before, which ends in a ba). Only ayah 1 of the
    // surahs that open with it is touched: 27:30 has the words of the basmala inside the verse (Solomon's
    // letter) and must stay whole, and 1:1 is the basmala itself.
    static final String BASMALA = "\u0628\u0650\u0633\u0652\u0645\u0650 \u0671\u0644\u0644\u0651\u064E\u0647\u0650 "
            + "\u0671\u0644\u0631\u0651\u064E\u062D\u0652\u0645\u064E\u0670\u0646\u0650 \u0671\u0644\u0631\u0651\u064E\u062D\u0650\u064A\u0645\u0650";
    static final String BASMALA_SHADDA = "\u0628\u0651" + BASMALA.substring(1);
    static final String BASMALA_EN = "In the name of Allah, the Beneficent, the Merciful."; // as in 1:1 of the file
    static final String BASMALA_TITLE = "Basmalah \u00B7 \u0627\u0644\u0628\u0633\u0645\u0644\u0629";

    // does the surah open with the basmala as a line of its own (not counted)?
    static boolean hasBasmala(int surah) {
        return surah != 1 && surah != 9;
    }

    // the Arabic of an ayah without the basmala that Tanzil puts in front of ayah 1 (see above)
    static String strip(int surah, int ayah, String ar) {
        if (ayah != 1 || !hasBasmala(surah)) return ar;
        for (String b : new String[]{BASMALA, BASMALA_SHADDA}) {
            if (ar.startsWith(b + " ") && ar.length() > b.length() + 1) return ar.substring(b.length() + 1);
        }
        return ar;
    }

    // ---- the data ---------------------------------------------------------------------------------------------

    static class Sura {
        int n, ayahs;
        String name, ar, meaning; // Al-Baqara, the Arabic name with "sura", The Cow
    }

    static class Ayah {
        int n, juz;
        String ar, en;
    }

    static File dir(Context c) {
        return new File(c.getFilesDir(), "quran");
    }

    static boolean have(Context c) {
        return new File(dir(c), "index.txt").exists() && new File(dir(c), "114.txt").exists();
    }

    // Downloads the file and splits it. Slow: not for the main thread. true if all 114 surahs are there.
    static boolean download(Context c) {
        try {
            String text = B.read(B.open(SRC).getInputStream(), Integer.MAX_VALUE).replace("\uFEFF", "");
            return split(c, text);
        } catch (Exception e) {
            return false;
        }
    }

    static void rm(File f) {
        File[] k = f.listFiles();
        if (k != null) for (File x : k) rm(x);
        f.delete();
    }

    private static Writer out(File f) throws Exception {
        return new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
    }

    // Splits the profile into a file for each surah: the first line has its names and the number of its ayahs, then
    // a line for every ayah: the number, the juz, the Arabic and the English, tab apart, with the text copied as it is.
    static boolean split(Context c, String text) {
        File tmp = new File(c.getFilesDir(), "quran.tmp");
        try {
            rm(tmp);
            tmp.mkdirs();
            Pattern head = Pattern.compile("^# \u200E(.+?) \u00B7 (.*)$");
            Pattern verse = Pattern.compile("^# \u200E(\\d+):(\\d+)/(\\d+) \\| (\\d+) \\|.*$");
            Pattern cnt = Pattern.compile("^(.*) \\((\\d+) ayahs\\)$");
            String[] ls = text.split("\n");
            StringBuilder index = new StringBuilder();
            Writer w = null;
            int sn = 0;
            for (int i = 0; i + 1 < ls.length; i++) {
                String l = ls[i];
                if (head.matcher(l).matches() && i + 3 < ls.length) { // a heading slide: the Arabic name, the name, the meaning (n ayahs)
                    String ar = ls[i + 1].replace("[50]", "").trim(), nm = ls[i + 2].replace("[25]", "").trim(), me = ls[i + 3].replace("[25]", "").trim();
                    int k = 0;
                    Matcher cm = cnt.matcher(me);
                    if (cm.matches()) {
                        me = cm.group(1);
                        k = Integer.parseInt(cm.group(2));
                    }
                    if (w != null) w.close();
                    sn++;
                    w = out(new File(tmp, sn + ".txt"));
                    w.write(nm + "\t" + ar + "\t" + me + "\t" + k + "\n");
                    index.append(sn).append('\t').append(nm).append('\t').append(ar).append('\t').append(me).append('\t').append(k).append('\n');
                    continue;
                }
                Matcher m = verse.matcher(l);
                if (w != null && m.matches()) {
                    String body = ls[i + 1];
                    if (body.startsWith("[_]")) body = body.substring(3);
                    int bar = body.indexOf(" | ");
                    w.write(m.group(2) + "\t" + m.group(4) + "\t" + (bar < 0 ? body : body.substring(0, bar)) + "\t"
                            + (bar < 0 ? "" : body.substring(bar + 3)) + "\n");
                }
            }
            if (w != null) w.close();
            if (sn != 114) throw new Exception("114 surahs expected, found " + sn);
            Writer iw = out(new File(tmp, "index.txt"));
            iw.write(index.toString());
            iw.close();
            rm(dir(c));
            return tmp.renameTo(dir(c));
        } catch (Exception e) {
            rm(tmp);
            return false;
        }
    }

    private static BufferedReader in(File f) throws Exception {
        return new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
    }

    // the surahs, in order
    static ArrayList<Sura> suras(Context c) {
        ArrayList<Sura> out = new ArrayList<>();
        try (BufferedReader r = in(new File(dir(c), "index.txt"))) {
            String l;
            while ((l = r.readLine()) != null) {
                String[] f = l.split("\t", -1);
                if (f.length < 5) continue;
                Sura s = new Sura();
                s.n = Integer.parseInt(f[0]);
                s.name = f[1];
                s.ar = f[2];
                s.meaning = f[3];
                s.ayahs = Integer.parseInt(f[4]);
                out.add(s);
            }
        } catch (Exception e) {
            out.clear();
        }
        return out;
    }

    // the ayahs of a surah, as they are in the file (the basmala is still in front of ayah 1, see strip)
    static ArrayList<Ayah> ayahs(Context c, int n) {
        ArrayList<Ayah> out = new ArrayList<>();
        try (BufferedReader r = in(new File(dir(c), n + ".txt"))) {
            r.readLine(); // the names
            String l;
            while ((l = r.readLine()) != null) {
                String[] f = l.split("\t", 4);
                if (f.length < 4) continue;
                Ayah a = new Ayah();
                a.n = Integer.parseInt(f[0]);
                a.juz = Integer.parseInt(f[1]);
                a.ar = f[2];
                a.en = f[3];
                out.add(a);
            }
        } catch (Exception e) {
            out.clear();
        }
        return out;
    }

    // The biggest text size (px, between min and max) at which s fits w wide and h high, with the line height
    // mult times the usual (Arabic with its marks needs room above and below the line).
    static float fit(CharSequence s, int w, float h, float min, float max, float mult) {
        if (w <= 0 || h <= 0 || s.length() == 0) return min;
        TextPaint tp = new TextPaint();
        float lo = min, hi = max;
        for (int i = 0; i < 9; i++) {
            float mid = (lo + hi) / 2;
            tp.setTextSize(mid);
            StaticLayout sl = StaticLayout.Builder.obtain(s, 0, s.length(), tp, w)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0, mult).build();
            if (sl.getHeight() <= h) lo = mid;
            else hi = mid;
        }
        return lo;
    }
}
