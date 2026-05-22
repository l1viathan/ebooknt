package org.ebookdroid.ui.settings;

import org.ebooknt.viewer.R;
import org.ebookdroid.common.settings.AppSettings;
import org.ebookdroid.common.settings.SettingsManager;
import org.ebookdroid.common.settings.books.BookSettings;
import org.ebookdroid.common.settings.books.BookSettingsTemplate;
import org.ebookdroid.common.settings.books.BookSettingsTemplateManager;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import java.util.List;

import org.emdev.common.filesystem.PathFromUri;

public class BookSettingsActivity extends BaseSettingsActivity {

    private BookSettings current;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String fileName = getIntent().getStringExtra("bookFileName");
        if (fileName == null) {
            finish();
            return;
        }
        current = SettingsManager.getBookSettings(fileName);
        if (current == null) {
            finish();
            return;
        }

        setRequestedOrientation(current.getOrientation(AppSettings.current()));

        SettingsManager.onBookSettingsActivityCreated(current);

        try {
            addPreferencesFromResource(R.xml.fragment_book);
        } catch (final ClassCastException e) {
            LCTX.e("Book preferences are corrupt! Resetting to default values.");

            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            final SharedPreferences.Editor editor = preferences.edit();
            editor.clear();
            editor.commit();

            PreferenceManager.setDefaultValues(this, R.xml.fragment_book, true);
            addPreferencesFromResource(R.xml.fragment_book);
        }

        decorator.decoratePreference(getRoot());
        decorator.decorateBooksSettings(current);

        wireTemplatePreferences();
    }

    private void wireTemplatePreferences() {
        final Preference applyPref = findPreference("pref_template_apply");
        if (applyPref != null) {
            applyPref.setOnPreferenceClickListener(pref -> {
                showApplyTemplateDialog();
                return true;
            });
        }
        final Preference savePref = findPreference("pref_template_save");
        if (savePref != null) {
            savePref.setOnPreferenceClickListener(pref -> {
                showSaveTemplateDialog();
                return true;
            });
        }
    }

    private void showApplyTemplateDialog() {
        final BookSettingsTemplateManager mgr = SettingsManager.getTemplates();
        final List<BookSettingsTemplate> templates = mgr.getAll();

        final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.pref_template_apply_title)
                .setNegativeButton(android.R.string.cancel, null);

        if (templates.isEmpty()) {
            builder.setMessage(R.string.pref_template_none).show();
            return;
        }

        final CharSequence[] names = new CharSequence[templates.size()];
        for (int i = 0; i < templates.size(); i++) {
            names[i] = templates.get(i).name;
        }

        builder.setItems(names, (dlg, which) -> {
            SettingsManager.prepareTemplateApply(templates.get(which), current);
            finish();
        });

        final AlertDialog dialog = builder.show();

        dialog.getListView().setOnItemLongClickListener((parent, view, pos, id) -> {
            final String tplName = templates.get(pos).name;
            new AlertDialog.Builder(this)
                    .setTitle(tplName)
                    .setMessage(R.string.pref_template_delete_confirm)
                    .setPositiveButton(android.R.string.yes, (d2, w2) -> mgr.delete(tplName))
                    .setNegativeButton(android.R.string.no, null)
                    .show();
            dialog.dismiss();
            return true;
        });
    }

    private void showSaveTemplateDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(R.string.pref_template_name_hint);

        new AlertDialog.Builder(this)
                .setTitle(R.string.pref_template_save_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dlg, which) -> {
                    final String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        SettingsManager.getTemplates().save(new BookSettingsTemplate(name, current));
                        Toast.makeText(this,
                                getString(R.string.pref_template_saved, name),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    protected void onPause() {
        SettingsManager.onBookSettingsActivityClosed(current);
        super.onPause();
    }
}
