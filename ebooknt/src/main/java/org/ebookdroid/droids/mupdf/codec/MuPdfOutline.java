package org.ebookdroid.droids.mupdf.codec;

import org.ebookdroid.core.codec.OutlineLink;

import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

public class MuPdfOutline {

    private static final float[] temp = new float[4];

    private long docHandle;

    public List<OutlineLink> getOutline(final long dochandle) {
        final Object[] flat = getOutlineFlat(dochandle);
        if (flat != null) {
            return buildFromFlat(flat);
        }
        final List<OutlineLink> ls = new ArrayList<OutlineLink>();
        docHandle = dochandle;
        final long outline = open(dochandle);
        ttOutline(ls, outline, 0);
        free(dochandle);
        return ls;
    }

    private static List<OutlineLink> buildFromFlat(final Object[] flat) {
        final String[] titles = (String[]) flat[0];
        final int[] pages = (int[]) flat[1];
        final float[] xs = (float[]) flat[2];
        final float[] ys = (float[]) flat[3];
        final int[] levels = (int[]) flat[4];
        final int count = titles.length;
        final List<OutlineLink> ls = new ArrayList<OutlineLink>(count);
        for (int i = 0; i < count; i++) {
            final OutlineLink link = new OutlineLink(titles[i], levels[i], pages[i]);
            if (pages[i] > 0) {
                link.targetRect = new RectF(xs[i], ys[i], xs[i], ys[i]);
            }
            ls.add(link);
        }
        return ls;
    }

    private void ttOutline(final List<OutlineLink> ls, long outline, final int level) {
        while (outline != 0) {

            final String title = getTitle(outline);
            final String link = getLink(outline, docHandle);
            if (title != null) {
                final OutlineLink outlineLink = new OutlineLink(title, link, level);
                if (outlineLink.targetPage != -1) {
                    int flags = fillLinkTargetPoint(docHandle, outline, temp);
                    outlineLink.targetRect = new RectF();
                    outlineLink.targetRect.left = temp[0];
                    outlineLink.targetRect.top = temp[1];
                    MuPdfDocument.normalizeLinkTargetRect(docHandle, outlineLink.targetPage, outlineLink.targetRect,
                            flags);
                }
                ls.add(outlineLink);
            }

            final long child = getChild(outline);
            ttOutline(ls, child, level + 1);

            outline = getNext(outline);
        }
    }

    private static native Object[] getOutlineFlat(long dochandle);

    private static native String getTitle(long outlinehandle);

    private static native String getLink(long outlinehandle, long dochandle);

    private static native int fillLinkTargetPoint(long dochandle, long outlinehandle, float[] point);

    private static native long getNext(long outlinehandle);

    private static native long getChild(long outlinehandle);

    private static native long open(long dochandle);

    private static native void free(long dochandle);
}
