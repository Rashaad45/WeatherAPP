package com.example.weatherappproject.ui;

import static com.example.weatherappproject.ui.WeatherVisuals.dp;
import static com.example.weatherappproject.ui.WeatherVisuals.sp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.weatherappproject.data.Forecast;
import com.example.weatherappproject.data.Format;

import java.util.ArrayList;
import java.util.List;

/**
 * 24-hour forecast: time, icon, a smooth temperature curve with labels, and
 * rain-probability bars underneath. Lives inside a HorizontalScrollView.
 */
public class HourlyChartView extends View {

    private final float column;
    private final float sidePad;
    private final float iconSize;
    private final float curveTop;
    private final float curveBottom;
    private final float barTop;
    private final float barBottom;
    private final float viewHeight;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tempPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint probPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path line = new Path();
    private final Path fill = new Path();
    private final RectF rect = new RectF();

    private final List<Forecast.Hour> hours = new ArrayList<>();
    private Drawable[] icons = new Drawable[0];
    private String[] timeLabels = new String[0];
    private String[] tempLabels = new String[0];
    private float[] xs = new float[0];
    private float[] ys = new float[0];

    private float progress = 1f;
    private ValueAnimator animator;

    public HourlyChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        column = dp(context, 56);
        sidePad = dp(context, 4);
        iconSize = dp(context, 28);
        curveTop = dp(context, 92);
        curveBottom = dp(context, 132);
        barTop = dp(context, 150);
        barBottom = dp(context, 172);
        viewHeight = dp(context, 194);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(context, 2.25f));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setColor(0xE6FFFFFF);

        fillPaint.setStyle(Paint.Style.FILL);

        dotPaint.setColor(0xFFFFFFFF);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(context, 1.5f));
        ringPaint.setColor(0x66FFFFFF);

        Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        timePaint.setTextSize(sp(context, 12));
        timePaint.setTextAlign(Paint.Align.CENTER);
        timePaint.setColor(0xB3FFFFFF);
        tempPaint.setTextSize(sp(context, 15));
        tempPaint.setTextAlign(Paint.Align.CENTER);
        tempPaint.setColor(0xFFFFFFFF);
        tempPaint.setTypeface(medium);
        tempPaint.setFontFeatureSettings("tnum");
        probPaint.setTextSize(sp(context, 11));
        probPaint.setTextAlign(Paint.Align.CENTER);
        probPaint.setColor(0xFF9FD4FF);
        probPaint.setTypeface(medium);

        basePaint.setColor(0x1FFFFFFF);
        basePaint.setStrokeWidth(dp(context, 1));
    }

    public void setData(List<Forecast.Hour> data, Format format, boolean animate) {
        hours.clear();
        hours.addAll(data);
        int n = hours.size();
        icons = new Drawable[n];
        timeLabels = new String[n];
        tempLabels = new String[n];
        for (int i = 0; i < n; i++) {
            Forecast.Hour h = hours.get(i);
            Drawable d = ContextCompat.getDrawable(getContext(), WeatherVisuals.icon(h.weatherCode, h.isDay));
            icons[i] = d == null ? null : d.mutate();
            timeLabels[i] = i == 0 ? "Now" : Format.hourLabel(h.time);
            tempLabels[i] = format.temp(h.temperature);
        }
        requestLayout();
        buildPaths();
        if (animate) {
            if (animator != null) animator.cancel();
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1100);
            animator.setStartDelay(150);
            animator.setInterpolator(new DecelerateInterpolator(1.6f));
            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });
            progress = 0f;
            animator.start();
        } else {
            progress = 1f;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = (int) Math.ceil(sidePad * 2 + column * Math.max(hours.size(), 1));
        setMeasuredDimension(w, (int) Math.ceil(viewHeight));
    }

    private void buildPaths() {
        int n = hours.size();
        xs = new float[n];
        ys = new float[n];
        line.reset();
        fill.reset();
        if (n == 0) return;

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Forecast.Hour h : hours) {
            min = Math.min(min, h.temperature);
            max = Math.max(max, h.temperature);
        }
        double span = Math.max(max - min, 1.0);
        for (int i = 0; i < n; i++) {
            xs[i] = sidePad + column * (i + 0.5f);
            double norm = (hours.get(i).temperature - min) / span;
            ys[i] = (float) (curveBottom - norm * (curveBottom - curveTop));
        }

        line.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) {
            float mid = (xs[i - 1] + xs[i]) / 2f;
            line.cubicTo(mid, ys[i - 1], mid, ys[i], xs[i], ys[i]);
        }
        fill.set(line);
        float floor = curveBottom + dp(getContext(), 14);
        fill.lineTo(xs[n - 1], floor);
        fill.lineTo(xs[0], floor);
        fill.close();
        fillPaint.setShader(new LinearGradient(0, curveTop, 0, floor,
                0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = hours.size();
        if (n == 0) return;
        float reveal = progress * getWidth();

        canvas.drawLine(xs[0], barBottom, xs[n - 1], barBottom, basePaint);

        canvas.save();
        canvas.clipRect(0, 0, reveal, getHeight());
        canvas.drawPath(fill, fillPaint);
        canvas.drawPath(line, linePaint);
        canvas.restore();

        float dot = dp(getContext(), 3);
        float barW = dp(getContext(), 16);
        float radius = dp(getContext(), 3);
        for (int i = 0; i < n; i++) {
            float x = xs[i];
            float a = clamp((reveal - (x - column / 2f)) / column);
            if (a <= 0f) continue;
            int alpha = Math.round(a * 255);

            timePaint.setAlpha(Math.round(a * (i == 0 ? 255 : 180)));
            timePaint.setFakeBoldText(i == 0);
            canvas.drawText(timeLabels[i], x, dp(getContext(), 16), timePaint);

            Drawable icon = icons[i];
            if (icon != null) {
                float top = dp(getContext(), 28);
                icon.setBounds(Math.round(x - iconSize / 2), Math.round(top),
                        Math.round(x + iconSize / 2), Math.round(top + iconSize));
                icon.setAlpha(alpha);
                icon.draw(canvas);
            }

            tempPaint.setAlpha(alpha);
            canvas.drawText(tempLabels[i], x, ys[i] - dp(getContext(), 11), tempPaint);

            dotPaint.setAlpha(alpha);
            if (i == 0) {
                ringPaint.setAlpha(Math.round(a * 102));
                canvas.drawCircle(x, ys[i], dot * 2.2f, ringPaint);
            }
            canvas.drawCircle(x, ys[i], i == 0 ? dot * 1.3f : dot, dotPaint);

            int prob = hours.get(i).precipitationProbability;
            if (prob > 0) {
                float h = Math.max((barBottom - barTop) * prob / 100f, dp(getContext(), 2));
                rect.set(x - barW / 2, barBottom - h, x + barW / 2, barBottom);
                barPaint.setColor(0xFF7CC6FF);
                barPaint.setAlpha(Math.round(a * (70 + 185 * prob / 100f)));
                canvas.drawRoundRect(rect, radius, radius, barPaint);
            }
            if (prob >= 10) {
                probPaint.setAlpha(alpha);
                canvas.drawText(prob + "%", x, barBottom + dp(getContext(), 16), probPaint);
            }
        }
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
