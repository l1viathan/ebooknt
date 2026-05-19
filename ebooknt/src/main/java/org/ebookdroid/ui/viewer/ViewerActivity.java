package org.ebookdroid.ui.viewer;

import org.ebookdroid.common.settings.types.DocumentViewMode;
import org.ebookdroid.common.settings.types.RotationType;
import org.emdev.ui.uimanager.UIManagerAppCompat;
import org.ebooknt.viewer.R;
import org.ebookdroid.common.settings.AppSettings;
import org.ebookdroid.common.settings.books.BookSettings;
import org.ebookdroid.common.settings.books.Bookmark;
import org.ebookdroid.common.settings.types.ToastPosition;
import org.ebookdroid.common.touch.TouchManagerView;
import org.ebookdroid.core.DecodeService;
import org.ebookdroid.core.codec.CodecFeatures;
import org.ebookdroid.ui.viewer.stubs.ViewStub;
import org.ebookdroid.ui.viewer.viewers.GLView;
import org.ebookdroid.ui.viewer.views.ManualCropView;
import org.ebookdroid.ui.viewer.views.PageViewZoomControls;
import org.ebookdroid.ui.viewer.views.SearchControls;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import org.emdev.common.android.AndroidVersion;
import org.emdev.ui.AbstractActionActivity;
import org.emdev.ui.actions.ActionDialogBuilder;
import org.emdev.ui.actions.ActionMenuHelper;
import org.emdev.ui.gl.GLConfiguration;
import org.emdev.ui.uimanager.IUIManager;
import org.emdev.utils.LayoutUtils;
import org.emdev.utils.LengthUtils;

public class ViewerActivity extends AbstractActionActivity<ViewerActivity, ViewerActivityController> {

    public static final DisplayMetrics DM = new DisplayMetrics();

    IView view;

    private FrameLayout rootView;
    private TextView pageOverlay;
    private TextView zoomOverlay;
    private final Handler overlayHandler = new Handler(Looper.getMainLooper());
    private final Runnable hidePageOverlay = () -> pageOverlay.setVisibility(View.GONE);
    private final Runnable hideZoomOverlay = () -> zoomOverlay.setVisibility(View.GONE);

    private PageViewZoomControls zoomControls;

    private SearchControls searchControls;

    private FrameLayout frameLayout;

    private TouchManagerView touchView;

    private boolean menuClosedCalled;

    private ManualCropView cropControls;

    private DrawerLayout drawerLayout;

    private ListView drawerList;

    private OpenBooksAdapter openBooksAdapter;

    private float edgeSwipeStartX = -1;
    private float edgeSwipeStartY = -1;
    private float drawerSwipeStartX = -1;
    private float drawerSwipeStartY = -1;

    /**
     * Instantiates a new base viewer activity.
     */
    public ViewerActivity() {
        super(false, ON_CREATE, ON_RESUME, ON_PAUSE, ON_DESTROY);
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.emdev.ui.AbstractActionActivity#createController()
     */
    @Override
    protected ViewerActivityController createController() {
        return new ViewerActivityController(this);
    }

    /**
     * {@inheritDoc}
     * 
     * @see android.app.Activity#onNewIntent(android.content.Intent)
     */
    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        if (LCTX.isDebugEnabled()) {
            LCTX.d("onNewIntent(): " + intent);
        }
        getController().loadBook(intent);
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.emdev.ui.AbstractActionActivity#onCreateImpl(android.os.Bundle)
     */
    @Override
    protected void onCreateImpl(final Bundle savedInstanceState) {
        getWindowManager().getDefaultDisplay().getMetrics(DM);
        LCTX.i("XDPI=" + DM.xdpi + ", YDPI=" + DM.ydpi);

        final View rootLayout = getLayoutInflater().inflate(R.layout.viewer, null);
        rootView = (FrameLayout) rootLayout.findViewById(R.id.viewer_content);

        frameLayout = (FrameLayout) rootView.findViewById(R.id.framelayout);

        view = ViewStub.STUB;

        try {
            GLConfiguration.checkConfiguration();

            view = new GLView(getController());
            this.registerForContextMenu(view.getView());

            LayoutUtils.fillInParent(frameLayout, view.getView());

            frameLayout.addView(view.getView());
            frameLayout.addView(getZoomControls());
            frameLayout.addView(getManualCropControls());
            frameLayout.addView(getSearchControls());
            frameLayout.addView(getTouchView());

        } catch (final Throwable th) {
            final ActionDialogBuilder builder = new ActionDialogBuilder(this, getController());
            builder.setTitle(R.string.error_dlg_title);
            builder.setMessage(th.getMessage());
            builder.setPositiveButton(R.string.error_close, R.id.mainmenu_close);
            builder.show();
        }

        drawerLayout = (DrawerLayout) rootLayout.findViewById(R.id.viewer_drawer_layout);
        drawerList = (ListView) rootLayout.findViewById(R.id.viewer_drawer_list);

        setContentView(rootLayout);
        initOverlays();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Push toolbar below the status bar when SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN is active.
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            final int sbInset = insets.getSystemWindowInsetTop();
            final android.widget.LinearLayout.LayoutParams lp =
                    (android.widget.LinearLayout.LayoutParams) v.getLayoutParams();
            if (lp.topMargin != sbInset) {
                lp.topMargin = sbInset;
                v.setLayoutParams(lp);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(toolbar);

        openBooksAdapter = new OpenBooksAdapter(this);

        final View headerSpacer = new View(this);
        final View footerSpacer = new View(this);
        drawerList.addHeaderView(headerSpacer, null, false);
        drawerList.addFooterView(footerSpacer, null, false);

        drawerList.setAdapter(openBooksAdapter);

        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(final View drawerView) {
                centerDrawerItems(headerSpacer, footerSpacer);
            }
            @Override
            public void onDrawerClosed(final View drawerView) {
                if (view != null && view.getView() != null) {
                    view.getView().requestLayout();
                }
            }
        });

        drawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                final Object item = parent.getItemAtPosition(position);
                if (item instanceof OpenBooksAdapter.BookItem) {
                    final OpenBooksAdapter.BookItem book = (OpenBooksAdapter.BookItem) item;
                    if (!book.isCurrent) {
                        getController().switchToOpenBook(book.path);
                    }
                } else if (item == OpenBooksAdapter.LIBRARY_SENTINEL) {
                    getController().goToLibrary(null);
                }
                // QUIT_SENTINEL and other cases: close the drawer.
                if (drawerLayout != null) {
                    drawerLayout.closeDrawers();
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * @see org.emdev.ui.AbstractActionActivity#onResumeImpl()
     */
    @Override
    protected void onResumeImpl() {
        IUIManager.instance.onResume(this);
        if (openBooksAdapter != null) {
            openBooksAdapter.refresh(getController().getCurrentBookPath());
        }
    }

    @Override
    public boolean dispatchTouchEvent(final MotionEvent ev) {
        if (drawerLayout != null) {
            final float density = getResources().getDisplayMetrics().density;
            final float edgeSize = 40 * density;
            final float minSwipe = 60 * density;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (drawerLayout.isDrawerOpen(Gravity.START)) {
                        drawerSwipeStartX = ev.getX();
                        drawerSwipeStartY = ev.getY();
                        edgeSwipeStartX = -1;
                    } else if (ev.getX() < edgeSize) {
                        edgeSwipeStartX = ev.getX();
                        edgeSwipeStartY = ev.getY();
                        drawerSwipeStartX = -1;
                    } else {
                        edgeSwipeStartX = -1;
                        drawerSwipeStartX = -1;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (edgeSwipeStartX >= 0) {
                        final float dx = ev.getX() - edgeSwipeStartX;
                        final float dy = Math.abs(ev.getY() - edgeSwipeStartY);
                        if (dx > minSwipe && dy < dx) {
                            if (openBooksAdapter != null) {
                                openBooksAdapter.refresh(getController() != null
                                    ? getController().getCurrentBookPath() : null);
                            }
                            drawerLayout.openDrawer(Gravity.START);
                            edgeSwipeStartX = -1;
                            ev.setAction(MotionEvent.ACTION_CANCEL);
                        }
                    } else if (drawerSwipeStartX >= 0) {
                        final float dx = ev.getX() - drawerSwipeStartX;
                        final float dy = Math.abs(ev.getY() - drawerSwipeStartY);
                        if (dx < -minSwipe && dy < -dx) {
                            drawerLayout.closeDrawers();
                            drawerSwipeStartX = -1;
                            ev.setAction(MotionEvent.ACTION_CANCEL);
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (drawerLayout.isDrawerOpen(Gravity.START) && drawerSwipeStartX >= 0) {
                        final float upX = ev.getX();
                        final float upY = ev.getY();
                        final float moveDx = Math.abs(upX - drawerSwipeStartX);
                        final float moveDy = Math.abs(upY - drawerSwipeStartY);
                        if (moveDx < 10 * density && moveDy < 10 * density
                                && isOnQuitSentinel(upX, upY)) {
                            drawerLayout.closeDrawers();
                            ev.setAction(MotionEvent.ACTION_CANCEL);
                        }
                    }
                    edgeSwipeStartX = -1;
                    drawerSwipeStartX = -1;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    edgeSwipeStartX = -1;
                    drawerSwipeStartX = -1;
                    break;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * {@inheritDoc}
     *
     * @see org.emdev.ui.AbstractActionActivity#onPauseImpl(boolean)
     */
    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPauseImpl(final boolean finishing) {
        IUIManager.instance.onPause(this);
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.emdev.ui.AbstractActionActivity#onDestroyImpl(boolean)
     */
    @Override
    protected void onDestroyImpl(final boolean finishing) {
        overlayHandler.removeCallbacksAndMessages(null);
        view.onDestroy();
    }

    /**
     * {@inheritDoc}
     * 
     * @see android.app.Activity#onWindowFocusChanged(boolean)
     */
    @Override
    public void onWindowFocusChanged(final boolean hasFocus) {
        if (hasFocus && this.view != null) {
            IUIManager.instance.setFullScreenMode(this, this.view.getView(), AppSettings.current().fullScreen);
        }
    }

    private void initOverlays() {
        final float dp = getResources().getDisplayMetrics().density;
        pageOverlay = createOverlay(dp);
        rootView.addView(pageOverlay);
        zoomOverlay = createOverlay(dp);
        rootView.addView(zoomOverlay);
    }

    private TextView createOverlay(final float dp) {
        final TextView tv = new TextView(this);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        final int ph = (int) (12 * dp);
        final int pv = (int) (8 * dp);
        tv.setPadding(ph, pv, ph, pv);
        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x80000000);
        bg.setCornerRadius(pv);
        tv.setBackground(bg);
        tv.setVisibility(View.GONE);
        final int margin = (int) (8 * dp);
        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(margin, margin, margin, margin);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void showOverlay(final TextView tv, final String text, final int gravity, final Runnable hideTask) {
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tv.getLayoutParams();
        if (lp.gravity != gravity) {
            lp.gravity = gravity;
            tv.setLayoutParams(lp);
        }
        tv.setText(text);
        tv.setVisibility(View.VISIBLE);
        overlayHandler.removeCallbacks(hideTask);
        overlayHandler.postDelayed(hideTask, 2000);
    }

    public TouchManagerView getTouchView() {
        if (touchView == null) {
            touchView = new TouchManagerView(getController());
        }
        return touchView;
    }

    public void currentPageChanged(final String pageText) {
        if (LengthUtils.isEmpty(pageText)) {
            return;
        }
        final AppSettings app = AppSettings.current();
        if (UIManagerAppCompat.isToolbarVisible(this) && app.pageInTitle) {
            return;
        }
        final ToastPosition pos = app.pageNumberToastPosition;
        if (pos == ToastPosition.Invisible) {
            overlayHandler.removeCallbacks(hidePageOverlay);
            pageOverlay.setVisibility(View.GONE);
            return;
        }
        showOverlay(pageOverlay, pageText, pos.position, hidePageOverlay);
    }

    public void zoomChanged(final float zoom) {
        if (getZoomControls().isShown()) {
            return;
        }
        final AppSettings app = AppSettings.current();
        final ToastPosition pos = app.zoomToastPosition;
        if (pos == ToastPosition.Invisible) {
            overlayHandler.removeCallbacks(hideZoomOverlay);
            zoomOverlay.setVisibility(View.GONE);
            return;
        }
        showOverlay(zoomOverlay, String.format("%.2f", zoom) + "x", pos.position, hideZoomOverlay);
    }

    public PageViewZoomControls getZoomControls() {
        if (zoomControls == null) {
            zoomControls = new PageViewZoomControls(this, getController().getZoomModel());
            zoomControls.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
        }
        return zoomControls;
    }

    public SearchControls getSearchControls() {
        if (searchControls == null) {
            searchControls = new SearchControls(this);
        }
        return searchControls;
    }

    public ManualCropView getManualCropControls() {
        if (cropControls == null) {
            cropControls = new ManualCropView(getController());
        }
        return cropControls;
    }

    /**
     * {@inheritDoc}
     * 
     * @see android.app.Activity#onCreateContextMenu(android.view.ContextMenu, android.view.View,
     *      android.view.ContextMenu.ContextMenuInfo)
     */
    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenuInfo menuInfo) {
        menu.clear();
        menu.setHeaderIcon(R.drawable.application_icon);
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu_context, menu);
        enableMenuGroupDividers(menu);
        updateMenuItems(menu);
    }

    /**
     * {@inheritDoc}
     * 
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        menu.clear();

        final MenuInflater inflater = getMenuInflater();

        if (hasNormalMenu()) {
            inflater.inflate(R.menu.mainmenu, menu);
        } else {
            inflater.inflate(R.menu.mainmenu_context, menu);
        }

        return true;
    }

    protected boolean hasNormalMenu() {
        return IUIManager.instance.isTabletUi(this) || AppSettings.current().showTitle;
    }

    /**
     * {@inheritDoc}
     * 
     * @see android.app.Activity#onMenuOpened(int, android.view.Menu)
     */
    @Override
    public boolean onMenuOpened(final int featureId, final Menu menu) {
        view.changeLayoutLock(true);
        IUIManager.instance.onMenuOpened(this);
        return super.onMenuOpened(featureId, menu);
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.emdev.ui.AbstractActionActivity#updateMenuItems(android.view.Menu)
     */
    @Override
    protected void updateMenuItems(final Menu menu) {
        final AppSettings as = AppSettings.current();

        ActionMenuHelper.setMenuItemChecked(menu, as.fullScreen, R.id.mainmenu_fullscreen);
        ActionMenuHelper.setMenuItemChecked(menu, as.showTitle, R.id.mainmenu_showtitle);
        ActionMenuHelper
                .setMenuItemChecked(menu, getZoomControls().getVisibility() == View.VISIBLE, R.id.mainmenu_zoom);

        final BookSettings bs = getController().getBookSettings();
        if (bs == null) {
            return;
        }

        ActionMenuHelper.setMenuItemChecked(menu,
                (bs.rotation == RotationType.PORTRAIT || bs.rotation == RotationType.REVERSE_PORTRAIT),
                R.id.mainmenu_force_portrait);
        ActionMenuHelper.setMenuItemChecked(menu,
                (bs.rotation == RotationType.LANDSCAPE || bs.rotation == RotationType.REVERSE_LANDSCAPE),
                R.id.mainmenu_force_landscape);
        ActionMenuHelper.setMenuItemChecked(menu,
                (bs.rotation == RotationType.REVERSE_LANDSCAPE || bs.rotation == RotationType.REVERSE_PORTRAIT),
                R.id.mainmenu_reverse_orientation);
        ActionMenuHelper.setMenuItemEnabled(menu,
                (bs.rotation == RotationType.PORTRAIT
                || bs.rotation == RotationType.REVERSE_PORTRAIT
                || bs.rotation == RotationType.LANDSCAPE
                || bs.rotation == RotationType.REVERSE_LANDSCAPE),
                R.id.mainmenu_reverse_orientation);
        ActionMenuHelper.setMenuItemChecked(menu, bs.nightMode, R.id.mainmenu_nightmode);
        ActionMenuHelper.setMenuItemChecked(menu, bs.cropPages, R.id.mainmenu_croppages);
        ActionMenuHelper.setMenuItemChecked(menu, bs.splitPages, R.id.mainmenu_splitpages,
                R.drawable.viewer_menu_split_pages, R.drawable.viewer_menu_split_pages_off);
        ActionMenuHelper.setMenuItemChecked(menu, bs.viewMode == DocumentViewMode.SINGLE_PAGE,
                R.id.mainmenu_singlepage);

        final DecodeService ds = getController().getDecodeService();

        final boolean cropSupported = ds.isFeatureSupported(CodecFeatures.FEATURE_CROP_SUPPORT);
        ActionMenuHelper.setMenuItemVisible(menu, cropSupported, R.id.mainmenu_croppages);
        ActionMenuHelper.setMenuItemVisible(menu, cropSupported, R.id.mainmenu_crop);

        final boolean splitSupported = ds.isFeatureSupported(CodecFeatures.FEATURE_SPLIT_SUPPORT);
        ActionMenuHelper.setMenuItemVisible(menu, splitSupported, R.id.mainmenu_splitpages);

        final MenuItem navMenu = menu.findItem(R.id.mainmenu_nav_menu);
        if (navMenu != null) {
            final SubMenu subMenu = navMenu.getSubMenu();
            subMenu.removeGroup(R.id.actions_goToBookmarkGroup);
            if (AppSettings.current().showBookmarksInMenu && LengthUtils.isNotEmpty(bs.bookmarks)) {
                for (final Bookmark b : bs.bookmarks) {
                    addBookmarkMenuItem(subMenu, b);
                }
            }
        }

    }

    protected void addBookmarkMenuItem(final Menu menu, final Bookmark b) {
        final MenuItem bmi = menu.add(R.id.actions_goToBookmarkGroup, R.id.actions_goToBookmark, Menu.NONE, b.name);
        bmi.setIcon(R.drawable.viewer_menu_bookmark);
        ActionMenuHelper.setMenuItemExtra(bmi, "bookmark", b);
    }

    /**
     * {@inheritDoc}
     * 
     * @see android.app.Activity#onPanelClosed(int, android.view.Menu)
     */
    @Override
    public void onPanelClosed(final int featureId, final Menu menu) {
        menuClosedCalled = false;
        super.onPanelClosed(featureId, menu);
        if (!menuClosedCalled) {
            onOptionsMenuClosed(menu);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see android.app.Activity#onOptionsMenuClosed(android.view.Menu)
     */
    @Override
    public void onOptionsMenuClosed(final Menu menu) {
        menuClosedCalled = true;
        IUIManager.instance.onMenuClosed(this);
        view.changeLayoutLock(false);
    }

    /**
     * {@inheritDoc}
     * 
     * @see android.app.Activity#dispatchKeyEvent(android.view.KeyEvent)
     */
    @Override
    public final boolean dispatchKeyEvent(final KeyEvent event) {
        view.checkFullScreenMode();
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if (!hasNormalMenu()) {
                getController().getOrCreateAction(R.id.actions_openOptionsMenu).run();
                return true;
            }
        }

        if (getController().dispatchKeyEvent(event)) {
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    private static void enableMenuGroupDividers(final android.view.Menu menu) {
        // android.view.Menu.setGroupDividerEnabled exists since API 28
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            menu.setGroupDividerEnabled(true);
            return;
        }
        // On older APIs, try reflection (e.g. support-lib MenuBuilder)
        try {
            menu.getClass().getMethod("setGroupDividerEnabled", boolean.class).invoke(menu, true);
        } catch (final Exception ignored) {}
    }


    public void showViewerContextMenu() {
        final PopupMenu pm = new PopupMenu(this, view.getView());
        pm.inflate(R.menu.mainmenu_context);
        final Menu menu = pm.getMenu();
        updateMenuItems(menu);
        view.changeLayoutLock(true);
        showContextMenuLevel(menu);
    }

    private void showContextMenuLevel(final Menu menu) {
        final List<MenuItem> visible = new ArrayList<>();
        for (int i = 0; i < menu.size(); i++) {
            final MenuItem item = menu.getItem(i);
            if (item.isVisible()) visible.add(item);
        }
        if (visible.isEmpty()) {
            view.changeLayoutLock(false);
            return;
        }

        final ListView lv = new ListView(this);
        lv.setDivider(new ColorDrawable(0xFFDDDDDD));
        lv.setDividerHeight(2);
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return visible.size(); }
            @Override public Object getItem(int pos) { return visible.get(pos); }
            @Override public long getItemId(int pos) { return pos; }
            @Override public View getView(int pos, View convertView, final ViewGroup parent) {
                if (convertView == null) {
                    final LinearLayout row = new LinearLayout(ViewerActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    final ImageView iv = new ImageView(ViewerActivity.this);
                    iv.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
                    iv.setPadding(32, 0, 0, 0);
                    final TextView tv = new TextView(ViewerActivity.this);
                    final LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    tv.setLayoutParams(tvLp);
                    tv.setPadding(24, 28, 0, 28);
                    tv.setTextSize(16);
                    final TextView arrow = new TextView(ViewerActivity.this);
                    arrow.setPadding(0, 28, 32, 28);
                    arrow.setTextSize(16);
                    arrow.setText("›");
                    row.addView(iv);
                    row.addView(tv);
                    row.addView(arrow);
                    convertView = row;
                }
                final LinearLayout row = (LinearLayout) convertView;
                final ImageView iv = (ImageView) row.getChildAt(0);
                final TextView tv = (TextView) row.getChildAt(1);
                final TextView arrow = (TextView) row.getChildAt(2);
                final MenuItem item = visible.get(pos);
                iv.setImageDrawable(item.getIcon());
                tv.setText(item.getTitle());
                tv.setAlpha(item.isEnabled() ? 1f : 0.4f);
                arrow.setVisibility(item.hasSubMenu() ? View.VISIBLE : View.INVISIBLE);
                return convertView;
            }
        });

        final AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(lv)
            .setOnCancelListener(d -> view.changeLayoutLock(false))
            .create();

        lv.setOnItemClickListener((adp, v, pos, id) -> {
            final MenuItem item = visible.get(pos);
            if (!item.isEnabled()) return;
            dialog.setOnCancelListener(null);
            dialog.dismiss();
            if (item.hasSubMenu()) {
                showContextMenuLevel(item.getSubMenu());
            } else {
                onOptionsItemSelected(item);
                view.changeLayoutLock(false);
            }
        });

        dialog.show();
    }

    public void showToastText(final int duration, final int resId, final Object... args) {
        Toast.makeText(getApplicationContext(), getResources().getString(resId, args), duration).show();
    }

    private boolean isOnQuitSentinel(final float winX, final float winY) {
        if (openBooksAdapter == null || drawerList == null) return false;
        final int count = openBooksAdapter.getCount();
        if (count == 0) return false;
        // QUIT_SENTINEL is the last adapter item; ListView position = adapter pos + 1 header
        final int quitListPos = count; // adapter pos (count-1) + 1 header = count
        final int firstVis = drawerList.getFirstVisiblePosition();
        final int childIdx = quitListPos - firstVis;
        if (childIdx < 0 || childIdx >= drawerList.getChildCount()) return false;
        final View quitView = drawerList.getChildAt(childIdx);
        if (quitView == null) return false;
        final int[] loc = new int[2];
        drawerList.getLocationInWindow(loc);
        final float top = loc[1] + quitView.getTop();
        final float bot = loc[1] + quitView.getBottom();
        final float left = loc[0] + quitView.getLeft();
        final float right = loc[0] + quitView.getRight();
        return winX >= left && winX <= right && winY >= top && winY <= bot;
    }

    private void centerDrawerItems(final View headerSpacer, final View footerSpacer) {
        drawerList.post(new Runnable() {
            @Override
            public void run() {
                final int listH = drawerList.getHeight();
                int contentH = 0;
                for (int i = 0; i < drawerList.getChildCount(); i++) {
                    final View child = drawerList.getChildAt(i);
                    if (child != null && child != headerSpacer && child != footerSpacer) {
                        contentH += child.getHeight();
                    }
                }
                if (contentH <= 0 || listH <= 0) return;
                final int spacerH = Math.max(0, (listH - contentH) / 2);
                final android.widget.AbsListView.LayoutParams lp =
                    new android.widget.AbsListView.LayoutParams(
                        android.widget.AbsListView.LayoutParams.MATCH_PARENT, spacerH);
                headerSpacer.setLayoutParams(lp);
                footerSpacer.setLayoutParams(lp);
                drawerList.invalidate();
            }
        });
    }

    static final class OpenBooksAdapter extends BaseAdapter {

        static final Object LIBRARY_SENTINEL = new Object();
        static final Object QUIT_SENTINEL = new Object();

        static final class BookItem {
            final String path;
            final String title;
            final boolean isCurrent;
            BookItem(final String path, final boolean isCurrent) {
                this.path = path;
                this.title = OpenBooksManager.getDisplayTitle(path);
                this.isCurrent = isCurrent;
            }
        }

        private static final int TYPE_BOOK = 0;
        private static final int TYPE_ACTION = 1;

        private final Context ctx;
        private final List<Object> items = new ArrayList<>();

        OpenBooksAdapter(final Context ctx) {
            this.ctx = ctx;
            refresh(null);
        }

        void refresh(final String currentPath) {
            items.clear();
            for (final String path : OpenBooksManager.get().getOpenBooks()) {
                items.add(new BookItem(path, path.equals(currentPath)));
            }
            items.add(LIBRARY_SENTINEL);
            items.add(QUIT_SENTINEL);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() { return items.size(); }

        @Override
        public Object getItem(final int pos) { return items.get(pos); }

        @Override
        public long getItemId(final int pos) { return pos; }

        @Override
        public int getViewTypeCount() { return 2; }

        @Override
        public int getItemViewType(final int pos) {
            return (items.get(pos) instanceof BookItem) ? TYPE_BOOK : TYPE_ACTION;
        }

        @Override
        public boolean isEnabled(final int pos) { return true; }

        @Override
        public View getView(final int pos, View convertView, final ViewGroup parent) {
            if (convertView == null) {
                final TextView tv = new TextView(ctx);
                tv.setPadding(48, 32, 48, 32);
                tv.setTextSize(15);
                tv.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                    android.widget.AbsListView.LayoutParams.MATCH_PARENT,
                    android.widget.AbsListView.LayoutParams.WRAP_CONTENT));
                convertView = tv;
            }
            final TextView tv = (TextView) convertView;
            final Object item = items.get(pos);
            if (item instanceof BookItem) {
                final BookItem book = (BookItem) item;
                tv.setText(book.title);
                tv.setTypeface(book.isCurrent
                    ? android.graphics.Typeface.DEFAULT_BOLD
                    : android.graphics.Typeface.DEFAULT);
                tv.setTextColor(book.isCurrent ? 0xFF1976D2 : 0xFF212121);
            } else if (item == LIBRARY_SENTINEL) {
                tv.setText(R.string.drawer_action_library);
                tv.setTypeface(android.graphics.Typeface.DEFAULT);
                tv.setTextColor(0xFF1976D2);
            } else {
                tv.setText(R.string.drawer_action_close);
                tv.setTypeface(android.graphics.Typeface.DEFAULT);
                tv.setTextColor(0xFF212121);
            }
            return convertView;
        }
    }

}
