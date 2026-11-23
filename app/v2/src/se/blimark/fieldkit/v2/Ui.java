package se.blimark.fieldkit.v2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The look of FieldKit, taken from the shipped app: near-black background with a
 * faint grid, amber accent, and tiles that each carry their own border colour.
 *
 * Everything is drawn in code because that is how V1 does it - its APK contains
 * one layout file, for the widget, and nothing else.
 */
public final class Ui {

    public static final int BG = 0xFF0B0E12;
    public static final int TILE = 0xFF101620;
    public static final int CARD = 0xFF12171F;
    public static final int TEXT = 0xFFF2F4F7;
    public static final int DIM = 0xFF9AA3AD;
    public static final int FAINT = 0xFF6B7480;
    public static final int AMBER = 0xFFFFB000;
    public static final int RED = 0xFFD05050;
    public static final int GREEN = 0xFF4E9A6B;
    public static final int BLUE = 0xFF4F7FD0;
    public static final int TEAL = 0xFF3FA7A0;
    public static final int PURPLE = 0xFF8B6FD0;
    public static final int ORANGE = 0xFFD08040;
    public static final int TAN = 0xFFC0A070;
    public static final int IVORY = 0xFFC9C2A8;
    public static final int GREY = 0xFF7A8189;

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static TextView text(Context context, String value, float sizeSp, int colour) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(colour);
        return view;
    }

    /** Small letter-spaced caps, the style of every label in V1. */
    public static TextView caps(Context context, String value, float sizeSp, int colour) {
        TextView view = text(context, value.toUpperCase(), sizeSp, colour);
        view.setLetterSpacing(0.12f);
        view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    public static TextView body(Context context, String value) {
        TextView view = text(context, value, 15f, TEXT);
        view.setLineSpacing(dp(context, 3), 1f);
        return view;
    }

    public static TextView note(Context context, String value) {
        TextView view = text(context, value, 13f, DIM);
        view.setLineSpacing(dp(context, 2), 1f);
        return view;
    }

    /** The amber hairline under the header. */
    public static View rule(Context context) {
        View view = new View(context);
        view.setBackgroundColor(AMBER);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(context, 2));
        params.topMargin = dp(context, 14);
        view.setLayoutParams(params);
        return view;
    }

    private static LinearLayout.LayoutParams spaced(Context context, int heightDp) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, heightDp);
        return params;
    }

    /** A rounded panel with a coloured border, used for warnings and results. */
    public static LinearLayout card(Context context, int border) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 16);
        panel.setPadding(pad, pad, pad, pad);
        android.graphics.drawable.GradientDrawable shape =
                new android.graphics.drawable.GradientDrawable();
        shape.setColor(CARD);
        shape.setCornerRadius(dp(context, 14));
        shape.setStroke(dp(context, 2), border);
        panel.setBackground(shape);
        panel.setLayoutParams(spaced(context, 14));
        return panel;
    }

    public static TextView headingRow(Context context, String title, String right) {
        LinearLayout row = null;
        TextView left = caps(context, title, 13f, AMBER);
        TextView tail = caps(context, right, 12f, FAINT);
        return left;
    }

    public static android.widget.Button button(Context context, String label, int colour,
                                               View.OnClickListener listener) {
        android.widget.Button button = new android.widget.Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        button.setTextColor(colour);
        button.setBackground(roundRect(0xFF161C24, colour, 12, context));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, 10);
        button.setLayoutParams(params);
        return button;
    }

    public static android.graphics.drawable.GradientDrawable roundRect(int fill, int stroke,
                                                                       int radiusDp,
                                                                       Context context) {
        android.graphics.drawable.GradientDrawable shape =
                new android.graphics.drawable.GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(context, radiusDp));
        shape.setStroke(dp(context, 1), stroke);
        return shape;
    }

    public static EditText field(Context context, String hint, String value, int inputType) {
        EditText edit = new EditText(context);
        edit.setHint(hint);
        edit.setText(value);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        edit.setTextColor(TEXT);
        edit.setHintTextColor(FAINT);
        edit.setInputType(inputType);
        edit.setBackground(roundRect(0xFF0E131A, 0xFF2A323C, 10, context));
        edit.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        edit.setLayoutParams(spaced(context, 8));
        return edit;
    }

    public static EditText number(Context context, String hint, String value) {
        return field(context, hint, value, InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    }

    public static EditText textField(Context context, String hint, String value) {
        return field(context, hint, value, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    }

    /** The faint square grid V1 paints behind everything. */
    public static Drawable background(Context context) {
        return new GridDrawable(dp(context, 28));
    }

    private static final class GridDrawable extends Drawable {
        private final int step;
        private final Paint paint = new Paint();

        GridDrawable(int step) {
            this.step = Math.max(step, 8);
            paint.setColor(0x0DFFFFFF);
            paint.setStrokeWidth(1f);
        }

        @Override
        public void draw(Canvas canvas) {
            int width = getBounds().width();
            int height = getBounds().height();
            for (int x = 0; x < width; x += step) {
                canvas.drawLine(x, 0, x, height, paint);
            }
            for (int y = 0; y < height; y += step) {
                canvas.drawLine(0, y, width, y, paint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter filter) {
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    /** Line icons in the same weight as V1's, drawn rather than loaded. */
    public static View icon(Context context, int kind, int colour) {
        IconView view = new IconView(context, kind, colour);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(context, 26),
                dp(context, 26));
        params.bottomMargin = dp(context, 8);
        view.setLayoutParams(params);
        return view;
    }

    public static final int ICON_PERSON = 0;
    public static final int ICON_GEAR = 1;
    public static final int ICON_CROSS = 2;
    public static final int ICON_CALC = 3;
    public static final int ICON_LINK = 4;
    public static final int ICON_SHARE = 5;
    public static final int ICON_SCREEN = 6;
    public static final int ICON_BOOK = 7;
    public static final int ICON_PEN = 8;
    public static final int ICON_MAP = 9;
    public static final int ICON_TALK = 10;
    public static final int ICON_QUESTION = 11;

    private static final class IconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int kind;

        IconView(Context context, int kind, int colour) {
            super(context);
            this.kind = kind;
            paint.setColor(colour);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(context.getResources().getDisplayMetrics().density * 1.6f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float r = Math.min(w, h) / 2f - 2f;
            path.reset();
            switch (kind) {
                case ICON_PERSON:
                    canvas.drawCircle(cx, cy - r * 0.34f, r * 0.28f, paint);
                    canvas.drawArc(new RectF(cx - r * 0.55f, cy + r * 0.08f, cx + r * 0.55f,
                            cy + r * 1.1f), 200f, 140f, false, paint);
                    break;
                case ICON_GEAR:
                    canvas.drawCircle(cx, cy, r * 0.38f, paint);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI * 2 * i / 8.0;
                        float x1 = (float) (cx + Math.cos(a) * r * 0.62f);
                        float y1 = (float) (cy + Math.sin(a) * r * 0.62f);
                        float x2 = (float) (cx + Math.cos(a) * r * 0.92f);
                        float y2 = (float) (cy + Math.sin(a) * r * 0.92f);
                        canvas.drawLine(x1, y1, x2, y2, paint);
                    }
                    break;
                case ICON_CROSS:
                    canvas.drawLine(cx, cy - r * 0.7f, cx, cy + r * 0.7f, paint);
                    canvas.drawLine(cx - r * 0.7f, cy, cx + r * 0.7f, cy, paint);
                    break;
                case ICON_CALC:
                    canvas.drawRoundRect(new RectF(cx - r * 0.55f, cy - r * 0.75f,
                            cx + r * 0.55f, cy + r * 0.75f), r * 0.15f, r * 0.15f, paint);
                    canvas.drawLine(cx - r * 0.3f, cy - r * 0.45f, cx + r * 0.3f, cy - r * 0.45f,
                            paint);
                    for (int row = 0; row < 2; row++) {
                        for (int col = 0; col < 2; col++) {
                            float x = cx + (col == 0 ? -r * 0.25f : r * 0.25f);
                            float y = cy + r * (row == 0 ? 0.05f : 0.45f);
                            canvas.drawCircle(x, y, r * 0.11f, paint);
                        }
                    }
                    break;
                case ICON_LINK:
                    canvas.drawRoundRect(new RectF(cx - r * 0.9f, cy - r * 0.34f,
                            cx + r * 0.15f, cy + r * 0.34f), r * 0.3f, r * 0.3f, paint);
                    canvas.drawRoundRect(new RectF(cx - r * 0.15f, cy - r * 0.34f,
                            cx + r * 0.9f, cy + r * 0.34f), r * 0.3f, r * 0.3f, paint);
                    canvas.drawLine(cx - r * 0.25f, cy, cx + r * 0.25f, cy, paint);
                    break;
                case ICON_SHARE:
                    float dot = r * 0.18f;
                    canvas.drawCircle(cx + r * 0.5f, cy - r * 0.62f, dot, paint);
                    canvas.drawCircle(cx - r * 0.55f, cy, dot, paint);
                    canvas.drawCircle(cx + r * 0.5f, cy + r * 0.62f, dot, paint);
                    canvas.drawLine(cx - r * 0.36f, cy - r * 0.1f, cx + r * 0.32f, cy - r * 0.5f,
                            paint);
                    canvas.drawLine(cx - r * 0.36f, cy + r * 0.1f, cx + r * 0.32f, cy + r * 0.5f,
                            paint);
                    break;
                case ICON_SCREEN:
                    canvas.drawRoundRect(new RectF(cx - r * 0.75f, cy - r * 0.6f,
                            cx + r * 0.75f, cy + r * 0.35f), r * 0.12f, r * 0.12f, paint);
                    canvas.drawLine(cx - r * 0.3f, cy + r * 0.7f, cx + r * 0.3f, cy + r * 0.7f,
                            paint);
                    break;
                case ICON_BOOK:
                    canvas.drawRoundRect(new RectF(cx - r * 0.65f, cy - r * 0.75f,
                            cx + r * 0.65f, cy + r * 0.75f), r * 0.1f, r * 0.1f, paint);
                    canvas.drawLine(cx, cy - r * 0.75f, cx, cy + r * 0.75f, paint);
                    break;
                case ICON_PEN:
                    canvas.drawLine(cx - r * 0.55f, cy + r * 0.6f, cx + r * 0.5f, cy - r * 0.55f,
                            paint);
                    canvas.drawLine(cx - r * 0.55f, cy + r * 0.6f, cx - r * 0.2f, cy + r * 0.55f,
                            paint);
                    break;
                case ICON_MAP:
                    path.moveTo(cx - r * 0.75f, cy - r * 0.5f);
                    path.lineTo(cx - r * 0.25f, cy - r * 0.7f);
                    path.lineTo(cx + r * 0.25f, cy - r * 0.5f);
                    path.lineTo(cx + r * 0.75f, cy - r * 0.7f);
                    path.lineTo(cx + r * 0.75f, cy + r * 0.5f);
                    path.lineTo(cx + r * 0.25f, cy + r * 0.7f);
                    path.lineTo(cx - r * 0.25f, cy + r * 0.5f);
                    path.lineTo(cx - r * 0.75f, cy + r * 0.7f);
                    path.close();
                    canvas.drawPath(path, paint);
                    break;
                case ICON_TALK:
                    canvas.drawRoundRect(new RectF(cx - r * 0.8f, cy - r * 0.6f,
                            cx + r * 0.8f, cy + r * 0.35f), r * 0.18f, r * 0.18f, paint);
                    canvas.drawLine(cx - r * 0.1f, cy + r * 0.35f, cx - r * 0.35f, cy + r * 0.75f,
                            paint);
                    break;
                default:
                    canvas.drawCircle(cx, cy - r * 0.25f, r * 0.22f, paint);
                    canvas.drawLine(cx, cy + r * 0.05f, cx, cy + r * 0.45f, paint);
                    canvas.drawCircle(cx, cy + r * 0.65f, r * 0.1f, paint);
                    break;
            }
        }
    }

    public static View spacer(Context context, int heightDp) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(context, heightDp)));
        return view;
    }

    public static LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return column;
    }

    public static int alpha(int colour, float factor) {
        return Color.argb((int) (Color.alpha(colour) * factor), Color.red(colour),
                Color.green(colour), Color.blue(colour));
    }

    public static TextView stat(Context context, String key, String value) {
        TextView view = text(context, key.toUpperCase() + "   " + value, 13f, TEXT);
        view.setGravity(Gravity.START);
        view.setPadding(0, dp(context, 4), 0, dp(context, 4));
        return view;
    }
}
