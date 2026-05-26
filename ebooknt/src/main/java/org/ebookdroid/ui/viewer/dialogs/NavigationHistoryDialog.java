package org.ebookdroid.ui.viewer.dialogs;

import org.ebooknt.viewer.R;
import org.ebookdroid.core.NavigationHistoryTree;
import org.ebookdroid.core.NavigationHistoryTree.FlatEntry;
import org.ebookdroid.core.NavigationHistoryTree.NavigationType;
import org.ebookdroid.core.NavigationHistoryTree.Node;
import org.ebookdroid.core.codec.OutlineLink;
import org.ebookdroid.ui.viewer.IActivityController;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

import org.emdev.utils.LayoutUtils;

public class NavigationHistoryDialog extends Dialog implements AdapterView.OnItemClickListener {

    private final IActivityController base;
    private final NavigationHistoryTree tree;
    private List<FlatEntry> entries;

    public NavigationHistoryDialog(final IActivityController base, final NavigationHistoryTree tree) {
        super(base.getContext());
        this.base = base;
        this.tree = tree;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setCanceledOnTouchOutside(true);
        setTitle(R.string.menu_nav_history);

        entries = tree.flatten();

        final ListView listView = new ListView(getContext());
        listView.setAdapter(new HistoryAdapter());
        listView.setOnItemClickListener(this);
        listView.setDivider(null);
        listView.setFastScrollEnabled(false);

        setContentView(listView);
        LayoutUtils.maximizeWindow(getWindow());

        final Node current = tree.getCurrent();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).node == current) {
                listView.setSelection(i);
                break;
            }
        }
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
        if (position < 0 || position >= entries.size()) {
            return;
        }
        final Node node = entries.get(position).node;
        tree.goToNode(node);
        base.getDocumentController().goToPage(node.page, 0, 0);
        dismiss();
    }

    private String getTypeLabel(final NavigationType type) {
        if (type == null) {
            return "○";
        }
        switch (type) {
            case LINK:
                return "→";
            case OUTLINE:
                return "≡";
            case SEARCH:
                return "⌕";
            case GOTO:
                return "#";
            case BOOKMARK:
                return "★";
            default:
                return "?";
        }
    }

    private String getOutlineLabel(final int docPageIndex) {
        final List<OutlineLink> outline = base.getDocumentModel().decodeService.getOutline();
        if (outline == null || outline.isEmpty()) {
            return null;
        }
        String best = null;
        for (final OutlineLink link : outline) {
            if (link.targetPage - 1 <= docPageIndex && link.targetPage >= 1) {
                best = link.title;
            } else if (link.targetPage - 1 > docPageIndex) {
                break;
            }
        }
        return best;
    }

    private class HistoryAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public FlatEntry getItem(final int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(final int position) {
            return position;
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            final FlatEntry entry = entries.get(position);
            final Node node = entry.node;
            final boolean isCurrent = (node == tree.getCurrent());

            final int offset = base.getBookSettings() != null ? base.getBookSettings().firstPageOffset : 1;
            final int displayPage = node.page + offset;

            final StringBuilder sb = new StringBuilder();
            sb.append(getTypeLabel(node.type));
            sb.append(" p.").append(displayPage);

            if (node.detail != null && node.detail.length() > 0) {
                sb.append(" — ").append(node.detail);
            } else if (node.type == null) {
                final String chapterLabel = getOutlineLabel(node.page);
                if (chapterLabel != null) {
                    sb.append(" — ").append(chapterLabel);
                }
            }

            TextView tv;
            if (convertView instanceof TextView) {
                tv = (TextView) convertView;
            } else {
                tv = new TextView(getContext());
                tv.setTextSize(16);
                final int vpad = dp(6);
                tv.setPadding(0, vpad, dp(12), vpad);
            }

            tv.setText(sb.toString());
            tv.setPadding(dp(12 + entry.depth * 20), tv.getPaddingTop(), tv.getPaddingRight(), tv.getPaddingBottom());

            if (isCurrent) {
                tv.setTypeface(null, Typeface.BOLD);
                tv.setTextColor(0xFF1976D2);
            } else {
                tv.setTypeface(null, Typeface.NORMAL);
                tv.setTextColor(tv.getResources().getColor(android.R.color.primary_text_light));
            }

            return tv;
        }

        private int dp(final int value) {
            return (int) (value * getContext().getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}
