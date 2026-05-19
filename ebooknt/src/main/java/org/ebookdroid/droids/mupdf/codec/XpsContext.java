package org.ebookdroid.droids.mupdf.codec;

import org.ebookdroid.core.codec.CodecDocument;


public class XpsContext extends MuPdfContext {

    @Override
    public CodecDocument openDocument(final String fileName, final String password) {
        return new MuPdfDocument(this, MuPdfDocument.FORMAT_XPS, fileName, password);
    }

    @Override
    public CodecDocument openDocumentFd(final int fd, final String password) {
        return new MuPdfDocument(this, MuPdfDocument.FORMAT_XPS, fd, password);
    }
}
