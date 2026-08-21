package arcsky.steph.sower;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ChapterGridActivity extends AppCompatActivity {

    public static final String EXTRA_BOOK = "book";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chapters);

        String bookFile = getIntent().getStringExtra(EXTRA_BOOK);
        final Bible.Book book = Bible.findBook(this, bookFile);
        if (book == null) {
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(book.name);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView grid = findViewById(R.id.chapterGrid);
        EdgeToEdge.apply(this, toolbar, grid);

        View fab = findViewById(R.id.passItOnFab);
        fab.setOnClickListener(v ->
                startActivity(new Intent(this, PassItOnActivity.class)));
        EdgeToEdge.liftAboveSystemBars(fab);

        grid.setLayoutManager(new GridLayoutManager(this, 5));
        grid.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_chapter, parent, false);
                return new RecyclerView.ViewHolder(view) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                final int chapter = position + 1;
                TextView number = holder.itemView.findViewById(R.id.chapterNumber);
                number.setText(String.valueOf(chapter));
                holder.itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(ChapterGridActivity.this, ReaderActivity.class);
                    intent.putExtra(ReaderActivity.EXTRA_BOOK, book.file);
                    intent.putExtra(ReaderActivity.EXTRA_CHAPTER, chapter);
                    startActivity(intent);
                });
            }

            @Override
            public int getItemCount() {
                return book.chapters;
            }
        });
    }
}
