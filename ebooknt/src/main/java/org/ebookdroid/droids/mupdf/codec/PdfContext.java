package org.ebookdroid.droids.mupdf.codec;

import org.ebookdroid.core.codec.CodecDocument;


public class PdfContext extends MuPdfContext {

    @Override
    public CodecDocument openDocument(final String fileName, final String password) {
        return new MuPdfDocument(this, MuPdfDocument.FORMAT_PDF, fileName, password);
    }

    @Override
    public CodecDocument openDocumentFd(final int fd, final String password) {
        return new MuPdfDocument(this, MuPdfDocument.FORMAT_PDF, fd, password);
    }
}
