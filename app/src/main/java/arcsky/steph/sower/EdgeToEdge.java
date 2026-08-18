package arcsky.steph.sower;

import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Draws each screen edge-to-edge: the toolbar's green extends up under the
 * (transparent) status bar with light icons, and the given bottom view is
 * padded clear of the navigation bar / keyboard.
 */
public final class EdgeToEdge {

    private EdgeToEdge() {
    }

    /** Keeps a floating view's bottom margin clear of the navigation bar. */
    public static void liftAboveSystemBars(View view) {
        final int baseMargin = ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.bottomMargin = baseMargin
                    + insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            v.setLayoutParams(lp);
            return insets;
        });
    }

    public static void apply(AppCompatActivity activity, Toolbar toolbar, View bottomContent) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        new WindowInsetsControllerCompat(window, window.getDecorView())
                .setAppearanceLightStatusBars(false); // white icons on the green bar

        TypedValue tv = new TypedValue();
        int resolved = 0;
        if (activity.getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)) {
            resolved = TypedValue.complexToDimensionPixelSize(
                    tv.data, activity.getResources().getDisplayMetrics());
        }
        final int actionBarSize = resolved;
        final int bottomBasePadding = bottomContent != null ? bottomContent.getPaddingBottom() : 0;

        // Never let the inset padding boundary slice text: if the view isn't re-measured
        // after padding lands (an insets/layout timing race seen on some devices), its
        // children would otherwise be clipped at the padding edge.
        if (bottomContent instanceof ViewGroup) {
            ((ViewGroup) bottomContent).setClipToPadding(false);
            ((ViewGroup) bottomContent).setClipChildren(false);
        }

        ViewCompat.setOnApplyWindowInsetsListener(
                activity.findViewById(android.R.id.content), (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
                    toolbar.setPadding(bars.left, bars.top, bars.right, 0);
                    ViewGroup.LayoutParams lp = toolbar.getLayoutParams();
                    lp.height = actionBarSize + bars.top;
                    toolbar.setLayoutParams(lp);
                    if (bottomContent != null) {
                        int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                        bottomContent.setPadding(
                                bottomContent.getPaddingLeft(),
                                bottomContent.getPaddingTop(),
                                bottomContent.getPaddingRight(),
                                bottomBasePadding + Math.max(bars.bottom, ime));
                    }
                    return insets;
                });
    }
}
