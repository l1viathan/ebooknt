package org.emdev.ui.uimanager;

import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
import static android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
import static android.view.View.SYSTEM_UI_FLAG_LOW_PROFILE;

import android.annotation.TargetApi;
import android.app.Activity;
import android.view.View;

@TargetApi(19)
public class UIManager44x extends UIManager40x {

    // IMMERSIVE_STICKY: taps don't show nav bar (only edge-swipe does, briefly).
    // This prevents the tap→nav-bar-shows→window-resize that causes gray pages.
    private static final int EXT_SYS_UI_FLAGS =
    /**/
    SYSTEM_UI_FLAG_LOW_PROFILE |
    /**/
    SYSTEM_UI_FLAG_FULLSCREEN |
    /**/
    SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
    /**/
    SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
    /**/
    SYSTEM_UI_FLAG_HIDE_NAVIGATION |
    /**/
    SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    @Override
    protected int getHideSysUIFlags(final Activity activity) {
        return EXT_SYS_UI_FLAGS;
    }

    // On API 19+, always keep HIDE_NAVIGATION with IMMERSIVE_STICKY when showing
    // the action bar so the nav bar never appears as a resizing element.
    // The action bar is an overlay (FrameLayout root), so it doesn't need the
    // nav bar to show. This prevents the 67px window-resize that causes gray pages.
    @Override
    public void setFullScreenMode(final Activity activity, final View view, final boolean fullScreen) {
        data.get(activity.getComponentName()).statusBarHidden = fullScreen;
        if (view != null) {
            if (fullScreen) {
                view.setSystemUiVisibility(getHideSysUIFlags(activity));
            } else {
                view.setSystemUiVisibility(
                    SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                );
            }
        }
        // Don't touch FLAG_FULLSCREEN (deprecated on API 30+); view-level flags suffice.
    }

    @Override
    public void onMenuOpened(final Activity activity) {
        // No FLAG_FULLSCREEN interaction in immersive mode.
    }

    @Override
    public void onMenuClosed(final Activity activity) {
        // No FLAG_FULLSCREEN interaction in immersive mode.
    }
}
