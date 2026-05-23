package org.ebookdroid.ui.library;

import org.ebookdroid.CodecType;
import org.emdev.ui.uimanager.IUIManager;
import org.emdev.ui.uimanager.UIManagerAppCompat;
import org.ebooknt.viewer.R;
import org.ebookdroid.common.cache.CacheManager;
import org.ebookdroid.common.settings.LibSettings;
import org.ebookdroid.common.settings.SettingsManager;
import org.ebookdroid.common.settings.books.BookSettings;
import org.ebookdroid.common.settings.books.Bookmark;
import org.ebookdroid.ui.library.adapters.BookNode;
import org.ebookdroid.ui.library.adapters.BrowserAdapter;
import org.ebookdroid.ui.library.dialogs.FolderDlg;
import org.ebookdroid.ui.library.tasks.CopyBookTask;
import org.ebookdroid.ui.library.tasks.MoveBookTask;
import org.ebookdroid.ui.library.tasks.RenameBookTask;
import org.ebookdroid.ui.settings.SettingsUI;
import org.ebookdroid.ui.viewer.OpenBooksManager;
import org.ebookdroid.ui.viewer.ViewerActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.AppCompatEditText;
import android.text.Editable;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.ImageView;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

import org.emdev.BaseDroidApp;
import org.emdev.common.android.AndroidVersion;
import org.emdev.common.filesystem.CompositeFilter;
import org.emdev.common.filesystem.DirectoryFilter;
import org.emdev.common.filesystem.PathFromUri;
import org.emdev.ui.AbstractActivityController;
import org.emdev.ui.actions.ActionDialogBuilder;
import org.emdev.ui.actions.ActionEx;
import org.emdev.ui.actions.ActionMenuHelper;
import org.emdev.ui.actions.ActionMethod;
import org.emdev.ui.actions.params.Constant;
import org.emdev.ui.actions.params.EditableValue;
import org.emdev.utils.CompareUtils;
import org.emdev.utils.FileUtils;
import org.emdev.utils.LengthUtils;

public class BrowserActivityController extends AbstractActivityController<BrowserActivity> implements IBrowserActivity {

    private static final String CURRENT_DIRECTORY = "currentDirectory";

    FileFilter filter;
    BrowserAdapter adapter;

    public BrowserActivityController(final BrowserActivity activity) {
        super(activity, BEFORE_CREATE, ON_POST_CREATE);
        this.filter = new CompositeFilter(false, DirectoryFilter.NOT_HIDDEN, LibSettings.current().allowedFileTypes);
    }

    /**
     * {@inheritDoc}
     *
     * @see org.emdev.ui.AbstractActivityController#beforeCreate(android.app.Activity)
     */
    @Override
    public void beforeCreate(final BrowserActivity activity) {
        adapter = new BrowserAdapter(activity, filter);
    }

    /**
     * {@inheritDoc}
     *
     * @see org.emdev.ui.AbstractActivityController#onPostCreate(android.os.Bundle, boolean)
     */
    @Override
    public void onPostCreate(final Bundle savedInstanceState, final boolean recreated) {
        if (recreated) {
            return;
        }

        goHome(null);

        final BrowserActivity activity = getManagedComponent();
        final Uri data = activity.getIntent().getData();
        if (data != null) {
            setCurrentDir(new File(PathFromUri.retrieve(activity.getContentResolver(), data)));
        } else if (savedInstanceState != null) {
            final String absolutePath = savedInstanceState.getString(CURRENT_DIRECTORY);
            if (absolutePath != null) {
                setCurrentDir(new File(absolutePath));
            }
        } else {
            final String libPath = LibSettings.current().libraryPath;
            if (LengthUtils.isNotEmpty(libPath)) {
                setCurrentDir(new File(libPath));
            }
        }

        showProgress(false);
    }

    public boolean onKeyUp(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && !event.isCanceled()) {
            final File dir = adapter.getCurrentDirectory();
            final File home = getHomeDirectory();
            final boolean atHome = dir != null && dir.getAbsolutePath().equals(home.getAbsolutePath());
            if (!atHome && dir != null && dir.getParentFile() != null) {
                setCurrentDir(dir.getParentFile());
            } else if (!org.ebookdroid.ui.viewer.OpenBooksManager.navigateToLastOpenBook(getManagedComponent())) {
                getManagedComponent().finish();
            }
            return true;
        }
        return false;
    }

    private File getHomeDirectory() {
        final String libPath = LibSettings.current().libraryPath;
        if (LengthUtils.isNotEmpty(libPath)) {
            return new File(libPath);
        } else if (BaseDroidApp.EXT_STORAGE.exists()) {
            return BaseDroidApp.EXT_STORAGE;
        } else {
            return new File("/");
        }
    }

    @ActionMethod(ids = R.id.browserhome)
    public void goHome(final ActionEx action) {
        setCurrentDir(getHomeDirectory());
    }

    @ActionMethod(ids = R.id.browsermenu_close)
    public void closeBrowser(final ActionEx action) {
        OpenBooksManager.get().setLastLibraryView(OpenBooksManager.LIBRARY_VIEW_RECENT);
        if (!org.ebookdroid.ui.viewer.OpenBooksManager.navigateToLastOpenBook(getManagedComponent())) {
            final Intent intent = new Intent(getManagedComponent(), RecentActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            getManagedComponent().startActivity(intent);
        }
    }

    public void goRecent(final ActionEx action) {
        final BrowserActivity activity = getManagedComponent();
        final Intent myIntent = new Intent(activity, RecentActivity.class);
        myIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        activity.startActivity(myIntent);
    }

    @ActionMethod(ids = R.id.mainmenu_settings)
    public void showSettings(final ActionEx action) {
        SettingsUI.showAppSettings(getManagedComponent(), null);
    }


/**
     * {@inheritDoc}
     *
     * @see org.ebookdroid.ui.library.IBrowserActivity#showDocument(android.net.Uri, org.ebookdroid.common.settings.books.Bookmark)
     */
    @Override
    public void showDocument(final Uri uri, final Bookmark b) {
        OpenBooksManager.get().setLastLibraryView(OpenBooksManager.LIBRARY_VIEW_BROWSER);
        final BrowserActivity activity = getManagedComponent();
        final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setClass(activity, ViewerActivity.class);
        if (b != null) {
            intent.putExtra("pageIndex", "" + b.page.viewIndex);
            intent.putExtra("offsetX", "" + b.offsetX);
            intent.putExtra("offsetY", "" + b.offsetY);
        }
        if (OpenBooksManager.get().isOpen(uri.getPath())) {
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        }
        activity.startActivity(intent);
    }

    /**
     * {@inheritDoc}
     *
     * @see org.ebookdroid.ui.library.IBrowserActivity#setCurrentDir(java.io.File)
     */
    @Override
    public void setCurrentDir(final File newDir) {
        adapter.setCurrentDirectory(newDir);
        getManagedComponent().setTitle(newDir);
    }

    /**
     * {@inheritDoc}
     *
     * @see org.emdev.common.filesystem.FileSystemScanner.ProgressListener#showProgress(boolean)
     */
    @Override
    public void showProgress(final boolean show) {
        final BrowserActivity activity = getManagedComponent();
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                UIManagerAppCompat.setProgressSpinnerVisible(activity, show);
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * @see org.ebookdroid.ui.library.IBrowserActivity#loadThumbnail(java.lang.String, android.widget.ImageView, int)
     */
    @Override
    public void loadThumbnail(final String path, final ImageView imageView, final int defaultResID) {
        imageView.setImageResource(defaultResID);
    }

    @ActionMethod(ids = R.id.actions_goToBookmark)
    public void openBook(final ActionEx action) {
        final File file = action.getParameter(ActionMenuHelper.MENU_ITEM_SOURCE);
        if (!file.isDirectory()) {
            final Bookmark b = action.getParameter("bookmark");
            showDocument(Uri.fromFile(file), b);
        }
    }

    @ActionMethod(ids = R.id.librarymenu_createshortcut)
    public void createDirectoryShortcut(final ActionEx action) {
        final File file = action.getParameter(ActionMenuHelper.MENU_ITEM_SOURCE);
        if (file != null && file.isDirectory()) {
            ShortcutHelper.createDirectoryShortcut(getManagedComponent(), file.getAbsolutePath());
        }
    }

    @ActionMethod(ids = R.id.librarymenu_properties)
    public void showFolderProperties(final ActionEx action) {
        final File file = action.getParameter(ActionMenuHelper.MENU_ITEM_SOURCE);
        if (file == null || !file.isDirectory()) {
            return;
        }
        final BrowserActivity activity = getManagedComponent();
        final long[] stats = new long[3];
        countFolderStats(file, stats);
        final long totalSize = stats[0];
        final int dirCount = (int) stats[1];
        final int bookCount = (int) stats[2];

        final StringBuilder sb = new StringBuilder();
        sb.append(activity.getString(R.string.menu_folder_properties_path, file.getAbsolutePath()));
        sb.append('\n');
        sb.append(activity.getString(R.string.menu_folder_properties_size, FileUtils.getFileSize(totalSize)));
        sb.append('\n');
        sb.append(activity.getString(R.string.menu_folder_properties_dirs, dirCount));
        sb.append('\n');
        sb.append(activity.getString(R.string.menu_folder_properties_books, bookCount));

        new android.support.v7.app.AlertDialog.Builder(activity)
            .setTitle(R.string.menu_folder_properties)
            .setMessage(sb.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private void countFolderStats(final File dir, final long[] stats) {
        final FileFilter bookFilter = LibSettings.current().allowedFileTypes;
        final File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (final File child : children) {
            if (child.isDirectory()) {
                if (!child.isHidden()) {
                    stats[1]++;
                    countFolderStats(child, stats);
                }
            } else {
                stats[0] += child.length();
                if (bookFilter != null && bookFilter.accept(child)) {
                    stats[2]++;
                }
            }
        }
    }

    @ActionMethod(ids = R.id.bookmenu_createshortcut)
    public void createShortcut(final ActionEx action) {
        final File file = action.getParameter(ActionMenuHelper.MENU_ITEM_SOURCE);
        if (file != null) {
            ShortcutHelper.createShortcut(getManagedComponent(), file.getAbsolutePath());
        }
    }

    @ActionMethod(ids = R.id.bookmenu_removefromrecent)
    public void removeBookFromRecents(final ActionEx action) {
        final File file = action.getParameter(ActionMenuHelper.MENU_ITEM_SOURCE);
        if (file != null) {
            SettingsManager.removeBookFromRecents(file.getAbsolutePath());
            adapter.notifyDataSetInvalidated();
        }
    }

    @ActionMethod(ids = R.id.bookmenu_cleardata)
    public void removeCachedBookFiles(final ActionEx action) {
        final File file = action.getParameter(ActionMenuHelper.MENU_ITEM_SOURCE);
        if (file != null) {
            CacheManager.clear(file.getAbsolutePath());
            adapter.notifyDataSetInvalidated();
        }
    }

    @ActionMethod(ids = R.id.bookmenu_deletesettings)
    public void removeBookSettings(final ActionEx action) {
        final File file = action.getParameter(ActionMenuHelper.MENU_ITEM_SOURCE);
        if (file != null) {
            final BookSettings bs = SettingsManager.getBookSettings(file.getAbsolutePath());
            if (bs != null) {
                SettingsManager.deleteBookSettings(bs);
                adapter.notifyDataSetInvalidated();
            }
        }
    }

    @ActionMethod(ids = { R.id.bookmenu_copy, R.id.bookmenu_move })
    public void copyBook(final ActionEx action) {
        final File file = action.getParameter("source");
        if (file == null) {
            return;
        }
        final boolean isCopy = action.id == R.id.bookmenu_copy;
        final int titleId = isCopy ? R.string.copy_book_to_dlg_title : R.string.move_book_to_dlg_title;
        final int id = isCopy ? R.id.actions_doCopyBook : R.id.actions_doMoveBook;

        getOrCreateAction(id).putValue("source", file);

        final FolderDlg dlg = new FolderDlg(this);
        dlg.show(new File(file.getAbsolutePath()), titleId, id);
    }

    @ActionMethod(ids = R.id.actions_doCopyBook)
    public void doCopyBook(final ActionEx action) {
        final File targetFolder = action.getParameter(FolderDlg.SELECTED_FOLDER);
        final File book = action.getParameter("source");
        final BookNode node = new BookNode(book, SettingsManager.getBookSettings(book.getAbsolutePath()));

        new CopyBookTask(this.getManagedComponent(), null, targetFolder).execute(node);
    }

    @ActionMethod(ids = R.id.actions_doMoveBook)
    public void doMoveBook(final ActionEx action) {
        final File targetFolder = action.getParameter(FolderDlg.SELECTED_FOLDER);
        final File book = action.getParameter("source");
        final BookNode node = new BookNode(book, SettingsManager.getBookSettings(book.getAbsolutePath()));

        new MoveBookTask(this.getContext(), null, targetFolder) {

            @Override
            protected void processTargetFile(final File target) {
                super.processTargetFile(target);
                adapter.remove(origin);
            }
        }.execute(node);
    }

    @ActionMethod(ids = R.id.bookmenu_rename)
    public void renameBook(final ActionEx action) {
        final File file = action.getParameter("source");
        if (file == null) {
            return;
        }

        final FileUtils.FilePath path = FileUtils.parseFilePath(file.getAbsolutePath(), CodecType.getAllExtensions());
        final EditText input = new AppCompatEditText(getManagedComponent());
        input.setSingleLine();
        input.setText(path.name);
        input.selectAll();

        final ActionDialogBuilder builder = new ActionDialogBuilder(this.getManagedComponent(), this);
        builder.setTitle(R.string.book_rename_title);
        builder.setMessage(R.string.book_rename_msg);
        builder.setView(input);
        builder.setPositiveButton(R.id.actions_doRenameBook, new Constant("source", file), new Constant("file", path),
                new EditableValue("input", input));
        builder.setNegativeButton().show();
    }

    @ActionMethod(ids = R.id.actions_doRenameBook)
    public void doRenameBook(final ActionEx action) {
        final File book = action.getParameter("source");
        final BookNode node = new BookNode(book, SettingsManager.getBookSettings(book.getAbsolutePath()));
        final FileUtils.FilePath path = action.getParameter("file");
        final Editable value = action.getParameter("input");
        final String newName = value.toString();
        if (!CompareUtils.equals(path.name, newName)) {
            path.name = newName;
            new RenameBookTask(this.getContext(), null, path) {

                @Override
                protected void processTargetFile(final File target) {
                    super.processTargetFile(target);
                    adapter.remove(origin);
                }
            }.execute(node);
        }
    }

    @ActionMethod(ids = R.id.bookmenu_delete)
    public void deleteBook(final ActionEx action) {
        final File file = action.getParameter("source");
        if (file == null) {
            return;
        }

        final ActionDialogBuilder builder = new ActionDialogBuilder(getContext(), this);
        builder.setTitle(R.string.book_delete_title);
        builder.setMessage(R.string.book_delete_msg);
        builder.setPositiveButton(R.id.actions_doDeleteBook, new Constant("source", file));
        builder.setNegativeButton().show();
    }

    @ActionMethod(ids = R.id.actions_doDeleteBook)
    public void doDeleteBook(final ActionEx action) {
        final File file = action.getParameter("source");
        if (file == null) {
            return;
        }

        if (file.delete()) {
            CacheManager.clear(file.getAbsolutePath());
            adapter.remove(file);
        }
    }

    public void searchCurrentDirectory(final String query) {
        final String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return;
        final File dir = adapter.getCurrentDirectory();
        if (dir == null) return;

        final FileFilter bookFilter = LibSettings.current().allowedFileTypes;
        final List<BookNode> results = new ArrayList<>();
        collectMatchingFiles(dir, q.toLowerCase(), bookFilter, results);

        if (results.isEmpty()) return;

        RecentActivity.sSearchParentView = OpenBooksManager.LIBRARY_VIEW_BROWSER;
        RecentActivity.sPendingSearchNodes = results;
        final Intent intent = new Intent(getManagedComponent(), RecentActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        getManagedComponent().startActivity(intent);
    }

    private void collectMatchingFiles(final File dir, final String query,
            final FileFilter bookFilter, final List<BookNode> results) {
        final File[] children = dir.listFiles();
        if (children == null) return;
        for (final File f : children) {
            if (f.isDirectory()) {
                if (!f.isHidden()) {
                    collectMatchingFiles(f, query, bookFilter, results);
                }
            } else if (bookFilter != null && bookFilter.accept(f)) {
                final String name = f.getName();
                final int dot = name.lastIndexOf('.');
                final String base = (dot > 0 ? name.substring(0, dot) : name).toLowerCase();
                if (base.contains(query)) {
                    results.add(new BookNode(f, SettingsManager.getBookSettings(f.getAbsolutePath())));
                }
            }
        }
    }
}
