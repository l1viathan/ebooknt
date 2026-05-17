package org.ebookdroid.common.settings.books;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import org.emdev.common.log.LogContext;
import org.emdev.common.log.LogManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class BookSettingsTemplateManager {

    private static final LogContext LCTX = LogManager.root().lctx("BookSettingsTemplateManager");
    private static final String PREF_KEY = "book_setting_templates";

    private final SharedPreferences prefs;

    public BookSettingsTemplateManager(final SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public List<BookSettingsTemplate> getAll() {
        final List<BookSettingsTemplate> result = new ArrayList<>();
        final String json = prefs.getString(PREF_KEY, "[]");
        try {
            final JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                final JSONObject obj = arr.optJSONObject(i);
                if (obj != null) {
                    result.add(new BookSettingsTemplate(obj));
                }
            }
        } catch (final JSONException e) {
            LCTX.e("Error loading templates: " + e.getMessage());
        }
        return result;
    }

    public void save(final BookSettingsTemplate template) {
        final List<BookSettingsTemplate> list = getAll();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equals(template.name)) {
                list.set(i, template);
                persist(list);
                return;
            }
        }
        list.add(template);
        persist(list);
    }

    public void delete(final String name) {
        final List<BookSettingsTemplate> list = getAll();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equals(name)) {
                list.remove(i);
                persist(list);
                return;
            }
        }
    }

    private void persist(final List<BookSettingsTemplate> list) {
        final JSONArray arr = new JSONArray();
        for (final BookSettingsTemplate t : list) {
            try {
                arr.put(t.toJSON());
            } catch (final JSONException e) {
                LCTX.e("Error serializing template \"" + t.name + "\": " + e.getMessage());
            }
        }
        prefs.edit().putString(PREF_KEY, arr.toString()).apply();
    }
}
