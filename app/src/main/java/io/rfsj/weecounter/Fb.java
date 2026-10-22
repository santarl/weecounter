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
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
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
    Xt xt, it;                        // the X and (in a profile) the i, only while the bubble is picked up
    Sp speech;                        // the speech bubble, only while it is shown
    Runnable fadeR;                   // what fades it out
    WindowManager.LayoutParams bp, xp, yp;
    float u;                          // one dp in px
    int size, W, H;                   // the bubble's size, the screen's size (px)
    boolean dragging;                 // the bubble is being dragged
    String last = "";                 // profile:slide the speech bubble last saw ("" = none yet)
    static final long[] BUZZ_NEAR = {0, 25};          // the bubble got over the X: a short tick
    static final long[] BUZZ_CLOSE = {0, 40, 60, 90};  // dropped on the X: two buzzes, the second longer
    static final long[] BUZZ_INFO = {0, 20, 40, 20};   // dropped on the i: two short ones
    final SharedPreferences.OnSharedPreferenceChangeListener nl = (sp, k) -> {
        if ("bs".equals(k)) resize();
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
        u = getResources().getDisplayMetrics().density;
        metrics();
        size = sizePx();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("fb", "Floating counter", NotificationManager.IMPORTANCE_LOW));
        // a tap on the notification opens the window of what is counted: the profile's own window (the
        // same intent as its shortcut, so an open window of it comes to the front), or the main window
        String bt = prof();
        if (bt != null && !getSharedPreferences("d", 0).contains(bt)) bt = null; // the profile is gone
        Intent open = bt == null
                ? new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClass(this, M.class)
                : new Intent(this, M.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse("wee://profile/" + Uri.encode(bt)));
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
        bp = new WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        bp.gravity = Gravity.TOP | Gravity.START;
        bp.x = p.getInt("bx", W - size);
        bp.y = p.getInt("by", H / 3);
        clamp();
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
        metrics();
        resize();
    }

    @Override public void onDestroy() {
        on = false;
        auto = false;
        p.unregisterOnSharedPreferenceChangeListener(nl);
        hideX();
        hideSay();
        try {
            wm.removeView(bb);
        } catch (RuntimeException ignored) {
        }
        super.onDestroy();
    }

    void metrics() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        W = dm.widthPixels;
        H = dm.heightPixels;
    }

    // The diameter in px: the setting, or 67dp (the old 56dp and a fifth more) when it is 0.
    int sizePx() {
        int d = ni("bs", 0);
        return Math.max((int) (24 * u), Math.min(d > 0 ? d : (int) (67 * u), Math.min(W, H) / 2));
    }

    void resize() {
        size = sizePx();
        bp.width = size;
        bp.height = size;
        clamp();
        try {
            wm.updateViewLayout(bb, bp);
        } catch (RuntimeException ignored) {
        }
    }

    // remembers where the bubble is
    void save() {
        p.edit().putInt("bx", bp.x).putInt("by", bp.y).apply();
    }

    void clamp() {
        bp.x = Math.max(0, Math.min(W - size, bp.x));
        bp.y = Math.max(0, Math.min(H - size, bp.y));
    }

    // The targets at the bottom while the bubble is picked up: the X (drop it there to close it) and,
    // in a profile, the i next to it (drop it there to see the current slide).
    void showX() {
        if (xt != null) return;
        boolean info = prof() != null;
        xt = target(0, info ? -44 : 0);
        if (info) it = target(1, 44);
    }

    // kind 0 is the X, 1 the i; dx: dp from the middle of the screen
    Xt target(int kind, int dx) {
        Xt t = new Xt(kind);
        int d = (int) (64 * u);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(d, d, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.x = (int) (dx * u);
        lp.y = (int) (48 * u);
        wm.addView(t, lp);
        return t;
    }

    void hideX() {
        for (Xt t : new Xt[]{xt, it}) {
            if (t == null) continue;
            try {
                wm.removeView(t);
            } catch (RuntimeException ignored) {
            }
        }
        xt = null;
        it = null;
    }

    // which target the middle of the bubble is over: 0 none, 1 the X, 2 the i
    int over() {
        float bx = bp.x + size / 2f, by = bp.y + size / 2f, y = H - 80 * u, dx = it != null ? 44 * u : 0;
        if (Math.hypot(bx - (W / 2f - dx), by - y) < 46 * u) return 1;
        if (it != null && Math.hypot(bx - (W / 2f + dx), by - y) < 46 * u) return 2;
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
            int n = p.getInt("n", 0), mx = ni("mx", 0);
            bb.s = mx > 0 ? n + "/" + mx : "" + n; // the goal, if there is one
            last = "";
        } else {
            int k = pr.c.length, cu = Math.max(0, Math.min(k, p.getInt("cu:" + pf, 0)));
            bb.s = cu >= k ? "\u2713" : counts(pf, k)[cu] + "/" + pr.c[cu];
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
        VelocityTracker vt;

        Bb() {
            super(Fb.this);
            q.setTypeface(Typeface.DEFAULT_BOLD);
            q.setTextAlign(Paint.Align.CENTER);
        }

        @Override protected void onDraw(Canvas c) {
            float r = getWidth() / 2f;
            q.setStyle(Paint.Style.FILL);
            q.setColor(th.ac);
            c.drawCircle(r, r, r - 2 * u, q);
            q.setStyle(Paint.Style.STROKE);
            q.setStrokeWidth(2 * u);
            q.setColor((th.fg & 0xFFFFFF) | 0x99000000);
            c.drawCircle(r, r, r - 2 * u, q);
            q.setStyle(Paint.Style.FILL);
            q.setColor(th.bg);
            q.setTextSize(24 * u * r / (33.5f * u)); // grows with the bubble
            float w = q.measureText(s);
            if (w > r * 1.45f) q.setTextSize(q.getTextSize() * r * 1.45f / w); // long text shrinks to fit
            c.drawText(s, r, r - (q.descent() + q.ascent()) / 2, q);
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
                    ox = bp.x;
                    oy = bp.y;
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
                    bp.x = ox + (int) dx;
                    bp.y = oy + (int) dy;
                    clamp();
                    wm.updateViewLayout(this, bp);
                    int t = over();
                    if (xt != null) {
                        if (t != 0 && t != hotT) play(BUZZ_NEAR); // got over a target
                        hotT = t;
                        xt.hot = t == 1;
                        xt.invalidate();
                        if (it != null) {
                            it.hot = t == 2;
                            it.invalidate();
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
            if (t == 2) { // the i: the bubble goes back where it was picked up, then the info shows
                play(BUZZ_INFO);
                hideX();
                glide(ox, oy, () -> info());
                return;
            }
            hideX();
            if (Math.hypot(vx, vy) > 800 * u) {
                int tx = bp.x, ty = bp.y;
                if (Math.abs(vx) >= Math.abs(vy)) tx = vx < 0 ? 0 : W - size;
                else ty = vy < 0 ? 0 : H - size;
                glide(tx, ty, null);
            } else {
                save();
            }
        }

        void glide(final int tx, final int ty, final Runnable after) {
            final int fx = bp.x, fy = bp.y;
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(240);
            a.setInterpolator(new DecelerateInterpolator());
            a.addUpdateListener(va -> {
                float f = (Float) va.getAnimatedValue();
                bp.x = fx + (int) ((tx - fx) * f);
                bp.y = fy + (int) ((ty - fy) * f);
                try {
                    wm.updateViewLayout(this, bp);
                } catch (RuntimeException ignored) {
                }
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
        float cx = bp.x + size / 2f, cy = bp.y + size / 2f;
        boolean right = cx > W / 2f;                                 // head on the right: the bubble goes to its left
        float room = (right ? bp.x : W - bp.x - size) - 4 * u - tl - 2 * m;
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
                        | (sticky ? WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        : WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE), PixelFormat.TRANSLUCENT);
        yp.gravity = Gravity.TOP | Gravity.START;
        yp.x = Math.max(0, Math.min(W - ww, (int) (right ? bp.x - 4 * u - ww : bp.x + size + 4 * u)));
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

    // a target at the bottom, bigger and coloured while the bubble is over it: kind 0 the X (warning
    // colour), kind 1 the i in a circle (accent colour)
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
            q.setStyle(Paint.Style.FILL);
            q.setColor(hot ? (kind == 0 ? th.warn : th.ac) : 0xCC202020);
            c.drawCircle(r, r, hot ? r : r * .85f, q);
            q.setStyle(Paint.Style.STROKE);
            q.setStrokeWidth(3 * u);
            q.setColor(kind == 1 && hot ? th.bg : 0xFFFFFFFF);
            if (kind == 0) {
                float a = r * (hot ? .36f : .3f);
                c.drawLine(r - a, r - a, r + a, r + a, q);
                c.drawLine(r - a, r + a, r + a, r - a, q);
            } else {
                float g = r * (hot ? .55f : .5f); // the ring of the i, with the dot and the stem inside it
                c.drawCircle(r, r, g, q);
                q.setStyle(Paint.Style.FILL);
                c.drawCircle(r, r - g * .42f, 2 * u, q);
                q.setStyle(Paint.Style.STROKE);
                c.drawLine(r, r - g * .1f, r, r + g * .5f, q);
            }
        }
    }
}
