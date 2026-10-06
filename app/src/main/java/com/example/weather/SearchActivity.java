package com.example.weather;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Location search: live city results while typing, recent searches, and "use current location". */
public class SearchActivity extends AppCompatActivity {

    private Prefs prefs;
    private EditText etSearch;
    private TextView tvListTitle, tvEmpty;
    private ProgressBar progress;
    private PlaceAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable pending;
    private int queryId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        prefs = new Prefs(this);

        etSearch = findViewById(R.id.etSearch);
        tvListTitle = findViewById(R.id.tvListTitle);
        tvEmpty = findViewById(R.id.tvEmpty);
        progress = findViewById(R.id.searchProgress);
        ListView lv = findViewById(R.id.lvPlaces);
        adapter = new PlaceAdapter(this);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((parent, view, pos, id) -> pick(adapter.getItem(pos)));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnGps).setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra("gps", true));
            finish();
        });

        // Search as you type (waits 350 ms after the last key press)
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { scheduleSearch(s.toString().trim()); }
        });
        etSearch.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                main.removeCallbacks(pending);
                String q = etSearch.getText().toString().trim();
                if (!q.isEmpty()) search(q);
                return true;
            }
            return false;
        });

        showRecents();
    }

    private void scheduleSearch(String q) {
        if (pending != null) main.removeCallbacks(pending);
        if (q.length() < 2) {
            showRecents();
            return;
        }
        pending = () -> search(q);
        main.postDelayed(pending, 350);
    }

    private void search(String q) {
        final int id = ++queryId;
        progress.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                List<Place> results = WeatherRepository.searchPlaces(q);
                main.post(() -> {
                    if (id != queryId) return; // a newer search started
                    progress.setVisibility(View.GONE);
                    tvListTitle.setText(R.string.results);
                    show(results, "No cities found for \"" + q + "\"");
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (id != queryId) return;
                    progress.setVisibility(View.GONE);
                    show(java.util.Collections.emptyList(), "Couldn't search. Check your internet connection.");
                });
            }
        });
    }

    private void showRecents() {
        queryId++;
        progress.setVisibility(View.GONE);
        tvListTitle.setText(R.string.recent);
        show(prefs.recents(), "Search for any city to see its weather.");
    }

    private void show(List<Place> places, String emptyText) {
        adapter.clear();
        adapter.addAll(places);
        tvEmpty.setText(emptyText);
        tvEmpty.setVisibility(places.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void pick(Place p) {
        prefs.addRecent(p);
        setResult(RESULT_OK, new Intent()
                .putExtra("name", p.name).putExtra("region", p.region)
                .putExtra("lat", p.lat).putExtra("lon", p.lon));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacksAndMessages(null);
        executor.shutdown();
    }

    /** Shows each place as a glass row: city name + region. */
    static class PlaceAdapter extends ArrayAdapter<Place> {
        PlaceAdapter(Context c) {
            super(c, 0);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_place, parent, false);
            }
            Place p = getItem(position);
            ((TextView) convertView.findViewById(R.id.tvPlaceName)).setText(p.name);
            ((TextView) convertView.findViewById(R.id.tvPlaceRegion)).setText(p.region);
            return convertView;
        }
    }
}
