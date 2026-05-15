package org.ebookdroid.common.notifications;

import org.sufficientlysecure.viewer.R;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;

import org.emdev.BaseDroidApp;

@TargetApi(11)
class ModernNotificationManager extends AbstractNotificationManager {

    static final String CHANNEL_ID = "ebooknt_default";

    ModernNotificationManager() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    BaseDroidApp.context.getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            getManager().createNotificationChannel(channel);
        }
    }

    @Override
    public int notify(final CharSequence title, final CharSequence message, final Intent intent) {
        final Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nb = new Notification.Builder(BaseDroidApp.context, CHANNEL_ID);
        } else {
            nb = new Notification.Builder(BaseDroidApp.context);
        }

        nb.setSmallIcon(R.drawable.application_icon);
        nb.setAutoCancel(true);
        nb.setWhen(System.currentTimeMillis());
        nb.setDefaults(Notification.DEFAULT_ALL & (~Notification.DEFAULT_VIBRATE));

        nb.setContentIntent(getIntent(intent));

        nb.setContentTitle(title);
        nb.setTicker(message);
        nb.setContentText(message);

        final Notification notification = nb.build();
        final int id = SEQ.getAndIncrement();
        getManager().notify(id, notification);

        return id;
    }
}
