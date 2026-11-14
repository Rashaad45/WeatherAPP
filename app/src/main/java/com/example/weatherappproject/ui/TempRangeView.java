package com.example.weatherappproject.ui;

import static com.example.weatherappproject.ui.WeatherVisuals.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * One day's low-to-high bar on a shared scale for the whole 10-day list,
 * coloured by actual temperature. Today's row also shows a dot for "now".
 */
public class TempRangeView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint segPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private double scaleMin, scaleMax = 1, dayMin, dayMax, now = Double.NaN;
    private float progress = 1f;

    public TempRangeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        trackPaint.setColor(0x26FFFFFF);
        dotPaint.setColor(0xFFFFFFFF);
        dotRing.setStyle(Paint.Style.STROKE);
        dotRing.setStrokeWidth(dp(context, 2));
        dotRing.setColor(0x66000000);
    }

    /** All values in °C. Pass NaN for nowCelsius on rows other than today. */
    public void setRange(double scaleMin, double scaleMax, double dayMin, double dayMax,
                         double nowCelsius, long delayMs) {
        this.scaleMin = scaleMin;
        this.scaleMax = Math.max(scaleMax, scaleMin + 1);
        this.dayMin = dayMin;
        this.dayMax = dayMax;
        this.now = nowCelsius;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(700);
        a.setStartDelay(delayMs);
        a.setInterpolator(new DecelerateInterpolator(2f));
        a.addUpdateListener(v -> {
            progress = (float) v.getAnimatedValue();
            invalidate();
        });
        progress = 0f;
        a.start();
    }

    private float xFor(double t, float left, float width) {
        return left + (float) ((t - scaleMin) / (scaleMax - scaleMin)) * width;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float h = dp(getContext(), 5);
        float cy = getHeight() / 2f;
        float pad = dp(getContext(), 5);
        float left = pad;
        float width = getWidth() - pad * 2;
        if (width <= 0) return;

        rect.set(left, cy - h / 2, left + width, cy + h / 2);
        canvas.drawRoundRect(rect, h / 2, h / 2, trackPaint);

        float l = xFor(dayMin, left, width);
        float r = xFor(dayMax, left, width);
        float c = (l + r) / 2f;
        float half = Math.max((r - l) / 2f, h / 2) * progress;
        rect.set(c - half, cy - h / 2, c + half, cy + h / 2);
        int[] colors = {
                WeatherVisuals.tempColor(dayMin),
                WeatherVisuals.tempColor((dayMin * 2 + dayMax) / 3),
                WeatherVisuals.tempColor((dayMin + dayMax * 2) / 3),
                WeatherVisuals.tempColor(dayMax)};
        segPaint.setShader(new LinearGradient(l, 0, Math.max(r, l + 1), 0, colors, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, h / 2, h / 2, segPaint);

        if (!Double.isNaN(now) && progress > 0.6f) {
            float x = xFor(Math.max(dayMin, Math.min(dayMax, now)), left, width);
            float rad = dp(getContext(), 4f);
            int alpha = Math.round((progress - 0.6f) / 0.4f * 255);
            dotRing.setAlpha(alpha * 102 / 255);
            dotPaint.setAlpha(alpha);
            canvas.drawCircle(x, cy, rad + dp(getContext(), 1), dotRing);
            canvas.drawCircle(x, cy, rad, dotPaint);
        }
    }
}
