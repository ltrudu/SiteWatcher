package com.ltrudu.sitewatcher.ui.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ltrudu.sitewatcher.R;

/**
 * Custom view that draws crosshair lines (vertical and horizontal)
 * at a specified tap point. Used to provide visual feedback during
 * action testing to show where taps occur.
 */
public class TapCrosshairView extends View {

    private static final int DEFAULT_LINE_WIDTH_DP = 2;
    private static final int DEFAULT_CIRCLE_RADIUS_DP = 24;
    private static final int DEFAULT_DISPLAY_DURATION_MS = 1200;
    private static final int FADE_DURATION_MS = 300;

    private final Paint linePaint;
    private final Paint circlePaint;
    private final Paint circleStrokePaint;

    private float tapX = -1;
    private float tapY = -1;
    private float circleRadius;
    private float alpha = 1.0f;

    private ValueAnimator fadeAnimator;
    private Runnable onHideCallback;

    public TapCrosshairView(Context context) {
        this(context, null);
    }

    public TapCrosshairView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TapCrosshairView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        float density = context.getResources().getDisplayMetrics().density;
        float lineWidth = DEFAULT_LINE_WIDTH_DP * density;
        circleRadius = DEFAULT_CIRCLE_RADIUS_DP * density;

        // Get theme primary color
        int primaryColor = ContextCompat.getColor(context, R.color.picker_crosshair);

        // Line paint for crosshair
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(primaryColor);
        linePaint.setStrokeWidth(lineWidth);
        linePaint.setStyle(Paint.Style.STROKE);

        // Filled circle paint
        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(primaryColor);
        circlePaint.setAlpha(80); // Semi-transparent fill
        circlePaint.setStyle(Paint.Style.FILL);

        // Circle stroke paint
        circleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circleStrokePaint.setColor(primaryColor);
        circleStrokePaint.setStrokeWidth(lineWidth * 1.5f);
        circleStrokePaint.setStyle(Paint.Style.STROKE);

        // Start invisible
        setVisibility(GONE);
    }

    /**
     * Show the crosshair at the specified screen coordinates.
     * The crosshair will be displayed for the default duration, then fade out.
     *
     * @param x X coordinate in pixels (relative to this view)
     * @param y Y coordinate in pixels (relative to this view)
     */
    public void showAt(float x, float y) {
        showAt(x, y, DEFAULT_DISPLAY_DURATION_MS, null);
    }

    /**
     * Show the crosshair at the specified screen coordinates.
     *
     * @param x             X coordinate in pixels (relative to this view)
     * @param y             Y coordinate in pixels (relative to this view)
     * @param durationMs    How long to display before fading (in milliseconds)
     * @param onHide        Callback when the crosshair is hidden
     */
    public void showAt(float x, float y, int durationMs, @Nullable Runnable onHide) {
        // Cancel any existing animation
        if (fadeAnimator != null && fadeAnimator.isRunning()) {
            fadeAnimator.cancel();
        }

        this.tapX = x;
        this.tapY = y;
        this.alpha = 1.0f;
        this.onHideCallback = onHide;

        // Update paint alphas
        updatePaintAlpha(1.0f);

        setVisibility(VISIBLE);
        invalidate();

        // Schedule fade out
        postDelayed(this::startFadeOut, durationMs);
    }

    /**
     * Start the fade out animation.
     */
    private void startFadeOut() {
        fadeAnimator = ValueAnimator.ofFloat(1.0f, 0.0f);
        fadeAnimator.setDuration(FADE_DURATION_MS);
        fadeAnimator.setInterpolator(new DecelerateInterpolator());
        fadeAnimator.addUpdateListener(animation -> {
            alpha = (float) animation.getAnimatedValue();
            updatePaintAlpha(alpha);
            invalidate();
        });
        fadeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                hide();
                if (onHideCallback != null) {
                    onHideCallback.run();
                }
            }
        });
        fadeAnimator.start();
    }

    /**
     * Update paint alpha values.
     */
    private void updatePaintAlpha(float alpha) {
        int lineAlpha = (int) (255 * alpha);
        int fillAlpha = (int) (80 * alpha);

        linePaint.setAlpha(lineAlpha);
        circlePaint.setAlpha(fillAlpha);
        circleStrokePaint.setAlpha(lineAlpha);
    }

    /**
     * Hide the crosshair immediately.
     */
    public void hide() {
        if (fadeAnimator != null && fadeAnimator.isRunning()) {
            fadeAnimator.cancel();
        }
        tapX = -1;
        tapY = -1;
        setVisibility(GONE);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        if (tapX < 0 || tapY < 0) {
            return;
        }

        int width = getWidth();
        int height = getHeight();

        // Draw vertical line (full height)
        canvas.drawLine(tapX, 0, tapX, height, linePaint);

        // Draw horizontal line (full width)
        canvas.drawLine(0, tapY, width, tapY, linePaint);

        // Draw filled circle at intersection
        canvas.drawCircle(tapX, tapY, circleRadius, circlePaint);

        // Draw circle stroke
        canvas.drawCircle(tapX, tapY, circleRadius, circleStrokePaint);

        // Draw smaller inner circle
        canvas.drawCircle(tapX, tapY, circleRadius * 0.4f, circleStrokePaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (fadeAnimator != null) {
            fadeAnimator.cancel();
        }
    }
}
