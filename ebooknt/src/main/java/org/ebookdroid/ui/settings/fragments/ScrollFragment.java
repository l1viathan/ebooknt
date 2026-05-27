package org.ebookdroid.ui.settings.fragments;

import org.ebooknt.viewer.R;
import org.ebookdroid.ui.viewer.views.DictionaryManager;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.preference.Preference;

@TargetApi(11)
public class ScrollFragment extends BasePreferenceFragment {

    public ScrollFragment() {
        super(R.xml.fragment_scroll);
    }

    @Override
    public void decorate() {
        super.decorate();
        decorator.decorateScrollSettings();

        final Preference lookupPref = findPreference("lookup_app");
        if (lookupPref == null) return;

        updateLookupSummary(lookupPref);

        lookupPref.setOnPreferenceClickListener(p -> {
            showLookupAppDialog(p);
            return true;
        });
    }

    private void updateLookupSummary(final Preference pref) {
        final String label = DictionaryManager.getSelectedLabel(getActivity());
        if (label != null) {
            pref.setSummary(label);
        } else {
            pref.setSummary(R.string.pref_lookup_app_summary_none);
        }
    }

    private void showLookupAppDialog(final Preference pref) {
        if (getActivity() == null) return;
        final DictionaryManager.DictEntry[] all = DictionaryManager.DICTIONARIES;
        final String currentId = DictionaryManager.getSelectedId(getActivity());
        final boolean hasCurrent = currentId != null;
        final int extra = hasCurrent ? 1 : 0;
        final String[] labels = new String[all.length + extra];
        int checked = -1;
        if (hasCurrent) {
            labels[0] = getString(R.string.pref_lookup_app_clear);
        }
        for (int i = 0; i < all.length; i++) {
            labels[i + extra] = all[i].label;
            if (all[i].id.equals(currentId)) {
                checked = i + extra;
            }
        }

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.pref_lookup_app_dialog_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    if (hasCurrent && which == 0) {
                        DictionaryManager.setSelectedId(getActivity(), null);
                    } else {
                        DictionaryManager.setSelectedId(getActivity(), all[which - extra].id);
                    }
                    updateLookupSummary(pref);
                    dialog.dismiss();
                })
                .show();
    }
}
