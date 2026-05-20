package org.ebookdroid.ui.library;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import org.ebooknt.viewer.R;
import org.ebookdroid.common.cache.CacheManager;
import org.ebookdroid.common.cache.ThumbnailFile;
import org.ebookdroid.ui.viewer.OpenBooksManager;
import org.ebookdroid.ui.viewer.ViewerActivity;

import java.io.File;

public final class ShortcutHelper {

    private ShortcutHelper() {}

    public static void createShortcut(final Activity activity, final String path) {
        if (path == null || path.isEmpty()) return;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(activity, R.string.bookmenu_shortcut_unsupported,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final ShortcutManager sm = activity.getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            Toast.makeText(activity, R.string.bookmenu_shortcut_unsupported,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final String title = OpenBooksManager.getDisplayTitle(path);

        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(activity, ViewerActivity.class);
        intent.setData(Uri.fromFile(new File(path)));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        Icon icon;
        final ThumbnailFile tf = CacheManager.getThumbnailFile(path);
        if (tf != null && tf.exists()) {
            final Bitmap bmp = BitmapFactory.decodeFile(tf.getPath());
            icon = bmp != null ? Icon.createWithBitmap(bmp) : Icon.createWithResource(activity, R.drawable.application_icon);
        } else {
            icon = Icon.createWithResource(activity, R.drawable.application_icon);
        }

        final ShortcutInfo info = new ShortcutInfo.Builder(activity,
                    "book_" + path.hashCode())
                .setShortLabel(title)
                .setLongLabel(title)
                .setIcon(icon)
                .setIntent(intent)
                .build();

        sm.requestPinShortcut(info, null);
    }
}
