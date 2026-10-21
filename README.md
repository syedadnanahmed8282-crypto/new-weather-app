# Weather

An Android weather app built with Kotlin and Jetpack Compose.

- Live data from [WeatherAPI.com](https://www.weatherapi.com): current weather, hourly and 3-day forecast, city search, alerts
- Live location: follows the phone (while the app is open), place name from the phone's map service
- Pull down to refresh (updates the weather and the location), plus automatic refresh every 10 minutes
- Cute 3D-style animated weather characters: sun, cloud, rain, thunderstorm, wind, snowman, moon
- Living sky: sun or moon, drifting clouds, rain, snow, wind and leaves, floating sparkles, stars, lightning
- Sky color changes with the time of day and the weather
- Frosted glass cards: real blur on Android 12+, milky frosted look on older phones
- Weather-dial pull-to-refresh animation

## Set up

1. Get a free API key at weatherapi.com.
2. Copy `local.properties.example` to `local.properties` (project root) and add your key:
   `WEATHER_API_KEY=your_key_here`
   This file is not uploaded to GitHub.
3. Open the folder in **Android Studio** (Ladybug 2024.2 or newer), let Gradle sync, and run on a device or
   emulator with Android 8.0+ (API 26+).

Gradle wrapper: this repo includes `gradle-wrapper.properties` only. Android Studio handles it on sync.
For command-line builds, run `gradle wrapper` once to generate `gradlew`, then commit it.

## Notes

- Package name is `com.example.weather`. Change it in `app/build.gradle.kts` and the `.kt` files before publishing.
- Default cities are in `DEFAULT_CITIES` in `WeatherViewModel.kt`.
- Keep your API key out of the code. It is read from `local.properties` at build time.

## Structure

```
app/src/main/java/com/example/weather/
  MainActivity.kt        entry point, location permission, live location
  WeatherApp.kt          tabs, auto refresh, screens
  HomeScreen.kt          Today screen with pull to refresh
  CitiesScreen.kt        search, current location, saved cities
  RefreshIndicator.kt    weather-dial refresh animation
  Characters.kt          animated weather characters
  SceneEffects.kt        rain, snow, wind, stars, lightning
  Glass.kt               glass cards, living sky, tab bar
  SkyTheme.kt            time-of-day and weather themes
  Icons.kt               3D-style icons
  WeatherViewModel.kt    state, saved cities, search, location naming
  WeatherApi.kt          WeatherAPI.com client
```
