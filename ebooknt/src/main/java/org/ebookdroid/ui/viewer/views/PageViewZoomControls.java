package org.ebookdroid.ui.viewer.views;

import org.ebookdroid.common.settings.AppSettings;
import org.ebookdroid.core.models.ZoomModel;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

public class PageViewZoomControls extends LinearLayout {

    private final ZoomModel zoomModel;
    private float savedZoom;
    private Runnable onDismiss;

    public PageViewZoomControls(final Context context, final ZoomModel zoomModel) {
        super(context);
        this.zoomModel = zoomModel;
        setVisibility(View.GONE);
        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.BOTTOM);

        final boolean eink = AppSettings.current().einkMode;
        final int btnTextColor = eink ? Color.BLACK : Color.WHITE;
        final int btnBgColor = eink ? 0xCCE0E0E0 : 0x80000000;

        final Button cancel = new Button(context);
        cancel.setText(android.R.string.cancel);
        cancel.setTextColor(btnTextColor);
        cancel.setTextSize(13);
        cancel.setTypeface(Typeface.DEFAULT_BOLD);
        cancel.setBackgroundColor(btnBgColor);
        cancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                zoomModel.setZoom(savedZoom);
                zoomModel.commit();
                ViewEffects.toggleControls(PageViewZoomControls.this);
                if (onDismiss != null) onDismiss.run();
            }
        });

        final Button ok = new Button(context);
        ok.setText(android.R.string.ok);
        ok.setTextColor(btnTextColor);
        ok.setTextSize(13);
        ok.setTypeface(Typeface.DEFAULT_BOLD);
        ok.setBackgroundColor(btnBgColor);
        ok.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                zoomModel.commit();
                ViewEffects.toggleControls(PageViewZoomControls.this);
                if (onDismiss != null) onDismiss.run();
            }
        });

        final ZoomRoll roll = new ZoomRoll(context, zoomModel);

        final LayoutParams btnParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        final LayoutParams rollParams = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f);

        addView(cancel, btnParams);
        addView(roll, rollParams);
        addView(ok, btnParams);
    }

    public void setOnDismissListener(Runnable r) {
        onDismiss = r;
    }

    @Override
    public void setVisibility(int visibility) {
        if (visibility == View.VISIBLE) {
            savedZoom = zoomModel.getZoom();
        }
        super.setVisibility(visibility);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        return false;
    }
}
