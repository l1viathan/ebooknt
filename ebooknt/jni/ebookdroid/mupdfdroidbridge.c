#include <jni.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <android/log.h>

#include <javahelpers.h>

#include <mupdf/fitz.h>
#include <mupdf/pdf.h>

#include <ebookdroid.h>

#define FORMAT_PDF 0
#define FORMAT_XPS 1
#define FORMAT_CBZ 2
#define FORMAT_EPUB 3

/* Debugging helper */

#define JNI_FN(A) Java_org_ebookdroid_droids_mupdf_codec_ ## A

#define LCTX "EBookDroid.MuPDF"

#define DEBUG(args...) \
    __android_log_print(ANDROID_LOG_DEBUG, LCTX, args)

#define ERROR(args...) \
    __android_log_print(ANDROID_LOG_ERROR, LCTX, args)

#define INFO(args...) \
    __android_log_print(ANDROID_LOG_INFO, LCTX, args)

typedef struct renderdocument_s renderdocument_t;
struct renderdocument_s
{
    fz_context *ctx;
    fz_document *document;
    fz_outline *outline;
    unsigned char format; // save current document format.
};

typedef struct renderpage_s renderpage_t;
struct renderpage_s
{
    fz_context *ctx;
    fz_page *page;
    int number;
    fz_display_list* pageList;
};

#define RUNTIME_EXCEPTION "java/lang/RuntimeException"
#define PASSWORD_REQUIRED_EXCEPTION "org/ebookdroid/droids/mupdf/codec/exceptions/MuPdfPasswordRequiredException"
#define WRONG_PASSWORD_EXCEPTION "org/ebookdroid/droids/mupdf/codec/exceptions/MuPdfWrongPasswordEnteredException"

extern fz_locks_context * jni_new_locks();
extern void jni_free_locks(fz_locks_context *locks);
extern void jni_free_lock_obj(void *user);
extern void jni_lock(fz_context *ctx);
extern void jni_unlock(fz_context *ctx);

extern fz_stream *fz_open_file_ptr(fz_context *ctx, FILE *file);

char ext_font_Courier[1024];
char ext_font_CourierBold[1024];
char ext_font_CourierOblique[1024];
char ext_font_CourierBoldOblique[1024];
char ext_font_Helvetica[1024];
char ext_font_HelveticaBold[1024];
char ext_font_HelveticaOblique[1024];
char ext_font_HelveticaBoldOblique[1024];
char ext_font_TimesRoman[1024];
char ext_font_TimesBold[1024];
char ext_font_TimesItalic[1024];
char ext_font_TimesBoldItalic[1024];
char ext_font_Symbol[1024];
char ext_font_ZapfDingbats[1024];

void mupdf_throw_exception_ex(JNIEnv *env, const char* exception, char *message)
{
    jthrowable new_exception = (*env)->FindClass(env, exception);
    if (new_exception == NULL)
    {
        DEBUG("Exception class not found: '%s'", exception);
        return;
    }
    DEBUG("Exception '%s', Message: '%s'", exception, message);
    (*env)->ThrowNew(env, new_exception, message);
}

void mupdf_throw_exception(JNIEnv *env, char *message)
{
    mupdf_throw_exception_ex(env, RUNTIME_EXCEPTION, message);
}

static void mupdf_free_document(renderdocument_t* doc)
{
    if (!doc)
    {
        return;
    }

    void *lock_user = doc->ctx->locks.user;

    if (doc->outline)
    {
        fz_drop_outline(doc->ctx, doc->outline);
    }
    doc->outline = NULL;

    if (doc->document)
    {
        fz_drop_document(doc->ctx, doc->document);
    }
    doc->document = NULL;

    fz_flush_warnings(doc->ctx);
    fz_drop_context(doc->ctx);
    doc->ctx = NULL;

    jni_free_lock_obj(lock_user);

    free(doc);
    doc = NULL;
}

unsigned char *
get_bytes_from_file(char *filename, unsigned int *len) {
	if(filename[0] == '\0') return NULL;

	FILE *fi = fopen(filename, "rb");
	if(!fi) {
		DEBUG("Fontfile '%s' not found!", filename);
		return NULL;
	}

	fseek(fi, 0, SEEK_END);
	*len = ftell(fi);
	fseek(fi, 0, SEEK_SET);

	unsigned char *hexdump = malloc(*len * sizeof(unsigned char));

	int c;
	int n = 0;
	while ((c = fgetc(fi)) != EOF) {
		hexdump[n++] = c;
	}
	fclose(fi);

	return hexdump;
}

void setFontFileName(char* ext_Font, const char* fontFileName)
{
    if (fontFileName && fontFileName[0])
    {
        strcpy(ext_Font, fontFileName);
    }
    else
    {
        ext_Font[0] = 0;
    }
}

JNIEXPORT void JNICALL
JNI_FN(MuPdfContext_setMonoFonts)(JNIEnv *env, jclass clazz, jstring regular,
                                                                 jstring italic, jstring bold, jstring boldItalic)
{
    jboolean iscopy;
    const char* regularFFN = GetStringUTFChars(env, regular, &iscopy);
    const char* italicFFN = GetStringUTFChars(env, italic, &iscopy);
    const char* boldFFN = GetStringUTFChars(env, bold, &iscopy);
    const char* boldItalicFFN = GetStringUTFChars(env, boldItalic, &iscopy);

    setFontFileName(ext_font_Courier, regularFFN);
    setFontFileName(ext_font_CourierOblique, italicFFN);
    setFontFileName(ext_font_CourierBold, boldFFN);
    setFontFileName(ext_font_CourierBoldOblique, boldItalicFFN);

    ReleaseStringUTFChars(env, regular, regularFFN);
    ReleaseStringUTFChars(env, italic, italicFFN);
    ReleaseStringUTFChars(env, bold, boldFFN);
    ReleaseStringUTFChars(env, boldItalic, boldItalicFFN);
}

JNIEXPORT void JNICALL
JNI_FN(MuPdfContext_setSansFonts)(JNIEnv *env, jclass clazz, jstring regular,
                                                                 jstring italic, jstring bold, jstring boldItalic)
{
    jboolean iscopy;
    const char* regularFFN = GetStringUTFChars(env, regular, &iscopy);
    const char* italicFFN = GetStringUTFChars(env, italic, &iscopy);
    const char* boldFFN = GetStringUTFChars(env, bold, &iscopy);
    const char* boldItalicFFN = GetStringUTFChars(env, boldItalic, &iscopy);

    setFontFileName(ext_font_Helvetica, regularFFN);
    setFontFileName(ext_font_HelveticaOblique, italicFFN);
    setFontFileName(ext_font_HelveticaBold, boldFFN);
    setFontFileName(ext_font_HelveticaBoldOblique, boldItalicFFN);

    ReleaseStringUTFChars(env, regular, regularFFN);
    ReleaseStringUTFChars(env, italic, italicFFN);
    ReleaseStringUTFChars(env, bold, boldFFN);
    ReleaseStringUTFChars(env, boldItalic, boldItalicFFN);
}

JNIEXPORT void JNICALL
JNI_FN(MuPdfContext_setSerifFonts)(JNIEnv *env, jclass clazz, jstring regular,
                                                                 jstring italic, jstring bold, jstring boldItalic)
{
    jboolean iscopy;
    const char* regularFFN = GetStringUTFChars(env, regular, &iscopy);
    const char* italicFFN = GetStringUTFChars(env, italic, &iscopy);
    const char* boldFFN = GetStringUTFChars(env, bold, &iscopy);
    const char* boldItalicFFN = GetStringUTFChars(env, boldItalic, &iscopy);

    setFontFileName(ext_font_TimesRoman, regularFFN);
    setFontFileName(ext_font_TimesItalic, italicFFN);
    setFontFileName(ext_font_TimesBold, boldFFN);
    setFontFileName(ext_font_TimesBoldItalic, boldItalicFFN);

    ReleaseStringUTFChars(env, regular, regularFFN);
    ReleaseStringUTFChars(env, italic, italicFFN);
    ReleaseStringUTFChars(env, bold, boldFFN);
    ReleaseStringUTFChars(env, boldItalic, boldItalicFFN);
}

JNIEXPORT void JNICALL
JNI_FN(MuPdfContext_setSymbolFont)(JNIEnv *env, jclass clazz, jstring regular)
{
    jboolean iscopy;
    const char* regularFFN = GetStringUTFChars(env, regular, &iscopy);

    setFontFileName(ext_font_Symbol, regularFFN);

    ReleaseStringUTFChars(env, regular, regularFFN);
}

JNIEXPORT void JNICALL
JNI_FN(MuPdfContext_setDingbatFont)(JNIEnv *env, jclass clazz, jstring regular)
{
    jboolean iscopy;
    const char* regularFFN = GetStringUTFChars(env, regular, &iscopy);

    setFontFileName(ext_font_ZapfDingbats, regularFFN);

    ReleaseStringUTFChars(env, regular, regularFFN);
}

JNIEXPORT jlong JNICALL
JNI_FN(MuPdfDocument_open)(JNIEnv *env, jclass clazz, jint storememory, jint format, jstring fname,
                                                      jstring pwd)
{
    renderdocument_t *doc;
    jboolean iscopy;
    jclass cls;
    jfieldID fid;
    char *filename;
    char *password;

    filename = (char*) (*env)->GetStringUTFChars(env, fname, &iscopy);
    password = (char*) (*env)->GetStringUTFChars(env, pwd, &iscopy);

    doc = malloc(sizeof(renderdocument_t));
    if (!doc)
    {
        mupdf_throw_exception(env, "Out of Memory");
        goto cleanup;
    }
    DEBUG("MuPdfDocument.nativeOpen(): storememory = %d", storememory);

    fz_locks_context *locks = jni_new_locks();
    if (!locks)
    {
        DEBUG("MuPdfDocument.nativeOpen(): no locks available");
    }
    doc->ctx = fz_new_context(NULL, locks, storememory);
    jni_free_locks(locks);
    if (!doc->ctx)
    {
        free(doc);
        mupdf_throw_exception(env, "Out of Memory");
        goto cleanup;
    }
    fz_register_document_handlers(doc->ctx);

    doc->document = NULL;
    doc->outline = NULL;

    doc->format = format;
    fz_try(doc->ctx) {
        doc->document = fz_open_document(doc->ctx, filename);
    } fz_catch(doc->ctx) {
        mupdf_free_document(doc);
        mupdf_throw_exception(env, "PDF file not found or corrupted");
        goto cleanup;
    }

    /*
     * Handle encrypted PDF files
     */

    if (fz_needs_password(doc->ctx, doc->document))
    {
        if (strlen(password))
        {
            int ok = fz_authenticate_password(doc->ctx, doc->document, password);
            if (!ok)
            {
                mupdf_free_document(doc);
                mupdf_throw_exception_ex(env, WRONG_PASSWORD_EXCEPTION, "Wrong password given");
                goto cleanup;
            }
        }
        else
        {
            mupdf_free_document(doc);
            mupdf_throw_exception_ex(env, PASSWORD_REQUIRED_EXCEPTION, "Document needs a password!");
            goto cleanup;
        }
    }

    cleanup:

    (*env)->ReleaseStringUTFChars(env, fname, filename);
    (*env)->ReleaseStringUTFChars(env, pwd, password);

    return (jlong) (long) doc;
}

static const char* format_to_magic(int format)
{
    switch (format) {
        case FORMAT_PDF:  return "x.pdf";
        case FORMAT_XPS:  return "x.xps";
        case FORMAT_CBZ:  return "x.cbz";
        case FORMAT_EPUB: return "x.epub";
        default:          return "x.pdf";
    }
}

JNIEXPORT jlong JNICALL
JNI_FN(MuPdfDocument_openFd)(JNIEnv *env, jclass clazz, jint storememory, jint format, jint fd,
                                                      jstring pwd)
{
    renderdocument_t *doc;
    jboolean iscopy;
    char *password;

    password = (char*) (*env)->GetStringUTFChars(env, pwd, &iscopy);

    doc = malloc(sizeof(renderdocument_t));
    if (!doc)
    {
        mupdf_throw_exception(env, "Out of Memory");
        goto cleanup;
    }
    DEBUG("MuPdfDocument.openFd(): storememory = %d, fd = %d", storememory, fd);

    fz_locks_context *locks_fd = jni_new_locks();
    if (!locks_fd)
    {
        DEBUG("MuPdfDocument.openFd(): no locks available");
    }
    doc->ctx = fz_new_context(NULL, locks_fd, storememory);
    jni_free_locks(locks_fd);
    if (!doc->ctx)
    {
        free(doc);
        mupdf_throw_exception(env, "Out of Memory");
        goto cleanup;
    }
    fz_register_document_handlers(doc->ctx);

    doc->document = NULL;
    doc->outline = NULL;
    doc->format = format;

    fz_try(doc->ctx) {
        int myfd = dup(fd);
        close(fd);
        if (myfd < 0) {
            fz_throw(doc->ctx, FZ_ERROR_GENERIC, "dup() failed on fd %d", fd);
        }
        FILE *file = fdopen(myfd, "rb");
        if (!file) {
            close(myfd);
            fz_throw(doc->ctx, FZ_ERROR_GENERIC, "fdopen() failed on fd %d", myfd);
        }
        fz_stream *stream = fz_open_file_ptr(doc->ctx, file);
        const char *magic = format_to_magic(format);
        doc->document = fz_open_document_with_stream(doc->ctx, magic, stream);
        fz_drop_stream(doc->ctx, stream);
    } fz_catch(doc->ctx) {
        mupdf_free_document(doc);
        mupdf_throw_exception(env, "PDF file not found or corrupted");
        goto cleanup;
    }

    if (fz_needs_password(doc->ctx, doc->document))
    {
        if (strlen(password))
        {
            int ok = fz_authenticate_password(doc->ctx, doc->document, password);
            if (!ok)
            {
                mupdf_free_document(doc);
                mupdf_throw_exception_ex(env, WRONG_PASSWORD_EXCEPTION, "Wrong password given");
                goto cleanup;
            }
        }
        else
        {
            mupdf_free_document(doc);
            mupdf_throw_exception_ex(env, PASSWORD_REQUIRED_EXCEPTION, "Document needs a password!");
            goto cleanup;
        }
    }

    cleanup:

    (*env)->ReleaseStringUTFChars(env, pwd, password);

    return (jlong) (long) doc;
}

JNIEXPORT void JNICALL
JNI_FN(MuPdfDocument_free)(JNIEnv *env, jclass clazz, jlong handle)
{
    renderdocument_t *doc = (renderdocument_t*) (long) handle;
    mupdf_free_document(doc);
}

JNIEXPORT jint JNICALL
JNI_FN(MuPdfDocument_getPageInfo)(JNIEnv *env, jclass cls, jlong handle, jint pageNumber,
                                                             jobject cpi)
{
    renderdocument_t *doc = (renderdocument_t*) (long) handle;

    fz_page *page = NULL;
    fz_rect bounds;

    jclass clazz;
    jfieldID fid;

    int page_error = 0;
    jni_lock(doc->ctx);
    fz_try(doc->ctx)
    {
        page = fz_load_page(doc->ctx, doc->document, pageNumber - 1);
        bounds = fz_bound_page(doc->ctx, page);
    }
    fz_catch(doc->ctx)
    {
        if (page) { fz_drop_page(doc->ctx, page); page = NULL; }
        page_error = 1;
    }
    jni_unlock(doc->ctx);

    if (page_error)
        return -1;

    if (page)
    {
        clazz = (*env)->GetObjectClass(env, cpi);
        if (0 == clazz)
        {
            return (-1);
        }

        fid = (*env)->GetFieldID(env, clazz, "width", "I");
        (*env)->SetIntField(env, cpi, fid, bounds.x1-bounds.x0);

        fid = (*env)->GetFieldID(env, clazz, "height", "I");
        (*env)->SetIntField(env, cpi, fid, bounds.y1-bounds.y0);

        fid = (*env)->GetFieldID(env, clazz, "dpi", "I");
        (*env)->SetIntField(env, cpi, fid, 0);

        fid = (*env)->GetFieldID(env, clazz, "rotation", "I");
        (*env)->SetIntField(env, cpi, fid, 0);

        fid = (*env)->GetFieldID(env, clazz, "version", "I");
        (*env)->SetIntField(env, cpi, fid, 0);

        jni_lock(doc->ctx);
        fz_drop_page(doc->ctx, page);
        jni_unlock(doc->ctx);
        return 0;
    }
    return -1;
}

JNIEXPORT jlong JNICALL
JNI_FN(MuPdfLinks_getFirstPageLink)(JNIEnv *env, jclass clazz, jlong handle,
                                                               jlong pagehandle)
{
    renderdocument_t *doc = (renderdocument_t*) (long) handle;
    renderpage_t *page = (renderpage_t*) (long) pagehandle;
    if (!page || !doc)
        return (jlong)(long)NULL;
    jni_lock(doc->ctx);
    fz_link *links = fz_load_links(doc->ctx, page->page);
    jni_unlock(doc->ctx);
    return (jlong)(long)links;
}

JNIEXPORT jlong JNICALL
JNI_FN(MuPdfLinks_getNextPageLink)(JNIEnv *env, jclass clazz, jlong linkhandle)
{
    fz_link *link = (fz_link*) (long) linkhandle;
    return (jlong)(long)(link ? link->next : NULL);
}

/**
 * Returns 1 for internal links (to a page number), 2 for external (to a URL)
 */
JNIEXPORT jint JNICALL
JNI_FN(MuPdfLinks_getPageLinkType)(JNIEnv *env, jclass clazz, jlong handle, jlong linkhandle)
{
    renderdocument_t *doc = (renderdocument_t*) (long) handle;
    fz_link *link = (fz_link*) (long) linkhandle;
    if (link == NULL)
        return 0;

    jni_lock(doc->ctx);
    int external = fz_is_external_link(doc->ctx, link->uri);
    jni_unlock(doc->ctx);
    return external ? 2 : 1;
}

JNIEXPORT jstring JNICALL
JNI_FN(MuPdfLinks_getPageLinkUrl)(JNIEnv *env, jclass clazz, jlong linkhandle)
{
    fz_link *link = (fz_link*) (long) linkhandle;

    if (!link)
    {
        return NULL;
    }

    char linkbuf[1024];
    snprintf(linkbuf, 1023, "%s", link->uri);

    return (*env)->NewStringUTF(env, linkbuf);
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPdfLinks_fillPageLinkSourceRect)(JNIEnv *env, jclass clazz, jlong linkhandle,
                                                                     jfloatArray boundsArray)
{
    fz_link *link = (fz_link*) (long) linkhandle;

    if (!link)
    {
        return JNI_FALSE;
    }

    jfloat *bounds = (*env)->GetPrimitiveArrayCritical(env, boundsArray, 0);
    if (!bounds)
    {
        return JNI_FALSE;
    }

    bounds[0] = link->rect.x0;
    bounds[1] = link->rect.y0;
    bounds[2] = link->rect.x1;
    bounds[3] = link->rect.y1;

    (*env)->ReleasePrimitiveArrayCritical(env, boundsArray, bounds, 0);

    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
JNI_FN(MuPdfLinks_getPageLinkTargetPage)(JNIEnv *env, jclass clazz, jlong handle, jlong linkhandle)
{
    renderdocument_t *doc = (renderdocument_t*) (long) handle;
    fz_link *link = (fz_link*) (long) linkhandle;

    if (!link)
        return -1;

    jni_lock(doc->ctx);
    int page = fz_resolve_link(doc->ctx, doc->document, link->uri, NULL, NULL);
    jni_unlock(doc->ctx);
    return page;
}

JNIEXPORT jint JNICALL
JNI_FN(MuPdfLinks_fillPageLinkTargetPoint)(JNIEnv *env, jclass clazz, jlong handle, jlong linkhandle,
                                                                      jfloatArray pointArray)
{
    renderdocument_t *doc = (renderdocument_t*) (long) handle;
    fz_link *link = (fz_link*) (long) linkhandle;

    if (!link)
        return 0;

    /* Resolve link target before entering critical section: must not hold
     * a pthread mutex (jni_lock) while GetPrimitiveArrayCritical is active. */
    jni_lock(doc->ctx);
    int is_external = fz_is_external_link(doc->ctx, link->uri);
    jni_unlock(doc->ctx);

    if (is_external)
        return 0;

    float x = 0.0f, y = 0.0f;
    jni_lock(doc->ctx);
    fz_resolve_link(doc->ctx, doc->document, link->uri, &x, &y);
    jni_unlock(doc->ctx);

    jfloat *point = (*env)->GetPrimitiveArrayCritical(env, pointArray, 0);
    if (!point)
        return 0;

    point[0] = x;
    point[1] = y;

    (*env)->ReleasePrimitiveArrayCritical(env, pointArray, point, 0);

    return 0;
}

JNIEXPORT jint JNICALL
JNI_FN(MuPdfDocument_getPageCount)(JNIEnv *env, jclass clazz, jlong handle)
{
    renderdocument_t *doc = (renderdocument_t*) (long) handle;
    return fz_count_pages(doc->ctx, doc->document);
}

JNIEXPORT jlong JNICALL
JNI_FN(MuPdfPage_open)(JNIEnv *env, jclass clazz, jlong dochandle, jint pageno)
{
    renderdocument_t *doc = (renderdocument_t*) (long) dochandle;
    renderpage_t *page = NULL;
    fz_device *dev = NULL;

    fz_context* ctx = fz_clone_context(doc->ctx);
    if (!ctx)
    {
        mupdf_throw_exception(env, "Context cloning failed");
        return (jlong) (long) NULL;
    }

    page = fz_malloc_no_throw(ctx, sizeof(renderpage_t));
//    DEBUG("MuPdfPage_open(%p, %d): page=%p", doc, pageno, page);

    if (!page)
    {
        fz_drop_context(ctx);
        mupdf_throw_exception(env, "Out of Memory");
        return (jlong) (long) NULL;
    }

    page->ctx = ctx;
    page->page = NULL;
    page->pageList = NULL;

    /* Display list and list device must be created with the master context,
     * alongside fz_load_page and fz_run_page — per MuPDF multi-threading rules.
     * All operations on doc->ctx are serialized via jni_lock. */
    int load_error = 0;
    jni_lock(doc->ctx);
    fz_try(doc->ctx)
    {
        page->page = fz_load_page(doc->ctx, doc->document, pageno - 1);
        fz_rect mediabox = fz_bound_page(doc->ctx, page->page);
        page->pageList = fz_new_display_list(doc->ctx, mediabox);
        dev = fz_new_list_device(doc->ctx, page->pageList);
        fz_run_page(doc->ctx, page->page, dev, fz_identity, NULL);
    }
    fz_catch(doc->ctx)
    {
        ERROR("MuPdfPage_open(%d): %s", pageno, fz_caught_message(doc->ctx));
        if (dev) { fz_drop_device(doc->ctx, dev); dev = NULL; }
        if (page->pageList) { fz_drop_display_list(doc->ctx, page->pageList); page->pageList = NULL; }
        if (page->page) { fz_drop_page(doc->ctx, page->page); page->page = NULL; }
        load_error = 1;
    }
    if (!load_error)
    {
        fz_close_device(doc->ctx, dev);
        fz_drop_device(doc->ctx, dev);
    }
    jni_unlock(doc->ctx);

    if (load_error)
    {
        fz_free(ctx, page);
        fz_drop_context(ctx);
        mupdf_throw_exception(env, "error loading/running page");
        return (jlong) (long) NULL;
    }

    return (jlong) (long) page;
}

JNIEXPORT void JNICALL
JNI_FN(MuPdfPage_free)(JNIEnv *env, jclass clazz, jlong dochandle, jlong handle)
{
    renderdocument_t *doc = (renderdocument_t*) (long) dochandle;
    renderpage_t *page = (renderpage_t*) (long) handle;
//    DEBUG("MuPdfPage_free(%p): start", page);

    if (!page || !page->ctx)
    {
        DEBUG("No page to free");
        return;
    }

    fz_context *ctx = page->ctx;

    if (page->pageList)
    {
        fz_drop_display_list(ctx, page->pageList);
    }

    if (page->page)
    {
        jni_lock(doc->ctx);
        fz_drop_page(doc->ctx, page->page);
        jni_unlock(doc->ctx);
    }

    fz_free(ctx, page);
    fz_drop_context(ctx);
    page = NULL;
    ctx = NULL;

//    DEBUG("MuPdfPage_free(%p): finish", page);
}

JNIEXPORT void JNICALL
JNI_FN(MuPdfPage_getBounds)(JNIEnv *env, jclass clazz, jlong dochandle, jlong handle,
                                                       jfloatArray bounds)
{
    renderdocument_t *doc = (renderdocument_t*) (long) dochandle;
    renderpage_t *page = (renderpage_t*) (long) handle;
    fz_rect page_bounds;
    jni_lock(doc->ctx);
    page_bounds = fz_bound_page(doc->ctx, page->page);
    jni_unlock(doc->ctx);

    jfloat *bbox = (*env)->GetPrimitiveArrayCritical(env, bounds, 0);
    if (!bbox)
        return;
    bbox[0] = page_bounds.x0;
    bbox[1] = page_bounds.y0;
    bbox[2] = page_bounds.x1;
    bbox[3] = page_bounds.y1;
    (*env)->ReleasePrimitiveArrayCritical(env, bounds, bbox, 0);
}


JNIEXPORT jboolean JNICALL
JNI_FN(MuPdfPage_renderPageDirect)(JNIEnv *env, jobject this, jlong dochandle,
                                                              jlong pagehandle, jintArray viewboxarray,
                                                              jfloatArray matrixarray, jobject byteBuffer, jint nightmode, jint slowcmyk)
{
    renderdocument_t *doc = (renderdocument_t*) (long) dochandle;
    renderpage_t *page = (renderpage_t*) (long) pagehandle;

//    DEBUG("MuPdfPage_renderPageBitmap(%p, %p): start", doc, page);

    fz_matrix ctm;
    fz_rect viewbox;
    fz_pixmap *pixmap;
    jfloat *matrix;
    jint *viewboxarr;
    jint *dimen;
    int length, val;
    fz_device *dev = NULL;

    void *pixels;

    int ret;

    pixels = (*env)->GetDirectBufferAddress(env, byteBuffer);
    if (!pixels) {
        ERROR("GetDirectBufferAddress failed!");
        return JNI_FALSE;
    }

    matrix = (*env)->GetPrimitiveArrayCritical(env, matrixarray, 0);
    ctm = fz_identity;
    ctm.a = matrix[0];
    ctm.b = matrix[1];
    ctm.c = matrix[2];
    ctm.d = matrix[3];
    ctm.e = matrix[4];
    ctm.f = matrix[5];
    (*env)->ReleasePrimitiveArrayCritical(env, matrixarray, matrix, 0);

    viewboxarr = (*env)->GetPrimitiveArrayCritical(env, viewboxarray, 0);
    viewbox.x0 = viewboxarr[0];
    viewbox.y0 = viewboxarr[1];
    viewbox.x1 = viewboxarr[2];
    viewbox.y1 = viewboxarr[3];
    (*env)->ReleasePrimitiveArrayCritical(env, viewboxarray, viewboxarr, 0);

    fz_context* ctx = page->ctx;
    if (!ctx)
    {
        ERROR("No page context");
        return JNI_FALSE;
    }

    // TODO: Changing the mupdf/fitz source for implementation of the slow cmyk
    // and night mode respectively is not so nice; have to find a more elegant
    // way to do this

    //add check for night mode and set global variable accordingly
    ctx->ebookdroid_nightmode = nightmode;
    // FIXME: This seems to be necessary; why is doc->ctx different than ctx?
    doc->ctx->ebookdroid_nightmode = nightmode;

    // TODO: slowcmyk patch no longer applies cleanly to mupdf, so this was disabled
    //add check for slowcmyk mode and set global variable accordingly
    //ctx->ebookdroid_slowcmyk = slowcmyk;

    fz_try(ctx)
    {
         const int w = viewbox.x1 - viewbox.x0;
         const int h = viewbox.y1 - viewbox.y0;
         pixmap = fz_new_pixmap_with_data(ctx, fz_device_rgb(ctx), w, h, NULL, 1, 4 * w, pixels);

         fz_clear_pixmap_with_value(ctx, pixmap, 0xff);

         dev = fz_new_draw_device(ctx, fz_identity, pixmap);

         fz_run_display_list(ctx, page->pageList, dev, ctm, viewbox, NULL);
    }
    fz_always(ctx)
    {
       fz_close_device(ctx, dev);
       fz_drop_device(ctx, dev);
       fz_drop_pixmap(ctx, pixmap);
    }
    fz_catch(ctx)
    {
        ERROR("renderPageDirect failed: %s", fz_caught_message(ctx));
    }
    return JNI_TRUE;
}

static int charat(fz_stext_page *page, int idx)
{
    fz_stext_block *block;
    fz_stext_line *line;
    fz_stext_char *ch;
    int ofs = 0;

    for (block = page->first_block; block; block = block->next)
    {
        if (block->type != FZ_STEXT_BLOCK_TEXT)
            continue;
        for (line = block->u.t.first_line; line; line = line->next)
        {
            for (ch = line->first_char; ch; ch = ch->next)
            {
                if (ofs == idx)
                    return ch->c;
                ofs++;
            }
            if (ofs == idx)
                return '\n';
            ofs++;
        }
    }
    return -1;
}

static fz_rect bboxcharat(fz_stext_page *page, int idx)
{
    fz_stext_block *block;
    fz_stext_line *line;
    fz_stext_char *ch;
    int ofs = 0;

    for (block = page->first_block; block; block = block->next)
    {
        if (block->type != FZ_STEXT_BLOCK_TEXT)
            continue;
        for (line = block->u.t.first_line; line; line = line->next)
        {
            for (ch = line->first_char; ch; ch = ch->next)
            {
                if (ofs == idx)
                    return fz_rect_from_quad(ch->quad);
                ofs++;
            }
            ofs++;
        }
    }
    return fz_empty_rect;
}

static int textlen(fz_stext_page *page)
{
    fz_stext_block *block;
    fz_stext_line *line;
    fz_stext_char *ch;
    int len = 0;

    for (block = page->first_block; block; block = block->next)
    {
        if (block->type != FZ_STEXT_BLOCK_TEXT)
            continue;
        for (line = block->u.t.first_line; line; line = line->next)
        {
            for (ch = line->first_char; ch; ch = ch->next)
            {
                len++;
            }
            len++;
        }
    }
    return len;
}

static int match(CharacterHelper* ch, fz_stext_page *page, const char *s, int n)
{
    int orig = n;
    int c;

    while (*s)
    {
        s += fz_chartorune(&c, (char *) s);
        if (c == ' ' && charat(page, n) == ' ')
        {
            while (charat(page, n) == ' ')
            {
                n++;
            }
        }
        else
        {
            if (c != CharacterHelper_toLowerCase(ch, charat(page, n)))
            {
                return 0;
            }
            n++;
        }
    }
    return n - orig;
}

JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPdfPage_search)(JNIEnv * env, jobject thiz, jlong dochandle, jlong pagehandle,
                                                        jstring text)
{

    renderdocument_t *doc = (renderdocument_t*) (long) dochandle;
    renderpage_t *page = (renderpage_t*) (long) pagehandle;
    // DEBUG("MuPdfPage(%p).search(%p, %p)", thiz, doc, page);

    if (!doc || !page)
    {
        return NULL;
    }

    const char *str = (*env)->GetStringUTFChars(env, text, NULL);
    if (str == NULL)
    {
        return NULL;
    }

    ArrayListHelper alh;
    PageTextBoxHelper ptbh;
    CharacterHelper ch;

    if (!ArrayListHelper_init(&alh, env) || !PageTextBoxHelper_init(&ptbh, env)|| !CharacterHelper_init(&ch, env))
    {
        DEBUG("search(): JNI helper initialization failed"); //, pagehandle);
        return NULL;
    }
    jobject arrayList = ArrayListHelper_create(&alh);
    // DEBUG("MuPdfPage(%p).search(%p, %p): array: %p", thiz, doc, page, arrayList);
    if (!arrayList)
    {
        return NULL;
    }

    fz_stext_page *pagetext = NULL;
    fz_device *dev = NULL;
    int pos;
    int len;
    int i, n;

    fz_try(doc->ctx)
    {
        fz_rect rect = fz_bound_page(doc->ctx, page->page);
        pagetext = fz_new_stext_page(doc->ctx, rect);
        dev = fz_new_stext_device(doc->ctx, pagetext, NULL);
        fz_run_page(doc->ctx, page->page, dev, fz_identity, NULL);

        fz_close_device(doc->ctx, dev);
        fz_drop_device(doc->ctx, dev);
        dev = NULL;

        len = textlen(pagetext);

        for (pos = 0; pos < len; pos++)
        {
            fz_rect rr = fz_empty_rect;

            n = match(&ch, pagetext, str, pos);
            if (n > 0)
            {
                for (i = 0; i < n; i++)
                {
                    fz_rect tmp_rr = bboxcharat(pagetext, pos + i);
                    rr = fz_union_rect(rr, tmp_rr);
                }

                if (!fz_is_empty_rect(rr))
                {
                    int coords[4];
                    coords[0] = (rr.x0);
                    coords[1] = (rr.y0);
                    coords[2] = (rr.x1);
                    coords[3] = (rr.y1);
                    jobject ptb = PageTextBoxHelper_create(&ptbh);
                    if (ptb)
                    {
                        PageTextBoxHelper_setRect(&ptbh, ptb, coords);
                        ArrayListHelper_add(&alh, arrayList, ptb);
                    }
                }
            }
        }
    } fz_always(doc->ctx)
    {
        if (pagetext)
        {
            fz_drop_stext_page(doc->ctx, pagetext);
        }
        if (dev)
        {
            fz_drop_device(doc->ctx, dev);
        }
    } fz_catch(doc->ctx)
    {
        jclass cls;
        (*env)->ReleaseStringUTFChars(env, text, str);
        cls = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
        if (cls != NULL)
        {
            (*env)->ThrowNew(env, cls, "Out of memory in MuPDFCore_searchPage");
        }
        (*env)->DeleteLocalRef(env, cls);
        return NULL;
    }

    (*env)->ReleaseStringUTFChars(env, text, str);

    return arrayList;
}

JNIEXPORT jobject JNICALL
JNI_FN(MuPdfPage_getPageText)(JNIEnv * env, jobject thiz, jlong dochandle, jlong pagehandle)
{
    renderdocument_t *doc = (renderdocument_t*) (long) dochandle;
    renderpage_t *page = (renderpage_t*) (long) pagehandle;

    if (!doc || !page)
    {
        return NULL;
    }

    ArrayListHelper alh;
    PageTextBoxHelper ptbh;

    if (!ArrayListHelper_init(&alh, env) || !PageTextBoxHelper_init(&ptbh, env))
    {
        return NULL;
    }
    jobject arrayList = ArrayListHelper_create(&alh);
    if (!arrayList)
    {
        return NULL;
    }

    fz_stext_page *pagetext = NULL;
    fz_device *dev = NULL;

    fz_try(doc->ctx)
    {
        fz_rect rect = fz_bound_page(doc->ctx, page->page);
        pagetext = fz_new_stext_page(doc->ctx, rect);
        dev = fz_new_stext_device(doc->ctx, pagetext, NULL);
        fz_run_page(doc->ctx, page->page, dev, fz_identity, NULL);

        fz_close_device(doc->ctx, dev);
        fz_drop_device(doc->ctx, dev);
        dev = NULL;

        fz_stext_block *block;
        fz_stext_line *line;
        fz_stext_char *ch;

        for (block = pagetext->first_block; block; block = block->next)
        {
            if (block->type != FZ_STEXT_BLOCK_TEXT)
                continue;
            for (line = block->u.t.first_line; line; line = line->next)
            {
                float total_w = 0;
                int char_count = 0;
                for (ch = line->first_char; ch; ch = ch->next)
                {
                    fz_rect cr = fz_rect_from_quad(ch->quad);
                    total_w += cr.x1 - cr.x0;
                    char_count++;
                }
                float avg_w = char_count > 0 ? total_w / char_count : 1;
                float gap_threshold = avg_w * 0.3f;

                char word_buf[512];
                int word_len = 0;
                fz_rect word_bbox = fz_empty_rect;
                float prev_x1 = -1;
                int words_emitted = 0;

                for (ch = line->first_char; ; ch = ch ? ch->next : NULL)
                {
                    int is_space = 0;
                    int is_gap = 0;
                    if (ch)
                    {
                        if (ch->c == ' ' || ch->c == '\t' || ch->c == 0xA0
                            || (ch->c >= 0x2000 && ch->c <= 0x200A)
                            || ch->c == 0x202F || ch->c == 0x205F || ch->c == 0x3000)
                        {
                            is_space = 1;
                        }
                        else if (prev_x1 >= 0)
                        {
                            fz_rect cr = fz_rect_from_quad(ch->quad);
                            if (cr.x0 - prev_x1 > gap_threshold)
                            {
                                is_gap = 1;
                            }
                        }
                    }
                    int is_sep = is_space || is_gap;
                    int is_end = (ch == NULL);

                    if ((is_sep || is_end) && word_len > 0)
                    {
                        word_buf[word_len] = 0;
                        if (!fz_is_empty_rect(word_bbox))
                        {
                            int coords[4];
                            coords[0] = (int) word_bbox.x0;
                            coords[1] = (int) word_bbox.y0;
                            coords[2] = (int) word_bbox.x1;
                            coords[3] = (int) word_bbox.y1;

                            jobject ptb = PageTextBoxHelper_create(&ptbh);
                            if (ptb)
                            {
                                PageTextBoxHelper_setRect(&ptbh, ptb, coords);
                                jstring jtext = (*env)->NewStringUTF(env, word_buf);
                                PageTextBoxHelper_setText(&ptbh, ptb, jtext);
                                (*env)->DeleteLocalRef(env, jtext);
                                ArrayListHelper_add(&alh, arrayList, ptb);
                            }
                        }
                        word_len = 0;
                        word_bbox = fz_empty_rect;
                        words_emitted++;
                    }

                    if (is_end)
                        break;

                    if (is_space)
                    {
                        fz_rect cr = fz_rect_from_quad(ch->quad);
                        prev_x1 = cr.x1;
                    }
                    else
                    {
                        if (is_gap)
                        {
                            word_len = 0;
                            word_bbox = fz_empty_rect;
                        }
                        int n = fz_runetochar(word_buf + word_len, ch->c);
                        word_len += n;
                        if (word_len >= (int)sizeof(word_buf) - 8)
                        {
                            word_buf[word_len] = 0;
                            word_len = 0;
                        }
                        fz_rect char_rect = fz_rect_from_quad(ch->quad);
                        word_bbox = fz_union_rect(word_bbox, char_rect);
                        prev_x1 = char_rect.x1;
                    }
                }
            }
        }
    } fz_always(doc->ctx)
    {
        if (pagetext)
        {
            fz_drop_stext_page(doc->ctx, pagetext);
        }
        if (dev)
        {
            fz_drop_device(doc->ctx, dev);
        }
    } fz_catch(doc->ctx)
    {
        return NULL;
    }

    return arrayList;
}


//Outline

static int outline_count(fz_outline *node)
{
    int n = 0;
    while (node) {
        if (node->title) n++;
        n += outline_count(node->down);
        node = node->next;
    }
    return n;
}

typedef struct {
    float width, height;
    int valid;
} page_dims_t;

static void page_dims_lookup(fz_context *ctx, fz_document *document,
    int page_idx, page_dims_t *dims_cache, int page_count)
{
    if (page_idx < 0 || page_idx >= page_count || dims_cache[page_idx].valid)
        return;

    pdf_document *pdoc = pdf_specifics(ctx, document);
    if (pdoc) {
        fz_try(ctx) {
            pdf_obj *pageobj = pdf_lookup_page_obj(ctx, pdoc, page_idx);
            fz_rect box = pdf_to_rect(ctx,
                pdf_dict_get_inheritable(ctx, pageobj, PDF_NAME(MediaBox)));
            dims_cache[page_idx].width = box.x1 - box.x0;
            dims_cache[page_idx].height = box.y1 - box.y0;
        } fz_catch(ctx) {
            dims_cache[page_idx].width = 1;
            dims_cache[page_idx].height = 1;
        }
    } else {
        fz_page *pg = NULL;
        fz_try(ctx) {
            pg = fz_load_page(ctx, document, page_idx);
            fz_rect bounds = fz_bound_page(ctx, pg);
            dims_cache[page_idx].width = bounds.x1 - bounds.x0;
            dims_cache[page_idx].height = bounds.y1 - bounds.y0;
        } fz_always(ctx) {
            if (pg) fz_drop_page(ctx, pg);
        } fz_catch(ctx) {
            dims_cache[page_idx].width = 1;
            dims_cache[page_idx].height = 1;
        }
    }
    dims_cache[page_idx].valid = 1;
}

static void outline_flatten(fz_context *ctx, fz_document *document,
    fz_outline *node, int level,
    jstring *titles, int *pages, float *xs, float *ys, int *levels,
    int *pos, JNIEnv *env,
    page_dims_t *dims_cache, int page_count)
{
    while (node) {
        if (node->title) {
            int i = *pos;
            titles[i] = (*env)->NewStringUTF(env, node->title ? node->title : "");
            levels[i] = level;
            pages[i] = -1;
            xs[i] = 0.0f;
            ys[i] = 0.0f;

            if (node->uri && !fz_is_external_link(ctx, node->uri)) {
                float x = 0.0f, y = 0.0f;
                int resolved = fz_resolve_link(ctx, document, node->uri, &x, &y);
                if (resolved >= 0) {
                    pages[i] = resolved + 1;
                    if (x != 0.0f || y != 0.0f) {
                        page_dims_lookup(ctx, document, resolved, dims_cache, page_count);
                        if (resolved < page_count) {
                            float w = dims_cache[resolved].width;
                            float h = dims_cache[resolved].height;
                            xs[i] = (w > 0) ? x / w : 0.0f;
                            ys[i] = (h > 0) ? y / h : 0.0f;
                        }
                    }
                }
            }
            (*pos)++;
        }
        outline_flatten(ctx, document, node->down, level + 1,
            titles, pages, xs, ys, levels, pos, env,
            dims_cache, page_count);
        node = node->next;
    }
}

JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPdfOutline_getOutlineFlat)(JNIEnv *env, jclass clazz, jlong dochandle)
{
    renderdocument_t *doc = (renderdocument_t*) (long) dochandle;

    if (!doc->outline) {
        jni_lock(doc->ctx);
        fz_try(doc->ctx) {
            doc->outline = fz_load_outline(doc->ctx, doc->document);
        } fz_catch(doc->ctx) {
            doc->outline = NULL;
        }
        jni_unlock(doc->ctx);
    }
    if (!doc->outline)
        return NULL;

    int count = outline_count(doc->outline);
    if (count <= 0)
        return NULL;

    jstring *c_titles = (jstring *)calloc(count, sizeof(jstring));
    int *c_pages = (int *)calloc(count, sizeof(int));
    float *c_xs = (float *)calloc(count, sizeof(float));
    float *c_ys = (float *)calloc(count, sizeof(float));
    int *c_levels = (int *)calloc(count, sizeof(int));

    if (!c_titles || !c_pages || !c_xs || !c_ys || !c_levels) {
        free(c_titles); free(c_pages); free(c_xs); free(c_ys); free(c_levels);
        return NULL;
    }

    jni_lock(doc->ctx);
    int page_count = fz_count_pages(doc->ctx, doc->document);
    page_dims_t *dims_cache = (page_dims_t *)calloc(page_count, sizeof(page_dims_t));
    int pos = 0;
    outline_flatten(doc->ctx, doc->document, doc->outline, 0,
        c_titles, c_pages, c_xs, c_ys, c_levels, &pos, env,
        dims_cache, page_count);
    jni_unlock(doc->ctx);

    free(dims_cache);

    fz_drop_outline(doc->ctx, doc->outline);
    doc->outline = NULL;

    jobjectArray result = (*env)->NewObjectArray(env, 5, (*env)->FindClass(env, "java/lang/Object"), NULL);

    jobjectArray jTitles = (*env)->NewObjectArray(env, count, (*env)->FindClass(env, "java/lang/String"), NULL);
    for (int i = 0; i < count; i++)
        (*env)->SetObjectArrayElement(env, jTitles, i, c_titles[i]);

    jintArray jPages = (*env)->NewIntArray(env, count);
    (*env)->SetIntArrayRegion(env, jPages, 0, count, c_pages);

    jfloatArray jXs = (*env)->NewFloatArray(env, count);
    (*env)->SetFloatArrayRegion(env, jXs, 0, count, c_xs);

    jfloatArray jYs = (*env)->NewFloatArray(env, count);
    (*env)->SetFloatArrayRegion(env, jYs, 0, count, c_ys);

    jintArray jLevels = (*env)->NewIntArray(env, count);
    (*env)->SetIntArrayRegion(env, jLevels, 0, count, c_levels);

    (*env)->SetObjectArrayElement(env, result, 0, jTitles);
    (*env)->SetObjectArrayElement(env, result, 1, jPages);
    (*env)->SetObjectArrayElement(env, result, 2, jXs);
    (*env)->SetObjectArrayElement(env, result, 3, jYs);
    (*env)->SetObjectArrayElement(env, result, 4, jLevels);

    free(c_titles); free(c_pages); free(c_xs); free(c_ys); free(c_levels);

    return result;
}

JNIEXPORT jint JNICALL
JNI_FN(MuPdfDocument_getPageLabelStart)(JNIEnv *env, jclass clazz, jlong dochandle)
{
    renderdocument_t *doc = (renderdocument_t*) (long) dochandle;
    fz_context *ctx = doc->ctx;
    int result = -1;

    jni_lock(ctx);
    fz_try(ctx) {
        pdf_document *pdoc = pdf_specifics(ctx, doc->document);
        if (pdoc) {
            pdf_obj *root = pdf_dict_get(ctx, pdf_trailer(ctx, pdoc), PDF_NAME(Root));
            pdf_obj *labels = pdf_dict_gets(ctx, root, "PageLabels");
            if (labels) {
                pdf_obj *nums = pdf_dict_gets(ctx, labels, "Nums");
                if (nums && pdf_is_array(ctx, nums)) {
                    int len = pdf_array_len(ctx, nums);
                    for (int i = 0; i + 1 < len; i += 2) {
                        int page_idx = pdf_to_int(ctx, pdf_array_get(ctx, nums, i));
                        pdf_obj *dict = pdf_array_get(ctx, nums, i + 1);
                        if (!pdf_is_dict(ctx, dict))
                            continue;
                        const char *style = pdf_to_name(ctx,
                            pdf_dict_gets(ctx, dict, "S"));
                        int st = 1;
                        pdf_obj *st_obj = pdf_dict_gets(ctx, dict, "St");
                        if (st_obj)
                            st = pdf_to_int(ctx, st_obj);
                        if (style && strcmp(style, "D") == 0 && st == 1) {
                            result = page_idx + 1;
                            break;
                        }
                    }
                }
            }
        }
    } fz_catch(ctx) {
        result = -1;
    }
    jni_unlock(ctx);

    return result;
}

JNIEXPORT jlong JNICALL
JNI_FN(MuPdfOutline_open)(JNIEnv *env, jclass clazz, jlong dochandle)
{
    renderdocument_t *doc = (renderdocument_t*) (long) dochandle;
    if (!doc->outline) {
        jni_lock(doc->ctx);
        fz_try(doc->ctx) {
            doc->outline = fz_load_outline(doc->ctx, doc->document);
        } fz_catch(doc->ctx) {
            doc->outline = NULL;
        }
        jni_unlock(doc->ctx);
    }
    return (jlong) (uintptr_t) doc->outline;
}

JNIEXPORT void JNICALL
JNI_FN(MuPdfOutline_free)(JNIEnv *env, jclass clazz, jlong dochandle)
{
    renderdocument_t *doc = (renderdocument_t*) (long) dochandle;
    if (doc)
    {
        if (doc->outline)
            fz_drop_outline(doc->ctx, doc->outline);
        doc->outline = NULL;
    }
}

JNIEXPORT jstring JNICALL
JNI_FN(MuPdfOutline_getTitle)(JNIEnv *env, jclass clazz, jlong outlinehandle)
{
    fz_outline *outline = (fz_outline*) (uintptr_t) outlinehandle;
    if (outline)
        return (*env)->NewStringUTF(env, outline->title);
    return NULL;
}

JNIEXPORT jstring JNICALL
JNI_FN(MuPdfOutline_getLink)(JNIEnv *env, jclass clazz, jlong outlinehandle, jlong dochandle)
{
    fz_outline *outline = (fz_outline*) (uintptr_t) outlinehandle;
    renderdocument_t *doc = (renderdocument_t*) (long) dochandle;

    if (!outline || !outline->uri)
        return NULL;

    if (fz_is_external_link(doc->ctx, outline->uri))
    {
        return (*env)->NewStringUTF(env, outline->uri);
    }
    else
    {
        jni_lock(doc->ctx);
        int resolved = fz_resolve_link(doc->ctx, doc->document, outline->uri, NULL, NULL);
        jni_unlock(doc->ctx);

        char linkbuf[128];
        snprintf(linkbuf, sizeof(linkbuf), "#%d", resolved + 1);
        return (*env)->NewStringUTF(env, linkbuf);
    }
}

JNIEXPORT jint JNICALL
JNI_FN(MuPdfOutline_fillLinkTargetPoint)(JNIEnv *env, jclass clazz, jlong dochandle, jlong outlinehandle,
                                                                      jfloatArray pointArray)
{
    renderdocument_t *doc = (renderdocument_t*) (long) dochandle;
    fz_outline *outline = (fz_outline*) (uintptr_t) outlinehandle;

    if (!outline || !outline->uri || fz_is_external_link(doc->ctx, outline->uri))
    {
        return 0;
    }

    float x = 0.0f, y = 0.0f;
    jni_lock(doc->ctx);
    fz_resolve_link(doc->ctx, doc->document, outline->uri, &x, &y);
    jni_unlock(doc->ctx);

    jfloat *point = (*env)->GetPrimitiveArrayCritical(env, pointArray, 0);
    if (!point)
    {
        return 0;
    }
    point[0] = x;
    point[1] = y;
    (*env)->ReleasePrimitiveArrayCritical(env, pointArray, point, 0);

    return 0;
}

JNIEXPORT jlong JNICALL
JNI_FN(MuPdfOutline_getNext)(JNIEnv *env, jclass clazz, jlong outlinehandle)
{
    fz_outline *outline = (fz_outline*) (uintptr_t) outlinehandle;
    return (jlong)(uintptr_t)(outline?outline->next:NULL);
}

JNIEXPORT jlong JNICALL
JNI_FN(MuPdfOutline_getChild)(JNIEnv *env, jclass clazz, jlong outlinehandle)
{
    fz_outline *outline = (fz_outline*) (uintptr_t) outlinehandle;
    return (jlong)(uintptr_t)(outline?outline->down:NULL);
}
