package org.ebookdroid.ui.library;

import org.emdev.ui.actions.ActionEx;
import org.emdev.ui.actions.params.Constant;
import org.emdev.ui.uimanager.UIManagerAppCompat;
import org.ebooknt.viewer.R;
import org.ebookdroid.common.settings.LibSettings;
import org.ebookdroid.common.settings.books.BookSettings;
import org.ebookdroid.common.settings.books.Bookmark;
import org.ebookdroid.core.PageIndex;
import org.ebookdroid.ui.library.adapters.BookNode;
import org.ebookdroid.ui.library.adapters.BookShelfAdapter;
import org.ebookdroid.ui.library.adapters.BooksAdapter;
import org.ebookdroid.ui.library.adapters.LibraryAdapter;
import org.ebookdroid.ui.library.adapters.RecentAdapter;
import org.ebookdroid.ui.library.adapters.SearchResultsAdapter;
import org.ebookdroid.ui.library.views.BookcaseView;
import org.ebookdroid.ui.library.views.LibraryView;
import org.ebookdroid.ui.library.views.RecentBooksView;
import org.ebookdroid.ui.library.views.SearchResultsView;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.TabLayout;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.SpannableStringBuilder;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.ViewFlipper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ebookdroid.ui.viewer.OpenBooksDrawerHelper;
import org.ebookdroid.ui.viewer.OpenBooksManager;
import org.ebookdroid.ui.viewer.ViewerActivity;
import android.support.v4.widget.DrawerLayout;
import android.widget.ListView;

import org.emdev.BaseDroidApp;
import org.emdev.common.android.AndroidVersion;
import org.emdev.ui.AbstractActionActivity;
import org.emdev.ui.actions.ActionMenuHelper;
import org.emdev.ui.uimanager.IUIManager;
import org.emdev.utils.FileUtils;
import org.emdev.utils.LengthUtils;

public class RecentActivity extends AbstractActionActivity<RecentActivity, RecentActivityController> {

    public static final int VIEW_RECENT = 0;
    public static final int VIEW_LIBRARY = 1;
    public static final int VIEW_SEARCH = 2;

    private ViewFlipper viewflipper;

    private DrawerLayout drawerLayout;
    private OpenBooksDrawerHelper.Adapter openBooksAdapter;

    static boolean sHasSearchResults = false;
    static int sPendingView = -1;

    private Spinner locationSpinner;
    private ArrayList<String> locationItems;
    private ArrayAdapter<String> locationAdapter;
    private boolean spinnerInitialized = false;

    BookcaseView bookcaseView;
    RecentBooksView recentBooksView;
    LibraryView libraryView;
    SearchResultsView searchResultsView;

    public RecentActivity() {
        super(true, ON_CREATE, ON_RESUME);
    }

    /**
     * {@inheritDoc}
     *
     * @see org.emdev.ui.AbstractActionActivity#createController()
     */
    @Override
    protected RecentActivityController createController() {
        return new RecentActivityController(this);
    }

    /**
     * {@inheritDoc}
     *
     * @see org.emdev.ui.AbstractActionActivity#onCreateImpl(android.os.Bundle)
     */
    @Override
    protected void onCreateImpl(final Bundle savedInstanceState) {
        setContentView(R.layout.recent);

        drawerLayout = (DrawerLayout) findViewById(R.id.recent_drawer_layout);
        openBooksAdapter = OpenBooksDrawerHelper.setup(
            this, drawerLayout, (ListView) findViewById(R.id.recent_drawer_list));

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        locationItems = new ArrayList<String>();

        locationAdapter = new ArrayAdapter<String>(this, R.layout.browser_spinner_item, locationItems);
        locationAdapter.setDropDownViewResource(R.layout.browser_spinner_dropdown_item);

        locationSpinner = (Spinner) toolbar.findViewById(R.id.recent_location_spinner);
        locationSpinner.setAdapter(locationAdapter);
        locationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
                if (!spinnerInitialized) return;
                if (position == 0) {
                    changeLibraryView(VIEW_RECENT);
                    UIManagerAppCompat.invalidateOptionsMenu(RecentActivity.this);
                } else if (position == 1) {
                    getController().goFileBrowserFromSpinner();
                } else if (position == 2 && sHasSearchResults) {
                    changeLibraryView(VIEW_SEARCH);
                    UIManagerAppCompat.invalidateOptionsMenu(RecentActivity.this);
                }
            }

            @Override
            public void onNothingSelected(final AdapterView<?> parent) {
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
        if (openBooksAdapter != null) openBooksAdapter.refresh();
        if (sPendingView >= 0) {
            final int view = sPendingView;
            sPendingView = -1;
            changeLibraryView(view);
        }
        rebuildLocationSpinner();
        UIManagerAppCompat.invalidateOptionsMenu(this);

        // HACK: invalidating the adapter when the tab view is not visible seems to leave
        // the scroll position in the wrong place.
        Handler h = new Handler(Looper.getMainLooper());
        h.post(new Runnable() {
            @Override
            public void run() {
                final TabLayout tl = (TabLayout) findViewById(R.id.tabs);
                if (tl != null) {
                    tl.setScrollPosition(tl.getSelectedTabPosition(), 0.0f, true);
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.recentmenu, menu);

        MenuItem searchItem = menu.findItem(R.id.recentmenu_searchBook);
        if (searchItem != null) {
            android.view.View actionView = searchItem.getActionView();
            if (actionView == null) {
                actionView = MenuItemCompat.getActionView(searchItem);
            }
            if (actionView instanceof SearchView) {
                final SearchView searchView = (SearchView) actionView;
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        ActionEx a = getController().getOrCreateAction(R.id.actions_searchBook);
                        a.addParameter(new Constant("input", new SpannableStringBuilder(query)));
                        a.run();
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        return false;
                    }
                });
                searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionExpand(MenuItem item) {
                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem item) {
                        if (sHasSearchResults) {
                            changeLibraryView(VIEW_SEARCH);
                        } else {
                            changeLibraryView(VIEW_RECENT);
                        }
                        return true;
                    }
                });
            }
        }

        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.emdev.ui.AbstractActionActivity#updateMenuItems(android.view.Menu)
     */
    @Override
    protected void updateMenuItems(final Menu menu) {
        final int viewMode = getViewMode();
        final boolean isSearch = viewMode == VIEW_SEARCH;
        final boolean isRecent = viewMode == VIEW_RECENT;

        ActionMenuHelper.setMenuItemVisible(menu, !isSearch, R.id.recent_showbrowser);
        ActionMenuHelper.setMenuItemVisible(menu, !isSearch, R.id.recentmenu_searchBook);
        ActionMenuHelper.setMenuItemVisible(menu, isSearch, R.id.recentmenu_closeSearch);
        ActionMenuHelper.setMenuItemVisible(menu, isRecent, R.id.mainmenu_settings);
        ActionMenuHelper.setMenuItemVisible(menu, isRecent, R.id.mainmenu_about);
        ActionMenuHelper.setMenuItemVisible(menu, isRecent, R.id.recentmenu_cleanrecent);

        final LibSettings ls = LibSettings.current();
        ActionMenuHelper.setMenuItemExtra(menu, R.id.recent_storage_all, "path", "/");
        final String extPath = BaseDroidApp.EXT_STORAGE != null ? BaseDroidApp.EXT_STORAGE.getAbsolutePath() : "/";
        ActionMenuHelper.setMenuItemExtra(menu, R.id.recent_storage_external, "path", extPath);

        final MenuItem storageMenu = menu.findItem(R.id.recent_storage_menu);
        if (storageMenu != null) {
            storageMenu.setVisible(isRecent);
            final SubMenu subMenu = storageMenu.getSubMenu();
            subMenu.removeGroup(R.id.actions_storageGroup);

            final Set<String> added = new HashSet<String>();
            added.add("/");
            added.add(FileUtils.getCanonicalPath(BaseDroidApp.EXT_STORAGE));

            if (ls.libraryPath != null && !ls.libraryPath.isEmpty()) {
                    final File file = new File(ls.libraryPath);
                    final String mp = FileUtils.getCanonicalPath(file);
                    if (mp != null && added.add(mp)) {
                        addStorageMenuItem(subMenu, R.drawable.recent_menu_storage_scanned, file.getPath(), ls.libraryPath);
                    }
                }
        }
    }

    protected void addStorageMenuItem(final Menu menu, final int resId, final String name, final String path) {
        final MenuItem bmi = menu.add(R.id.actions_storageGroup, R.id.actions_storage, Menu.NONE, name);
        bmi.setIcon(resId);
        ActionMenuHelper.setMenuItemExtra(bmi, "path", path);
    }

    /**
     * {@inheritDoc}
     *
     * @see android.app.Activity#onCreateContextMenu(android.view.ContextMenu, android.view.View, android.view.ContextMenu.ContextMenuInfo)
     */
    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenuInfo menuInfo) {
        final Object source = getContextMenuSource(v, menuInfo);

        if (source instanceof BookNode) {
            onCreateBookMenu(menu, (BookNode) source);
        } else if (source instanceof BookShelfAdapter) {
            onCreateShelfMenu(menu, (BookShelfAdapter) source);
        }

        ActionMenuHelper.setMenuSource(getController(), menu, source);
    }

    protected Object getContextMenuSource(final View v, final ContextMenuInfo menuInfo) {
        Object source = null;
        if (menuInfo instanceof AdapterContextMenuInfo) {
            final AbsListView list = (AbsListView) v;
            final AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfo;
            source = list.getAdapter().getItem(mi.position);
        } else if (menuInfo instanceof ExpandableListContextMenuInfo) {
            final ExpandableListView list = (ExpandableListView) v;
            final ExpandableListAdapter adapter = list.getExpandableListAdapter();
            final ExpandableListContextMenuInfo mi = (ExpandableListContextMenuInfo) menuInfo;
            final long pp = mi.packedPosition;
            final int group = ExpandableListView.getPackedPositionGroup(pp);
            final int child = ExpandableListView.getPackedPositionChild(pp);
            if (child >= 0) {
                source = adapter.getChild(group, child);
            } else {
                source = adapter.getGroup(group);
            }
        }
        return source;
    }

    protected void onCreateBookMenu(final ContextMenu menu, final BookNode node) {
        final BookSettings bs = node.settings;
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.book_menu, menu);

        menu.setHeaderTitle(node.path);
        menu.findItem(R.id.bookmenu_recentgroup).setVisible(bs != null);

        final BookShelfAdapter bookShelf = getController().getBookShelf(node);
        final BookShelfAdapter current = bookcaseView != null ? getController().getBookShelf(
                bookcaseView.getCurrentList()) : null;
        menu.findItem(R.id.bookmenu_openbookshelf).setVisible(
                bookShelf != null && current != null && bookShelf != current);

        final MenuItem om = menu.findItem(R.id.bookmenu_open);
        final SubMenu osm = om != null ? om.getSubMenu() : null;
        if (osm == null) {
            return;
        }
        osm.clear();

        final List<Bookmark> list = new ArrayList<Bookmark>();
        list.add(new Bookmark(true, getString(R.string.bookmark_start), PageIndex.FIRST, 0, 0));
        list.add(new Bookmark(true, getString(R.string.bookmark_end), PageIndex.LAST, 0, 1));
        if (bs != null) {
            if (LengthUtils.isNotEmpty(bs.bookmarks)) {
                list.addAll(bs.bookmarks);
            }
            list.add(new Bookmark(true, getString(R.string.bookmark_current), bs.currentPage, bs.offsetX, bs.offsetY));
        }

        Collections.sort(list);
        for (final Bookmark b : list) {
            addBookmarkMenuItem(osm, b);
        }
    }

    protected void addBookmarkMenuItem(final Menu menu, final Bookmark b) {
        final MenuItem bmi = menu.add(R.id.actions_goToBookmarkGroup, R.id.actions_goToBookmark, Menu.NONE, b.name);
        bmi.setIcon(R.drawable.viewer_menu_bookmark);
        ActionMenuHelper.setMenuItemExtra(bmi, "bookmark", b);
    }

    protected void onCreateShelfMenu(final ContextMenu menu, final BookShelfAdapter a) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.library_menu, menu);
        menu.setHeaderTitle(a.name);
    }

    void changeLibraryView(final int view) {
        final ViewFlipper vf = getViewflipper();
        if (view == VIEW_SEARCH) {
            vf.setDisplayedChild(VIEW_SEARCH);
            syncSpinnerToView(2);
        } else {
            vf.setDisplayedChild(VIEW_RECENT);
            syncSpinnerToView(0);
        }
        UIManagerAppCompat.invalidateOptionsMenu(this);
    }

    void showSearchResults() {
        sHasSearchResults = true;
        rebuildLocationSpinner();
        changeLibraryView(VIEW_SEARCH);
    }

    void closeSearchResults() {
        sHasSearchResults = false;
        rebuildLocationSpinner();
        changeLibraryView(VIEW_RECENT);
    }

    private void syncSpinnerToView(final int position) {
        if (locationSpinner != null && position < locationItems.size()) {
            spinnerInitialized = false;
            locationSpinner.setSelection(position);
            locationSpinner.post(new Runnable() {
                @Override
                public void run() {
                    spinnerInitialized = true;
                }
            });
        }
    }

    int getViewMode() {
        final ViewFlipper vf = getViewflipper();
        return vf != null ? vf.getDisplayedChild() : VIEW_RECENT;
    }

    void showBookshelf(final int shelfIndex) {
        if (bookcaseView != null) {
            bookcaseView.setCurrentList(shelfIndex);
        }
    }

    void showNextBookshelf() {
        if (bookcaseView != null) {
            bookcaseView.nextList();
        }
    }

    void showPrevBookshelf() {
        if (bookcaseView != null) {
            bookcaseView.prevList();
        }
    }

    void showBookcase(final BooksAdapter bookshelfAdapter, final RecentAdapter recentAdapter) {
        final ViewFlipper vf = getViewflipper();
        vf.removeAllViews();
        if (bookcaseView == null) {
            bookcaseView = (BookcaseView) LayoutInflater.from(this).inflate(R.layout.bookcase_view, vf, false);
            bookcaseView.init(bookshelfAdapter, recentAdapter);
        }
        vf.addView(bookcaseView, 0);
    }

    void showLibrary(final LibraryAdapter libraryAdapter, final RecentAdapter recentAdapter,
            final SearchResultsAdapter searchResultsAdapter) {
        if (recentBooksView == null) {
            recentBooksView = new RecentBooksView(getController(), recentAdapter);
            registerForContextMenu(recentBooksView);
        }
        if (libraryView == null) {
            libraryView = new LibraryView(getController(), libraryAdapter);
            registerForContextMenu(libraryView);
        }
        if (searchResultsView == null) {
            searchResultsView = new SearchResultsView(getController(), searchResultsAdapter);
            registerForContextMenu(searchResultsView);
        }

        final ViewFlipper vf = getViewflipper();
        vf.removeAllViews();
        vf.addView(recentBooksView, VIEW_RECENT);
        vf.addView(libraryView, VIEW_LIBRARY);
        vf.addView(searchResultsView, VIEW_SEARCH);
    }

    ViewFlipper getViewflipper() {
        if (viewflipper == null) {
            viewflipper = (ViewFlipper) findViewById(R.id.recentflip);
        }

        return viewflipper;
    }

    private void rebuildLocationSpinner() {
        if (locationSpinner == null) return;
        spinnerInitialized = false;

        final int viewMode = getViewMode();

        locationItems.clear();
        locationItems.add(getString(R.string.nav_label_recent));
        locationItems.add(getString(R.string.nav_label_files));
        if (sHasSearchResults) {
            locationItems.add(getString(R.string.nav_label_search_results));
        }

        locationAdapter.notifyDataSetChanged();

        int selection = 0;
        if (viewMode == VIEW_SEARCH && sHasSearchResults) {
            selection = 2;
        }
        locationSpinner.setSelection(selection);
        locationSpinner.post(new Runnable() {
            @Override
            public void run() {
                spinnerInitialized = true;
            }
        });
    }
}
