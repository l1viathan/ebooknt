package org.ebookdroid.ui.viewer.dialogs;

import org.ebooknt.viewer.R;
import org.ebookdroid.common.settings.books.BookSettings;
import org.ebookdroid.core.codec.OutlineLink;
import org.ebookdroid.ui.viewer.IActivityController;
import org.ebookdroid.ui.viewer.adapters.OutlineAdapter;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListView;

import java.util.List;

import org.emdev.ui.actions.ActionController;
import org.emdev.utils.LayoutUtils;

public class OutlineDialog extends Dialog implements OnItemClickListener {

    final IActivityController base;
    final List<OutlineLink> outline;
    final ActionController<OutlineDialog> actions;

    public OutlineDialog(final IActivityController base, final List<OutlineLink> outline) {
        super(base.getContext());
        this.base = base;
        this.outline = outline;
        this.actions = new ActionController<OutlineDialog>(base, this);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.outline_title);

        final View contentView = base.getActivity().getLayoutInflater().inflate(R.layout.outline, null);
        setContentView(contentView);

        LayoutUtils.maximizeWindow(getWindow());

        final ListView listView = (ListView) contentView.findViewById(R.id.outline_list);
        final EditText searchEdit = (EditText) contentView.findViewById(R.id.outline_search);

        final BookSettings bs = base.getBookSettings();
        OutlineLink current = null;
        if (bs != null) {
            final int currentIndex = bs.currentPage.docIndex;
            for (final OutlineLink item : outline) {
                int targetIndex = item.targetPage - 1;
                if (targetIndex <= currentIndex) {
                    if (targetIndex >= 0) {
                        current = item;
                    }
                } else {
                    break;
                }
            }
        }

        final OutlineAdapter adapter = new OutlineAdapter(getContext(), base, outline, current);

        listView.setAdapter(adapter);
        listView.setOnItemClickListener(this);

        if (current != null) {
            int pos = adapter.getItemPosition(current);
            if (pos != -1) {
                listView.setSelection(pos);
            }
        }

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                adapter.setFilter(s.toString());
            }
        });
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
        this.dismiss();
        actions.getOrCreateAction(R.id.actions_gotoOutlineItem).onItemClick(parent, view, position, id);
    }
}
