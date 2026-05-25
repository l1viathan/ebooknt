package org.ebookdroid.ui.viewer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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

import org.ebookdroid.common.settings.AppSettings;
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
            TextView titleTv;
            TextView progressTv;
            TextView outlineTv;
            if (convertView == null) {
                final float density = ctx.getResources().getDisplayMetrics().density;
                final int dp16 = (int) (16 * density + 0.5f);
                final int dp12 = (int) (12 * density + 0.5f);
                final int dp24 = (int) (24 * density + 0.5f);
                final int dp8 = (int) (8 * density + 0.5f);
                final int dp2 = (int) (2 * density + 0.5f);

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

                final LinearLayout textCol = new LinearLayout(ctx);
                textCol.setOrientation(LinearLayout.VERTICAL);
                final LinearLayout.LayoutParams textColLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                textCol.setLayoutParams(textColLp);

                final LinearLayout titleRow = new LinearLayout(ctx);
                titleRow.setOrientation(LinearLayout.HORIZONTAL);
                titleRow.setGravity(Gravity.CENTER_VERTICAL);

                titleTv = new TextView(ctx);
                titleTv.setTextSize(16);
                titleTv.setSingleLine(true);
                titleTv.setEllipsize(TextUtils.TruncateAt.END);
                titleTv.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
                titleRow.addView(titleTv);

                progressTv = new TextView(ctx);
                progressTv.setTextSize(12);
                progressTv.setSingleLine(true);
                final LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                pLp.leftMargin = dp8;
                progressTv.setLayoutParams(pLp);
                titleRow.addView(progressTv);

                textCol.addView(titleRow);

                outlineTv = new TextView(ctx);
                outlineTv.setTextSize(12);
                outlineTv.setSingleLine(true);
                outlineTv.setEllipsize(TextUtils.TruncateAt.END);
                final LinearLayout.LayoutParams olLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                olLp.topMargin = dp2;
                outlineTv.setLayoutParams(olLp);
                textCol.addView(outlineTv);

                row.addView(textCol);
                convertView = row;
            } else {
                final ViewGroup row = (ViewGroup) convertView;
                icon = (ImageView) row.getChildAt(0);
                final ViewGroup textCol = (ViewGroup) row.getChildAt(1);
                final ViewGroup titleRow = (ViewGroup) textCol.getChildAt(0);
                titleTv = (TextView) titleRow.getChildAt(0);
                progressTv = (TextView) titleRow.getChildAt(1);
                outlineTv = (TextView) textCol.getChildAt(1);
            }
            final String path = items.get(pos);
            final boolean isCurrent = path.equals(currentBookPath);
            final boolean isActive = isCurrent || OpenBooksManager.get().isActive(path);
            final boolean eink = AppSettings.current().einkMode;

            if (isCurrent) {
                titleTv.setText("▸ " + OpenBooksManager.getDisplayTitle(path));
                titleTv.setTypeface(Typeface.DEFAULT_BOLD);
                titleTv.setTextColor(eink ? 0xFF8B4513 : 0xFFFFCC80);
                icon.setAlpha(1.0f);
            } else if (isActive) {
                titleTv.setText(OpenBooksManager.getDisplayTitle(path));
                titleTv.setTypeface(Typeface.DEFAULT_BOLD);
                titleTv.setTextColor(eink ? 0xFF000000 : 0xFFFFFFFF);
                icon.setAlpha(0.9f);
            } else {
                titleTv.setText(OpenBooksManager.getDisplayTitle(path));
                titleTv.setTypeface(Typeface.DEFAULT);
                titleTv.setTextColor(eink ? 0xFF505050 : 0xFFD0D0D0);
                icon.setAlpha(0.5f);
            }
            icon.setImageResource(R.drawable.viewer_menu_bookmark);

            final String olLabel = OpenBooksManager.get().getOutlineLabel(path);
            if (olLabel != null) {
                outlineTv.setText(olLabel);
                outlineTv.setTextColor(eink ? 0xFF777777 : 0xFFAAAAAA);
                outlineTv.setVisibility(View.VISIBLE);
            } else {
                outlineTv.setVisibility(View.GONE);
            }

            final int pc = OpenBooksManager.get().getPageCount(path);
            if (pc > 0) {
                final BookSettings bs = SettingsManager.getBookSettings(path);
                final int cur = bs != null && bs.currentPage != null ? bs.currentPage.viewIndex + 1 : 0;
                progressTv.setText(cur + "/" + pc);
                progressTv.setTextColor(eink ? 0xFF777777 : 0xFFAAAAAA);
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

        if (AppSettings.current().einkMode) {
            drawerList.setBackgroundColor(0xFEF0F0F0);
            drawerList.setDivider(new ColorDrawable(0xFFCCCCCC));
            drawerList.setDividerHeight(1);
        }

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
                if (listH <= 0) return;
                int itemH = 0;
                for (int i = 0; i < list.getChildCount(); i++) {
                    final View child = list.getChildAt(i);
                    if (child != null && child != header && child != footer) {
                        itemH = child.getHeight();
                        break;
                    }
                }
                if (itemH <= 0) return;
                final int totalItems = list.getAdapter().getCount() - 2;
                final int contentH = totalItems * itemH;
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
        private boolean intercepting;
        private boolean closeIntercepting;

        public EdgeSwipeHelper(DrawerLayout drawerLayout) {
            this.drawerLayout = drawerLayout;
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        }

        public void setOnBeforeOpen(Runnable r) { onBeforeOpen = r; }

        public boolean handleTouch(MotionEvent ev, Activity activity) {
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
                    intercepting = false;
                    closeIntercepting = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (edgeSwipeStartX >= 0) {
                        final float dx = ev.getX() - edgeSwipeStartX;
                        final float dy = Math.abs(ev.getY() - edgeSwipeStartY);
                        if (dy > dx && dy > edgeSize) {
                            edgeSwipeStartX = -1;
                            intercepting = false;
                            return false;
                        }
                        if (dx > minSwipe && dy < dx) {
                            if (onBeforeOpen != null) onBeforeOpen.run();
                            drawerLayout.openDrawer(Gravity.START);
                            edgeSwipeStartX = -1;
                        }
                        intercepting = true;
                        return true;
                    } else if (closeSwipeStartX >= 0) {
                        final float dx = ev.getX() - closeSwipeStartX;
                        final float dy = Math.abs(ev.getY() - closeSwipeStartY);
                        if (dy > edgeSize && dy > Math.abs(dx)) {
                            closeSwipeStartX = -1;
                            closeIntercepting = false;
                            return false;
                        }
                        if (Math.abs(dx) > edgeSize) {
                            closeIntercepting = true;
                        }
                        if (dx < -minSwipe && dy < -dx) {
                            drawerLayout.closeDrawers();
                            closeSwipeStartX = -1;
                        }
                        return closeIntercepting;
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    final boolean was = intercepting || closeIntercepting;
                    edgeSwipeStartX = -1;
                    closeSwipeStartX = -1;
                    intercepting = false;
                    closeIntercepting = false;
                    return was;
            }
            return false;
        }
    }
}
