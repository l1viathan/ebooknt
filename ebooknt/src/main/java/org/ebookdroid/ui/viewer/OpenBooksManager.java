package org.ebookdroid.ui.viewer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class OpenBooksManager {

    private static final OpenBooksManager INSTANCE = new OpenBooksManager();

    private final List<String> openBooks = new ArrayList<>();

    private OpenBooksManager() {}

    public static OpenBooksManager get() {
        return INSTANCE;
    }

    /** Fresh session start: clear all tracked books. */
    public static void clear() {
        synchronized (INSTANCE) {
            INSTANCE.openBooks.clear();
        }
    }

    /** Restore books from a previously saved list (Android process restore). */
    public static void restore(final List<String> saved) {
        synchronized (INSTANCE) {
            INSTANCE.openBooks.clear();
            for (final String path : saved) {
                if (path != null && !path.isEmpty() && new File(path).exists()) {
                    INSTANCE.openBooks.add(path);
                }
            }
        }
    }

    public synchronized void onBookOpened(final String path) {
        if (path == null) return;
        openBooks.remove(path);
        openBooks.add(0, path);
    }

    public synchronized void onBookClosed(final String path) {
        if (path != null) openBooks.remove(path);
    }

    public synchronized List<String> getOpenBooks() {
        return new ArrayList<>(openBooks);
    }

    public synchronized boolean isOpen(final String path) {
        return path != null && openBooks.contains(path);
    }

    public synchronized boolean isEmpty() {
        return openBooks.isEmpty();
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
