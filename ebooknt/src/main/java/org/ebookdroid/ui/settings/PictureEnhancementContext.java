package org.ebookdroid.ui.settings;

import org.ebookdroid.core.DecodeService;

public final class PictureEnhancementContext {

    private static DecodeService decodeService;
    private static int pageNo;

    private PictureEnhancementContext() {}

    public static void set(final DecodeService ds, final int page) {
        decodeService = ds;
        pageNo = page;
    }

    public static DecodeService getDecodeService() {
        return decodeService;
    }

    public static int getPageNo() {
        return pageNo;
    }

    public static void clear() {
        decodeService = null;
    }
}
