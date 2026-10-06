package dev.tap.counter;

import android.app.Activity;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public class M extends Activity implements View.OnClickListener, View.OnLongClickListener {
    // colour changes every 100 taps, cycling through this palette
    static final int[] C = {0xFFFFFFFF, 0xFF00E676, 0xFF40C4FF, 0xFFFFD740, 0xFFFF5252, 0xFFE040FB};
    int n;
    TextView t, r;
    Vibrator v;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);
        v = getSystemService(Vibrator.class);

        t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(96);
        t.setOnClickListener(this);
        t.setOnLongClickListener(this);

        r = new TextView(this);
        r.setText("HOLD TO RESET");
        r.setTextColor(0xFF444444);
        r.setPadding(64, 32, 64, 32);
        r.setOnLongClickListener(this);

        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(0xFF000000);
        f.addView(t, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        p.bottomMargin = 160;
        f.addView(r, p);
        setContentView(f);
        draw();
    }

    void draw() {
        t.setText(Integer.toString(n));
        t.setTextColor(C[(n / 100) % C.length]);
    }

    @Override public void onClick(View x) {
        n++;
        draw();
        v.vibrate(VibrationEffect.createOneShot(12, -1));
    }

    @Override public boolean onLongClick(View x) {
        if (x == r) {
            n = 0;
            draw();
            v.vibrate(VibrationEffect.createOneShot(80, -1));
        } else {
            Toast.makeText(this, Integer.toString(n), Toast.LENGTH_SHORT).show();
            v.vibrate(VibrationEffect.createWaveform(new long[]{0, 40, 60, 40}, -1));
        }
        return true;
    }
}
