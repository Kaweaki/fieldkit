package se.blimark.fieldkit.v2;

import android.util.Log;
import android.view.Choreographer;

/**
 * Frame timing for the motion in this app, taken from the vsync callbacks the
 * app itself receives.
 *
 * This exists because the two outside sources lie here: `dumpsys gfxinfo` only
 * counts frames the UI thread drew, and alpha/translation animations run on the
 * render thread, so a smooth 190 ms fade shows up there as two frames; and
 * SurfaceFlinger's latency buffer is all zeros on this Samsung build. A
 * Choreographer callback fires once per vsync whether or not the UI thread had
 * to draw, so the gaps between them are the cadence the user actually sees.
 *
 * One measurement runs at a time: a second track() during a window is ignored,
 * which keeps the counter honest when a tap lands on the tail of a transition.
 * Each finished window logs one line under FieldKitFrames.
 */
final class FrameMeter {

    private static final String TAG = "FieldKitFrames";
    private static final long SCREEN_WINDOW_MS = 260;
    private static final long PRESS_WINDOW_MS = 200;
    private static final long LATE_MS = 20;

    private static boolean running;
    private static String label = "";
    private static long start = -1;
    private static long last;
    private static long total;
    private static long worst;
    private static int intervals;
    private static int late;
    private static long window;

    private static final Choreographer.FrameCallback STEP =
            new Choreographer.FrameCallback() {
                @Override
                public void doFrame(long nanos) {
                    if (start < 0) {
                        start = nanos;
                        last = nanos;
                    } else {
                        long gap = (nanos - last) / 1000000L;
                        intervals++;
                        total += gap;
                        worst = Math.max(worst, gap);
                        if (gap > LATE_MS) {
                            late++;
                        }
                        last = nanos;
                    }
                    if ((nanos - start) / 1000000L < window) {
                        Choreographer.getInstance().postFrameCallback(this);
                    } else {
                        Log.i(TAG, label + " frames=" + (intervals + 1)
                                + " mean=" + (intervals == 0 ? 0 : total / intervals) + "ms"
                                + " max=" + worst + "ms"
                                + " late=" + late + "/" + intervals
                                + " window=" + window + "ms");
                        running = false;
                    }
                }
            };

    private FrameMeter() {
    }

    /** Starts a measurement; called on the UI thread, next to the animation. */
    static void track(String what, boolean press) {
        if (running) {
            if (press) {
                return;
            }
            // A screen arriving is the measurement that matters, and it lands
            // inside the press window of the tap that caused it.
            Choreographer.getInstance().removeFrameCallback(STEP);
        }
        running = true;
        label = what;
        start = -1;
        last = 0;
        total = 0;
        worst = 0;
        intervals = 0;
        late = 0;
        window = press ? PRESS_WINDOW_MS : SCREEN_WINDOW_MS;
        Choreographer.getInstance().postFrameCallback(STEP);
    }
}
