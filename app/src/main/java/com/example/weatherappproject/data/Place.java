package com.example.weatherappproject.data;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/** A location the user can get weather for. */
public final class Place {

    public final String name;
    public final String admin;        // state / region, may be empty
    public final String country;      // may be empty
    public final String countryCode;  // ISO 3166-1 alpha-2, may be empty
    public final double latitude;
    public final double longitude;
    public final boolean isCurrentLocation;

    public Place(String name, String admin, String country, String countryCode,
                 double latitude, double longitude, boolean isCurrentLocation) {
        this.name = name == null ? "" : name;
        this.admin = admin == null ? "" : admin;
        this.country = country == null ? "" : country;
        this.countryCode = countryCode == null ? "" : countryCode.toUpperCase(Locale.US);
        this.latitude = latitude;
        this.longitude = longitude;
        this.isCurrentLocation = isCurrentLocation;
    }

    /** Stable id: coordinates rounded to ~100 m, so the same city found twice is one entry. */
    public String key() {
        return String.format(Locale.US, "%.3f,%.3f", latitude, longitude);
    }

    /** "Tamil Nadu, India" — skips empty or repeated parts. */
    public String subtitle() {
        StringBuilder sb = new StringBuilder();
        if (!admin.isEmpty() && !admin.equalsIgnoreCase(name)) sb.append(admin);
        if (!country.isEmpty() && !country.equalsIgnoreCase(admin)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(country);
        }
        return sb.toString();
    }

    /** Country flag as an emoji, built from the two regional-indicator letters. */
    public String flagEmoji() {
        if (countryCode.length() != 2) return "";
        int first = Character.codePointAt(countryCode, 0) - 'A' + 0x1F1E6;
        int second = Character.codePointAt(countryCode, 1) - 'A' + 0x1F1E6;
        return new String(Character.toChars(first)) + new String(Character.toChars(second));
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("name", name);
            o.put("admin", admin);
            o.put("country", country);
            o.put("cc", countryCode);
            o.put("lat", latitude);
            o.put("lon", longitude);
            o.put("here", isCurrentLocation);
        } catch (JSONException ignored) {
            // put() only throws for non-finite numbers, which coordinates never are
        }
        return o;
    }

    public static Place fromJson(JSONObject o) {
        return new Place(
                o.optString("name"),
                o.optString("admin"),
                o.optString("country"),
                o.optString("cc"),
                o.optDouble("lat"),
                o.optDouble("lon"),
                o.optBoolean("here", false));
    }

    public static Place defaultPlace() {
        return new Place("Mumbai", "Maharashtra", "India", "IN", 19.0760, 72.8777, false);
    }
}
