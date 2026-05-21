package org.ebookdroid;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.emdev.BaseDroidApp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CrashLogger {

    private static final String TAG = "CrashLogger";
    private static final String CRASH_FILE = "crash_log.txt";
    private static final String PREFS_NAME = "crash_logger";
    private static final String KEY_LAST_EXIT_TS = "last_exit_ts";
    private static final int MAX_LOG_BYTES = 256 * 1024;

    private static Context sContext;

    public static void init(final Context context) {
        sContext = context.getApplicationContext();
        trimLogFile();
        collectExitInfo();
        installExceptionHandler();
        Log.i(TAG, "CrashLogger initialized, log file: " + getCrashFile().getAbsolutePath());
    }

    private static void installExceptionHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable ex) {
                writeJavaCrash(thread, ex);
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, ex);
                }
            }
        });
    }

    private static void writeJavaCrash(final Thread thread, final Throwable ex) {
        try {
            final PrintWriter pw = new PrintWriter(new FileWriter(getCrashFile(), true));
            pw.println("=== JAVA CRASH ===");
            pw.println("Time: " + formatTime(System.currentTimeMillis()));
            pw.println("Thread: " + thread.getName() + " (id=" + thread.getId() + ")");
            writeDeviceInfo(pw);
            pw.println();
            ex.printStackTrace(pw);
            pw.println();
            pw.flush();
            pw.close();
        } catch (final IOException e) {
            Log.e(TAG, "Failed to write crash log", e);
        }
    }

    private static void collectExitInfo() {
        if (Build.VERSION.SDK_INT < 30) {
            return;
        }

        final ActivityManager am = (ActivityManager)
                sContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return;
        }
        final List<ApplicationExitInfo> exits =
                am.getHistoricalProcessExitReasons(sContext.getPackageName(), 0, 10);
        if (exits == null || exits.isEmpty()) {
            return;
        }

        final SharedPreferences prefs =
                sContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        final long lastTs = prefs.getLong(KEY_LAST_EXIT_TS, 0);
        long maxTs = lastTs;

        for (final ApplicationExitInfo info : exits) {
            if (info.getTimestamp() <= lastTs) {
                continue;
            }
            final int reason = info.getReason();
            if (reason == ApplicationExitInfo.REASON_CRASH
                    || reason == ApplicationExitInfo.REASON_CRASH_NATIVE
                    || reason == ApplicationExitInfo.REASON_ANR) {
                writeExitInfo(info);
                Log.i(TAG, "Recorded " + reasonString(reason)
                        + " at " + formatTime(info.getTimestamp()));
            }
            if (info.getTimestamp() > maxTs) {
                maxTs = info.getTimestamp();
            }
        }

        if (maxTs > lastTs) {
            prefs.edit().putLong(KEY_LAST_EXIT_TS, maxTs).apply();
        }
    }

    private static void writeExitInfo(final ApplicationExitInfo info) {
        try {
            final PrintWriter pw = new PrintWriter(new FileWriter(getCrashFile(), true));
            pw.println("=== " + reasonString(info.getReason()) + " ===");
            pw.println("Time: " + formatTime(info.getTimestamp()));
            pw.println("PID: " + info.getPid());
            pw.println("Description: " + info.getDescription());
            writeDeviceInfo(pw);

            InputStream is = null;
            try {
                is = info.getTraceInputStream();
                if (is != null) {
                    pw.println("--- Trace ---");
                    final BufferedReader reader =
                            new BufferedReader(new InputStreamReader(is));
                    String line;
                    int count = 0;
                    while ((line = reader.readLine()) != null && count < 500) {
                        pw.println(line);
                        count++;
                    }
                    if (count >= 500) {
                        pw.println("... (truncated)");
                    }
                    pw.println("--- End Trace ---");
                }
            } catch (final IOException e) {
                pw.println("(trace not available: " + e.getMessage() + ")");
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (final IOException ignored) {
                    }
                }
            }
            pw.println();
            pw.flush();
            pw.close();
        } catch (final IOException e) {
            Log.e(TAG, "Failed to write exit info", e);
        }
    }

    private static void writeDeviceInfo(final PrintWriter pw) {
        pw.println("App: " + BaseDroidApp.APP_VERSION_NAME
                + " (" + BaseDroidApp.APP_VERSION_CODE + ")");
        pw.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
        pw.println("Android: " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")");
    }

    public static File getCrashFile() {
        return new File(sContext.getFilesDir(), CRASH_FILE);
    }

    public static boolean hasCrashLog() {
        if (sContext == null) {
            return false;
        }
        final File file = getCrashFile();
        return file.exists() && file.length() > 0;
    }

    public static void clearCrashLog() {
        final File file = getCrashFile();
        if (file.exists()) {
            file.delete();
        }
    }

    public static File exportToExternalStorage() {
        final File src = getCrashFile();
        if (!src.exists() || src.length() == 0) {
            return null;
        }
        final File dir = sContext.getExternalFilesDir(null);
        if (dir == null) {
            return null;
        }
        final File dst = new File(dir, CRASH_FILE);
        try {
            final FileInputStream in = new FileInputStream(src);
            final FileOutputStream out = new FileOutputStream(dst);
            final byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.flush();
            out.close();
            in.close();
            return dst;
        } catch (final IOException e) {
            Log.e(TAG, "Failed to export crash log", e);
            return null;
        }
    }

    private static void trimLogFile() {
        final File file = getCrashFile();
        if (!file.exists() || file.length() <= MAX_LOG_BYTES) {
            return;
        }
        try {
            final FileInputStream in = new FileInputStream(file);
            final byte[] all = new byte[(int) file.length()];
            in.read(all);
            in.close();

            int start = all.length - MAX_LOG_BYTES;
            while (start < all.length && all[start] != '\n') {
                start++;
            }
            if (start < all.length) {
                start++;
            }

            final FileOutputStream out = new FileOutputStream(file);
            out.write("... (earlier entries trimmed) ...\n\n".getBytes());
            out.write(all, start, all.length - start);
            out.flush();
            out.close();
        } catch (final IOException e) {
            Log.e(TAG, "Failed to trim crash log", e);
        }
    }

    private static String formatTime(final long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date(millis));
    }

    private static String reasonString(final int reason) {
        if (Build.VERSION.SDK_INT < 30) {
            return "UNKNOWN";
        }
        switch (reason) {
            case ApplicationExitInfo.REASON_CRASH:
                return "JAVA CRASH (system)";
            case ApplicationExitInfo.REASON_CRASH_NATIVE:
                return "NATIVE CRASH";
            case ApplicationExitInfo.REASON_ANR:
                return "ANR";
            default:
                return "EXIT (reason=" + reason + ")";
        }
    }
}
