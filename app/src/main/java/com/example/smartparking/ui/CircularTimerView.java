package com.example.smartparking.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Kružni progress indikator (prsten) za prikaz preostalog vremena.
 * API oponaša android.widget.ProgressBar: koristi se
 * setProgress(int percent) sa vrijednostima 0-100.
 *
 * Podrazumijevane boje su usklađene sa plavom (blue_600) hero karticom:
 * providan bijeli pozadinski prsten + puni bijeli progres,
 * isto kao stil horizontalne trake (progressTimer) ispod njega u istom dizajnu.
 *
 * Korištenje u layout-u:
 *   <com.example.smartparking.ui.CircularTimerView
 *       android:id="@+id/circularTimer"
 *       android:layout_width="64dp"
 *       android:layout_height="64dp" />
 */
public class CircularTimerView extends View {

    private static final float DEFAULT_STROKE_WIDTH_DP = 5f;
    private static final float START_ANGLE = -90f; // počni od vrha (12h pozicija)

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
        trackPaint.setColor(0x33FFFFFF); // providan bijeli — isti duh kao #1AFFFFFF pozadina progressTimer trake

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidthPx);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(0xFFFFFFFF); // puna bijela — vidljivo na blue_600 kartici
    }

    /**
     * Postavlja procenat preostalog vremena (0-100).
     * Analogno ProgressBar.setProgress(int) — poziva se iz UserPayFragment
     * u updateTimerProgress().
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

        // Pozadinski (prazan) prsten — uvijek pun krug
        canvas.drawOval(arcBounds, trackPaint);

        // Progress luk — proporcionalan procentu
        float sweepAngle = 360f * progress / 100f;
        if (sweepAngle > 0f) {
            canvas.drawArc(arcBounds, START_ANGLE, sweepAngle, false, progressPaint);
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}