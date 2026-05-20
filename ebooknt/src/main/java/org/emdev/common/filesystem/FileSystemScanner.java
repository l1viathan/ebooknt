package org.emdev.common.filesystem;

import android.app.Activity;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.emdev.common.cache.CacheManager;
import org.emdev.common.log.LogContext;
import org.emdev.common.log.LogManager;
import org.emdev.ui.actions.EventDispatcher;
import org.emdev.ui.actions.InvokationType;
import org.emdev.ui.tasks.AsyncTask;
import org.emdev.utils.LengthUtils;
import org.emdev.utils.StringUtils;

public class FileSystemScanner {

    private static final LogContext LCTX = LogManager.root().lctx("FileSystemScanner", false);

    final EventDispatcher listeners;
    final AtomicBoolean inScan = new AtomicBoolean();

    private ScanTask m_scanTask;

    public FileSystemScanner(final Activity activity) {
        this.listeners = new EventDispatcher(activity, InvokationType.AsyncUI, Listener.class, ProgressListener.class);
    }

    public void shutdown() {
        stopScan();
    }

    public void startScan(final FileExtensionFilter filter, final String... paths) {
        if (inScan.compareAndSet(false, true)) {
            m_scanTask = new ScanTask(filter);
            m_scanTask.execute(paths);
        } else {
            m_scanTask.addPaths(paths);
        }
    }

    public void startScan(final FileExtensionFilter filter, final Collection<String> paths) {
        final String[] arr = paths.toArray(new String[paths.size()]);
        if (inScan.compareAndSet(false, true)) {
            m_scanTask = new ScanTask(filter);
            m_scanTask.execute(arr);
        } else {
            m_scanTask.addPaths(arr);
        }
    }

    public boolean isScan() {
        return inScan.get();
    }

    public void stopScan() {
        if (inScan.compareAndSet(true, false)) {
            m_scanTask = null;
        }
    }

    public void addListener(final Object listener) {
        listeners.addListener(listener);
    }

    public void removeListener(final Object listener) {
        listeners.removeListener(listener);
    }

    class ScanTask extends AsyncTask<String, String, Void> {

        final FileExtensionFilter filter;

        final LinkedList<File> paths = new LinkedList<File>();

        public ScanTask(final FileExtensionFilter filter) {
            this.filter = filter;
        }

        @Override
        protected void onPreExecute() {
            final ProgressListener pl = listeners.getListener();
            pl.showProgress(true);
        }

        @Override
        protected Void doInBackground(final String... paths) {
            addPaths(paths);

            try {
                for (File dir = getDir(); dir != null && inScan.get(); dir = getDir()) {
                    scanDir(dir);
                }
            } finally {
                inScan.set(false);
            }
            return null;
        }

        @Override
        protected void onPostExecute(final Void v) {
            final ProgressListener pl = listeners.getListener();
            pl.showProgress(false);
        }

        void scanDir(final File dir) {
            if (!inScan.get()) {
                return;
            }

            if (dir == null || !dir.isDirectory()) {
                return;
            }

            if (dir.getAbsolutePath().startsWith("/sys")) {
                LCTX.d("Skip system dir: " + dir);
                return;
            }

            try {
                final File cd = CacheManager.getCacheDir();
                if (cd != null && dir.getCanonicalPath().equals(cd.getCanonicalPath())) {
                    LCTX.d("Skip file cache: " + dir);
                    return;
                }
            } catch (final IOException ex) {
                ex.printStackTrace();
            }

            if (LCTX.isDebugEnabled()) {
                LCTX.d("Scan dir: " + dir);
            }

            final Listener l = listeners.getListener();

            final File[] files = dir.listFiles((FilenameFilter) filter);
            if (LengthUtils.isNotEmpty(files)) {
                Arrays.sort(files, StringUtils.NFC);
            }
            l.onFileScan(dir, files);

            final File[] childDirs = dir.listFiles(DirectoryFilter.ALL);

            if (LengthUtils.isNotEmpty(childDirs)) {
                Arrays.sort(childDirs, StringUtils.NFC);
                synchronized (this) {
                    for (int i = childDirs.length - 1; i >= 0; i--) {
                        this.paths.addFirst(childDirs[i]);
                    }
                }
            }
        }

        synchronized void addPaths(final String... paths) {
            for (final String path : paths) {
                final File dir = new File(path);
                if (dir.exists() && dir.isDirectory()) {
                    this.paths.add(dir);
                }
            }
        }

        synchronized File getDir() {
            return this.paths.isEmpty() ? null : this.paths.removeFirst();
        }
    }

    public static interface Listener {

        void onFileScan(File parent, File[] files);

        void onFileAdded(File parent, File f);

        void onFileDeleted(File parent, File f);

        void onDirAdded(File parent, File f);

        void onDirDeleted(File parent, File f);
    }

    public static interface ProgressListener {

        public void showProgress(boolean show);

    }
}
