package com.campus.lostfound;

import android.view.View;
import android.widget.AdapterView;

public class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
    public interface Callback {
        void onItemSelected(int position);
    }

    private final Callback callback;

    private SimpleItemSelectedListener(Callback callback) {
        this.callback = callback;
    }

    public static SimpleItemSelectedListener create(Callback callback) {
        return new SimpleItemSelectedListener(callback);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        callback.onItemSelected(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // No-op
    }
}
