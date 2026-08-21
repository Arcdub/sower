package arcsky.steph.sower;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads the bundled Bible(s) from the APK assets — no network, ever.
 *
 * An edition may carry one or several translations. assets/translations.json (when
 * present) lists them as {id, name, dir}; each dir holds an index.json listing the
 * 66 books plus one file per book. With no translations.json the edition has a single
 * translation living in assets/bible/.
 */
public final class Bible {

    public static final class Translation {
        public final String id;
        public final String name;
        public final String dir;

        Translation(String id, String name, String dir) {
            this.id = id;
            this.name = name;
            this.dir = dir;
        }
    }

    public static final class Book {
        public final String file;
        public final String name;
        public final int chapters;
        public final boolean nt;

        Book(String file, String name, int chapters, boolean nt) {
            this.file = file;
            this.name = name;
            this.chapters = chapters;
            this.nt = nt;
        }
    }

    public static final class BookText {
        public final String name;
        public final List<List<String>> chapters;

        BookText(String name, List<List<String>> chapters) {
            this.name = name;
            this.chapters = chapters;
        }
    }

    public interface Callback<T> {
        void onResult(T value);
    }

    public static final class SearchResult {
        public final String file;
        public final String bookName;
        public final int chapter; // 1-based
        public final int verse;   // 1-based
        public final String text; // plain, no red-letter markers

        SearchResult(String file, String bookName, int chapter, int verse, String text) {
            this.file = file;
            this.bookName = bookName;
            this.chapter = chapter;
            this.verse = verse;
            this.text = text;
        }
    }

    private static List<Translation> translations;
    private static String activeDir;
    private static List<Book> books;

    private static final Map<String, BookText> CACHE =
            new LinkedHashMap<String, BookText>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, BookText> eldest) {
                    return size() > 4;
                }
            };

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Bible() {
    }

    /** The translations this edition bundles (always at least one). */
    public static synchronized List<Translation> translations(Context context) {
        if (translations == null) {
            List<Translation> list = new ArrayList<>();
            String json = readAssetOrNull(context, "translations.json");
            if (json != null) {
                try {
                    JSONArray arr = new JSONArray(json);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new Translation(
                                o.getString("id"), o.getString("name"), o.getString("dir")));
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("Corrupt translations.json", e);
                }
            }
            if (list.isEmpty()) {
                list.add(new Translation("default", null, "bible"));
            }
            translations = Collections.unmodifiableList(list);
        }
        return translations;
    }

    public static Translation currentTranslation(Context context) {
        String id = Prefs.translation(context);
        List<Translation> all = translations(context);
        if (id != null) {
            for (Translation t : all) {
                if (t.id.equals(id)) {
                    return t;
                }
            }
        }
        return all.get(0);
    }

    /** Switch the reading translation, dropping all cached text for the previous one. */
    public static synchronized void setTranslation(Context context, String id) {
        Prefs.setTranslation(context, id);
        activeDir = null;
        books = null;
        synchronized (CACHE) {
            CACHE.clear();
        }
        corpus = null;
        corpusMeta = null;
        corpusNorm = null;
    }

    private static synchronized String activeDir(Context context) {
        if (activeDir == null) {
            activeDir = currentTranslation(context).dir;
        }
        return activeDir;
    }

    public static synchronized List<Book> books(Context context) {
        if (books == null) {
            List<Book> loaded = new ArrayList<>(66);
            try {
                JSONArray arr = new JSONArray(
                        readAsset(context, activeDir(context) + "/index.json"));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    loaded.add(new Book(
                            o.getString("file"),
                            o.getString("name"),
                            o.getInt("chapters"),
                            o.getBoolean("nt")));
                }
            } catch (Exception e) {
                throw new IllegalStateException("Corrupt bundled Bible index", e);
            }
            books = Collections.unmodifiableList(loaded);
        }
        return books;
    }

    public static Book findBook(Context context, String file) {
        for (Book b : books(context)) {
            if (b.file.equals(file)) {
                return b;
            }
        }
        return null;
    }

    /** Loads a book off the main thread and delivers it on the main thread. */
    public static void load(Context context, String file, Callback<BookText> callback) {
        final Context app = context.getApplicationContext();
        synchronized (CACHE) {
            BookText cached = CACHE.get(file);
            if (cached != null) {
                callback.onResult(cached);
                return;
            }
        }
        EXECUTOR.execute(() -> {
            final BookText text = loadSync(app, file);
            MAIN.post(() -> callback.onResult(text));
        });
    }

    private static BookText loadSync(Context context, String file) {
        synchronized (CACHE) {
            BookText cached = CACHE.get(file);
            if (cached != null) {
                return cached;
            }
        }
        try {
            JSONObject o = new JSONObject(
                    readAsset(context, activeDir(context) + "/" + file + ".json"));
            JSONArray chaptersJson = o.getJSONArray("chapters");
            List<List<String>> chapters = new ArrayList<>(chaptersJson.length());
            for (int c = 0; c < chaptersJson.length(); c++) {
                JSONArray versesJson = chaptersJson.getJSONArray(c);
                List<String> verses = new ArrayList<>(versesJson.length());
                for (int v = 0; v < versesJson.length(); v++) {
                    verses.add(versesJson.getString(v));
                }
                chapters.add(Collections.unmodifiableList(verses));
            }
            BookText text = new BookText(o.getString("name"), Collections.unmodifiableList(chapters));
            synchronized (CACHE) {
                CACHE.put(file, text);
            }
            return text;
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt bundled book: " + file, e);
        }
    }

    // Search corpus: every verse, plain and normalized, built once per process.
    private static List<String[]> corpusMeta;        // per book: {file, name}
    private static List<List<List<String>>> corpus;  // book -> chapter -> plain verses
    private static List<List<List<String>>> corpusNorm; // same, normalized for matching

    /**
     * Lowercases, straightens typographic quotes, and strips accents so searches match
     * regardless of typing precision ("corazon" finds "corazón", "dont" finds "don’t").
     */
    public static String normalizeQuery(String s) {
        String folded = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return folded.toLowerCase(java.util.Locale.ROOT)
                .replace('’', '\'').replace('‘', '\'')
                .replace('“', '"').replace('”', '"');
    }

    private static synchronized void buildCorpus(Context context) {
        if (corpus != null) {
            return;
        }
        List<String[]> meta = new ArrayList<>();
        List<List<List<String>>> plain = new ArrayList<>();
        List<List<List<String>>> norm = new ArrayList<>();
        for (Book book : books(context)) {
            BookText text = loadSync(context, book.file);
            List<List<String>> plainChapters = new ArrayList<>(text.chapters.size());
            List<List<String>> normChapters = new ArrayList<>(text.chapters.size());
            for (List<String> verses : text.chapters) {
                List<String> plainVerses = new ArrayList<>(verses.size());
                List<String> normVerses = new ArrayList<>(verses.size());
                for (String verse : verses) {
                    String p = RedLetter.plain(verse);
                    plainVerses.add(p);
                    normVerses.add(normalizeQuery(p));
                }
                plainChapters.add(plainVerses);
                normChapters.add(normVerses);
            }
            meta.add(new String[]{book.file, book.name});
            plain.add(plainChapters);
            norm.add(normChapters);
        }
        corpusMeta = meta;
        corpus = plain;
        corpusNorm = norm;
    }

    /** Full-text search over every verse. Call from a background thread. */
    public static List<SearchResult> search(Context context, String query, int limit) {
        buildCorpus(context.getApplicationContext());
        String needle = normalizeQuery(query.trim());
        List<SearchResult> results = new ArrayList<>();
        if (needle.isEmpty()) {
            return results;
        }
        for (int b = 0; b < corpusNorm.size(); b++) {
            String[] meta = corpusMeta.get(b);
            List<List<String>> chapters = corpusNorm.get(b);
            for (int c = 0; c < chapters.size(); c++) {
                List<String> verses = chapters.get(c);
                for (int v = 0; v < verses.size(); v++) {
                    if (verses.get(v).contains(needle)) {
                        results.add(new SearchResult(meta[0], meta[1], c + 1, v + 1,
                                corpus.get(b).get(c).get(v)));
                        if (results.size() >= limit) {
                            return results;
                        }
                    }
                }
            }
        }
        return results;
    }

    private static String readAsset(Context context, String path) throws Exception {
        try (InputStream in = context.getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(in.available());
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** Reads an optional asset, returning null when the file is absent. */
    private static String readAssetOrNull(Context context, String path) {
        try {
            return readAsset(context, path);
        } catch (Exception e) {
            return null;
        }
    }
}
