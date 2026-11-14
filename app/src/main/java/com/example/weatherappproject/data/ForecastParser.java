package com.example.weatherappproject.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Parses Open-Meteo JSON. Pure Java (no Android classes) so it is unit-testable on the JVM. */
public final class ForecastParser {

    private static final int HOURS_TO_SHOW = 25;

    private ForecastParser() { }

    /**
     * @param forecastJson response of api.open-meteo.com/v1/forecast
     * @param airJson      response of air-quality-api.open-meteo.com, or null
     */
    public static Forecast parse(String forecastJson, String airJson, long fetchedAt) throws JSONException {
        JSONObject root = new JSONObject(forecastJson);
        Forecast f = new Forecast();
        f.fetchedAt = fetchedAt;
        f.timezone = root.optString("timezone", "");
        f.utcOffsetSeconds = root.optInt("utc_offset_seconds", 0);

        JSONObject c = root.getJSONObject("current");
        Forecast.Current cur = f.current;
        cur.time = c.getString("time");
        cur.temperature = c.optDouble("temperature_2m", Double.NaN);
        cur.feelsLike = c.optDouble("apparent_temperature", cur.temperature);
        cur.humidity = c.optDouble("relative_humidity_2m", Double.NaN);
        cur.dewPoint = c.optDouble("dew_point_2m", Double.NaN);
        cur.precipitation = c.optDouble("precipitation", 0);
        cur.pressure = c.optDouble("pressure_msl", Double.NaN);
        cur.windSpeed = c.optDouble("wind_speed_10m", 0);
        cur.windDirection = c.optDouble("wind_direction_10m", 0);
        cur.windGusts = c.optDouble("wind_gusts_10m", cur.windSpeed);
        cur.uvIndex = c.optDouble("uv_index", Double.NaN);
        cur.visibility = c.optDouble("visibility", Double.NaN);
        cur.weatherCode = c.optInt("weather_code", 3);
        cur.isDay = c.optInt("is_day", 1) == 1;

        f.hours = parseHours(root.getJSONObject("hourly"), cur.time);
        f.days = parseDays(root.getJSONObject("daily"));

        // Fill gaps in "current" from the matching hour if the API left them out.
        if (Double.isNaN(cur.uvIndex) || Double.isNaN(cur.visibility)) {
            JSONObject h = root.getJSONObject("hourly");
            int i = currentHourIndex(h.getJSONArray("time"), cur.time);
            if (Double.isNaN(cur.uvIndex)) cur.uvIndex = optAt(h.optJSONArray("uv_index"), i, 0);
            if (Double.isNaN(cur.visibility)) cur.visibility = optAt(h.optJSONArray("visibility"), i, Double.NaN);
        }

        if (airJson != null) {
            try {
                JSONObject a = new JSONObject(airJson).optJSONObject("current");
                if (a != null) {
                    double aqi = a.optDouble("us_aqi", Double.NaN);
                    f.usAqi = Double.isNaN(aqi) ? -1 : (int) Math.round(aqi);
                    f.pm25 = a.optDouble("pm2_5", Double.NaN);
                }
            } catch (JSONException ignored) {
                // Air quality is a bonus; a bad response must not break the forecast.
            }
        }
        return f;
    }

    private static List<Forecast.Hour> parseHours(JSONObject h, String currentTime) throws JSONException {
        JSONArray time = h.getJSONArray("time");
        JSONArray temp = h.getJSONArray("temperature_2m");
        JSONArray prob = h.optJSONArray("precipitation_probability");
        JSONArray code = h.optJSONArray("weather_code");
        JSONArray day = h.optJSONArray("is_day");

        int start = currentHourIndex(time, currentTime);
        int end = Math.min(time.length(), start + HOURS_TO_SHOW);
        List<Forecast.Hour> out = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) {
            Forecast.Hour hr = new Forecast.Hour();
            hr.time = time.getString(i);
            hr.temperature = temp.optDouble(i, Double.NaN);
            hr.precipitationProbability = (int) Math.round(optAt(prob, i, 0));
            hr.weatherCode = (int) optAt(code, i, 3);
            hr.isDay = optAt(day, i, 1) == 1;
            out.add(hr);
        }
        return out;
    }

    private static List<Forecast.Day> parseDays(JSONObject d) throws JSONException {
        JSONArray date = d.getJSONArray("time");
        List<Forecast.Day> out = new ArrayList<>(date.length());
        for (int i = 0; i < date.length(); i++) {
            Forecast.Day day = new Forecast.Day();
            day.date = date.getString(i);
            day.weatherCode = (int) optAt(d.optJSONArray("weather_code"), i, 3);
            day.max = optAt(d.optJSONArray("temperature_2m_max"), i, Double.NaN);
            day.min = optAt(d.optJSONArray("temperature_2m_min"), i, Double.NaN);
            day.sunrise = optStringAt(d.optJSONArray("sunrise"), i);
            day.sunset = optStringAt(d.optJSONArray("sunset"), i);
            day.uvIndexMax = optAt(d.optJSONArray("uv_index_max"), i, Double.NaN);
            day.precipitationProbabilityMax = (int) Math.round(optAt(d.optJSONArray("precipitation_probability_max"), i, 0));
            day.precipitationSum = optAt(d.optJSONArray("precipitation_sum"), i, 0);
            out.add(day);
        }
        return out;
    }

    /** Index of the last hourly slot at or before "now" (ISO local strings compare correctly). */
    static int currentHourIndex(JSONArray time, String currentTime) throws JSONException {
        int idx = 0;
        for (int i = 0; i < time.length(); i++) {
            if (time.getString(i).compareTo(currentTime) <= 0) idx = i;
            else break;
        }
        return idx;
    }

    private static double optAt(JSONArray a, int i, double fallback) {
        if (a == null || i < 0 || i >= a.length() || a.isNull(i)) return fallback;
        double v = a.optDouble(i, fallback);
        return Double.isNaN(v) ? fallback : v;
    }

    private static String optStringAt(JSONArray a, int i) {
        if (a == null || i < 0 || i >= a.length() || a.isNull(i)) return null;
        return a.optString(i, null);
    }

    /** Parses the geocoding search response into places. */
    public static List<Place> parsePlaces(String json) throws JSONException {
        JSONArray results = new JSONObject(json).optJSONArray("results");
        List<Place> out = new ArrayList<>();
        if (results == null) return out;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            out.add(new Place(
                    r.optString("name"),
                    r.optString("admin1"),
                    r.optString("country"),
                    r.optString("country_code"),
                    r.getDouble("latitude"),
                    r.getDouble("longitude"),
                    false));
        }
        return out;
    }
}
