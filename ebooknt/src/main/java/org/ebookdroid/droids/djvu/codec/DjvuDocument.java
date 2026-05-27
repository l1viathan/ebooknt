package org.ebookdroid.droids.djvu.codec;

import org.ebookdroid.core.codec.AbstractCodecDocument;
import org.ebookdroid.core.codec.CodecPageInfo;
import org.ebookdroid.core.codec.OutlineLink;
import org.ebookdroid.core.codec.PageTextBox;

import android.graphics.RectF;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.emdev.utils.LengthUtils;

public class DjvuDocument extends AbstractCodecDocument {

    private final ConcurrentLinkedQueue<Long> pendingPageReleases = new ConcurrentLinkedQueue<>();
    private volatile boolean documentFreed;

    DjvuDocument(final DjvuContext djvuContext, final String fileName) {
        super(djvuContext, open(djvuContext.getContextHandle(), fileName));
    }

    DjvuDocument(final DjvuContext djvuContext, final int fd) {
        super(djvuContext, openFd(djvuContext.getContextHandle(), fd));
    }

    void queuePageRelease(final long pageHandle) {
        if (!documentFreed) {
            pendingPageReleases.add(pageHandle);
        }
    }

    private void drainPendingReleases() {
        Long h;
        while ((h = pendingPageReleases.poll()) != null) {
            DjvuPage.free(h);
        }
    }

    @Override
    public List<OutlineLink> getOutline() {
        final DjvuOutline ou = new DjvuOutline();
        return ou.getOutline(documentHandle);
    }

    @Override
    public DjvuPage getPage(final int pageNumber) {
        drainPendingReleases();
        return new DjvuPage(this, context.getContextHandle(), documentHandle, getPage(documentHandle, pageNumber), pageNumber);
    }

    @Override
    public int getPageCount() {
        return getPageCount(documentHandle);
    }

    @Override
    public CodecPageInfo getPageInfo(final int pageNumber) {
        final CodecPageInfo info = new CodecPageInfo();
        final int res = getPageInfo(documentHandle, pageNumber, context.getContextHandle(), info);
        if (res == -1) {
            return null;
        } else {
            return info;
        }
    }

    @Override
    protected void freeDocument() {
        documentFreed = true;
        drainPendingReleases();
        free(documentHandle);
    }

    private native static int getPageInfo(long docHandle, int pageNumber, long contextHandle, CodecPageInfo cpi);

    private native static long open(long contextHandle, String fileName);

    private native static long openFd(long contextHandle, int fd);

    private native static long getPage(long docHandle, int pageNumber);

    private native static int getPageCount(long docHandle);

    private native static void free(long pageHandle);

    private native static int getPageLabelStart(long docHandle);

    public int getPageLabelStart() {
        return getPageLabelStart(documentHandle);
    }

    @Override
    public List<? extends RectF> searchText(final int pageNuber, final String pattern) {
        final List<PageTextBox> list = DjvuPage.getPageText(documentHandle, pageNuber, context.getContextHandle(), pattern.toLowerCase());
        if (LengthUtils.isNotEmpty(list)) {
            CodecPageInfo cpi = getPageInfo(pageNuber);
            for (final PageTextBox ptb : list) {
                DjvuPage.normalizeTextBox(ptb, cpi.width, cpi.height);
            }
        }
        return list;
    }
}
