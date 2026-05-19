package org.ebookdroid.ui.library.views;

import org.ebookdroid.ui.library.IBrowserActivity;
import org.ebookdroid.ui.library.adapters.BookNode;
import org.ebookdroid.ui.library.adapters.SearchResultsAdapter;

import android.net.Uri;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.io.File;

public class SearchResultsView extends ListView implements AdapterView.OnItemClickListener {

    private final IBrowserActivity base;
    private final SearchResultsAdapter adapter;

    public SearchResultsView(final IBrowserActivity base, final SearchResultsAdapter adapter) {
        super(base.getContext());
        this.base = base;
        this.adapter = adapter;
        setAdapter(adapter);
        setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_LOW);
        setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
        final BookNode node = adapter.getItem(position);
        base.showDocument(Uri.fromFile(new File(node.path)), null);
    }
}
