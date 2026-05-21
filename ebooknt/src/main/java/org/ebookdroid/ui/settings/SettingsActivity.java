package org.ebookdroid.ui.settings;

import org.ebookdroid.CrashLogger;
import org.ebookdroid.EBookDroidApp;
import org.ebooknt.viewer.BuildConfig;
import org.ebooknt.viewer.R;
import org.ebookdroid.common.settings.AppSettings;
import org.ebookdroid.common.settings.SettingsManager;
import org.ebookdroid.common.settings.books.BookSettings;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.widget.Toast;

import java.io.File;

import org.emdev.common.filesystem.PathFromUri;

public class SettingsActivity extends BaseSettingsActivity {

    @Override
    protected final void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EBookDroidApp.initFonts();

        final Uri uri = getIntent().getData();
        if (uri != null) {
            final String fileName = PathFromUri.retrieve(getContentResolver(), uri);
            BookSettings current = SettingsManager.getBookSettings(fileName);
            if (current != null) {
                setRequestedOrientation(current.getOrientation(AppSettings.current()));
            }
        }

        onCreate();
    }

    @Override
    protected void onPause() {
        SettingsManager.onSettingsChanged();
        super.onPause();
    }

    @SuppressWarnings("deprecation")
    protected void onCreate() {
        try {
            setPreferenceScreen(createPreferences());
        } catch (final ClassCastException e) {
            LCTX.e("Shared preferences are corrupt! Resetting to default values.");

            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            final SharedPreferences.Editor editor = preferences.edit();
            editor.clear();
            editor.commit();

            setPreferenceScreen(createPreferences());
        }

        decorator.decorateSettings();
    }

    @SuppressWarnings("deprecation")
    PreferenceScreen createPreferences() {
        final PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);

        root.setTitle(R.string.menu_settings);

        loadPreferences(root, R.xml.fragment_ui);
        loadPreferences(root, R.xml.fragment_scroll);
        loadPreferences(root, R.xml.fragment_navigation);
        loadPreferences(root, R.xml.fragment_performance);
        loadPreferences(root, R.xml.fragment_render);
        loadPreferences(root, R.xml.fragment_typespec);
        loadPreferences(root, R.xml.fragment_browser);

        loadPreferences(root, R.xml.fragment_backup);

        if (BuildConfig.DEBUG) {
            addCrashLogPreferences(root);
        }

        return root;
    }

    @SuppressWarnings("deprecation")
    private void addCrashLogPreferences(final PreferenceScreen root) {
        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(this);
        screen.setTitle("Crash Log (Debug)");
        updateCrashLogSummary(screen);

        final Preference exportPref = new Preference(this);
        exportPref.setTitle("Export crash log");
        exportPref.setSummary("Copy to app external storage");
        exportPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                final File exported = CrashLogger.exportToExternalStorage();
                if (exported != null) {
                    Toast.makeText(SettingsActivity.this,
                            "Exported to: " + exported.getAbsolutePath(),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(SettingsActivity.this,
                            "No crash log to export",
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        screen.addPreference(exportPref);

        final Preference clearPref = new Preference(this);
        clearPref.setTitle("Clear crash log");
        clearPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                CrashLogger.clearCrashLog();
                Toast.makeText(SettingsActivity.this,
                        "Crash log cleared", Toast.LENGTH_SHORT).show();
                updateCrashLogSummary(screen);
                return true;
            }
        });
        screen.addPreference(clearPref);

        root.addPreference(screen);
    }

    private void updateCrashLogSummary(final PreferenceScreen screen) {
        if (CrashLogger.hasCrashLog()) {
            final long bytes = CrashLogger.getCrashFile().length();
            screen.setSummary("Log size: " + (bytes / 1024) + " KB");
        } else {
            screen.setSummary("No crash log");
        }
    }

    @SuppressWarnings("deprecation")
    void loadPreferences(final PreferenceScreen root, final int... resourceIds) {
        for (final int id : resourceIds) {
            setPreferenceScreen(null);
            addPreferencesFromResource(id);
            root.addPreference(getPreferenceScreen());
            setPreferenceScreen(null);
        }
    }

}
