package com.example.weatherappproject.data;

/** Translates WMO weather codes (used by Open-Meteo) into words and visual categories. */
public final class WeatherCode {

    public enum Category { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

    private WeatherCode() { }

    public static Category category(int code) {
        if (code == 0 || code == 1) return Category.CLEAR;
        if (code == 2) return Category.PARTLY;
        if (code == 3) return Category.CLOUDY;
        if (code == 45 || code == 48) return Category.FOG;
        if (code >= 51 && code <= 57) return Category.DRIZZLE;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return Category.RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Category.SNOW;
        if (code >= 95) return Category.STORM;
        return Category.CLOUDY;
    }

    public static String describe(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mostly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45: return "Fog";
            case 48: return "Freezing fog";
            case 51: return "Light drizzle";
            case 53: return "Drizzle";
            case 55: return "Heavy drizzle";
            case 56:
            case 57: return "Freezing drizzle";
            case 61: return "Light rain";
            case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66:
            case 67: return "Freezing rain";
            case 71: return "Light snow";
            case 73: return "Snow";
            case 75: return "Heavy snow";
            case 77: return "Snow grains";
            case 80: return "Light showers";
            case 81: return "Showers";
            case 82: return "Heavy showers";
            case 85: return "Snow showers";
            case 86: return "Heavy snow showers";
            case 95: return "Thunderstorm";
            case 96:
            case 99: return "Thunderstorm with hail";
            default: return "Cloudy";
        }
    }

    /** Is something actually falling from the sky? Drives the rain/snow animation. */
    public static boolean isPrecipitating(int code) {
        Category c = category(code);
        return c == Category.DRIZZLE || c == Category.RAIN || c == Category.SNOW || c == Category.STORM;
    }
}
