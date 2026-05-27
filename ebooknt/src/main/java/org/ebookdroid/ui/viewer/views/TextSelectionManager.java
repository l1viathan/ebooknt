package org.ebookdroid.ui.viewer.views;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import org.ebookdroid.core.Page;
import org.ebookdroid.core.codec.PageTextBox;
import org.ebookdroid.core.models.DocumentModel;
import org.ebookdroid.ui.viewer.IActivityController;

import java.util.Collections;
import java.util.List;

public class TextSelectionManager {

    private static final int HANDLE_RADIUS_DP = 12;
    private static final int HANDLE_TOUCH_SLOP_DP = 24;

    private final IActivityController base;
    private final View hostView;
    private final float density;

    private List<PageTextBox> pageWords;
    private int selPageDocIndex = -1;
    private int selStartIdx = -1;
    private int selEndIdx = -1;

    private boolean active;
    private int draggingHandle; // 0=none, 1=start, 2=end
    private ActionMode actionMode;

    private final Paint highlightPaint = new Paint();
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private HandleOverlay handleOverlay;

    public TextSelectionManager(final IActivityController base, final View hostView) {
        this.base = base;
        this.hostView = hostView;
        this.density = hostView.getResources().getDisplayMetrics().density;
        highlightPaint.setColor(0x4033B5E5);
        handlePaint.setColor(0xFF33B5E5);
    }

    public boolean isActive() {
        return active;
    }

    public boolean startSelection(final float viewX, final float viewY) {
        final DocumentModel model = base.getDocumentModel();
        if (model == null) return false;

        final float zoom = base.getZoomModel().getZoom();
        final float scrollX = base.getDocumentController().getView().getContentScrollX();
        final float scrollY = base.getDocumentController().getView().getContentScrollY();
        final float absX = viewX + scrollX;
        final float absY = viewY + scrollY;

        final Page[] allPages = model.getPages();
        final RectF bounds = new RectF();
        for (final Page page : allPages) {
            page.getBounds(zoom, bounds);
            if (absX >= bounds.left && absX <= bounds.right
                    && absY >= bounds.top && absY <= bounds.bottom) {
                float normX = (absX - bounds.left) / bounds.width();
                float normY = (absY - bounds.top) / bounds.height();
                final RectF crop = page.getCropping();
                if (crop != null) {
                    normX = crop.left + normX * crop.width();
                    normY = crop.top + normY * crop.height();
                }
                return startSelectionOnPage(page.index.docIndex, normX, normY);
            }
        }
        return false;
    }

    private boolean startSelectionOnPage(final int pageDocIndex,
                                         final float normX, final float normY) {
        final org.ebookdroid.core.DecodeService ds = base.getDecodeService();
        if (ds == null) return false;
        final List<PageTextBox> boxes = ds.getPageText(pageDocIndex);
        if (boxes == null || boxes.isEmpty()) return false;

        int bestIdx = -1;
        float bestDist = Float.MAX_VALUE;
        boolean direct = false;
        for (int i = 0; i < boxes.size(); i++) {
            final PageTextBox box = boxes.get(i);
            final float dx = Math.max(0, Math.max(box.left - normX, normX - box.right));
            final float dy = Math.max(0, Math.max(box.top - normY, normY - box.bottom));
            final float edgeDist = dx * dx + dy * dy;
            if (edgeDist == 0) {
                final float cx = (box.left + box.right) / 2;
                final float cy = (box.top + box.bottom) / 2;
                final float cd = (normX - cx) * (normX - cx) + (normY - cy) * (normY - cy);
                if (!direct || cd < bestDist) {
                    bestIdx = i;
                    bestDist = cd;
                    direct = true;
                }
            } else if (!direct && edgeDist < bestDist) {
                bestDist = edgeDist;
                bestIdx = i;
            }
        }
        if (bestIdx < 0) return false;

        this.pageWords = boxes;
        this.selPageDocIndex = pageDocIndex;
        this.selStartIdx = bestIdx;
        this.selEndIdx = bestIdx;
        this.active = true;
        this.draggingHandle = 0;

        ensureHandleOverlay();
        handleOverlay.setVisibility(View.VISIBLE);
        showActionMode();
        requestRedraw();
        return true;
    }

    public void clearSelection() {
        dispose();
        requestRedraw();
    }

    public void dispose() {
        active = false;
        selStartIdx = -1;
        selEndIdx = -1;
        selPageDocIndex = -1;
        pageWords = null;
        draggingHandle = 0;
        if (actionMode != null) {
            actionMode.finish();
            actionMode = null;
        }
        if (handleOverlay != null) {
            final android.view.ViewGroup parent =
                    (android.view.ViewGroup) handleOverlay.getParent();
            if (parent != null) {
                parent.removeView(handleOverlay);
            }
            handleOverlay = null;
        }
    }

    public boolean onTouchEvent(final MotionEvent ev) {
        if (!active) return false;

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                draggingHandle = hitTestHandle(ev.getX(), ev.getY());
                return draggingHandle != 0;

            case MotionEvent.ACTION_MOVE:
                if (draggingHandle != 0) {
                    updateSelectionByDrag(ev.getX(), ev.getY());
                    return true;
                }
                return false;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (draggingHandle != 0) {
                    draggingHandle = 0;
                    showActionMode();
                    return true;
                }
                return false;
        }
        return false;
    }

    public boolean onSingleTap(final float x, final float y) {
        if (!active) return false;
        if (hitTestHandle(x, y) != 0) return true;
        if (!hitTestSelection(x, y)) {
            clearSelection();
        }
        return true;
    }

    private int hitTestHandle(final float viewX, final float viewY) {
        if (!active || pageWords == null) return 0;
        final float slop = HANDLE_TOUCH_SLOP_DP * density;

        final RectF startScreen = getWordScreenRect(selStartIdx);
        final RectF endScreen = getWordScreenRect(selEndIdx);
        if (startScreen == null || endScreen == null) return 0;

        final float r = HANDLE_RADIUS_DP * density;

        final float startAnchorX = startScreen.left;
        final float startAnchorY = startScreen.bottom;
        final float startCx = startAnchorX - r;
        final float startCy = startAnchorY + r + r;
        if (dist(viewX, viewY, startCx, startCy) < slop + r) {
            return 1;
        }

        final float endAnchorX = endScreen.right;
        final float endAnchorY = endScreen.bottom;
        final float endCx = endAnchorX + r;
        final float endCy = endAnchorY + r + r;
        if (dist(viewX, viewY, endCx, endCy) < slop + r) {
            return 2;
        }
        return 0;
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        final float dx = x1 - x2;
        final float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private boolean hitTestSelection(final float viewX, final float viewY) {
        if (!active || pageWords == null) return false;
        final int lo = Math.min(selStartIdx, selEndIdx);
        final int hi = Math.max(selStartIdx, selEndIdx);
        for (int i = lo; i <= hi; i++) {
            final RectF r = getWordScreenRect(i);
            if (r != null && r.contains(viewX, viewY)) return true;
        }
        return false;
    }

    private void updateSelectionByDrag(final float viewX, final float viewY) {
        final int wordIdx = findNearestWord(viewX, viewY);
        if (wordIdx < 0) return;
        if (draggingHandle == 1) {
            selStartIdx = Math.min(wordIdx, selEndIdx);
        } else {
            selEndIdx = Math.max(wordIdx, selStartIdx);
        }
        if (handleOverlay != null) handleOverlay.invalidate();
        if (actionMode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            actionMode.invalidateContentRect();
        }
        requestRedraw();
    }

    private int findNearestWord(final float viewX, final float viewY) {
        if (pageWords == null) return -1;
        final float zoom = base.getZoomModel().getZoom();
        final float scrollX = base.getDocumentController().getView().getContentScrollX();
        final float scrollY = base.getDocumentController().getView().getContentScrollY();
        final float absX = viewX + scrollX;
        final float absY = viewY + scrollY;

        final DocumentModel model = base.getDocumentModel();
        if (model == null) return -1;
        final Page page = model.getPageByDocIndex(selPageDocIndex);
        if (page == null) return -1;
        final RectF bounds = new RectF();
        page.getBounds(zoom, bounds);

        float normX = (absX - bounds.left) / bounds.width();
        float normY = (absY - bounds.top) / bounds.height();
        final RectF crop = page.getCropping();
        if (crop != null) {
            normX = crop.left + normX * crop.width();
            normY = crop.top + normY * crop.height();
        }

        int bestIdx = -1;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < pageWords.size(); i++) {
            final PageTextBox box = pageWords.get(i);
            final float cx = (box.left + box.right) / 2;
            final float cy = (box.top + box.bottom) / 2;
            final float d = (normX - cx) * (normX - cx) + (normY - cy) * (normY - cy);
            if (d < bestDist) {
                bestDist = d;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private RectF getWordScreenRect(final int wordIdx) {
        if (pageWords == null || wordIdx < 0 || wordIdx >= pageWords.size()) return null;
        final DocumentModel model = base.getDocumentModel();
        if (model == null) return null;
        final Page page = model.getPageByDocIndex(selPageDocIndex);
        if (page == null) return null;
        final float zoom = base.getZoomModel().getZoom();
        final RectF pageBounds = new RectF();
        page.getBounds(zoom, pageBounds);
        final PageTextBox box = pageWords.get(wordIdx);
        final RectF r = page.getPageRegion(pageBounds, new RectF(box));
        if (r == null) return null;
        final float scrollX = base.getDocumentController().getView().getContentScrollX();
        final float scrollY = base.getDocumentController().getView().getContentScrollY();
        r.offset(-scrollX, -scrollY);
        return r;
    }

    public String getSelectedText() {
        if (!active || pageWords == null) return null;
        final int lo = Math.min(selStartIdx, selEndIdx);
        final int hi = Math.max(selStartIdx, selEndIdx);
        final StringBuilder sb = new StringBuilder();
        for (int i = lo; i <= hi; i++) {
            final PageTextBox box = pageWords.get(i);
            if (box.text != null) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(box.text.trim());
            }
        }
        return sb.toString();
    }

    public List<RectF> getSelectionRects() {
        if (!active || pageWords == null) return Collections.emptyList();
        final int lo = Math.min(selStartIdx, selEndIdx);
        final int hi = Math.max(selStartIdx, selEndIdx);
        final List<RectF> rects = new java.util.ArrayList<>(hi - lo + 1);
        for (int i = lo; i <= hi; i++) {
            rects.add(new RectF(pageWords.get(i)));
        }
        return rects;
    }

    public int getSelectionPageDocIndex() {
        return selPageDocIndex;
    }

    private void requestRedraw() {
        final View v = base.getDocumentController().getView().getView();
        if (v instanceof org.emdev.ui.gl.GLRootView) {
            ((org.emdev.ui.gl.GLRootView) v).requestRender();
        }
        if (handleOverlay != null) {
            handleOverlay.postInvalidate();
        }
    }

    public void onScrollChanged() {
        if (active && handleOverlay != null) {
            handleOverlay.postInvalidate();
        }
    }

    private void showActionMode() {
        if (actionMode != null) return;
        final Context ctx = base.getContext();
        if (!(ctx instanceof android.app.Activity)) return;
        final android.app.Activity activity = (android.app.Activity) ctx;

        final ActionMode.Callback callback = new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(0, 1, 0, "复制").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                final String dictLabel = DictionaryManager.getSelectedLabel(ctx);
                final String label = dictLabel != null ? "查询 (" + dictLabel + ")" : "查询";
                menu.add(0, 2, 1, label).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                return true;
            }
            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                final String text = getSelectedText();
                if (text == null || text.isEmpty()) return false;
                switch (item.getItemId()) {
                    case 1:
                        copyToClipboard(text);
                        clearSelection();
                        return true;
                    case 2:
                        sendLookupIntent(text);
                        clearSelection();
                        return true;
                }
                return false;
            }
            @Override
            public void onDestroyActionMode(ActionMode mode) {
                actionMode = null;
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            actionMode = activity.startActionMode(new ActionMode.Callback2() {
                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    return callback.onCreateActionMode(mode, menu);
                }
                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return callback.onPrepareActionMode(mode, menu);
                }
                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return callback.onActionItemClicked(mode, item);
                }
                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    callback.onDestroyActionMode(mode);
                }
                @Override
                public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
                    final RectF sel = getSelectionScreenRect();
                    if (sel != null) {
                        outRect.set((int) sel.left, (int) sel.top,
                                    (int) sel.right, (int) sel.bottom);
                    }
                }
            }, ActionMode.TYPE_FLOATING);
        } else {
            actionMode = activity.startActionMode(callback);
        }
    }

    private RectF getSelectionScreenRect() {
        if (!active || pageWords == null) return null;
        final int lo = Math.min(selStartIdx, selEndIdx);
        final int hi = Math.max(selStartIdx, selEndIdx);
        RectF union = null;
        for (int i = lo; i <= hi; i++) {
            final RectF r = getWordScreenRect(i);
            if (r == null) continue;
            if (union == null) {
                union = new RectF(r);
            } else {
                union.union(r);
            }
        }
        return union;
    }

    private void copyToClipboard(final String text) {
        final Context ctx = base.getContext();
        final ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("text", text));
        Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show();
    }

    private void sendLookupIntent(final String text) {
        final Context ctx = base.getContext();
        if (!DictionaryManager.lookup(ctx, text)) {
            Toast.makeText(ctx, "请在设置中选择查询应用", Toast.LENGTH_SHORT).show();
        }
    }

    private void ensureHandleOverlay() {
        if (handleOverlay != null) return;
        final android.widget.FrameLayout frame =
                (android.widget.FrameLayout) hostView.getParent();
        handleOverlay = new HandleOverlay(hostView.getContext());
        frame.addView(handleOverlay, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private class HandleOverlay extends View {
        HandleOverlay(Context ctx) {
            super(ctx);
            setWillNotDraw(false);
            setClickable(false);
            setFocusable(false);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!active) return;
            final RectF startRect = getWordScreenRect(selStartIdx);
            final RectF endRect = getWordScreenRect(selEndIdx);
            if (startRect == null || endRect == null) return;

            drawHandle(canvas, startRect.left, startRect.bottom, true);
            drawHandle(canvas, endRect.right, endRect.bottom, false);
        }

        private void drawHandle(Canvas canvas, float x, float y, boolean isStart) {
            final float r = HANDLE_RADIUS_DP * density;
            final float stemBottom = y + r;

            handlePaint.setStrokeWidth(2 * density);
            canvas.drawLine(x, y, x, stemBottom, handlePaint);

            final float cx = isStart ? x - r : x + r;
            final float cy = stemBottom + r;
            canvas.drawCircle(cx, cy, r, handlePaint);

            final Path path = new Path();
            path.moveTo(x, stemBottom);
            path.lineTo(cx, cy - r);
            if (isStart) {
                path.lineTo(cx - r, cy);
            } else {
                path.lineTo(cx + r, cy);
            }
            path.close();
            canvas.drawPath(path, handlePaint);
        }
    }
}
