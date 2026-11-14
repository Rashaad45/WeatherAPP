package com.example.weatherappproject;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weatherappproject.data.Place;
import com.example.weatherappproject.data.WeatherApi;
import com.example.weatherappproject.data.WeatherStore;
import com.example.weatherappproject.databinding.ActivitySearchBinding;
import com.example.weatherappproject.databinding.ItemHeaderBinding;
import com.example.weatherappproject.databinding.ItemPlaceBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** City search (worldwide, via Open-Meteo geocoding), saved places and "use my location". */
public class SearchActivity extends AppCompatActivity {

    private static final long DEBOUNCE_MS = 300;
    private static final long LOCATION_TIMEOUT_MS = 15000;

    private ActivitySearchBinding b;
    private WeatherStore store;
    private final WeatherApi api = new WeatherApi();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final RowAdapter adapter = new RowAdapter();

    private int searchToken = 0;
    private Runnable pendingSearch;
    private LocationListener locationListener;
    private Runnable locationTimeout;

    private final ActivityResultLauncher<String> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) locate();
                else showSaved(getString(R.string.location_denied));
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        b = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(b.searchRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });

        store = new WeatherStore(this);
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        b.btnBack.setOnClickListener(v -> close());

        b.query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { scheduleSearch(s.toString().trim()); }
        });
        b.query.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (pendingSearch != null) main.removeCallbacks(pendingSearch);
                runSearch(b.query.getText().toString().trim());
                hideKeyboard();
                return true;
            }
            return false;
        });

        showSaved(null);
        b.query.requestFocus();
    }

    @Override
    protected void onDestroy() {
        stopLocationUpdates();
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        close();
    }

    private void close() {
        hideKeyboard();
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void choose(Place place) {
        store.select(place);
        setResult(RESULT_OK);
        close();
    }

    // ------------------------------------------------------------------ lists

    private void showSaved(String message) {
        searchToken++;
        b.searchProgress.setVisibility(View.INVISIBLE);
        List<Row> rows = new ArrayList<>();
        rows.add(Row.location());
        if (message != null) rows.add(Row.header(message));
        rows.add(Row.header(getString(R.string.saved_places)));
        Place selected = store.selected();
        List<Place> places = store.places();
        for (Place p : places) rows.add(Row.place(p, places.size() > 1, p.key().equals(selected.key())));
        adapter.submit(rows);
    }

    private void scheduleSearch(String query) {
        if (pendingSearch != null) main.removeCallbacks(pendingSearch);
        if (query.length() < 2) {
            showSaved(null);
            return;
        }
        pendingSearch = () -> runSearch(query);
        main.postDelayed(pendingSearch, DEBOUNCE_MS);
    }

    private void runSearch(String query) {
        if (query.length() < 2) return;
        final int token = ++searchToken;
        b.searchProgress.setVisibility(View.VISIBLE);
        io.execute(() -> {
            List<Place> results = null;
            try {
                results = api.search(query);
            } catch (Exception ignored) {
                // handled below as a failed search
            }
            final List<Place> found = results;
            main.post(() -> {
                if (token != searchToken || isFinishing()) return;
                b.searchProgress.setVisibility(View.INVISIBLE);
                List<Row> rows = new ArrayList<>();
                if (found == null) {
                    rows.add(Row.header(getString(R.string.search_failed)));
                } else if (found.isEmpty()) {
                    rows.add(Row.header(getString(R.string.no_results, query)));
                } else {
                    rows.add(Row.header(getString(R.string.results)));
                    for (Place p : found) rows.add(Row.place(p, false, false));
                }
                adapter.submit(rows);
            });
        });
    }

    // ------------------------------------------------------------------ location

    private void requestLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locate();
        } else {
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")   // only called after the permission check above
    private void locate() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            showSaved(getString(R.string.location_failed));
            return;
        }
        String provider = null;
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) provider = LocationManager.NETWORK_PROVIDER;
        else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) provider = LocationManager.GPS_PROVIDER;
        if (provider == null) {
            showSaved(getString(R.string.location_off));
            return;
        }

        Location best = null;
        for (String p : lm.getProviders(true)) {
            Location l = lm.getLastKnownLocation(p);
            if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
        }
        if (best != null && System.currentTimeMillis() - best.getTime() < 30 * 60 * 1000L) {
            resolve(best);
            return;
        }

        searchToken++;
        b.searchProgress.setVisibility(View.VISIBLE);
        List<Row> rows = new ArrayList<>();
        rows.add(Row.header(getString(R.string.locating)));
        adapter.submit(rows);

        stopLocationUpdates();
        locationListener = new LocationListener() {
            @Override public void onLocationChanged(@NonNull Location location) {
                stopLocationUpdates();
                resolve(location);
            }
            // Implemented explicitly: these are abstract on Android 10 and older.
            @Override public void onStatusChanged(String p, int status, Bundle extras) { }
            @Override public void onProviderEnabled(@NonNull String p) { }
            @Override public void onProviderDisabled(@NonNull String p) { }
        };
        lm.requestLocationUpdates(provider, 0L, 0f, locationListener, Looper.getMainLooper());
        final Location fallback = best;
        locationTimeout = () -> {
            stopLocationUpdates();
            if (fallback != null) resolve(fallback);
            else showSaved(getString(R.string.location_failed));
        };
        main.postDelayed(locationTimeout, LOCATION_TIMEOUT_MS);
    }

    private void stopLocationUpdates() {
        if (locationTimeout != null) {
            main.removeCallbacks(locationTimeout);
            locationTimeout = null;
        }
        if (locationListener != null) {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) lm.removeUpdates(locationListener);
            locationListener = null;
        }
    }

    /** Turns coordinates into a named place (falls back to "Current location"). */
    private void resolve(Location location) {
        final double lat = location.getLatitude();
        final double lon = location.getLongitude();
        b.searchProgress.setVisibility(View.VISIBLE);
        io.execute(() -> {
            String name = getString(R.string.current_location);
            String admin = "";
            String country = "";
            String cc = "";
            if (Geocoder.isPresent()) {
                try {
                    @SuppressWarnings("deprecation")
                    List<Address> found = new Geocoder(this, Locale.getDefault()).getFromLocation(lat, lon, 1);
                    if (found != null && !found.isEmpty()) {
                        Address a = found.get(0);
                        if (a.getLocality() != null) name = a.getLocality();
                        else if (a.getSubAdminArea() != null) name = a.getSubAdminArea();
                        if (a.getAdminArea() != null) admin = a.getAdminArea();
                        if (a.getCountryName() != null) country = a.getCountryName();
                        if (a.getCountryCode() != null) cc = a.getCountryCode();
                    }
                } catch (Exception ignored) {
                    // no network or no geocoder backend: keep the generic name
                }
            }
            final Place here = new Place(name, admin, country, cc, lat, lon, true);
            main.post(() -> {
                if (!isFinishing()) choose(here);
            });
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(b.query.getWindowToken(), 0);
    }

    // ------------------------------------------------------------------ adapter

    private static final class Row {
        static final int HEADER = 0;
        static final int PLACE = 1;
        static final int LOCATION = 2;

        final int type;
        final String text;
        final Place place;
        final boolean removable;
        final boolean selected;

        private Row(int type, String text, Place place, boolean removable, boolean selected) {
            this.type = type;
            this.text = text;
            this.place = place;
            this.removable = removable;
            this.selected = selected;
        }

        static Row header(String text) { return new Row(HEADER, text, null, false, false); }
        static Row location() { return new Row(LOCATION, null, null, false, false); }
        static Row place(Place p, boolean removable, boolean selected) {
            return new Row(PLACE, null, p, removable, selected);
        }
    }

    private final class RowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<Row> rows = new ArrayList<>();

        @SuppressLint("NotifyDataSetChanged")   // lists are short and fully replaced each time
        void submit(List<Row> newRows) {
            rows.clear();
            rows.addAll(newRows);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == Row.HEADER) {
                return new HeaderHolder(ItemHeaderBinding.inflate(inf, parent, false));
            }
            return new PlaceHolder(ItemPlaceBinding.inflate(inf, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (holder instanceof HeaderHolder) {
                ((HeaderHolder) holder).b.headerText.setText(row.text);
                return;
            }
            ItemPlaceBinding pb = ((PlaceHolder) holder).b;
            if (row.type == Row.LOCATION) {
                pb.placeFlag.setVisibility(View.GONE);
                pb.placeIcon.setVisibility(View.VISIBLE);
                pb.placeIcon.setImageResource(R.drawable.ic_my_location);
                pb.placeName.setText(R.string.use_current_location);
                pb.placeDetail.setVisibility(View.GONE);
                pb.placeRemove.setVisibility(View.GONE);
                pb.getRoot().setOnClickListener(v -> requestLocation());
                return;
            }
            Place p = row.place;
            String flag = p.flagEmoji();
            if (p.isCurrentLocation || flag.isEmpty()) {
                pb.placeFlag.setVisibility(View.GONE);
                pb.placeIcon.setVisibility(View.VISIBLE);
                pb.placeIcon.setImageResource(p.isCurrentLocation ? R.drawable.ic_navigation : R.drawable.ic_place);
            } else {
                pb.placeIcon.setVisibility(View.GONE);
                pb.placeFlag.setVisibility(View.VISIBLE);
                pb.placeFlag.setText(flag);
            }
            pb.placeName.setText(p.name);
            pb.placeName.setTypeface(null, row.selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            String detail = p.subtitle();
            pb.placeDetail.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
            pb.placeDetail.setText(detail);
            pb.placeRemove.setVisibility(row.removable ? View.VISIBLE : View.GONE);
            pb.placeRemove.setOnClickListener(v -> {
                store.remove(p);
                showSaved(null);
            });
            pb.getRoot().setOnClickListener(v -> choose(p));
        }
    }

    private static final class HeaderHolder extends RecyclerView.ViewHolder {
        final ItemHeaderBinding b;
        HeaderHolder(ItemHeaderBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    private static final class PlaceHolder extends RecyclerView.ViewHolder {
        final ItemPlaceBinding b;
        PlaceHolder(ItemPlaceBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
