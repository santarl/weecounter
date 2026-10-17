package io.rfsj.weecounter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.View;

// Colour themes: a palette (name) with a dark and a light mode (or "auto", follows the phone).
// Stored as "palette,mode" in the setting th (the default) and th:<profile> (a profile's own).
final class Th {
    static final String[] NAMES = {"AMOLED", "Ocean", "Rose", "Sand", "Forest", "Violet"};
    static final String[] MODES = {"dark", "light", "auto"};
    // per palette: background, text, dim text, accent; first dark, then light
    static final int[][] C = {
        {0xFF000000, 0xFFFFFFFF, 0xFF888888, 0xFF00E676, 0xFFFFFFFF, 0xFF111111, 0xFF666666, 0xFF008744},
        {0xFF0B1621, 0xFFE6F1FF, 0xFF7F93A8, 0xFF4FC3F7, 0xFFF2F8FD, 0xFF0B2540, 0xFF5B7185, 0xFF0174B9},
        {0xFF1A0F14, 0xFFFCE4EC, 0xFF9C7A86, 0xFFF48FB1, 0xFFFFF5F8, 0xFF3B0A1E, 0xFF8A5A6C, 0xFFC2185B},
        {0xFF1B1710, 0xFFF5ECD7, 0xFF9A8F75, 0xFFFFC107, 0xFFFBF6E9, 0xFF3A2F12, 0xFF7A6E50, 0xFF8F6808},
        {0xFF0C1A12, 0xFFE3F4E8, 0xFF7DA08A, 0xFF66BB6A, 0xFFF1F8F3, 0xFF10301B, 0xFF4F7A5E, 0xFF2E7D32},
        {0xFF140F1E, 0xFFEDE7F6, 0xFF8E82A8, 0xFFB388FF, 0xFFF6F2FC, 0xFF24114A, 0xFF6A5C88, 0xFF673AB7},
    };

    int pal, mode, bg, fg, dim, ac, warn, bad;
    boolean light;
    int[] lap; // lap colours, unless you wrote your own in the settings

    Th(Context x, String s) {
        int[] v = parse(s);
        pal = v[0];
        mode = v[1];
        light = mode == 1 || mode == 2
                && (x.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
        int[] c = C[pal];
        int o = light ? 4 : 0;
        bg = c[o];
        fg = c[o + 1];
        dim = c[o + 2];
        ac = c[o + 3];
        warn = light ? 0xFFA05F00 : 0xFFFFB74D;
        bad = light ? 0xFFD32F2F : 0xFFFF5252;
        lap = light ? new int[]{fg, ac, 0xFF0174B9, 0xFF8F6808, 0xFFD32F2F, 0xFF8E24AA}
                : new int[]{fg, ac, 0xFF40C4FF, 0xFFFFD740, 0xFFFF5252, 0xFFE040FB};
    }

    // {palette, mode} of "palette,mode"; anything odd gives AMOLED dark
    static int[] parse(String s) {
        try {
            String[] t = s.split(",");
            int a = Integer.parseInt(t[0].trim()), m = Integer.parseInt(t[1].trim());
            if (a >= 0 && a < C.length && m >= 0 && m < 3) return new int[]{a, m};
        } catch (Exception e) {
            // the default
        }
        return new int[]{0, 0};
    }

    // the theme of a profile if it has its own, else the default one
    static Th get(Context x, SharedPreferences p, String pf) {
        String s = pf.isEmpty() ? null : p.getString("th:" + pf, null);
        return new Th(x, s != null ? s : p.getString("th", "0,0"));
    }

    // a blended with b, t of the way (0..1)
    static int mix(int a, int b, float t) {
        int r = 0xFF000000;
        for (int s = 0; s < 24; s += 8) r |= (int) ((a >> s & 255) * (1 - t) + (b >> s & 255) * t) << s;
        return r;
    }

    int theme() {
        return light ? android.R.style.Theme_DeviceDefault_Light_NoActionBar
                : android.R.style.Theme_DeviceDefault_NoActionBar;
    }

    AlertDialog.Builder dlg(Activity a) {
        return new AlertDialog.Builder(a, light ? android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    // status and navigation bars in the colour of the screen, with dark icons on a light one
    void bars(Activity a) {
        a.getWindow().setStatusBarColor(bg);
        a.getWindow().setNavigationBarColor(bg);
        View d = a.getWindow().getDecorView();
        int f = d.getSystemUiVisibility(), l = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        d.setSystemUiVisibility(light ? f | l : f & ~l);
    }
}
