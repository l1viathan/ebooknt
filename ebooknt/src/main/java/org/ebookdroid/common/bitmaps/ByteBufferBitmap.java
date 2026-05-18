package org.ebookdroid.common.bitmaps;

import org.ebookdroid.common.settings.books.BookSettings;
import org.ebookdroid.common.settings.definitions.AppPreferences;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ByteBufferBitmap {

    private static final AtomicInteger SEQ = new AtomicInteger();

    public final int id = SEQ.incrementAndGet();
    final AtomicBoolean used = new AtomicBoolean(true);
    long gen;

    volatile ByteBuffer pixels;
    final int size;
    int width;
    int height;

    ByteBufferBitmap(final int width, final int height) {
        this.width = width;
        this.height = height;
        this.size = 4 * width * height;
        this.pixels = create(size).order(ByteOrder.nativeOrder());
        this.pixels.rewind();
    }

    @Override
    protected void finalize() throws Throwable {
        recycle();
    }

    public void recycle() {
        ByteBuffer buf = pixels;
        pixels = null;
        free(buf);
        buf = null;
    }

    public static ByteBufferBitmap get(final Bitmap bmp) {
        if (bmp.getConfig() != Bitmap.Config.ARGB_8888) {
            throw new IllegalArgumentException("Wrong bitmap config: " + bmp.getConfig());
        }
        final ByteBufferBitmap b = ByteBufferManager.getBitmap(bmp.getWidth(), bmp.getHeight());
        bmp.copyPixelsToBuffer(b.pixels);
        return b;
    }

    public static ByteBufferBitmap get(final Bitmap bitmap, final Rect srcRect) {
        final ByteBufferBitmap full = get(bitmap);

        final int srcWidth = srcRect.width();
        final int srcHeight = srcRect.height();

        if (full.width == srcWidth && full.height == srcHeight) {
            return full;
        }

        final ByteBufferBitmap part = ByteBufferManager.getBitmap(srcWidth, srcHeight);
        part.copyPixelsFrom(full, srcRect.left, srcRect.top, part.width, part.height);

        ByteBufferManager.release(full);

        return part;
    }

    public void applyEffects(final BookSettings bs) {
        final boolean correctContrast = bs.contrast != AppPreferences.CONTRAST.defValue;
        final boolean correctGamma = bs.gamma != AppPreferences.GAMMA.defValue;
        final boolean correctExposure = bs.exposure != AppPreferences.EXPOSURE.defValue;
        final boolean applyThreshold = bs.threshold > AppPreferences.THRESHOLD.defValue;
        final boolean applySmoothness = bs.smoothness > AppPreferences.SMOOTHNESS.defValue;

        if (correctContrast || correctGamma || correctExposure || bs.autoLevels || applyThreshold || applySmoothness) {
            if (correctGamma) {
                gamma(bs.gamma);
            }
            if (correctContrast) {
                contrast(bs.contrast);
            }
            if (correctExposure) {
                exposure(bs.exposure);
            }
            if (bs.autoLevels) {
                autoLevels();
            }
            if (applySmoothness) {
                smoothness(bs.smoothness);
            }
            if (applyThreshold) {
                threshold(bs.threshold);
            }
        }
    }

    public void applyTint(final BookSettings bs) {
        if (bs.tint && bs.tintColor != AppPreferences.TINT_COLOR.defValue) {
            tint(bs.tintColor);
        }
    }

    public void copyPixelsFrom(final ByteBufferBitmap src, final int left, final int top, final int width,
            final int height) {
        if (width > this.width) {
            throw new IllegalArgumentException("width > this.width: " + width + ", " + this.width);
        }
        if (height > this.height) {
            throw new IllegalArgumentException("height > this.height: " + height + ", " + this.height);
        }
        if (left + width > src.width) {
            throw new IllegalArgumentException("left + width > src.width: " + left + ", " + width + ", " + src.width);
        }
        if (top + height > src.height) {
            throw new IllegalArgumentException("top + height > src.height: " + top + ", " + height + ", " + src.height);
        }
        nativeFillRect(src.pixels, src.width, this.pixels, this.width, left, top, width, height);
    }

    public ByteBuffer getPixels() {
        return pixels;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public IBitmapRef toBitmap() {
        final IBitmapRef bitmap = BitmapManager.getBitmap("RawBitmap", width, height, Bitmap.Config.ARGB_8888);
        pixels.rewind();
        bitmap.getBitmap().copyPixelsFromBuffer(pixels);
        return bitmap;
    }

    public void fillAlpha(final int v) {
        nativeFillAlpha(pixels, width, height, v);
    }

    public void invert() {
        nativeInvert(pixels, width, height);
    }

    public void tint(int c) {
        nativeTint(pixels, width, height, c);
    }

    public int getAvgLum() {
        return nativeAvgLum(pixels, width, height);
    }

    public void contrast(final int contrast) {
        nativeContrast(pixels, width, height, contrast + 256);
    }

    public void gamma(final int gamma) {
        nativeGamma(pixels, width, height, gamma);
    }

    public void exposure(final int exposure) {
        nativeExposure(pixels, width, height, exposure);
    }

    public void threshold(final int threshold) {
        // threshold stored 0..50 (0.00..0.50); binarize pixels below cutoff→black, above→white
        final int cutoff = threshold * 255 / 100;
        final ByteBuffer buf = pixels;
        final int n = width * height * 4;
        for (int i = 0; i < n; i += 4) {
            final int b = buf.get(i) & 0xFF;
            final int g = buf.get(i + 1) & 0xFF;
            final int r = buf.get(i + 2) & 0xFF;
            final int lum = (r * 77 + g * 150 + b * 29) >> 8;
            final int out = (byte)(lum < cutoff ? 0 : 255);
            buf.put(i, (byte) out);
            buf.put(i + 1, (byte) out);
            buf.put(i + 2, (byte) out);
        }
    }

    public void smoothness(final int smoothness) {
        nativeSmoothness(pixels, width, height, smoothness);
    }

    public void autoLevels() {
        nativeAutoLevels2(pixels, width, height);
    }

    public void eraseColor(final int color) {
        nativeEraseColor(pixels, width, height, color);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[id=" + id + ", width=" + width + ", height=" + height + ", size=" + size
                + "]";
    }

    private static native void nativeInvert(ByteBuffer src, int width, int height);

    private static native void nativeTint(ByteBuffer src, int width, int height, int color);

    private static native void nativeFillAlpha(ByteBuffer src, int width, int height, int value);

    private static native void nativeEraseColor(ByteBuffer src, int width, int height, int color);

    /* contrast value 256 - normal */
    private static native void nativeContrast(ByteBuffer src, int width, int height, int contrast);

    /* gamma value 100 - normal */
    private static native void nativeGamma(ByteBuffer src, int width, int height, int gamma);

    /* Exposure correction values -128...+128 */
    private static native void nativeExposure(ByteBuffer src, int width, int height, int exposure);


    private static native void nativeAutoLevels(ByteBuffer src, int width, int height);

    private static native void nativeAutoLevels2(ByteBuffer src, int width, int height);

    private static native int nativeAvgLum(ByteBuffer src, int width, int height);

    private static native void nativeSmoothness(ByteBuffer src, int width, int height, int smoothness);

    private static native void nativeFillRect(ByteBuffer src, int srcWidth, ByteBuffer dst, int dstWidth, int x,
            int y, int width, int height);

    private static native ByteBuffer create(int size);

    private static native void free(ByteBuffer buf);
}
