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
    static final String[] NAMES = {"Midnight", "Ocean", "Rose", "Sand", "Forest", "Violet", "Sunset", "Mint", "Teal",
        "Slate", "Coffee", "Lime", "Graphite", "Orchid"};
    static final String[] MODES = {"dark", "light", "auto"};
    // per palette: its hue (degrees) and how tinted the backgrounds are (0 = pure black / white).
    // Every other colour is worked out from these, see the constructor.
    static final float[][] H = {{150, 0}, {205, .5f}, {340, .5f}, {42, .5f}, {140, .5f}, {265, .5f}, {18, .55f},
        {160, .4f}, {185, .5f}, {215, .18f}, {24, .45f}, {88, .5f}, {0, .02f}, {300, .5f}};

    // the name of a palette in a mode: pure black has a light mode of its own name
    static String name(int pal, boolean light) {
        return pal == 0 && light ? "myeyes" : NAMES[pal];
    }

    int pal, mode, bg, fg, dim, ac, warn, bad;
    boolean light;
    int[] lap; // the colour of every lap: text, accent, then the hue turned further and further

    Th(Context x, String s) {
        int[] v = parse(s);
        pal = v[0];
        mode = v[1];
        light = mode == 1 || mode == 2
                && (x.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
        float h = H[pal][0], t = H[pal][1];
        bg = t == 0 ? (light ? 0xFFFFFFFF : 0xFF000000) : hsl(h, t, light ? .965f : .07f);
        fg = fit(h, t == 0 ? 0 : .1f, bg, 17);
        dim = fit(h, t == 0 ? 0 : .15f, bg, 5);
        ac = fit(h, .85f, bg, 4.6f);
        warn = fit(38, .9f, bg, 4.6f);
        bad = fit(2, .85f, bg, 4.6f);
        lap = new int[]{fg, ac, fit(h + 70, .8f, bg, 4.6f), fit(h + 140, .8f, bg, 4.6f),
                fit(h + 210, .8f, bg, 4.6f), fit(h + 280, .8f, bg, 4.6f)};
    }

    // colour of a hue and saturation, with the first lightness (stepped from the vivid middle: up on
    // a dark background, down on a light one) that has the contrast c to bg, so it reads on any theme
    static int fit(float h, float s, int bg, float c) {
        boolean dark = lum(bg) < .4f;
        int r = 0;
        for (int i = 0; i <= 50; i++) {
            r = hsl(h, s, Math.max(0, Math.min(1, dark ? .45f + i * .011f : .55f - i * .011f)));
            if (ratio(r, bg) >= c) break;
        }
        return r;
    }

    static int hsl(float h, float s, float l) {
        float q = l < .5f ? l * (1 + s) : l + s - l * s, p = 2 * l - q, k = h / 360;
        return 0xFF000000 | ch(p, q, k + 1f / 3) << 16 | ch(p, q, k) << 8 | ch(p, q, k - 1f / 3);
    }

    static int ch(float p, float q, float t) {
        t = (t % 1 + 1) % 1;
        float v = t < 1f / 6 ? p + (q - p) * 6 * t : t < .5f ? q : t < 2f / 3 ? p + (q - p) * (2f / 3 - t) * 6 : p;
        return Math.round(v * 255);
    }

    // relative luminance, and the contrast ratio of two colours (WCAG)
    static float lum(int c) {
        float[] w = {.2126f, .7152f, .0722f};
        float r = 0;
        for (int k = 0; k < 3; k++) {
            float v = (c >> 16 - 8 * k & 255) / 255f;
            r += w[k] * (v <= .03928f ? v / 12.92f : (float) Math.pow((v + .055f) / 1.055f, 2.4f));
        }
        return r;
    }

    static float ratio(int a, int b) {
        float x = lum(a), y = lum(b);
        return (Math.max(x, y) + .05f) / (Math.min(x, y) + .05f);
    }

    // {palette, mode} of "palette,mode"; anything odd gives AMOLED dark
    static int[] parse(String s) {
        try {
            String[] t = s.split(",");
            int a = Integer.parseInt(t[0].trim()), m = Integer.parseInt(t[1].trim());
            if (a >= 0 && a < H.length && m >= 0 && m < 3) return new int[]{a, m};
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
