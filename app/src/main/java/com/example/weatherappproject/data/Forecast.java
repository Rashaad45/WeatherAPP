package com.example.weatherappproject.data;

import java.util.Collections;
import java.util.List;

/**
 * Everything the screen shows for one place. All values are metric
 * (°C, km/h, mm, hPa, metres); conversion happens only when formatting.
 *
 * Times are local wall-clock strings for the place ("2026-11-14T15:00"),
 * exactly as Open-Meteo returns them with timezone=auto.
 */
public final class Forecast {

    public static final class Current {
        public String time;
        public double temperature;
        public double feelsLike;
        public double humidity;
        public double dewPoint;
        public double precipitation;
        public double pressure;
        public double windSpeed;
        public double windDirection;
        public double windGusts;
        public double uvIndex;
        public double visibility;   // metres, NaN if unknown
        public int weatherCode;
        public boolean isDay;
    }

    public static final class Hour {
        public String time;
        public double temperature;
        public int precipitationProbability;
        public int weatherCode;
        public boolean isDay;
    }

    public static final class Day {
        public String date;
        public int weatherCode;
        public double max;
        public double min;
        public String sunrise;
        public String sunset;
        public double uvIndexMax;
        public int precipitationProbabilityMax;
        public double precipitationSum;
    }

    public String timezone = "";
    public int utcOffsetSeconds;
    public Current current = new Current();
    /** Starts at the current hour; normally 25 entries. */
    public List<Hour> hours = Collections.emptyList();
    /** Starts today; normally 10 entries. */
    public List<Day> days = Collections.emptyList();
    /** US AQI, or -1 when air quality is unavailable. */
    public int usAqi = -1;
    public double pm25 = Double.NaN;
    /** When this was downloaded, epoch millis. */
    public long fetchedAt;

    public Day today() {
        return days.isEmpty() ? null : days.get(0);
    }
}
