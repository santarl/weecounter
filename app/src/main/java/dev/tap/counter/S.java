package dev.tap.counter;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class S extends Activity implements View.OnClickListener {
    SharedPreferences p;
    EditText[] e = new EditText[M.D.length];
    CheckBox aw, dk;
    TextView df;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences("p", 0);
        int m = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(m, m, m, m);

        l.addView(tv("Vibration notation: milliseconds, alternating buzz and pause. "
                + "\"40 60 40\" = buzz 40, pause 60, buzz 40. Empty = off.", 0xFF888888, m));
        for (int i = 0; i < e.length; i++) {
            l.addView(tv(M.D[i][2], 0xFFFFFFFF, m));
            e[i] = new EditText(this);
            e[i].setTextColor(0xFFFFFFFF);
            e[i].setSingleLine();
            if (i < 3) e[i].setInputType(2); // numbers only
            l.addView(e[i]);
        }
        aw = new CheckBox(this);
        aw.setText("Keep screen on");
        aw.setTextColor(0xFFFFFFFF);
        l.addView(aw);
        dk = new CheckBox(this);
        dk.setText("Start with black screen");
        dk.setTextColor(0xFFFFFFFF);
        l.addView(dk);
        df = tv("RESET TO DEFAULTS", 0xFFFF5252, m);
        df.setOnClickListener(this);
        l.addView(df);

        ScrollView s = new ScrollView(this);
        s.setBackgroundColor(0xFF000000);
        s.setFitsSystemWindows(true);
        s.addView(l);
        setContentView(s);
        fill(false);
    }

    TextView tv(String s, int c, int m) {
        TextView x = new TextView(this);
        x.setText(s);
        x.setTextColor(c);
        x.setPadding(0, m, 0, 0);
        return x;
    }

    void fill(boolean d) {
        for (int i = 0; i < e.length; i++)
            e[i].setText(d ? M.D[i][1] : p.getString(M.D[i][0], M.D[i][1]));
        aw.setChecked(d || p.getBoolean("aw", true));
        dk.setChecked(!d && p.getBoolean("dk", false));
    }

    @Override public void onClick(View x) {
        fill(true);
    }

    @Override protected void onPause() {
        super.onPause();
        SharedPreferences.Editor x = p.edit();
        for (int i = 0; i < e.length; i++)
            x.putString(M.D[i][0], e[i].getText().toString());
        x.putBoolean("aw", aw.isChecked()).putBoolean("dk", dk.isChecked()).apply();
    }
}
