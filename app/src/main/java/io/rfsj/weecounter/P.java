package io.rfsj.weecounter;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.TreeSet;

// dhikr profiles: pick one (or Off), import / export text files, hold the red x to delete
public class P extends Activity implements View.OnClickListener, View.OnLongClickListener {
    // example profile added on the first visit (Arabic written as escapes to keep the source ASCII)
    static final String EX = "# Example. A line like ---33 starts a slide that is counted 33 times\n"
        + "# (0 skips it, 1 shows it once). The lines below it are the slide's text.\n"
        + "# [70] / [30] in front of a line give it that share of the text area.\n"
        + "---33\n[70]\u0633\u0628\u062D\u0627\u0646 \u0627\u0644\u0644\u0647\n[30]Subhanallah\n"
        + "---33\n[70]\u0627\u0644\u062D\u0645\u062F \u0644\u0644\u0647\n[30]Alhamdulillah\n"
        + "---34\n[70]\u0627\u0644\u0644\u0647 \u0623\u0643\u0628\u0631\n[30]Allahu Akbar\n";

    SharedPreferences p, d; // p = settings, d = the profiles (name -> text)
    LinearLayout ls;
    String ex;              // profile being exported

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences("p", 0);
        d = getSharedPreferences("d", 0);
        if (!p.getBoolean("sd", false)) {
            d.edit().putString("Tasbih 33-33-34", EX).apply();
            p.edit().putBoolean("sd", true).apply();
        }
        int m = dp(16);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(m, m, m, m);
        l.addView(tv("Dhikr profiles are text files (UTF-8, any language). A line like ---33 starts a slide "
                + "that is counted 33 times (0 skips it, 1 shows it once). The lines below it, up to the "
                + "next --- line, are shown above the number. A line can start with [70] to give it that share "
                + "of the text area ([_] = what is left, [10] alone = a spacer, [] hides it). Write alternatives "
                + "of a line with | (Arabic | transliteration | meaning): swipe up or down to switch. "
                + "Swipe right to skip the slide, left to go back.", 0xFF888888));
        TextView im = tv("IMPORT FILE", 0xFF00E676);
        im.setId(4);
        im.setPadding(0, dp(16), 0, dp(8));
        im.setOnClickListener(this);
        l.addView(im);
        ls = new LinearLayout(this);
        ls.setOrientation(LinearLayout.VERTICAL);
        l.addView(ls);

        ScrollView s = new ScrollView(this);
        s.setBackgroundColor(0xFF000000);
        s.setFitsSystemWindows(true);
        s.addView(l);
        setContentView(s);
        fill();
    }

    int dp(int x) {
        return (int) (x * getResources().getDisplayMetrics().density + .5f);
    }

    TextView tv(String s, int c) {
        TextView x = new TextView(this);
        x.setText(s);
        x.setTextColor(c);
        return x;
    }

    void fill() {
        ls.removeAllViews();
        String cur = p.getString("pf", "");
        row("Off - normal counter", "", cur.isEmpty(), false);
        for (String k : new TreeSet<>(d.getAll().keySet())) row(k, k, k.equals(cur), true);
    }

    // ids: 1 = select, 2 = export, 3 = delete (hold), 4 = import
    void row(String label, String key, boolean on, boolean full) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView a = tv((on ? "\u2713 " : "") + label, 0xFFFFFFFF);
        a.setTextSize(18);
        a.setPadding(0, dp(16), dp(8), dp(16));
        a.setId(1);
        a.setTag(key);
        a.setOnClickListener(this);
        r.addView(a, new LinearLayout.LayoutParams(0, -2, 1f));
        if (full) {
            TextView e = tv("export", 0xFF888888);
            e.setPadding(dp(12), dp(16), dp(12), dp(16));
            e.setId(2);
            e.setTag(key);
            e.setOnClickListener(this);
            r.addView(e);
            TextView x = tv("\u2715", 0xFFFF5252);
            x.setTextSize(22);
            x.setPadding(dp(16), dp(12), dp(16), dp(12));
            x.setId(3);
            x.setTag(key);
            x.setOnClickListener(this);
            x.setOnLongClickListener(this);
            r.addView(x);
        }
        ls.addView(r);
    }

    @Override public void onClick(View v) {
        String k = (String) v.getTag();
        switch (v.getId()) {
            case 1:
                p.edit().putString("pf", k).apply();
                fill();
                break;
            case 2: {
                ex = k;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("text/plain").putExtra(Intent.EXTRA_TITLE, k + ".txt");
                startActivityForResult(i, 2);
                break;
            }
            case 3:
                Toast.makeText(this, "Hold to delete", Toast.LENGTH_SHORT).show();
                break;
            default: {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(i, 1);
            }
        }
    }

    @Override public boolean onLongClick(View v) {
        if (v.getId() != 3) return false;
        String k = (String) v.getTag();
        d.edit().remove(k).apply();
        SharedPreferences.Editor e = p.edit().remove("g:" + k);
        if (k.equals(p.getString("pf", ""))) e.putString("pf", "");
        e.apply();
        fill();
        return true;
    }

    @Override protected void onActivityResult(int rq, int rs, Intent r) {
        if (rs != RESULT_OK || r == null) return;
        try {
            if (rq == 2) {
                OutputStream o = getContentResolver().openOutputStream(r.getData());
                o.write(d.getString(ex, "").getBytes("UTF-8"));
                o.close();
                Toast.makeText(this, "Exported", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipData cd = r.getClipData();
            int total = cd == null ? 1 : cd.getItemCount(), ok = 0;
            for (int j = 0; j < total; j++)
                ok += add(cd == null ? r.getData() : cd.getItemAt(j).getUri());
            Toast.makeText(this, "Imported " + ok + " of " + total, Toast.LENGTH_SHORT).show();
            fill();
        } catch (Exception e) {
            Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show();
        }
    }

    // reads one file, keeps it only if it has at least one slide to count; returns 1 if kept
    int add(Uri u) {
        try {
            InputStream in = getContentResolver().openInputStream(u);
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int k;
            while ((k = in.read(buf)) > 0 && b.size() < 300000) b.write(buf, 0, k);
            in.close();
            String s = b.toString("UTF-8").replace("\uFEFF", "");
            if (M.parse(s) == null) return 0;
            d.edit().putString(name(u), s).apply();
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    // profile name = file name without its extension
    String name(Uri u) {
        String n = null;
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst())
                n = c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME));
        } catch (Exception e) {
            // fall through to the last path segment
        }
        if (n == null) n = u.getLastPathSegment();
        if (n == null) n = "profile";
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        n = n.trim();
        return n.isEmpty() ? "profile" : n;
    }
}
