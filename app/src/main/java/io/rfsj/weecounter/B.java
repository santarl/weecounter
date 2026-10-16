package io.rfsj.weecounter;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

// Browse and download the dhikr profiles of the profile repo. Only one small file, the manifest
// (index.json), is fetched: by itself at most once a week (see M.weekly) or by hand here. It is
// cached and only sent again when it changed (ETag). A profile file is fetched only when its
// button is tapped. Manifest: {"profiles":[{"id","name","ver","author","desc","slides","f"}]}
public class B extends Activity implements View.OnClickListener {
    static final String BASE = "https://raw.githubusercontent.com/santarl/dhikr-profile-repo/main/";

    SharedPreferences p, d; // p = settings and the cached manifest, d = the profiles
    LinearLayout ls;
    TextView st;

    // 0 = failed, 1 = manifest unchanged, 2 = new manifest saved
    static int refresh(SharedPreferences p) {
        try {
            HttpURLConnection c = open(BASE + "index.json");
            String et = p.getString("me", null);
            if (et != null) c.setRequestProperty("If-None-Match", et);
            int rc = c.getResponseCode();
            long now = System.currentTimeMillis();
            if (rc == 304) {
                p.edit().putLong("mt", now).apply();
                return 1;
            }
            if (rc != 200) return 0;
            String s = read(c.getInputStream(), 1000000);
            new JSONObject(s).getJSONArray("profiles"); // must be a manifest
            p.edit().putString("mf", s).putString("me", c.getHeaderField("ETag")).putLong("mt", now).apply();
            return 2;
        } catch (Exception e) {
            return 0;
        }
    }

    static HttpURLConnection open(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "weecounter");
        return c;
    }

    static String read(InputStream in, int max) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] x = new byte[4096];
        int k;
        while ((k = in.read(x)) > 0 && b.size() < max) b.write(x, 0, k);
        in.close();
        return b.toString("UTF-8");
    }

    static JSONArray list(SharedPreferences p) {
        try {
            return new JSONObject(p.getString("mf", "")).getJSONArray("profiles");
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    // installed version of a repo profile, -1 if it is not installed
    static int have(SharedPreferences p, SharedPreferences d, String id) {
        String n = p.getString("id:" + id, null), t = n == null ? null : d.getString(n, null);
        if (t == null) return -1;
        try {
            return Integer.parseInt(M.hd(t, "version"));
        } catch (Exception e) {
            return 0;
        }
    }

    // how many installed repo profiles have a newer version in the cached manifest
    static int updates(SharedPreferences p, SharedPreferences d) {
        JSONArray a = list(p);
        int n = 0;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            int h = have(p, d, o.optString("id"));
            if (h >= 0 && h < o.optInt("ver", 1)) n++;
        }
        return n;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences("p", 0);
        d = getSharedPreferences("d", 0);
        int m = dp(16);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(m, m, m, m);
        l.addView(tv("Dhikr profiles shared in the profile repo. Tap a profile to read its description, "
                + "DOWNLOAD to add it, UPDATE to get a newer version (your counts are kept).", 0xFF888888, 14));
        st = tv("", 0xFF888888, 14);
        st.setPadding(0, dp(12), 0, 0);
        l.addView(st);
        TextView ck = tv("CHECK NOW", 0xFF00E676, 16);
        ck.setId(1);
        ck.setPadding(0, dp(8), 0, dp(8));
        ck.setOnClickListener(this);
        l.addView(ck);
        ls = new LinearLayout(this);
        ls.setOrientation(LinearLayout.VERTICAL);
        l.addView(ls);

        ScrollView s = new ScrollView(this);
        s.setBackgroundColor(0xFF000000);
        s.setFitsSystemWindows(true);
        s.addView(l);
        setContentView(s);
        show();
        if (p.getString("mf", null) == null) check(); // first visit: get the manifest
    }

    int dp(int x) {
        return (int) (x * getResources().getDisplayMetrics().density + .5f);
    }

    TextView tv(String s, int c, int sp) {
        TextView x = new TextView(this);
        x.setText(s);
        x.setTextColor(c);
        x.setTextSize(sp);
        return x;
    }

    void check() {
        st.setText("Checking...");
        new Thread(() -> {
            int r = refresh(p);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (r == 0) Toast.makeText(this, "Could not check, is there a connection?", Toast.LENGTH_SHORT).show();
                show();
            });
        }).start();
    }

    void show() {
        long t = p.getLong("mt", 0);
        st.setText(t == 0 ? "Not checked yet" : "Checked " + DateUtils.getRelativeTimeSpanString(t,
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        ls.removeAllViews();
        JSONArray a = list(p);
        if (a.length() == 0) ls.addView(tv("No profiles listed", 0xFF888888, 16));
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) row(o);
        }
    }

    void row(JSONObject o) {
        String id = o.optString("id"), au = o.optString("author");
        int v = o.optInt("ver", 1), h = have(p, d, id), sl = o.optInt("slides", 0);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        LinearLayout t = new LinearLayout(this);
        t.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout n = new LinearLayout(this);
        n.setOrientation(LinearLayout.VERTICAL);
        n.setPadding(0, dp(14), dp(8), dp(14));
        n.addView(tv(o.optString("name", id), 0xFFFFFFFF, 18));
        n.addView(tv((au.isEmpty() ? "" : "by " + au + "  -  ") + "version " + v
                + (sl > 0 ? "  -  " + sl + " slides" : ""), 0xFF888888, 13));
        t.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView a = tv(h < 0 ? "DOWNLOAD" : h < v ? "UPDATE" : "installed",
                h < 0 ? 0xFF00E676 : h < v ? 0xFFFFC107 : 0xFF888888, 15);
        a.setPadding(dp(8), dp(14), 0, dp(14));
        if (h < v) { // not installed, or older
            a.setId(2);
            a.setTag(o.toString());
            a.setOnClickListener(this);
        }
        t.addView(a);
        r.addView(t);
        TextView ds = tv(o.optString("desc", "No description"), 0xFFCCCCCC, 15);
        ds.setPadding(0, 0, 0, dp(12));
        ds.setVisibility(View.GONE);
        r.addView(ds);
        n.setOnClickListener(x -> ds.setVisibility(ds.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        ls.addView(r);
    }

    @Override public void onClick(View v) {
        if (v.getId() == 1) {
            check();
            return;
        }
        TextView a = (TextView) v;
        a.setText("...");
        a.setOnClickListener(null);
        final String js = (String) v.getTag();
        new Thread(() -> {
            boolean ok = false;
            try {
                JSONObject o = new JSONObject(js);
                String f = o.getString("f"), id = o.getString("id");
                if (!f.matches("[\\w./-]+") || f.contains("..")) throw new Exception();
                String s = read(open(BASE + f).getInputStream(), Integer.MAX_VALUE).replace("\uFEFF", "");
                if (M.parse(s) == null) throw new Exception();
                String nm = p.getString("id:" + id, null);
                if (nm == null) {
                    nm = o.optString("name", id);
                    if (d.contains(nm)) nm += " (repo)"; // do not overwrite a profile of the same name
                }
                d.edit().putString(nm, s).apply();
                p.edit().putString("id:" + id, nm).apply();
                ok = true;
            } catch (Exception e) {
                // reported below
            }
            final boolean done = ok;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                Toast.makeText(this, done ? "Saved. Select it in the profiles" : "Download failed",
                        Toast.LENGTH_SHORT).show();
                show();
            });
        }).start();
    }
}
