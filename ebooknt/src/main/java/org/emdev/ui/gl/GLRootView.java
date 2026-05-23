/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.emdev.ui.gl;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Process;
import android.util.AttributeSet;
import android.view.TextureView;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

import org.emdev.common.log.LogContext;
import org.emdev.common.log.LogManager;

public class GLRootView extends TextureView implements TextureView.SurfaceTextureListener {

    private static final LogContext LCTX = LogManager.root().lctx("GLRootView");

    public static final int FLAG_INITIALIZED = 1;
    public static final int FLAG_NEED_LAYOUT = 2;

    protected GL11 mGL;
    protected GLCanvas mCanvas;

    protected int mFlags = FLAG_NEED_LAYOUT;
    protected volatile boolean mRenderRequested = false;

    protected final ReentrantLock mRenderLock = new ReentrantLock();
    protected final Condition mFreezeCondition = mRenderLock.newCondition();
    protected boolean mFreeze;

    protected long mLastDrawFinishTime;
    protected boolean mInDownState = false;
    protected boolean mFirstDraw = true;

    private GLThread mGLThread;

    public GLRootView(final Context context) {
        this(context, null);
    }

    public GLRootView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mFlags |= FLAG_INITIALIZED;
        setOpaque(true);
        setSurfaceTextureListener(this);
    }

    @Override
    public void onSurfaceTextureAvailable(final SurfaceTexture surface, final int width, final int height) {
        LCTX.i("onSurfaceTextureAvailable: " + width + "x" + height);
        mGLThread = new GLThread(surface, width, height);
        mGLThread.start();
    }

    @Override
    public void onSurfaceTextureSizeChanged(final SurfaceTexture surface, final int width, final int height) {
        LCTX.i("onSurfaceTextureSizeChanged: " + width + "x" + height);
        if (mGLThread != null) {
            mGLThread.onWindowResize(width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(final SurfaceTexture surface) {
        LCTX.i("onSurfaceTextureDestroyed");
        unfreeze();
        if (mGLThread != null) {
            mGLThread.finish();
            try {
                mGLThread.join();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            mGLThread = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(final SurfaceTexture surface) {
    }

    public void requestRender() {
        if (mRenderRequested) {
            return;
        }
        mRenderRequested = true;
        if (mGLThread != null) {
            mGLThread.requestRender();
        }
    }

    public void requestLayoutContentPane() {
        mRenderLock.lock();
        try {
            if ((mFlags & FLAG_NEED_LAYOUT) != 0) {
                return;
            }

            if ((mFlags & FLAG_INITIALIZED) == 0) {
                return;
            }

            mFlags |= FLAG_NEED_LAYOUT;
            requestRender();
        } finally {
            mRenderLock.unlock();
        }
    }

    private void layoutContentPane() {
        mFlags &= ~FLAG_NEED_LAYOUT;

        final int w = getWidth();
        final int h = getHeight();

        LCTX.i("layout content pane " + w + "x" + h);
    }

    @Override
    protected void onLayout(final boolean changed, final int left, final int top, final int right, final int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            requestLayoutContentPane();
        }
    }

    public void onSurfaceCreated(final GL10 gl1, final EGLConfig config) {
        final GL11 gl = (GL11) gl1;
        if (mGL != null) {
            LCTX.i("GLObject has changed from " + mGL + " to " + gl);
        }
        mRenderLock.lock();
        try {
            mGL = gl;
            mCanvas = new GLCanvasImpl(gl);
            BasicTexture.invalidateAllTextures();
        } finally {
            mRenderLock.unlock();
        }
    }

    public void onSurfaceChanged(final GL10 gl1, final int width, final int height) {
        LCTX.i("onSurfaceChanged: " + width + "x" + height);
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
        if (mCanvas != null) {
            mCanvas.setSize(width, height);
        }
        mRenderRequested = false;
        requestRender();
    }

    public void onDrawFrame(final GL10 gl) {
        mRenderLock.lock();

        while (mFreeze) {
            mFreezeCondition.awaitUninterruptibly();
        }

        try {
            onDrawFrameLocked(gl);
        } finally {
            mRenderLock.unlock();
        }
    }

    protected void onDrawFrameLocked(final GL10 gl) {

        mCanvas.deleteRecycledResources();

        UploadedTexture.resetUploadLimit();

        mRenderRequested = false;

        if ((mFlags & FLAG_NEED_LAYOUT) != 0) {
            layoutContentPane();
        }

        mCanvas.save(GLCanvas.SAVE_FLAG_ALL);

        draw(this.mCanvas);

        mCanvas.restore();

        if (UploadedTexture.uploadLimitReached()) {
            requestRender();
        }
    }

    protected void draw(final GLCanvas canvas) {
    }

    public void lockRenderThread() {
        mRenderLock.lock();
    }

    public void unlockRenderThread() {
        mRenderLock.unlock();
    }

    public void onPause() {
        unfreeze();
        if (mGLThread != null) {
            mGLThread.onPause();
        }
    }

    public void onResume() {
        if (mGLThread != null) {
            mGLThread.onResume();
        }
    }

    public void freeze() {
        mRenderLock.lock();
        mFreeze = true;
        mRenderLock.unlock();
    }

    public void unfreeze() {
        mRenderLock.lock();
        mFreeze = false;
        mFreezeCondition.signalAll();
        mRenderLock.unlock();
    }

    @Override
    protected void onDetachedFromWindow() {
        unfreeze();
        super.onDetachedFromWindow();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            unfreeze();
        } finally {
            super.finalize();
        }
    }

    private class GLThread extends Thread {

        private final SurfaceTexture mSurfaceTexture;
        private volatile boolean mRunning = true;
        private volatile boolean mPaused = false;
        private volatile boolean mSizeChanged = false;
        private int mWidth;
        private int mHeight;

        private EGL10 mEgl;
        private EGLDisplay mEglDisplay;
        private EGLConfig mEglConfig;
        private EGLContext mEglContext;
        private EGLSurface mEglSurface;

        GLThread(final SurfaceTexture surfaceTexture, final int width, final int height) {
            super("GLThread");
            this.mSurfaceTexture = surfaceTexture;
            this.mWidth = width;
            this.mHeight = height;
        }

        synchronized void requestRender() {
            notify();
        }

        synchronized void onWindowResize(final int w, final int h) {
            mWidth = w;
            mHeight = h;
            mSizeChanged = true;
            notify();
        }

        synchronized void onPause() {
            mPaused = true;
        }

        synchronized void onResume() {
            mPaused = false;
            notify();
        }

        synchronized void finish() {
            mRunning = false;
            notify();
        }

        @Override
        public void run() {
            initEGL();

            final GL10 gl10 = (GL10) mEglContext.getGL();

            GLRootView.this.onSurfaceCreated(gl10, mEglConfig);
            GLRootView.this.onSurfaceChanged(gl10, mWidth, mHeight);

            while (mRunning) {
                synchronized (this) {
                    while (mRunning && (mPaused || (!mRenderRequested && !mSizeChanged))) {
                        try {
                            wait();
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    if (!mRunning) {
                        break;
                    }

                    if (mSizeChanged) {
                        mSizeChanged = false;
                        GLRootView.this.onSurfaceChanged(gl10, mWidth, mHeight);
                    }
                }

                GLRootView.this.onDrawFrame(gl10);

                if (!mEgl.eglSwapBuffers(mEglDisplay, mEglSurface)) {
                    LCTX.e("eglSwapBuffers failed: " + mEgl.eglGetError());
                    break;
                }
            }

            destroyEGL();
        }

        private void initEGL() {
            mEgl = (EGL10) EGLContext.getEGL();
            mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

            final int[] version = new int[2];
            if (!mEgl.eglInitialize(mEglDisplay, version)) {
                throw new RuntimeException("eglInitialize failed");
            }

            final BaseEGLConfigChooser chooser = new BaseEGLConfigChooser();
            mEglConfig = chooser.chooseConfig(mEgl, mEglDisplay);

            final int[] contextAttribs = { EGL10.EGL_NONE };
            mEglContext = mEgl.eglCreateContext(mEglDisplay, mEglConfig,
                    EGL10.EGL_NO_CONTEXT, contextAttribs);

            mEglSurface = mEgl.eglCreateWindowSurface(mEglDisplay, mEglConfig,
                    mSurfaceTexture, null);

            if (!mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
                throw new RuntimeException("eglMakeCurrent failed: " + mEgl.eglGetError());
            }
        }

        private void destroyEGL() {
            if (mEgl != null) {
                mEgl.eglMakeCurrent(mEglDisplay, EGL10.EGL_NO_SURFACE,
                        EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                if (mEglSurface != null) {
                    mEgl.eglDestroySurface(mEglDisplay, mEglSurface);
                }
                if (mEglContext != null) {
                    mEgl.eglDestroyContext(mEglDisplay, mEglContext);
                }
                mEgl.eglTerminate(mEglDisplay);
            }
        }
    }
}
