# Start.io documentation and official Compose demo review

This review compares KiduyuTV’s Start.io integration with Start.io’s Maven integration article, the Advanced Usage article, and every file in the official `app-compose` demo at commit `eca959faed7f67c7e33547332bab8366753e71b1` (2025-07-15).

The reviewed KiduyuTV branch is `v1.1.906` / commit `74c25b17`.

## Sources

[1]: https://support.start.io/hc/en-us/articles/360014774799-Integration-via-Maven "Start.io Integration via Maven"
[2]: https://support.start.io/hc/en-us/articles/360006662474-Advanced-Usage "Start.io Advanced Usage"
[3]: https://github.com/StartApp-SDK/android-sdk-demo/tree/eca959faed7f67c7e33547332bab8366753e71b1/app-compose "Official Start.io Compose demo at reviewed commit"
[4]: https://github.com/StartApp-SDK/android-sdk-demo/blob/eca959faed7f67c7e33547332bab8366753e71b1/app-compose/src/main/kotlin/com/startapp/demo/compose/MainActivity.kt "Official Compose demo MainActivity.kt"
[5]: https://github.com/StartApp-SDK/android-sdk-demo/blob/eca959faed7f67c7e33547332bab8366753e71b1/app-compose/src/main/kotlin/com/startapp/demo/compose/NativeAdLayout.kt "Official Compose demo NativeAdLayout.kt"
[6]: https://github.com/StartApp-SDK/android-sdk-demo/blob/eca959faed7f67c7e33547332bab8366753e71b1/app-compose/build.gradle.kts "Official Compose demo build.gradle.kts"

## Overall conclusion

KiduyuTV has a substantial direct Start.io integration. Its interstitial, rewarded, banner, native, consent, timeout, and fallback paths are not merely copied from the demo; they add application-specific policy and fallback behavior around the SDK.

The most important incompatibility with the official guidance is not an API omission. It is the current release configuration:

```kotlin
StartAppSDK.setTestAdsEnabled(true)
```

KiduyuTV executes this unconditionally from `StartAppAdManager.preloadAds()`. The official demo contains the same call, but explicitly labels it as a line to remove before production. The KiduyuTV code therefore needs a debug/QA build guard before any live release.

The most important API difference is banners. The official Compose demo uses the newer asynchronous `BannerRequest` → `BannerCreator` → `View` flow, described in its README as the optimized advanced integration. KiduyuTV uses the older direct `Banner(activity)` constructor. The older API is still represented in Start.io’s Advanced Usage article, so this is not automatically incorrect, but it is an opportunity to align with the current official sample.

KiduyuTV’s native-ad implementation follows the official Compose sample’s most important rule: the Compose container uses `IntrinsicSize.Max`, and a full-size Android `View` is registered with `registerViewForInteraction()`. That is the strongest direct match between the two implementations.

## 1. Maven integration guidance compared with KiduyuTV

### Dependency and repository

The Maven article identifies SDK version `5.3.1` and shows a dynamic dependency:

```gradle
implementation 'com.startapp:inapp-sdk:5.+'
```

It presents `mavenCentral()` as the preferred repository and also contains an older alternate repository section using `startappdev.bintray.com`. The official Compose demo uses `mavenCentral()` and the same dynamic `5.+` dependency. KiduyuTV instead uses:

```gradle
implementation 'com.startapp:inapp-sdk:5.3.1'
```

and forces the same version through Gradle resolution.

KiduyuTV’s fixed version is safer for reproducible builds. The demo’s `5.+` dependency is convenient for a sample but can silently change behavior between builds. The app should keep the fixed version and update it deliberately after reviewing the release notes and rebuilding all ad paths.

### Initialization methods

The Maven article presents two initialization styles and says to use only one:

1. Automatic initialization through the SDK provider.
2. Explicit code initialization after removing the provider.

KiduyuTV uses the second style. It removes:

```xml
<com.startapp.sdk.adsbase.StartAppInitProvider ... tools:node="remove" />
```

and initializes from `StartAppAdManager.preloadAds()` with:

```kotlin
StartAppSDK.init(
    context.applicationContext,
    BuildConfig.START_IO_APP_ID,
    false
)
```

The final `false` disables Return Ads, consistent with the manifest metadata:

```xml
<meta-data
    android:name="com.startapp.sdk.RETURN_ADS_ENABLED"
    android:value="false" />
```

The official Compose demo uses the newer builder/callback form:

```kotlin
StartAppSDK.initParams(applicationContext, "205489527")
    .setReturnAdsEnabled(false)
    .setCallback { initializedFlow.value = true }
    .init()
```

The callback is important. The demo does not mark the SDK ready merely because the init method returned. KiduyuTV does mark `isInitialised` immediately after `StartAppSDK.init()` returns. If SDK 5.3.1 supports `initParams` as used by the official demo, KiduyuTV should consider moving to the callback form and only publish `isInitialisedState = true` from the callback. That would make the Compose wrappers’ readiness signal reflect actual SDK initialization rather than a synchronous method return.

### App ID location

The Maven article shows a manifest application ID metadata entry. The official Compose demo does not include that metadata; it supplies the ID through `initParams`. KiduyuTV also supplies the ID in code through `BuildConfig.START_IO_APP_ID` and does not declare `com.startapp.sdk.APPLICATION_ID` metadata.

That arrangement is internally consistent with explicit code initialization. The app ID is currently hardcoded in Gradle:

```gradle
def startIoAppId = "207814611"
```

It would be cleaner to source it from a Gradle property or environment variable, as the repository already does for several other ad credentials. The app ID is not a secret, but separating QA and production IDs prevents test inventory from accidentally reaching the production build.

### Optional permissions

The Maven article lists Bluetooth and Advertising ID permissions as optional. KiduyuTV declares Bluetooth and an Advertising ID permission, together with many unrelated application permissions. The official Compose demo does not declare these permissions in its module manifest.

This difference does not prove that either configuration is wrong. The SDK and target SDK determine which permissions are merged or required. The important point is that privacy-sensitive permissions should be included intentionally and checked in the merged release manifest rather than copied from the SDK article without a policy decision.

### COPPA and Wi-Fi permission

The Maven article says that child-targeted apps using Maven should remove `ACCESS_WIFI_STATE` from the manifest merger. KiduyuTV already has:

```xml
<uses-permission
    android:name="android.permission.ACCESS_WIFI_STATE"
    tools:node="remove" />
```

This matches that specific guidance.

However, the same article says Start.io services must not be used in apps distributed through Google Play that are primarily directed at children. KiduyuTV currently declares:

```xml
MIXED_AUDIENCE = false
CHILD_DIRECTED = false
```

Those values must reflect the actual audience and distribution policy. They should not be treated as a technical default. If the app is mixed-audience, the Start.io guidance says to mark it as mixed audience and determine child-directed handling per user when legally appropriate. If it is primarily child-directed, Start.io’s own documentation says not to use the service on Google Play. [1]

### Start.io’s own consent disclosure

The Maven article says Start.io’s disclosure/consent box is enabled by default. KiduyuTV separately uses Google UMP and forwards UMP-derived signals to Start.io, but the reviewed manifest does not show a setting that explicitly disables Start.io’s own disclosure dialog.

This creates a behavior that must be verified on a clean install: the user could see UMP and then a Start.io disclosure/consent surface, or Start.io may suppress its own UI under the selected initialization/configuration path. The source review cannot establish the SDK’s runtime behavior here. If UMP is intended to be the only consent UI, confirm the correct SDK 5.3.1 setting for suppressing duplicate Start.io disclosure and test it on EEA-like consent states.

## 2. Advanced Usage guidance compared with KiduyuTV

### Interstitials

The Advanced Usage article recommends an object-based flow for control over loading and display. It documents:

- `StartAppAd` objects.
- `loadAd()` with `AdEventListener`.
- `showAd()` with `AdDisplayListener`.
- Automatic, video, and offerwall modes.
- `adDisplayed`, `adHidden`, `adClicked`, and `adNotDisplayed` callbacks.
- The instruction not to call `loadAd()` again inside `onFailedToReceiveAd()` because the SDK reloads automatically. [2]

KiduyuTV follows the object/callback model. `StartAppAdManager.showInterstitial()` creates a `StartAppAd`, calls `loadAd()`, and then calls `showAd()` only from `onReceiveAd()`.

It handles all four display callbacks and adds application-level protection around them. `AtomicBoolean` guards prevent duplicate completion, an eight-second load timeout moves the fallback chain forward, and display is dispatched to the main thread. This is more defensive than the minimal sample.

KiduyuTV does not use offerwall mode, so the Advanced Usage requirement to forward `onSaveInstanceState()` and `onRestoreInstanceState()` for that mode does not currently apply. It also does not use Start.io AutoInterstitials or Exit Ads. That is deliberate: the app has its own `FullscreenAdPolicy` and fallback dispatcher, so enabling Start.io automatic activity-transition or back-press ads would risk duplicate fullscreen ads.

The Advanced Usage article warns that a manually requested interstitial can overlap an Autostitial unless automatic interstitials are disabled around the manual show. KiduyuTV avoids that class of overlap by setting Return Ads false and never enabling Start.io AutoInterstitials.

### Rewarded video

The official guidance requires:

```kotlin
loadAd(AdMode.REWARDED_VIDEO)
```

and a `VideoListener` whose completion callback grants the reward. [1] [2]

KiduyuTV does exactly this:

```kotlin
rewardedVideo.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, ...)
```

and calls `onRewarded()` only from `onVideoCompleted()`.

The implementation also prevents duplicate reward delivery with `rewardDelivered` and prevents rewards after the flow has already been settled. This is correct. A standard interstitial may be used as a no-inventory fallback, but it deliberately does not call `onRewarded()`. The UI should make that behavior clear because users who requested a reward may not understand why a normal interstitial appeared without unlocking anything.

### Banners

The Advanced Usage article shows the classic direct `Banner` view and a `BannerListener`. KiduyuTV uses that path:

```kotlin
val banner = Banner(activity)
banner.setBannerListener(...)
container.addView(banner, ...)
```

The official Compose demo, however, uses the newer advanced flow:

```kotlin
BannerRequest(applicationContext)
    .setAdFormat(BannerFormat.BANNER)
    .load { creator, error ->
        val adView = creator?.create(applicationContext, listener)
    }
```

The demo README describes this as optimized for resource usage and recommends it for advanced integration. [4] [6]

KiduyuTV’s direct constructor has several advantages: it is simple, it fits the existing fallback state machine, and it obtains a real `Banner` view immediately. The disadvantages are that it does not use the official demo’s creator lifecycle and has no explicit Start.io-specific request cancellation or disposal handle.

The active KiduyuTV banner path is:

```text
Unity → Start.io → AdMob
```

with a five-second timeout per provider. The standalone `StartAppBannerAdView` is Start.io-only through the older direct-selection dispatcher. The official demo is not a fallback implementation; it exposes manual Load/Show/Hide controls for one SDK. Therefore, KiduyuTV’s fallback behavior is application-specific and should not be expected to match the demo’s state machine.

### MRec and Cover

The Maven article documents MRec as a 300×250 placement and Cover as a 300×157 DP placement. The official Compose demo includes MRec through `BannerFormat.MREC`, but does not demonstrate Cover in `MainActivity.kt`.

KiduyuTV does not currently use Start.io MRec or Cover. Its in-feed native ad is a different format and should not be described as MRec.

### Splash and Return Ads

Start.io’s Maven article recommends Splash Ads and says they are enabled by default. Both the official Compose demo and KiduyuTV explicitly disable them:

```xml
<meta-data
    android:name="com.startapp.sdk.SPLASH_ENABLED"
    android:value="false" />
```

KiduyuTV contains a `showSplash()` helper, but no application call site invokes it. This is dead/inactive integration code at present.

Return Ads are disabled in both the official demo initialization and KiduyuTV’s manifest/code configuration. This is appropriate because KiduyuTV already owns its app-open behavior through AdMob and uses a shared fullscreen cooldown.

### Native ads

The Advanced Usage article describes native ads as raw ad data with application-owned UI. It requires the app to register an interaction view with:

```kotlin
nativeAdDetails.registerViewForInteraction(view)
```

The official Compose demo highlights two important details:

1. Use `IntrinsicSize.Max` on the outer Compose container.
2. Place a full-size Android `View` over the rendered ad and register that view for interaction. [2] [5]

KiduyuTV follows both rules. `StartAppNativeAdView` uses:

```kotlin
.height(IntrinsicSize.Max)
```

and registers a full-size Android `View`:

```kotlin
ad.registerViewForInteraction(this)
```

The wrapper also unregisters the ad when Compose disposes and unregisters a late-loaded ad if the request completes after disposal. This is stronger lifecycle handling than the basic demo UI, which unregisters on its explicit Hide action.

The official demo requests auto-downloaded bitmaps but also specifies an image size in its detailed documentation. KiduyuTV sets `setAutoBitmapDownload(true)` but does not explicitly call `setPrimaryImageSize()`, so it relies on the SDK default. If the UI is designed around a 120dp image, specifying the image size explicitly would make the requested asset more predictable.

KiduyuTV displays title, category, description, call-to-action text, and an ad label. It does not display rating, installs, package name, or secondary icon, although those fields are available in the documented `NativeAdDetails` API. That is a UI choice, not an integration defect.

### Ad tags and demographic data

The Advanced Usage article documents ad tags for banners, interstitials, and native ads, and documents optional demographic fields such as age and gender. KiduyuTV does not use either feature. That is the correct privacy-minimal default unless the product has a documented consented use case and a reliable source for those attributes.

The article also recommends app-ads.txt. That cannot be verified from the Android source tree; it belongs to the publisher website/domain and Start.io portal configuration.

## 3. File-by-file review of the official `app-compose` folder

The reviewed folder contains seven files.

### `app-compose/.gitignore`

The file contains:

```text
release
```

It excludes a release directory from the module. This is ordinary sample-project hygiene and has no Start.io runtime effect.

### `app-compose/build.gradle.kts`

The module applies:

```kotlin
com.android.application
org.jetbrains.kotlin.android
org.jetbrains.kotlin.plugin.compose
```

It uses namespace and application ID `com.startapp.demo.compose`, compile/target SDK 36, minimum SDK 21, Java/Kotlin 1.8 bytecode, and Compose build support.

Dependencies are deliberately minimal:

```kotlin
implementation("com.startapp:inapp-sdk:5.+")
implementation("androidx.activity:activity-compose:1.10.1")
implementation("androidx.compose.material3:material3:1.3.2")
```

The demo does not declare a separate UMP dependency, Firebase dependency, fallback network, lifecycle manager, or ad analytics layer. It is intended to demonstrate direct SDK usage, not production ad orchestration.

Compared with KiduyuTV, the demo’s dynamic `5.+` dependency is less reproducible, while KiduyuTV’s fixed `5.3.1` is more controlled. The demo’s Kotlin 2.0 Compose setup is newer than the general application context in which KiduyuTV’s Start.io code was written, so API availability should be checked when migrating the banner path.

### `app-compose/src/main/AndroidManifest.xml`

The demo manifest:

- Declares the Compose activity.
- Uses a network security config.
- Adds Android ad-services configuration for Topics.
- Disables Start.io Splash Ads.
- Does not declare a Start.io App ID in manifest metadata.
- Does not declare the optional permissions shown in the Maven article.

The relevant Start.io entry is:

```xml
<meta-data
    android:name="com.startapp.sdk.SPLASH_ENABLED"
    android:value="false" />
```

There is no explicit provider removal because the demo initializes through `StartAppSDK.initParams()` and does not show the provider-removal pattern in this module. The SDK initialization occurs from `MainActivity`.

KiduyuTV’s manifest is more complex and explicitly removes the provider so initialization waits for UMP. That is a reasonable production choice, but it makes the app responsible for every initialization and consent timing detail.

### `app-compose/src/main/kotlin/com/startapp/demo/compose/MainActivity.kt`

This is the complete demo application and contains four functional areas: initialization, Compose state, interstitial/rewarded ads, banner/MRec ads, and native ads.

#### Initialization

The activity uses a process-shared `MutableStateFlow<Boolean?>` called `initializedFlow`. A lifecycle coroutine runs inside `repeatOnLifecycle(Lifecycle.State.STARTED)` and initializes the SDK once when the state is null:

```kotlin
StartAppSDK.setTestAdsEnabled(true)
StartAppSDK.initParams(applicationContext, "205489527")
    .setReturnAdsEnabled(false)
    .setCallback { initializedFlow.value = true }
    .init()
```

The demo uses Start.io’s public demo App ID and explicitly comments that a real App ID must be used in production. It also explicitly comments that the test-ad line must be removed before production.

KiduyuTV’s `StartAppAdManager` has a stronger global eligibility gate and consent forwarding, but it should adopt the same explicit callback readiness model. The demo’s `initializedFlow` becomes true only in the SDK callback, whereas KiduyuTV sets its state immediately after `init()` returns.

#### Compose state model

The activity has one `MutableStateFlow<Pair<Ad?, AdState>?>` per ad type:

- Interstitial: `StartAppAd?`
- Rewarded: `StartAppAd?`
- Banner: `View?`
- MRec: `View?`
- Native: `NativeAdDetails?`

Each state has one of:

```kotlin
IDLE
LOADING
VISIBLE
```

The `Helper` class computes whether Load, Show, and Hide buttons are enabled. This is a simple but useful model for a demo because each SDK object remains associated with its UI state.

KiduyuTV’s production code does not expose manual Load/Show buttons. It uses centralized fallback dispatchers, timeout states, and callbacks. The demo is therefore best understood as a reference for SDK object ownership and callback ordering, not as a replacement for KiduyuTV’s dispatchers.

#### Interstitial loading and showing

`loadInterstitial()` creates a `StartAppAd`, calls `loadAd()`, and stores the object only after `onReceiveAd()`.

`showInterstitial()` clears the flow before calling `showAd()`, then logs every `AdDisplayListener` callback. If no ad object is present, it shows a Toast.

This confirms the important SDK contract: do not call `showAd()` until the load callback reports success. KiduyuTV follows this contract and adds timeout/fallback behavior.

#### Rewarded loading and showing

`loadRewarded()` uses:

```kotlin
AdMode.REWARDED_VIDEO
```

After the ad is loaded, `showRewarded()` installs a `VideoListener` before showing the ad. The completion callback displays a Toast saying the user gained a reward.

KiduyuTV installs its video listener before `showAd()` as well. Its callback is more production-safe because it has duplicate-reward protection and separates reward delivery from dismissal.

#### Banner and MRec loading

The demo implements both through `loadAdView()`:

```kotlin
BannerRequest(applicationContext)
    .setAdFormat(format)
    .setAdPreferences(adPreferences)
    .load { creator, error ->
        val adView = creator.create(applicationContext, listener)
    }
```

The `BannerFormat` is either `BANNER` or `MREC`. The resulting Android `View` is stored in a flow, and Compose displays it using `AndroidView`.

The demo registers `BannerListener` callbacks at view creation. It does not create a provider fallback chain, impose a timeout, or cancel a request when the composable leaves the screen.

KiduyuTV’s active banners use adaptive AdMob `AdView` objects as the eventual AdMob leg and a direct Start.io `Banner` object as the Start.io leg. The official demo suggests that a future KiduyuTV refactor could use `BannerRequest` to create the Start.io view asynchronously, then hand it to the existing per-container fallback state machine.

#### Native ad loading

The demo uses:

```kotlin
val adPreferences = NativeAdPreferences()
adPreferences.setAutoBitmapDownload(true)
val nativeAd = StartAppNativeAd(applicationContext)
nativeAd.setPreferences(adPreferences)
```

It retrieves the first `NativeAdDetails` after `onReceiveAd()`. It handles null/empty results as failure.

KiduyuTV uses the overload that passes preferences directly to `loadAd()`, requests one ad, and performs equivalent null/empty checks. The functional behavior is aligned.

#### Compose rendering

The main layout is a scrollable `Column` with Load/Show/Hide controls. Banners and MRec views are embedded with `AndroidView`. Native content is rendered through `NativeAdLayout`.

The demo does not use `DisposableEffect` for its ads. It relies on the manual Hide button for banner/MRec removal and native `unregisterView()`. KiduyuTV’s production wrapper improves this by tying unregister/removal to Compose disposal and guarding late callbacks.

### `app-compose/src/main/kotlin/com/startapp/demo/compose/NativeAdLayout.kt`

This file is the most relevant Compose reference for KiduyuTV.

The outer container uses:

```kotlin
.fillMaxWidth()
.height(IntrinsicSize.Max)
```

The source explicitly says that `IntrinsicSize.Max` is extremely important for making the native ad clickable.

The UI renders:

- Image bitmap.
- Title.
- Rating.
- Category.
- Description.
- Call-to-action button.
- “Ad” label.

The call-to-action button intentionally has a no-op Compose `onClick`. The comment explains that the actual interaction is configured in the Android view registered with the Start.io ad object.

The final `AndroidView` fills the entire container:

```kotlin
modifier = Modifier.fillMaxSize()
```

and registers that view:

```kotlin
ad.registerViewForInteraction(result)
```

KiduyuTV mirrors this structure almost exactly. It uses a transparent/focusable Android view over the Compose ad, adds TV key handling, and unregisters the native ad during disposal. The main improvement to consider is explicitly setting `setPrimaryImageSize()` to match the rendered image dimensions and ensuring the transparent interaction layer behaves correctly for both touch and Android TV focus.

### `app-compose/src/main/res/xml/ad_services_config.xml`

This file enables Android Topics access for ad services:

```xml
<ad-services-config>
    <topics allowAllToAccess="true"/>
</ad-services-config>
```

KiduyuTV’s manifest review did not show this exact Topics configuration. That does not make KiduyuTV incorrect, but it means the official demo and the production app have different Android Privacy Sandbox configuration. The app should decide whether Topics access is intended and verify the merged manifest/configuration against its consent and privacy policy.

### `app-compose/src/main/res/xml/network_security_config.xml`

The demo trusts system and user certificates in its base config and user certificates in debug overrides:

```xml
<network-security-config>
    <base-config>
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </base-config>
    <debug-overrides>
        <trust-anchors>
            <certificates src="user" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

This is demo-oriented and permits user-installed certificates in the base configuration. KiduyuTV has its own network security configuration and `usesCleartextTraffic="true"` for broader app functionality. Neither setting should be copied solely for Start.io. The production app should minimize trust exceptions and cleartext access to what its streaming and ad requirements actually need.

## 4. Recommended changes based on the comparison

### Required before release

1. Replace unconditional `StartAppSDK.setTestAdsEnabled(true)` with a debug/QA-only flag. Keep test mode enabled only in explicitly non-production variants.
2. Verify the Start.io App ID, the Start.io portal designation, UMP vendor configuration, and the actual app audience. The current `MIXED_AUDIENCE=false` and `CHILD_DIRECTED=false` values require an explicit product/privacy decision.
3. Test a clean EEA-like install to determine whether Start.io’s own default disclosure appears in addition to UMP. If duplicate consent UI is possible, configure the SDK so UMP is the sole consent surface or document the intended sequence.
4. Test the SDK callback readiness path. If `initParams(...).setCallback(...).init()` is available in the pinned SDK, use it to publish `isInitialisedState` only after the callback.

### Strongly recommended

1. Evaluate migrating Start.io banners from direct `Banner(activity)` construction to `BannerRequest`/`BannerCreator`, while preserving the existing fallback state machine and per-container stale-callback guards.
2. Add explicit native image sizing with `setPrimaryImageSize()` when the UI depends on a particular image size.
3. Add Start.io placement tags such as `home_banner`, `player_transition`, and `settings_native` if portal-level placement reporting is useful. Do not add user identifiers to tags.
4. Update stale comments and preview text: Start.io is not the first active shared banner leg, and `SectionNativeAd` no longer uses Wortise.
5. Add app-ads.txt entries in the publisher domain/portal configuration; this is outside the Android source tree.
6. Add a small integration test matrix for SDK init callback, banner timeout, interstitial late-load suppression, rewarded completion, native unregister, consent change, and Android TV D-pad interaction.

## References

[1]: https://support.start.io/hc/en-us/articles/360014774799-Integration-via-Maven "Start.io Integration via Maven"
[2]: https://support.start.io/hc/en-us/articles/360006662474-Advanced-Usage "Start.io Advanced Usage"
[3]: https://github.com/StartApp-SDK/android-sdk-demo/tree/eca959faed7f67c7e33547332bab8366753e71b1/app-compose "Official Start.io Compose demo at reviewed commit"
[4]: https://github.com/StartApp-SDK/android-sdk-demo/blob/eca959faed7f67c7e33547332bab8366753e71b1/app-compose/src/main/kotlin/com/startapp/demo/compose/MainActivity.kt "Official Compose demo MainActivity.kt"
[5]: https://github.com/StartApp-SDK/android-sdk-demo/blob/eca959faed7f67c7e33547332bab8366753e71b1/app-compose/src/main/kotlin/com/startapp/demo/compose/NativeAdLayout.kt "Official Compose demo NativeAdLayout.kt"
[6]: https://github.com/StartApp-SDK/android-sdk-demo/blob/eca959faed7f67c7e33547332bab8366753e71b1/app-compose/build.gradle.kts "Official Compose demo build.gradle.kts"
