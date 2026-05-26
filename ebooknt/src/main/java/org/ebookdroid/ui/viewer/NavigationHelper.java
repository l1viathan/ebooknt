package org.ebookdroid.ui.viewer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import org.ebookdroid.common.settings.books.Bookmark;

public final class NavigationHelper {

    private NavigationHelper() {}

    public static void bringToFront(final Activity from, final Class<? extends Activity> target) {
        final Intent intent = new Intent(from, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        from.startActivity(intent);
    }

    public static void openDocument(final Activity from, final int libraryView,
            final Uri uri, final Bookmark bookmark) {
        OpenBooksManager.get().setLastLibraryView(libraryView);
        final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setClass(from, ViewerActivity.class);
        if (bookmark != null) {
            intent.putExtra("pageIndex", "" + bookmark.page.viewIndex);
            intent.putExtra("offsetX", "" + bookmark.offsetX);
            intent.putExtra("offsetY", "" + bookmark.offsetY);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        from.startActivity(intent);
    }
}
