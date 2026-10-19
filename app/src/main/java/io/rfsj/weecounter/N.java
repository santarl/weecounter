package io.rfsj.weecounter;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

// Reminders at a time of day, for a profile or for the plain counter (menu > Reminders). A reminder
// is {i: id, p: profile ("" = the plain counter), d: days (bit n = Calendar day n + 1, 127 = daily),
// h, m: time, k: keep}. A kept reminder stays in the notification bar (and comes back if it is
// swiped away) until the profile is finished, or for the plain counter until a lap is done: M calls
// done() for that. An alarm is set for the next time of every reminder; the receiver Rc posts the
// notification, sets the next alarm, and sets all of them again after a reboot, an update or a
// change of the clock or time zone.
public class N extends Activity {
    static final String REMIND = "io.rfsj.weecounter.REMIND", DISMISS = "io.rfsj.weecounter.DISMISS";
    static final int[] DOW = {2, 3, 4, 5, 6, 7, 1}; // Monday to Sunday as Calendar numbers
    static final String[] DN = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    SharedPreferences p, d; // p = settings and the reminders, d = the profiles
    Th th;
    LinearLayout ls, nt;

    // ---------------------------------------------------------------- stored reminders and alarms

    static JSONArray all(SharedPreferences p) {
        try {
            return new JSONArray(p.getString("rm", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static JSONObject find(JSONArray a, int id) {
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optInt("i") == id) return o;
        }
        return null;
    }

    // the ids of the kept reminders that are waiting to be done (their notification is up)
    static ArrayList<Integer> act(SharedPreferences p) {
        ArrayList<Integer> l = new ArrayList<>();
        for (String s : p.getString("ra", "").split(","))
            if (!s.isEmpty()) l.add(Integer.parseInt(s));
        return l;
    }

    static void act(SharedPreferences p, ArrayList<Integer> l) {
        StringBuilder b = new StringBuilder();
        for (int i : l) b.append(i).append(',');
        p.edit().putString("ra", b.toString()).apply();
    }

    // when the reminder is next due after now (ms), -1 if never
    static long next(JSONObject o) {
        Calendar now = Calendar.getInstance();
        for (int i = 0; i < 8; i++) {
            Calendar t = (Calendar) now.clone();
            t.add(Calendar.DAY_OF_YEAR, i);
            t.set(Calendar.HOUR_OF_DAY, o.optInt("h"));
            t.set(Calendar.MINUTE, o.optInt("m"));
            t.set(Calendar.SECOND, 0);
            t.set(Calendar.MILLISECOND, 0);
            if (t.after(now) && ((o.optInt("d") >> (t.get(Calendar.DAY_OF_WEEK) - 1)) & 1) != 0) return t.getTimeInMillis();
        }
        return -1;
    }

    static PendingIntent pi(Context c, String act, int id, int base) {
        return PendingIntent.getBroadcast(c, base + id, new Intent(c, Rc.class).setAction(act).putExtra("i", id),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // the alarm for the next time of a reminder: exact if the phone lets the app, else a little late
    static void plan(Context c, JSONObject o) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = pi(c, REMIND, o.optInt("i"), 0);
        long t = next(o);
        if (t < 0) am.cancel(pi);
        else if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
    }

    static void planAll(Context c) {
        JSONArray a = all(c.getSharedPreferences("p", 0));
        for (int i = 0; i < a.length(); i++) plan(c, a.optJSONObject(i));
    }

    // stops a reminder: its alarm and its notification
    static void stop(Context c, int id) {
        c.getSystemService(AlarmManager.class).cancel(pi(c, REMIND, id, 0));
        c.getSystemService(NotificationManager.class).cancel(id);
        SharedPreferences p = c.getSharedPreferences("p", 0);
        ArrayList<Integer> l = act(p);
        if (l.remove((Integer) id)) act(p, l);
    }

    // takes a reminder away for good: also its notification channel
    static void drop(Context c, int id) {
        stop(c, id);
        c.getSystemService(NotificationManager.class).deleteNotificationChannel("r" + id);
    }

    // Every reminder has a notification channel of its own, so that each one can get its own sound,
    // vibration and importance in the settings of the phone. A channel that exists keeps what the
    // user chose; only its name follows the reminder.
    static String chan(Context c, JSONObject o) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.deleteNotificationChannel("r"); // the one channel of the first version
        nm.createNotificationChannelGroup(new NotificationChannelGroup("g", "Reminders"));
        String pf = o.optString("p");
        NotificationChannel ch = new NotificationChannel("r" + o.optInt("i"), (pf.isEmpty() ? "Plain counter" : pf)
                + " " + hm(o.optInt("h"), o.optInt("m")) + " " + days(o.optInt("d")), NotificationManager.IMPORTANCE_DEFAULT);
        ch.setGroup("g");
        nm.createNotificationChannel(ch);
        return "r" + o.optInt("i");
    }

    static Icon small() {
        Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        q.setColor(0xFFFFFFFF);
        q.setStrokeWidth(10);
        q.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 4; i++) c.drawLine(16 + i * 21, 20, 16 + i * 21, 76, q);
        c.drawLine(8, 66, 88, 30, q);
        return Icon.createWithBitmap(b);
    }

    // posts the notification of a reminder (again: it comes back after being swiped away or a reboot)
    static void fire(Context c, int id, boolean again) {
        SharedPreferences p = c.getSharedPreferences("p", 0);
        JSONObject o = find(all(p), id);
        if (o == null) return;
        if (!again) plan(c, o); // the next time
        String pf = o.optString("p");
        boolean keep = o.optBoolean("k");
        if (!pf.isEmpty() && !c.getSharedPreferences("d", 0).contains(pf)) return; // its profile is gone
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        Intent go = new Intent(c, M.class).setAction(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setData(Uri.parse(pf.isEmpty() ? "wee://simple" : "wee://profile/" + Uri.encode(pf)));
        Notification.Builder b = new Notification.Builder(c, chan(c, o)).setSmallIcon(small())
                .setContentTitle(pf.isEmpty() ? "weecounter" : pf)
                .setContentText(pf.isEmpty() ? (keep ? "Time to count. Finish a lap to clear this." : "Time to count.")
                        : (keep ? "Time for your dhikr. Finish the profile to clear this." : "Time for your dhikr."))
                .setCategory(Notification.CATEGORY_REMINDER).setOnlyAlertOnce(again)
                .setContentIntent(PendingIntent.getActivity(c, 100000 + id, go,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        if (keep) b.setOngoing(true).setDeleteIntent(pi(c, DISMISS, id, 200000));
        else b.setAutoCancel(true);
        nm.notify(id, b.build());
        if (keep) {
            ArrayList<Integer> l = act(p);
            if (!l.contains(id)) {
                l.add(id);
                act(p, l);
            }
        }
    }

    // M: the profile pf is finished (or, with "", a lap or the goal of the plain counter): the
    // kept reminders of it that are waiting go away
    static void done(Context c, String pf) {
        SharedPreferences p = c.getSharedPreferences("p", 0);
        if (p.getString("ra", "").isEmpty()) return; // nothing is waiting
        JSONArray a = all(p);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        ArrayList<Integer> l = act(p), rest = new ArrayList<>();
        for (int id : l) {
            JSONObject o = find(a, id);
            if (o != null && o.optString("p").equals(pf)) nm.cancel(id);
            else rest.add(id);
        }
        act(p, rest);
    }

    public static class Rc extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            SharedPreferences p = c.getSharedPreferences("p", 0);
            int id = i.getIntExtra("i", -1);
            if (REMIND.equals(a)) fire(c, id, false);
            else if (DISMISS.equals(a)) {
                if (act(p).contains(id)) fire(c, id, true); // a kept reminder comes back until it is done
            } else { // reboot, update, clock or time zone change, exact alarms allowed: all alarms again
                planAll(c);
                for (int k : act(p)) fire(c, k, true); // a reboot cleared the notifications that still wait
            }
        }
    }

    // ---------------------------------------------------------------------------------- screen

    @Override protected void onCreate(Bundle b) {
        SharedPreferences q = getSharedPreferences("p", 0);
        th = Th.get(this, q, q.getString("pf", ""));
        setTheme(th.theme());
        super.onCreate(b);
        th.bars(this);
        p = q;
        d = getSharedPreferences("d", 0);
        int m = dp(16);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(m, m, m, m);
        l.addView(tv("A notification at a time of day, for a profile or for the plain counter. \"Keep until done\" "
                + "makes it stay in the notification bar (it comes back if swiped away) until you finish the profile, "
                + "or, for the plain counter, a lap. \"alert\" opens the sound, vibration and importance of that "
                + "reminder in the settings of the phone.", th.dim, 14));
        nt = new LinearLayout(this);
        nt.setOrientation(LinearLayout.VERTICAL);
        l.addView(nt);
        TextView add = tv("ADD REMINDER", th.ac, 18);
        add.setPadding(0, m, 0, m);
        add.setOnClickListener(v -> choose(x -> form(null, x)));
        l.addView(add);
        ls = new LinearLayout(this);
        ls.setOrientation(LinearLayout.VERTICAL);
        l.addView(ls);
        ScrollView s = new ScrollView(this);
        s.setBackgroundColor(th.bg);
        s.setFitsSystemWindows(true);
        s.addView(l);
        setContentView(s);
    }

    @Override protected void onResume() {
        super.onResume();
        planAll(this); // e.g. after exact alarms were allowed
        status();
        show();
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

    // what keeps reminders from working, with a way to put it right
    void status() {
        nt.removeAllViews();
        if (!getSystemService(NotificationManager.class).areNotificationsEnabled()) {
            line("Notifications of this app are off, so no reminder can show.", "OPEN NOTIFICATION SETTINGS", v -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
                else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            });
        }
        if (Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager.class).canScheduleExactAlarms()) {
            line("Exact alarms are not allowed: a reminder can come a few minutes late.", "ALLOW EXACT TIMES", v ->
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))));
        }
    }

    void line(String s, String b, View.OnClickListener l) {
        TextView t = tv(s, th.warn, 14);
        t.setPadding(0, dp(12), 0, 0);
        nt.addView(t);
        TextView x = tv(b, th.ac, 15);
        x.setPadding(0, dp(6), 0, dp(6));
        x.setOnClickListener(l);
        nt.addView(x);
    }

    void show() {
        ls.removeAllViews();
        JSONArray a = all(p);
        if (a.length() == 0) ls.addView(tv("No reminders yet.", th.dim, 16));
        ArrayList<JSONObject> l = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) l.add(a.optJSONObject(i));
        l.sort((x, y) -> (x.optInt("h") * 60 + x.optInt("m")) - (y.optInt("h") * 60 + y.optInt("m")));
        for (JSONObject o : l) row(o);
    }

    static String hm(int h, int m) {
        return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
    }

    static String days(int m) {
        if (m == 127) return "daily";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 7; i++)
            if (((m >> (DOW[i] - 1)) & 1) != 0) b.append(b.length() > 0 ? " " : "").append(DN[i]);
        return b.toString();
    }

    void row(final JSONObject o) {
        final int id = o.optInt("i");
        final String pf = o.optString("p");
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout n = new LinearLayout(this);
        n.setOrientation(LinearLayout.VERTICAL);
        n.setPadding(0, dp(10), dp(8), dp(10));
        boolean gone = !pf.isEmpty() && !d.contains(pf);
        n.addView(tv(pf.isEmpty() ? "Plain counter" : pf + (gone ? " (profile is gone)" : ""), gone ? th.warn : th.fg, 18));
        n.addView(tv(hm(o.optInt("h"), o.optInt("m")) + "  " + days(o.optInt("d")) + "  "
                + (o.optBoolean("k") ? "kept until done" : "can be swiped away"), th.dim, 13));
        r.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView e = tv("edit", th.dim, 15), c = tv("copy", th.dim, 15), al = tv("alert", th.dim, 15), x = tv("\u2715", th.bad, 22);
        for (TextView t : new TextView[]{e, c, al, x}) {
            t.setPadding(dp(10), dp(14), dp(10), dp(14));
            r.addView(t);
        }
        e.setOnClickListener(v -> form(o, pf));
        al.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS) // sound, vibration, importance of this reminder
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID, chan(this, o))));
        c.setOnClickListener(v -> {
            JSONArray a = all(p);
            try {
                JSONObject k = new JSONObject(o.toString());
                k.put("i", p.getInt("rn", 1));
                p.edit().putInt("rn", p.getInt("rn", 1) + 1).apply();
                a.put(k);
                p.edit().putString("rm", a.toString()).apply();
                chan(this, k);
                plan(this, k);
            } catch (Exception ex) {
                // not saved
            }
            show();
        });
        x.setOnClickListener(v -> Toast.makeText(this, "Hold to delete", Toast.LENGTH_SHORT).show());
        x.setOnLongClickListener(v -> {
            JSONArray a = all(p), b = new JSONArray();
            for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i).optInt("i") != id) b.put(a.optJSONObject(i));
            p.edit().putString("rm", b.toString()).apply();
            drop(this, id);
            show();
            return true;
        });
        ls.addView(r);
    }

    // which profile (or the plain counter, "")
    void choose(final Consumer<String> r) {
        final ArrayList<String> n = new ArrayList<>(new TreeSet<>(d.getAll().keySet()));
        String[] it = new String[n.size() + 1];
        it[0] = "Plain counter (simple mode)";
        for (int i = 0; i < n.size(); i++) it[i + 1] = n.get(i);
        th.dlg(this).setTitle("Remind for").setItems(it, (dg, i) -> r.accept(i == 0 ? "" : n.get(i - 1))).show();
    }

    void paint(TextView t, boolean on) {
        t.setBackgroundColor(on ? th.ac : Th.mix(th.bg, th.fg, .1f));
        t.setTextColor(on ? th.bg : th.dim);
    }

    // days, time and whether to keep it; old = the reminder being edited (null: a new one)
    void form(final JSONObject old, String pf0) {
        final String[] pf = {pf0};
        final int[] mk = {old == null ? 127 : old.optInt("d", 127)};
        final int[] tm = {old == null ? 8 : old.optInt("h"), old == null ? 0 : old.optInt("m")};
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(8), dp(20), dp(4));
        final TextView who = tv(pf[0].isEmpty() ? "Plain counter" : pf[0], th.fg, 18);
        who.setPadding(0, dp(8), 0, dp(8));
        who.setOnClickListener(v -> choose(x -> {
            pf[0] = x;
            who.setText(x.isEmpty() ? "Plain counter" : x);
        }));
        l.addView(tv("For (tap to change)", th.dim, 13));
        l.addView(who);
        l.addView(tv("Days", th.dim, 13));
        LinearLayout row = new LinearLayout(this);
        final TextView[] ch = new TextView[7];
        for (int i = 0; i < 7; i++) {
            final int k = i;
            ch[i] = tv(DN[i].substring(0, 2), th.fg, 14);
            ch[i].setGravity(Gravity.CENTER);
            ch[i].setPadding(0, dp(10), 0, dp(10));
            paint(ch[i], ((mk[0] >> (DOW[i] - 1)) & 1) != 0);
            ch[i].setOnClickListener(v -> {
                mk[0] ^= 1 << (DOW[k] - 1);
                paint(ch[k], ((mk[0] >> (DOW[k] - 1)) & 1) != 0);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(dp(2), dp(4), dp(2), dp(4));
            row.addView(ch[i], lp);
        }
        l.addView(row);
        TextView daily = tv("every day", th.ac, 15);
        daily.setPadding(0, dp(4), 0, dp(8));
        daily.setOnClickListener(v -> {
            mk[0] = 127;
            for (TextView t : ch) paint(t, true);
        });
        l.addView(daily);
        l.addView(tv("Time (tap to change)", th.dim, 13));
        final TextView at = tv(hm(tm[0], tm[1]), th.fg, 24);
        at.setPadding(0, dp(4), 0, dp(8));
        at.setOnClickListener(v -> new TimePickerDialog(this, th.light ? android.R.style.Theme_DeviceDefault_Light_Dialog
                : android.R.style.Theme_DeviceDefault_Dialog, (tp, h, mi) -> {
            tm[0] = h;
            tm[1] = mi;
            at.setText(hm(h, mi));
        }, tm[0], tm[1], DateFormat.is24HourFormat(this)).show());
        l.addView(at);
        final CheckBox ck = new CheckBox(this);
        ck.setText("Keep until done");
        ck.setTextColor(th.fg);
        ck.setChecked(old != null && old.optBoolean("k"));
        l.addView(ck);
        l.addView(tv("A profile: until it is finished. The plain counter: until a lap is done. Without it the "
                + "notification can be swiped away.", th.dim, 13));
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        th.dlg(this).setTitle(old == null ? "New reminder" : "Edit reminder").setView(sv).setPositiveButton("Save", (dg, w) -> {
            JSONArray a = all(p);
            JSONObject o = old == null ? new JSONObject() : find(a, old.optInt("i"));
            if (o == null) return;
            try {
                if (old == null) {
                    o.put("i", p.getInt("rn", 1));
                    p.edit().putInt("rn", p.getInt("rn", 1) + 1).apply();
                    a.put(o);
                }
                o.put("p", pf[0]).put("d", mk[0] == 0 ? 127 : mk[0]).put("h", tm[0]).put("m", tm[1]).put("k", ck.isChecked());
            } catch (Exception ex) {
                return;
            }
            p.edit().putString("rm", a.toString()).apply();
            if (old != null) stop(this, o.optInt("i")); // a changed reminder starts over
            chan(this, o); // so that it is in the settings of the phone from now on
            plan(this, o);
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
            status();
            show();
        }).setNegativeButton("Cancel", null).show();
    }

    @Override public void onRequestPermissionsResult(int c, String[] p, int[] r) {
        status();
    }
}
