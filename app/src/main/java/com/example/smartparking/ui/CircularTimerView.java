package com.example.smartparking.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Circular ring progress indicator for showing remaining time.
 * Mirrors android.widget.ProgressBar's API: use setProgress(int percent)
 * with values 0-100.
 *
 * Default colors match the blue_600 hero card: a translucent white
 * background ring plus a solid white progress arc, same style as the
 * horizontal progressTimer bar used below it in the same design.
 *
 * Usage in layout:
 *   <com.example.smartparking.ui.CircularTimerView
 *       android:id="@+id/circularTimer"
 *       android:layout_width="64dp"
 *       android:layout_height="64dp" />
 */
public class CircularTimerView extends View {

    private static final float DEFAULT_STROKE_WIDTH_DP = 5f;
    private static final float START_ANGLE = -90f; // start at the top (12 o'clock)

    private static final int DEFAULT_TRACK_COLOR    = 0x33FFFFFF; // translucent white, matches the progressTimer bar's track
    private static final int DEFAULT_PROGRESS_COLOR = 0xFFFFFFFF; // solid white, visible on the blue_600 card

    private final RectF arcBounds = new RectF();
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int progress = 100; // 0-100
    private float strokeWidthPx;

    public CircularTimerView(Context context) {
        this(context, null);
    }

    public CircularTimerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircularTimerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        strokeWidthPx = dpToPx(DEFAULT_STROKE_WIDTH_DP);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidthPx);
        trackPaint.setColor(DEFAULT_TRACK_COLOR);

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidthPx);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(DEFAULT_PROGRESS_COLOR);
    }

    /**
     * Sets the remaining-time percentage (0-100).
     * Mirrors ProgressBar.setProgress(int) — called from UserPayFragment's
     * updateTimerProgress().
     */
    public void setProgress(int percent) {
        progress = Math.max(0, Math.min(100, percent));
        invalidate();
    }

    public int getProgress() {
        return progress;
    }

    public void setProgressColor(int color) {
        progressPaint.setColor(color);
        invalidate();
    }

    public void setTrackColor(int color) {
        trackPaint.setColor(color);
        invalidate();
    }

    public void setStrokeWidthDp(float dp) {
        strokeWidthPx = dpToPx(dp);
        trackPaint.setStrokeWidth(strokeWidthPx);
        progressPaint.setStrokeWidth(strokeWidthPx);
        updateBounds(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateBounds(w, h);
    }

    private void updateBounds(int w, int h) {
        float half = strokeWidthPx / 2f;
        arcBounds.set(half, half, w - half, h - half);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Background ring — always a full circle
        canvas.drawOval(arcBounds, trackPaint);

        // Progress arc — proportional to the percentage
        float sweepAngle = 360f * progress / 100f;
        if (sweepAngle > 0f) {
            canvas.drawArc(arcBounds, START_ANGLE, sweepAngle, false, progressPaint);
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}