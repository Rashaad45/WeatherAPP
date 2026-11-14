package com.example.weatherappproject.ui;

import static com.example.weatherappproject.ui.WeatherVisuals.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/** The sun's path across the sky today, with the sun at its current position. */
public class SunArcView extends View {

    private static final int SAMPLES = 48;

    private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint travelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint horizonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path full = new Path();
    private final Path travelled = new Path();

    private float target = -1f;   // sun progress 0..1, <0 before sunrise, >1 after sunset
    private float shown = 0f;

    public SunArcView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dashPaint.setStyle(Paint.Style.STROKE);
        dashPaint.setStrokeWidth(dp(context, 1.5f));
        dashPaint.setColor(0x4DFFFFFF);
        dashPaint.setPathEffect(new DashPathEffect(new float[]{dp(context, 3), dp(context, 4)}, 0));
        travelPaint.setStyle(Paint.Style.STROKE);
        travelPaint.setStrokeWidth(dp(context, 2.5f));
        travelPaint.setStrokeCap(Paint.Cap.ROUND);
        travelPaint.setColor(0xFFFFC857);
        horizonPaint.setColor(0x33FFFFFF);
        horizonPaint.setStrokeWidth(dp(context, 1));
        sunPaint.setColor(0xFFFFC857);
    }

    public void setProgress(float progress) {
        target = progress;
        float end = Math.max(0f, Math.min(1f, progress));
        ValueAnimator a = ValueAnimator.ofFloat(0f, end);
        a.setDuration(1200);
        a.setStartDelay(200);
        a.setInterpolator(new DecelerateInterpolator(1.8f));
        a.addUpdateListener(v -> {
            shown = (float) v.getAnimatedValue();
            invalidate();
        });
        a.start();
    }

    private float horizon() {
        return getHeight() * 0.80f;
    }

    private float px(float t) {
        float pad = dp(getContext(), 8);
        return pad + t * (getWidth() - pad * 2);
    }

    private float py(float t) {
        float peak = dp(getContext(), 10);
        return horizon() - (float) Math.sin(Math.PI * t) * (horizon() - peak);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getWidth() == 0) return;
        float hy = horizon();
        canvas.drawLine(0, hy, getWidth(), hy, horizonPaint);

        full.reset();
        travelled.reset();
        for (int i = 0; i <= SAMPLES; i++) {
            float t = i / (float) SAMPLES;
            if (i == 0) full.moveTo(px(t), py(t));
            else full.lineTo(px(t), py(t));
        }
        canvas.drawPath(full, dashPaint);

        boolean up = target >= 0f && target <= 1f;
        if (up && shown > 0f) {
            int steps = Math.max(1, Math.round(SAMPLES * shown));
            for (int i = 0; i <= steps; i++) {
                float t = shown * i / steps;
                if (i == 0) travelled.moveTo(px(t), py(t));
                else travelled.lineTo(px(t), py(t));
            }
            canvas.drawPath(travelled, travelPaint);
        }

        float t = up ? shown : (target < 0 ? 0f : 1f);
        float sx = px(t);
        float sy = py(t);
        float r = dp(getContext(), 6);
        if (up) {
            glowPaint.setShader(new RadialGradient(sx, sy, r * 3.2f,
                    0x66FFC857, 0x00FFC857, Shader.TileMode.CLAMP));
            canvas.drawCircle(sx, sy, r * 3.2f, glowPaint);
            sunPaint.setAlpha(255);
        } else {
            sunPaint.setAlpha(110);
        }
        canvas.drawCircle(sx, sy, r, sunPaint);
    }
}
