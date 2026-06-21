# Phase 2 Implementation Report — Region/Language classifier + filter

Date: 2026-06-22  
Branch: `feat/live-tv-ux-v2`  
Commits: `e444789`…`a23da5a`

---

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/itrepos/aiotv/data/remote/iptv/RegionClassifier.kt` | **Created** — pure `object` classifier |
| `app/src/test/java/com/itrepos/aiotv/RegionClassifierTest.kt` | **Created** — 8 unit-test cases |
| `gradle/libs.versions.toml` | Added `junit = "4.13.2"` version + `junit` library entry |
| `app/build.gradle.kts` | Added `testImplementation(libs.junit)` |
| `app/src/main/java/com/itrepos/aiotv/data/repository/LiveTvRepository.kt` | Import `RegionClassifier`; set `regionTag` for categories and channels in `refresh()` |
| `app/src/main/java/com/itrepos/aiotv/data/local/AppDataStore.kt` | Added `KEY_LIVE_REGIONS`, `DEFAULT_LIVE_REGIONS`, `liveRegions` Flow, `setLiveRegions()` |
| `app/src/main/java/com/itrepos/aiotv/ui/screen/live/LiveTvViewModel.kt` | Rewrote `observeRoomFlows()` to use `flatMapLatest` on `liveRegions`; added `selectedRegions` to state; added `setRegions()`; global search via `repository.searchChannels()` |

---

## `RegionClassifier.classify` rules (as implemented)

Matching is done on the upper-cased input; rules are evaluated **in order, first match wins**:

1. `"LATIN AMERICA"` → **LATAM** (before US so "LATIN AMERICA" is not caught by "AMERICA")
2. `"LATINO"` → **LATAM**
3. Whole-token country codes DO, EC, HN, HT, PA, MX, CO → **LATAM** (before MENA so "CO" isn't ambiguous)
4. `"AR/AF"` → **MENA** (before AR-alone so "AR/AF" wins)
5. `"ARAB"`, `"AFRICA"`, `"ASIAN"` → **MENA**
6. Whole-token `"AF"` → **MENA**
7. Whole-token `"AR"` (lone Argentina) → **LATAM**
8. Whole-token `"USA"` → **US**; whole-token `"US"` → **US**; `"AMERICAN"` → **US**
9. `"AMERICA"` (without LATIN prefix, caught above) → **LATAM**
10. Whole-token `"UK"`, `"GB"` → **UK**; `"UNITED KINGDOM"`, `"BRITISH"`, `"ENGLAND"`, `"IRELAND"`, `"IRISH"` → **UK**
11. `"ENGLISH"`, `"24/7"` → **EN**
12. `"EUROP"` (substring, catches EUROPE, WEST EUROP, EAST EUROP) → **EU**
13. Anything else → **OTHER**

Token matching uses a `containsToken()` helper that checks the character before and after the match is non-alphanumeric, preventing "US" from matching inside "MUSIC" or "RUSSIAN".

---

## New `AppDataStore` methods

```kotlin
val KEY_LIVE_REGIONS = stringSetPreferencesKey("live_regions")
val DEFAULT_LIVE_REGIONS = setOf("US", "UK", "EN")

val liveRegions: Flow<Set<String>>          // emits DEFAULT_LIVE_REGIONS if unset
suspend fun setLiveRegions(regions: Set<String>)
```

---

## How the ViewModel re-queries on region change

`observeRoomFlows()` calls `appDataStore.liveRegions.flatMapLatest { regions → ... }`:

- When `liveRegions` emits a new set (user calls `setRegions()`), `flatMapLatest` cancels the old downstream Flow and starts a new `repository.observeChannels(regionList, categoryId)` query.
- A nested `categoryIdFlow.flatMapLatest { catId → ... }` inside the outer `flatMapLatest` means category changes also trigger a new Room query without re-subscribing the outer region Flow.
- A separate `appDataStore.liveRegions.flatMapLatest { repository.observeCategories(it.toList()) }` drives the category list the same way.
- When `query` is non-blank, `setQuery()` issues `repository.searchChannels(q)` (global, region-agnostic) as a separate collected Flow; when query clears, the scoped channel Flow resumes naturally.

---

## Unit test result

```
./gradlew testDebugUnitTest → BUILD SUCCESSFUL
```

All 8 test cases passed:
- `"AM | USA SPORTS"` → US
- `"AM | USA NEWS"` → US
- `"24/7 | ENGLISH"` → EN
- `"VIP | FIFA WC26 WEST EUROP"` → EU
- `"VIP | FIFA WC26 AR/AF"` → MENA
- `"FIFA-DO| CDN DEPORTES"` → LATAM
- `"UK SPORTS"` → UK
- `""` → OTHER

---

## Build status

`./gradlew assembleDebug → BUILD SUCCESSFUL` (clean, zero errors, zero warnings after `@OptIn(ExperimentalCoroutinesApi::class)` added to ViewModel).

---

## Commit hashes

| Task | Commit |
|---|---|
| 2.1 RegionClassifier + tests | `e444789` |
| 2.2 Region tagging + AppDataStore | `669c1de` |
| 2.3 Region filter in ViewModel | `a23da5a` |
