package org.ebookdroid.ui.library.dialogs;

import org.ebookdroid.EBookDroidApp;
import org.ebooknt.viewer.R;
import org.ebookdroid.common.settings.LibSettings;
import org.ebookdroid.ui.library.adapters.BrowserAdapter;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.FileFilter;

import org.emdev.common.filesystem.DirectoryFilter;
import org.emdev.ui.actions.ActionController;
import org.emdev.ui.actions.ActionDialogBuilder;
import org.emdev.ui.actions.ActionEx;
import org.emdev.ui.actions.ActionMethod;
import org.emdev.ui.actions.IActionController;
import org.emdev.ui.actions.params.AbstractActionParameter;
import org.emdev.utils.LayoutUtils;

public class FolderDlg implements AdapterView.OnItemClickListener {

    public static final String SELECTED_FOLDER = "selected";

    public interface OnFolderSelectedListener {
        void onFolderSelected(File folder);
    }

    protected final FileFilter filter;
    private BrowserAdapter adapter;

    private TextView header;
    private ListView filesView;

    private final IActionController<FolderDlg> controller;
    private final Context context;

    private File selected;
    private ImageView homeButton;

    public FolderDlg(final IActionController<? extends Activity> controller) {
        this.filter = DirectoryFilter.NOT_HIDDEN;
        this.context = controller.getManagedComponent();
        this.controller = new ActionController<FolderDlg>(controller, this);
    }

    public FolderDlg(final Context context) {
        this.filter = DirectoryFilter.NOT_HIDDEN;
        this.context = context;
        this.controller = new ActionController<FolderDlg>(this);
    }

    public void show(final File file, int titleId, final int okActionId) {
        final View view = LayoutInflater.from(context).inflate(R.layout.folder_dialog, null);

        adapter = new BrowserAdapter(context, filter);

        header = (TextView) view.findViewById(R.id.browsertext);
        filesView = (ListView) view.findViewById(R.id.browserview);
        homeButton = (ImageView) view.findViewById(R.id.browserhome);

        homeButton.setOnClickListener(controller.getOrCreateAction(R.id.browserhome));

        filesView.setAdapter(adapter);
        filesView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_LOW);
        filesView.setOnItemClickListener(this);

        final ActionDialogBuilder builder = new ActionDialogBuilder(context, controller);

        builder.setTitle(titleId);
        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok, okActionId, new SelectedFolder());
        builder.setNegativeButton();

        goHome(null);

        AlertDialog dlg = builder.show();
        LayoutUtils.maximizeWindow(dlg.getWindow());
    }

    public void show(final File file, int titleId, final int okActionId, final int cancelActionId) {
        final View view = LayoutInflater.from(context).inflate(R.layout.folder_dialog, null);

        adapter = new BrowserAdapter(context, filter);

        header = (TextView) view.findViewById(R.id.browsertext);
        filesView = (ListView) view.findViewById(R.id.browserview);
        homeButton = (ImageView) view.findViewById(R.id.browserhome);

        homeButton.setOnClickListener(controller.getOrCreateAction(R.id.browserhome));

        filesView.setAdapter(adapter);
        filesView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_LOW);
        filesView.setOnItemClickListener(this);

        final ActionDialogBuilder builder = new ActionDialogBuilder(context, controller);

        builder.setTitle(titleId);
        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok, okActionId, new SelectedFolder());
        builder.setNegativeButton(android.R.string.cancel, cancelActionId);

        goHome(null);

        AlertDialog dlg = builder.show();
        LayoutUtils.maximizeWindow(dlg.getWindow());
    }

    public void show(int titleId, final OnFolderSelectedListener listener) {
        final View view = LayoutInflater.from(context).inflate(R.layout.folder_dialog, null);

        adapter = new BrowserAdapter(context, filter);

        header = (TextView) view.findViewById(R.id.browsertext);
        filesView = (ListView) view.findViewById(R.id.browserview);
        homeButton = (ImageView) view.findViewById(R.id.browserhome);

        homeButton.setOnClickListener(controller.getOrCreateAction(R.id.browserhome));

        filesView.setAdapter(adapter);
        filesView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_LOW);
        filesView.setOnItemClickListener(this);

        goHome(null);

        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(context)
            .setTitle(titleId)
            .setView(view)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (selected != null && listener != null) {
                        listener.onFolderSelected(selected);
                    }
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
        LayoutUtils.maximizeWindow(dlg.getWindow());
    }

    @ActionMethod(ids = R.id.browserhome)
    public void goHome(final ActionEx action) {
        final String libPath = LibSettings.current().libraryPath;
        if (libPath != null && !libPath.isEmpty()) {
            setCurrentDir(new File(libPath));
        } else if (EBookDroidApp.EXT_STORAGE != null && EBookDroidApp.EXT_STORAGE.exists()) {
            setCurrentDir(EBookDroidApp.EXT_STORAGE);
        } else {
            setCurrentDir(new File("/"));
        }
    }

    public void setCurrentDir(final File newDir) {
        selected = newDir;
        header.setText(newDir.getAbsolutePath());
        adapter.setCurrentDirectory(newDir);
    }

    @Override
    public void onItemClick(final AdapterView<?> adapterView, final View view, final int i, final long l) {
        final File selected = adapter.getItem(i);
        if (selected.isDirectory()) {
            setCurrentDir(selected);
        }
    }

    private class SelectedFolder extends AbstractActionParameter {

        public SelectedFolder() {
            super(SELECTED_FOLDER);
        }

        @Override
        public Object getValue() {
            return selected;
        }

    }
}
