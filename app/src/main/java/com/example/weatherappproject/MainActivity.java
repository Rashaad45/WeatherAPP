package com.example.weatherappproject;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private TextView cityNameText, temperatureText, humidityText, descriptionText, windText;
    private ImageView weatherIcon;
    private Button refreshButton;
    private EditText cityNameInput;
    private static final String API_KEY ="e9ef26557245eb8826a4af60a4186804";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        cityNameText = findViewById(R.id.cityNameText);
        temperatureText = findViewById(R.id.temperatureText);
        humidityText = findViewById(R.id.humidityText);
        windText = findViewById(R.id.windText);
        descriptionText = findViewById(R.id.descriptionText);
        weatherIcon = findViewById(R.id.weatherIcon);
        refreshButton = findViewById(R.id.fetchWeatherButton);
        cityNameInput = findViewById(R.id.cityNameInput);

        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String cityName = cityNameInput.getText().toString();
                if(!cityName.isEmpty()) {
                    fetchWeatherData(cityName);
                } else {
                    cityNameInput.setError("Please enter a city name");
                }
            }
        });

        // Fetch weather for default city on app start
        fetchWeatherData("Mumbai");
    }

    private void fetchWeatherData(String cityName) {
        // Fixed URL - added missing slash between https: and api
        String url = "https://api.openweathermap.org/data/2.5/weather?q=" + cityName + "&appid=" + API_KEY + "&units=metric";

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();
            try {
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String result = response.body().string();
                    runOnUiThread(() -> updateUI(result));
                } else {
                    final int statusCode = response.code();
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this,
                                    "Error: " + statusCode + " - City not found or network issue",
                                    Toast.LENGTH_LONG).show()
                    );
                }
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this,
                                "Network error: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    private void updateUI(String result) {
        if(result != null) {
            try {
                JSONObject jsonObject = new JSONObject(result);
                JSONObject main = jsonObject.getJSONObject("main");
                double temperature = main.getDouble("temp");
                double humidity = main.getDouble("humidity");
                double windSpeed = jsonObject.getJSONObject("wind").getDouble("speed");

                String description = jsonObject.getJSONArray("weather")
                        .getJSONObject(0).getString("description");
                String iconCode = jsonObject.getJSONArray("weather")
                        .getJSONObject(0).getString("icon");

                // Handle icon resource more safely
                String resourceName = "ic_" + iconCode;
                int resId = getResources().getIdentifier(resourceName, "drawable", getPackageName());

                // Fallback to default icon if resource not found
                if (resId != 0) {
                    weatherIcon.setImageResource(resId);
                } else {
                    // Set a default icon
                    weatherIcon.setImageResource(R.drawable.ic_01d);
                    Toast.makeText(this, "Weather icon not found: " + iconCode, Toast.LENGTH_SHORT).show();
                }

                cityNameText.setText(jsonObject.getString("name"));
                temperatureText.setText(String.format("%.0f°", temperature));
                humidityText.setText(String.format("%.0f%%", humidity));
                windText.setText(String.format("%.0f km/h", windSpeed));
                descriptionText.setText(description);

            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error parsing weather data: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "No data received from weather service", Toast.LENGTH_LONG).show();
        }
    }
}