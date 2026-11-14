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

/** A thin coloured scale (UV, air quality, humidity...) with a marker sliding to the current value. */
public class ScaleBarView extends View {

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int[] colors = WeatherVisuals.UV_SCALE;
    private float target = 0f;
    private float shown = 0f;

    public ScaleBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        markerPaint.setColor(0xFFFFFFFF);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(context, 2));
        ringPaint.setColor(0x59000000);
    }

    /** @param fraction 0..1 position of the marker along the scale. */
    public void setValue(int[] scaleColors, float fraction) {
        colors = scaleColors;
        target = Math.max(0f, Math.min(1f, fraction));
        barPaint.setShader(null);
        ValueAnimator a = ValueAnimator.ofFloat(0f, target);
        a.setDuration(900);
        a.setStartDelay(250);
        a.setInterpolator(new DecelerateInterpolator(1.8f));
        a.addUpdateListener(v -> {
            shown = (float) v.getAnimatedValue();
            invalidate();
        });
        a.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        barPaint.setShader(null);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (int) dp(getContext(), 14));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float h = dp(getContext(), 5);
        float pad = dp(getContext(), 6);
        float cy = getHeight() / 2f;
        float w = getWidth() - pad * 2;
        if (w <= 0) return;
        if (barPaint.getShader() == null) {
            barPaint.setShader(new LinearGradient(pad, 0, pad + w, 0, colors, null, Shader.TileMode.CLAMP));
        }
        rect.set(pad, cy - h / 2, pad + w, cy + h / 2);
        canvas.drawRoundRect(rect, h / 2, h / 2, barPaint);

        float x = pad + shown * w;
        float r = dp(getContext(), 5);
        canvas.drawCircle(x, cy, r + dp(getContext(), 1), ringPaint);
        canvas.drawCircle(x, cy, r, markerPaint);
    }
}
