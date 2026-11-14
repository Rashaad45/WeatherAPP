# Weather

A native Android weather app written in Java. It covers any city in the world, with no API key needed.

## What it shows

- **Current conditions.** The sky colour changes with the weather and time of day, and rain, snow and storms are animated.
- **24-hour forecast.** A temperature curve, chance of rain for each hour, and a plain-English summary such as "Rain likely around 5 PM."
- **10-day forecast.** Each day's low-to-high range sits on one shared temperature scale, and today's bar marks the current temperature.
- **Air quality.** The US AQI value on a coloured scale, with PM2.5 and health advice.
- **Detail tiles:**
  - UV index
  - Sunrise and sunset, with the sun's position
  - Wind, on a compass
  - Feels-like temperature
  - Humidity and dew point
  - Rainfall
  - Visibility
  - Pressure
- **Search.** Any city, town or airport worldwide. Places you open are saved, and you can use your current location.
- **Works offline.** The last forecast for each place is cached, and you can pull down to refresh.
- **Units.** Switch between metric and imperial from the ⋮ menu.

## Data

[Open-Meteo](https://open-meteo.com) provides all the data. It's free for non-commercial use and needs no API key.

| What | Endpoint |
|---|---|
| Forecast | `api.open-meteo.com/v1/forecast` |
| City search | `geocoding-api.open-meteo.com/v1/search` (GeoNames, worldwide) |
| Air quality | `air-quality-api.open-meteo.com/v1/air-quality` |

Weather data by Open-Meteo.com, licensed CC BY 4.0. The app displays this attribution at the bottom of the screen.

## Run it from VS Code (no Android Studio)

**One-time setup:** install JDK 17 and the Android SDK command-line tools, with `ANDROID_HOME` pointing at the SDK. Then, in VS Code's terminal (PowerShell), from this folder:

```powershell
.\gradlew.bat testDebugUnitTest      # unit tests
.\gradlew.bat installDebug           # build and install on a USB-connected phone
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n com.example.weatherappproject/.MainActivity
```

If the install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, an older copy of the app built elsewhere is on the phone. Uninstall it first:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" uninstall com.example.weatherappproject
```

**Without a local SDK:** every push runs **Actions → Android build** on GitHub. Download the APK from the run's **Artifacts** section.

## Code layout

```
app/src/main/java/com/example/weatherappproject/
├── MainActivity.java        main screen: rendering, refresh, animations
├── SearchActivity.java      city search, saved places, current location
├── data/
│   ├── WeatherApi.java      Open-Meteo HTTP client (OkHttp)
│   ├── ForecastParser.java  JSON → Forecast (pure Java, unit-tested)
│   ├── Forecast.java        data model
│   ├── Format.java          units, time labels, plain-English captions
│   ├── WeatherCode.java     WMO weather codes → text / category
│   ├── Place.java           a location
│   └── WeatherStore.java    saved places, settings, offline cache
└── ui/                      custom-drawn views
    ├── HourlyChartView      24 h temperature curve + rain bars
    ├── TempRangeView        10-day range bars
    ├── SunArcView           sun path
    ├── WindCompassView      wind direction and speed
    ├── ScaleBarView         UV / AQI / humidity / pressure scales
    ├── AtmosphereView       rain, snow and lightning animation
    └── WeatherVisuals       icons, sky colours, temperature colours
```

The weather icons in `res/drawable/wx_*.xml` are custom vector drawables.
