package io.rfsj.weecounter;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

// The dialogs of the app: a card of the theme in the middle of a dim screen, the same for the focus,
// menu and adjust sheets of the main screen and for every dialog of the other screens. A tap outside
// the card, or Back (see back()), closes it. A sheet is a view over the content of its activity, so
// nothing underneath gets a touch while it is up (see isUp()).
//
// It also takes the calls of AlertDialog.Builder that the app uses (setTitle, setCustomTitle,
// setMessage, setView, setItems and the three buttons), so a dialog is written as before:
// Sh.of(activity, theme).setTitle("...").setMessage("...").setPositiveButton("OK", null).show().
final class Sh implements DialogInterface {
    static final String MARK = "sheet"; // the content description of the root of every sheet

    // the root (dim screen) and the card of a sheet
    static class Shell {
        FrameLayout root;
        LinearLayout card;
    }

    // Puts a sheet over the content of the activity. fields: it has text fields, so the screen shrinks
    // above the keyboard (the card stays in the middle of what is left); scroll: the card may be taller
    // than the screen.
    static Shell shell(final Activity a, Th th, boolean fields, boolean scroll) {
        final Shell s = new Shell();
        s.root = new FrameLayout(a);
        s.root.setContentDescription(MARK);
        s.root.setBackgroundColor((th.bg & 0xFFFFFF) | 0xEE000000);
        s.root.setOnClickListener(v -> hide(a, s.root)); // a tap outside the card
        s.card = new LinearLayout(a);
        s.card.setOrientation(LinearLayout.VERTICAL);
        s.card.setClickable(true);
        s.card.setPadding(dp(a, 20), dp(a, 20), dp(a, 20), dp(a, 12));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Th.mix(th.bg, th.fg, .08f));
        g.setCornerRadius(dp(a, 20));
        s.card.setBackground(g);
        View inner = s.card;
        if (scroll) {
            ScrollView sv = new ScrollView(a);
            sv.addView(s.card);
            inner = sv;
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        lp.setMargins(dp(a, 24), 0, dp(a, 24), 0);
        s.root.addView(inner, lp);
        if (fields) {
            s.root.setTag(a.getWindow().getAttributes().softInputMode); // to put back when it closes
            a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        ((ViewGroup) a.findViewById(android.R.id.content)).addView(s.root, new FrameLayout.LayoutParams(-1, -1));
        return s;
    }

    // closes a sheet: the keyboard goes, the screen is as it was, and the view is taken away
    static void hide(Activity a, final View root) {
        if (root.getVisibility() == View.GONE) return;
        InputMethodManager im = a.getSystemService(InputMethodManager.class);
        if (im != null) im.hideSoftInputFromWindow(root.getWindowToken(), 0);
        Object t = root.getTag();
        if (t instanceof Integer) {
            a.getWindow().setSoftInputMode((Integer) t);
            root.setTag(null);
        }
        root.setVisibility(View.GONE);
        root.post(() -> {
            ViewParent pa = root.getParent();
            if (pa instanceof ViewGroup) ((ViewGroup) pa).removeView(root);
        });
    }

    // the sheet that is up in the activity, the one on top, or null
    static View top(Activity a) {
        ViewGroup c = a.findViewById(android.R.id.content);
        for (int i = c.getChildCount() - 1; i >= 0; i--) {
            View v = c.getChildAt(i);
            if (MARK.equals(v.getContentDescription()) && v.getVisibility() == View.VISIBLE) return v;
        }
        return null;
    }

    static boolean isUp(Activity a) {
        return top(a) != null;
    }

    // for onBackPressed: closes the sheet that is up and says whether there was one
    static boolean back(Activity a) {
        View v = top(a);
        if (v == null) return false;
        if (v instanceof Tr) ((Tr) v).close(); // the guided tour
        else v.performClick();
        return true;
    }

    static int dp(Activity a, float v) {
        return (int) (v * a.getResources().getDisplayMetrics().density + .5f);
    }

    // Caps the height of what it holds (so that the buttons below stay on the screen); the contents
    // of a message or of a list scroll.
    static class Lim extends FrameLayout {
        final int max, keep;

        Lim(Activity a, int max, int keep) {
            super(a);
            this.max = max;
            this.keep = keep;
        }

        @Override protected void onMeasure(int w, int h) {
            int room = MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED ? max : Math.min(max, MeasureSpec.getSize(h) - keep);
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(Math.max(room, 0), MeasureSpec.AT_MOST));
        }
    }

    // ---- the dialog: the calls of AlertDialog.Builder ------------------------------------------

    final Activity a;
    final Th th;
    CharSequence title, msg, pos, neg, neu;
    CharSequence[] items;
    View custom, view;
    OnClickListener itemL, posL, negL, neuL;
    FrameLayout root;

    private Sh(Activity a, Th th) {
        this.a = a;
        this.th = th;
    }

    static Sh of(Activity a, Th th) {
        return new Sh(a, th);
    }

    Sh setTitle(CharSequence t) {
        title = t;
        return this;
    }

    Sh setCustomTitle(View v) {
        custom = v;
        return this;
    }

    Sh setMessage(CharSequence m) {
        msg = m;
        return this;
    }

    Sh setView(View v) {
        view = v;
        return this;
    }

    Sh setItems(CharSequence[] it, OnClickListener l) {
        items = it;
        itemL = l;
        return this;
    }

    Sh setPositiveButton(CharSequence t, OnClickListener l) {
        pos = t;
        posL = l;
        return this;
    }

    Sh setNegativeButton(CharSequence t, OnClickListener l) {
        neg = t;
        negL = l;
        return this;
    }

    Sh setNeutralButton(CharSequence t, OnClickListener l) {
        neu = t;
        neuL = l;
        return this;
    }

    Sh create() {
        return this;
    }

    private static boolean hasEdit(View v) {
        if (v instanceof EditText) return true;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) if (hasEdit(g.getChildAt(i))) return true;
        }
        return false;
    }

    private TextView text(CharSequence s, int col, float sp) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextColor(col);
        t.setTextSize(sp);
        return t;
    }

    // a body (a message, a list, your own view) in a box that never takes the whole screen
    private void body(LinearLayout card, View v) {
        int h = a.getResources().getDisplayMetrics().heightPixels;
        Lim l = new Lim(a, (int) (h * .7f), dp(a, 80));
        l.addView(v);
        card.addView(l, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView button(CharSequence label, int col, final int which, final OnClickListener l) {
        TextView b = text(label, col, 16);
        b.setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 12));
        b.setOnClickListener(v -> {
            dismiss();
            if (l != null) l.onClick(this, which);
        });
        return b;
    }

    Sh show() {
        final Shell s = shell(a, th, hasEdit(view), false);
        root = s.root;
        LinearLayout card = s.card;
        if (custom != null) {
            card.addView(custom);
        } else if (title != null) {
            TextView t = text(title, th.fg, 22);
            t.setPadding(0, 0, 0, dp(a, 8));
            card.addView(t);
        }
        if (msg != null) {
            ScrollView sv = new ScrollView(a);
            sv.addView(text(msg, th.fg, 16));
            body(card, sv);
        }
        if (view != null) {
            if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
            body(card, view);
        }
        if (items != null) {
            LinearLayout rows = new LinearLayout(a);
            rows.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < items.length; i++) {
                final int at = i;
                TextView t = text(items[i], th.fg, 18);
                t.setPadding(dp(a, 7), dp(a, 12), dp(a, 7), dp(a, 12));
                t.setOnClickListener(v -> {
                    dismiss();
                    if (itemL != null) itemL.onClick(this, at);
                });
                rows.addView(t);
            }
            ScrollView sv = new ScrollView(a);
            sv.addView(rows);
            body(card, sv);
        }
        if (pos != null || neg != null || neu != null) {
            LinearLayout bar = new LinearLayout(a);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(0, dp(a, 8), 0, 0);
            if (neu != null) bar.addView(button(neu, th.dim, BUTTON_NEUTRAL, neuL));
            View gap = new View(a);
            bar.addView(gap, new LinearLayout.LayoutParams(0, 1, 1f));
            if (neg != null) bar.addView(button(neg, th.dim, BUTTON_NEGATIVE, negL));
            if (pos != null) bar.addView(button(pos, th.ac, BUTTON_POSITIVE, posL));
            card.addView(bar);
        }
        return this;
    }

    @Override public void dismiss() {
        if (root != null) hide(a, root);
    }

    @Override public void cancel() {
        dismiss();
    }
}
