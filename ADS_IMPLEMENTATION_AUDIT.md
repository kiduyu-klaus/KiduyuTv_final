# KiduyuTV Ads Implementation Audit

**Scope:** Source-level review of the Android ad implementation on the current `main` branch. Findings below describe the pre-fix baseline; see the dated implementation addendum at the end for subsequent changes. No Gradle build or device test was run.

## Overall picture

The app uses Google Mobile Ads (AdMob/GMA) as a core SDK and layers direct SDK fallbacks on top. A shared eligibility gate is intended to suppress all ad requests when the user disables ads or UMP consent does not permit requests. UMP consent is gathered in the splash flow before the secondary SDKs initialize. The active secondary integrations are Unity Ads and Start.io; Wortise remains in the codebase but is explicitly disabled. This is a hand-built sequential fallback design, not a unified mediation waterfall configured solely in AdMob.

The main active paths are:

- **Banners:** Unity → Start.io → AdMob, using the phone or TV AdMob unit at the last leg (`BannerFallbackDispatcher.kt:15–75`).
- **Interstitials:** a ready Unity ad → Start.io → a ready AdMob ad; otherwise the app continues without waiting (`InterstitialFallbackDispatcher.kt:17–74`). A global two-minute cooldown is shared across providers.
- **Rewarded video / rewarded interstitial:** ready Unity → Start.io → ready AdMob. If rewarded inventory is unavailable, the implementation can optionally show a standard interstitial, which does not grant the rewarded callback (`RewardedFallbackDispatcher.kt:10–103`).
- **Native in-feed ads:** Start.io and AdMob alternate as the first network per placement; a failed load advances to the other. Unity and Wortise native formats are not used by this dispatcher (`NativeAdFallbackDispatcher.kt:21–69`).
- **App-open:** AdMob only. It is checked when the process returns to the foreground after the initial launch; a missing ad is loaded for a future foreground (`AppOpenAdObserver.kt:60–75`; `AppOpenFallbackDispatcher.kt:10–27`).

## Startup, consent, and eligibility

`KiduyuTvApp` starts Firebase-backed ad-unit configuration and installs the process-wide app-open observer (`KiduyuTvApp.kt:73–75, 109–112`). In `SplashActivity`, the UI is created before SDK initialization, then `ConsentManager.requestConsent()` starts the UMP flow; Start.io and Unity are initialized from its completion callback, while AdMob initialization is awaited before the splash marks ad handling complete (`SplashActivity.kt:263–301, 307–318`). AdMob also has a separate fast path when UMP's stored state already allows requests (`SplashActivity.kt:320–324`).

`AdEligibility.canRequestAds()` is the shared fail-closed check. It combines the local ads-disabled preference with `ConsentManager.canRequestAds()` and returns false if evaluation throws (`AdEligibility.kt:14–31`). Ad request paths generally re-check this eligibility at load/show time. The app uses UMP and forwards a consent-derived signal to Start.io and Unity. Wortise has its own consent-manager call in dormant code, but its active bridge is currently commented out (`ConsentManager.kt:123–138`).

The privacy mapping warrants a dedicated review against the deployed UMP message and each network's current consent specification. The code derives Unity's single `userConsent` boolean from TCF purpose-consent bits 1, 3, and 4, and reuses the result for Start.io personalization (`ConsentManager.kt:199–220`; `StartAppAdManager.kt:87–107`). Those purpose bits alone may not demonstrate consent for a particular ad vendor. This is a **policy/configuration risk to verify**, not a conclusion that the current configuration is non-compliant. The manifest and UMP request also explicitly set child-directed/under-age flags to false (`AndroidManifest.xml:44–48`; `ConsentManager.kt:59–61`); verify this matches the actual audience.

## Placement and user experience

Phone and TV navigation graphs each mount a shared bottom banner component (`MobileNavGraph.kt:967–973`; `NavGraph.kt:742`). Catalog screens contain many `SectionNativeAd` placements, for example mobile home and movies (`MobileHomeScreen.kt:391, 430, 464`; `MobileMoviesScreen.kt:151, 233, 295`) and TV home and movies (`HomeScreen.kt:426, 501, 575`; `MoviesScreen.kt:210, 260, 326, 344`). Each placement can issue its own native request; no app-wide native-ad impression/request cap was found in the dispatcher. Actual request count is affected by lazy list composition and scrolling.

The main navigation observer treats each destination change as an interstitial opportunity, excluding the initial destination and relying on the shared cooldown (`MainActivity.kt:99–119`). This includes Compose back-stack pops; the source explicitly says Compose BackHandlers are covered by the destination observer (`MainActivity.kt:128–140`). Phone and TV player-launch paths also use interstitials. Phone screen back helpers themselves call through immediately (`MobileInterstitialBackClick.kt:9–13`), but the resulting route change can still be caught by the global observer. TV back navigation is explicitly immediate in `TvInterstitialManager.showAndThen()` (`TvInterstitialManager.kt:23–26`).

App-open inventory is treated as valid for four hours (`AdManager.kt:474–475, 665–672`), and the code has no separate app-open frequency cooldown. Therefore each eligible foreground transition can display a currently available ad. Confirm this is the intended UX and policy for the product.

## Findings, in priority order

### 1. The “disable ads” setting currently does not disable ads — confirmed

`SettingsManager.setAdsDisabled()` persists the choice, but `isAdsDisabled()` returns the hardcoded value `false` instead of reading the preference (`SettingsManager.kt:142–156`). `AdEligibility` depends on this getter (`AdEligibility.kt:14–18`), and both phone and TV settings UI present the switch as disabling all advertisements. As implemented, toggling it cannot suppress ad requests. This is the clearest functional defect and should be fixed before treating the setting as effective.

### 2. Persisted interstitial cooldown can suppress ads long after a reboot — confirmed

`FullscreenAdPolicy` stores `SystemClock.elapsedRealtime()` in SharedPreferences and later subtracts that saved value from the current elapsed realtime (`FullscreenAdPolicy.kt:8–25`). Elapsed realtime resets at reboot. If the saved pre-reboot value is greater than the new uptime, the computed age is negative, so the two-minute gate continues denying interstitials until uptime catches up. Use wall-clock time with clock-change handling, or store boot-aware state and reset/clamp negative ages.

### 3. Secondary-network consent mapping needs privacy/configuration verification

The mapping from general TCF purposes to one Unity opt-in boolean may not reflect vendor-specific consent. Check the actual UMP message, vendor list, and current Unity/Start.io SDK requirements. Keep all network requests fail-closed until the resulting signals are correct. The implementation currently gates requests globally, but the network-specific mapping should not be assumed correct just because UMP returned `canRequestAds=true`.

### 4. TV defaults reuse the phone production ad units — verify account intent

`defaultTvProduction` is assigned `defaultPhoneProduction` (`AdUnitIds.kt:33–42`). Separate TV unit IDs are only used when Firebase supplies valid TV-specific values (`AdUnitIds.kt:143–150`). As a result, a missing or incomplete Firebase TV configuration silently directs TV placements to phone production units. If that is intentional, document it; otherwise provide valid TV defaults and validate remote configuration before launch.

### 5. Fallbacks are readiness-based and non-blocking, not a full waterfall

The dispatchers only try the final AdMob leg when the corresponding AdMob object is already ready. If it is not ready, the placement completes without an ad and preloads for a later opportunity (`InterstitialFallbackDispatcher.kt:35–49`; `RewardedFallbackDispatcher.kt:66–79`). This preserves navigation and avoids making users wait, but means a placement can be lost even when a provider might fill after a fresh request. Treat it as an explicit latency/revenue trade-off rather than assuming every network gets a request on every opportunity.

### 6. App-open has no app-level cooldown

The four-hour value is an ad freshness limit, not a display-frequency cap. The process observer checks on foreground transitions, and there is no additional interval gate. Add a separate cap if frequent app switching or activity transitions create an intrusive experience. The current `appOpenShowing` guard prevents a second app-open while one is already showing (`AdManager.kt:617–620`).

### 7. No central cap was found for repeated in-feed native placements

A long catalog scroll can compose/request several native ads, with multiple placements across home, movie, and show lists. The dispatcher rotates the initial provider but does not impose a session/page impression limit. Confirm this density is intentional and measure request/impression rates; consider a per-session or per-screen cap if needed.

### 8. Network-priority comments do not match active code

`SectionNativeAd` documentation says Wortise is preferred, but its actual dispatcher uses Start.io then AdMob (`SectionNativeAd.kt:19–25`; `NativeAdFallbackDispatcher.kt:25–28`). Wortise is disabled (`WortiseAdManager.kt:36–44, 112–115`). Banner preview copy says Start.io is primary, but the shared dispatcher tries Unity first (`BannerAdView.kt:49–55`; `TvBannerAdView.kt:57–64`; `BannerFallbackDispatcher.kt:69–74`). These do not alter request behavior, but can mislead future maintenance and test expectations.

### 9. Dormant Wortise code and dependency remain

The Wortise manager contains substantial integration code and the manifest has a Wortise App ID, but `WORTISE_ENABLED = false` short-circuits initialization. The SDK dependency is still included (`WortiseAdManager.kt:36–44, 112–115`; `app/build.gradle:367`; `AndroidManifest.xml:66–68`). Do not count it as active fallback capacity. If it will remain disabled, consider removing or clearly isolating the dead integration; if re-enabled, restore and test consent initialization before loading ads.

## Positive implementation choices

The core request gate fails closed when consent state cannot be read. AdMob initialization includes a watchdog so a missing callback does not stall splash forever (`AdManager.kt:67–74, 227–249`). Fallback dispatchers are generally structured to let the UI proceed if inventory is missing, and interstitial frequency is shared across networks rather than accidentally giving each SDK a separate cap. Native ad wrappers also destroy AdMob native assets or unregister Start.io interaction views on Compose disposal (`AdMobNativeAdView.kt:47–67`; `StartAppNativeAdView.kt:86–127`).

## Recommended next actions

1. Fix `SettingsManager.isAdsDisabled()` and test both settings states against banner, native, interstitial, rewarded, and app-open requests.
2. Make `FullscreenAdPolicy` reboot-safe and test both normal cooldown and reboot/negative-age cases.
3. Review Unity/Start.io consent signals against the live UMP configuration and vendor requirements; verify child-directed settings against audience intent.
4. Decide whether TV should have distinct production ad units and whether native/app-open frequency caps are needed.
5. Update stale network-priority comments and then test failure paths for every active fallback leg on phone and Android TV.

**Validation limit:** This report is based on source inspection only. It does not establish actual fill rate, provider-account correctness, consent-console configuration, or runtime behavior on devices.

## Implementation addendum — 2026-10-30

The following source changes were made after the baseline audit:

- **Ads-enabled preference:** deliberately left unchanged per the user's instruction; app policy continues to treat ads as always enabled. UMP's consent/privacy gate remains active and is not bypassed.
- **Fullscreen pacing:** replaced persisted monotonic-time cooldown state with wall-clock timestamps and a two-minute shared cap across interstitial and app-open ads. A backward clock adjustment fails open rather than suppressing ads indefinitely. App-open ads with future-dated load times are stale.
- **Consent signaling:** removed the purpose-bit-to-Unity-Boolean mapping. Unity now uses the standard stored TCF signal from UMP; Start.io personalization requires TCF consent for relevant purposes and Start.io's vendor bit. Missing TCF data fails closed for personalization only. The privacy-options entry point was confirmed in both mobile and TV settings.
- **Fallback/lifecycle:** Unity banner initialization now has a bounded wait and main-thread view work; the shared banner chain has per-provider timeouts, per-container stale-callback guards, and disposal cancellation. Start.io fullscreen inventory loads time out after eight seconds and ignore late load success. AdMob interstitial/rewarded inventory is requested and awaited for at most 1.5 seconds. GMA native loading is dispatched on Main.

These changes were source-reviewed with `git diff --check` and symbol/callsite checks. No Gradle command was run. Device behavior, SDK account configuration, and provider fill/fallback timing still require CI and phone/Android TV runtime validation. The TV ad-unit fallback, native-ad placement cap, stale comments, and dormant Wortise integration remain separate audit follow-ups.
