package org.ebookdroid.ui.viewer.views;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

public class DictionaryManager {

    private static final String PREF_FILE = "ebooknt_lookup";
    private static final String PREF_KEY = "lookup_dictionary";

    public static final DictEntry[] DICTIONARIES = {
            new DictEntry("Fora Dictionary", "com.ngc.fora"),
            new DictEntry("ColorDict", "com.socialnmobile.colordict"),
            new DictEntry("GoldenDict", "mobi.goldendict.android"),
            new DictEntry("Google Translate", "com.google.android.apps.translate"),
            new DictEntry("Dictan", "info.softex.dictan"),
            new DictEntry("MDict", "cn.mdict"),
            new DictEntry("Yandex Translate", "ru.yandex.translate"),
            new DictEntry("Aard", "aarddict.android"),
            new DictEntry("Aard 2", "itkach.aard2"),
            new DictEntry("Livio English dictionary", "livio.pack.lang.en_US"),
            new DictEntry("Livio French dictionary", "livio.pack.lang.fr_FR"),
            new DictEntry("Livio Italian dictionary", "livio.pack.lang.it_IT"),
            new DictEntry("Livio Spain dictionary", "livio.pack.lang.es_ES"),
            new DictEntry("Livio German dictionary", "livio.pack.lang.de_DE"),
    };

    public static String getSelectedId(final Context ctx) {
        return ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .getString(PREF_KEY, null);
    }

    public static void setSelectedId(final Context ctx, final String id) {
        final SharedPreferences.Editor ed = ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit();
        if (id == null) {
            ed.remove(PREF_KEY);
        } else {
            ed.putString(PREF_KEY, id);
        }
        ed.apply();
    }

    public static DictEntry findById(final String id) {
        if (id == null) return null;
        for (final DictEntry e : DICTIONARIES) {
            if (e.id.equals(id)) return e;
        }
        return null;
    }

    public static String getSelectedLabel(final Context ctx) {
        final DictEntry e = findById(getSelectedId(ctx));
        return e != null ? e.label : null;
    }

    public static boolean lookup(final Context ctx, final String text) {
        final DictEntry e = findById(getSelectedId(ctx));
        if (e == null) return false;
        return e.launch(ctx, text);
    }

    public static class DictEntry {
        public final String label;
        public final String id;

        DictEntry(final String label, final String id) {
            this.label = label;
            this.id = id;
        }

        public boolean launch(final Context ctx, final String text) {
            final Intent intent = buildIntent(text);
            if (intent.resolveActivity(ctx.getPackageManager()) != null) {
                ctx.startActivity(intent);
                return true;
            }
            return false;
        }

        private Intent buildIntent(final String text) {
            switch (id) {
                case "com.google.android.apps.translate":
                    return new Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, text)
                            .setPackage(id);
                case "ru.yandex.translate":
                    return new Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, text)
                            .setPackage(id);
                case "itkach.aard2":
                    return new Intent(Intent.ACTION_SEARCH)
                            .putExtra("query", text)
                            .setPackage(id);
                default:
                    return new Intent("colordict.intent.action.SEARCH")
                            .putExtra("EXTRA_QUERY", text)
                            .putExtra("EXTRA_FULLSCREEN", false)
                            .setPackage(id);
            }
        }

        public boolean isInstalled(final Context ctx) {
            try {
                ctx.getPackageManager().getPackageInfo(id, 0);
                return true;
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }
    }
}
