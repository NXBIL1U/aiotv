# AIO TV

A native Android app combining live IPTV with Stremio-style VOD powered by TorBox. Supports Fire TV Stick 4K, Android TV / Google TV, and Samsung Galaxy Fold 7 from a single APK.

## Requirements

- JDK 17+ (`JAVA_HOME` set)
- Android SDK (API 35); set `ANDROID_HOME` or `sdk.dir` in `local.properties`
- Internet connection for Gradle to download dependencies on first run

## Build

```bash
# Debug APK (no signing required)
./gradlew assembleDebug

# APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

## Signing a Release Build

**1. Generate a keystore (one-time):**
```bash
keytool -genkeypair -v \
  -keystore aiotv-release.jks \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -alias aiotv
```

**2. Add signing config to `gradle.properties`** (do NOT commit this file):
```properties
RELEASE_STORE_FILE=/path/to/aiotv-release.jks
RELEASE_STORE_PASSWORD=yourPassword
RELEASE_KEY_ALIAS=aiotv
RELEASE_KEY_PASSWORD=yourPassword
```

**3. Wire up in `app/build.gradle.kts`** — add under `android { }`:
```kotlin
signingConfigs {
    create("release") {
        storeFile = file(project.properties["RELEASE_STORE_FILE"] as String)
        storePassword = project.properties["RELEASE_STORE_PASSWORD"] as String
        keyAlias = project.properties["RELEASE_KEY_ALIAS"] as String
        keyPassword = project.properties["RELEASE_KEY_PASSWORD"] as String
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        // ...existing minify config...
    }
}
```

**4. Build:**
```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

## Sideloading to Fire TV

### Method 1 — ADB over Wi-Fi

1. On Fire TV: **Settings → My Fire TV → Developer Options** → enable *ADB Debugging* and *Apps from Unknown Sources*.
2. Find the device IP: **Settings → My Fire TV → About → Network**.
3. On your computer:
```bash
adb connect <FIRE_TV_IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Method 2 — Downloader App

1. Install the **Downloader** app from the Fire TV App Store.
2. Host your APK somewhere accessible (e.g., Nginx, Python HTTP server):
```bash
cd app/build/outputs/apk/debug
python3 -m http.server 8080
```
3. In Downloader enter `http://<your-PC-IP>:8080/app-debug.apk` and install.

## First-Time Setup

Open the app → navigate to **Settings** and configure:

| Field | Description |
|---|---|
| TorBox API Key | From `app.torbox.app` dashboard |
| Xtream Server URL | e.g. `http://my-iptv-provider.com:8080` |
| Xtream Username/Password | From your IPTV provider |
| M3U URL | Alternative: direct M3U playlist link |
| XMLTV EPG URL | Electronic programme guide URL |
| Stremio Addon URL | e.g. `https://v3-cinemeta.strem.io/manifest.json` |

## Architecture

```
data/
  local/        DataStore (settings, watch progress, quality pref), Room cache (channels/categories/EPG)
  remote/
    iptv/       XtreamApi, M3uParser, XmltvParser, RegionClassifier
    stremio/    StremioApi (catalog/meta/stream + Cinemeta search; CINEMETA_HOSTS)
    torbox/     TorBoxApi (cached check, create, poll, dl)
  repository/   IptvRepository, LiveTvRepository, StremioRepository, MetaRepository (Cinemeta),
                SearchRepository (Cinemeta search), TorBoxRepository
domain/
  model/        Channel, EpgProgram, MediaItem, Episode/SeriesMeta, Stream (quality/seeders/[TB+]), WatchProgress
  playback/     PlaybackController (now-playing session: failover + next-episode), BingeSequencing
  StreamRanker (cached→English→quality→seeders), StreamParsing
  usecase/      GetChannels, GetCatalog, GetStreams, SearchVod, ResolveStream
ui/
  theme/        Dark + Netflix-red (tonal red interactive; #E50914 brand-only)
  navigation/   NavHost, Screen sealed class (Player route carries url/title/progressId)
  components/   MediaCard, ContentRail, HeroSection, NavRail
  screen/       home, live, player (auto-next + failover), search (VOD-only), detail (Netflix movie/series), settings, addons
```

- No Google Play Services dependency — works on Fire TV
- Single `AndroidManifest.xml` with both `LAUNCHER` and `LEANBACK_LAUNCHER`
- TV detection via `UiModeManager.UI_MODE_TYPE_TELEVISION`
- Media3/ExoPlayer for all playback (HLS + direct HTTP)
