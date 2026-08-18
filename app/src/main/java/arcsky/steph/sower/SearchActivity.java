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
    private ResultsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView results = findViewById(R.id.resultList);
        EdgeToEdge.apply(this, toolbar, results);
        results.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ResultsAdapter();
        results.setAdapter(adapter);

        status = findViewById(R.id.searchStatus);
        input = findViewById(R.id.searchInput);
        input.setOnEditorActionListener((v, actionId, event) -> {
            runSearch(input.getText().toString());
            return true;
        });
        input.requestFocus();
    }

    private void runSearch(String query) {
        final String trimmed = query.trim();
        if (trimmed.length() < 2) {
            status.setText(R.string.search_too_short);
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(input.getWindowToken(), 0);

        final int gen = generation.incrementAndGet();
        status.setText(R.string.search_searching);
        EXECUTOR.execute(() -> {
            final List<Bible.SearchResult> found = Bible.search(this, trimmed, RESULT_LIMIT);
            runOnUiThread(() -> {
                if (gen != generation.get()) {
                    return; // a newer search replaced this one
                }
                adapter.set(found, trimmed);
                if (found.isEmpty()) {
                    status.setText(R.string.search_no_results);
                } else if (found.size() >= RESULT_LIMIT) {
                    status.setText(getString(R.string.search_results_capped, RESULT_LIMIT));
                } else {
                    status.setText(getResources().getQuantityString(
                            R.plurals.search_results, found.size(), found.size()));
                }
            });
        });
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
            ((TextView) holder.itemView.findViewById(R.id.resultRef))
                    .setText(result.bookName + " " + result.chapter + ":" + result.verse);

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
