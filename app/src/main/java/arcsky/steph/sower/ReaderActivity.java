package arcsky.steph.sower;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ReaderActivity extends AppCompatActivity {

    public static final String EXTRA_BOOK = "book";
    public static final String EXTRA_CHAPTER = "chapter";
    public static final String EXTRA_VERSE = "verse";
    public static final String EXTRA_HIGHLIGHT = "highlight";

    private static final String STATE_CHAPTER = "state.chapter";
    private static final String STATE_SCROLL_INDEX = "state.scrollIndex";
    private static final String STATE_SCROLL_OFFSET = "state.scrollOffset";
    private static final String STATE_HIGHLIGHT = "state.highlight";

    private Bible.BookText book;
    private String bookFile;
    private int chapter; // 1-based
    private int pendingVerse; // verse to scroll to on first load
    private boolean highlightPendingVerse = true;
    private int pendingScrollIndex = -1; // exact scroll restore after recreation
    private int pendingScrollOffset;
    private int restoredHighlight;

    private Toolbar toolbar;
    private RecyclerView verseList;
    private TextView chapterLabel;
    private Button prevButton;
    private Button nextButton;
    private VerseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);

        bookFile = getIntent().getStringExtra(EXTRA_BOOK);
        chapter = Math.max(1, getIntent().getIntExtra(EXTRA_CHAPTER, 1));
        pendingVerse = getIntent().getIntExtra(EXTRA_VERSE, 0);
        highlightPendingVerse = getIntent().getBooleanExtra(EXTRA_HIGHLIGHT, true);

        // Recreated (rotation, or coming back after the system reclaimed memory):
        // restore where the reader actually was, not where the launch intent pointed.
        if (savedInstanceState != null) {
            chapter = savedInstanceState.getInt(STATE_CHAPTER, chapter);
            pendingScrollIndex = savedInstanceState.getInt(STATE_SCROLL_INDEX, -1);
            pendingScrollOffset = savedInstanceState.getInt(STATE_SCROLL_OFFSET, 0);
            restoredHighlight = savedInstanceState.getInt(STATE_HIGHLIGHT, 0);
            pendingVerse = 0;
        }

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_reader);
        MainActivity.tintOverflowWhite(toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_text_larger) {
                adjustTextSize(2f);
                return true;
            } else if (id == R.id.action_text_smaller) {
                adjustTextSize(-2f);
                return true;
            }
            return false;
        });
        findViewById(R.id.toolbarPassButton).setOnClickListener(v ->
                startActivity(new Intent(this, PassItOnActivity.class)));

        verseList = findViewById(R.id.verseList);
        verseList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VerseAdapter();
        verseList.setAdapter(adapter);

        EdgeToEdge.apply(this, toolbar, findViewById(R.id.navBar));

        chapterLabel = findViewById(R.id.chapterLabel);
        prevButton = findViewById(R.id.prevChapter);
        nextButton = findViewById(R.id.nextChapter);
        prevButton.setOnClickListener(v -> showChapter(chapter - 1));
        nextButton.setOnClickListener(v -> showChapter(chapter + 1));

        Bible.load(this, bookFile, text -> {
            book = text;
            showChapter(chapter);
        });
    }

    private void showChapter(int newChapter) {
        if (book == null || newChapter < 1 || newChapter > book.chapters.size()) {
            return;
        }
        chapter = newChapter;
        toolbar.setTitle(book.name + " " + chapter);
        chapterLabel.setText(getString(R.string.chapter_x_of_y, chapter, book.chapters.size()));
        prevButton.setEnabled(chapter > 1);
        nextButton.setEnabled(chapter < book.chapters.size());
        adapter.setVerses(book.chapters.get(chapter - 1));
        LinearLayoutManager layout = (LinearLayoutManager) verseList.getLayoutManager();
        if (pendingScrollIndex >= 0) {
            adapter.setHighlight(restoredHighlight);
            layout.scrollToPositionWithOffset(pendingScrollIndex, pendingScrollOffset);
            pendingScrollIndex = -1;
        } else if (pendingVerse > 0) {
            adapter.setHighlight(highlightPendingVerse ? pendingVerse : 0);
            int index = adapter.indexOfVerse(pendingVerse);
            layout.scrollToPositionWithOffset(Math.max(index, 0),
                    highlightPendingVerse ? verseList.getHeight() / 4 : 0);
            pendingVerse = 0;
        } else {
            adapter.setHighlight(0);
            verseList.scrollToPosition(0);
        }
        Prefs.setLastRead(this, bookFile, chapter);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CHAPTER, chapter);
        outState.putInt(STATE_HIGHLIGHT, adapter.getHighlight());
        LinearLayoutManager layout = (LinearLayoutManager) verseList.getLayoutManager();
        int index = layout.findFirstVisibleItemPosition();
        if (index >= 0) {
            outState.putInt(STATE_SCROLL_INDEX, index);
            android.view.View first = layout.findViewByPosition(index);
            outState.putInt(STATE_SCROLL_OFFSET,
                    first != null ? first.getTop() - verseList.getPaddingTop() : 0);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Remember the verse at the top of the screen so "Continue reading" can
        // return here even after the process is gone entirely.
        LinearLayoutManager layout = (LinearLayoutManager) verseList.getLayoutManager();
        int index = layout.findFirstVisibleItemPosition();
        if (index >= 0 && index < adapter.getItemCount()) {
            Prefs.setLastVerse(this, adapter.verseNumberAt(index));
        }
    }

    private void adjustTextSize(float delta) {
        float size = Prefs.textSize(this) + delta;
        size = Math.max(Prefs.MIN_TEXT_SIZE, Math.min(Prefs.MAX_TEXT_SIZE, size));
        Prefs.setTextSize(this, size);
        adapter.notifyDataSetChanged();
    }

    private String verseReference(int verseNumber) {
        return book.name + " " + chapter + ":" + verseNumber;
    }

    private void shareVerse(int verseNumber, String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT,
                "“" + RedLetter.plain(text) + "”\n— " + verseReference(verseNumber) + " (WEB)");
        startActivity(Intent.createChooser(intent, getString(R.string.share_verse_via)));
    }

    private void copyVerse(int verseNumber, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(verseReference(verseNumber),
                RedLetter.plain(text) + " — " + verseReference(verseNumber)));
        Toast.makeText(this, R.string.verse_copied, Toast.LENGTH_SHORT).show();
    }

    class VerseAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<Integer> numbers = new ArrayList<>();
        private final List<String> texts = new ArrayList<>();
        private int highlightNumber;

        void setHighlight(int verseNumber) {
            highlightNumber = verseNumber;
        }

        int getHighlight() {
            return highlightNumber;
        }

        int indexOfVerse(int verseNumber) {
            return numbers.indexOf(verseNumber);
        }

        int verseNumberAt(int index) {
            return numbers.get(index);
        }

        void setVerses(List<String> verses) {
            numbers.clear();
            texts.clear();
            for (int i = 0; i < verses.size(); i++) {
                String text = verses.get(i);
                if (text != null && !text.isEmpty()) {
                    numbers.add(i + 1);
                    texts.add(text);
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView view = (TextView) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_verse, parent, false);
            return new RecyclerView.ViewHolder(view) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final int number = numbers.get(position);
            final String text = texts.get(position);

            SpannableStringBuilder builder = new SpannableStringBuilder();
            String prefix = number + " ";
            builder.append(prefix).append(RedLetter.styled(ReaderActivity.this, text));
            int color = ContextCompat.getColor(ReaderActivity.this, R.color.verse_number);
            builder.setSpan(new ForegroundColorSpan(color), 0, prefix.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new StyleSpan(Typeface.BOLD), 0, prefix.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new RelativeSizeSpan(0.7f), 0, prefix.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            TextView view = (TextView) holder.itemView;
            view.setText(builder);
            view.setTextSize(Prefs.textSize(ReaderActivity.this));
            view.setBackgroundColor(number == highlightNumber ? 0x33C8A24B : 0x00000000);
            view.setOnClickListener(v -> shareVerse(number, text));
            view.setOnLongClickListener(v -> {
                copyVerse(number, text);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return texts.size();
        }
    }
}
