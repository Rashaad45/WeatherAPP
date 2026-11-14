package com.example.weatherappproject.ui;

import static com.example.weatherappproject.ui.WeatherVisuals.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.weatherappproject.data.WeatherCode;

import java.util.Random;

/**
 * Subtle falling rain or snow behind the content, plus the occasional lightning
 * flash during storms. Draws nothing (and costs nothing) in dry weather.
 */
public class AtmosphereView extends View {

    private enum Mode { NONE, DRIZZLE, RAIN, SNOW, STORM }

    private static final int MAX_PARTICLES = 110;

    private final Random random = new Random();
    private final Paint rainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint snowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPaint = new Paint();

    private final float[] px = new float[MAX_PARTICLES];
    private final float[] py = new float[MAX_PARTICLES];
    private final float[] speed = new float[MAX_PARTICLES];
    private final float[] size = new float[MAX_PARTICLES];
    private final float[] phase = new float[MAX_PARTICLES];

    private Mode mode = Mode.NONE;
    private int count = 0;
    private long lastFrame = 0;
    private float flash = 0f;
    private long nextFlashAt = 0;
    private float fade = 0f;   // fades particles in when the mode changes

    public AtmosphereView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        rainPaint.setColor(0xFFFFFFFF);
        rainPaint.setStrokeWidth(dp(context, 1.2f));
        rainPaint.setStrokeCap(Paint.Cap.ROUND);
        snowPaint.setColor(0xFFFFFFFF);
        flashPaint.setColor(0xFFFFFFFF);
    }

    public void setWeather(int code) {
        Mode next;
        switch (WeatherCode.category(code)) {
            case DRIZZLE: next = Mode.DRIZZLE; break;
            case RAIN: next = Mode.RAIN; break;
            case SNOW: next = Mode.SNOW; break;
            case STORM: next = Mode.STORM; break;
            default: next = Mode.NONE;
        }
        if (next == mode) return;
        mode = next;
        count = mode == Mode.NONE ? 0 : (mode == Mode.DRIZZLE ? 45 : (mode == Mode.SNOW ? 70 : MAX_PARTICLES));
        fade = 0f;
        if (getWidth() > 0) seed();
        nextFlashAt = SystemClock.uptimeMillis() + 3000 + random.nextInt(6000);
        lastFrame = 0;
        invalidate();
    }

    private void seed() {
        for (int i = 0; i < count; i++) respawn(i, true);
    }

    private void respawn(int i, boolean anywhere) {
        float w = getWidth();
        float h = getHeight();
        px[i] = random.nextFloat() * (w + dp(getContext(), 60)) - dp(getContext(), 30);
        py[i] = anywhere ? random.nextFloat() * h : -dp(getContext(), 30) * random.nextFloat();
        phase[i] = random.nextFloat() * 6.28f;
        if (mode == Mode.SNOW) {
            speed[i] = dp(getContext(), 30 + random.nextFloat() * 45);
            size[i] = dp(getContext(), 1.2f + random.nextFloat() * 2.0f);
        } else if (mode == Mode.DRIZZLE) {
            speed[i] = dp(getContext(), 520 + random.nextFloat() * 260);
            size[i] = dp(getContext(), 7 + random.nextFloat() * 6);
        } else {
            speed[i] = dp(getContext(), 850 + random.nextFloat() * 450);
            size[i] = dp(getContext(), 13 + random.nextFloat() * 10);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        seed();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mode == Mode.NONE) return;
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min((now - lastFrame) / 1000f, 0.05f);
        lastFrame = now;
        fade = Math.min(1f, fade + dt * 0.8f);
        float h = getHeight();
        float slant = 0.18f;

        for (int i = 0; i < count; i++) {
            py[i] += speed[i] * dt;
            if (mode == Mode.SNOW) {
                px[i] += (float) Math.sin(now / 1000.0 + phase[i]) * dp(getContext(), 12) * dt;
            } else {
                px[i] -= speed[i] * slant * dt;
            }
            if (py[i] - size[i] > h) respawn(i, false);

            if (mode == Mode.SNOW) {
                snowPaint.setAlpha(Math.round(fade * (90 + 60 * (size[i] / dp(getContext(), 3.2f)))));
                canvas.drawCircle(px[i], py[i], size[i], snowPaint);
            } else {
                int base = mode == Mode.DRIZZLE ? 55 : 75;
                rainPaint.setAlpha(Math.round(fade * (base + 40 * phase[i] / 6.28f)));
                canvas.drawLine(px[i], py[i], px[i] + size[i] * slant, py[i] - size[i], rainPaint);
            }
        }

        if (mode == Mode.STORM) {
            if (now >= nextFlashAt) {
                flash = 1f;
                nextFlashAt = now + 5000 + random.nextInt(9000);
            }
            if (flash > 0f) {
                flashPaint.setAlpha(Math.round(flash * 46));
                canvas.drawRect(0, 0, getWidth(), h, flashPaint);
                flash = Math.max(0f, flash - dt * 3.5f);
            }
        }
        postInvalidateOnAnimation();
    }
}
