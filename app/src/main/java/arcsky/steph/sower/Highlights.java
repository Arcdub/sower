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
        return map;
    }

    /** Verse-level keys "book:chapter:verse" that carry any highlight in this translation. */
    static Set<String> verseKeysFor(Context context, String translation) {
        Set<String> out = new HashSet<>();
        for (String key : Prefs.highlights(context)) {
            String[] p = key.split(":");
            if (p.length == 6 && p[0].equals(translation)) {
                out.add(p[1] + ":" + p[2] + ":" + p[3]);
            } else if (p.length == 3) {
                out.add(key);
            }
        }
        return out;
    }

    /** Adds [start, end) to a verse, merging overlapping or touching ranges. */
    static void add(Context context, String translation, String book, int chapter,
                    int verse, int start, int end, int verseLength) {
        List<int[]> ranges = collect(context, translation, book, chapter, verse, verseLength);
        ranges.add(new int[]{start, end});
        Collections.sort(ranges, (a, b) -> a[0] - b[0]);
        List<int[]> merged = new ArrayList<>();
        for (int[] r : ranges) {
            if (!merged.isEmpty() && r[0] <= merged.get(merged.size() - 1)[1]) {
                int[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], r[1]);
            } else {
                merged.add(r);
            }
        }
        write(context, translation, book, chapter, verse, merged);
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
