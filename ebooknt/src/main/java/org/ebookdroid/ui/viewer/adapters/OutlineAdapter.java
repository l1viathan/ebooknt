package org.ebookdroid.ui.viewer.adapters;

import org.ebooknt.viewer.R;
import org.ebookdroid.common.settings.books.BookSettings;
import org.ebookdroid.common.settings.types.PageType;
import org.ebookdroid.core.Page;
import org.ebookdroid.core.codec.OutlineLink;
import org.ebookdroid.core.models.DocumentModel;
import org.ebookdroid.ui.viewer.IActivityController;

import org.ebookdroid.common.settings.AppSettings;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;

import java.util.List;

public class OutlineAdapter extends BaseAdapter {

    private int spaceWidth;

    private final Drawable background;
    private final Drawable selected;

    private final VoidListener voidListener = new VoidListener();
    private final ItemListener itemListener = new ItemListener();
    private final CollapseListener collapseListener = new CollapseListener();

    private final Context context;
    private final OutlineLink[] objects;
    private final String[] lowerTitles;
    private final int[] pageIndexes;
    private final OutlineItemState[] states;
    private final boolean[] filtered;
    private final SparseIntArray mapping = new SparseIntArray();
    private final int currentId;
    private final int offset;
    private String filterQuery = "";

    public OutlineAdapter(final Context context, final IActivityController base, final List<OutlineLink> objects,
            final OutlineLink current) {

        this.context = context;

        final BookSettings bs = base.getBookSettings();
        final DocumentModel model = base.getDocumentModel();
        final Resources resources = context.getResources();

        background = resources.getDrawable(R.drawable.viewer_outline_item_background);
        selected = resources.getDrawable(R.drawable.viewer_outline_item_background_selected);

        this.offset = bs != null ? bs.firstPageOffset : 1;
        this.objects = objects.toArray(new OutlineLink[objects.size()]);
        this.lowerTitles = new String[this.objects.length];
        this.pageIndexes = new int[this.objects.length];
        this.states = new OutlineItemState[this.objects.length];
        this.filtered = new boolean[this.objects.length];

        final Page[] allPages = model.getPages();
        final SparseArray<Page> docIndexMap = new SparseArray<Page>(allPages.length);
        for (final Page p : allPages) {
            docIndexMap.put(p.index.docIndex, p);
        }

        boolean treeFound = false;
        for (int i = 0; i < this.objects.length; i++) {
            mapping.put(i, i);
            lowerTitles[i] = this.objects[i].title.toLowerCase();
            final int next = i + 1;
            if (next < this.objects.length && this.objects[i].level < this.objects[next].level) {
                states[i] = OutlineItemState.COLLAPSED;
                treeFound = true;
            } else {
                states[i] = OutlineItemState.LEAF;
            }

            final int docIndex = this.objects[i].targetPage - 1;
            Page target = docIndex >= 0 ? docIndexMap.get(docIndex) : null;
            if (target != null && bs != null && this.objects[i].targetRect != null
                    && target.type == PageType.LEFT_PAGE && this.objects[i].targetRect.left >= 0.5f) {
                target = model.getPageObject(target.index.viewIndex + (bs.splitRTL ? -1 : 1));
            }
            this.pageIndexes[i] = target != null ? target.index.viewIndex + 1 : -1;
        }

        currentId = current != null ? objects.indexOf(current) : -1;

        if (treeFound) {
            for (int parent = getParentId(currentId); parent != -1; parent = getParentId(parent)) {
                states[parent] = OutlineItemState.EXPANDED;
            }
            rebuild();
            if (getCount() == 1 && states[0] == OutlineItemState.COLLAPSED) {
                states[0] = OutlineItemState.EXPANDED;
                rebuild();
            }
        }
    }

    public void setFilter(String query) {
        this.filterQuery = query != null ? query.toLowerCase().trim() : "";
        applyFilter();
        rebuild();
        notifyDataSetChanged();
    }

    private void applyFilter() {
        if (filterQuery.isEmpty()) {
            for (int i = 0; i < filtered.length; i++) {
                filtered[i] = false;
            }
            return;
        }
        for (int i = 0; i < filtered.length; i++) {
            filtered[i] = true;
        }
        for (int i = 0; i < objects.length; i++) {
            if (lowerTitles[i].contains(filterQuery)) {
                filtered[i] = false;
                for (int p = getParentId(i); p != -1; p = getParentId(p)) {
                    if (!filtered[p]) break;
                    filtered[p] = false;
                }
            }
        }
    }

    public int getParentId(final int id) {
        if (0 <= id && id < objects.length) {
            final int level = objects[id].level;
            for (int i = id - 1; i >= 0; i--) {
                if (objects[i].level < level) {
                    return i;
                }
            }
        }
        return -1;
    }

    protected void rebuild() {
        mapping.clear();
        int pos = 0;
        int level = Integer.MAX_VALUE;
        final boolean isFiltering = !filterQuery.isEmpty();
        for (int cid = 0; cid < objects.length; cid++) {
            if (filtered[cid]) continue;
            if (objects[cid].level <= level) {
                mapping.put(pos++, cid);
                if (!isFiltering && states[cid] == OutlineItemState.COLLAPSED) {
                    level = objects[cid].level;
                } else {
                    level = Integer.MAX_VALUE;
                }
            }
        }
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public int getCount() {
        return mapping.size();
    }

    @Override
    public OutlineLink getItem(final int position) {
        final int id = mapping.get(position, -1);
        return id >= 0 && id < objects.length ? objects[id] : null;
    }

    public String getPageIndex(final int position) {
        final int id = mapping.get(position, -1);
        final int index = id >= 0 && id < pageIndexes.length ? pageIndexes[id] : -1;
        return index > 0 ? "" + (index - 1 + offset) : "";
    }

    @Override
    public long getItemId(final int position) {
        return mapping.get(position, -1);
    }

    public int getItemPosition(final OutlineLink item) {
        for (int i = 0, n = getCount(); i < n; i++) {
            if (item == getItem(i)) {
                return i;
            }
        }
        return -1;
    }

    public int getItemId(final OutlineLink item) {
        for (int i = 0, n = objects.length; i < n; i++) {
            if (item == objects[i]) {
                return i;
            }
        }
        return -1;
    }

    private static final class ViewHolder {
        TextView title;
        ImageView btn;
        View space;
        TextView pageIndex;
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        final int id = (int) getItemId(position);
        View container;
        ViewHolder holder;
        if (convertView == null) {
            container = LayoutInflater.from(context).inflate(R.layout.outline_item, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) container.findViewById(R.id.outline_title);
            holder.btn = (ImageView) container.findViewById(R.id.outline_collapse);
            holder.space = container.findViewById(R.id.outline_space);
            holder.pageIndex = (TextView) container.findViewById(R.id.outline_pageindex);
            final RelativeLayout.LayoutParams btnParams = (LayoutParams) holder.btn.getLayoutParams();
            spaceWidth = btnParams.width / 2;
            container.setOnClickListener(voidListener);
            holder.title.setOnClickListener(itemListener);
            container.setTag(holder);
        } else {
            container = convertView;
            holder = (ViewHolder) container.getTag();
        }

        final OutlineLink item = getItem(position);
        holder.title.setText(item.title.trim());
        holder.title.setTag(position);
        holder.btn.setTag(position);
        holder.pageIndex.setText(getPageIndex(position));

        container.setBackgroundDrawable(id == currentId ? this.selected : this.background);

        final RelativeLayout.LayoutParams sl = (LayoutParams) holder.space.getLayoutParams();
        sl.width = item.level * spaceWidth;
        holder.space.setLayoutParams(sl);

        if (states[id] == OutlineItemState.LEAF) {
            holder.btn.setOnClickListener(voidListener);
            holder.btn.setImageDrawable(null);
        } else {
            holder.btn.setOnClickListener(collapseListener);
            holder.btn.setImageResource(states[id] == OutlineItemState.EXPANDED ? R.drawable.viewer_outline_item_expanded
                    : R.drawable.viewer_outline_item_collapsed);
            if (AppSettings.current().einkMode) {
                holder.btn.setColorFilter(0xFF333333, PorterDuff.Mode.SRC_IN);
            }
        }

        return container;
    }

    private static enum OutlineItemState {
        LEAF, EXPANDED, COLLAPSED;
    }

    private final class CollapseListener implements OnClickListener {

        @Override
        public void onClick(final View v) {
            {
                final int position = ((Integer) v.getTag()).intValue();
                final int id = (int) getItemId(position);
                final OutlineItemState newState = states[id] == OutlineItemState.EXPANDED ? OutlineItemState.COLLAPSED
                        : OutlineItemState.EXPANDED;
                states[id] = newState;
            }
            rebuild();

            v.post(new Runnable() {

                @Override
                public void run() {
                    notifyDataSetChanged();
                }
            });
        }
    }

    private static final class ItemListener implements OnClickListener {

        @Override
        public void onClick(final View v) {
            for (ViewParent p = v.getParent(); p != null; p = p.getParent()) {
                if (p instanceof ListView) {
                    final ListView list = (ListView) p;
                    final OnItemClickListener l = list.getOnItemClickListener();
                    if (l != null) {
                        l.onItemClick(list, v, ((Integer) v.getTag()).intValue(), 0);
                    }
                    return;
                }
            }

        }
    }

    private static final class VoidListener implements OnClickListener {

        @Override
        public void onClick(final View v) {
        }
    }
}
