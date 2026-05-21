package org.ebookdroid.ui.viewer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.support.v4.widget.DrawerLayout;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

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
            if (convertView == null) {
                final float density = ctx.getResources().getDisplayMetrics().density;
                final int dp16 = (int) (16 * density + 0.5f);
                final int dp12 = (int) (12 * density + 0.5f);
                final int dp24 = (int) (24 * density + 0.5f);

                final LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp16, dp12, dp16, dp12);
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
                row.addView(tv);

                convertView = row;
            } else {
                icon = (ImageView) ((ViewGroup) convertView).getChildAt(0);
                tv = (TextView) ((ViewGroup) convertView).getChildAt(1);
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
}
