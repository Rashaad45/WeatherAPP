package com.example.weatherappproject;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.PopupMenu;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.weatherappproject.data.Forecast;
import com.example.weatherappproject.data.Format;
import com.example.weatherappproject.data.Place;
import com.example.weatherappproject.data.WeatherApi;
import com.example.weatherappproject.data.WeatherCode;
import com.example.weatherappproject.data.WeatherStore;
import com.example.weatherappproject.databinding.ActivityMainBinding;
import com.example.weatherappproject.databinding.ItemDayBinding;
import com.example.weatherappproject.databinding.TileMetricBinding;
import com.example.weatherappproject.ui.ScaleBarView;
import com.example.weatherappproject.ui.SunArcView;
import com.example.weatherappproject.ui.WeatherVisuals;
import com.example.weatherappproject.ui.WindCompassView;
import com.google.android.material.snackbar.Snackbar;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final long STALE_AFTER_MS = 20 * 60 * 1000L;
    private static final int MENU_UNITS = 1;

    private ActivityMainBinding b;
    private WeatherStore store;
    private final WeatherApi api = new WeatherApi();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Place place;
    private Forecast shown;
    private int requestId = 0;

    private final GradientDrawable sky = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xFF1D2C48, 0xFF1D2C48});
    private int[] skyColors = {0xFF1D2C48, 0xFF1D2C48};
    private ValueAnimator skyAnimator;
    private ValueAnimator tempAnimator;
    private double shownTempValue = Double.NaN;

    private ScaleBarView uvBar;
    private ScaleBarView humidityBar;
    private ScaleBarView pressureBar;
    private SunArcView sunArc;
    private WindCompassView compass;

    private final ActivityResultLauncher<Intent> searchLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Place selected = store.selected();
                if (place == null || !selected.key().equals(place.key())) {
                    place = selected;
                    showPlace();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        new WindowInsetsControllerCompat(getWindow(), b.getRoot()).setAppearanceLightStatusBars(false);

        final int baseBottom = b.content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(b.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            b.content.setPadding(b.content.getPaddingLeft(), bars.top,
                    b.content.getPaddingRight(), baseBottom + bars.bottom);
            b.refresh.setProgressViewOffset(false, bars.top, bars.top + (int) WeatherVisuals.dp(this, 64));
            return insets;
        });

        b.sky.setBackground(sky);
        b.refresh.setColorSchemeColors(0xFFFFFFFF);
        b.refresh.setProgressBackgroundColorSchemeColor(0xFF2A3546);
        b.refresh.setOnRefreshListener(() -> refresh(false));

        store = new WeatherStore(this);
        setUpTiles();

        View.OnClickListener openSearch = v -> {
            searchLauncher.launch(new Intent(this, SearchActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        };
        b.btnSearch.setOnClickListener(openSearch);
        b.placeButton.setOnClickListener(openSearch);
        b.btnMore.setOnClickListener(this::showMenu);
        b.stateRetry.setOnClickListener(v -> {
            showLoading();
            refresh(true);
        });

        place = store.selected();
        showPlace();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (shown != null && System.currentTimeMillis() - shown.fetchedAt > STALE_AFTER_MS) {
            b.refresh.setRefreshing(true);
            refresh(false);
        }
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ loading

    private void showPlace() {
        b.cityName.setText(place.name);
        b.hereIcon.setVisibility(place.isCurrentLocation ? View.VISIBLE : View.GONE);
        shown = null;
        Forecast cached = store.cached(place);
        if (cached != null) {
            render(cached, true);
            b.refresh.setRefreshing(true);
            refresh(false);
        } else {
            showLoading();
            refresh(true);
        }
    }

    private void refresh(boolean firstLoad) {
        final int id = ++requestId;
        final Place target = place;
        io.execute(() -> {
            try {
                WeatherApi.Result r = api.forecast(target);
                store.cache(target, r);
                main.post(() -> {
                    if (id != requestId || isFinishing()) return;
                    b.refresh.setRefreshing(false);
                    render(r.forecast, firstLoad || shown == null);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (id != requestId || isFinishing()) return;
                    b.refresh.setRefreshing(false);
                    if (shown == null) {
                        showError();
                    } else {
                        String when = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(shown.fetchedAt));
                        Snackbar.make(b.getRoot(), getString(R.string.refresh_failed, when), Snackbar.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void showLoading() {
        b.refresh.setVisibility(View.INVISIBLE);
        b.stateView.setVisibility(View.VISIBLE);
        b.stateProgress.setVisibility(View.VISIBLE);
        b.stateRetry.setVisibility(View.GONE);
        b.stateMessage.setText(R.string.loading);
    }

    private void showError() {
        b.refresh.setVisibility(View.INVISIBLE);
        b.stateView.setVisibility(View.VISIBLE);
        b.stateProgress.setVisibility(View.GONE);
        b.stateRetry.setVisibility(View.VISIBLE);
        b.stateMessage.setText(R.string.error_offline);
    }

    // ------------------------------------------------------------------ rendering

    private void render(Forecast f, boolean animate) {
        shown = f;
        b.stateView.setVisibility(View.GONE);
        b.refresh.setVisibility(View.VISIBLE);

        Format fmt = new Format(store.imperial());
        Forecast.Current c = f.current;
        Forecast.Day today = f.today();

        animateSky(WeatherVisuals.sky(c.weatherCode, c.isDay));
        b.atmosphere.setWeather(c.weatherCode);

        // Hero
        String updated = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(f.fetchedAt));
        b.dateLine.setText(Format.longDate(f) + "  ·  Updated " + updated);
        animateTemperature(fmt.tempValue(c.temperature), animate);
        b.heroIcon.setImageResource(WeatherVisuals.icon(c.weatherCode, c.isDay));
        b.condition.setText(WeatherCode.describe(c.weatherCode));
        if (today != null) {
            b.hiLo.setText("H " + fmt.temp(today.max) + "   L " + fmt.temp(today.min)
                    + "   ·   Feels like " + fmt.temp(c.feelsLike));
        } else {
            b.hiLo.setText("Feels like " + fmt.temp(c.feelsLike));
        }

        // Hourly
        b.hourlySummary.setText(fmt.hourlySummary(f));
        b.hourlyChart.setData(f.hours, fmt, animate);
        if (animate) b.hourlyScroll.scrollTo(0, 0);

        renderDays(f, fmt, animate);
        renderAirQuality(f);
        renderTiles(f, fmt);

        if (animate) staggerIn();
    }

    private void renderDays(Forecast f, Format fmt, boolean animate) {
        b.dailyList.removeAllViews();
        if (f.days.isEmpty()) return;
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        for (Forecast.Day d : f.days) {
            lo = Math.min(lo, d.min);
            hi = Math.max(hi, d.max);
        }
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < f.days.size(); i++) {
            Forecast.Day d = f.days.get(i);
            ItemDayBinding row = ItemDayBinding.inflate(inflater, b.dailyList, false);
            row.dayDivider.setVisibility(i == 0 ? View.GONE : View.VISIBLE);
            row.dayName.setText(Format.dayLabel(d.date, i));
            row.dayIcon.setImageResource(WeatherVisuals.icon(d.weatherCode, true));
            if (d.precipitationProbabilityMax >= 20) {
                row.dayProb.setVisibility(View.VISIBLE);
                row.dayProb.setText(d.precipitationProbabilityMax + "%");
            }
            row.dayLow.setText(fmt.temp(d.min));
            row.dayHigh.setText(fmt.temp(d.max));
            double now = i == 0 ? f.current.temperature : Double.NaN;
            row.dayRange.setRange(lo, hi, d.min, d.max, now, animate ? 350 + 45L * i : 0);
            b.dailyList.addView(row.getRoot());
        }
    }

    private void renderAirQuality(Forecast f) {
        if (f.usAqi < 0) {
            b.aqiCard.setVisibility(View.GONE);
            return;
        }
        b.aqiCard.setVisibility(View.VISIBLE);
        b.aqiValue.setText(String.valueOf(f.usAqi));
        b.aqiCategory.setText(Format.aqiCategory(f.usAqi));
        b.aqiBar.setValue(WeatherVisuals.AQI_SCALE, f.usAqi / 300f);
        b.aqiCaption.setText(Format.aqiCaption(f.usAqi, f.pm25));
    }

    // ------------------------------------------------------------------ tiles

    private void setUpTiles() {
        title(b.tileUv, R.string.label_uv);
        title(b.tileWind, R.string.label_wind);
        title(b.tileFeels, R.string.label_feels_like);
        title(b.tileHumidity, R.string.label_humidity);
        title(b.tileRain, R.string.label_precipitation);
        title(b.tileVisibility, R.string.label_visibility);
        title(b.tilePressure, R.string.label_pressure);

        uvBar = new ScaleBarView(this, null);
        b.tileUv.tileVisual.addView(uvBar, matchWidth());
        humidityBar = new ScaleBarView(this, null);
        b.tileHumidity.tileVisual.addView(humidityBar, matchWidth());
        pressureBar = new ScaleBarView(this, null);
        b.tilePressure.tileVisual.addView(pressureBar, matchWidth());

        sunArc = new SunArcView(this, null);
        b.tileSun.tileVisual.addView(sunArc, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) WeatherVisuals.dp(this, 58)));

        compass = new WindCompassView(this, null);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                (int) WeatherVisuals.dp(this, 112), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        b.tileWind.tileVisual.addView(compass, lp);
        b.tileWind.tileValue.setVisibility(View.GONE);
    }

    private static FrameLayout.LayoutParams matchWidth() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void title(TileMetricBinding tile, int res) {
        tile.tileTitle.setText(res);
    }

    private void renderTiles(Forecast f, Format fmt) {
        Forecast.Current c = f.current;

        // UV
        double uv = Double.isNaN(c.uvIndex) ? 0 : c.uvIndex;
        b.tileUv.tileValue.setText(String.valueOf(Math.round(uv)));
        subvalue(b.tileUv, Format.uvLevel(uv));
        uvBar.setValue(WeatherVisuals.UV_SCALE, (float) (uv / 11.0));
        b.tileUv.tileCaption.setText(Format.uvCaption(f));

        // Sun
        String[] sun = Format.sunInfo(f);
        b.tileSun.tileTitle.setText(sun[0]);
        b.tileSun.tileValue.setText(sun[1]);
        b.tileSun.tileCaption.setText(sun[2]);
        sunArc.setProgress(Format.sunProgress(f));

        // Wind
        compass.setWind(c.windDirection, String.valueOf(fmt.speedValue(c.windSpeed)), fmt.speedUnit());
        b.tileWind.tileCaption.setText(fmt.windCaption(c));

        // Feels like
        b.tileFeels.tileValue.setText(fmt.temp(c.feelsLike));
        b.tileFeels.tileCaption.setText(fmt.feelsLikeCaption(c));

        // Humidity
        b.tileHumidity.tileValue.setText(Double.isNaN(c.humidity) ? "–" : Math.round(c.humidity) + "%");
        humidityBar.setValue(WeatherVisuals.HUMIDITY_SCALE, (float) (Double.isNaN(c.humidity) ? 0 : c.humidity / 100.0));
        b.tileHumidity.tileCaption.setText(fmt.humidityCaption(c));

        // Rainfall
        Forecast.Day today = f.today();
        b.tileRain.tileValue.setText(fmt.precipitation(today == null ? c.precipitation : today.precipitationSum));
        b.tileRain.tileCaption.setText(fmt.precipitationCaption(f));

        // Visibility
        b.tileVisibility.tileValue.setText(fmt.visibility(c.visibility));
        b.tileVisibility.tileCaption.setText(Format.visibilityCaption(c.visibility));

        // Pressure: scale 980–1040 hPa
        b.tilePressure.tileValue.setText(fmt.pressure(c.pressure));
        float p = Double.isNaN(c.pressure) ? 0.5f : (float) ((c.pressure - 980) / 60.0);
        pressureBar.setValue(WeatherVisuals.PRESSURE_SCALE, p);
        b.tilePressure.tileCaption.setText(Format.pressureCaption(c.pressure));
    }

    private void subvalue(TileMetricBinding tile, String text) {
        tile.tileSubvalue.setVisibility(View.VISIBLE);
        tile.tileSubvalue.setText(text);
    }

    // ------------------------------------------------------------------ animation

    private void animateSky(int[] to) {
        final int[] from = skyColors.clone();
        skyColors = to;
        if (skyAnimator != null) skyAnimator.cancel();
        skyAnimator = ValueAnimator.ofFloat(0f, 1f);
        skyAnimator.setDuration(900);
        skyAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            sky.setColors(new int[]{
                    WeatherVisuals.blend(from[0], to[0], t),
                    WeatherVisuals.blend(from[1], to[1], t)});
        });
        skyAnimator.start();
    }

    private void animateTemperature(double target, boolean animate) {
        if (tempAnimator != null) tempAnimator.cancel();
        if (Double.isNaN(target)) {
            b.temperature.setText("–");
            return;
        }
        double from = Double.isNaN(shownTempValue) ? target - 6 : shownTempValue;
        shownTempValue = target;
        if (!animate && Math.round(from) == Math.round(target)) {
            b.temperature.setText(Math.round(target) + "°");
            return;
        }
        tempAnimator = ValueAnimator.ofFloat((float) from, (float) target);
        tempAnimator.setDuration(900);
        tempAnimator.setInterpolator(new DecelerateInterpolator(2f));
        tempAnimator.addUpdateListener(a ->
                b.temperature.setText(Math.round((float) a.getAnimatedValue()) + "°"));
        tempAnimator.start();
    }

    /** Content rises into place, one block after another. */
    private void staggerIn() {
        float rise = WeatherVisuals.dp(this, 24);
        ViewGroup content = b.content;
        for (int i = 1; i < content.getChildCount(); i++) {   // skip the top bar
            View child = content.getChildAt(i);
            child.animate().cancel();
            child.setAlpha(0f);
            child.setTranslationY(rise);
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(60L * (i - 1))
                    .setDuration(520)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .start();
        }
    }

    // ------------------------------------------------------------------ menu

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        boolean imperial = store.imperial();
        menu.getMenu().add(Menu.NONE, MENU_UNITS, Menu.NONE,
                imperial ? R.string.units_metric : R.string.units_imperial);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_UNITS) {
                store.setImperial(!imperial);
                if (shown != null) render(shown, false);
                return true;
            }
            return false;
        });
        menu.show();
    }
}
