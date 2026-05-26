package org.ebookdroid.ui.viewer.dialogs;

import org.emdev.ui.uimanager.UIManagerAppCompat;
import org.ebooknt.viewer.R;
import org.ebookdroid.common.settings.AppSettings;
import org.ebookdroid.common.settings.books.BookSettings;
import org.ebookdroid.common.settings.books.Bookmark;
import org.ebookdroid.core.NavigationHistoryTree;
import org.ebookdroid.core.Page;
import org.ebookdroid.core.models.DocumentModel;
import org.ebookdroid.ui.viewer.IActivityController;
import org.ebookdroid.ui.viewer.adapters.BookmarkAdapter;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;

import org.emdev.ui.actions.ActionDialogBuilder;
import org.emdev.ui.actions.ActionEx;
import org.emdev.ui.actions.ActionMethod;
import org.emdev.ui.actions.DialogController;
import org.emdev.ui.actions.IActionController;
import org.emdev.ui.actions.params.Constant;
import org.emdev.ui.actions.params.EditableValue;
import org.emdev.ui.widget.IViewContainer;
import org.emdev.ui.widget.SeekBarIncrementHandler;

public class GoToPageDialog extends Dialog {

    final IActivityController base;
    final SeekBarIncrementHandler handler;
    BookmarkAdapter adapter;
    Bookmark current;
    DialogController<GoToPageDialog> actions;
    int offset;
    boolean useFilePage;
    int originalPage;

    public GoToPageDialog(final IActivityController base) {
        super(base.getContext());
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        setCanceledOnTouchOutside(true);
        setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                base.jumpToPage(originalPage, 0, 0, false);
            }
        });
        this.base = base;
        this.actions = new DialogController<GoToPageDialog>(this);
        this.handler = new SeekBarIncrementHandler();

        final BookSettings bs = base.getBookSettings();
        this.offset = bs != null ? bs.firstPageOffset : 1;

        setContentView(R.layout.gotopage);

        final android.view.Window win = getWindow();
        win.setBackgroundDrawableResource(android.R.color.transparent);
        win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        if (AppSettings.current().einkMode) {
            final View root = findViewById(R.id.gotopageView);
            root.setBackgroundColor(0xFFF0F0F0);
            final android.widget.CheckBox cb = (android.widget.CheckBox) findViewById(R.id.useFilePageCheckBox);
            if (cb != null) {
                cb.setTextColor(0xFF444444);
            }
        }

        final SeekBar seekbar = (SeekBar) findViewById(R.id.seekbar);
        final EditText editText = (EditText) findViewById(R.id.pageNumberTextEdit);

        actions.connectViewToAction(R.id.bookmark_add);
        actions.connectViewToAction(R.id.bookmark_remove_all);
        actions.connectViewToAction(R.id.bookmark_remove);
        actions.connectViewToAction(R.id.goToButton);
        actions.connectEditorToAction(editText, R.id.actions_gotoPage);

        seekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(final SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(final SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
                updateControls(progress, false);
            }
        });

        handler.init(new IViewContainer.DialogBridge(this), seekbar, R.id.seekbar_minus, R.id.seekbar_plus);

        final CheckBox filePageCb = (CheckBox) findViewById(R.id.useFilePageCheckBox);
        if (filePageCb == null) {
            // layout-land omits this checkbox
        } else if (offset == 1) {
            filePageCb.setVisibility(View.GONE);
        } else {
            filePageCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(final CompoundButton button, final boolean checked) {
                    useFilePage = checked;
                    updateControls(seekbar.getProgress(), false);
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        final android.view.Window win = getWindow();
        win.setGravity(Gravity.CENTER);
        win.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                      WindowManager.LayoutParams.WRAP_CONTENT);

        final DocumentModel dm = base.getDocumentModel();
        final Page lastPage = dm != null ? dm.getLastPageObject() : null;
        final int current = dm != null ? dm.getCurrentViewPageIndex() : 0;
        final int max = lastPage != null ? lastPage.index.viewIndex : 0;

        originalPage = current;

        adapter = new BookmarkAdapter(this.getContext(), actions, lastPage, base.getBookSettings());

        final ListView bookmarks = (ListView) findViewById(R.id.bookmarks);
        bookmarks.setAdapter(adapter);

        final SeekBar seekbar = (SeekBar) findViewById(R.id.seekbar);
        seekbar.setMax(max);

        updateControls(current, true);
    }

    @Override
    protected void onStop() {
        final ListView bookmarks = (ListView) findViewById(R.id.bookmarks);
        bookmarks.setAdapter(null);
        adapter = null;
        UIManagerAppCompat.invalidateOptionsMenu(base.getManagedComponent());
    }

    @ActionMethod(ids = { R.id.goToButton, R.id.actions_gotoPage })
    public void goToPageAndDismiss(final ActionEx action) {
        if (current != null) {
            final Page actualPage = current.page.getActualPage(base.getDocumentModel(), adapter.bookSettings);
            if (actualPage != null) {
                base.setPendingNavigation(NavigationHistoryTree.NavigationType.BOOKMARK, current.name);
                base.jumpToPage(actualPage.index.viewIndex, current.offsetX, current.offsetY,
                        AppSettings.current().storeGotoHistory);
                dismiss();
            }
            return;
        }
        final EditText text = (EditText) findViewById(R.id.pageNumberTextEdit);
        final int pageNumber = getEnteredPageIndex(text);
        final int pageCount = base.getDocumentModel().getPageCount();
        if (pageNumber < 0 || pageNumber >= pageCount) {
            final int lo = useFilePage ? 1 : offset;
            final int hi = useFilePage ? pageCount : (pageCount - 1 + offset);
            final String msg = base.getContext().getString(R.string.bookmark_invalid_page, lo, hi);
            Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
            return;
        }
        base.setPendingNavigation(NavigationHistoryTree.NavigationType.GOTO, null);
        base.jumpToPage(pageNumber, 0, 0, AppSettings.current().storeGotoHistory);
        dismiss();
    }

    @ActionMethod(ids = R.id.actions_setBookmarkedPage)
    public void updateControls(final ActionEx action) {
        final View view = action.getParameter(IActionController.VIEW_PROPERTY);
        final Bookmark bookmark = (Bookmark) view.getTag();
        final Page actualPage = bookmark.page.getActualPage(base.getDocumentModel(), adapter.bookSettings);
        if (actualPage != null) {
            updateControls(actualPage.index.viewIndex, true);
        }
        current = bookmark;
    }

    @ActionMethod(ids = R.id.actions_showDeleteBookmarkDlg)
    public void showDeleteBookmarkDlg(final ActionEx action) {
        final View view = action.getParameter(IActionController.VIEW_PROPERTY);
        final Bookmark bookmark = view != null ? (Bookmark) view.getTag() : null;
        if (bookmark.service) {
            return;
        }

        final ActionDialogBuilder builder = new ActionDialogBuilder(getContext(), actions);
        builder.setTitle(R.string.del_bookmark_title);
        builder.setMessage(R.string.del_bookmark_text);
        builder.setPositiveButton(R.id.actions_removeBookmark, new Constant("bookmark", bookmark));
        builder.setNegativeButton().show();
    }

    @ActionMethod(ids = R.id.actions_removeBookmark)
    public void removeBookmark(final ActionEx action) {
        final Bookmark bookmark = action.getParameter("bookmark");
        adapter.remove(bookmark);
    }

    @ActionMethod(ids = R.id.bookmark_add)
    public void showAddBookmarkDlg(final ActionEx action) {
        final Context context = getContext();
        final View view = action.getParameter(IActionController.VIEW_PROPERTY);
        final Bookmark bookmark = (Bookmark) view.getTag();

        final EditText input = (EditText) LayoutInflater.from(getContext()).inflate(R.layout.bookmark_edit, null);
        final ActionDialogBuilder builder = new ActionDialogBuilder(getContext(), actions);
        builder.setMessage(R.string.add_bookmark_name);
        builder.setView(input);

        if (bookmark == null) {
            builder.setTitle(R.string.menu_add_bookmark);

            final SeekBar seekbar = (SeekBar) findViewById(R.id.seekbar);
            final int viewIndex = seekbar.getProgress();

            input.setText(context.getString(R.string.text_page) + " " + (viewIndex + offset));
            input.selectAll();

            builder.setPositiveButton(R.id.actions_addBookmark, new EditableValue("input", input), new Constant(
                    "viewIndex", viewIndex));
        } else {
            builder.setTitle(R.string.menu_edit_bookmark);

            input.setText(bookmark.name);
            input.selectAll();

            builder.setPositiveButton(R.id.actions_addBookmark, new EditableValue("input", input), new Constant(
                    "bookmark", bookmark));
        }

        builder.setNegativeButton().show();
    }

    @ActionMethod(ids = R.id.actions_addBookmark)
    public void addBookmark(final ActionEx action) {
        final Editable value = action.getParameter("input");
        final Bookmark bookmark = action.getParameter("bookmark");
        if (bookmark != null) {
            bookmark.name = value.toString();
            adapter.update(bookmark);
        } else {
            final Integer viewIndex = action.getParameter("viewIndex");
            final Page page = base.getDocumentModel().getPageObject(viewIndex);
            adapter.add(new Bookmark(value.toString(), page.index, 0, 0));
            adapter.notifyDataSetChanged();
        }
    }

    @ActionMethod(ids = { R.id.bookmark_remove_all, R.id.actions_showDeleteAllBookmarksDlg })
    public void showDeleteAllBookmarksDlg(final ActionEx action) {
        if (!adapter.hasUserBookmarks()) {
            return;
        }

        final ActionDialogBuilder builder = new ActionDialogBuilder(getContext(), actions);
        builder.setTitle(R.string.clear_bookmarks_title);
        builder.setMessage(R.string.clear_bookmarks_text);
        builder.setPositiveButton(R.id.actions_deleteAllBookmarks);
        builder.setNegativeButton().show();
    }

    @ActionMethod(ids = R.id.actions_deleteAllBookmarks)
    public void deleteAllBookmarks(final ActionEx action) {
        adapter.clear();
    }

    private void updateControls(final int viewIndex, final boolean updateBar) {
        final SeekBar seekbar = (SeekBar) findViewById(R.id.seekbar);
        final EditText editText = (EditText) findViewById(R.id.pageNumberTextEdit);

        final int displayedPage = useFilePage ? (viewIndex + 1) : (viewIndex + offset);
        editText.setText("" + displayedPage);
        editText.selectAll();

        if (updateBar) {
            seekbar.setProgress(viewIndex);
        }

        current = null;

        base.jumpToPage(viewIndex, 0, 0, false);
    }

    private int getEnteredPageIndex(final EditText text) {
        try {
            final int entered = Integer.parseInt(text.getText().toString());
            return useFilePage ? (entered - 1) : (entered - offset);
        } catch (final Exception e) {
        }
        return -1;
    }
}
