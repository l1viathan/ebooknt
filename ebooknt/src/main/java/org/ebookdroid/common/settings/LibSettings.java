package org.ebookdroid.common.settings;

import org.ebookdroid.common.settings.definitions.LibPreferences;
import org.ebookdroid.common.settings.listeners.ILibSettingsChangeListener;
import org.ebookdroid.common.settings.types.CacheLocation;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import org.emdev.common.backup.BackupManager;
import org.emdev.common.backup.IBackupAgent;
import org.emdev.common.filesystem.FileExtensionFilter;
import org.emdev.common.settings.backup.SettingsBackupHelper;
import org.emdev.utils.CompareUtils;
import org.json.JSONObject;

public class LibSettings implements LibPreferences, IBackupAgent {

    public static final String BACKUP_KEY = "lib-settings";

    private static LibSettings current;

    /* =============== Browser settings =============== */

    public final boolean useBookcase;

    public final String libraryPath;

    public final String searchBookQuery;

    public final FileExtensionFilter allowedFileTypes;

    public final CacheLocation cacheLocation;

    private LibSettings() {
        BackupManager.addAgent(this);
        final SharedPreferences prefs = SettingsManager.prefs;
        /* =============== Browser settings =============== */
        useBookcase = USE_BOOK_CASE.getPreferenceValue(prefs);
        libraryPath = migrateLibraryPath(LIBRARY_PATH.getPreferenceValue(prefs), prefs);
        searchBookQuery = SEARCH_BOOK_QUERY.getPreferenceValue(prefs);
        allowedFileTypes = FILE_TYPE_FILTER.getFilter(prefs);
        cacheLocation  = CACHE_LOCATION.getPreferenceValue(prefs);
    }

    private static String migrateLibraryPath(final String raw, final SharedPreferences prefs) {
        if (raw == null || !raw.contains(":")) {
            return raw;
        }
        final String first = raw.substring(0, raw.indexOf(':'));
        final Editor edit = prefs.edit();
        LIBRARY_PATH.setPreferenceValue(edit, first);
        edit.commit();
        return first;
    }

    /* =============== */

    public static void init() {
        current = new LibSettings();
    }

    public static LibSettings current() {
        SettingsManager.lock.readLock().lock();
        try {
            return current;
        } finally {
            SettingsManager.lock.readLock().unlock();
        }
    }

    public static void setLibraryPath(final String dir) {
        SettingsManager.lock.writeLock().lock();
        try {
            if (!dir.equals(current.libraryPath)) {
                final Editor edit = SettingsManager.prefs.edit();
                LibPreferences.LIBRARY_PATH.setPreferenceValue(edit, dir);
                edit.commit();
                final LibSettings oldSettings = current;
                current = new LibSettings();
                applySettingsChanges(oldSettings, current);
            }
        } finally {
            SettingsManager.lock.writeLock().unlock();
        }
    }

    public static void updateSearchBookQuery(final String searchQuery) {
        SettingsManager.lock.writeLock().lock();
        try {
            final Editor edit = SettingsManager.prefs.edit();
            LibPreferences.SEARCH_BOOK_QUERY.setPreferenceValue(edit, searchQuery);
            edit.commit();
            final LibSettings oldSettings = current;
            current = new LibSettings();
            applySettingsChanges(oldSettings, current);
        } finally {
            SettingsManager.lock.writeLock().unlock();
        }
    }

    static Diff onSettingsChanged() {
        final LibSettings oldLibSettings = current;
        current = new LibSettings();
        return applySettingsChanges(oldLibSettings, current);
    }

    public static LibSettings.Diff applySettingsChanges(final LibSettings oldSettings, final LibSettings newSettings) {
        final LibSettings.Diff diff = new LibSettings.Diff(oldSettings, newSettings);
        final ILibSettingsChangeListener l = SettingsManager.listeners.getListener();
        l.onLibSettingsChanged(oldSettings, newSettings, diff);
        return diff;
    }

    @Override
    public String key() {
        return BACKUP_KEY;
    }

    @Override
    public JSONObject backup() {
        return SettingsBackupHelper.backup(BACKUP_KEY, SettingsManager.prefs, LibPreferences.class);
    }

    @Override
    public void restore(final JSONObject backup) {
        SettingsBackupHelper.restore(BACKUP_KEY, SettingsManager.prefs, LibPreferences.class, backup);
        onSettingsChanged();
    }

    public static class Diff {

        private static final int D_UseBookcase = 0x0001 << 0;
        private static final int D_ScanDirs = 0x0001 << 1;
        private static final int D_AllowedFileTypes = 0x0001 << 2;
        private static final int D_CacheLocation = 0x0001 << 3;

        private int mask;
        private final boolean firstTime;

        public Diff(final LibSettings olds, final LibSettings news) {
            firstTime = olds == null;
            if (firstTime) {
                mask = 0xFFFFFFFF;
            } else if (news != null) {
                if (olds.useBookcase != news.useBookcase) {
                    mask |= D_UseBookcase;
                }
                if (!CompareUtils.equals(olds.libraryPath, news.libraryPath)) {
                    mask |= D_ScanDirs;
                }
                if (!olds.allowedFileTypes.equals(news.allowedFileTypes)) {
                    mask |= D_AllowedFileTypes;
                }
                if (olds.cacheLocation != news.cacheLocation) {
                    mask |= D_CacheLocation;
                }
            }
        }

        public boolean isFirstTime() {
            return firstTime;
        }

        public boolean isUseBookcaseChanged() {
            return 0 != (mask & D_UseBookcase);
        }

        public boolean isScanDirsChanged() {
            return 0 != (mask & D_ScanDirs);
        }

        public boolean isAllowedFileTypesChanged() {
            return 0 != (mask & D_AllowedFileTypes);
        }

        public boolean isCacheLocationChanged() {
            return 0 != (mask & D_CacheLocation);
        }
    }
}
