package arcsky.steph.sower;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReaderActivity extends AppCompatActivity {

    public static final String EXTRA_BOOK = "book";
    public static final String EXTRA_CHAPTER = "chapter";
    public static final String EXTRA_VERSE = "verse";
    public static final String EXTRA_HIGHLIGHT = "highlight";

    private static final String STATE_CHAPTER = "state.chapter";
    private static final String STATE_TOP_VERSE = "state.topVerse";
    private static final String STATE_HIGHLIGHT = "state.highlight";

    private static final int MENU_ADD_HIGHLIGHT = 1001;
    private static final int MENU_REMOVE_HIGHLIGHT = 1002;

    private Bible.BookText book;
    private String bookFile;
    private int chapter; // 1-based
    private int pendingVerse; // verse to scroll to on first load
    private boolean highlightPendingVerse = true;
    private int restoredHighlight;
    private int transientVerse; // search-jump tint
    private boolean longPressActive;
    private boolean dragExtended;
    private boolean charDragActive;
    private boolean suppressActionMode;
    private int anchorStart;
    private int anchorEnd;
    private ActionMode activeActionMode;
    private float downX;
    private float downY;

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '\'' || c == '’';
    }

    private Toolbar toolbar;
    private NestedScrollView verseScroll;
    private TextView chapterText;
    private TextView chapterLabel;
    private Button prevButton;
    private Button nextButton;

    // Per verse, offsets into the chapter spannable: the number prefix, and the
    // verse text region that highlight ranges index.
    private final List<Integer> verseNumbers = new ArrayList<>();
    private final List<Integer> versePrefixStarts = new ArrayList<>();
    private final List<Integer> verseTextStarts = new ArrayList<>();
    private final List<Integer> verseTextEnds = new ArrayList<>();
    private final List<String> verseRawTexts = new ArrayList<>();
    private Map<Integer, List<int[]>> chapterRanges = new java.util.HashMap<>();

    @SuppressLint("ClickableViewAccessibility")
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
            pendingVerse = savedInstanceState.getInt(STATE_TOP_VERSE, 0);
            highlightPendingVerse = false;
            restoredHighlight = savedInstanceState.getInt(STATE_HIGHLIGHT, 0);
        }

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_reader);
        MainActivity.tintOverflowWhite(toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_text_size) {
                showTextSizePopup();
                return true;
            }
            return false;
        });
        findViewById(R.id.toolbarPassButton).setOnClickListener(v ->
                startActivity(new Intent(this, PassItOnActivity.class)));

        verseScroll = findViewById(R.id.verseScroll);
        chapterText = findViewById(R.id.chapterText);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            // The smart-selection classifier flashes its sprite animation in the
            // theme accent (green) over the text; plain selections don't.
            chapterText.setTextClassifier(android.view.textclassifier.TextClassifier.NO_OP);
        }
        // A plain click listener misses the first tap (it only grants the
        // selectable TextView focus), so taps are detected from the touch stream.
        final android.view.GestureDetector taps = new android.view.GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        if (!chapterText.hasSelection()) {
                            handleTap(e.getX(), e.getY());
                        }
                        return false;
                    }

                    @Override
                    public void onLongPress(@NonNull MotionEvent e) {
                        longPressActive = true;
                        // Anchor on the pressed word so the drag can then grow the
                        // selection character by character from the raw touch.
                        int offset = chapterText.getOffsetForPosition(e.getX(), e.getY());
                        CharSequence text = chapterText.getText();
                        int start = offset;
                        int end = offset;
                        while (start > 0 && isWordChar(text.charAt(start - 1))) {
                            start--;
                        }
                        while (end < text.length() && isWordChar(text.charAt(end))) {
                            end++;
                        }
                        if (end > start) {
                            anchorStart = start;
                            anchorEnd = end;
                            charDragActive = true;
                        }
                    }
                });
        final int dragSlop = android.view.ViewConfiguration.get(this).getScaledTouchSlop() * 3;
        chapterText.setOnTouchListener((v, event) -> {
            boolean consumed = false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    longPressActive = false;
                    dragExtended = false;
                    charDragActive = false;
                    suppressActionMode = false;
                    downX = event.getX();
                    downY = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (longPressActive && !dragExtended
                            && (Math.abs(event.getX() - downX) > dragSlop
                            || Math.abs(event.getY() - downY) > dragSlop)) {
                        dragExtended = true;
                        if (charDragActive) {
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                            if (activeActionMode != null) {
                                activeActionMode.finish();
                            }
                        }
                    }
                    if (dragExtended && charDragActive) {
                        // Character-precise: the selection tracks the finger exactly,
                        // never snapping to word boundaries. Events are consumed so
                        // the editor's word-jumping drag logic stays out of it.
                        int offset = chapterText.getOffsetForPosition(
                                event.getX(), event.getY());
                        android.text.Spannable text =
                                (android.text.Spannable) chapterText.getText();
                        if (offset >= anchorEnd) {
                            android.text.Selection.setSelection(text, anchorStart, offset);
                        } else if (offset <= anchorStart) {
                            android.text.Selection.setSelection(text, anchorEnd, offset);
                        } else {
                            android.text.Selection.setSelection(text, anchorStart, anchorEnd);
                        }
                        consumed = true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    // Kindle-style: press-hold, drag over the words, let go — the
                    // highlight commits the moment the finger lifts. A plain
                    // long-press (no drag) still offers the selection toolbar.
                    // The bounds are captured now: suppressing the action mode
                    // can clear the selection before a posted read would run.
                    if (longPressActive && dragExtended && chapterText.hasSelection()) {
                        suppressActionMode = true;
                        consumed = charDragActive;
                        final int selA = Math.min(chapterText.getSelectionStart(),
                                chapterText.getSelectionEnd());
                        final int selB = Math.max(chapterText.getSelectionStart(),
                                chapterText.getSelectionEnd());
                        chapterText.post(() -> {
                            applyHighlightRange(selA, selB);
                            android.text.Selection.removeSelection(
                                    (android.text.Spannable) chapterText.getText());
                        });
                    }
                    break;
                default:
                    break;
            }
            taps.onTouchEvent(event);
            return consumed;
        });
        chapterText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                if (suppressActionMode) {
                    // The gesture already committed a highlight; no toolbar, and
                    // whatever selection the editor restored async is dropped.
                    chapterText.post(() -> android.text.Selection.removeSelection(
                            (android.text.Spannable) chapterText.getText()));
                    return false;
                }
                activeActionMode = mode;
                menu.add(Menu.NONE, MENU_ADD_HIGHLIGHT, 100, R.string.verse_highlight);
                menu.add(Menu.NONE, MENU_REMOVE_HIGHLIGHT, 101, R.string.verse_unhighlight);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                int id = item.getItemId();
                if (id != MENU_ADD_HIGHLIGHT && id != MENU_REMOVE_HIGHLIGHT) {
                    return false;
                }
                applyHighlightSelection(id == MENU_ADD_HIGHLIGHT);
                mode.finish();
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                if (activeActionMode == mode) {
                    activeActionMode = null;
                }
            }
        });

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

        int jumpVerse = 0;
        if (pendingVerse > 0) {
            transientVerse = highlightPendingVerse ? pendingVerse : restoredHighlight;
            jumpVerse = pendingVerse;
            pendingVerse = 0;
        } else {
            transientVerse = 0;
        }
        restoredHighlight = 0;

        buildChapterText();
        final int target = jumpVerse;
        final boolean center = transientVerse != 0;
        verseScroll.post(() -> {
            if (target > 0) {
                scrollToVerse(target, center);
            } else {
                verseScroll.scrollTo(0, 0);
            }
        });
        if (transientVerse != 0) {
            clearTransientSoon(transientVerse);
        }
        Prefs.setLastRead(this, bookFile, chapter);
    }

    /** The jump marker is a pointer, not a highlight: fade it out after a moment. */
    private void clearTransientSoon(int marked) {
        verseScroll.postDelayed(() -> {
            if (transientVerse != marked) {
                return;
            }
            if (chapterText.hasSelection()) {
                clearTransientSoon(marked); // don't wipe an in-progress selection
                return;
            }
            transientVerse = 0;
            buildChapterText();
        }, 3000);
    }

    /** Renders the whole chapter into the single selectable TextView. */
    private void buildChapterText() {
        verseNumbers.clear();
        versePrefixStarts.clear();
        verseTextStarts.clear();
        verseTextEnds.clear();
        verseRawTexts.clear();

        chapterRanges = Highlights.rangesFor(this,
                Bible.currentTranslation(this).id, bookFile, chapter);
        Map<Integer, List<int[]>> ranges = chapterRanges;
        int numberColor = ContextCompat.getColor(this, R.color.verse_number);
        List<String> verses = book.chapters.get(chapter - 1);
        SpannableStringBuilder builder = new SpannableStringBuilder();

        for (int i = 0; i < verses.size(); i++) {
            String text = verses.get(i);
            if (text == null || text.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                // Paragraph gap: a blank line shrunk to roughly half a line's height.
                int gap = builder.length();
                builder.append("\n\n");
                builder.setSpan(new RelativeSizeSpan(0.4f), gap + 1, gap + 2,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int number = i + 1;
            String prefix = number + " ";
            int prefixStart = builder.length();
            builder.append(prefix);
            builder.setSpan(new ForegroundColorSpan(numberColor), prefixStart,
                    prefixStart + prefix.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new StyleSpan(Typeface.BOLD), prefixStart,
                    prefixStart + prefix.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new RelativeSizeSpan(0.7f), prefixStart,
                    prefixStart + prefix.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            int textStart = builder.length();
            builder.append(RedLetter.styled(this, text));
            int textEnd = builder.length();

            if (number == transientVerse) {
                // Soft green, deliberately distinct from the gold highlights, so the
                // searched-for verse can't be mistaken for a marked one. Applied
                // first so gold highlight spans draw on top of it.
                builder.setSpan(new BackgroundColorSpan(0x408CBF94), prefixStart, textEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            List<int[]> verseRanges = ranges.get(number);
            if (verseRanges != null) {
                for (int[] r : verseRanges) {
                    int start = textStart + Math.max(0, r[0]);
                    int end = textStart + Math.min(textEnd - textStart, r[1]);
                    if (end > start) {
                        builder.setSpan(new BackgroundColorSpan(0x59C8A24B), start, end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }

            verseNumbers.add(number);
            versePrefixStarts.add(prefixStart);
            verseTextStarts.add(textStart);
            verseTextEnds.add(textEnd);
            verseRawTexts.add(text);
        }

        // Cycling the selectable flag around setText keeps selection startable;
        // otherwise the TextView's selection machinery goes stale after a reset.
        chapterText.setTextIsSelectable(false);
        chapterText.setText(builder);
        chapterText.setTextIsSelectable(true);
        chapterText.setTextSize(Prefs.textSize(this));
    }

    /** Turns the current selection into per-verse highlight range edits. */
    private void applyHighlightSelection(boolean add) {
        int selStart = Math.min(chapterText.getSelectionStart(), chapterText.getSelectionEnd());
        int selEnd = Math.max(chapterText.getSelectionStart(), chapterText.getSelectionEnd());
        if (add) {
            applyHighlightRange(selStart, selEnd);
            return;
        }
        if (selEnd <= selStart) {
            return;
        }
        transientVerse = 0; // acting on the text retires the jump marker
        String translation = Bible.currentTranslation(this).id;
        for (int i = 0; i < verseNumbers.size(); i++) {
            int textStart = verseTextStarts.get(i);
            int textEnd = verseTextEnds.get(i);
            int start = Math.max(selStart, textStart) - textStart;
            int end = Math.min(selEnd, textEnd) - textStart;
            if (end <= start) {
                continue;
            }
            Highlights.remove(this, translation, bookFile, chapter,
                    verseNumbers.get(i), start, end, textEnd - textStart);
        }
        chapterText.post(this::buildChapterText);
    }

    /** Stores [selStart, selEnd) of the chapter text as highlights and redraws. */
    private void applyHighlightRange(int selStart, int selEnd) {
        if (selEnd <= selStart) {
            return;
        }
        transientVerse = 0; // acting on the text retires the jump marker
        String translation = Bible.currentTranslation(this).id;
        for (int i = 0; i < verseNumbers.size(); i++) {
            int textStart = verseTextStarts.get(i);
            int textEnd = verseTextEnds.get(i);
            int start = Math.max(selStart, textStart) - textStart;
            int end = Math.min(selEnd, textEnd) - textStart;
            if (end <= start) {
                continue;
            }
            Highlights.add(this, translation, bookFile, chapter,
                    verseNumbers.get(i), start, end, textEnd - textStart);
        }
        // Rebuild after the action mode is gone; the text is identical so the
        // scroll position is unaffected.
        chapterText.post(this::buildChapterText);
    }

    /** A tap on a highlight offers to remove it; anywhere else shares the verse. */
    private void handleTap(float x, float y) {
        int offset = chapterText.getOffsetForPosition(x, y);
        int index = verseIndexForOffset(offset);
        if (index < 0) {
            return;
        }
        List<int[]> swath = swathAt(index, offset);
        if (swath != null) {
            confirmRemoveSwath(swath);
        } else {
            shareVerse(verseNumbers.get(index), verseRawTexts.get(index));
        }
    }

    private int verseIndexForOffset(int offset) {
        int index = -1;
        for (int i = 0; i < versePrefixStarts.size(); i++) {
            if (versePrefixStarts.get(i) <= offset) {
                index = i;
            } else {
                break;
            }
        }
        return index;
    }

    private int plainLengthAt(int index) {
        return verseTextEnds.get(index) - verseTextStarts.get(index);
    }

    /** The stored range containing a local offset in a verse, clamped; null if none. */
    private int[] rangeAt(int number, int local, int length) {
        List<int[]> ranges = chapterRanges.get(number);
        if (ranges == null) {
            return null;
        }
        for (int[] r : ranges) {
            int start = Math.max(0, r[0]);
            int end = Math.min(length, r[1]);
            if (start <= local && local < end) {
                return new int[]{start, end};
            }
        }
        return null;
    }

    private int[] edgeRange(int number, int length, boolean last) {
        List<int[]> ranges = chapterRanges.get(number);
        if (ranges == null || ranges.isEmpty()) {
            return null;
        }
        int[] r = ranges.get(last ? ranges.size() - 1 : 0);
        return new int[]{Math.max(0, r[0]), Math.min(length, r[1])};
    }

    /**
     * The whole contiguous highlight under a tapped character, as
     * {verse, start, end, verseLength} pieces — walking across verse boundaries
     * wherever the marking runs on unbroken. Null when the tap isn't on a highlight.
     */
    private List<int[]> swathAt(int index, int offset) {
        int number = verseNumbers.get(index);
        int local = offset - verseTextStarts.get(index);
        int length = plainLengthAt(index);
        if (local < 0 || local >= length) {
            return null;
        }
        int[] hit = rangeAt(number, local, length);
        if (hit == null) {
            return null;
        }
        List<int[]> pieces = new ArrayList<>();
        pieces.add(new int[]{number, hit[0], hit[1], length});
        int[] cur = pieces.get(0);
        while (cur[1] == 0) {
            int prevIndex = verseNumbers.indexOf(cur[0] - 1);
            if (prevIndex < 0) {
                break;
            }
            int prevLength = plainLengthAt(prevIndex);
            int[] prev = edgeRange(cur[0] - 1, prevLength, true);
            if (prev == null || prev[1] != prevLength) {
                break;
            }
            cur = new int[]{cur[0] - 1, prev[0], prev[1], prevLength};
            pieces.add(0, cur);
        }
        cur = pieces.get(pieces.size() - 1);
        while (cur[2] == cur[3]) {
            int nextIndex = verseNumbers.indexOf(cur[0] + 1);
            if (nextIndex < 0) {
                break;
            }
            int nextLength = plainLengthAt(nextIndex);
            int[] next = edgeRange(cur[0] + 1, nextLength, false);
            if (next == null || next[0] != 0) {
                break;
            }
            cur = new int[]{cur[0] + 1, next[0], next[1], nextLength};
            pieces.add(cur);
        }
        return pieces;
    }

    /** Shows the tapped highlight and offers to remove it (or share the verse). */
    private void confirmRemoveSwath(List<int[]> pieces) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pieces.size(); i++) {
            int[] p = pieces.get(i);
            String plain = RedLetter.plain(
                    verseRawTexts.get(verseNumbers.indexOf(p[0])));
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(plain, p[1], p[2]);
        }
        int[] head = pieces.get(0);
        int[] tail = pieces.get(pieces.size() - 1);
        String snippet = (head[1] > 0 ? "…" : "") + sb.toString().trim()
                + (tail[2] < tail[3] ? "…" : "");
        String reference = book.name + " " + chapter + ":" + head[0]
                + (tail[0] > head[0] ? "–" + tail[0] : "");
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(reference)
                .setMessage(snippet)
                .setPositiveButton(R.string.verse_unhighlight, (d, w) -> removeSwath(pieces))
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.share_verse_via, (d, w) -> shareVerse(head[0],
                        verseRawTexts.get(verseNumbers.indexOf(head[0]))))
                .show();
    }

    private void removeSwath(List<int[]> pieces) {
        final java.util.Set<String> before = Prefs.highlights(this);
        String translation = Bible.currentTranslation(this).id;
        for (int[] p : pieces) {
            Highlights.remove(this, translation, bookFile, chapter, p[0], p[1], p[2], p[3]);
        }
        buildChapterText();
        com.google.android.material.snackbar.Snackbar
                .make(verseScroll, R.string.highlight_removed,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> {
                    Prefs.saveHighlights(this, before);
                    buildChapterText();
                })
                .show();
    }

    /** The verse at the top of the viewport. */
    private int topVerse() {
        Layout layout = chapterText.getLayout();
        if (layout == null || verseNumbers.isEmpty()) {
            return 1;
        }
        int y = verseScroll.getScrollY() - chapterText.getTop();
        int offset = layout.getLineStart(layout.getLineForVertical(Math.max(0, y)));
        int index = verseIndexForOffset(offset);
        return verseNumbers.get(Math.max(0, index));
    }

    private void scrollToVerse(int verse, boolean center) {
        Layout layout = chapterText.getLayout();
        int index = verseNumbers.indexOf(verse);
        if (layout == null || index < 0) {
            return;
        }
        int line = layout.getLineForOffset(versePrefixStarts.get(index));
        int y = chapterText.getTop() + layout.getLineTop(line);
        verseScroll.scrollTo(0, Math.max(0, y - (center ? verseScroll.getHeight() / 4 : 0)));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CHAPTER, chapter);
        outState.putInt(STATE_TOP_VERSE, topVerse());
        outState.putInt(STATE_HIGHLIGHT, transientVerse);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Remember the verse at the top of the screen so "Continue reading" can
        // return here even after the process is gone entirely.
        if (book != null) {
            Prefs.setLastVerse(this, topVerse());
        }
    }

    /** Pops a text-size slider out from the toolbar's "A" button. */
    private void showTextSizePopup() {
        View content = getLayoutInflater().inflate(R.layout.popup_text_size, null);
        com.google.android.material.slider.Slider slider = content.findViewById(R.id.textSizeSlider);
        slider.setValueFrom(Prefs.MIN_TEXT_SIZE);
        slider.setValueTo(Prefs.MAX_TEXT_SIZE);
        slider.setStepSize(1f);
        float current = Math.max(Prefs.MIN_TEXT_SIZE,
                Math.min(Prefs.MAX_TEXT_SIZE, Prefs.textSize(this)));
        slider.setValue(current);
        slider.addOnChangeListener((s, value, fromUser) -> {
            Prefs.setTextSize(this, value);
            chapterText.setTextSize(value);
        });

        float density = getResources().getDisplayMetrics().density;
        int width = (int) (300 * density);
        android.widget.PopupWindow popup = new android.widget.PopupWindow(content, width,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(12f);
        View anchor = toolbar.findViewById(R.id.action_text_size);
        if (anchor == null) {
            anchor = toolbar;
        }
        popup.showAsDropDown(anchor, (int) (-260 * density), 0);
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
}
