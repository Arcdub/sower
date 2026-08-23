package arcsky.steph.sower;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.util.AttributeSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws stored highlights with the same geometry the live text selection uses:
 * tight per-line stripes that skip the added line spacing. Each line is drawn
 * as its own uniformly rounded rectangle (rather than one path with a corner
 * effect, which puts little bumps wherever line rectangles of different widths
 * meet), so a committed highlight looks exactly like the selection did while
 * the finger was still down, with clean edges.
 */
public class HighlightTextView extends androidx.appcompat.widget.AppCompatTextView {

    private final List<int[]> highlights = new ArrayList<>();
    private final Path path = new Path();
    private final RectF bounds = new RectF();
    private final Paint gold = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint green = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private int jumpStart = -1;
    private int jumpEnd = -1;

    public HighlightTextView(Context context) {
        this(context, null);
    }

    public HighlightTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        gold.setColor(0x59C8A24B);
        green.setColor(0x408CBF94);
    }

    /** Highlight ranges as {start, end} offsets into the displayed text. */
    void setHighlights(List<int[]> spans) {
        highlights.clear();
        highlights.addAll(spans);
        invalidate();
    }

    /** The transient search-jump marker, painted green under the highlights. */
    void setJumpTint(int start, int end) {
        jumpStart = start;
        jumpEnd = end;
        invalidate();
    }

    void clearJumpTint() {
        setJumpTint(-1, -1);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Layout layout = getLayout();
        if (layout != null && (!highlights.isEmpty() || jumpStart >= 0)) {
            canvas.save();
            canvas.translate(getTotalPaddingLeft(), getTotalPaddingTop());
            int length = length();
            if (jumpStart >= 0 && jumpEnd > jumpStart && jumpEnd <= length) {
                drawRange(canvas, layout, jumpStart, jumpEnd, green);
            }
            for (int[] r : highlights) {
                if (r[0] >= 0 && r[1] > r[0] && r[1] <= length) {
                    drawRange(canvas, layout, r[0], r[1], gold);
                }
            }
            canvas.restore();
        }
        super.onDraw(canvas);
    }

    /** One uniformly rounded stripe per line, with a hairline gap between lines. */
    private void drawRange(Canvas canvas, Layout layout, int start, int end, Paint paint) {
        int firstLine = layout.getLineForOffset(start);
        int lastLine = layout.getLineForOffset(Math.max(start, end - 1));
        RectF[] stripes = new RectF[lastLine - firstLine + 1];
        for (int line = firstLine; line <= lastLine; line++) {
            int lineStart = Math.max(start, layout.getLineStart(line));
            int lineEnd = Math.min(end, layout.getLineEnd(line));
            RectF stripe = new RectF();
            if (lineEnd > lineStart) {
                path.reset();
                layout.getSelectionPath(lineStart, lineEnd, path);
                path.computeBounds(stripe, true);
            }
            stripes[line - firstLine] = stripe;
        }
        // A blank gap line inside a cross-verse run has no glyphs of its own;
        // give it the following line's horizontal extent so the run connects.
        for (int i = 0; i < stripes.length; i++) {
            if (stripes[i].width() < 2 && i + 1 < stripes.length
                    && stripes[i + 1].width() >= 2) {
                int line = firstLine + i;
                stripes[i].set(stripes[i + 1].left, layout.getLineTop(line),
                        stripes[i + 1].right, layout.getLineBottom(line));
            }
        }
        float inset = density * 0.5f;
        float radius = density * 4;
        for (RectF stripe : stripes) {
            if (stripe.width() < 2) {
                continue;
            }
            canvas.drawRoundRect(stripe.left, stripe.top + inset,
                    stripe.right, stripe.bottom - inset, radius, radius, paint);
        }
    }
}
