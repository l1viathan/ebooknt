package org.ebookdroid.ui.library.adapters;

import org.ebooknt.viewer.R;
import org.ebookdroid.common.settings.LibSettings;

import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;

import org.emdev.ui.adapters.BaseViewHolder;
import org.emdev.utils.FileUtils;

public class SearchResultsAdapter extends BaseAdapter {

    private final BookShelfAdapter shelf;

    public SearchResultsAdapter(final BookShelfAdapter shelf) {
        this.shelf = shelf;
        shelf.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                notifyDataSetChanged();
            }
            @Override
            public void onInvalidated() {
                notifyDataSetInvalidated();
            }
        });
    }

    @Override
    public int getCount() {
        return shelf.getCount();
    }

    @Override
    public BookNode getItem(final int position) {
        return (BookNode) shelf.getItem(position);
    }

    @Override
    public long getItemId(final int position) {
        return position;
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        final ViewHolder holder = BaseViewHolder.getOrCreateViewHolder(ViewHolder.class, R.layout.browseritem,
                convertView, parent);

        final BookNode node = getItem(position);
        final File file = new File(node.path);

        holder.name.setText(file.getName());
        holder.image.setImageResource(R.drawable.recent_item_book);
        holder.info.setText(relativePath(node.path));
        holder.info.setSelected(true);
        holder.fileSize.setText(FileUtils.getFileSize(file.length()));

        return holder.getView();
    }

    private static String relativePath(final String filePath) {
        final String libPath = LibSettings.current().libraryPath;
        if (libPath != null && !libPath.isEmpty()) {
            final String prefix = libPath.endsWith("/") ? libPath : libPath + "/";
            if (filePath.startsWith(prefix)) {
                return filePath.substring(prefix.length());
            }
        }
        return filePath;
    }

    public static class ViewHolder extends BaseViewHolder {

        TextView name;
        ImageView image;
        TextView info;
        TextView fileSize;

        @Override
        public void init(final View convertView) {
            super.init(convertView);
            this.name = (TextView) convertView.findViewById(R.id.browserItemText);
            this.image = (ImageView) convertView.findViewById(R.id.browserItemIcon);
            this.info = (TextView) convertView.findViewById(R.id.browserItemInfo);
            this.fileSize = (TextView) convertView.findViewById(R.id.browserItemfileSize);
        }
    }
}
