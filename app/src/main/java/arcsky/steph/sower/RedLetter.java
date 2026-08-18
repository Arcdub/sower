package arcsky.steph.sower;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import androidx.core.content.ContextCompat;

/**
 * Verse strings from assets mark the words of Jesus with sentinel characters
 * (U+0001 opens a span, U+0002 closes it — see tools/transform.js). This class
 * renders those spans in red, or strips them for plain-text share/copy.
 */
public final class RedLetter {

    private static final char OPEN = '\u0001';
    private static final char CLOSE = '\u0002';

    private RedLetter() {
    }

    /** Returns the verse with words of Jesus colored red. */
    public static CharSequence styled(Context context, String raw) {
        if (raw.indexOf(OPEN) < 0 && raw.indexOf(CLOSE) < 0) {
            return raw;
        }
        int color = ContextCompat.getColor(context, R.color.red_letter);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int redStart = -1;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == OPEN) {
                redStart = builder.length();
            } else if (c == CLOSE) {
                if (redStart >= 0 && builder.length() > redStart) {
                    builder.setSpan(new ForegroundColorSpan(color), redStart, builder.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                redStart = -1;
            } else {
                builder.append(c);
            }
        }
        if (redStart >= 0 && builder.length() > redStart) {
            builder.setSpan(new ForegroundColorSpan(color), redStart, builder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
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
