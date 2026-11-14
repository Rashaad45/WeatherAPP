package com.example.weatherappproject.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;

import com.example.weatherappproject.R;
import com.example.weatherappproject.data.WeatherCode;

/** Maps weather to icons, sky colours and temperature colours. */
public final class WeatherVisuals {

    private WeatherVisuals() { }

    public static int icon(int code, boolean isDay) {
        switch (WeatherCode.category(code)) {
            case CLEAR: return isDay ? R.drawable.wx_clear_day : R.drawable.wx_clear_night;
            case PARTLY: return isDay ? R.drawable.wx_partly_day : R.drawable.wx_partly_night;
            case FOG: return R.drawable.wx_fog;
            case DRIZZLE: return R.drawable.wx_drizzle;
            case RAIN: return R.drawable.wx_rain;
            case SNOW: return R.drawable.wx_snow;
            case STORM: return R.drawable.wx_thunder;
            case CLOUDY:
            default: return R.drawable.wx_cloudy;
        }
    }

    /**
     * Sky gradient, top to bottom. Colours are deliberately muted and taken from
     * real sky tones rather than saturated "app" gradients.
     */
    public static int[] sky(int code, boolean isDay) {
        switch (WeatherCode.category(code)) {
            case CLEAR:
                return isDay ? pair(0xFF2F6DB5, 0xFF7FAEDC) : pair(0xFF0A1222, 0xFF1D2C48);
            case PARTLY:
                return isDay ? pair(0xFF41709F, 0xFF8AAFCF) : pair(0xFF101827, 0xFF26334A);
            case CLOUDY:
                return isDay ? pair(0xFF55667A, 0xFF8E9CAC) : pair(0xFF161B23, 0xFF2D3540);
            case FOG:
                return isDay ? pair(0xFF6B737C, 0xFFA2A8AE) : pair(0xFF1B1F24, 0xFF363C43);
            case DRIZZLE:
            case RAIN:
                return isDay ? pair(0xFF3B4A5A, 0xFF66788B) : pair(0xFF0E141B, 0xFF222C37);
            case SNOW:
                return isDay ? pair(0xFF6F86A0, 0xFFB3C2D2) : pair(0xFF1A2230, 0xFF3A4658);
            case STORM:
            default:
                return isDay ? pair(0xFF262A35, 0xFF4A5060) : pair(0xFF0C0E14, 0xFF242833);
        }
    }

    private static int[] pair(int top, int bottom) {
        return new int[]{top, bottom};
    }

    private static final float[] TEMP_STOPS = {-10f, 0f, 10f, 20f, 28f, 36f};
    private static final int[] TEMP_COLORS = {
            0xFF9FB8FF, 0xFF8FD3FF, 0xFF7FDCC2, 0xFFF2D675, 0xFFF4A35E, 0xFFEC6A50};

    /** Colour for a temperature in °C, used on the 10-day range bars. */
    public static int tempColor(double celsius) {
        float t = (float) celsius;
        if (t <= TEMP_STOPS[0]) return TEMP_COLORS[0];
        for (int i = 1; i < TEMP_STOPS.length; i++) {
            if (t <= TEMP_STOPS[i]) {
                float f = (t - TEMP_STOPS[i - 1]) / (TEMP_STOPS[i] - TEMP_STOPS[i - 1]);
                return blend(TEMP_COLORS[i - 1], TEMP_COLORS[i], f);
            }
        }
        return TEMP_COLORS[TEMP_COLORS.length - 1];
    }

    public static int blend(int a, int b, float f) {
        float inv = 1f - f;
        return Color.argb(
                Math.round(Color.alpha(a) * inv + Color.alpha(b) * f),
                Math.round(Color.red(a) * inv + Color.red(b) * f),
                Math.round(Color.green(a) * inv + Color.green(b) * f),
                Math.round(Color.blue(a) * inv + Color.blue(b) * f));
    }

    public static final int[] UV_SCALE = {0xFF6FCF7C, 0xFFE9D65A, 0xFFF2A14E, 0xFFE5574A, 0xFFA772D6};
    public static final int[] AQI_SCALE = {0xFF6FCF7C, 0xFFE9D65A, 0xFFF2A14E, 0xFFE5574A, 0xFFA772D6, 0xFF8C3B4A};
    public static final int[] HUMIDITY_SCALE = {0xFFB9DDF5, 0xFF7CC6FF, 0xFF4C8FE0};
    public static final int[] PRESSURE_SCALE = {0xFF8FB3D9, 0xFFDCE6F0, 0xFFF2C77E};

    public static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    public static float sp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
    }
}
