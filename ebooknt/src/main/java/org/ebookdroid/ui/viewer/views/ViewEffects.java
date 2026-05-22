package org.ebookdroid.ui.viewer.views;

import android.view.View;
import android.view.ViewGroup;


public class ViewEffects {

    public static void toggleControls(View view) {
        if (view.getVisibility() == View.VISIBLE) {
            view.clearAnimation();
            view.setVisibility(View.GONE);
            final ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null) {
                parent.invalidate();
            }
        } else {
            view.setVisibility(View.VISIBLE);
        }
    }
}
