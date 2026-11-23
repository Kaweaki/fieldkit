package se.blimark.fieldkit;

import android.view.View;
import android.view.WindowInsets;

/**
 * Keep the drawn layout out from under the system bars.
 *
 * From targetSdk 35 Android draws edge to edge whether the app asks or not, and
 * every screen here is a full-screen ScrollView with its own painted
 * background. Without this the header sits under the clock and the last row
 * under the gesture bar.
 */
public final class Insets {

    private Insets() {
    }

    public static void apply(final View root) {
        if (root == null) {
            return;
        }
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                view.setPadding(
                        insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        root.requestApplyInsets();
    }

    /** For a screen that already owns its top padding. */
    public static void applyBottom(final View root) {
        if (root == null) {
            return;
        }
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                        view.getPaddingRight(), insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        root.requestApplyInsets();
    }
}
