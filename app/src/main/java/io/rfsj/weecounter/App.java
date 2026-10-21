package io.rfsj.weecounter;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;

// Knows when the app as a whole (not just one of its windows) goes out of sight. Then the floating
// counter (Fb) comes up by itself, unless the setting "fa" is off or the permission "Display over
// other apps" is missing, and it goes away again when the app is back. A floating counter that was
// started from the menu stays.
public class App extends Application implements Application.ActivityLifecycleCallbacks {
    static boolean fg; // a window of the app is on screen
    int started;       // how many windows are started (visible): from one window to the next it is never 0

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override public void onActivityStarted(Activity a) {
        if (started++ == 0) {
            fg = true;
            if (Fb.on && Fb.auto) stopService(new Intent(this, Fb.class)); // back: the one that came up by itself goes
        }
    }

    @Override public void onActivityStopped(Activity a) {
        if (--started > 0 || a.isChangingConfigurations()) return; // another window is up, or a rotation
        fg = false;
        if (a.isFinishing()) return; // the app was closed, not minimized
        SharedPreferences p = getSharedPreferences("p", 0);
        if (!p.getBoolean("fa", true) || Fb.on || !Settings.canDrawOverlays(this)) return;
        try {
            Fb.autoNext = true;
            startForegroundService(new Intent(this, Fb.class));
        } catch (RuntimeException e) { // the system did not allow it: no bubble, no crash
            Fb.autoNext = false;
        }
    }

    @Override public void onActivityCreated(Activity a, Bundle b) {
    }

    @Override public void onActivityResumed(Activity a) {
    }

    @Override public void onActivityPaused(Activity a) {
    }

    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {
    }

    @Override public void onActivityDestroyed(Activity a) {
    }
}
