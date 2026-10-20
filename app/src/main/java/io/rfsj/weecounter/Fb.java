package io.rfsj.weecounter;

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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Toast;

// The floating counter: a round bubble over other apps that shows the plain counter and counts it
// with a tap. Hold it to pick it up, drag it where you want it (it snaps to the nearest side), drop
// it on the X that appears at the bottom to close it. It always counts the plain counter, the
// number "n" of the settings file, which the app takes over at once (see M.nl and M.keepN).
public class Fb extends Service {
    static boolean on;                // the bubble is up
    static final String STOP = "io.rfsj.weecounter.STOP_BUBBLE";
    WindowManager wm;
    SharedPreferences p;
    Th th;
    Bb bb;                            // the bubble
    Xt xt;                            // the X, only while the bubble is picked up
    WindowManager.LayoutParams bp, xp;
    float u;                          // one dp in px
    int size, W, H;                   // the bubble's size, the screen's size (px)
    final SharedPreferences.OnSharedPreferenceChangeListener nl = (sp, k) -> {
        if ("n".equals(k) && bb != null) { // the app counted
            bb.n = sp.getInt("n", 0);
            bb.invalidate();
        }
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
        size = (int) (56 * u);
        metrics();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("fb", "Floating counter", NotificationManager.IMPORTANCE_LOW));
        Notification nf = new Notification.Builder(this, "fb").setSmallIcon(N.small())
                .setContentTitle("Floating counter")
                .setContentText("Tap the bubble to count. Hold it to move it, drop it on the X to close.")
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, M.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))
                .addAction(new Notification.Action.Builder(N.small(), "Close",
                        PendingIntent.getService(this, 1, new Intent(this, Fb.class).setAction(STOP),
                                PendingIntent.FLAG_IMMUTABLE)).build())
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, nf, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, nf);

        bb = new Bb();
        bb.n = p.getInt("n", 0);
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
        clamp();
        try {
            wm.updateViewLayout(bb, bp);
        } catch (RuntimeException ignored) {
        }
    }

    @Override public void onDestroy() {
        on = false;
        p.unregisterOnSharedPreferenceChangeListener(nl);
        hideX();
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

    void clamp() {
        bp.x = Math.max(0, Math.min(W - size, bp.x));
        bp.y = Math.max(0, Math.min(H - size, bp.y));
    }

    // the X that shows where to drop the bubble to close it
    void showX() {
        if (xt != null) return;
        xt = new Xt();
        int d = (int) (64 * u);
        xp = new WindowManager.LayoutParams(d, d, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        xp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        xp.y = (int) (48 * u);
        wm.addView(xt, xp);
    }

    void hideX() {
        if (xt == null) return;
        try {
            wm.removeView(xt);
        } catch (RuntimeException ignored) {
        }
        xt = null;
    }

    // is the middle of the bubble over the X?
    boolean over() {
        float bx = bp.x + size / 2f, by = bp.y + size / 2f, xx = W / 2f, xy = H - 80 * u;
        return Math.hypot(bx - xx, by - xy) < 56 * u;
    }

    int ni(String key, int d) {
        try {
            return Integer.parseInt(p.getString(key, "" + d).trim());
        } catch (Exception e) {
            return d;
        }
    }

    // a tap: the plain counter counts, with the same buzz and reminder clearing as in the app
    void tap() {
        int n = p.getInt("n", 0) + 1;
        p.edit().putInt("n", n).apply();
        bb.n = n;
        bb.invalidate();
        int mx = ni("mx", 0), st = Math.max(1, ni("st", 100));
        boolean goal = mx > 0 && n == mx, lap = n % st == 0;
        buzz(goal ? 8 : lap ? 9 : 5); // the settings of the app: vibration for the goal, a lap, a tap
        if (goal || lap) N.done(this, "");
    }

    // i is the number of the vibration setting in M.D, played the way the app plays it
    void buzz(int i) {
        try {
            String[] a = p.getString(M.D[i][0], M.D[i][1]).trim().split("[^0-9]+");
            long[] w = new long[a.length + 1];
            for (int j = 0; j < a.length; j++) w[j + 1] = Long.parseLong(a[j]);
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

    // the bubble: the number in a circle of the accent colour
    class Bb extends View {
        int n;
        final Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        float sx, sy;                 // where the finger went down (screen)
        int ox, oy;                   // where the bubble was then
        boolean held, moved;          // picked up by a hold / the finger moved before the hold
        final Runnable hold = () -> {
            held = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            animate().scaleX(1.15f).scaleY(1.15f).setDuration(100);
            showX();
        };

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
            String s = "" + n;
            q.setStyle(Paint.Style.FILL);
            q.setColor(th.bg);
            q.setTextSize(24 * u);
            float w = q.measureText(s);
            if (w > r * 1.45f) q.setTextSize(24 * u * r * 1.45f / w); // long numbers shrink to fit
            c.drawText(s, r, r - (q.descent() + q.ascent()) / 2, q);
        }

        // a quick tap counts; a hold picks the bubble up and it follows the finger
        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    sx = e.getRawX();
                    sy = e.getRawY();
                    ox = bp.x;
                    oy = bp.y;
                    held = false;
                    moved = false;
                    postDelayed(hold, 350);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
                    if (!held) { // moving before the hold is neither a tap nor a drag
                        if (Math.hypot(dx, dy) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                            moved = true;
                            removeCallbacks(hold);
                        }
                        return true;
                    }
                    bp.x = ox + (int) dx;
                    bp.y = oy + (int) dy;
                    clamp();
                    wm.updateViewLayout(this, bp);
                    if (xt != null) {
                        xt.hot = over();
                        xt.invalidate();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(hold);
                    if (held) drop();
                    else if (!moved && e.getActionMasked() == MotionEvent.ACTION_UP) tap();
                    held = false;
                    return true;
            }
            return true;
        }

        // let go: on the X it closes, else it snaps to the nearest side and the place is remembered
        void drop() {
            animate().scaleX(1f).scaleY(1f).setDuration(100);
            if (over()) {
                stopSelf();
                return;
            }
            hideX();
            final int to = bp.x + size / 2 < W / 2 ? 0 : W - size;
            ValueAnimator a = ValueAnimator.ofInt(bp.x, to);
            a.setDuration(160);
            a.addUpdateListener(va -> {
                bp.x = (Integer) va.getAnimatedValue();
                try {
                    wm.updateViewLayout(this, bp);
                } catch (RuntimeException ignored) {
                }
            });
            a.start();
            p.edit().putInt("bx", to).putInt("by", bp.y).apply();
        }
    }

    // the X: bigger and in the warning colour while the bubble is over it
    class Xt extends View {
        boolean hot;
        final Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);

        Xt() {
            super(Fb.this);
            q.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override protected void onDraw(Canvas c) {
            float r = getWidth() / 2f;
            q.setStyle(Paint.Style.FILL);
            q.setColor(hot ? th.warn : 0xCC202020);
            c.drawCircle(r, r, hot ? r : r * .85f, q);
            q.setStyle(Paint.Style.STROKE);
            q.setStrokeWidth(3 * u);
            q.setColor(0xFFFFFFFF);
            float a = r * (hot ? .36f : .3f);
            c.drawLine(r - a, r - a, r + a, r + a, q);
            c.drawLine(r - a, r + a, r + a, r - a, q);
        }
    }
}
