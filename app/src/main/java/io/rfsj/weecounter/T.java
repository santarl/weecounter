package io.rfsj.weecounter;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntConsumer;

// The terminal look shared by every screen: monospace on black, green text, [ buttons ], [x]
// boxes, bordered dialogs and menus.
final class T {
    static final int FG = 0xFF39FF6A, DIM = 0xFF2E9E4F, WARN = 0xFFFFB000, BAD = 0xFFFF5252;
    static final Typeface F = Typeface.MONOSPACE;

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + .5f);
    }

    static TextView tv(Context c, String s, int col, int sp) {
        TextView x = new TextView(c);
        x.setText(s);
        x.setTextColor(col);
        x.setTextSize(sp);
        x.setTypeface(F);
        return x;
    }

    // a thin border on black
    static void box(View v, int col) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF000000);
        g.setStroke(dp(v.getContext(), 1), col);
        v.setBackground(g);
    }

    static EditText ed(Context c) {
        EditText e = new EditText(c);
        e.setTypeface(F);
        e.setTextColor(FG);
        e.setHintTextColor(DIM);
        e.setSingleLine();
        e.setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6));
        box(e, DIM);
        return e;
    }

    // "[x] label" / "[ ] label"
    static class K extends TextView {
        boolean on;
        final String lb;

        K(Context c, String l) {
            super(c);
            lb = l;
            setTypeface(F);
            setTextColor(FG);
            setTextSize(15);
            setPadding(0, dp(c, 10), 0, dp(c, 10));
            setOnClickListener(v -> setChecked(!on));
            setChecked(false);
        }

        boolean isChecked() {
            return on;
        }

        void setChecked(boolean b) {
            on = b;
            setText((b ? "[x] " : "[ ] ") + lb);
        }
    }

    // a bordered box over the screen: title, content, a row of [ buttons ]
    static class D extends Dialog {
        final LinearLayout body, bar;

        D(Activity a, String title) {
            super(a);
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout box = new LinearLayout(a);
            box.setOrientation(LinearLayout.VERTICAL);
            int m = dp(a, 12);
            box.setPadding(m, m, m, m);
            box(box, FG);
            box.addView(tv(a, "== " + title + " ==", FG, 16));
            body = new LinearLayout(a);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(0, m, 0, m);
            box.addView(body);
            bar = new LinearLayout(a);
            bar.setGravity(Gravity.END);
            box.addView(bar);
            setContentView(box);
            Window w = getWindow();
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (a.getResources().getDisplayMetrics().widthPixels * .94f), -2);
            w.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM); // so a text box can take the keyboard
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        D add(View v) {
            body.addView(v);
            return this;
        }

        D add(View v, int h) {
            body.addView(v, new LinearLayout.LayoutParams(-1, h));
            return this;
        }

        D btn(String s, boolean close, Runnable r) {
            TextView b = tv(getContext(), "[ " + s + " ]", FG, 15);
            b.setPadding(dp(getContext(), 10), dp(getContext(), 8), 0, dp(getContext(), 4));
            b.setOnClickListener(v -> {
                if (close) dismiss();
                if (r != null) r.run();
            });
            bar.addView(b);
            return this;
        }
    }

    // a list of choices in a box, "> item"
    static void menu(Activity a, String title, String[] items, IntConsumer pick) {
        D d = new D(a, title);
        for (int i = 0; i < items.length; i++) {
            final int k = i;
            TextView t = tv(a, "> " + items[i], FG, 18);
            t.setPadding(0, dp(a, 10), 0, dp(a, 10));
            t.setOnClickListener(v -> {
                d.dismiss();
                pick.accept(k);
            });
            d.add(t);
        }
        d.show();
    }
}
