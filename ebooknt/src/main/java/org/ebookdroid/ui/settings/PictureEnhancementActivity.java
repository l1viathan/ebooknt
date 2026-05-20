package org.ebookdroid.ui.settings;

import static org.ebookdroid.common.settings.definitions.BookPreferences.*;

import org.ebooknt.viewer.R;
import org.ebookdroid.common.bitmaps.ByteBufferBitmap;
import org.ebookdroid.common.bitmaps.ByteBufferManager;
import org.ebookdroid.common.settings.AppSettings;
import org.ebookdroid.common.settings.books.BookSettings;
import org.ebookdroid.core.DecodeService;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.CompoundButton;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import org.emdev.BaseDroidApp;

import java.util.Locale;

public class PictureEnhancementActivity extends Activity {

    @Override
    protected void attachBaseContext(final Context newBase) {
        super.attachBaseContext(BaseDroidApp.wrapContext(newBase));
    }

    private static final String TAG = "PicEnhance";
    private static final int CONTRAST_MIN = -255;
    private static final int GAMMA_MIN = 0;
    private static final int EXPOSURE_MIN = -128;
    private static final int THRESHOLD_MIN = 0;
    private static final int SMOOTHNESS_MIN = 100;

    private ImageView previewImage;
    private SeekBar seekContrast, seekGamma, seekExposure, seekThreshold, seekSmoothness;
    private Switch switchAutolevels;
    private TextView labelContrast, labelGamma, labelExposure, labelThreshold, labelSmoothness;

    private Bitmap rawThumbnail;

    private int contrast, gamma, exposure, threshold, smoothness;
    private boolean autoLevels;

    private int initContrast, initGamma, initExposure, initThreshold, initSmoothness;
    private boolean initAutoLevels;

    private final Handler handler = new Handler();
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            renderPreview();
        }
    };

    private Bitmap currentBitmap;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Log.d(TAG, "onCreate start");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picture_enhancement);
        Log.d(TAG, "setContentView done");

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        AppSettings.migrateSmoothness(prefs, BOOK_SMOOTHNESS.key);
        contrast = BOOK_CONTRAST.getPreferenceValue(prefs, 0);
        gamma = BOOK_GAMMA.getPreferenceValue(prefs, 100);
        exposure = BOOK_EXPOSURE.getPreferenceValue(prefs, 0);
        autoLevels = BOOK_AUTO_LEVELS.getPreferenceValue(prefs, false);
        threshold = BOOK_THRESHOLD.getPreferenceValue(prefs, 0);
        smoothness = BOOK_SMOOTHNESS.getPreferenceValue(prefs, 100);

        initContrast = contrast;
        initGamma = gamma;
        initExposure = exposure;
        initAutoLevels = autoLevels;
        initThreshold = threshold;
        initSmoothness = smoothness;

        previewImage = (ImageView) findViewById(R.id.preview_image);
        seekContrast = (SeekBar) findViewById(R.id.seek_contrast);
        seekGamma = (SeekBar) findViewById(R.id.seek_gamma);
        seekExposure = (SeekBar) findViewById(R.id.seek_exposure);
        seekThreshold = (SeekBar) findViewById(R.id.seek_threshold);
        seekSmoothness = (SeekBar) findViewById(R.id.seek_smoothness);
        switchAutolevels = (Switch) findViewById(R.id.switch_autolevels);
        labelContrast = (TextView) findViewById(R.id.label_contrast);
        labelGamma = (TextView) findViewById(R.id.label_gamma);
        labelExposure = (TextView) findViewById(R.id.label_exposure);
        labelThreshold = (TextView) findViewById(R.id.label_threshold);
        labelSmoothness = (TextView) findViewById(R.id.label_smoothness);

        seekContrast.setProgress(contrast - CONTRAST_MIN);
        seekGamma.setProgress(gamma - GAMMA_MIN);
        seekExposure.setProgress(exposure - EXPOSURE_MIN);
        seekThreshold.setProgress(threshold - THRESHOLD_MIN);
        seekSmoothness.setProgress(smoothness - SMOOTHNESS_MIN);
        switchAutolevels.setChecked(autoLevels);

        updateLabels();

        seekContrast.setOnSeekBarChangeListener(new SliderListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                contrast = progress + CONTRAST_MIN;
                labelContrast.setText(String.format(Locale.US, "%.2f", contrast / 100.0));
                schedulePreviewUpdate();
            }
        });

        seekGamma.setOnSeekBarChangeListener(new SliderListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                gamma = progress + GAMMA_MIN;
                labelGamma.setText(String.format(Locale.US, "%.2f", gamma / 100.0));
                schedulePreviewUpdate();
            }
        });

        seekExposure.setOnSeekBarChangeListener(new SliderListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                exposure = progress + EXPOSURE_MIN;
                labelExposure.setText(String.format(Locale.US, "%.2f", exposure / 100.0));
                schedulePreviewUpdate();
            }
        });

        seekThreshold.setOnSeekBarChangeListener(new SliderListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                threshold = progress + THRESHOLD_MIN;
                labelThreshold.setText(String.format(Locale.US, "%.2f", threshold / 100.0));
                schedulePreviewUpdate();
            }
        });

        seekSmoothness.setOnSeekBarChangeListener(new SliderListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                smoothness = progress + SMOOTHNESS_MIN;
                labelSmoothness.setText(String.format(Locale.US, "%.2f", smoothness / 100.0));
                schedulePreviewUpdate();
            }
        });

        switchAutolevels.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                autoLevels = isChecked;
                schedulePreviewUpdate();
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViewById(R.id.btn_reset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetToSaved();
            }
        });

        findViewById(R.id.btn_default).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetToDefaults();
            }
        });

        Log.d(TAG, "calling loadRawThumbnail");
        loadRawThumbnail();
        Log.d(TAG, "onCreate done");
    }

    private void loadRawThumbnail() {
        final DecodeService ds = PictureEnhancementContext.getDecodeService();
        final int pageNo = PictureEnhancementContext.getPageNo();
        Log.d(TAG, "loadRawThumbnail: ds=" + ds + " pageNo=" + pageNo);
        if (ds == null) {
            Log.w(TAG, "DecodeService is null, no preview");
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "background: calling createPageThumbnail");
                    final RectF fullPage = new RectF(0, 0, 1, 1);
                    final ByteBufferBitmap raw = ds.createPageThumbnail(480, 720, pageNo, fullPage);
                    Log.d(TAG, "background: createPageThumbnail returned " + raw);
                    if (raw == null) {
                        return;
                    }
                    raw.getPixels().rewind();
                    final Bitmap bmp = Bitmap.createBitmap(raw.getWidth(), raw.getHeight(), Bitmap.Config.ARGB_8888);
                    bmp.copyPixelsFromBuffer(raw.getPixels());
                    ByteBufferManager.release(raw);
                    Log.d(TAG, "background: thumbnail ready " + bmp.getWidth() + "x" + bmp.getHeight());

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            rawThumbnail = bmp;
                            renderPreview();
                        }
                    });
                } catch (final Throwable t) {
                    Log.e(TAG, "background: failed to create thumbnail", t);
                }
            }
        }).start();
    }

    private void updateLabels() {
        labelContrast.setText(String.format(Locale.US, "%.2f", contrast / 100.0));
        labelGamma.setText(String.format(Locale.US, "%.2f", gamma / 100.0));
        labelExposure.setText(String.format(Locale.US, "%.2f", exposure / 100.0));
        labelThreshold.setText(String.format(Locale.US, "%.2f", threshold / 100.0));
        labelSmoothness.setText(String.format(Locale.US, "%.2f", smoothness / 100.0));
    }

    private void applyValues(int c, int g, int e, boolean al, int t, int s) {
        contrast = c;
        gamma = g;
        exposure = e;
        autoLevels = al;
        threshold = t;
        smoothness = s;

        seekContrast.setProgress(contrast - CONTRAST_MIN);
        seekGamma.setProgress(gamma - GAMMA_MIN);
        seekExposure.setProgress(exposure - EXPOSURE_MIN);
        seekThreshold.setProgress(threshold - THRESHOLD_MIN);
        seekSmoothness.setProgress(smoothness - SMOOTHNESS_MIN);
        switchAutolevels.setChecked(autoLevels);

        updateLabels();
        schedulePreviewUpdate();
    }

    private void resetToDefaults() {
        applyValues(0, 100, 0, false, 0, 100);
    }

    private void resetToSaved() {
        applyValues(initContrast, initGamma, initExposure, initAutoLevels, initThreshold, initSmoothness);
    }

    private void schedulePreviewUpdate() {
        handler.removeCallbacks(updateRunnable);
        handler.postDelayed(updateRunnable, 150);
    }

    private void renderPreview() {
        if (rawThumbnail == null || rawThumbnail.isRecycled()) {
            return;
        }

        final int c = contrast;
        final int g = gamma;
        final int e = exposure;
        final boolean al = autoLevels;
        final int t = threshold;
        final int s = smoothness;

        new Thread(new Runnable() {
            @Override
            public void run() {
                final ByteBufferBitmap buf = ByteBufferBitmap.get(rawThumbnail);

                final BookSettings temp = new BookSettings("__preview__");
                temp.contrast = c;
                temp.gamma = g;
                temp.exposure = e;
                temp.autoLevels = al;
                temp.threshold = t;
                temp.smoothness = s;

                buf.applyEffects(temp);

                buf.getPixels().rewind();
                final Bitmap bmp = Bitmap.createBitmap(buf.getWidth(), buf.getHeight(), Bitmap.Config.ARGB_8888);
                bmp.copyPixelsFromBuffer(buf.getPixels());
                ByteBufferManager.release(buf);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (currentBitmap != null) {
                            currentBitmap.recycle();
                        }
                        currentBitmap = bmp;
                        previewImage.setImageBitmap(bmp);
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final SharedPreferences.Editor edit = prefs.edit();
        BOOK_CONTRAST.setPreferenceValue(edit, contrast);
        BOOK_GAMMA.setPreferenceValue(edit, gamma);
        BOOK_EXPOSURE.setPreferenceValue(edit, exposure);
        BOOK_AUTO_LEVELS.setPreferenceValue(edit, autoLevels);
        BOOK_THRESHOLD.setPreferenceValue(edit, threshold);
        BOOK_SMOOTHNESS.setPreferenceValue(edit, smoothness);
        edit.commit();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        handler.removeCallbacks(updateRunnable);
        if (currentBitmap != null) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
        if (rawThumbnail != null) {
            rawThumbnail.recycle();
            rawThumbnail = null;
        }
        super.onDestroy();
    }

    private static abstract class SliderListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
