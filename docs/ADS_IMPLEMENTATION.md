# KiduyuTV — Ads Implementation Guide

## Overview

This guide covers a complete ads integration for KiduyuTV using **Google AdMob** for the `phone` flavour and **Google Ad Manager (GAM)** for the `tv` flavour. The two platforms require different SDK approaches — AdMob is optimised for touch screens while GAM provides leanback-compatible banner and interstitial formats suitable for TV/Fire TV.

The implementation is structured to be **flavour-aware**, so ad code compiles only into the correct variant, and a **no-op stub** keeps the shared code clean for the TV build when using AdMob APIs (and vice-versa).

---

## Table of Contents

1. [SDK Setup](#1-sdk-setup)
2. [Ad Unit IDs](#2-ad-unit-ids)
3. [AdManager Utility Class](#3-admanager-utility-class)
4. [Initialisation in KiduyuTvApp](#4-initialisation-in-kiduyutvapp)
5. [Phone — Banner Ads](#5-phone--banner-ads)
6. [Phone — Interstitial Ads](#6-phone--interstitial-ads)
7. [Phone — Rewarded Ads](#7-phone--rewarded-ads)
8. [TV — Banner Ads (GAM)](#8-tv--banner-ads-gam)
9. [TV — Interstitial Ads (GAM)](#9-tv--interstitial-ads-gam)
10. [Placement Strategy](#10-placement-strategy)
11. [GDPR / Consent (UMP)](#11-gdpr--consent-ump)
12. [Testing](#12-testing)
13. [AndroidManifest Changes](#13-androidmanifest-changes)
14. [ProGuard Rules](#14-proguard-rules)

---

## 1. SDK Setup

### `app/build.gradle`

```groovy
dependencies {

    // ── Phone flavour only ────────────────────────────────────────────────
    phoneImplementation 'com.google.android.gms:play-services-ads:23.3.0'

    // ── TV flavour only ───────────────────────────────────────────────────
    // Google Ad Manager supports leanback / Fire TV
    tvImplementation 'com.google.android.gms:play-services-ads:23.3.0'

    // ── Consent SDK (both flavours, required for EEA users) ───────────────
    implementation 'com.google.android.ump:user-messaging-platform:3.0.0'
}
```

> Both flavours share the same `play-services-ads` artifact. The difference is in which **ad unit type** and **ad format** you use at runtime — AdMob ad units for phone, GAM ad units for TV.

---

## 2. Ad Unit IDs

Create a singleton to hold your ad unit IDs. Keep test IDs in during development — AdMob will flag accounts that use real IDs in test builds.

### `util/AdUnitIds.kt`

```kotlin
package com.kiduyuk.klausk.kiduyutv.util

import com.kiduyuk.klausk.kiduyutv.BuildConfig

object AdUnitIds {

    // Set to true during development to use Google test ad units
    private val useTestIds = BuildConfig.DEBUG

    // ── Phone (AdMob) ─────────────────────────────────────────────────────

    val PHONE_BANNER: String get() = if (useTestIds)
        "ca-app-pub-3940256099942544/6300978111"   // Google test banner
    else
        "ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY"   // Your AdMob banner unit

    val PHONE_INTERSTITIAL: String get() = if (useTestIds)
        "ca-app-pub-3940256099942544/1033173712"   // Google test interstitial
    else
        "ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY"   // Your AdMob interstitial unit

    val PHONE_REWARDED: String get() = if (useTestIds)
        "ca-app-pub-3940256099942544/5224354917"   // Google test rewarded
    else
        "ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY"   // Your AdMob rewarded unit

    // ── TV (Google Ad Manager) ────────────────────────────────────────────

    val TV_BANNER: String get() = if (useTestIds)
        "/6499/example/banner"                     // GAM test banner
    else
        "/YOUR_NETWORK_CODE/kiduyutv/tv_banner"    // Your GAM banner unit

    val TV_INTERSTITIAL: String get() = if (useTestIds)
        "/6499/example/interstitial"               // GAM test interstitial
    else
        "/YOUR_NETWORK_CODE/kiduyutv/tv_interstitial"
}
```

---

## 3. AdManager Utility Class

A single class that wraps SDK initialisation, interstitial loading, and rewarded ad logic. Flavour-specific code is gated at runtime using `BuildConfig.FLAVOR`.

### `util/AdManager.kt`

```kotlin
package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AdManager {

    private const val TAG = "AdManager"

    @Volatile private var isInitialised = false
    @Volatile private var interstitialAd: InterstitialAd? = null
    @Volatile private var rewardedAd: RewardedAd? = null

    // ── Initialisation ────────────────────────────────────────────────────

    /**
     * Initialise the Mobile Ads SDK. Call once from KiduyuTvApp.onCreate().
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    fun init(context: Context) {
        if (isInitialised) return
        MobileAds.initialize(context) { initStatus ->
            isInitialised = true
            val statuses = initStatus.adapterStatusMap.entries
                .joinToString { "${it.key}: ${it.value.initializationState}" }
            Log.i(TAG, "MobileAds initialised — $statuses")
            // Pre-load interstitial immediately after init
            preloadInterstitial(context)
            if (BuildConfig.FLAVOR == "phone") {
                preloadRewarded(context)
            }
        }
    }

    // ── Interstitial ──────────────────────────────────────────────────────

    /**
     * Pre-loads an interstitial ad in the background so it is ready to show
     * without delay when needed.
     */
    fun preloadInterstitial(context: Context) {
        if (!isInitialised) return
        val unitId = if (BuildConfig.FLAVOR == "tv")
            AdUnitIds.TV_INTERSTITIAL
        else
            AdUnitIds.PHONE_INTERSTITIAL

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, unitId, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
                Log.i(TAG, "Interstitial loaded")
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                interstitialAd = null
                Log.w(TAG, "Interstitial failed to load: ${error.message}")
            }
        })
    }

    /**
     * Shows the pre-loaded interstitial if available, then immediately
     * pre-loads the next one. Calls [onDismissed] when the ad closes
     * (or immediately if no ad is ready).
     */
    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
        val ad = interstitialAd
        if (ad == null) {
            Log.i(TAG, "No interstitial ready — proceeding without ad")
            onDismissed()
            preloadInterstitial(activity)
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                preloadInterstitial(activity)
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                preloadInterstitial(activity)
                onDismissed()
            }
        }
        ad.show(activity)
    }

    // ── Rewarded (phone only) ─────────────────────────────────────────────

    fun preloadRewarded(context: Context) {
        if (BuildConfig.FLAVOR != "phone") return
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, AdUnitIds.PHONE_REWARDED, adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    Log.i(TAG, "Rewarded ad loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    Log.w(TAG, "Rewarded ad failed: ${error.message}")
                }
            })
    }

    /**
     * Shows a rewarded ad. [onRewarded] is only called when the user
     * earns the reward (watched the full ad). [onDismissed] always fires.
     */
    fun showRewarded(
        activity: Activity,
        onRewarded: () -> Unit = {},
        onDismissed: () -> Unit = {}
    ) {
        val ad = rewardedAd
        if (ad == null) {
            Log.i(TAG, "No rewarded ad ready")
            onDismissed()
            preloadRewarded(activity)
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                preloadRewarded(activity)
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                onDismissed()
            }
        }
        ad.show(activity) { rewardItem ->
            Log.i(TAG, "User rewarded: ${rewardItem.amount} ${rewardItem.type}")
            onRewarded()
        }
    }

    val isInterstitialReady: Boolean get() = interstitialAd != null
    val isRewardedReady: Boolean get() = rewardedAd != null
}
```

---

## 4. Initialisation in KiduyuTvApp

Add a single call inside `onCreate()`:

```kotlin
// In KiduyuTvApp.kt — inside onCreate(), after existing initialisations

// Initialize Mobile Ads SDK (AdMob for phone, GAM for tv)
AdManager.init(this)
```

---

## 5. Phone — Banner Ads

### Composable wrapper

Banner ads require an `AndroidView` bridge because the AdMob `AdView` is a legacy Android View, not a Compose component.

### `ui/components/BannerAdView.kt`

```kotlin
package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.kiduyuk.klausk.kiduyutv.util.AdUnitIds

/**
 * Composable banner ad for the phone flavour.
 * Renders a standard AdMob adaptive banner anchored to the bottom of the screen.
 *
 * Usage: place inside a Column/Box where you want the banner to appear.
 */
@Composable
fun BannerAdView(
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(60.dp)
        .background(Color(0xFF141414))
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdUnitIds.PHONE_BANNER
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
```

### Adding a banner below `MobileBottomNavigation`

The cleanest placement is at the very bottom of `MainActivity`'s Scaffold, beneath the bottom navigation bar.

```kotlin
// In MainActivity.kt — inside setContent { KiduyuTvTheme { ... } }

import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.ui.components.BannerAdView

Scaffold(
    bottomBar = {
        Column {
            MobileBottomNavigation(navController = navController, currentRoute = currentRoute)
            // Show banner only on phone flavour
            if (BuildConfig.FLAVOR == "phone") {
                BannerAdView()
            }
        }
    }
) { innerPadding ->
    // ... existing NavHost
}
```

---

## 6. Phone — Interstitial Ads

Interstitials should be shown at natural navigation breaks — not mid-content. The best placements in KiduyuTV are:

- When the user navigates **from a detail screen back to home** after browsing
- When the user opens the **stream links screen** (before the provider list appears)

### In `MobileStreamLinksScreen.kt`

```kotlin
// At the top of MobileStreamLinksScreen composable

import android.app.Activity
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.util.AdManager

val context = LocalContext.current

// Show interstitial once when the screen first opens (phone flavour only)
LaunchedEffect(Unit) {
    if (BuildConfig.FLAVOR == "phone") {
        val activity = context as? Activity
        if (activity != null) {
            AdManager.showInterstitial(activity)
            // The ad's onDismissed callback fires automatically;
            // no navigation blocking needed here since content loads in parallel
        }
    }
}
```

### In `MobileMovieDetailScreen.kt` / `MobileTvShowDetailScreen.kt` — on back press

```kotlin
val activity = LocalContext.current as? Activity

// Override back navigation to show an interstitial
BackHandler {
    if (BuildConfig.FLAVOR == "phone") {
        AdManager.showInterstitial(activity!!) {
            onBackClick()  // navigate back after ad closes
        }
    } else {
        onBackClick()
    }
}
```

---

## 7. Phone — Rewarded Ads

Rewarded ads fit well as a "watch an ad to remove ads for 1 hour" or "watch an ad to skip the wait" UX. A natural placement in KiduyuTV is in `SettingsScreen` as an optional "Go Ad-Free" action.

### In `MobileSettingsScreen.kt`

```kotlin
// Inside the Playback SettingsGroup or a new "Support" group

import com.kiduyuk.klausk.kiduyutv.util.AdManager
import com.kiduyuk.klausk.kiduyutv.BuildConfig

if (BuildConfig.FLAVOR == "phone") {
    SettingsGroup(title = "Support KiduyuTV") {
        SettingsItem(
            icon = Icons.Default.CardGiftcard,
            title = "Watch an Ad",
            subtitle = "Support us by watching a short ad",
            onClick = {
                AdManager.showRewarded(
                    activity = context as Activity,
                    onRewarded = {
                        // Grant the user something — e.g. set a flag in SettingsManager
                        // to hide banners for 1 hour
                        SettingsManager(context).setAdFreeUntil(
                            System.currentTimeMillis() + 60 * 60 * 1000L
                        )
                    }
                )
            }
        )
    }
}
```

---

## 8. TV — Banner Ads (GAM)

Fire TV and Android TV do not support touch-driven ad formats. Use **Google Ad Manager** banner placements sized for the 1080p TV canvas. The `AdManagerAdView` is the correct class (not `AdView`).

### `ui/components/TvBannerAdView.kt`

```kotlin
package com.kiduyuk.klausk.kiduyutv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.kiduyuk.klausk.kiduyutv.util.AdUnitIds

/**
 * GAM banner for the TV flavour.
 * Uses a 728×90 leaderboard — appropriate for a 1080p TV canvas.
 */
@Composable
fun TvBannerAdView(
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(90.dp)
        .background(Color(0xFF0F0F0F))
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AdManagerAdView(context).apply {
                setAdSizes(AdSize.LEADERBOARD)   // 728×90
                adUnitId = AdUnitIds.TV_BANNER
                loadAd(AdManagerAdRequest.Builder().build())
            }
        }
    )
}
```

### Placement — bottom of `HomeScreen` (TV)

```kotlin
// In NavGraph.kt — inside the Home composable route

import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.ui.components.TvBannerAdView

Box(modifier = Modifier.fillMaxSize()) {
    HomeScreen(/* ... */)

    // Overlay a non-intrusive banner at the bottom when idle
    if (BuildConfig.FLAVOR == "tv") {
        TvBannerAdView(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(90.dp)
                .background(Color(0xCC000000))  // semi-transparent backing
        )
    }
}
```

---

## 9. TV — Interstitial Ads (GAM)

TV interstitials use `AdManagerInterstitialAd` and are D-pad navigable by default. The safest placement is between browsing and playback — i.e. after the user selects a provider and before `PlayerActivity` launches.

### `util/TvInterstitialManager.kt`

```kotlin
package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback

object TvInterstitialManager {

    private const val TAG = "TvInterstitialManager"
    private var interstitialAd: AdManagerInterstitialAd? = null

    fun preload(context: Context) {
        AdManagerInterstitialAd.load(
            context,
            AdUnitIds.TV_INTERSTITIAL,
            AdManagerAdRequest.Builder().build(),
            object : AdManagerInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: AdManagerInterstitialAd) {
                    interstitialAd = ad
                    Log.i(TAG, "TV interstitial loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.w(TAG, "TV interstitial failed: ${error.message}")
                }
            }
        )
    }

    fun showAndThenLaunch(activity: Activity, onDismissed: () -> Unit) {
        val ad = interstitialAd
        if (ad == null) {
            onDismissed()
            preload(activity)
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                preload(activity)
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                onDismissed()
            }
        }
        ad.show(activity)
    }
}
```

### Pre-load in `AdManager.init()`

```kotlin
// Add to AdManager.init(), after preloadInterstitial()
if (BuildConfig.FLAVOR == "tv") {
    TvInterstitialManager.preload(context)
}
```

### Show before `PlayerActivity` launches (TV `StreamLinksScreen`)

```kotlin
// In StreamLinksScreen.kt — inside the provider card onClick lambda

val activity = LocalContext.current as? Activity

// ... existing onClick logic that builds the intent ...
if (BuildConfig.FLAVOR == "tv" && activity != null) {
    TvInterstitialManager.showAndThenLaunch(activity) {
        activity.startActivity(intent)
    }
} else {
    context.startActivity(intent)
}
```

---

## 10. Placement Strategy

| Location | Phone | TV |
|---|---|---|
| Home screen | — | GAM leaderboard banner (bottom) |
| Movies / TV Shows screen | AdMob banner (bottom nav area) | — |
| Detail screen (back press) | Interstitial | — |
| Stream links screen (on open) | Interstitial | Interstitial (before player launches) |
| Settings | Rewarded ad opt-in | — |

**Rules to follow:**

- Never show an interstitial during playback or while content is loading.
- Never show more than one interstitial per 3-minute session window — use a timestamp gate in `AdManager` to enforce this.
- TV banners should only appear on screens where the user is idle (browsing), never during D-pad-focused navigation through a row.
- Always call `preload*` after an ad is shown so the next one is ready.

### Session frequency gate (add to `AdManager`)

```kotlin
private var lastInterstitialShownAt = 0L
private const val MIN_INTERSTITIAL_INTERVAL_MS = 3 * 60 * 1000L  // 3 minutes

fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
    val now = System.currentTimeMillis()
    if (now - lastInterstitialShownAt < MIN_INTERSTITIAL_INTERVAL_MS) {
        Log.i(TAG, "Interstitial skipped — too soon since last show")
        onDismissed()
        return
    }
    val ad = interstitialAd ?: run { onDismissed(); preloadInterstitial(activity); return }
    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() {
            lastInterstitialShownAt = System.currentTimeMillis()
            interstitialAd = null
            preloadInterstitial(activity)
            onDismissed()
        }
        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            interstitialAd = null
            preloadInterstitial(activity)
            onDismissed()
        }
    }
    ad.show(activity)
}
```

---

## 11. GDPR / Consent (UMP)

The User Messaging Platform (UMP) SDK handles consent for EEA users. It must be called **before** `MobileAds.initialize()`.

### `util/ConsentManager.kt`

```kotlin
package com.kiduyuk.klausk.kiduyutv.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

object ConsentManager {

    private const val TAG = "ConsentManager"

    /**
     * Requests the latest consent information and shows a consent form
     * if required. Call from SplashActivity before AdManager.init().
     *
     * @param activity  The calling Activity (needed to show the form).
     * @param onComplete Fires when consent has been handled — proceed to
     *                   call AdManager.init() here.
     */
    fun requestConsent(activity: Activity, onComplete: () -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)

        consentInfo.requestConsentInfoUpdate(activity, params,
            {
                // Consent info updated successfully
                if (consentInfo.isConsentFormAvailable) {
                    loadAndShowConsentForm(activity, consentInfo, onComplete)
                } else {
                    onComplete()
                }
            },
            { formError ->
                Log.w(TAG, "Consent info update failed: ${formError.message}")
                onComplete()  // Proceed even on failure — non-EEA users won't see a form
            }
        )
    }

    private fun loadAndShowConsentForm(
        activity: Activity,
        consentInfo: ConsentInformation,
        onComplete: () -> Unit
    ) {
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Consent form error: ${formError.message}")
            }
            onComplete()
        }
    }

    fun canRequestAds(context: Context): Boolean {
        val info = UserMessagingPlatform.getConsentInformation(context)
        return info.canRequestAds()
    }
}
```

### Integrate in `SplashActivity`

```kotlin
// In SplashActivity.onCreate(), BEFORE checkForUpdates()

ConsentManager.requestConsent(this) {
    // Only initialise ads after consent is resolved
    AdManager.init(this)
}
```

---

## 12. Testing

### Use test device IDs

Register your physical device as a test device to avoid invalid traffic:

```kotlin
// In KiduyuTvApp.onCreate(), before AdManager.init()

val testDeviceConfig = RequestConfiguration.Builder()
    .setTestDeviceIds(listOf(
        "YOUR_DEVICE_ID_HERE",  // find in logcat: "Use RequestConfiguration..."
        AdRequest.DEVICE_ID_EMULATOR
    ))
    .build()
MobileAds.setRequestConfiguration(testDeviceConfig)
```

### Verify ad loading in logcat

```
# Banner loaded
I/Ads: Ad loaded.

# Interstitial loaded  
I/AdManager: Interstitial loaded

# Interstitial shown and dismissed
I/AdManager: Interstitial loaded  ← next one pre-loading
```

### Test ad unit IDs reference

| Format | Test ID |
|---|---|
| Banner | `ca-app-pub-3940256099942544/6300978111` |
| Interstitial | `ca-app-pub-3940256099942544/1033173712` |
| Rewarded | `ca-app-pub-3940256099942544/5224354917` |
| GAM Banner | `/6499/example/banner` |
| GAM Interstitial | `/6499/example/interstitial` |

---

## 13. AndroidManifest Changes

```xml
<manifest>

    <!-- Required for ads -->
    <uses-permission android:name="android.permission.INTERNET" />  <!-- already present -->
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />  <!-- already present -->

    <application>

        <!-- AdMob App ID — replace with your real ID from the AdMob dashboard -->
        <meta-data
            android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY" />

        <!-- Delay app measurement until consent is obtained (recommended for EEA) -->
        <meta-data
            android:name="com.google.android.gms.ads.DELAY_APP_MEASUREMENT_INIT"
            android:value="true" />

    </application>

</manifest>
```

> For flavour-specific App IDs, create `app/src/phone/AndroidManifest.xml` and `app/src/tv/AndroidManifest.xml` with just the `<meta-data>` block — Gradle will merge them correctly.

---

## 14. ProGuard Rules

Add to `app/proguard-rules.pro`:

```proguard
# Google Mobile Ads SDK
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }

# User Messaging Platform (UMP / GDPR consent)
-keep class com.google.android.ump.** { *; }

# AdMob mediation adapters (if added later)
-keep class com.google.android.gms.ads.mediation.** { *; }
```

---

## Summary of New Files

| File | Purpose |
|---|---|
| `util/AdUnitIds.kt` | Centralised ad unit IDs, test/prod switch |
| `util/AdManager.kt` | SDK init, interstitial + rewarded lifecycle (phone & TV) |
| `util/TvInterstitialManager.kt` | GAM interstitial wrapper for TV flavour |
| `util/ConsentManager.kt` | UMP GDPR consent flow |
| `ui/components/BannerAdView.kt` | Composable AdMob banner (phone) |
| `ui/components/TvBannerAdView.kt` | Composable GAM leaderboard banner (TV) |

## Summary of Modified Files

| File | Change |
|---|---|
| `app/build.gradle` | Add `play-services-ads` and `user-messaging-platform` |
| `AndroidManifest.xml` | Add `APPLICATION_ID` meta-data and `DELAY_APP_MEASUREMENT_INIT` |
| `KiduyuTvApp.kt` | Call `ConsentManager.requestConsent` → `AdManager.init` |
| `SplashActivity.kt` | Trigger consent request on launch |
| `app/proguard-rules.pro` | Keep AdMob and UMP classes |
| `MainActivity.kt` | Add `BannerAdView` below bottom nav (phone) |
| `MobileStreamLinksScreen.kt` | Show interstitial on open (phone) |
| `StreamLinksScreen.kt` | Show TV interstitial before player launches (TV) |
| `MobileMovieDetailScreen.kt` | Show interstitial on back press (phone) |
| `MobileSettingsScreen.kt` | Add rewarded ad opt-in (phone) |
| `NavGraph.kt` | Overlay `TvBannerAdView` on home screen (TV) |
