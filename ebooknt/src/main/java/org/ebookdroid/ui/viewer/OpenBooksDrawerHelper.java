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

    private static final Object QUIT_SENTINEL = new Object();

    public static final class Adapter extends BaseAdapter {
        private final Context ctx;
        private final List<Object> items = new ArrayList<>();

        public Adapter(Context ctx) {
            this.ctx = ctx;
            refresh();
        }

        public void refresh() {
            items.clear();
            for (final String path : OpenBooksManager.get().getOpenBooks()) {
                items.add(path);
            }
            items.add(QUIT_SENTINEL);
            notifyDataSetChanged();
        }

        @Override public int getCount()              { return items.size(); }
        @Override public Object getItem(int pos)     { return items.get(pos); }
        @Override public long getItemId(int pos)     { return pos; }
        @Override public boolean isEnabled(int pos)  { return true; }

        public String getBookPath(int pos) {
            final Object item = items.get(pos);
            return (item != QUIT_SENTINEL) ? (String) item : null;
        }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                final TextView tv = new TextView(ctx);
                tv.setPadding(48, 32, 48, 32);
                tv.setTextSize(15);
                tv.setLayoutParams(new AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    AbsListView.LayoutParams.WRAP_CONTENT));
                convertView = tv;
            }
            final TextView tv = (TextView) convertView;
            final Object item = items.get(pos);
            if (item == QUIT_SENTINEL) {
                tv.setText(R.string.drawer_action_close);
                tv.setTypeface(Typeface.DEFAULT);
                tv.setTextColor(0xFF212121);
            } else {
                tv.setText(OpenBooksManager.getDisplayTitle((String) item));
                tv.setTypeface(Typeface.DEFAULT);
                tv.setTextColor(0xFF1976D2);
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
}
