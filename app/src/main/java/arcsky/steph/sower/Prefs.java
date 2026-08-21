package arcsky.steph.sower;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {

    private static final String NAME = "sower";
    private static final String KEY_LAST_BOOK = "lastBook";
    private static final String KEY_LAST_CHAPTER = "lastChapter";
    private static final String KEY_LAST_VERSE = "lastVerse";
    private static final String KEY_TEXT_SIZE = "textSize";
    private static final String KEY_TRANSLATION = "translation";

    public static final float MIN_TEXT_SIZE = 14f;
    public static final float MAX_TEXT_SIZE = 30f;
    public static final float DEFAULT_TEXT_SIZE = 18f;

    private Prefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    /** Selected translation id when an edition bundles more than one. */
    public static String translation(Context context) {
        return prefs(context).getString(KEY_TRANSLATION, null);
    }

    public static void setTranslation(Context context, String id) {
        prefs(context).edit().putString(KEY_TRANSLATION, id).apply();
    }

    public static void setLastRead(Context context, String bookFile, int chapter) {
        prefs(context).edit()
                .putString(KEY_LAST_BOOK, bookFile)
                .putInt(KEY_LAST_CHAPTER, chapter)
                .putInt(KEY_LAST_VERSE, 1)
                .apply();
    }

    /** The verse at the top of the screen when the reader was last left. */
    public static void setLastVerse(Context context, int verse) {
        prefs(context).edit().putInt(KEY_LAST_VERSE, verse).apply();
    }

    public static int lastVerse(Context context) {
        return prefs(context).getInt(KEY_LAST_VERSE, 1);
    }

    public static String lastBook(Context context) {
        return prefs(context).getString(KEY_LAST_BOOK, null);
    }

    public static int lastChapter(Context context) {
        return prefs(context).getInt(KEY_LAST_CHAPTER, 1);
    }

    public static float textSize(Context context) {
        return prefs(context).getFloat(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE);
    }

    public static void setTextSize(Context context, float sp) {
        prefs(context).edit().putFloat(KEY_TEXT_SIZE, sp).apply();
    }
}
