package arcsky.steph.sower;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import androidx.core.content.ContextCompat;

/**
 * Verse strings from assets mark the words of Jesus with sentinel characters
 * (U+0001 opens a span, U+0002 closes it — see tools/transform.js). This class
 * renders those spans in red, or strips them for plain-text share/copy.
 */
public final class RedLetter {

    private static final char OPEN = '\u0001';
    private static final char CLOSE = '\u0002';

    /** How the words of Jesus are distinguished (see Prefs.wordsOfJesusStyle). */
    public static final int STYLE_RED = 0;
    public static final int STYLE_BOLD = 1;   // colour-blind friendly: distinguish by weight
    public static final int STYLE_NONE = 2;

    private RedLetter() {
    }

    /** Returns the verse with words of Jesus in the user's chosen style. */
    public static CharSequence styled(Context context, String raw) {
        if (raw.indexOf(OPEN) < 0 && raw.indexOf(CLOSE) < 0) {
            return raw;
        }
        int style = Prefs.wordsOfJesusStyle(context);
        if (style == STYLE_NONE) {
            return plain(raw);
        }
        int color = ContextCompat.getColor(context, R.color.red_letter);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int start = -1;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == OPEN) {
                start = builder.length();
            } else if (c == CLOSE) {
                applyStyle(builder, start, style, color);
                start = -1;
            } else {
                builder.append(c);
            }
        }
        applyStyle(builder, start, style, color);
        return builder;
    }

    private static void applyStyle(SpannableStringBuilder builder, int start, int style, int color) {
        if (start < 0 || builder.length() <= start) {
            return;
        }
        Object span = style == STYLE_BOLD
                ? new StyleSpan(Typeface.BOLD)
                : new ForegroundColorSpan(color);
        builder.setSpan(span, start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** Returns the verse with the red-letter markers removed. */
    public static String plain(String raw) {
        if (raw.indexOf(OPEN) < 0 && raw.indexOf(CLOSE) < 0) {
            return raw;
        }
        StringBuilder builder = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != OPEN && c != CLOSE) {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
