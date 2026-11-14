package com.example.weatherappproject.data;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Unit conversion, time labels and the short plain-English captions shown under
 * each metric. Pure Java so it can be unit-tested.
 *
 * Place-local times are handled as "wall-clock millis": the local time string is
 * parsed as if it were UTC. That keeps arithmetic simple and avoids the device's
 * own time zone leaking into another city's times.
 */
public final class Format {

    private final boolean imperial;

    public Format(boolean imperial) {
        this.imperial = imperial;
    }

    public boolean isImperial() {
        return imperial;
    }

    // ------------------------------------------------------------------ units

    public double tempValue(double celsius) {
        return imperial ? celsius * 9.0 / 5.0 + 32.0 : celsius;
    }

    /** "31°" */
    public String temp(double celsius) {
        if (Double.isNaN(celsius)) return "–";
        return Math.round(tempValue(celsius)) + "°";
    }

    public String speedUnit() {
        return imperial ? "mph" : "km/h";
    }

    public long speedValue(double kmh) {
        return Math.round(imperial ? kmh * 0.621371 : kmh);
    }

    /** "12 km/h" */
    public String speed(double kmh) {
        return speedValue(kmh) + " " + speedUnit();
    }

    /** "4.2 mm" / "0.17 in" */
    public String precipitation(double mm) {
        if (imperial) return String.format(Locale.US, "%.2f in", mm / 25.4);
        if (mm > 0 && mm < 10) return String.format(Locale.US, "%.1f mm", mm);
        return Math.round(mm) + " mm";
    }

    /** "1012 hPa" / "29.88 inHg" */
    public String pressure(double hpa) {
        if (Double.isNaN(hpa)) return "–";
        if (imperial) return String.format(Locale.US, "%.2f inHg", hpa * 0.0295300);
        return Math.round(hpa) + " hPa";
    }

    /** "10 km" / "6 mi" */
    public String visibility(double metres) {
        if (Double.isNaN(metres)) return "–";
        double v = imperial ? metres / 1609.344 : metres / 1000.0;
        String unit = imperial ? " mi" : " km";
        if (v < 10) return String.format(Locale.US, "%.1f", v) + unit;
        return Math.round(v) + unit;
    }

    // ------------------------------------------------------------------ time

    private static final ThreadLocal<SimpleDateFormat> ISO = new ThreadLocal<SimpleDateFormat>() {
        @Override protected SimpleDateFormat initialValue() { return utc("yyyy-MM-dd'T'HH:mm"); }
    };
    private static final ThreadLocal<SimpleDateFormat> DATE = new ThreadLocal<SimpleDateFormat>() {
        @Override protected SimpleDateFormat initialValue() { return utc("yyyy-MM-dd"); }
    };

    private static SimpleDateFormat utc(String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f;
    }

    /** Parses "2026-11-14T15:00" (or "2026-11-14") to wall-clock millis; -1 if invalid. */
    public static long wallMillis(String iso) {
        if (iso == null) return -1;
        try {
            return (iso.length() > 10 ? ISO.get() : DATE.get()).parse(iso).getTime();
        } catch (ParseException e) {
            return -1;
        }
    }

    /** The current wall-clock time at the forecast location. */
    public static long placeNow(Forecast f) {
        return System.currentTimeMillis() + f.utcOffsetSeconds * 1000L;
    }

    private static String pattern(String p, long wallMillis) {
        return utc(p).format(new Date(wallMillis));
    }

    /** "3 PM" */
    public static String hourLabel(String iso) {
        long t = wallMillis(iso);
        return t < 0 ? "" : pattern("h a", t);
    }

    /** "6:12 AM" */
    public static String clock(String iso) {
        long t = wallMillis(iso);
        return t < 0 ? "–" : pattern("h:mm a", t);
    }

    /** "Today", then "Mon", "Tue"... */
    public static String dayLabel(String date, int index) {
        if (index == 0) return "Today";
        long t = wallMillis(date);
        return t < 0 ? "" : pattern("EEE", t);
    }

    /** "Saturday, 14 November" in the place's own calendar. */
    public static String longDate(Forecast f) {
        return pattern("EEEE, d MMMM", placeNow(f));
    }

    // ------------------------------------------------------------------ captions

    /** One or two sentences that sum up the next 24 hours. */
    public String hourlySummary(Forecast f) {
        List<Forecast.Hour> hours = f.hours;
        if (hours.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();

        boolean wetNow = WeatherCode.isPrecipitating(f.current.weatherCode);
        String wetWord = precipWord(f.current.weatherCode);
        if (wetNow) {
            sb.append(WeatherCode.describe(f.current.weatherCode)).append(" now.");
            for (int i = 1; i < hours.size(); i++) {
                if (hours.get(i).precipitationProbability < 30) {
                    sb.append(" Easing around ").append(hourLabel(hours.get(i).time)).append('.');
                    break;
                }
            }
        } else {
            int maxProb = 0;
            int firstLikely = -1;
            for (int i = 1; i < hours.size(); i++) {
                Forecast.Hour h = hours.get(i);
                maxProb = Math.max(maxProb, h.precipitationProbability);
                if (firstLikely < 0 && h.precipitationProbability >= 50) firstLikely = i;
            }
            if (firstLikely > 0) {
                Forecast.Hour h = hours.get(firstLikely);
                sb.append(capitalize(precipWord(h.weatherCode))).append(" likely around ")
                        .append(hourLabel(h.time)).append('.');
            } else if (maxProb >= 20) {
                sb.append("Small chance of ").append(wetWord).append(" later (").append(maxProb).append("%).");
            } else {
                sb.append("No rain expected in the next 24 hours.");
            }
        }

        int peak = 0;
        for (int i = 1; i < hours.size(); i++) {
            if (hours.get(i).temperature > hours.get(peak).temperature) peak = i;
        }
        if (peak > 0) {
            sb.append(" High of ").append(temp(hours.get(peak).temperature))
                    .append(" around ").append(hourLabel(hours.get(peak).time)).append('.');
        } else {
            int low = 0;
            for (int i = 1; i < hours.size(); i++) {
                if (hours.get(i).temperature < hours.get(low).temperature) low = i;
            }
            if (low > 0) {
                sb.append(" Dropping to ").append(temp(hours.get(low).temperature))
                        .append(" by ").append(hourLabel(hours.get(low).time)).append('.');
            }
        }
        return sb.toString();
    }

    private static String precipWord(int code) {
        return WeatherCode.category(code) == WeatherCode.Category.SNOW ? "snow" : "rain";
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public String feelsLikeCaption(Forecast.Current c) {
        double diff = c.feelsLike - c.temperature;
        if (Math.abs(diff) < 1.5) return "Similar to the actual temperature.";
        if (diff > 0) {
            return c.humidity >= 60 ? "Humidity is making it feel warmer." : "Feels warmer than it is.";
        }
        return c.windSpeed >= 15 ? "Wind is making it feel cooler." : "Feels cooler than it is.";
    }

    public String humidityCaption(Forecast.Current c) {
        if (Double.isNaN(c.dewPoint)) return "";
        return "The dew point is " + temp(c.dewPoint) + " right now.";
    }

    public static String uvLevel(double uv) {
        if (uv < 3) return "Low";
        if (uv < 6) return "Moderate";
        if (uv < 8) return "High";
        if (uv < 11) return "Very high";
        return "Extreme";
    }

    public static String uvCaption(Forecast f) {
        Forecast.Day d = f.today();
        if (d == null || Double.isNaN(d.uvIndexMax)) return "";
        double peak = d.uvIndexMax;
        if (peak >= 6) return "Peaks at " + Math.round(peak) + " today. Use sun protection around midday.";
        if (peak >= 3) return "Peaks at " + Math.round(peak) + " today. Moderate around midday.";
        return "Stays low for the rest of the day.";
    }

    private static final String[] COMPASS = {
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};

    public static String compass(double degrees) {
        int i = (int) Math.round(((degrees % 360) + 360) % 360 / 22.5) % 16;
        return COMPASS[i];
    }

    public String windCaption(Forecast.Current c) {
        return "Gusts up to " + speed(c.windGusts) + " from the " + compass(c.windDirection) + ".";
    }

    public static String visibilityCaption(double metres) {
        if (Double.isNaN(metres)) return "";
        if (metres >= 10000) return "Clear view.";
        if (metres >= 5000) return "Light haze.";
        if (metres >= 1000) return "Haze is reducing visibility.";
        return "Fog is limiting visibility.";
    }

    public static String pressureCaption(double hpa) {
        if (Double.isNaN(hpa)) return "";
        if (hpa >= 1020) return "High pressure. Usually settled weather.";
        if (hpa <= 1000) return "Low pressure. Unsettled weather is likely.";
        return "Within the normal range.";
    }

    public String precipitationCaption(Forecast f) {
        if (f.days.size() < 2) return "Expected today.";
        double tomorrow = f.days.get(1).precipitationSum;
        if (tomorrow < 0.1) return "Expected today. None expected tomorrow.";
        return "Expected today. " + precipitation(tomorrow) + " tomorrow.";
    }

    public static String aqiCategory(int aqi) {
        if (aqi <= 50) return "Good";
        if (aqi <= 100) return "Moderate";
        if (aqi <= 150) return "Unhealthy for sensitive groups";
        if (aqi <= 200) return "Unhealthy";
        if (aqi <= 300) return "Very unhealthy";
        return "Hazardous";
    }

    public static String aqiCaption(int aqi, double pm25) {
        String advice;
        if (aqi <= 50) advice = "Air quality is good for outdoor activity.";
        else if (aqi <= 100) advice = "Acceptable. Unusually sensitive people may want to limit long outdoor effort.";
        else if (aqi <= 150) advice = "Sensitive groups should reduce long or intense outdoor activity.";
        else if (aqi <= 200) advice = "Everyone should reduce long or intense outdoor activity.";
        else advice = "Avoid outdoor activity if you can.";
        if (!Double.isNaN(pm25)) advice += String.format(Locale.US, " PM2.5 is %.0f µg/m³.", pm25);
        return advice;
    }

    /** Sun tile: [headline label, headline time, caption]. */
    public static String[] sunInfo(Forecast f) {
        Forecast.Day today = f.today();
        if (today == null) return new String[]{"Sunset", "–", ""};
        long now = placeNow(f);
        long rise = wallMillis(today.sunrise);
        long set = wallMillis(today.sunset);
        String daylight = "";
        if (rise > 0 && set > rise) {
            long mins = (set - rise) / 60000;
            daylight = (mins / 60) + "h " + (mins % 60) + "m of daylight today.";
        }
        if (now < rise) {
            return new String[]{"Sunrise", clock(today.sunrise), "Sunset " + clock(today.sunset) + ". " + daylight};
        }
        if (now < set) {
            return new String[]{"Sunset", clock(today.sunset), "Sunrise was " + clock(today.sunrise) + ". " + daylight};
        }
        String nextRise = f.days.size() > 1 ? f.days.get(1).sunrise : today.sunrise;
        return new String[]{"Sunrise", clock(nextRise), "Sunset was " + clock(today.sunset) + ". " + daylight};
    }

    /** Where the sun is between sunrise (0) and sunset (1); <0 before, >1 after. */
    public static float sunProgress(Forecast f) {
        Forecast.Day today = f.today();
        if (today == null) return -1;
        long rise = wallMillis(today.sunrise);
        long set = wallMillis(today.sunset);
        if (rise < 0 || set <= rise) return -1;
        return (float) (placeNow(f) - rise) / (set - rise);
    }
}
