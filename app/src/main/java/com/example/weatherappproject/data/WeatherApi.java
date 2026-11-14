package com.example.weatherappproject.data;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Open-Meteo client (https://open-meteo.com). Free, no API key, worldwide coverage.
 * Blocking calls — run them off the main thread.
 */
public final class WeatherApi {

    private static final String CURRENT = "temperature_2m,relative_humidity_2m,apparent_temperature,"
            + "dew_point_2m,is_day,precipitation,weather_code,pressure_msl,wind_speed_10m,"
            + "wind_direction_10m,wind_gusts_10m,uv_index,visibility";
    private static final String HOURLY = "temperature_2m,precipitation_probability,weather_code,is_day,"
            + "uv_index,visibility";
    private static final String DAILY = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,"
            + "uv_index_max,precipitation_probability_max,precipitation_sum";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    /** Returns the parsed forecast plus the raw JSON (for caching). */
    public Result forecast(Place place) throws IOException, org.json.JSONException {
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https").host("api.open-meteo.com")
                .addPathSegments("v1/forecast")
                .addQueryParameter("latitude", coord(place.latitude))
                .addQueryParameter("longitude", coord(place.longitude))
                .addQueryParameter("current", CURRENT)
                .addQueryParameter("hourly", HOURLY)
                .addQueryParameter("daily", DAILY)
                .addQueryParameter("timezone", "auto")
                .addQueryParameter("forecast_days", "10")
                .build();
        String forecastJson = get(url);

        String airJson = null;
        try {
            HttpUrl aq = new HttpUrl.Builder()
                    .scheme("https").host("air-quality-api.open-meteo.com")
                    .addPathSegments("v1/air-quality")
                    .addQueryParameter("latitude", coord(place.latitude))
                    .addQueryParameter("longitude", coord(place.longitude))
                    .addQueryParameter("current", "us_aqi,pm2_5")
                    .build();
            airJson = get(aq);
        } catch (IOException ignored) {
            // Air quality is optional; show the forecast without it.
        }

        long now = System.currentTimeMillis();
        return new Result(ForecastParser.parse(forecastJson, airJson, now), forecastJson, airJson, now);
    }

    /** City search over Open-Meteo's worldwide geocoding database. */
    public List<Place> search(String query) throws IOException, org.json.JSONException {
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https").host("geocoding-api.open-meteo.com")
                .addPathSegments("v1/search")
                .addQueryParameter("name", query)
                .addQueryParameter("count", "15")
                .addQueryParameter("language", "en")
                .addQueryParameter("format", "json")
                .build();
        return ForecastParser.parsePlaces(get(url));
    }

    private String get(HttpUrl url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = http.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("HTTP " + response.code());
            }
            return body.string();
        }
    }

    private static String coord(double v) {
        return String.format(Locale.US, "%.4f", v);
    }

    public static final class Result {
        public final Forecast forecast;
        public final String forecastJson;
        public final String airJson;
        public final long fetchedAt;

        Result(Forecast forecast, String forecastJson, String airJson, long fetchedAt) {
            this.forecast = forecast;
            this.forecastJson = forecastJson;
            this.airJson = airJson;
            this.fetchedAt = fetchedAt;
        }
    }
}
