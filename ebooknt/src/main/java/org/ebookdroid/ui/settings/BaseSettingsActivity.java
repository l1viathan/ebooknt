package org.ebookdroid.ui.settings;


import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;

import org.ebookdroid.common.settings.AppSettings;
import org.ebooknt.viewer.R;
import org.emdev.BaseDroidApp;
import org.emdev.common.log.LogContext;
import org.emdev.common.log.LogManager;

public class BaseSettingsActivity extends PreferenceActivity implements IPreferenceContainer {

    public static final LogContext LCTX = LogManager.root().lctx("Settings");

    @Override
    protected void attachBaseContext(final Context newBase) {
        super.attachBaseContext(BaseDroidApp.wrapContext(newBase));
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        if (AppSettings.current().einkMode) {
            setTheme(R.style.ebookdroid_prefs_eink);
        }
        super.onCreate(savedInstanceState);
    }

    protected final PreferencesDecorator decorator = new PreferencesDecorator(this);

    @Override
    public Preference getRoot() {
        return this.getPreferenceScreen();
    }

    @Override
    public Activity getActivity() {
        return this;
    }
}
