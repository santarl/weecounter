package io.rfsj.weecounter;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Display;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import java.util.Arrays;

// The floating counter: a round bubble over other apps that counts with a tap. Drag it where you
// want it (it stays where you drop it), fling it at an edge to make it stick there, drop it on the X
// that appears at the bottom to close it. It counts what the app is on: the current slide of the
// profile in use (the slide moves on at its target, as in the app), or the plain counter. Both live
// in the settings file, which the app writes at every redraw and takes over at once (see M.live and
// M.takeOver). When the slide changes while the app is not on screen, a comic speech bubble with
// the new slide fades in beside the head and out again.
public class Fb extends Service {
    static boolean on;                // the bubble is up
    static boolean auto;              // it came up by itself when the app was minimized (see App)
    static boolean autoNext;          // the next one is started by App
    static final String STOP = "io.rfsj.weecounter.STOP_BUBBLE";
    WindowManager wm;
    SharedPreferences p;
    Th th;
    Bb bb;                            // the bubble
    Xt xt, it, ct, ot;                // the X, the i (in a profile), the cog and the open button: only while the bubble is picked up
    Sp speech;                        // the speech bubble, only while it is shown
    Runnable fadeR;                   // what fades it out
    WindowManager.LayoutParams bp, xp, yp;
    float u;                          // one dp in px
    int size, W, H;                   // the bubble's size, the screen's size (px)
    int bx, by, halo;                 // the bubble's top left corner (it may be partly off the screen), and the room round it in its window
    float fu, fv;                     // the middle of the bubble, as a fraction of the screen in its natural position (see toFrac)
    int rot;                          // the way the screen is turned (Surface.ROTATION_*)
    boolean dragging;                 // the bubble is being dragged
    int style;                        // the look of the bubble (see Sy)
    Cz cz;                            // the customising, while it is up
    String last = "";                 // profile:slide the speech bubble last saw ("" = none yet)
    static final long[] BUZZ_NEAR = {0, 25};          // the bubble got over the X: a short tick
    static final long[] BUZZ_CLOSE = {0, 40, 60, 90};  // dropped on the X: two buzzes, the second longer
    static final long[] BUZZ_INFO = {0, 20, 40, 20};   // dropped on the i: two short ones
    final SharedPreferences.OnSharedPreferenceChangeListener nl = (sp, k) -> {
        if ("bs".equals(k)) resize();
        if ("bstyle".equals(k)) { // a new look from the customising
            style = Math.floorMod(p.getInt("bstyle", 0), Sy.COUNT);
            if (bb != null) {
                bb.sync();
                bb.invalidate();
            }
        }
        if (k == null || k.equals("n") || k.equals("bt") || k.equals("pf") || k.equals("pm") || k.startsWith("cn:") || k.startsWith("cu:")
                || k.startsWith("rp:") || k.startsWith("th")) // the app counted or changed profile, theme or repeats
            refresh();
    };

    @Override public IBinder onBind(Intent i) {
        return null;
    }

    @Override public void onCreate() {
        super.onCreate();
        wm = getSystemService(WindowManager.class);
        p = getSharedPreferences("p", 0);
        th = Th.get(this, p, "");
        style = Math.floorMod(p.getInt("bstyle", 0), Sy.COUNT);
        u = getResources().getDisplayMetrics().density;
        metrics();
        size = sizePx();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("fb", "Floating counter", NotificationManager.IMPORTANCE_LOW));
        String bt = prof();
        if (bt != null && !getSharedPreferences("d", 0).contains(bt)) bt = null; // the profile is gone
        Intent open = openIntent();
        Notification nf = new Notification.Builder(this, "fb").setSmallIcon(N.small())
                .setContentTitle("Floating counter: " + (bt == null ? "plain counter" : bt))
                .setContentText("Tap here to open it. Tap the bubble to count, hold it to move it.")
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, open,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(new Notification.Action.Builder(N.small(), "Close",
                        PendingIntent.getService(this, 1, new Intent(this, Fb.class).setAction(STOP),
                                PendingIntent.FLAG_IMMUTABLE)).build())
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, nf, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, nf);
        auto = autoNext;
        autoNext = false;
        if (auto && App.fg) { // the app was back before the bubble was up
            stopSelf();
            return;
        }

        bb = new Bb();
        refresh();
        // The window is a little bigger than the bubble, so that the bubble can swell in it (it does when it is
        // picked up) without being cut off; it lets the bubble hang partly off the screen, and it is laid out
        // in the whole screen, so what is counted from the edges is the same for every window.
        halo = (int) (size * .1f);
        bp = new WindowManager.LayoutParams(size + 2 * halo, size + 2 * halo, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        bp.gravity = Gravity.TOP | Gravity.START;
        if (p.contains("bu")) {
            fu = p.getFloat("bu", 1f);
            fv = p.getFloat("bv", .33f);
            fromFrac();
        } else { // the first time: at the right edge, a third of the way down
            bx = W - size;
            by = H / 3;
            clampB();
            toFrac();
        }
        bp.x = bx - halo;
        bp.y = by - halo;
        try {
            wm.addView(bb, bp);
        } catch (RuntimeException e) { // the permission was taken back
            Toast.makeText(this, "Allow \"Display over other apps\" for weecounter", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        p.registerOnSharedPreferenceChangeListener(nl);
        on = true;
    }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        if (i != null && STOP.equals(i.getAction())) stopSelf(); // the Close button of the notification
        return START_NOT_STICKY;
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        endCz(false);
        metrics();
        relayout(); // the screen has turned: the bubble stays where it was on the phone
    }

    @Override public void onDestroy() {
        on = false;
        auto = false;
        p.unregisterOnSharedPreferenceChangeListener(nl);
        hideX();
        hideSay();
        endCz(false);
        try {
            wm.removeView(bb);
        } catch (RuntimeException ignored) {
        }
        super.onDestroy();
    }

    // the size of the whole screen (with the bars) and the way it is turned
    void metrics() {
        DisplayMetrics dm = new DisplayMetrics();
        Display d = wm.getDefaultDisplay();
        d.getRealMetrics(dm);
        W = dm.widthPixels;
        H = dm.heightPixels;
        rot = d.getRotation();
    }

    // The diameter in px: the setting, or 67dp (the old 56dp and a fifth more) when it is 0.
    int sizePx() {
        int d = ni("bs", 0);
        return Math.max((int) (24 * u), Math.min(d > 0 ? d : (int) (67 * u), Math.min(W, H) / 2));
    }

    // a new size from the setting, round the same middle
    void resize() {
        toFrac();
        relayout();
    }

    // The size or the screen changed: the window is made again from the size and from where the bubble
    // is on the phone (fu, fv), which stays the same when the screen turns.
    void relayout() {
        if (bp == null) return;
        size = sizePx();
        halo = (int) (size * .1f);
        bp.width = size + 2 * halo;
        bp.height = size + 2 * halo;
        fromFrac();
        place();
    }

    // the window follows the bubble
    void place() {
        bp.x = bx - halo;
        bp.y = by - halo;
        try {
            wm.updateViewLayout(bb, bp);
        } catch (RuntimeException ignored) {
        }
    }

    // How much of the bubble may be off the screen: next to an edge it hangs partly over it, as docked.
    int hang() {
        return (int) (size * .45f);
    }

    void clampB() {
        bx = Math.max(-hang(), Math.min(W - size + hang(), bx));
        by = Math.max(-hang(), Math.min(H - size + hang(), by));
    }

    // The middle of the bubble as a fraction of the screen the way it is when the phone is held upright,
    // whichever way the screen is turned now (fu to the right, fv down). When the screen turns the
    // bubble is put back at the same fractions, so it stays where it was on the phone, not where it was
    // on the screen.
    void toFrac() {
        float cx = (bx + size / 2f) / W, cy = (by + size / 2f) / H;
        switch (rot) {
            case Surface.ROTATION_90:
                fu = 1 - cy;
                fv = cx;
                break;
            case Surface.ROTATION_180:
                fu = 1 - cx;
                fv = 1 - cy;
                break;
            case Surface.ROTATION_270:
                fu = cy;
                fv = 1 - cx;
                break;
            default:
                fu = cx;
                fv = cy;
        }
    }

    void fromFrac() {
        float cx, cy;
        switch (rot) {
            case Surface.ROTATION_90:
                cx = fv;
                cy = 1 - fu;
                break;
            case Surface.ROTATION_180:
                cx = 1 - fu;
                cy = 1 - fv;
                break;
            case Surface.ROTATION_270:
                cx = 1 - fv;
                cy = fu;
                break;
            default:
                cx = fu;
                cy = fv;
        }
        bx = Math.round(cx * W - size / 2f);
        by = Math.round(cy * H - size / 2f);
        clampB();
    }

    // remembers where the bubble is on the phone
    void save() {
        toFrac();
        p.edit().putFloat("bu", fu).putFloat("bv", fv).apply();
    }

    // the intent of the window of what is counted: the profile's own window (the same intent as its
    // shortcut, so an open window of it comes to the front), or the main window (a window opened for one
    // profile has that profile in its address, "bw"; the main window, where a profile is just the one
    // selected, is opened like the launcher does, or it would be opened twice)
    Intent openIntent() {
        String bw = p.getString("bw", "");
        if (!bw.isEmpty() && !getSharedPreferences("d", 0).contains(bw)) bw = "";
        return bw.isEmpty()
                ? new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClass(this, M.class)
                : new Intent(this, M.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse("wee://profile/" + Uri.encode(bw)));
    }

    // Dropped on the cog: the customising. The bubble is large in the middle of a dim screen and the real one is
    // out of the way. A swipe left or right shows the next or the previous look (Sy), a pinch sets the size,
    // the check mark keeps both, and a tap anywhere else leaves everything as it was.
    void customise() {
        if (cz != null) return;
        cz = new Cz();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        try {
            wm.addView(cz, lp);
        } catch (RuntimeException e) {
            cz = null;
            return;
        }
        bb.setVisibility(View.INVISIBLE);
    }

    // the customising is over: keep the look and the size (they are saved as the settings, so the bubble takes them
    // at once), or not
    void endCz(boolean keep) {
        if (cz == null) return;
        Cz z = cz;
        cz = null;
        if (keep) p.edit().putInt("bstyle", z.cur).putString("bs", "" + Math.round(z.sz)).apply();
        z.stop();
        try {
            wm.removeView(z);
        } catch (RuntimeException ignored) {
        }
        if (bb != null) bb.setVisibility(View.VISIBLE);
    }

    // dropped on the open button: the app, on the window of what is counted
    void openApp() {
        try {
            startActivity(openIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Toast.makeText(this, "Could not open the app", Toast.LENGTH_SHORT).show();
        }
    }

    // The targets while the bubble is picked up. At the bottom: the X (drop it there to close it) and, in a
    // profile, the i next to it (drop it there to see the current slide). At the top, the same distance
    // from the edge: the cog (to customise the bubble) and a button that opens the app.
    void showX() {
        if (xt != null) return;
        boolean info = prof() != null;
        xt = target(0, info ? -44 : 0, false);
        if (info) it = target(1, 44, false);
        ct = target(2, -44, true);
        ot = target(3, 44, true);
    }

    // kind 0 is the X, 1 the i, 2 the cog, 3 open the app; dx: dp from the middle of the screen
    Xt target(int kind, int dx, boolean top) {
        Xt t = new Xt(kind);
        int d = (int) (64 * u);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(d, d, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = (top ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL;
        lp.x = (int) (dx * u);
        lp.y = (int) (48 * u);
        wm.addView(t, lp);
        return t;
    }

    void hideX() {
        for (Xt t : new Xt[]{xt, it, ct, ot}) {
            if (t == null) continue;
            try {
                wm.removeView(t);
            } catch (RuntimeException ignored) {
            }
        }
        xt = null;
        it = null;
        ct = null;
        ot = null;
    }

    // which target the middle of the bubble is over: 0 none, 1 the X, 2 the i, 3 the cog, 4 open the app
    int over() {
        float cx = bx + size / 2f, cy = by + size / 2f, yb = H - 80 * u, yt = 80 * u, dx = it != null ? 44 * u : 0;
        if (Math.hypot(cx - (W / 2f - dx), cy - yb) < 46 * u) return 1;
        if (it != null && Math.hypot(cx - (W / 2f + dx), cy - yb) < 46 * u) return 2;
        if (Math.hypot(cx - (W / 2f - 44 * u), cy - yt) < 46 * u) return 3;
        if (Math.hypot(cx - (W / 2f + 44 * u), cy - yt) < 46 * u) return 4;
        return 0;
    }

    int ni(String key, int d) {
        try {
            return Integer.parseInt(p.getString(key, "" + d).trim());
        } catch (Exception e) {
            return d;
        }
    }

    // The profile the bubble counts, or null for the plain counter. "bt" is set by the window that
    // starts it (the menu item: that window's profile) or, when it comes up by itself, by the window
    // that was left last (see M.onPause); it does not change while the bubble is up.
    String prof() {
        String bt = p.getString("bt", "");
        return bt.isEmpty() ? null : bt;
    }

    // the profile with its repeats, or null if it is gone
    M.Pr load(String pf) {
        String tx = getSharedPreferences("d", 0).getString(pf, null);
        M.Pr r = tx == null ? null : M.parse(tx);
        if (r != null) M.ov(r, p.getString("rp:" + pf, ""));
        return r;
    }

    // the count of every slide of the profile
    int[] counts(String pf, int k) {
        int[] a = new int[k];
        try {
            String sv = p.getString("cn:" + pf, null);
            if (sv != null) {
                String[] q = sv.split(",");
                for (int i = 0; i < k && i < q.length; i++) a[i] = Math.max(0, Integer.parseInt(q[i]));
            }
        } catch (Exception e) {
            Arrays.fill(a, 0);
        }
        return a;
    }

    // What the bubble shows: the count of the current slide of the profile in use (a check mark when
    // it is completed), else the plain counter. The colours are those of that profile's theme.
    void refresh() {
        if (bb == null) return;
        String pf = prof();
        M.Pr pr = pf == null ? null : load(pf);
        th = Th.get(this, p, pr != null ? pf : "");
        if (pr == null) {
            int n = p.getInt("n", 0), mx = ni("mx", 0), lap = Math.max(1, ni("st", 100));
            bb.s = mx > 0 ? n + "/" + mx : "" + n; // the goal, if there is one
            bb.prog = mx > 0 ? Math.min(1f, n / (float) mx) : n > 0 && n % lap == 0 ? 1f : (n % lap) / (float) lap; // of the goal, or of the lap
            last = "";
        } else {
            int k = pr.c.length, cu = Math.max(0, Math.min(k, p.getInt("cu:" + pf, 0)));
            int had = cu >= k ? 0 : counts(pf, k)[cu];
            bb.s = cu >= k ? "\u2713" : had + "/" + pr.c[cu];
            bb.prog = cu >= k ? 1f : Math.min(1f, had / (float) Math.max(1, pr.c[cu])); // of the slide
            String old = last;
            last = pf + ":" + cu;
            // another slide while the app is not on screen: the speech bubble says which
            if (old.startsWith(pf + ":") && !old.equals(last) && !App.fg && !dragging)
                say(cu >= k ? "\u2713" : slideText(pr, cu), false);
        }
        bb.invalidate();
    }

    // the info: the current slide in a speech bubble that stays until some input
    void info() {
        String pf = prof();
        M.Pr pr = pf == null ? null : load(pf);
        if (pr == null) return;
        int k = pr.c.length, cu = Math.max(0, Math.min(k, p.getInt("cu:" + pf, 0)));
        say(cu >= k ? "\u2713" : slideText(pr, cu), true);
    }

    // the text of the slide in the view the app is on, or its title
    String slideText(M.Pr pr, int s) {
        M.Vw[] vs = pr.v[s];
        M.Vw w = vs[Math.max(0, Math.min(vs.length - 1, p.getInt("vi", 0)))];
        StringBuilder b = new StringBuilder();
        for (String t : w.t) {
            String x = t.trim();
            if (x.isEmpty()) continue;
            if (b.length() > 0) b.append('\n');
            b.append(x);
        }
        return b.length() > 0 ? b.toString() : pr.ti[s];
    }

    // a tap counts what the bubble shows
    void tap() {
        String pf = prof();
        M.Pr pr = pf == null ? null : load(pf);
        if (pr == null) plain();
        else slide(pf, pr);
        refresh();
    }

    // the plain counter counts, with the same buzz and reminder clearing as in the app
    void plain() {
        int n = p.getInt("n", 0) + 1;
        p.edit().putInt("n", n).apply();
        int mx = ni("mx", 0), st = Math.max(1, ni("st", 100));
        boolean goal = mx > 0 && n == mx, lap = n % st == 0;
        buzz(goal ? 8 : lap ? 9 : 5); // the settings of the app: vibration for the goal, a lap, a tap
        if (goal || lap) N.done(this, "");
    }

    // The current slide of the profile counts and, at its target, moves on to the next slide that is
    // not full yet, with the lap buzz (the goal buzz when the profile is completed), as in M.inc().
    // A completed profile does nothing: the app's completed screen starts over, a bubble does not.
    void slide(String pf, M.Pr pr) {
        int k = pr.c.length, cur = Math.max(0, Math.min(k, p.getInt("cu:" + pf, 0)));
        if (cur >= k) return;
        int[] cn = counts(pf, k);
        int c0 = pr.c[cur];
        cn[cur]++; // counting past the target is allowed (40/33); the slide moves on once, at the target
        boolean fin = cn[cur] == c0;
        int nx = cur;
        if (fin) {
            nx = cur + 1;
            while (nx < k && cn[nx] >= pr.c[nx]) nx++;
        }
        boolean done = fin && nx >= k;
        buzz(done ? 8 : c0 > 1 && cn[cur] % c0 == 0 ? 9 : 5);
        StringBuilder b = new StringBuilder();
        for (int c : cn) b.append(c).append(',');
        p.edit().putString("cn:" + pf, b.toString()).putInt("cu:" + pf, nx).apply();
        if (done) N.done(this, pf); // a kept reminder of this profile goes away
    }

    // i is the number of the vibration setting in M.D, played the way the app plays it
    void buzz(int i) {
        try {
            String[] a = p.getString(M.D[i][0], M.D[i][1]).trim().split("[^0-9]+");
            long[] w = new long[a.length + 1];
            for (int j = 0; j < a.length; j++) w[j + 1] = Long.parseLong(a[j]);
            play(w);
        } catch (Exception ignored) { // an empty pattern is off
        }
    }

    // plays a pattern (off, buzz, pause, buzz ... in ms), the way the app plays its own
    void play(long[] w) {
        try {
            VibrationEffect e = VibrationEffect.createWaveform(w, -1);
            Vibrator v = getSystemService(Vibrator.class);
            if (!p.getBoolean("hp", false)) v.vibrate(e);
            else if (Build.VERSION.SDK_INT >= 33)
                v.vibrate(e, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH));
            else v.vibrate(e, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build());
        } catch (Exception ignored) {
        }
    }

    // the bubble: what it counts, in a circle of the accent colour
    class Bb extends View {
        String s = "0";              // what it shows
        final Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        float sx, sy;                 // where the finger went down (screen)
        int ox, oy;                   // where the bubble was then
        boolean drag;                 // the finger moved far enough: the bubble follows it
        int hotT;                     // the target it is over: 0 none, 1 the X, 2 the i
        float prog;                   // how far the count has come, 0 to 1 (for the looks that show it)
        float phase;                  // 0 to 1 over and over, for the looks that move
        ValueAnimator pulse;          // moves the phase, only while the look moves
        VelocityTracker vt;

        Bb() {
            super(Fb.this);
            q.setTypeface(Typeface.DEFAULT_BOLD);
            q.setTextAlign(Paint.Align.CENTER);
        }

        // in the middle of its window, which has room round it to swell in
        @Override protected void onDraw(Canvas c) {
            Sy.draw(c, style, getWidth() / 2f, getHeight() / 2f, size / 2f, s, th, u, phase, prog, q);
        }

        // the looks that move are redrawn all the time, the others only when something changes
        void sync() {
            boolean want = Sy.animated(style) && isAttachedToWindow();
            if (want && pulse == null) {
                pulse = ValueAnimator.ofFloat(0f, 1f);
                pulse.setDuration(2400);
                pulse.setRepeatCount(ValueAnimator.INFINITE);
                pulse.setInterpolator(new LinearInterpolator());
                pulse.addUpdateListener(va -> {
                    phase = (Float) va.getAnimatedValue();
                    invalidate();
                });
                pulse.start();
            } else if (!want && pulse != null) {
                pulse.cancel();
                pulse = null;
            }
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            sync();
        }

        @Override protected void onDetachedFromWindow() {
            if (pulse != null) {
                pulse.cancel();
                pulse = null;
            }
            super.onDetachedFromWindow();
        }

        // a tap counts; a drag moves the bubble as soon as the finger has really moved, and where it
        // is let go it stays; a fling (let go while moving fast) sticks it to the edge it was thrown at
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (vt == null) vt = VelocityTracker.obtain();
            MotionEvent c = MotionEvent.obtain(e);
            c.offsetLocation(e.getRawX() - e.getX(), e.getRawY() - e.getY()); // screen coordinates
            vt.addMovement(c);
            c.recycle();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    sx = e.getRawX();
                    sy = e.getRawY();
                    ox = bx;
                    oy = by;
                    drag = false;
                    if (speech != null && speech.sticky) hideSay(); // any input puts the info away
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
                    if (!drag) {
                        if (Math.hypot(dx, dy) < 2 * ViewConfiguration.get(getContext()).getScaledTouchSlop()) return true;
                        drag = true;
                        dragging = true;
                        hotT = 0;
                        hideSay();
                        showX();
                        animate().scaleX(1.15f).scaleY(1.15f).setDuration(100);
                    }
                    bx = ox + (int) dx;
                    by = oy + (int) dy;
                    clampB();
                    place();
                    int t = over();
                    if (xt != null) {
                        if (t != 0 && t != hotT) play(BUZZ_NEAR); // got over a target
                        hotT = t;
                        for (Xt x : new Xt[]{xt, it, ct, ot}) {
                            if (x == null) continue;
                            x.hot = t == x.kind + 1;
                            x.invalidate();
                        }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    vt.computeCurrentVelocity(1000);
                    float vx = vt.getXVelocity(), vy = vt.getYVelocity();
                    vt.recycle();
                    vt = null;
                    if (drag) release(vx, vy);
                    else if (e.getActionMasked() == MotionEvent.ACTION_UP) tap();
                    drag = false;
                    dragging = false;
                    return true;
                }
            }
            return true;
        }

        // let go: on the X it closes (a different buzz from getting over it); thrown fast it glides
        // to the edge it was thrown at; otherwise it stays right here
        void release(float vx, float vy) {
            animate().scaleX(1f).scaleY(1f).setDuration(100);
            int t = over();
            if (t == 1) {
                play(BUZZ_CLOSE);
                stopSelf();
                return;
            }
            if (t >= 2) { // the i, the cog, open: the bubble goes back where it was picked up, then it happens
                play(BUZZ_INFO);
                hideX();
                Runnable act;
                if (t == 2) act = () -> info();
                else if (t == 3) act = () -> customise();
                else act = () -> openApp();
                glide(ox, oy, act);
                return;
            }
            hideX();
            if (Math.hypot(vx, vy) > 800 * u) { // thrown: docked at the edge it was thrown at, partly off the screen
                int tx = bx, ty = by;
                if (Math.abs(vx) >= Math.abs(vy)) tx = vx < 0 ? -hang() : W - size + hang();
                else ty = vy < 0 ? -hang() : H - size + hang();
                glide(tx, ty, null);
            } else {
                clampB();
                save();
            }
        }

        void glide(final int tx, final int ty, final Runnable after) {
            final int fx = bx, fy = by;
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(240);
            a.setInterpolator(new DecelerateInterpolator());
            a.addUpdateListener(va -> {
                float f = (Float) va.getAnimatedValue();
                bx = fx + (int) ((tx - fx) * f);
                by = fy + (int) ((ty - fy) * f);
                place();
            });
            a.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator an) {
                    save();
                    if (after != null) after.run();
                }
            });
            a.start();
        }
    }

    // A comic speech bubble beside the head with the text of a slide, all of it (the text gets smaller
    // if it would not fit the screen). It fades in slowly; then it either stays a while and fades out
    // slowly, or (sticky: the info) stays until some input: a touch on it, on the head, or anywhere
    // else. It takes no touches when it is not sticky. A new one replaces the one showing.
    void say(String text, boolean sticky) {
        hideSay();
        if (text == null || text.isEmpty()) return;
        float tl = 14 * u, m = 6 * u, pad = 12 * u;                  // tail, room for the outline, padding
        float cx = bx + size / 2f, cy = by + size / 2f;
        boolean right = cx > W / 2f;                                 // head on the right: the bubble goes to its left
        float room = (right ? bx : W - bx - size) - 4 * u - tl - 2 * m;
        float bw = Math.max(110 * u, Math.min(280 * u, room));       // the body's width
        float most = H - 24 * u - 2 * m - 2 * pad;                   // the most height the text may have
        float ts = 15 * u;
        StaticLayout sl;
        while (true) {
            TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            tp.setColor(0xFF000000);
            tp.setTextSize(ts);
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            sl = StaticLayout.Builder.obtain(text, 0, text.length(), tp, (int) (bw - 2 * pad))
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).build();
            if (sl.getHeight() <= most || ts <= 8 * u) break;
            ts -= u;
        }
        float bh = sl.getHeight() + 2 * pad;
        int ww = (int) (bw + tl + 2 * m), wh = (int) (bh + 2 * m);
        yp = new WindowManager.LayoutParams(ww, wh, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | (sticky ? WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        : WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE), PixelFormat.TRANSLUCENT);
        yp.gravity = Gravity.TOP | Gravity.START;
        yp.x = Math.max(0, Math.min(W - ww, (int) (right ? bx - 4 * u - ww : bx + size + 4 * u)));
        yp.y = Math.max(0, Math.min(H - wh, (int) (cy - wh / 2f)));
        final Sp v = new Sp(sl, bw, bh, tl, m, pad, cy - yp.y, right, sticky);
        v.setAlpha(0f);
        try {
            wm.addView(v, yp);
        } catch (RuntimeException e) {
            return;
        }
        speech = v;
        v.animate().alpha(1f).setDuration(350);
        if (!sticky) {
            fadeR = () -> v.animate().alpha(0f).setDuration(1200).withEndAction(() -> {
                if (speech == v) hideSay();
            });
            v.postDelayed(fadeR, Math.min(9000, 3000 + 40 * text.length()));
        }
    }

    void hideSay() {
        if (speech == null) return;
        Sp v = speech;
        speech = null;
        v.animate().cancel();
        v.removeCallbacks(fadeR);
        try {
            wm.removeView(v);
        } catch (RuntimeException ignored) {
        }
    }

    // the screen of the customising: the look in the middle, its name and the size under it, and two buttons
    class Cz extends View {
        int cur, prev = -1, dir; // the look that is shown, the one that slides out, and which way
        float off = 1;           // 0 to 1: the new look slides in as the old one slides out
        float sz;                // the size (px)
        float phase;             // for the looks that move
        final float lo, hi;      // the smallest and the largest size
        final Paint q = new Paint(Paint.ANTI_ALIAS_FLAG), tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        final ScaleGestureDetector sd;
        float x0, y0;
        boolean moved, scaled;
        ValueAnimator slide, loop;

        Cz() {
            super(Fb.this);
            cur = style;
            sz = size;
            lo = 24 * u;
            hi = Math.min(W, H) / 2f;
            tp.setTextAlign(Paint.Align.CENTER);
            sd = new ScaleGestureDetector(Fb.this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector d) {
                    sz = Math.max(lo, Math.min(hi, sz * d.getScaleFactor()));
                    invalidate();
                    return true;
                }
            });
            loop = ValueAnimator.ofFloat(0f, 1f);
            loop.setDuration(2400);
            loop.setRepeatCount(ValueAnimator.INFINITE);
            loop.setInterpolator(new LinearInterpolator());
            loop.addUpdateListener(va -> {
                phase = (Float) va.getAnimatedValue();
                if (Sy.animated(cur) || prev >= 0 && off < 1 && Sy.animated(prev)) invalidate();
            });
            loop.start();
        }

        void stop() {
            loop.cancel();
            if (slide != null) slide.cancel();
        }

        // d = 1: the next look comes in from the right, d = -1: the previous one from the left
        void step(int d) {
            if (slide != null) slide.cancel();
            prev = cur;
            cur = Math.floorMod(cur + d, Sy.COUNT);
            dir = d;
            off = 0;
            slide = ValueAnimator.ofFloat(0f, 1f);
            slide.setDuration(240);
            slide.setInterpolator(new DecelerateInterpolator());
            slide.addUpdateListener(va -> {
                off = (Float) va.getAnimatedValue();
                invalidate();
            });
            slide.start();
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            sd.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    x0 = e.getX();
                    y0 = e.getY();
                    moved = false;
                    scaled = false;
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    scaled = true;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.hypot(e.getX() - x0, e.getY() - y0) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) moved = true;
                    return true;
                case MotionEvent.ACTION_UP: {
                    if (scaled || sd.isInProgress()) return true;
                    float dx = e.getX() - x0, dy = e.getY() - y0;
                    if (Math.abs(dx) > 56 * u && Math.abs(dx) > 2 * Math.abs(dy)) {
                        step(dx < 0 ? 1 : -1);
                    } else if (!moved) { // a tap: on the check mark it keeps the look, anywhere else it leaves
                        endCz(Math.hypot(e.getX() - (W / 2f + 56 * u), e.getY() - (H - 96 * u)) < 36 * u);
                    }
                    return true;
                }
                default:
                    return true;
            }
        }

        @Override protected void onDraw(Canvas c) {
            float cx = W / 2f, cy = H * .42f, sh = W * .6f, by0 = H - 96 * u, br = 28 * u, k = 8 * u;
            c.drawColor((th.bg & 0xFFFFFF) | 0xF2000000);
            tp.setStyle(Paint.Style.FILL);
            tp.setFakeBoldText(false);
            tp.setColor(th.dim);
            tp.setTextSize(15 * u);
            c.drawText("Swipe for another look, pinch to resize", cx, 110 * u, tp);
            c.drawText("The check mark keeps it, a tap anywhere else leaves it", cx, 132 * u, tp);
            if (prev >= 0 && off < 1) Sy.draw(c, prev, cx - dir * off * sh, cy, sz / 2f, bb.s, th, u, phase, .65f, q);
            Sy.draw(c, cur, cx + dir * (1 - off) * sh, cy, sz / 2f, bb.s, th, u, phase, .65f, q);
            tp.setColor(th.fg);
            tp.setTextSize(20 * u);
            tp.setFakeBoldText(true);
            c.drawText(Sy.NAMES[cur], cx, cy + sz / 2f + 56 * u, tp);
            tp.setFakeBoldText(false);
            tp.setColor(th.dim);
            tp.setTextSize(14 * u);
            c.drawText((cur + 1) + " of " + Sy.COUNT + "   " + Math.round(sz) + " px", cx, cy + sz / 2f + 82 * u, tp);
            q.reset();
            q.setAntiAlias(true);
            q.setStyle(Paint.Style.FILL);
            q.setColor((th.dim & 0xFFFFFF) | 0x44000000);
            c.drawCircle(cx - 56 * u, by0, br, q); // cancel
            q.setStyle(Paint.Style.STROKE);
            q.setStrokeWidth(3 * u);
            q.setStrokeCap(Paint.Cap.ROUND);
            q.setStrokeJoin(Paint.Join.ROUND);
            q.setColor(th.fg);
            c.drawLine(cx - 56 * u - k, by0 - k, cx - 56 * u + k, by0 + k, q);
            c.drawLine(cx - 56 * u - k, by0 + k, cx - 56 * u + k, by0 - k, q);
            q.setStyle(Paint.Style.FILL);
            q.setColor(th.ac);
            c.drawCircle(cx + 56 * u, by0, br, q); // keep
            q.setStyle(Paint.Style.STROKE);
            q.setColor(th.bg);
            Path ok = new Path();
            ok.moveTo(cx + 56 * u - k, by0 + k * .1f);
            ok.lineTo(cx + 56 * u - k * .3f, by0 + k * .8f);
            ok.lineTo(cx + 56 * u + k, by0 - k * .8f);
            c.drawPath(ok, q);
        }
    }

    // the speech bubble: a white rounded body with a thick black outline, a hard shadow and a tail
    // that points at the head, bold black text in it
    class Sp extends View {
        final StaticLayout sl;
        final Path body = new Path();
        final Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float left, top, pad;
        final boolean sticky;

        Sp(StaticLayout l, float bw, float bh, float tl, float m, float padding, float tailY, boolean tailRight,
           boolean st) {
            super(Fb.this);
            sl = l;
            pad = padding;
            sticky = st;
            left = m + (tailRight ? 0 : tl);
            top = m;
            float r = 14 * u;
            RectF rc = new RectF(left, top, left + bw, top + bh);
            body.addRoundRect(rc, r, r, Path.Direction.CW);
            float ty = Math.max(rc.top + r + 4 * u, Math.min(rc.bottom - r - 4 * u, tailY)); // where the tail leaves the body
            float edge = tailRight ? rc.right : rc.left, tip = tailRight ? rc.right + tl : rc.left - tl;
            Path tail = new Path();
            tail.moveTo(edge, ty - 9 * u);
            tail.lineTo(tip, Math.max(top, Math.min(top + bh, tailY)));
            tail.lineTo(edge, ty + 9 * u);
            tail.close();
            body.op(tail, Path.Op.UNION); // one outline round the body and the tail
            q.setStrokeJoin(Paint.Join.ROUND);
        }

        // the sticky one goes at any touch: on it, or (as ACTION_OUTSIDE) anywhere else
        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (sticky && (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_OUTSIDE)) {
                animate().alpha(0f).setDuration(250).withEndAction(() -> {
                    if (speech == this) hideSay();
                });
                return a == MotionEvent.ACTION_DOWN;
            }
            return false;
        }

        @Override protected void onDraw(Canvas c) {
            q.setStyle(Paint.Style.FILL);
            q.setColor(0x66000000); // the hard shadow, a bit down and to the right
            c.save();
            c.translate(3 * u, 3 * u);
            c.drawPath(body, q);
            c.restore();
            q.setColor(0xFFFFFFFF);
            c.drawPath(body, q);
            q.setStyle(Paint.Style.STROKE);
            q.setStrokeWidth(2.5f * u);
            q.setColor(0xFF000000);
            c.drawPath(body, q);
            c.save();
            c.translate(left + pad, top + pad);
            sl.draw(c);
            c.restore();
        }
    }

    // a target, bigger and coloured while the bubble is over it: kind 0 the X (warning colour), 1 the i in a
    // circle, 2 a cog, 3 a window with an arrow out of it (open the app); the last three in the accent colour
    class Xt extends View {
        final int kind;
        boolean hot;
        final Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);

        Xt(int k) {
            super(Fb.this);
            kind = k;
            q.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override protected void onDraw(Canvas c) {
            float r = getWidth() / 2f;
            int disc = hot ? (kind == 0 ? th.warn : th.ac) : 0xCC202020;
            q.setStyle(Paint.Style.FILL);
            q.setColor(disc);
            c.drawCircle(r, r, hot ? r : r * .85f, q);
            q.setStyle(Paint.Style.STROKE);
            q.setStrokeWidth(3 * u);
            int ink = kind != 0 && hot ? th.bg : 0xFFFFFFFF;
            q.setColor(ink);
            if (kind == 0) {
                float a = r * (hot ? .36f : .3f);
                c.drawLine(r - a, r - a, r + a, r + a, q);
                c.drawLine(r - a, r + a, r + a, r - a, q);
            } else if (kind == 1) {
                float g = r * (hot ? .55f : .5f); // the ring of the i, with the dot and the stem inside it
                c.drawCircle(r, r, g, q);
                q.setStyle(Paint.Style.FILL);
                c.drawCircle(r, r - g * .42f, 2 * u, q);
                q.setStyle(Paint.Style.STROKE);
                c.drawLine(r, r - g * .1f, r, r + g * .5f, q);
            } else if (kind == 2) {
                float g = r * (hot ? .42f : .38f); // a cog: eight teeth round a body that has a hole in it
                q.setStyle(Paint.Style.FILL);
                for (int k = 0; k < 8; k++) {
                    c.save();
                    c.rotate(45 * k, r, r);
                    c.drawRoundRect(r - g * .22f, r - g * 1.5f, r + g * .22f, r - g * .85f, 2 * u, 2 * u, q);
                    c.restore();
                }
                c.drawCircle(r, r, g * 1.05f, q);
                q.setColor(disc);
                c.drawCircle(r, r, g * .45f, q);
            } else {
                float g = r * (hot ? .45f : .4f); // a window with its top right corner open, and an arrow out of it
                c.drawLine(r - g, r - g, r - g * .1f, r - g, q);
                c.drawLine(r - g, r - g, r - g, r + g, q);
                c.drawLine(r - g, r + g, r + g, r + g, q);
                c.drawLine(r + g, r + g, r + g, r + g * .15f, q);
                c.drawLine(r - g * .15f, r + g * .15f, r + g * 1.05f, r - g * 1.05f, q);
                c.drawLine(r + g * 1.05f, r - g * 1.05f, r + g * .35f, r - g * 1.05f, q);
                c.drawLine(r + g * 1.05f, r - g * 1.05f, r + g * 1.05f, r - g * .35f, q);
            }
        }
    }
}
