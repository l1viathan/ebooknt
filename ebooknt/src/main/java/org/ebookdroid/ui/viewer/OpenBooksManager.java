package org.ebookdroid.ui.viewer;

import android.content.SharedPreferences;

import org.emdev.BaseDroidApp;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class OpenBooksManager {

    private static final OpenBooksManager INSTANCE = new OpenBooksManager();
    private static final String PREFS_NAME = "open_books";
    private static final String KEY_PATHS = "paths";

    private final List<String> openBooks = new ArrayList<>();
    private boolean loaded;

    private OpenBooksManager() {}

    public static OpenBooksManager get() {
        return INSTANCE;
    }

    public synchronized void onBookOpened(final String path) {
        if (path == null) return;
        ensureLoaded();
        openBooks.remove(path);
        openBooks.add(0, path);
        save();
    }

    public synchronized void onBookClosed(final String path) {
        if (path == null) return;
        ensureLoaded();
        openBooks.remove(path);
        save();
    }

    public synchronized List<String> getOpenBooks() {
        ensureLoaded();
        return new ArrayList<>(openBooks);
    }

    public synchronized boolean isOpen(final String path) {
        ensureLoaded();
        return path != null && openBooks.contains(path);
    }

    public synchronized boolean isEmpty() {
        ensureLoaded();
        return openBooks.isEmpty();
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (BaseDroidApp.context == null) return;
        final SharedPreferences prefs =
                BaseDroidApp.context.getSharedPreferences(PREFS_NAME, 0);
        final String json = prefs.getString(KEY_PATHS, null);
        if (json == null) return;
        try {
            final JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                final String p = arr.getString(i);
                if (p != null && !p.isEmpty() && new File(p).exists()) {
                    openBooks.add(p);
                }
            }
        } catch (final JSONException e) {
        }
    }

    private void save() {
        if (BaseDroidApp.context == null) return;
        final JSONArray arr = new JSONArray();
        for (final String p : openBooks) {
            arr.put(p);
        }
        BaseDroidApp.context.getSharedPreferences(PREFS_NAME, 0)
                .edit().putString(KEY_PATHS, arr.toString()).apply();
    }

    public static String getDisplayTitle(final String path) {
        if (path == null) return "";
        final String name = new File(path).getName();
        final int dot = name.lastIndexOf('.');
        String title = dot > 0 ? name.substring(0, dot) : name;
        title = title.replaceAll("\\[.*?\\]", "").replaceAll("\\(.*?\\)", "");
        title = title.trim().replaceAll("\\s{2,}", " ");
        return title;
    }
}
