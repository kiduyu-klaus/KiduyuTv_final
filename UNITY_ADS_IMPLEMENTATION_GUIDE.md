# Unity Ads Android Integration Guide for KiduyuTV

**Project reviewed:** [kiduyu-klaus/KiduyuTv_final](https://github.com/kiduyu-klaus/KiduyuTv_final)  
**Prepared by:** Manus AI  
**Scope:** Unity Ads Android SDK integration for the existing phone and Android TV flavors, including initialization, interstitials, rewarded ads, banners, consent, Compose lifecycle, mediation/fallback behavior, testing, and production hardening.

## 1. Executive summary

KiduyuTV already contains a substantial Unity Ads implementation. The repository includes the Unity Ads SDK, Unity-related manifest placeholders, an `UnityAdManager` singleton for initialization and ad lifecycle callbacks, a Compose `UnityBannerAdView`, consent propagation through Google UMP, and Unity-specific shrinker rules.

However, the current implementation is only partially wired into the product. The most important issues are architectural rather than missing SDK calls:

| Area | Current state | Required correction |
| --- | --- | --- |
| SDK dependency | Unity Ads is declared as `4.7.0` but globally forced to `4.12.2` | Declare one explicit version and remove the hidden version mismatch |
| Initialization | `UnityAdManager.preloadAds()` exists but is not called from the splash consent completion path | Initialize Unity after UMP consent is resolved and before any ad request |
| Interstitial fallback | `AdFallbackDispatcher` chooses AdMob or Start.io only | Add Unity as a real fallback, or make Unity a selectable network |
| Rewarded fallback | Dispatcher chooses AdMob or Start.io only | Add Unity as a fallback while preserving the completion-only reward rule |
| Phone banner | Active `BannerAdView` uses AdMob and Start.io directly | Route the active phone banner through a network-aware wrapper or intentionally choose Unity |
| TV banner | Active `TvBannerAdView` uses AdMob and Start.io directly | Use the existing `UnityBannerAdView` when Unity is selected |
| Direct-stream launch | `DirectStreamLauncher` calls AdMob directly | Route it through the common dispatcher so Unity can participate |
| Ad disable switch | `SettingsManager.isAdsDisabled()` currently always returns `false` | Fix it to read the stored preference before enabling Unity |
| Consent | UMP forwards consent to Unity using `MetaData` | Keep this ordering and ensure Unity initialization occurs afterward |

The official Unity documentation describes the sequence as **install the SDK, initialize it, load an ad unit, then show it after the load callback**. Interstitial and rewarded ads use `UnityAds.load()` and `UnityAds.show()`, while banners use `BannerView`, a banner listener, `load()`, and explicit destruction when the view is removed.[1] [2] [3] [4] [5] [6]

> **Important:** The supplied Unity documentation is for the previous Unity Dashboard experience. If the project uses the New Unity Dashboard, Unity directs developers to the newer Monetization or User Acquisition documentation.[1]

## 2. What Unity Ads requires

Unity’s Android requirements page states that the Android Gradle Plugin must be at least 4.2.0, `minSdkVersion` must be at least 19, `compileSdkVersion` must be at least 33, and Java 8 targeting is required for Unity Ads SDK 4.7.0 and newer.[2]

KiduyuTV already exceeds these minimums. It uses `minSdk 24`, `compileSdk 35`, target SDK 35, and Java/Kotlin 17. The project therefore satisfies the documented platform requirements. The requirement that matters most to this repository is dependency consistency: Unity warns that the SDK must be installed through Gradle so its transitive dependencies, manifest entries, and ProGuard rules are resolved correctly.[3]

The Unity installation page also documents the Google Advertising ID permission for apps targeting Android 13 or newer:

```xml
<uses-permission android:name="com.google.android.gms.permission.AD_ID" />
```

Unity Ads 4.1 and later automatically declare the advertising-ID permission when available, but the final merged manifest and Play Console data-safety declarations should still be verified.[3]

## 3. Existing KiduyuTV implementation audit

### 3.1 Existing Gradle configuration

The current `app/build.gradle` contains these Unity-related declarations:

```groovy
implementation 'com.unity3d.ads-mediation:unityads-adapter:5.5.0'
implementation 'com.unity3d.ads:unity-ads:4.7.0'
implementation 'com.google.ads.mediation:unity:4.12.2.0'
```

Later in the same file, the project forces a different direct Unity Ads version:

```groovy
configurations.all {
    resolutionStrategy {
        force 'com.unity3d.ads:unity-ads:4.12.2'
    }
}
```

This means the source declaration says `4.7.0`, while dependency resolution attempts to use `4.12.2`. That is unnecessarily confusing and can create incompatibilities with the mediation adapter or with APIs compiled against another version.

### 3.2 Recommended dependency correction

If the project intends to use the direct Unity Ads API already present in `UnityAdManager`, standardize the direct dependency instead of relying on a global force:

```groovy
def unityAdsVersion = '4.12.2'

dependencies {
    implementation "com.unity3d.ads:unity-ads:$unityAdsVersion"

    // Keep only if AdMob mediation with Unity is intentionally used.
    implementation 'com.google.ads.mediation:unity:4.12.2.0'

    // Keep only if this separate mediation adapter is required by the selected
    // mediation stack. It is not required for direct UnityAds.initialize/load/show.
    implementation 'com.unity3d.ads-mediation:unityads-adapter:5.5.0'
}
```

Then remove the hidden force:

```groovy
// Remove this if every dependency already agrees on the chosen version.
// configurations.all {
//     resolutionStrategy {
//         force 'com.unity3d.ads:unity-ads:4.12.2'
//     }
// }
```

Use the exact Unity Ads and mediation adapter versions approved by the Unity/AdMob mediation documentation for the project’s chosen monetization architecture. The official installation page shows `4.7.0` as its documented example, while KiduyuTV currently resolves `4.12.2`; the important fix is to use one deliberate, tested version rather than declaring one version and silently forcing another.[3]

If KiduyuTV is using Unity only as a direct ad network fallback, the simplest dependency set is:

```groovy
implementation 'com.unity3d.ads:unity-ads:4.12.2'
```

If KiduyuTV is using AdMob mediation to request Unity demand, the AdMob Unity adapter is also required. That is a different integration path from directly calling `UnityAds.load()` and `UnityAds.show()`. Do not assume that adding both paths automatically creates a valid mediation waterfall.

### 3.3 Existing manifest configuration

The project already provides Unity metadata through manifest placeholders:

```xml
<meta-data
    android:name="com.unity3d.ads.UNITY_ADS_GAME_ID"
    android:value="${unityAdsGameId}" />
<meta-data
    android:name="com.unity3d.ads.UNITY_ADS_TEST_MODE"
    android:value="${unityAdsTestMode}" />
```

The `phone` and `tv` flavors currently use the same Game ID and set test mode to `false`:

```groovy
productFlavors {
    phone {
        dimension 'formfactor'
        applicationIdSuffix '.phone'
        manifestPlaceholders = [
            unityAdsGameId: '800077880',
            unityAdsTestMode: 'false'
        ]
    }

    tv {
        dimension 'formfactor'
        applicationIdSuffix '.tv'
        manifestPlaceholders = [
            unityAdsGameId: '800077880',
            unityAdsTestMode: 'false'
        ]
    }
}
```

For development, test mode must be enabled for non-production builds. A safer arrangement is to make debug and release values explicit through Gradle properties or a private CI configuration:

```groovy
android {
    defaultConfig {
        manifestPlaceholders.unityAdsGameId = providers.gradleProperty('UNITY_GAME_ID')
            .orElse('REPLACE_WITH_TEST_GAME_ID')
            .get()
    }

    buildTypes {
        debug {
            manifestPlaceholders.unityAdsTestMode = 'true'
        }
        release {
            manifestPlaceholders.unityAdsTestMode = 'false'
        }
    }
}
```

Do not ship production ads while testing. Unity’s initialization example explicitly uses a `testMode` Boolean, where `true` restricts the app to test ads.[4]

### 3.4 Existing `UnityAdManager`

`UnityAdManager` already follows the official lifecycle reasonably well:

1. It reads the Game ID and test mode from manifest metadata.
2. It initializes with `UnityAds.initialize(...)`.
3. It loads interstitial and rewarded placements after initialization.
4. It tracks readiness manually because `UnityAds.isReady()` is no longer used by the project.
5. It shows only loaded placements.
6. It reloads placements after show completion or show failure.
7. It destroys banner views when they are removed.

The manager uses these placement IDs:

```kotlin
private const val PLACEMENT_REWARDED = "Rewarded_Android"
private const val PLACEMENT_INTERSTITIAL = "Interstitial_Android"
private const val PLACEMENT_BANNER = "Banner_Android"
```

These strings must exactly match the ad unit or placement identifiers configured for the corresponding Android Game ID in the Unity dashboard. A valid Game ID alone is not sufficient if the placement name is wrong or disabled.

The primary defect is that the manager is effectively orphaned. `UnityAdManager.preloadAds()` is defined, but the splash startup path initializes Start.io and AdMob after consent and does not initialize Unity. The manager can still be triggered lazily by `showInterstitial()` or `showRewarded()`, but banners will be skipped whenever the wrapper is composed before Unity initialization completes.

## 4. Correct initialization sequence for KiduyuTV

Unity’s initialization documentation says to initialize early in the runtime lifecycle, pass the appropriate platform Game ID, pass the test-mode Boolean, and provide an `IUnityAdsInitializationListener`.[4] KiduyuTV already has a consent gate in `SplashActivity`, so the correct order is:

```text
Application startup
    -> UMP requests/updates consent
    -> consent is propagated to Unity metadata
    -> Unity Ads initializes
    -> AdMob/Start.io/Wortise initialize
    -> Unity placements are preloaded
    -> MainActivity is allowed to render ad surfaces
```

The existing `SplashActivity` consent block should be updated so Unity initializes after `ConsentManager.requestConsent()` completes:

```kotlin
ConsentManager.requestConsent(this) {
    // ConsentManager already forwards the UMP decision to Unity through
    // MetaData("gdpr.consent") and MetaData("privacy.consent").
    UnityAdManager.preloadAds(this@SplashActivity)

    StartAppAdManager.preloadAds(this@SplashActivity)

    AdManager.initAndAwait(this@SplashActivity) {
        adsConsentHandled = true
    }
}
```

Add the import:

```kotlin
import com.kiduyuk.klausk.kiduyutv.util.UnityAdManager
```

A more robust version exposes a completion callback from Unity initialization so the splash can track readiness without blocking forever:

```kotlin
fun initializeAfterConsent(
    context: Context,
    onComplete: (success: Boolean) -> Unit = {}
) {
    if (!shouldShowAds(context)) {
        onComplete(false)
        return
    }

    if (UnityAds.isInitialized) {
        isInitialised = true
        loadFullscreenPlacements()
        onComplete(true)
        return
    }

    val gameId = readUnityGameId(context)
    if (gameId.isNullOrBlank()) {
        Log.w(TAG, "Unity Game ID missing")
        onComplete(false)
        return
    }

    UnityAds.initialize(
        context.applicationContext,
        gameId,
        readUnityTestMode(context),
        object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() {
                isInitialised = true
                loadFullscreenPlacements()
                onComplete(true)
            }

            override fun onInitializationFailed(
                error: UnityAds.UnityAdsInitializationError,
                message: String
            ) {
                isInitialised = false
                _isInterstitialReady = false
                _isRewardedReady = false
                Log.w(TAG, "Unity initialization failed: $error: $message")
                onComplete(false)
            }
        }
    )
}
```

The app should not prevent normal navigation if Unity fails to initialize. The callback should report failure and let another ad network or an ad-free fallback continue the user flow.

## 5. Interstitial ads

Unity’s documented interstitial flow is **initialize → load → show after `onUnityAdsAdLoaded`**.[5] KiduyuTV’s existing `showInterstitial()` method is close to correct because it checks initialization, a cooldown, readiness, and reloads after completion/failure.

A production-safe direct implementation should look like this:

```kotlin
fun showInterstitial(
    activity: Activity,
    onFinished: () -> Unit = {}
) {
    if (!shouldShowAds(activity)) {
        onFinished()
        return
    }

    if (!UnityAds.isInitialized || !isInitialised) {
        UnityAdManager.preloadAds(activity)
        onFinished()
        return
    }

    if (!isInterstitialReady) {
        UnityAds.load(PLACEMENT_INTERSTITIAL, loadListener)
        onFinished()
        return
    }

    _isInterstitialReady = false

    UnityAds.show(
        activity,
        PLACEMENT_INTERSTITIAL,
        UnityAdsShowOptions(),
        object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(
                placementId: String,
                error: UnityAds.UnityAdsShowError,
                message: String
            ) {
                UnityAds.load(PLACEMENT_INTERSTITIAL, loadListener)
                onFinished()
            }

            override fun onUnityAdsShowStart(placementId: String) = Unit

            override fun onUnityAdsShowClick(placementId: String) = Unit

            override fun onUnityAdsShowComplete(
                placementId: String,
                state: UnityAds.UnityAdsShowCompletionState
            ) {
                lastInterstitialShownAt = System.currentTimeMillis()
                UnityAds.load(PLACEMENT_INTERSTITIAL, loadListener)
                onFinished()
            }
        }
    )
}
```

The callback must be invoked on both show failure and show completion. Otherwise a failed Unity request can leave navigation blocked. The existing manager handles this correctly in principle.

### 5.1 Add Unity to `AdFallbackDispatcher`

The current dispatcher routes interstitials only between AdMob and Start.io. A minimal Unity-aware version is:

```kotlin
fun showInterstitial(
    activity: Activity,
    onDismissed: () -> Unit
) {
    when {
        AdManager.isInterstitialReady -> {
            AdManager.showInterstitial(activity, onDismissed)
        }
        UnityAdManager.isInterstitialReady -> {
            UnityAdManager.showInterstitial(activity, onDismissed)
        }
        StartAppAdManager.isInterstitialReady -> {
            StartAppAdManager.showInterstitial(activity, onDismissed)
        }
        else -> {
            // Do not stall navigation while waiting for an ad.
            UnityAdManager.preloadAds(activity)
            StartAppAdManager.preloadAds(activity)
            onDismissed()
        }
    }
}
```

The exact `StartAppAdManager.isInterstitialReady` property should be confirmed against the current manager API. If it does not exist, retain the existing Start.io fallback branch and use Unity only when its readiness flag is true.

Do not show multiple networks for one user action. The dispatcher must choose one network, call exactly one show method, and invoke `onDismissed` exactly once.

### 5.2 Fix direct-stream launch bypass

The existing `DirectStreamLauncher` calls `AdManager.showInterstitial(...)` directly. That means Unity cannot participate in the ad waterfall for the most important playback transition. Replace that direct call with the shared dispatcher:

```kotlin
if (AdManager.isInterstitialReady || UnityAdManager.isInterstitialReady) {
    AdFallbackDispatcher.showInterstitial(activity) {
        activity.startActivityForResult(intent, REQUEST_CODE)
    }
} else {
    AdManager.preloadInterstitial(activity)
    UnityAdManager.preloadAds(activity)
    activity.startActivityForResult(intent, REQUEST_CODE)
}
```

If the project uses a TV-specific `TvInterstitialManager`, make that façade delegate to the same dispatcher rather than creating another independent ad-selection policy.

## 6. Rewarded ads

The official rewarded flow requires checking `UnityAds.UnityAdsShowCompletionState.COMPLETED` before granting the reward.[6] Closing the ad, skipping it, or encountering a show failure must not grant the reward.

KiduyuTV’s current `showRewarded()` implementation correctly checks the completion state:

```kotlin
if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
    onRewarded()
}
onDismissed()
```

The key fix is making Unity reachable through the product’s rewarded entry point. The current settings screen calls `AdFallbackDispatcher.showRewardedInterstitial(...)`, but the dispatcher chooses AdMob or Start.io and never Unity. Add Unity as a fallback:

```kotlin
fun showRewarded(
    activity: Activity,
    onRewarded: () -> Unit,
    onDismissed: () -> Unit
) {
    when {
        AdManager.isRewardedReady -> {
            AdManager.showRewarded(activity, onRewarded, onDismissed)
        }
        UnityAdManager.isRewardedReady -> {
            UnityAdManager.showRewarded(activity, onRewarded, onDismissed)
        }
        else -> {
            UnityAdManager.preloadAds(activity)
            StartAppAdManager.showRewarded(activity, onRewarded, onDismissed)
        }
    }
}
```

If the UI specifically requests a rewarded interstitial, define that product behavior clearly. Unity’s direct manager currently exposes rewarded video, not a separate rewarded-interstitial API. Either route that request to the ordinary rewarded placement or keep the AdMob rewarded-interstitial path separate and document the distinction.

A safe Compose call site is:

```kotlin
val context = LocalContext.current
val activity = context as? Activity

Button(
    enabled = activity != null,
    onClick = {
        activity?.let { currentActivity ->
            AdFallbackDispatcher.showRewarded(
                activity = currentActivity,
                onRewarded = {
                    // Grant the benefit only here.
                    viewModel.recordSupportReward()
                },
                onDismissed = {
                    // Re-enable the button or update loading state here.
                }
            )
        }
    }
) {
    Text("Watch an ad")
}
```

Never grant a reward in `onUnityAdsShowStart`, `onUnityAdsShowClick`, `onDismissed`, or `onUnityAdsShowFailure`.

## 7. Banner ads

Unity requires initialization before displaying a banner. The banner implementation creates a `BannerView`, attaches an `IListener`, inserts the view into the Android view hierarchy, calls `load()`, and destroys the view when it is no longer needed.[7]

KiduyuTV already has a `UnityBannerAdView` Compose wrapper that uses `AndroidView`, creates a `FrameLayout`, delegates loading through `AdFallbackDispatcher.loadBanner(..., BannerNetwork.UNITY)`, and calls `UnityAdManager.destroyBanner()` in `DisposableEffect`.

The wrapper pattern should look like this:

```kotlin
@Composable
fun UnityBannerAdView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val container = remember { FrameLayout(context) }

    AndroidView(
        modifier = modifier,
        factory = { container }
    )

    DisposableEffect(activity, container) {
        UnityAdManager.loadBanner(activity, container)

        onDispose {
            UnityAdManager.destroyBanner()
            container.removeAllViews()
        }
    }
}
```

The current active phone and TV surfaces are more important than the existence of the wrapper. `BannerAdView` and `TvBannerAdView` currently instantiate AdMob `AdView` objects directly and fall back to Start.io. Therefore, Unity banners are not used unless those active components are changed.

A simple network-selectable banner surface is:

```kotlin
@Composable
fun NetworkBannerAdView(
    network: AdFallbackDispatcher.BannerNetwork,
    modifier: Modifier = Modifier
) {
    when (network) {
        AdFallbackDispatcher.BannerNetwork.UNITY -> {
            UnityBannerAdView(modifier)
        }
        AdFallbackDispatcher.BannerNetwork.STARTAPP -> {
            StartAppBannerAdView(modifier)
        }
        AdFallbackDispatcher.BannerNetwork.ADMOB,
        AdFallbackDispatcher.BannerNetwork.WORTISE -> {
            BannerAdView(modifier)
        }
    }
}
```

Then replace the active phone or TV wrapper at the desired location:

```kotlin
if (!SettingsManager(context).isAdsDisabled()) {
    NetworkBannerAdView(
        network = AdFallbackDispatcher.BannerNetwork.UNITY,
        modifier = Modifier.fillMaxWidth()
    )
}
```

For a real fallback chain, the banner component must wait for a load-failure callback before attempting another network. It should not create multiple banner SDK views on top of one another. A dispatcher method can expose a callback:

```kotlin
fun loadUnityBanner(
    activity: Activity,
    container: ViewGroup,
    onFailed: () -> Unit
) {
    UnityAdManager.loadBanner(
        activity = activity,
        container = container,
        onFailed = onFailed
    )
}
```

That requires extending `UnityAdManager.loadBanner()` so the `onBannerFailedToLoad()` callback calls `onFailed()`. The current manager only logs the failure, so a banner failure cannot automatically advance to a different network.

### 7.1 Banner sizing

The supplied Unity banner documentation uses `UnityBannerSize(320, 50)` for a standard banner and discusses MREC dimensions such as `300 x 250`.[7] KiduyuTV’s existing manager uses `320 x 50`. For TV screens, validate the banner’s physical size and focus behavior; a small phone banner may be visually inappropriate on a large living-room display.

### 7.2 Banner cleanup

Always remove the banner from its parent and call `destroy()` when the screen leaves composition. This is especially important in Compose because navigation can create and dispose Android views repeatedly. The current `UnityBannerAdView` and `UnityAdManager.destroyBanner()` are designed for this lifecycle and should be retained.

## 8. Consent and privacy

`ConsentManager` already uses Google UMP and forwards consent to Unity through:

```kotlin
MetaData(context).apply {
    set("gdpr.consent", if (canPersonalize) "true" else "false")
    set("privacy.consent", if (canPersonalize) "true" else "false")
    commit()
}
```

This propagation must happen before `UnityAds.initialize()`. The current startup sequence should therefore call `UnityAdManager.preloadAds()` only inside the UMP completion callback, not from `KiduyuTvApp.onCreate()` before consent is known.

The existing `SettingsManager.isAdsDisabled()` implementation is also a blocker because it currently returns `false` regardless of the stored preference. Fix it before connecting Unity to the dispatcher:

```kotlin
fun isAdsDisabled(): Boolean {
    return prefs.getBoolean(KEY_ADS_DISABLED, false)
}
```

Use the same preference key that `setAdsDisabled()` writes. If there is no shared constant, define one:

```kotlin
private const val KEY_ADS_DISABLED = "ads_disabled"

fun setAdsDisabled(disabled: Boolean) {
    prefs.edit().putBoolean(KEY_ADS_DISABLED, disabled).apply()
}

fun isAdsDisabled(): Boolean = prefs.getBoolean(KEY_ADS_DISABLED, false)
```

The application should expose the required privacy-options form from settings when UMP reports that privacy options are required. Unity-specific metadata forwarding should be repeated after the user changes privacy choices, which the existing `showPrivacyOptionsForm()` path already attempts to do.

## 9. Placement and flavor strategy

Use explicit placement names per format and flavor. Do not reuse an interstitial placement as a banner placement. A simple mapping is:

```kotlin
object UnityAdUnits {
    const val INTERSTITIAL = "Interstitial_Android"
    const val REWARDED = "Rewarded_Android"
    const val BANNER = "Banner_Android"
}
```

If the Unity dashboard has separate placements for phone and TV, make the flavor part of the configuration:

```groovy
productFlavors {
    phone {
        dimension 'formfactor'
        buildConfigField 'String', 'UNITY_INTERSTITIAL_PLACEMENT', '"Interstitial_Phone"'
        buildConfigField 'String', 'UNITY_REWARDED_PLACEMENT', '"Rewarded_Phone"'
        buildConfigField 'String', 'UNITY_BANNER_PLACEMENT', '"Banner_Phone"'
    }

    tv {
        dimension 'formfactor'
        buildConfigField 'String', 'UNITY_INTERSTITIAL_PLACEMENT', '"Interstitial_TV"'
        buildConfigField 'String', 'UNITY_REWARDED_PLACEMENT', '"Rewarded_TV"'
        buildConfigField 'String', 'UNITY_BANNER_PLACEMENT', '"Banner_TV"'
    }
}
```

Then read the values in Kotlin through `BuildConfig`. This prevents accidental reuse of a phone placement in a TV build and lets Unity dashboard reporting distinguish the experiences.

## 10. ProGuard and release builds

The repository already contains Unity-specific keep rules for `com.unity3d.ads`, `com.unity3d.services`, mediation, and banner packages. Keep those rules while validating release builds. Do not add broad reflection rules unnecessarily if the SDK’s own consumer ProGuard rules are being imported through Gradle.

A representative minimal set is:

```proguard
-keep class com.unity3d.ads.** { *; }
-keep class com.unity3d.services.** { *; }
-keep class com.unity3d.mediation.** { *; }
-keep class com.unity3d.services.banners.** { *; }
-keepattributes *Annotation*
```

The Gradle dependency should be used rather than manually copying an AAR. Unity specifically warns that manually including the AAR can omit or mishandle dependencies introduced in newer SDK versions.[3]

## 11. Recommended implementation order

### Step 1: Standardize dependencies

Choose one direct Unity Ads SDK version, update the explicit dependency to that version, and remove the hidden resolution force. Decide separately whether AdMob mediation or the direct Unity API is the intended monetization path.

### Step 2: Fix the ad preference

Make `SettingsManager.isAdsDisabled()` return the stored preference. Verify that every banner, interstitial, and rewarded entry point checks this setting before requesting an ad.

### Step 3: Initialize after consent

Add `UnityAdManager.preloadAds(this@SplashActivity)` inside the `ConsentManager.requestConsent()` completion callback. Do not initialize Unity earlier in `Application.onCreate()`.

### Step 4: Add Unity to the dispatcher

Update `AdFallbackDispatcher` so Unity can be chosen for interstitial and rewarded requests. Ensure each request has exactly one winner and all failure paths invoke the continuation callback.

### Step 5: Migrate bypassing call sites

Replace direct AdMob calls in `DirectStreamLauncher` with `AdFallbackDispatcher`. Review every remaining `AdManager.show...` call to determine whether it should participate in the common fallback policy.

### Step 6: Activate Unity banners intentionally

Replace or parameterize the active `BannerAdView` and `TvBannerAdView` surfaces. The existing `UnityBannerAdView` is not enough if no active screen composes it.

### Step 7: Test debug mode and release mode separately

Use test Game IDs/placements or test mode for development. Verify that release builds use production configuration only after a successful test pass.

## 12. Verification checklist

| Test | Expected result |
| --- | --- |
| Fresh install, consent required | UMP completes before Unity initialization begins |
| Fresh install, no consent form | Unity initializes after UMP completion without blocking startup |
| Unity initialization failure | App continues and another ad network or no-ad path is used |
| Interstitial load success | Ad shows once, continuation runs once, placement reloads |
| Interstitial show failure | Continuation runs and placement reloads |
| Rewarded completion | Reward is granted exactly once |
| Rewarded skip/close/failure | No reward is granted; UI recovers |
| Banner load success | View is attached exactly once |
| Banner load failure | Fallback is attempted once, without stacked views |
| Compose navigation away | Banner is removed and destroyed |
| Direct stream launch | Common dispatcher is used rather than AdMob-only logic |
| Ads disabled | No Unity, AdMob, Start.io, or other ad request is made |
| Phone flavor | Phone Game ID/placements and banner behavior are used |
| TV flavor | TV Game ID/placements and D-pad-safe banner behavior are used |
| Release/R8 build | Unity callbacks and banner classes remain functional |
| Offline startup | App remains usable without waiting indefinitely for ad SDKs |

## 13. Logging and production hardening

The current Unity manager logs placement identifiers, errors, and messages. That is useful during development, but production logs should avoid emitting sensitive request data and should use a build-controlled logging policy. The broader application also starts logcat capture, so ad callbacks should not log unnecessary identifiers, cookies, or user information.

Avoid showing an ad during a critical playback transition if it would create a poor user experience. A practical policy for KiduyuTV is to show an interstitial before starting playback only when a ready ad exists and a cooldown has elapsed; otherwise start playback immediately. Rewarded ads should always be opt-in and should never be required to browse or play content.

The Unity sample repository is useful as a basic Kotlin reference, but it is a tutorial project with older dependency versions and simplified helpers. Its rewarded helper correctly grants a reward only for `COMPLETED`, and its interstitial helper reloads after completion/failure, but it does not provide the lifecycle, consent, Compose disposal, fallback arbitration, or multi-flavor configuration required by KiduyuTV.[8]

## References

[1]: https://docs.unity.com/en-us/grow/ads/android-sdk "Unity Ads SDK integration guide for Android developers"
[2]: https://docs.unity.com/en-us/grow/ads/android-sdk/requirements "Unity Ads Android integration requirements"
[3]: https://docs.unity.com/en-us/grow/ads/android-sdk/install-sdk "Install the Unity Ads SDK for Android"
[4]: https://docs.unity.com/en-us/grow/ads/android-sdk/initialize-sdk "Initialize the SDK in Android"
[5]: https://docs.unity.com/en-us/grow/ads/android-sdk/interstitial-ads "Implement interstitial ads in Android"
[6]: https://docs.unity.com/en-us/grow/ads/android-sdk/rewarded-ads "Implement rewarded ads in Android"
[7]: https://docs.unity.com/en-us/grow/ads/android-sdk/banner-ads "Implement banner ads in Android"
[8]: https://github.com/Coding-Meet/Unity-Ads-App "Coding-Meet Unity Ads Android Kotlin sample repository"
[9]: https://github.com/kiduyu-klaus/KiduyuTv_final/blob/main/app/build.gradle "KiduyuTV Android Gradle configuration"
[10]: https://github.com/kiduyu-klaus/KiduyuTv_final/blob/main/app/src/main/java/com/kiduyuk/klausk/kiduyutv/util/UnityAdManager.kt "KiduyuTV UnityAdManager.kt"
[11]: https://github.com/kiduyu-klaus/KiduyuTv_final/blob/main/app/src/main/java/com/kiduyuk/klausk/kiduyutv/util/AdFallbackDispatcher.kt "KiduyuTV AdFallbackDispatcher.kt"
[12]: https://github.com/kiduyu-klaus/KiduyuTv_final/blob/main/app/src/main/java/com/kiduyuk/klausk/kiduyutv/util/ConsentManager.kt "KiduyuTV ConsentManager.kt"
