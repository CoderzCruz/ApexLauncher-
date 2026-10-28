package net.kdt.pojavlaunch.dashboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Live 12-hour clock + zero-permission weather updater with graceful caching & offline fallback.
 */
public class LiveWeatherUpdater {

    public interface WeatherListener {
        void onClockTick(@NonNull String formattedTime);
        void onWeatherUpdated(@NonNull String tempText, @NonNull String conditionText,
                              @NonNull String locationText, @DrawableRes int iconRes);
    }

    private static final String PREF_WEATHER = "apex_aurora_weather";
    private static final String KEY_TEMP_C = "temp_c";
    private static final String KEY_COND = "condition";
    private static final String KEY_LOC = "location";
    private static final String KEY_CODE = "weather_code";
    private static final String KEY_USE_F = "use_fahrenheit";
    private static final String KEY_FETCH_TS = "last_fetch_ms";
    private static final long CACHE_TTL_MS = 25L * 60_000L;

    private final Context mContext;
    private final WeatherListener mListener;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mRunning = false;

    private final Runnable mClockRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mRunning) return;
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
            mListener.onClockTick(sdf.format(new Date()));
            mHandler.postDelayed(this, 15_000L);
        }
    };

    public LiveWeatherUpdater(@NonNull Context context, @NonNull WeatherListener listener) {
        mContext = context.getApplicationContext();
        mListener = listener;
    }

    public void start() {
        if (mRunning) return;
        mRunning = true;
        mClockRunnable.run();
        publishCachedWeather();

        SharedPreferences prefs = mContext.getSharedPreferences(PREF_WEATHER, Context.MODE_PRIVATE);
        long age = System.currentTimeMillis() - prefs.getLong(KEY_FETCH_TS, 0L);
        if (age > CACHE_TTL_MS) {
            refreshWeatherAsync(false);
        }
    }

    public void stop() {
        mRunning = false;
        mHandler.removeCallbacks(mClockRunnable);
    }

    public void toggleUnitAndRefresh() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREF_WEATHER, Context.MODE_PRIVATE);
        boolean useF = !prefs.getBoolean(KEY_USE_F, false);
        prefs.edit().putBoolean(KEY_USE_F, useF).apply();
        publishCachedWeather();
        refreshWeatherAsync(true);
    }

    public void refreshWeatherAsync(final boolean force) {
        if (!Tools.isOnline(mContext)) return;
        PojavApplication.sExecutorService.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://wttr.in/?format=j1");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "ApexLauncher/1.0");
                if (conn.getResponseCode() != 200) return;

                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }

                JsonObject root = Tools.GLOBAL_GSON.fromJson(sb.toString(), JsonObject.class);
                if (root == null) return;
                JsonArray currentArr = root.getAsJsonArray("current_condition");
                if (currentArr == null || currentArr.size() == 0) return;
                JsonObject cur = currentArr.get(0).getAsJsonObject();

                int tempC = Integer.parseInt(cur.get("temp_C").getAsString());
                int code = Integer.parseInt(cur.get("weatherCode").getAsString());
                String cond = "Partly Cloudy";
                JsonArray descArr = cur.getAsJsonArray("weatherDesc");
                if (descArr != null && descArr.size() > 0) {
                    cond = descArr.get(0).getAsJsonObject().get("value").getAsString();
                }

                String loc = fallbackLocationName();
                JsonArray areaArr = root.getAsJsonArray("nearest_area");
                if (areaArr != null && areaArr.size() > 0) {
                    JsonArray areaNames = areaArr.get(0).getAsJsonObject().getAsJsonArray("areaName");
                    if (areaNames != null && areaNames.size() > 0) {
                        String parsedCity = areaNames.get(0).getAsJsonObject().get("value").getAsString();
                        if (Tools.isValidString(parsedCity)) loc = parsedCity;
                    }
                }

                SharedPreferences prefs = mContext.getSharedPreferences(PREF_WEATHER, Context.MODE_PRIVATE);
                prefs.edit()
                        .putInt(KEY_TEMP_C, tempC)
                        .putString(KEY_COND, cond)
                        .putString(KEY_LOC, loc)
                        .putInt(KEY_CODE, code)
                        .putLong(KEY_FETCH_TS, System.currentTimeMillis())
                        .apply();

                mHandler.post(this::publishCachedWeather);
            } catch (Exception ignored) {
                // Keep cached / default weather cleanly without disturbing user
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void publishCachedWeather() {
        if (!mRunning) return;
        SharedPreferences prefs = mContext.getSharedPreferences(PREF_WEATHER, Context.MODE_PRIVATE);
        int tempC = prefs.getInt(KEY_TEMP_C, 24);
        boolean useF = prefs.getBoolean(KEY_USE_F, false);
        String cond = prefs.getString(KEY_COND, "Partly Cloudy");
        String loc = prefs.getString(KEY_LOC, fallbackLocationName());
        int code = prefs.getInt(KEY_CODE, 116);

        String tempDisplay = useF
                ? Math.round(tempC * 9f / 5f + 32f) + "°F"
                : tempC + "°C";

        mListener.onWeatherUpdated(tempDisplay, cond, loc, resolveWeatherIcon(code, cond));
    }

    @NonNull
    private String fallbackLocationName() {
        String tzId = TimeZone.getDefault().getID();
        if (tzId != null && tzId.contains("/")) {
            String city = tzId.substring(tzId.lastIndexOf('/') + 1).replace('_', ' ');
            if (!city.isEmpty() && !"UTC".equalsIgnoreCase(city) && !"GMT".equalsIgnoreCase(city)) {
                return city;
            }
        }
        return "Voxelia";
    }

    @DrawableRes
    private int resolveWeatherIcon(int code, String cond) {
        String lower = cond == null ? "" : cond.toLowerCase(Locale.ROOT);
        if (code == 113 || lower.contains("sunny") || lower.equals("clear")) {
            return R.drawable.ic_aurora_weather_clear;
        }
        if (lower.contains("rain") || lower.contains("drizzle") || lower.contains("shower") || lower.contains("thunder")) {
            return R.drawable.ic_aurora_weather_rain;
        }
        return R.drawable.ic_aurora_weather_partly_cloudy;
    }
}
