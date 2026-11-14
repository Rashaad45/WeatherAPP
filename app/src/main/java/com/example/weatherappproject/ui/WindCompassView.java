package com.example.weatherappproject.ui;

import static com.example.weatherappproject.ui.WeatherVisuals.dp;
import static com.example.weatherappproject.ui.WeatherVisuals.sp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

/** Compass dial with an arrow showing where the wind is blowing to, and the speed in the middle. */
public class WindCompassView extends View {

    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path head = new Path();

    private float angle = 0f;     // degrees, arrow direction (wind travel direction)
    private String value = "–";
    private String unit = "";
    private ValueAnimator animator;

    public WindCompassView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        tickPaint.setColor(0x4DFFFFFF);
        tickPaint.setStrokeWidth(dp(context, 1));
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        letterPaint.setColor(0x99FFFFFF);
        letterPaint.setTextSize(sp(context, 10));
        letterPaint.setTextAlign(Paint.Align.CENTER);
        letterPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        arrowPaint.setColor(0xFFFFFFFF);
        arrowPaint.setStrokeWidth(dp(context, 2));
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        headPaint.setColor(0xFFFFFFFF);
        valuePaint.setColor(0xFFFFFFFF);
        valuePaint.setTextSize(sp(context, 20));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        valuePaint.setFontFeatureSettings("tnum");
        unitPaint.setColor(0xB3FFFFFF);
        unitPaint.setTextSize(sp(context, 10));
        unitPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** @param fromDegrees meteorological direction the wind comes from (0 = north). */
    public void setWind(double fromDegrees, String speedValue, String speedUnit) {
        value = speedValue;
        unit = speedUnit;
        float to = (float) ((fromDegrees + 180) % 360);
        float start = angle;
        float delta = ((to - start) % 360 + 540) % 360 - 180;   // shortest turn
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(start, start + delta);
        animator.setDuration(1100);
        animator.setStartDelay(250);
        animator.setInterpolator(new OvershootInterpolator(1.4f));
        animator.addUpdateListener(a -> {
            angle = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(w, w);   // always square
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - dp(getContext(), 2);
        if (r <= 0) return;

        for (int deg = 0; deg < 360; deg += 6) {
            boolean major = deg % 90 == 0;
            if (major) continue;  // letters go there
            float len = deg % 30 == 0 ? dp(getContext(), 7) : dp(getContext(), 4);
            tickPaint.setAlpha(deg % 30 == 0 ? 140 : 77);
            double rad = Math.toRadians(deg);
            float sx = (float) Math.sin(rad);
            float sy = (float) -Math.cos(rad);
            canvas.drawLine(cx + sx * r, cy + sy * r, cx + sx * (r - len), cy + sy * (r - len), tickPaint);
        }
        float lr = r - dp(getContext(), 6);
        float textOffset = -(letterPaint.ascent() + letterPaint.descent()) / 2f;
        canvas.drawText("N", cx, cy - lr + textOffset, letterPaint);
        canvas.drawText("S", cx, cy + lr + textOffset, letterPaint);
        canvas.drawText("E", cx + lr, cy + textOffset, letterPaint);
        canvas.drawText("W", cx - lr, cy + textOffset, letterPaint);

        // Arrow, drawn in two parts so it does not run through the number in the middle.
        float inner = r * 0.36f;
        float outer = r * 0.72f;
        canvas.save();
        canvas.rotate(angle, cx, cy);
        canvas.drawLine(cx, cy + outer, cx, cy + inner, arrowPaint);
        canvas.drawCircle(cx, cy + outer, dp(getContext(), 3), headPaint);
        canvas.drawLine(cx, cy - inner, cx, cy - outer + dp(getContext(), 4), arrowPaint);
        float hw = dp(getContext(), 6);
        float hl = dp(getContext(), 10);
        head.reset();
        head.moveTo(cx, cy - outer - dp(getContext(), 2));
        head.lineTo(cx - hw, cy - outer + hl);
        head.lineTo(cx + hw, cy - outer + hl);
        head.close();
        canvas.drawPath(head, headPaint);
        canvas.restore();

        canvas.drawText(value, cx, cy + dp(getContext(), 3), valuePaint);
        canvas.drawText(unit, cx, cy + dp(getContext(), 16), unitPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
