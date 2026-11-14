package com.example.weatherappproject.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Saved places, the selected place, unit preference and the last forecast per place (offline cache). */
public final class WeatherStore {

    private static final String PREFS = "weather";
    private static final String KEY_PLACES = "places";
    private static final String KEY_SELECTED = "selected";
    private static final String KEY_IMPERIAL = "imperial";
    private static final String CACHE_PREFIX = "cache_";

    private final SharedPreferences prefs;

    public WeatherStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ places

    public List<Place> places() {
        List<Place> out = new ArrayList<>();
        String raw = prefs.getString(KEY_PLACES, null);
        if (raw != null) {
            try {
                JSONArray a = new JSONArray(raw);
                for (int i = 0; i < a.length(); i++) out.add(Place.fromJson(a.getJSONObject(i)));
            } catch (JSONException ignored) {
                // corrupted prefs: fall through to the default list
            }
        }
        if (out.isEmpty()) out.add(Place.defaultPlace());
        return out;
    }

    private void savePlaces(List<Place> places) {
        JSONArray a = new JSONArray();
        for (Place p : places) a.put(p.toJson());
        prefs.edit().putString(KEY_PLACES, a.toString()).apply();
    }

    public Place selected() {
        List<Place> places = places();
        String key = prefs.getString(KEY_SELECTED, null);
        for (Place p : places) if (p.key().equals(key)) return p;
        return places.get(0);
    }

    /** Adds the place (or replaces the one with the same key, or the old "current location"), and selects it. */
    public void select(Place place) {
        List<Place> places = places();
        List<Place> updated = new ArrayList<>();
        updated.add(place);
        for (Place p : places) {
            boolean same = p.key().equals(place.key());
            boolean staleHere = place.isCurrentLocation && p.isCurrentLocation;
            if (!same && !staleHere) updated.add(p);
        }
        savePlaces(updated);
        prefs.edit().putString(KEY_SELECTED, place.key()).apply();
    }

    public void remove(Place place) {
        List<Place> updated = new ArrayList<>();
        for (Place p : places()) if (!p.key().equals(place.key())) updated.add(p);
        savePlaces(updated);
        prefs.edit().remove(CACHE_PREFIX + place.key()).apply();
    }

    // ------------------------------------------------------------------ units

    public boolean imperial() {
        return prefs.getBoolean(KEY_IMPERIAL, false);
    }

    public void setImperial(boolean imperial) {
        prefs.edit().putBoolean(KEY_IMPERIAL, imperial).apply();
    }

    // ------------------------------------------------------------------ cache

    public void cache(Place place, WeatherApi.Result result) {
        JSONObject o = new JSONObject();
        try {
            o.put("f", result.forecastJson);
            if (result.airJson != null) o.put("a", result.airJson);
            o.put("t", result.fetchedAt);
        } catch (JSONException ignored) {
            return;
        }
        prefs.edit().putString(CACHE_PREFIX + place.key(), o.toString()).apply();
    }

    /** The last downloaded forecast for this place, or null. */
    public Forecast cached(Place place) {
        String raw = prefs.getString(CACHE_PREFIX + place.key(), null);
        if (raw == null) return null;
        try {
            JSONObject o = new JSONObject(raw);
            String air = o.has("a") ? o.getString("a") : null;
            return ForecastParser.parse(o.getString("f"), air, o.optLong("t"));
        } catch (JSONException e) {
            return null;
        }
    }
}
