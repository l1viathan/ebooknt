package org.ebookdroid.ui.viewer.views;

import org.ebooknt.viewer.R;
import org.ebookdroid.common.settings.SettingsManager;
import org.ebookdroid.common.settings.books.BookSettings;
import org.ebookdroid.common.settings.types.PageAlign;
import org.ebookdroid.core.EventCrop;
import org.ebookdroid.core.Page;
import org.ebookdroid.core.ViewState;
import org.ebookdroid.ui.viewer.IActivityController;
import org.ebookdroid.ui.viewer.IViewController;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff.Mode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;

import org.emdev.ui.actions.ActionController;
import org.emdev.ui.actions.ActionDialogBuilder;
import org.emdev.ui.actions.ActionEx;
import org.emdev.ui.actions.ActionMethod;
import org.emdev.ui.actions.IActionController;
import org.emdev.utils.MathUtils;

public class ManualCropView extends View {

    private static final Paint PAINT = new Paint();
    private static final Paint BTN_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint BTN_TEXT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF btnOk = new RectF();
    private final RectF btnCancel = new RectF();

    private final IActivityController base;

    private final GestureDetector gestureDetector;

    private final ActionController<ManualCropView> controller = new ActionController<ManualCropView>(this);

    private final PointF topLeft = new PointF(0.1f, 0.1f);
    private final PointF bottomRight = new PointF(0.9f, 0.9f);
    private PointF currentPoint = null;

    private Page page;

    private RectF result;

    private PageAlign savedPageAlign;
    private float savedZoom;
    private boolean needCropRestore;
    private RectF savedRelativeCrop;

    public ManualCropView(final IActivityController base) {
        super(base.getContext());
        this.base = base;

        super.setVisibility(View.GONE);
        PAINT.setColor(Color.CYAN);

        setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
        setFocusable(true);
        setFocusableInTouchMode(true);

        gestureDetector = new GestureDetector(getContext(), new GestureListener());
    }

    public void initControls() {
        page = base.getDocumentModel().getCurrentPageObject();
        if (page == null) {
            return;
        }

        savedZoom = base.getZoomModel().getZoom();
        savedPageAlign = base.getBookSettings().pageAlign;

        base.getZoomModel().setZoom(1.0f, true);

        base.getBookSettings().pageAlign = PageAlign.AUTO;

        needCropRestore = false;
        savedRelativeCrop = null;

        final RectF oldCb = page.nodes.root.getCropping();

        if (page.shouldCrop() && oldCb != null) {
            if (page.nodes.root.hasManualCropping()) {
                final RectF ir = new RectF(page.type.getInitialRect());
                final float irw = ir.width();
                savedRelativeCrop = new RectF(
                        (oldCb.left - ir.left) / irw, oldCb.top,
                        (oldCb.right - ir.left) / irw, oldCb.bottom);
            }
            needCropRestore = true;
            new EventCrop(base.getDocumentController()).add(page).process().release();
        }

        if (oldCb == null) {
            topLeft.set(0.1f, 0.1f);
            bottomRight.set(0.9f, 0.9f);
        } else {
            final RectF ir = new RectF(page.type.getInitialRect());
            final float irw = ir.width();

            final float left = (oldCb.left - ir.left) / irw;
            final float top = (oldCb.top);
            final float right = (oldCb.right - ir.left) / irw;
            final float bottom = (oldCb.bottom);

            topLeft.set(left, top);
            bottomRight.set(right, bottom);
        }
    }

    public void applyCropping() {
        if (page == null) {
            ViewEffects.toggleControls(this);
            return;
        }

        result = new RectF(Math.min(topLeft.x, bottomRight.x), Math.min(topLeft.y, bottomRight.y), Math.max(topLeft.x,
                bottomRight.x), Math.max(topLeft.y, bottomRight.y));

        final BookSettings bs = base.getBookSettings();
        if (bs != null && bs.defaultCropAction >= 0 && bs.defaultCropAction <= 3) {
            applyAction(bs.defaultCropAction);
            return;
        }

        final ActionDialogBuilder builder = new ActionDialogBuilder(getContext(), controller);
        builder.setTitle(R.string.manual_cropping_title);
        builder.setItems(R.array.list_crop_actions, controller.getOrCreateAction(R.id.actions_applyCrop));
        builder.setNegativeButton(R.string.manual_cropping_back);
        builder.show();
    }

    private void applyAction(final int index) {
        needCropRestore = false;
        ViewEffects.toggleControls(this);

        EventCrop event;
        switch (index) {
            case 0:
                event = new EventCrop(base.getDocumentController(), result, true);
                event.add(page).process().release();
                return;
            case 1:
                event = new EventCrop(base.getDocumentController(), result, true);
                event.addEvenOdd(page, true).process().release();
                return;
            case 2:
                event = new EventCrop(base.getDocumentController(), result, true);
                event.addEvenOdd(page, true).process().release();

                final RectF symm = new RectF();
                symm.left = 1 - result.right;
                symm.top = result.top;
                symm.right = 1 - result.left;
                symm.bottom = result.bottom;

                event = new EventCrop(base.getDocumentController(), symm, true);
                event.addEvenOdd(page, false).process().release();
                return;
            case 3:
                event = new EventCrop(base.getDocumentController(), result, true);
                event.addAll().process().release();
                return;
        }
    }

    @ActionMethod(ids = R.id.actions_applyCrop)
    public void onApply(final ActionEx action) {
        final Integer index = action.getParameter(IActionController.DIALOG_ITEM_PROPERTY);
        if (index == null) {
            return;
        }

        switch (index.intValue()) {
            case 0:
            case 1:
            case 2:
            case 3:
                applyAction(index.intValue());
                return;
            case 4: {
                needCropRestore = false;
                ViewEffects.toggleControls(this);
                final EventCrop event = new EventCrop(base.getDocumentController(), null, true);
                event.add(page).process().release();
                return;
            }
            case 5: {
                needCropRestore = false;
                ViewEffects.toggleControls(this);
                final EventCrop event = new EventCrop(base.getDocumentController(), null, true);
                event.addAll().process().release();
                return;
            }
            case 6: {
                final ActionDialogBuilder subBuilder = new ActionDialogBuilder(getContext(), controller);
                subBuilder.setTitle(R.string.manual_cropping_set_default);
                subBuilder.setItems(R.array.list_crop_default_options,
                        controller.getOrCreateAction(R.id.actions_setDefaultCrop));
                subBuilder.setNegativeButton();
                subBuilder.show();
                return;
            }
            case 7: {
                needCropRestore = false;
                ViewEffects.toggleControls(this);
                final BookSettings bs = base.getBookSettings();
                if (bs != null) {
                    bs.defaultCropAction = -1;
                    bs.lastChanged = System.currentTimeMillis();
                    SettingsManager.storeBookSettings(bs);
                }
                return;
            }
        }
    }

    @ActionMethod(ids = R.id.actions_setDefaultCrop)
    public void onSetDefault(final ActionEx action) {
        final Integer index = action.getParameter(IActionController.DIALOG_ITEM_PROPERTY);
        if (index == null) {
            return;
        }

        final int actionIndex = index.intValue();
        if (actionIndex >= 0 && actionIndex <= 3) {
            final BookSettings bs = base.getBookSettings();
            if (bs != null) {
                bs.defaultCropAction = actionIndex;
                bs.lastChanged = System.currentTimeMillis();
                SettingsManager.storeBookSettings(bs);
            }
            applyAction(actionIndex);
        }
    }

    @Override
    public void setVisibility(final int visibility) {
        final boolean wasVisible = getVisibility() == View.VISIBLE;
        if (visibility == View.VISIBLE) {
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }
        super.setVisibility(visibility);
        if (wasVisible && visibility != View.VISIBLE) {
            setLayerType(LAYER_TYPE_NONE, null);
            if (needCropRestore && page != null) {
                needCropRestore = false;
                new EventCrop(base.getDocumentController(), savedRelativeCrop, false)
                        .add(page).process().release();
            }
            if (savedPageAlign != null) {
                base.getBookSettings().pageAlign = savedPageAlign;
                savedPageAlign = null;
                base.getDocumentController().invalidatePageSizes(
                        IViewController.InvalidateSizeReason.PAGE_LOADED, null);
                base.getZoomModel().setZoom(savedZoom, true);
            }
        }
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        if (page == null) {
            return;
        }

        final RectF r = getActualRect();

        canvas.drawColor(0x7F000000);

        canvas.save();

        canvas.clipRect(r.left, r.top, r.right, r.bottom);
        canvas.drawColor(0x00FFFFFF, Mode.CLEAR);
        canvas.restore();

        final Drawable d = base.getContext().getResources().getDrawable(R.drawable.components_cropper_circle);
        d.setBounds((int) (r.left - 25), (int) (r.top - 25), (int) (r.left + 25), (int) (r.top + 25));
        d.draw(canvas);

        d.setBounds((int) (r.right - 25), (int) (r.bottom - 25), (int) (r.right + 25), (int) (r.bottom + 25));
        d.draw(canvas);

        canvas.drawLine(0, r.top, getWidth(), r.top, PAINT);
        canvas.drawLine(0, r.bottom, getWidth(), r.bottom, PAINT);
        canvas.drawLine(r.left, 0, r.left, getHeight(), PAINT);
        canvas.drawLine(r.right, 0, r.right, getHeight(), PAINT);

        final float density = getResources().getDisplayMetrics().density;
        final float btnW = 72 * density;
        final float btnH = 44 * density;
        final float btnGap = 24 * density;
        final float btnY = getHeight() - btnH - 24 * density;
        final float centerX = getWidth() / 2f;

        btnCancel.set(centerX - btnGap / 2 - btnW, btnY, centerX - btnGap / 2, btnY + btnH);
        btnOk.set(centerX + btnGap / 2, btnY, centerX + btnGap / 2 + btnW, btnY + btnH);

        BTN_PAINT.setColor(0xCC333333);
        BTN_PAINT.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(btnCancel, 8 * density, 8 * density, BTN_PAINT);
        canvas.drawRoundRect(btnOk, 8 * density, 8 * density, BTN_PAINT);

        BTN_PAINT.setColor(Color.CYAN);
        BTN_PAINT.setStyle(Paint.Style.STROKE);
        BTN_PAINT.setStrokeWidth(2 * density);
        canvas.drawRoundRect(btnCancel, 8 * density, 8 * density, BTN_PAINT);
        canvas.drawRoundRect(btnOk, 8 * density, 8 * density, BTN_PAINT);

        BTN_TEXT_PAINT.setColor(Color.WHITE);
        BTN_TEXT_PAINT.setTextSize(20 * density);
        BTN_TEXT_PAINT.setTextAlign(Paint.Align.CENTER);
        final float textY = btnY + btnH / 2 - (BTN_TEXT_PAINT.descent() + BTN_TEXT_PAINT.ascent()) / 2;
        canvas.drawText("✗", btnCancel.centerX(), textY, BTN_TEXT_PAINT);
        canvas.drawText("✓", btnOk.centerX(), textY, BTN_TEXT_PAINT);
    }

    private RectF getActualRect() {
        final RectF pageBounds = getPageRect();
        final float pageWidth = pageBounds.width();
        final float pageHeight = pageBounds.height();

        final float left = pageBounds.left + topLeft.x * pageWidth;
        final float top = pageBounds.top + topLeft.y * pageHeight;
        final float right = pageBounds.left + bottomRight.x * pageWidth;
        final float bottom = pageBounds.top + bottomRight.y * pageHeight;

        return new RectF(Math.min(left, right), Math.min(top, bottom), Math.max(right, left), Math.max(bottom, top));
    }

    private RectF getPageRect() {
        final ViewState viewState = ViewState.get(base.getDocumentController());
        final RectF pageBounds = viewState.getBounds(page);
        pageBounds.offset(-viewState.viewBase.x, -viewState.viewBase.y);
        viewState.release();
        return pageBounds;
    }

    static String str(final PointF p) {
        return "(" + p.x + ", " + p.y + ")";
    }

    @Override
    public boolean onTouchEvent(final MotionEvent ev) {
        try {
            Thread.sleep(16);
        } catch (final InterruptedException e) {
            Thread.interrupted();
        }

        if ((ev.getAction() & MotionEvent.ACTION_UP) == MotionEvent.ACTION_UP) {
            currentPoint = null;
            final float x = ev.getX();
            final float y = ev.getY();
            if (btnOk.contains(x, y)) {
                applyCropping();
                return true;
            }
            if (btnCancel.contains(x, y)) {
                ViewEffects.toggleControls(this);
                return true;
            }
        }

        return gestureDetector.onTouchEvent(ev);
    }

    protected class GestureListener extends SimpleOnGestureListener {

        private static final int SPOT_SIZE = 25;

        @Override
        public boolean onDown(final MotionEvent e) {
            if (page == null) {
                return true;
            }

            final float x = e.getX();
            final float y = e.getY();
            final RectF r = getActualRect();

            if ((Math.abs(x - r.left) < SPOT_SIZE) && (Math.abs(y - r.top) < SPOT_SIZE)) {
                currentPoint = topLeft;
                return true;
            }

            if ((Math.abs(x - r.right) < SPOT_SIZE) && (Math.abs(y - r.bottom) < SPOT_SIZE)) {
                currentPoint = bottomRight;
                return true;
            }

            currentPoint = null;
            return true;
        }

        @Override
        public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX, final float distanceY) {
            if (page == null || currentPoint == null) {
                return true;
            }

            final float x = e2.getX();
            final float y = e2.getY();
            final RectF r = getPageRect();

            currentPoint.x = MathUtils.adjust((x - r.left) / r.width(), 0f, 1f);
            currentPoint.y = MathUtils.adjust((y - r.top) / r.height(), 0f, 1f);

            invalidate();

            return true;
        }

        @Override
        public boolean onDoubleTap(final MotionEvent e) {
            applyCropping();
            return true;
        }
    }
}
