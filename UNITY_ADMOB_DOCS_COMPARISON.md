# Unity Ads and AdMob documentation compared with KiduyuTV

**Scope and versions.** This source review compares the Android ad implementation with official Unity Ads and Google Mobile Ads (GMA/AdMob) documentation. Declared dependencies include Google Mobile Ads `25.5.0`, Google UMP `4.0.0`, Unity Ads `4.21.0`, and Start.io `5.3.1` (`app/build.gradle:353–366`). Wortise is declared but disabled (`app/build.gradle:367`; `WortiseAdManager.kt`). The code changes described below were applied on 2026-10-30. This remains source-level validation only: no Gradle build, runtime, live account, or ad-unit configuration was tested.

## Architecture

KiduyuTV directly calls Unity Ads and Google Mobile Ads. The app's own dispatchers sequence providers—for example, Unity → Start.io → GMA for banners. This is publisher-managed fallback, not AdMob mediation. Google's Unity mediation adapter is relevant only if the app intentionally moves Unity demand into GMA mediation and configures that adapter and the AdMob account; it is not a repair for the current direct-SDK design.[16] [17]

## Unity Ads: lifecycle, banners, and privacy

The Unity manager follows the initialize → load → show lifecycle and retains/reloads interstitial and rewarded placements. Reward delivery remains separate from dismissal. The banner path was improved: it waits up to 2.5 seconds for asynchronous Unity initialization, marshals banner/view/fallback work to Main, guards duplicate callbacks and stale generations, and allows a later show failure to continue to Start.io. The shared banner dispatcher adds a five-second provider timeout and cancellation on Compose disposal. Unity banner sizing remains fixed at `320 × 50` by design; `MATCH_PARENT` host width does not make the requested Unity inventory adaptive. Retain this only if the layout reserves an adequate fixed-height area; use Unity's own sizing API if adaptive sizing is later required.[1] [2] [3] [4]

Unity's consent signal is no longer derived from a hand-built Boolean made from three TCF purpose bits. With a TCF-compliant UMP message configured to include Unity as a vendor, Unity Ads 4.21 consumes the stored TCF signal. The app does not manually set `UnityAds.userConsent`; the live UMP message and publisher/vendor configuration must still be checked in the actual account.[5] [6] [7] [8]

## Google Mobile Ads and provider fallback

The GMA full-screen methods load an ad, attach `FullScreenContentCallback`, clear consumed inventory, and preload the next ad. The publisher fallback path now requests fresh GMA interstitial/rewarded inventory if none is ready, waits at most 1.5 seconds without blocking the UI, and then completes or follows the existing rewarded-support fallback. Start.io interstitial/rewarded inventory loads have an eight-second timeout; a late load response is ignored so it cannot launch a second ad after fallback has moved on. This bounds *load* waiting; it deliberately does not start a competing provider after a fullscreen show has already begun, which could display two ads if the first SDK callback arrived late.[9] [10] [11] [12]

Banner fallback is now a per-container state machine: it rechecks consent before each provider, applies five-second provider timeouts, serializes Unity → Start.io → GMA, ignores late callbacks from an older stage/request, marshals UI operations to Main, and cancels the chain when its Compose host is disposed. GMA banner listeners are attached before `loadAd()`. The phone/TV wrappers retain their separate GMA `AdView` lifecycle pause/resume and disposal behavior.[14]

GMA native-ad construction and `AdLoader.loadAd()` now run on `Dispatchers.Main.immediate`; existing native-ad ownership/disposal remains in the Compose wrapper. This is a conservative main-thread choice, not a claim that the prior IO dispatcher violated a documented GMA rule.[15]

The app-open ad remains GMA-only and retains its four-hour freshness check. It now shares a two-minute fullscreen cooldown with interstitials, so returning to the app cannot immediately stack another full-screen ad after an interstitial. The cooldown persists wall-clock timestamps (not `elapsedRealtime`, which resets on reboot); a backward wall-clock adjustment fails open rather than suppressing ads indefinitely. An app-open ad with a future-dated load timestamp is considered stale.[13]

## UMP, consent, and settings

UMP still gates ad requests through `canRequestAds()`. The Google privacy-options entry point is present in both mobile and TV settings when UMP says it is required (`MobileSettingsScreen.kt`; `SettingsScreen.kt`).

Start.io's `pas` personalization flag now requires UMP's TCF `gdprApplies` state, consent for purposes 1, 3, and 4, and the Start.io vendor bit (vendor ID 1216 in GVL v3 version 183 as checked on 2026-10-29). Unknown TCF state fails closed for *personalization only*; it does not disable contextual ad requests where UMP allows them. Recheck the vendor entry against the current GVL and Start.io's integration guidance when updating dependencies or consent policy.[7] [8]

Per the user's explicit requirement, the ads-enabled settings checker was not changed: ads remain enabled by default/always in app policy. This does not bypass UMP's legal privacy gate; requests remain suppressed until `AdEligibility` says they are allowed. The existing voluntary “Watch an Ad” support action and disclosure were preserved.[12]

## Remaining validation and references

Source-level fixes address the actionable issues found in the comparison. Still verify live UMP message configuration, Unity/Start.io account IDs and test/live mode, manifest and ad-unit setup, and actual rendering/fallback behavior on phone and Android TV/Fire TV. No local Gradle build was run, as requested.

[1]: https://docs.unity.com/en-us/grow/ads/android-sdk/initialize-sdk "Initialize the Unity Ads SDK in Android"
[2]: https://docs.unity.com/en-us/ads-android/4.21.0/sdk-integration/api/android-api "Unity Ads Android SDK API reference, version 4.21.0"
[3]: https://docs.unity.com/en-us/ads-android/4.21.0/sdk-integration/rewarded-ads "Implement rewarded ads in Android, Unity Ads 4.21.0"
[4]: https://docs.unity.com/en-us/ads-android/4.21.0/sdk-integration/banner-ads "Implement banner ads in Android, Unity Ads 4.21.0"
[5]: https://docs.unity.com/ads-android/4.21.0/privacy/developer-consent/consent/consent-communication "Unity Ads user consent communication"
[6]: https://docs.unity.com/ads-android/4.21.0/privacy/developer-consent/solution/custom-consent "Configure a custom Unity Ads consent solution"
[7]: https://developers.google.com/admob/android/privacy "Set up the UMP SDK for Android"
[8]: https://developers.google.com/admob/android/privacy/gdpr "Disclose to EEA users with Google UMP"
[9]: https://developers.google.com/admob/android/quick-start "Set up Google Mobile Ads SDK for Android"
[10]: https://developers.google.com/admob/android/interstitial "Interstitial ads, Google Mobile Ads SDK for Android"
[11]: https://developers.google.com/admob/android/rewarded "Rewarded ads, Google Mobile Ads SDK for Android"
[12]: https://developers.google.com/admob/android/rewarded-interstitial "Rewarded interstitial ads, Google Mobile Ads SDK for Android"
[13]: https://developers.google.com/admob/android/app-open "App open ads, Google Mobile Ads SDK for Android"
[14]: https://developers.google.com/admob/android/banner "Banner ads, Google Mobile Ads SDK for Android"
[15]: https://developers.google.com/admob/android/native/advanced "Native ads advanced, Google Mobile Ads SDK for Android"
[16]: https://developers.google.com/admob/android/mediation "Set up AdMob mediation for Android"
[17]: https://developers.google.com/admob/android/mediation/unity "Integrate Unity Ads with AdMob mediation for Android"
