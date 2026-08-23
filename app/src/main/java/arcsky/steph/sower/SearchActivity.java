package arcsky.steph.sower;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SearchActivity extends AppCompatActivity {

    private static final int RESULT_LIMIT = 300;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final AtomicInteger generation = new AtomicInteger();
    private EditText input;
    private TextView status;
    private RecyclerView results;
    private ResultsAdapter adapter;
    private com.google.android.material.chip.Chip highlightsChip;
    private com.google.android.material.progressindicator.LinearProgressIndicator progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        results = findViewById(R.id.resultList);
        EdgeToEdge.apply(this, toolbar, results);
        results.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ResultsAdapter();
        results.setAdapter(adapter);

        // Swiping a highlight result away removes that highlight (with an undo).
        new androidx.recyclerview.widget.ItemTouchHelper(
                new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0,
                        androidx.recyclerview.widget.ItemTouchHelper.LEFT
                                | androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
                    @Override
                    public boolean onMove(@NonNull RecyclerView rv,
                                          @NonNull RecyclerView.ViewHolder vh,
                                          @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public int getSwipeDirs(@NonNull RecyclerView rv,
                                            @NonNull RecyclerView.ViewHolder vh) {
                        int position = vh.getBindingAdapterPosition();
                        return position >= 0 && adapter.isHighlightResult(position)
                                ? super.getSwipeDirs(rv, vh) : 0;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                        removeHighlightAt(vh.getBindingAdapterPosition());
                    }
                }).attachToRecyclerView(results);

        status = findViewById(R.id.searchStatus);
        progress = findViewById(R.id.searchProgress);
        input = findViewById(R.id.searchInput);
        input.setOnEditorActionListener((v, actionId, event) -> {
            runSearch(input.getText().toString());
            return true;
        });

        highlightsChip = findViewById(R.id.highlightsChip);
        highlightsChip.setOnCheckedChangeListener((button, checked) -> runSearch(input.getText().toString()));

        input.requestFocus();
    }

    private void runSearch(String query) {
        final String trimmed = query.trim();
        final boolean highlightsOnly = highlightsChip.isChecked();
        // With the chip on, an empty query lists all highlights; otherwise require 2+ chars.
        if (!highlightsOnly && trimmed.length() < 2) {
            adapter.set(java.util.Collections.emptyList(), "");
            status.setText(R.string.search_too_short);
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(input.getWindowToken(), 0);

        final int gen = generation.incrementAndGet();
        status.setText(R.string.search_searching);
        progress.setVisibility(android.view.View.VISIBLE);
        adapter.set(java.util.Collections.emptyList(), "");
        EXECUTOR.execute(() -> {
            final List<Bible.SearchResult> found = highlightsOnly
                    ? Bible.searchHighlights(this, trimmed, RESULT_LIMIT)
                    : Bible.search(this, trimmed, RESULT_LIMIT);
            runOnUiThread(() -> {
                if (gen != generation.get()) {
                    return; // a newer search replaced this one
                }
                progress.setVisibility(android.view.View.GONE);
                adapter.set(found, trimmed);
                if (found.isEmpty()) {
                    status.setText(highlightsOnly
                            ? R.string.search_no_highlights : R.string.search_no_results);
                } else if (found.size() >= RESULT_LIMIT) {
                    status.setText(getString(R.string.search_results_capped, RESULT_LIMIT));
                } else {
                    status.setText(getResources().getQuantityString(
                            R.plurals.search_results, found.size(), found.size()));
                }
            });
        });
    }

    /** Removes the highlight behind a swiped result row, offering an undo. */
    private void removeHighlightAt(int position) {
        if (position < 0 || position >= adapter.items.size()) {
            return;
        }
        final Bible.SearchResult item = adapter.items.get(position);
        if (item.pieces == null) {
            return;
        }
        final java.util.Set<String> before = Prefs.highlights(this);
        String translation = Bible.currentTranslation(this).id;
        for (int[] p : item.pieces) {
            Highlights.remove(this, translation, item.file, item.chapter,
                    p[0], p[1], p[2], p[3]);
        }
        adapter.removeAt(position);
        showResultCount();
        com.google.android.material.snackbar.Snackbar
                .make(results, R.string.highlight_removed,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> {
                    Prefs.saveHighlights(this, before);
                    adapter.restoreAt(position, item);
                    showResultCount();
                })
                .show();
    }

    private void showResultCount() {
        int count = adapter.items.size();
        status.setText(count == 0
                ? getString(R.string.search_no_highlights)
                : getResources().getQuantityString(R.plurals.search_results, count, count));
    }

    class ResultsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<Bible.SearchResult> items = new ArrayList<>();
        private String query = "";

        void set(List<Bible.SearchResult> results, String newQuery) {
            items.clear();
            items.addAll(results);
            query = Bible.normalizeQuery(newQuery);
            notifyDataSetChanged();
        }

        boolean isHighlightResult(int position) {
            return items.get(position).pieces != null;
        }

        void removeAt(int position) {
            items.remove(position);
            notifyItemRemoved(position);
        }

        void restoreAt(int position, Bible.SearchResult item) {
            items.add(Math.min(position, items.size()), item);
            notifyItemInserted(Math.min(position, items.size() - 1));
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_result, parent, false);
            return new RecyclerView.ViewHolder(view) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Bible.SearchResult result = items.get(position);
            String reference = result.bookName + " " + result.chapter + ":" + result.verse
                    + (result.endVerse > result.verse ? "–" + result.endVerse : "");
            ((TextView) holder.itemView.findViewById(R.id.resultRef)).setText(reference);

            SpannableString text = new SpannableString(result.text);
            int at = Bible.normalizeQuery(result.text).indexOf(query);
            if (at >= 0) {
                text.setSpan(new StyleSpan(Typeface.BOLD), at, at + query.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            ((TextView) holder.itemView.findViewById(R.id.resultText)).setText(text);

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(SearchActivity.this, ReaderActivity.class);
                intent.putExtra(ReaderActivity.EXTRA_BOOK, result.file);
                intent.putExtra(ReaderActivity.EXTRA_CHAPTER, result.chapter);
                intent.putExtra(ReaderActivity.EXTRA_VERSE, result.verse);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
