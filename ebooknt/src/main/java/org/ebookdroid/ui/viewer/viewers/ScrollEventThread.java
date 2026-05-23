package org.ebookdroid.ui.viewer.viewers;

import org.ebookdroid.ui.viewer.IActivityController;
import org.ebookdroid.ui.viewer.IViewController;

import android.graphics.Rect;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.emdev.utils.MathUtils;
import org.emdev.utils.concurrent.Flag;

final class ScrollEventThread extends Thread {

    private static boolean mergeEvents = false;

    private final IActivityController base;

    private final GLView glView;

    private final Flag stop = new Flag();
    private volatile boolean paused = false;

    private final BlockingQueue<OnScrollEvent> queue = new LinkedBlockingQueue<OnScrollEvent>();

    private final ConcurrentLinkedQueue<OnScrollEvent> pool = new ConcurrentLinkedQueue<OnScrollEvent>();

    ScrollEventThread(final IActivityController base, GLView glView) {
        super("ScrollEventThread");
        this.base = base;
        this.glView = glView;
    }

    void setPaused(final boolean p) {
        paused = p;
        if (!p) {
            interrupt();
        }
    }

    @Override
    public void run() {
        while (!stop.get()) {
            try {
                if (paused) {
                    Thread.sleep(5000);
                    continue;
                }
                final OnScrollEvent event = queue.poll(1, TimeUnit.SECONDS);
                if (event == null) {
                    continue;
                }
                if (mergeEvents) {
                    for (OnScrollEvent event1 = queue.poll(); event1 != null; event1 = queue.poll()) {
                        event.reuse(event1.m_curX, event1.m_curY, event.m_oldX, event.m_oldY);
                        pool.add(event1);
                    }
                }
                process(event);
            } catch (final InterruptedException e) {
                Thread.interrupted();
            } catch (final Throwable th) {
                th.printStackTrace();
            }
        }
        // System.out.println("ScrollEventThread.run(): finished");
    }

    void finish() {
        stop.set();
    }

    void scrollTo(final int x, final int y) {
        final IViewController dc = base.getDocumentController();

        if (dc != null && base.getDocumentModel() != null) {
            final Rect l = dc.getScrollLimits();
            final int xx = MathUtils.adjust(x, l.left, l.right);
            final int yy = MathUtils.adjust(y, l.top, l.bottom);

            int oldX = glView.mGLScrollX;
            int oldY = glView.mGLScrollY;
            if (oldX != xx || oldY != yy) {
                glView.mGLScrollX = xx;
                glView.mGLScrollY = yy;
                glView.onScrollChanged(xx, yy, oldX, oldY);
            }
        }
    }

    void onScrollChanged(final int curX, final int curY, final int oldX, final int oldY) {
        OnScrollEvent event = pool.poll();
        if (event != null) {
            event.reuse(curX, curY, oldX, oldY);
        } else {
            event = new OnScrollEvent(curX, curY, oldX, oldY);
        }
        queue.offer(event);
    }

    private void process(final OnScrollEvent event) {
        // final long t1 = System.currentTimeMillis();
        try {
            final int dX = event.m_curX - event.m_oldX;
            final int dY = event.m_curY - event.m_oldY;

            base.getDocumentController().onScrollChanged(dX, dY);

        } catch (final Throwable th) {
            th.printStackTrace();
        } finally {
            pool.add(event);
            // final long t2 = System.currentTimeMillis();
            // System.out.println("ScrollEventThread.onScrollChanged(): " + (t2 - t1) + " ms, " + pool.size());
        }
    }

    final static class OnScrollEvent {

        int m_oldX;
        int m_curY;
        int m_curX;
        int m_oldY;

        OnScrollEvent(final int curX, final int curY, final int oldX, final int oldY) {
            reuse(curX, curY, oldX, oldY);
        }

        void reuse(final int curX, final int curY, final int oldX, final int oldY) {
            m_oldX = oldX;
            m_curY = curY;
            m_curX = curX;
            m_oldY = oldY;
        }

    }
}
