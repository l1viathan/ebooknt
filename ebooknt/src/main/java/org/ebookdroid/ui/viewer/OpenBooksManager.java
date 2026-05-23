package org.ebookdroid.ui.viewer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import org.emdev.BaseDroidApp;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OpenBooksManager {

    private static final OpenBooksManager INSTANCE = new OpenBooksManager();
    private static final String PREFS_NAME = "open_books";
    private static final String KEY_PATHS = "paths";

    private final List<String> openBooks = new ArrayList<>();
    private final Set<String> activeBooks = new HashSet<>();
    private final Map<String, Integer> pageCounts = new HashMap<>();
    private boolean loaded;

    private OpenBooksManager() {}

    public static OpenBooksManager get() {
        return INSTANCE;
    }

    private static final int MAX_FILES = 6;

    public synchronized void onBookOpened(final String path) {
        if (path == null) return;
        ensureLoaded();
        openBooks.remove(path);
        openBooks.add(0, path);
        trimToMax();
        save();
    }

    public synchronized void onBookClosed(final String path) {
        if (path == null) return;
        activeBooks.remove(path);
    }

    public synchronized void onBookResumed(final String path) {
        if (path == null) return;
        activeBooks.add(path);
    }

    public synchronized void onBookPaused(final String path) {
    }

    public synchronized List<String> getOpenBooks() {
        ensureLoaded();
        return new ArrayList<>(openBooks);
    }

    public synchronized boolean isOpen(final String path) {
        ensureLoaded();
        return path != null && openBooks.contains(path);
    }

    public synchronized boolean isActive(final String path) {
        return path != null && activeBooks.contains(path);
    }

    public synchronized void removeBook(final String path) {
        if (path == null) return;
        ensureLoaded();
        openBooks.remove(path);
        save();
    }

    public synchronized void closeAll() {
        ensureLoaded();
        openBooks.clear();
        activeBooks.clear();
        save();
    }

    public synchronized boolean isEmpty() {
        ensureLoaded();
        return openBooks.isEmpty();
    }

    public synchronized void setPageCount(final String path, final int count) {
        if (path != null && count > 0) {
            pageCounts.put(path, count);
            save();
        }
    }

    public synchronized int getPageCount(final String path) {
        ensureLoaded();
        final Integer c = pageCounts.get(path);
        return c != null ? c : 0;
    }

    private void trimToMax() {
        while (openBooks.size() > MAX_FILES) {
            final String removed = openBooks.remove(openBooks.size() - 1);
            activeBooks.remove(removed);
        }
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
                final Object item = arr.get(i);
                String p;
                if (item instanceof JSONObject) {
                    final JSONObject obj = (JSONObject) item;
                    p = obj.optString("path", null);
                    final int pc = obj.optInt("pageCount", 0);
                    if (p != null && pc > 0) pageCounts.put(p, pc);
                } else {
                    p = item.toString();
                }
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
            final JSONObject obj = new JSONObject();
            try {
                obj.put("path", p);
                final Integer pc = pageCounts.get(p);
                if (pc != null) obj.put("pageCount", pc.intValue());
            } catch (final JSONException e) {
            }
            arr.put(obj);
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

    public static boolean navigateToLastOpenBook(final Activity activity) {
        final List<String> books = get().getOpenBooks();
        if (books.isEmpty()) {
            return false;
        }
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.fromFile(new File(books.get(0))));
        intent.setClass(activity, ViewerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        activity.startActivity(intent);
        return true;
    }
}
