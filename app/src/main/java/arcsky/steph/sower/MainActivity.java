package arcsky.steph.sower;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    /** Gospel-centered daily verses: {book file, chapter, verse} (1-based). */
    private static final Object[][] DAILY_VERSES = {
            {"john", 3, 16}, {"romans", 5, 8}, {"psalms", 23, 1}, {"isaiah", 53, 5},
            {"john", 14, 6}, {"romans", 8, 38}, {"ephesians", 2, 8}, {"john", 1, 1},
            {"psalms", 46, 1}, {"matthew", 11, 28}, {"romans", 10, 9}, {"1john", 1, 9},
            {"isaiah", 40, 31}, {"philippians", 4, 6}, {"john", 10, 10}, {"2corinthians", 5, 17},
            {"jeremiah", 29, 11}, {"psalms", 121, 1}, {"matthew", 28, 19}, {"acts", 4, 12},
            {"romans", 6, 23}, {"revelation", 3, 20}, {"galatians", 2, 20}, {"hebrews", 11, 1},
            {"proverbs", 3, 5}, {"joshua", 1, 9}, {"micah", 6, 8}, {"zephaniah", 3, 17},
            {"lamentations", 3, 22}, {"1peter", 5, 7},
    };

    private BookAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_main);
        tintOverflowWhite(toolbar);
        // The translation picker only makes sense when this edition bundles more than one.
        toolbar.getMenu().findItem(R.id.action_translation)
                .setVisible(Bible.translations(this).size() > 1);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_pass_it_on) {
                startActivity(new Intent(this, PassItOnActivity.class));
                return true;
            } else if (id == R.id.action_search) {
                startActivity(new Intent(this, SearchActivity.class));
                return true;
            } else if (id == R.id.action_translation) {
                showTranslationPicker();
                return true;
            } else if (id == R.id.action_accessibility) {
                showAccessibilityOptions();
                return true;
            } else if (id == R.id.action_privacy) {
                showPrivacyPolicy();
                return true;
            }
            return false;
        });

        RecyclerView list = findViewById(R.id.bookList);
        EdgeToEdge.apply(this, toolbar, list);

        View fab = findViewById(R.id.passItOnFab);
        fab.setOnClickListener(v -> startActivity(new Intent(this, PassItOnActivity.class)));
        // A gentle breathing pulse so the eye finds the one action the mission depends on.
        // Finite on purpose: an endless animator never lets the UI report idle (blocking
        // accessibility/UI-test tooling) and wastes battery. Eight breaths, then rest.
        ObjectAnimator pulseX = ObjectAnimator.ofFloat(fab, View.SCALE_X, 1f, 1.06f);
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(fab, View.SCALE_Y, 1f, 1.06f);
        AnimatorSet pulse = new AnimatorSet();
        pulse.playTogether(pulseX, pulseY);
        pulse.setDuration(1100);
        pulseX.setRepeatCount(15);
        pulseX.setRepeatMode(ObjectAnimator.REVERSE);
        pulseY.setRepeatCount(15);
        pulseY.setRepeatMode(ObjectAnimator.REVERSE);
        pulse.start();
        EdgeToEdge.liftAboveSystemBars(fab);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BookAdapter(this);
        list.setAdapter(adapter);

        loadVerseOfTheDay();
    }

    /** Paints the three-dot overflow icon white so it reads on the green toolbar. */
    static void tintOverflowWhite(Toolbar toolbar) {
        android.graphics.drawable.Drawable overflow = toolbar.getOverflowIcon();
        if (overflow != null) {
            overflow = overflow.mutate();
            overflow.setColorFilter(android.graphics.Color.WHITE,
                    android.graphics.PorterDuff.Mode.SRC_IN);
            toolbar.setOverflowIcon(overflow);
        }
    }

    private void showAccessibilityOptions() {
        View view = getLayoutInflater().inflate(R.layout.dialog_accessibility, null);
        android.widget.RadioGroup wjGroup = view.findViewById(R.id.wjGroup);
        int[] wjIds = {R.id.wjRed, R.id.wjBold, R.id.wjNone};
        wjGroup.check(wjIds[Prefs.wordsOfJesusStyle(this)]);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_accessibility)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int wj = RedLetter.STYLE_RED;
                    if (wjGroup.getCheckedRadioButtonId() == R.id.wjBold) {
                        wj = RedLetter.STYLE_BOLD;
                    } else if (wjGroup.getCheckedRadioButtonId() == R.id.wjNone) {
                        wj = RedLetter.STYLE_NONE;
                    }
                    Prefs.setWordsOfJesusStyle(this, wj);
                    adapter.rebuild();
                    loadVerseOfTheDay();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showTranslationPicker() {
        final java.util.List<Bible.Translation> all = Bible.translations(this);
        String currentId = Bible.currentTranslation(this).id;
        CharSequence[] names = new CharSequence[all.size()];
        int checked = 0;
        for (int i = 0; i < all.size(); i++) {
            names[i] = all.get(i).name;
            if (all.get(i).id.equals(currentId)) {
                checked = i;
            }
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_translation)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    Bible.setTranslation(this, all.get(which).id);
                    dialog.dismiss();
                    adapter.rebuild();
                    loadVerseOfTheDay();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Shown in-app so it is readable with no internet, matching the app's offline design. */
    private void showPrivacyPolicy() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_privacy)
                .setMessage(R.string.privacy_policy_body)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.rebuild();
    }

    private void loadVerseOfTheDay() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        Object[] pick = DAILY_VERSES[day % DAILY_VERSES.length];
        final String file = (String) pick[0];
        final int chapter = (Integer) pick[1];
        final int verse = (Integer) pick[2];
        Bible.load(this, file, text -> {
            if (chapter <= text.chapters.size()) {
                List<String> verses = text.chapters.get(chapter - 1);
                if (verse <= verses.size()) {
                    adapter.setVerseOfTheDay(
                            verses.get(verse - 1),
                            text.name + " " + chapter + ":" + verse,
                            file, chapter);
                }
            }
        });
    }

    static class BookAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_VOTD = 0;
        private static final int TYPE_CONTINUE = 1;
        private static final int TYPE_HEADER = 2;
        private static final int TYPE_BOOK = 3;

        static class VotdRow {
            final String text;
            final String ref;
            final String file;
            final int chapter;

            VotdRow(String text, String ref, String file, int chapter) {
                this.text = text;
                this.ref = ref;
                this.file = file;
                this.chapter = chapter;
            }
        }

        static class ContinueRow {
            final String label;
            final String file;
            final int chapter;

            ContinueRow(String label, String file, int chapter) {
                this.label = label;
                this.file = file;
                this.chapter = chapter;
            }
        }

        private final AppCompatActivity activity;
        private final List<Object> rows = new ArrayList<>();
        private VotdRow votd;

        BookAdapter(AppCompatActivity activity) {
            this.activity = activity;
            rebuild();
        }

        void setVerseOfTheDay(String text, String ref, String file, int chapter) {
            votd = new VotdRow(text, ref, file, chapter);
            rebuild();
        }

        void rebuild() {
            rows.clear();
            if (votd != null) {
                rows.add(votd);
            }
            String lastBook = Prefs.lastBook(activity);
            if (lastBook != null) {
                Bible.Book book = Bible.findBook(activity, lastBook);
                if (book != null) {
                    int chapter = Prefs.lastChapter(activity);
                    rows.add(new ContinueRow(book.name + " " + chapter, book.file, chapter));
                }
            }
            boolean ntStarted = false;
            rows.add(activity.getString(R.string.old_testament));
            for (Bible.Book book : Bible.books(activity)) {
                if (book.nt && !ntStarted) {
                    rows.add(activity.getString(R.string.new_testament));
                    ntStarted = true;
                }
                rows.add(book);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            Object row = rows.get(position);
            if (row instanceof VotdRow) return TYPE_VOTD;
            if (row instanceof ContinueRow) return TYPE_CONTINUE;
            if (row instanceof String) return TYPE_HEADER;
            return TYPE_BOOK;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            int layout;
            switch (viewType) {
                case TYPE_VOTD: layout = R.layout.item_votd; break;
                case TYPE_CONTINUE: layout = R.layout.item_continue; break;
                case TYPE_HEADER: layout = R.layout.item_header; break;
                default: layout = R.layout.item_book; break;
            }
            View view = inflater.inflate(layout, parent, false);
            return new RecyclerView.ViewHolder(view) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object row = rows.get(position);
            if (row instanceof VotdRow) {
                VotdRow v = (VotdRow) row;
                ((TextView) holder.itemView.findViewById(R.id.votdText))
                        .setText(RedLetter.styled(activity, v.text));
                ((TextView) holder.itemView.findViewById(R.id.votdRef)).setText(v.ref);
                holder.itemView.setOnClickListener(view -> openReader(v.file, v.chapter));
            } else if (row instanceof ContinueRow) {
                ContinueRow c = (ContinueRow) row;
                ((TextView) holder.itemView.findViewById(R.id.continueWhere)).setText(c.label);
                holder.itemView.setOnClickListener(view -> {
                    Intent intent = new Intent(activity, ReaderActivity.class);
                    intent.putExtra(ReaderActivity.EXTRA_BOOK, c.file);
                    intent.putExtra(ReaderActivity.EXTRA_CHAPTER, c.chapter);
                    int verse = Prefs.lastVerse(activity);
                    if (verse > 1) {
                        intent.putExtra(ReaderActivity.EXTRA_VERSE, verse);
                        intent.putExtra(ReaderActivity.EXTRA_HIGHLIGHT, false);
                    }
                    activity.startActivity(intent);
                });
            } else if (row instanceof String) {
                ((TextView) holder.itemView.findViewById(R.id.headerText)).setText((String) row);
            } else {
                Bible.Book book = (Bible.Book) row;
                TextView bookName = holder.itemView.findViewById(R.id.bookName);
                bookName.setText(book.name);
                // Click on the bubble itself so the ripple stays inside the rounded box.
                bookName.setOnClickListener(view -> {
                    Intent intent = new Intent(activity, ChapterGridActivity.class);
                    intent.putExtra(ChapterGridActivity.EXTRA_BOOK, book.file);
                    activity.startActivity(intent);
                });
            }
        }

        private void openReader(String file, int chapter) {
            Intent intent = new Intent(activity, ReaderActivity.class);
            intent.putExtra(ReaderActivity.EXTRA_BOOK, file);
            intent.putExtra(ReaderActivity.EXTRA_CHAPTER, chapter);
            activity.startActivity(intent);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
