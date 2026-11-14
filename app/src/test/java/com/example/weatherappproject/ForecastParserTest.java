package com.example.weatherappproject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.weatherappproject.data.Forecast;
import com.example.weatherappproject.data.ForecastParser;
import com.example.weatherappproject.data.Format;
import com.example.weatherappproject.data.Place;
import com.example.weatherappproject.data.WeatherCode;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

public class ForecastParserTest {

    private static String resource(String name) {
        InputStream in = ForecastParserTest.class.getClassLoader().getResourceAsStream(name);
        try (Scanner s = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            return s.next();
        }
    }

    private static Forecast sample() throws Exception {
        return ForecastParser.parse(resource("forecast_sample.json"), resource("air_sample.json"), 0L);
    }

    @Test
    public void parsesCurrentConditions() throws Exception {
        Forecast f = sample();
        assertEquals(29.4, f.current.temperature, 1e-9);
        assertEquals(32.1, f.current.feelsLike, 1e-9);
        assertEquals(2, f.current.weatherCode);
        assertTrue(f.current.isDay);
        assertEquals(19800, f.utcOffsetSeconds);
        assertEquals(72, f.usAqi);
    }

    @Test
    public void hourlyStartsAtCurrentHour() throws Exception {
        Forecast f = sample();
        assertEquals("2026-11-14T15:00", f.hours.get(0).time);
        assertEquals(25, f.hours.size());
    }

    @Test
    public void dailyHandlesNullValues() throws Exception {
        Forecast f = sample();
        assertEquals(2, f.days.size());
        assertEquals(0.0, f.days.get(1).precipitationSum, 1e-9);
        assertEquals("2026-11-14T17:58", f.days.get(0).sunset);
    }

    @Test
    public void missingAirQualityIsTolerated() throws Exception {
        Forecast f = ForecastParser.parse(resource("forecast_sample.json"), null, 0L);
        assertEquals(-1, f.usAqi);
        Forecast g = ForecastParser.parse(resource("forecast_sample.json"), "not json", 0L);
        assertEquals(-1, g.usAqi);
    }

    @Test
    public void parsesGeocodingResults() throws Exception {
        List<Place> places = ForecastParser.parsePlaces(resource("geo_sample.json"));
        assertEquals(2, places.size());
        assertEquals("Coimbatore", places.get(0).name);
        assertEquals("Tamil Nadu, India", places.get(0).subtitle());
        assertEquals("🇮🇳", places.get(0).flagEmoji());
        assertTrue(ForecastParser.parsePlaces("{}").isEmpty());
    }

    @Test
    public void summaryMentionsComingRain() throws Exception {
        String s = new Format(false).hourlySummary(sample());
        assertTrue(s, s.startsWith("Rain likely around 5 PM."));
    }

    @Test
    public void unitsConvert() {
        Format metric = new Format(false);
        Format imperial = new Format(true);
        assertEquals("30°", metric.temp(30));
        assertEquals("86°", imperial.temp(30));
        assertEquals("16 km/h", metric.speed(16));
        assertEquals("10 mph", imperial.speed(16));
        assertEquals("NW", Format.compass(315));
        assertEquals("N", Format.compass(359));
        assertEquals("Moderate", Format.aqiCategory(72));
        assertEquals("High", Format.uvLevel(7.2));
    }

    @Test
    public void weatherCodesMapToCategories() {
        assertEquals(WeatherCode.Category.CLEAR, WeatherCode.category(0));
        assertEquals(WeatherCode.Category.RAIN, WeatherCode.category(81));
        assertEquals(WeatherCode.Category.SNOW, WeatherCode.category(86));
        assertEquals(WeatherCode.Category.STORM, WeatherCode.category(99));
        assertTrue(WeatherCode.isPrecipitating(61));
        assertFalse(WeatherCode.isPrecipitating(3));
    }
}
