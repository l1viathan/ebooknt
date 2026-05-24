package org.ebookdroid.ui.viewer;

import org.ebookdroid.common.settings.types.DocumentViewMode;
import org.ebookdroid.common.settings.types.RotationType;
import org.emdev.ui.uimanager.UIManagerAppCompat;
import org.ebooknt.viewer.R;
import org.ebookdroid.common.settings.AppSettings;
import org.ebookdroid.common.settings.SettingsManager;
import org.ebookdroid.common.settings.books.BookSettings;
import org.ebookdroid.common.settings.books.Bookmark;
import org.ebookdroid.common.settings.types.ToastPosition;
import org.ebookdroid.common.touch.TouchManagerView;
import org.ebookdroid.core.DecodeService;
import org.ebookdroid.core.codec.CodecFeatures;
import org.ebookdroid.ui.viewer.OpenBooksManager;
import org.ebookdroid.ui.viewer.stubs.ViewStub;
import org.ebookdroid.ui.viewer.viewers.GLView;
import org.ebookdroid.ui.viewer.views.ManualCropView;
import org.ebookdroid.ui.viewer.views.PageViewZoomControls;
import org.ebookdroid.ui.viewer.views.SearchControls;
import org.ebookdroid.ui.viewer.views.ViewEffects;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
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

    private OpenBooksDrawerHelper.EdgeSwipeHelper edgeSwipeHelper;

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
            frameLayout.addView(getZoomControls(), new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM));
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
        drawerLayout.setScrimColor(Color.TRANSPARENT);
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

        edgeSwipeHelper = new OpenBooksDrawerHelper.EdgeSwipeHelper(drawerLayout);
        edgeSwipeHelper.setOnBeforeOpen(new Runnable() {
            @Override public void run() {
                if (openBooksAdapter != null) {
                    openBooksAdapter.refresh(getController() != null
                        ? getController().getCurrentBookPath() : null);
                }
            }
        });

        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(final View drawerView, float slideOffset) {
            }
            @Override
            public void onDrawerOpened(final View drawerView) {
                centerDrawerItems(headerSpacer, footerSpacer);
            }
            @Override
            public void onDrawerClosed(final View drawerView) {
                final View glView = view.getView();
                if (glView instanceof org.emdev.ui.gl.GLRootView) {
                    ((org.emdev.ui.gl.GLRootView) glView).requestLayoutContentPane();
                }
            }
            @Override
            public void onDrawerStateChanged(int newState) {
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
        view.onResume();
        final BookSettings bs = getController().getBookSettings();
        if (bs != null) {
            setRequestedOrientation(bs.getOrientation(AppSettings.current()));
        }
        OpenBooksManager.get().onBookResumed(getController().getCurrentBookPath());
        if (openBooksAdapter != null) {
            openBooksAdapter.refresh(getController().getCurrentBookPath());
        }
    }

    private boolean edgeSwipeIntercepting;

    @Override
    public boolean dispatchTouchEvent(final MotionEvent ev) {
        if (edgeSwipeHelper != null && edgeSwipeHelper.handleTouch(ev, this)) {
            if (!edgeSwipeIntercepting) {
                edgeSwipeIntercepting = true;
                final MotionEvent cancel = MotionEvent.obtain(ev);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
            }
            return true;
        }
        edgeSwipeIntercepting = false;
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
        view.onPause();
        OpenBooksManager.get().onBookPaused(getController().getCurrentBookPath());
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
            zoomControls.setOnDismissListener(new Runnable() {
                @Override
                public void run() {
                    if (view != null) {
                        view.redrawView();
                    }
                }
            });
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
                bs.rotation == RotationType.PORTRAIT,
                R.id.mainmenu_force_portrait);
        ActionMenuHelper.setMenuItemChecked(menu,
                bs.rotation == RotationType.LANDSCAPE,
                R.id.mainmenu_force_landscape);
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

    private void centerDrawerItems(final View headerSpacer, final View footerSpacer) {
        drawerList.post(new Runnable() {
            @Override
            public void run() {
                final int listH = drawerList.getHeight();
                if (listH <= 0) return;
                int itemH = 0;
                for (int i = 0; i < drawerList.getChildCount(); i++) {
                    final View child = drawerList.getChildAt(i);
                    if (child != null && child != headerSpacer && child != footerSpacer) {
                        itemH = child.getHeight();
                        break;
                    }
                }
                if (itemH <= 0) return;
                final int totalItems = drawerList.getAdapter().getCount() - 2;
                final int contentH = totalItems * itemH;
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

        static final class BookItem {
            final String path;
            final String title;
            final boolean isCurrent;
            final int currentPage;
            final int pageCount;
            BookItem(final String path, final boolean isCurrent) {
                this.path = path;
                this.title = OpenBooksManager.getDisplayTitle(path);
                this.isCurrent = isCurrent;
                this.pageCount = OpenBooksManager.get().getPageCount(path);
                if (pageCount > 0) {
                    final BookSettings bs = SettingsManager.getBookSettings(path);
                    this.currentPage = bs != null && bs.currentPage != null ? bs.currentPage.viewIndex + 1 : 0;
                } else {
                    this.currentPage = 0;
                }
            }
        }

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
            notifyDataSetChanged();
        }

        @Override
        public int getCount() { return items.size(); }

        @Override
        public Object getItem(final int pos) { return items.get(pos); }

        @Override
        public long getItemId(final int pos) { return pos; }

        @Override
        public boolean isEnabled(final int pos) { return true; }

        @Override
        public View getView(final int pos, View convertView, final ViewGroup parent) {
            ImageView icon;
            TextView tv;
            TextView progressTv;
            if (convertView == null) {
                final float density = ctx.getResources().getDisplayMetrics().density;
                final int dp16 = (int) (16 * density + 0.5f);
                final int dp12 = (int) (12 * density + 0.5f);
                final int dp24 = (int) (24 * density + 0.5f);
                final int dp8 = (int) (8 * density + 0.5f);

                final LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp16, dp12, dp8, dp12);
                row.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                    android.widget.AbsListView.LayoutParams.MATCH_PARENT,
                    android.widget.AbsListView.LayoutParams.WRAP_CONTENT));

                icon = new ImageView(ctx);
                final LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp24, dp24);
                iconLp.rightMargin = dp16;
                icon.setLayoutParams(iconLp);
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                row.addView(icon);

                tv = new TextView(ctx);
                tv.setTextSize(15);
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                final LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                tv.setLayoutParams(tvLp);
                row.addView(tv);

                progressTv = new TextView(ctx);
                progressTv.setTextSize(13);
                progressTv.setSingleLine(true);
                final LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                pLp.leftMargin = dp8;
                progressTv.setLayoutParams(pLp);
                row.addView(progressTv);

                convertView = row;
            } else {
                icon = (ImageView) ((ViewGroup) convertView).getChildAt(0);
                tv = (TextView) ((ViewGroup) convertView).getChildAt(1);
                progressTv = (TextView) ((ViewGroup) convertView).getChildAt(2);
            }
            final Object item = items.get(pos);
            if (item instanceof BookItem) {
                final BookItem book = (BookItem) item;
                final boolean isActive = book.isCurrent || OpenBooksManager.get().isActive(book.path);
                if (book.isCurrent) {
                    tv.setText("▸ " + book.title);
                    tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    tv.setTextColor(0xFFFFCC80);
                    icon.setAlpha(1.0f);
                } else if (isActive) {
                    tv.setText(book.title);
                    tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    tv.setTextColor(0xFFFFFFFF);
                    icon.setAlpha(0.9f);
                } else {
                    tv.setText(book.title);
                    tv.setTypeface(android.graphics.Typeface.DEFAULT);
                    tv.setTextColor(0xFFD0D0D0);
                    icon.setAlpha(0.5f);
                }
                icon.setImageResource(R.drawable.viewer_menu_bookmark);
                if (book.pageCount > 0) {
                    progressTv.setText(book.currentPage + "/" + book.pageCount);
                    progressTv.setTextColor(0xFFAAAAAA);
                    progressTv.setVisibility(View.VISIBLE);
                } else {
                    progressTv.setVisibility(View.GONE);
                }
            } else {
                tv.setText(R.string.drawer_action_library);
                tv.setTypeface(android.graphics.Typeface.DEFAULT);
                tv.setTextColor(0xFFB0B0B0);
                icon.setImageResource(R.drawable.application_icon);
                icon.setAlpha(0.7f);
                progressTv.setVisibility(View.GONE);
            }
            return convertView;
        }
    }

}
