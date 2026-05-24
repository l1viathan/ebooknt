package org.ebookdroid.ui.viewer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.support.v4.widget.DrawerLayout;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.ebookdroid.common.settings.SettingsManager;
import org.ebookdroid.common.settings.books.BookSettings;
import org.ebooknt.viewer.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared helper that wires up the "open books" side-drawer for
 * RecentActivity and BrowserActivity.  Tapping a book entry
 * brings the ViewerActivity to the front; tapping "关闭" closes
 * the drawer.
 */
public class OpenBooksDrawerHelper {

    public static final class Adapter extends BaseAdapter {
        private final Context ctx;
        private final List<String> items = new ArrayList<>();
        private String currentBookPath;

        public Adapter(Context ctx) {
            this.ctx = ctx;
            refresh();
        }

        public void refresh() {
            refresh(null);
        }

        public void refresh(final String currentBook) {
            this.currentBookPath = currentBook;
            items.clear();
            items.addAll(OpenBooksManager.get().getOpenBooks());
            notifyDataSetChanged();
        }

        @Override public int getCount()              { return items.size(); }
        @Override public Object getItem(int pos)     { return items.get(pos); }
        @Override public long getItemId(int pos)     { return pos; }
        @Override public boolean isEnabled(int pos)  { return true; }

        public String getBookPath(int pos) {
            return (pos >= 0 && pos < items.size()) ? items.get(pos) : null;
        }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            ImageView icon;
            TextView tv;
            TextView progressTv;
            if (convertView == null) {
                final float density = ctx.getResources().getDisplayMetrics().density;
                final int dp16 = (int) (16 * density + 0.5f);
                final int dp12 = (int) (12 * density + 0.5f);
                final int dp24 = (int) (24 * density + 0.5f);
                final int dp8 = (int) (8 * density + 0.5f);

                final LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp16, dp12, dp8, dp12);
                row.setLayoutParams(new AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    AbsListView.LayoutParams.WRAP_CONTENT));

                icon = new ImageView(ctx);
                final LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp24, dp24);
                iconLp.rightMargin = dp16;
                icon.setLayoutParams(iconLp);
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                row.addView(icon);

                tv = new TextView(ctx);
                tv.setTextSize(15);
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                final LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                tv.setLayoutParams(tvLp);
                row.addView(tv);

                progressTv = new TextView(ctx);
                progressTv.setTextSize(13);
                progressTv.setSingleLine(true);
                final LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                pLp.leftMargin = dp8;
                progressTv.setLayoutParams(pLp);
                row.addView(progressTv);

                convertView = row;
            } else {
                icon = (ImageView) ((ViewGroup) convertView).getChildAt(0);
                tv = (TextView) ((ViewGroup) convertView).getChildAt(1);
                progressTv = (TextView) ((ViewGroup) convertView).getChildAt(2);
            }
            final String path = items.get(pos);
            final boolean isCurrent = path.equals(currentBookPath);
            final boolean isActive = isCurrent || OpenBooksManager.get().isActive(path);

            if (isCurrent) {
                tv.setText("▸ " + OpenBooksManager.getDisplayTitle(path));
                tv.setTypeface(Typeface.DEFAULT_BOLD);
                tv.setTextColor(0xFFFFCC80);
                icon.setAlpha(1.0f);
            } else if (isActive) {
                tv.setText(OpenBooksManager.getDisplayTitle(path));
                tv.setTypeface(Typeface.DEFAULT_BOLD);
                tv.setTextColor(0xFFFFFFFF);
                icon.setAlpha(0.9f);
            } else {
                tv.setText(OpenBooksManager.getDisplayTitle(path));
                tv.setTypeface(Typeface.DEFAULT);
                tv.setTextColor(0xFFD0D0D0);
                icon.setAlpha(0.5f);
            }
            icon.setImageResource(R.drawable.viewer_menu_bookmark);

            final int pc = OpenBooksManager.get().getPageCount(path);
            if (pc > 0) {
                final BookSettings bs = SettingsManager.getBookSettings(path);
                final int cur = bs != null && bs.currentPage != null ? bs.currentPage.viewIndex + 1 : 0;
                progressTv.setText(cur + "/" + pc);
                progressTv.setTextColor(0xFFAAAAAA);
                progressTv.setVisibility(View.VISIBLE);
            } else {
                progressTv.setVisibility(View.GONE);
            }
            return convertView;
        }
    }

    /** Call from the host Activity's onCreate after setContentView. */
    public static Adapter setup(
            final Activity host,
            final DrawerLayout drawerLayout,
            final ListView drawerList) {

        final Adapter adapter = new Adapter(host);

        final View headerSpacer = new View(host);
        final View footerSpacer = new View(host);
        drawerList.addHeaderView(headerSpacer, null, false);
        drawerList.addFooterView(footerSpacer, null, false);
        drawerList.setAdapter(adapter);

        drawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                final String path = adapter.getBookPath(position - 1);
                if (path != null) {
                    final Intent intent = new Intent(
                        Intent.ACTION_VIEW, Uri.fromFile(new File(path)));
                    intent.setClass(host, ViewerActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    host.startActivity(intent);
                }
                drawerLayout.closeDrawers();
            }
        });

        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(final View drawerView) {
                centerItems(drawerList, headerSpacer, footerSpacer);
            }
        });

        return adapter;
    }

    private static void centerItems(final ListView list,
                                    final View header, final View footer) {
        list.post(new Runnable() {
            @Override
            public void run() {
                final int listH = list.getHeight();
                int contentH = 0;
                for (int i = 0; i < list.getChildCount(); i++) {
                    final View child = list.getChildAt(i);
                    if (child != null && child != header && child != footer) {
                        contentH += child.getHeight();
                    }
                }
                if (contentH <= 0 || listH <= 0) return;
                final int spacerH = Math.max(0, (listH - contentH) / 2);
                final AbsListView.LayoutParams lp = new AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT, spacerH);
                header.setLayoutParams(lp);
                footer.setLayoutParams(lp);
                list.invalidate();
            }
        });
    }

    public static class EdgeSwipeHelper {
        private final DrawerLayout drawerLayout;
        private Runnable onBeforeOpen;
        private float edgeSwipeStartX = -1;
        private float edgeSwipeStartY = -1;
        private float closeSwipeStartX = -1;
        private float closeSwipeStartY = -1;

        public EdgeSwipeHelper(DrawerLayout drawerLayout) {
            this.drawerLayout = drawerLayout;
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        }

        public void setOnBeforeOpen(Runnable r) { onBeforeOpen = r; }

        public void handleTouch(MotionEvent ev, Activity activity) {
            final float density = activity.getResources().getDisplayMetrics().density;
            final float edgeSize = 40 * density;
            final float minSwipe = 60 * density;
            final int viewHeight = activity.getWindow().getDecorView().getHeight();
            final float excludeZone = viewHeight * 0.40f;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (drawerLayout.isDrawerOpen(Gravity.START)) {
                        closeSwipeStartX = ev.getX();
                        closeSwipeStartY = ev.getY();
                        edgeSwipeStartX = -1;
                    } else if (ev.getX() < edgeSize
                            && ev.getY() > excludeZone
                            && ev.getY() < viewHeight - excludeZone) {
                        edgeSwipeStartX = ev.getX();
                        edgeSwipeStartY = ev.getY();
                        closeSwipeStartX = -1;
                    } else {
                        edgeSwipeStartX = -1;
                        closeSwipeStartX = -1;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (edgeSwipeStartX >= 0) {
                        final float dx = ev.getX() - edgeSwipeStartX;
                        final float dy = Math.abs(ev.getY() - edgeSwipeStartY);
                        if (dx > minSwipe && dy < dx) {
                            if (onBeforeOpen != null) onBeforeOpen.run();
                            drawerLayout.openDrawer(Gravity.START);
                            edgeSwipeStartX = -1;
                            ev.setAction(MotionEvent.ACTION_CANCEL);
                        }
                    } else if (closeSwipeStartX >= 0) {
                        final float dx = ev.getX() - closeSwipeStartX;
                        final float dy = Math.abs(ev.getY() - closeSwipeStartY);
                        if (dx < -minSwipe && dy < -dx) {
                            drawerLayout.closeDrawers();
                            closeSwipeStartX = -1;
                            ev.setAction(MotionEvent.ACTION_CANCEL);
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    edgeSwipeStartX = -1;
                    closeSwipeStartX = -1;
                    break;
            }
        }
    }
}
