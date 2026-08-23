package arcsky.steph.sower;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Character-range verse highlights, stored in Prefs as
 * "translationId:bookFile:chapter:verse:start:end". Offsets index the plain verse
 * text (red-letter markers stripped), so ranges are per translation. Legacy
 * whole-verse keys ("bookFile:chapter:verse") are still honoured and are migrated
 * to explicit ranges the first time that verse's highlights are edited.
 */
final class Highlights {

    /** Sentinel end for a legacy whole-verse highlight; renderers clamp to text length. */
    static final int WHOLE_VERSE = Integer.MAX_VALUE;

    private Highlights() {
    }

    /** verse number -> list of [start, end) ranges, for one chapter in one translation. */
    static Map<Integer, List<int[]>> rangesFor(Context context, String translation,
                                               String book, int chapter) {
        Map<Integer, List<int[]>> map = new HashMap<>();
        String rangePrefix = translation + ":" + book + ":" + chapter + ":";
        String legacyPrefix = book + ":" + chapter + ":";
        for (String key : Prefs.highlights(context)) {
            String[] p = key.split(":");
            int verse;
            int[] range;
            if (p.length == 6 && key.startsWith(rangePrefix)) {
                verse = Integer.parseInt(p[3]);
                range = new int[]{Integer.parseInt(p[4]), Integer.parseInt(p[5])};
            } else if (p.length == 3 && key.startsWith(legacyPrefix)) {
                verse = Integer.parseInt(p[2]);
                range = new int[]{0, WHOLE_VERSE};
            } else {
                continue;
            }
            List<int[]> list = map.get(verse);
            if (list == null) {
                list = new ArrayList<>();
                map.put(verse, list);
            }
            list.add(range);
        }
        // Merge, not just sort: overlapping stored keys (interrupted writes,
        // restored backups) must never draw a doubled highlight.
        for (Map.Entry<Integer, List<int[]>> entry : map.entrySet()) {
            entry.setValue(merged(entry.getValue()));
        }
        return map;
    }

    /** Adds [start, end) to a verse, merging overlapping or touching ranges. */
    static void add(Context context, String translation, String book, int chapter,
                    int verse, int start, int end, int verseLength) {
        List<int[]> ranges = collect(context, translation, book, chapter, verse, verseLength);
        ranges.add(new int[]{start, end});
        write(context, translation, book, chapter, verse, merged(ranges));
    }

    /** Sorts ranges and merges any that overlap or touch. */
    private static List<int[]> merged(List<int[]> ranges) {
        Collections.sort(ranges, (a, b) -> a[0] - b[0]);
        List<int[]> out = new ArrayList<>();
        for (int[] r : ranges) {
            if (!out.isEmpty() && r[0] <= out.get(out.size() - 1)[1]) {
                int[] last = out.get(out.size() - 1);
                last[1] = Math.max(last[1], r[1]);
            } else {
                out.add(r);
            }
        }
        return out;
    }

    /** Every highlight in a translation: book -> chapter -> verse -> merged ranges. */
    static Map<String, Map<Integer, Map<Integer, List<int[]>>>> allFor(Context context,
                                                                       String translation) {
        Map<String, Map<Integer, Map<Integer, List<int[]>>>> books = new HashMap<>();
        for (String key : Prefs.highlights(context)) {
            String[] p = key.split(":");
            String book;
            int chapter;
            int verse;
            int[] range;
            if (p.length == 6 && p[0].equals(translation)) {
                book = p[1];
                chapter = Integer.parseInt(p[2]);
                verse = Integer.parseInt(p[3]);
                range = new int[]{Integer.parseInt(p[4]), Integer.parseInt(p[5])};
            } else if (p.length == 3) {
                book = p[0];
                chapter = Integer.parseInt(p[1]);
                verse = Integer.parseInt(p[2]);
                range = new int[]{0, WHOLE_VERSE};
            } else {
                continue;
            }
            Map<Integer, Map<Integer, List<int[]>>> chapters = books.get(book);
            if (chapters == null) {
                chapters = new HashMap<>();
                books.put(book, chapters);
            }
            Map<Integer, List<int[]>> verses = chapters.get(chapter);
            if (verses == null) {
                verses = new HashMap<>();
                chapters.put(chapter, verses);
            }
            List<int[]> list = verses.get(verse);
            if (list == null) {
                list = new ArrayList<>();
                verses.put(verse, list);
            }
            list.add(range);
        }
        for (Map<Integer, Map<Integer, List<int[]>>> chapters : books.values()) {
            for (Map<Integer, List<int[]>> verses : chapters.values()) {
                for (Map.Entry<Integer, List<int[]>> entry : verses.entrySet()) {
                    entry.setValue(merged(entry.getValue()));
                }
            }
        }
        return books;
    }

    /** Removes [start, end) from a verse's highlights, splitting ranges as needed. */
    static void remove(Context context, String translation, String book, int chapter,
                       int verse, int start, int end, int verseLength) {
        List<int[]> ranges = collect(context, translation, book, chapter, verse, verseLength);
        List<int[]> kept = new ArrayList<>();
        for (int[] r : ranges) {
            if (r[1] <= start || r[0] >= end) {
                kept.add(r);
                continue;
            }
            if (r[0] < start) {
                kept.add(new int[]{r[0], start});
            }
            if (r[1] > end) {
                kept.add(new int[]{end, r[1]});
            }
        }
        write(context, translation, book, chapter, verse, kept);
    }

    private static List<int[]> collect(Context context, String translation, String book,
                                       int chapter, int verse, int verseLength) {
        List<int[]> list = new ArrayList<>();
        String rangePrefix = translation + ":" + book + ":" + chapter + ":" + verse + ":";
        String legacyKey = book + ":" + chapter + ":" + verse;
        for (String key : Prefs.highlights(context)) {
            if (key.startsWith(rangePrefix) && key.split(":").length == 6) {
                String[] p = key.split(":");
                list.add(new int[]{Integer.parseInt(p[4]),
                        Math.min(Integer.parseInt(p[5]), verseLength)});
            } else if (key.equals(legacyKey)) {
                list.add(new int[]{0, verseLength});
            }
        }
        return list;
    }

    private static void write(Context context, String translation, String book,
                              int chapter, int verse, List<int[]> ranges) {
        Set<String> all = Prefs.highlights(context);
        String rangePrefix = translation + ":" + book + ":" + chapter + ":" + verse + ":";
        String legacyKey = book + ":" + chapter + ":" + verse;
        Iterator<String> it = all.iterator();
        while (it.hasNext()) {
            String key = it.next();
            if ((key.startsWith(rangePrefix) && key.split(":").length == 6)
                    || key.equals(legacyKey)) {
                it.remove();
            }
        }
        for (int[] r : ranges) {
            if (r[1] > r[0]) {
                all.add(rangePrefix + r[0] + ":" + r[1]);
            }
        }
        Prefs.saveHighlights(context, all);
    }
}
