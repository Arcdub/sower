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
    private final Paint gold = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint green = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int jumpStart = -1;
    private int jumpEnd = -1;

    public HighlightTextView(Context context) {
        this(context, null);
    }

    public HighlightTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
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

    /**
     * The exact primitive the live selection uses: one selection path for the
     * whole range, filled once. A single fill can't double where rectangles
     * overlap, and the geometry is identical to the preview under the finger.
     */
    private void drawRange(Canvas canvas, Layout layout, int start, int end, Paint paint) {
        path.reset();
        layout.getSelectionPath(start, end, path);
        canvas.drawPath(path, paint);
    }
}
