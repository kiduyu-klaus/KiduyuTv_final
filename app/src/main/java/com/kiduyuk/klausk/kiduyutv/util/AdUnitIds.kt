package com.kiduyuk.klausk.kiduyutv.util

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object AdUnitIds {

    private const val TAG = "AdUnitIds"
    private const val CONFIG_PATH = "app_config/google_ads_Configuration"

    // ── Phone Production ───────────────────────────────────────────────────

    private const val DEFAULT_PHONE_ADAPTIVE_BANNER = "ca-app-pub-3803477439180910/7183108212"
    private const val DEFAULT_PHONE_INTERSTITIAL_UNIT = "ca-app-pub-3803477439180910/5295324788"
    private const val DEFAULT_PHONE_REWARDED_UNIT = "ca-app-pub-3803477439180910/3982243116"
    private const val DEFAULT_PHONE_REWARDED_INTERSTITIAL_UNIT = "ca-app-pub-3803477439180910/1751398697"
    private const val DEFAULT_PHONE_APP_OPEN_UNIT = "ca-app-pub-3803477439180910/9694976435"
    private const val DEFAULT_PHONE_NATIVE_UNIT = "ca-app-pub-3803477439180910/5035465131"

    // ── Google Sample Test IDs ─────────────────────────────────────────────

    private const val DEFAULT_TEST_ADAPTIVE_BANNER = "ca-app-pub-3940256099942544/9214589741"
    private const val DEFAULT_TEST_INTERSTITIAL_UNIT = "ca-app-pub-3940256099942544/1033173712"
    private const val DEFAULT_TEST_REWARDED_UNIT = "ca-app-pub-3940256099942544/5224354917"
    private const val DEFAULT_TEST_REWARDED_INTERSTITIAL_UNIT = "ca-app-pub-3940256099942544/5354046379"
    private const val DEFAULT_TEST_APP_OPEN_UNIT = "ca-app-pub-3940256099942544/9257395921"
    private const val DEFAULT_TEST_NATIVE_UNIT = "ca-app-pub-3940256099942544/2247696110"

    private val defaultPhoneProduction = DeviceAdUnitIds(
        banner = DEFAULT_PHONE_ADAPTIVE_BANNER,
        interstitial = DEFAULT_PHONE_INTERSTITIAL_UNIT,
        rewarded = DEFAULT_PHONE_REWARDED_UNIT,
        rewardedInterstitial = DEFAULT_PHONE_REWARDED_INTERSTITIAL_UNIT,
        appOpen = DEFAULT_PHONE_APP_OPEN_UNIT,
        native = DEFAULT_PHONE_NATIVE_UNIT
    )

    private val defaultTest = DeviceAdUnitIds(
        banner = DEFAULT_TEST_ADAPTIVE_BANNER,
        interstitial = DEFAULT_TEST_INTERSTITIAL_UNIT,
        rewarded = DEFAULT_TEST_REWARDED_UNIT,
        rewardedInterstitial = DEFAULT_TEST_REWARDED_INTERSTITIAL_UNIT,
        appOpen = DEFAULT_TEST_APP_OPEN_UNIT,
        native = DEFAULT_TEST_NATIVE_UNIT
    )

    private val defaultConfig = GoogleAdsConfig(
        useTestAds = false,
        phoneProduction = defaultPhoneProduction,
        phoneTest = defaultTest,
        tvProduction = defaultTest,
        tvTest = defaultTest
    )

    @Volatile
    private var activeConfig = defaultConfig

    @Volatile
    private var firebaseListener: ValueEventListener? = null

    // ── Phone (AdMob) ─────────────────────────────────────────────────────

    val PHONE_BANNER: String get() = activeConfig.phoneIds.banner
    val PHONE_INTERSTITIAL: String get() = activeConfig.phoneIds.interstitial
    val PHONE_REWARDED: String get() = activeConfig.phoneIds.rewarded
    val PHONE_REWARDED_INTERSTITIAL: String get() = activeConfig.phoneIds.rewardedInterstitial
    val PHONE_APP_OPEN: String get() = activeConfig.phoneIds.appOpen
    val PHONE_NATIVE: String get() = activeConfig.phoneIds.native

    // ── TV (AdMob) ────────────────────────────────────────────────────────

    val TV_BANNER: String get() = activeConfig.tvIds.banner
    val TV_INTERSTITIAL: String get() = activeConfig.tvIds.interstitial
    val TV_REWARDED: String get() = activeConfig.tvIds.rewarded
    val TV_REWARDED_INTERSTITIAL: String get() = activeConfig.tvIds.rewardedInterstitial
    val TV_APP_OPEN: String get() = activeConfig.tvIds.appOpen
    val TV_NATIVE: String get() = activeConfig.tvIds.native

    /**
     * Starts a realtime listener for app_config/google_ads_Configuration.
     * Local IDs remain the fallback until Firebase returns valid values.
     */
    fun startFirebaseSync() {
        if (firebaseListener != null) return

        val ref = FirebaseDatabase.getInstance().getReference(CONFIG_PATH)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                activeConfig = parseConfig(snapshot)
                Log.i(TAG, "Loaded Google Ads config from Firebase. useTestAds=${activeConfig.useTestAds}")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Failed to load Google Ads config from Firebase: ${error.message}")
            }
        }

        firebaseListener = listener
        ref.addValueEventListener(listener)
    }

    fun stopFirebaseSync() {
        val listener = firebaseListener ?: return
        FirebaseDatabase.getInstance()
            .getReference(CONFIG_PATH)
            .removeEventListener(listener)
        firebaseListener = null
    }

    private fun parseConfig(snapshot: DataSnapshot): GoogleAdsConfig {
        val useTestAds = snapshot.boolean("enable_test_ads") || snapshot.boolean("use_test_ads")

        return GoogleAdsConfig(
            useTestAds = useTestAds,
            phoneProduction = DeviceAdUnitIds(
                banner = snapshot.adUnitOrDefault(defaultPhoneProduction.banner, "PHONE_ADAPTIVE_BANNER", "PHONE_BANNER"),
                interstitial = snapshot.adUnitOrDefault(defaultPhoneProduction.interstitial, "PHONE_INTERSTITIAL_UNIT", "PHONE_INTERSTITIAL"),
                rewarded = snapshot.adUnitOrDefault(defaultPhoneProduction.rewarded, "PHONE_REWARDED_UNIT", "PHONE_REWARDED"),
                rewardedInterstitial = snapshot.adUnitOrDefault(defaultPhoneProduction.rewardedInterstitial, "PHONE_REWARDED_INTERSTITIAL_UNIT", "PHONE_REWARDED_INTERSTITIAL"),
                appOpen = snapshot.adUnitOrDefault(defaultPhoneProduction.appOpen, "PHONE_APP_OPEN_UNIT", "PHONE_APP_OPEN"),
                native = snapshot.adUnitOrDefault(defaultPhoneProduction.native, "PHONE_NATIVE_UNIT", "PHONE_NATIVE")
            ),
            phoneTest = DeviceAdUnitIds(
                banner = snapshot.adUnitOrDefault(defaultTest.banner, "TEST_PHONE_ADAPTIVE_BANNER", "TEST_PHONE_BANNER"),
                interstitial = snapshot.adUnitOrDefault(defaultTest.interstitial, "TEST_PHONE_INTERSTITIAL_UNIT", "TEST_PHONE_INTERSTITIAL"),
                rewarded = snapshot.adUnitOrDefault(defaultTest.rewarded, "TEST_PHONE_REWARDED_UNIT", "TEST_PHONE_REWARDED"),
                rewardedInterstitial = snapshot.adUnitOrDefault(defaultTest.rewardedInterstitial, "TEST_PHONE_REWARDED_INTERSTITIAL_UNIT", "TEST_PHONE_REWARDED_INTERSTITIAL"),
                appOpen = snapshot.adUnitOrDefault(defaultTest.appOpen, "TEST_PHONE_APP_OPEN_UNIT", "TEST_PHONE_APP_OPEN"),
                native = snapshot.adUnitOrDefault(defaultTest.native, "TEST_PHONE_NATIVE_UNIT", "TEST_PHONE_NATIVE")
            ),
            tvProduction = DeviceAdUnitIds(
                banner = snapshot.adUnitOrDefault(defaultConfig.tvProduction.banner, "TV_ADAPTIVE_BANNER", "TV_BANNER"),
                interstitial = snapshot.adUnitOrDefault(defaultConfig.tvProduction.interstitial, "TV_INTERSTITIAL_UNIT", "TV_INTERSTITIAL"),
                rewarded = snapshot.adUnitOrDefault(defaultConfig.tvProduction.rewarded, "TV_REWARDED_UNIT", "TV_REWARDED"),
                rewardedInterstitial = snapshot.adUnitOrDefault(defaultConfig.tvProduction.rewardedInterstitial, "TV_REWARDED_INTERSTITIAL_UNIT", "TV_REWARDED_INTERSTITIAL"),
                appOpen = snapshot.adUnitOrDefault(defaultConfig.tvProduction.appOpen, "TV_APP_OPEN_UNIT", "TV_APP_OPEN"),
                native = snapshot.adUnitOrDefault(defaultConfig.tvProduction.native, "TV_NATIVE_UNIT", "TV_NATIVE")
            ),
            tvTest = DeviceAdUnitIds(
                banner = snapshot.adUnitOrDefault(defaultTest.banner, "TEST_TV_ADAPTIVE_BANNER", "TEST_TV_BANNER"),
                interstitial = snapshot.adUnitOrDefault(defaultTest.interstitial, "TEST_TV_INTERSTITIAL_UNIT", "TEST_TV_INTERSTITIAL"),
                rewarded = snapshot.adUnitOrDefault(defaultTest.rewarded, "TEST_TV_REWARDED_UNIT", "TEST_TV_REWARDED"),
                rewardedInterstitial = snapshot.adUnitOrDefault(defaultTest.rewardedInterstitial, "TEST_TV_REWARDED_INTERSTITIAL_UNIT", "TEST_TV_REWARDED_INTERSTITIAL"),
                appOpen = snapshot.adUnitOrDefault(defaultTest.appOpen, "TEST_TV_APP_OPEN_UNIT", "TEST_TV_APP_OPEN"),
                native = snapshot.adUnitOrDefault(defaultTest.native, "TEST_TV_NATIVE_UNIT", "TEST_TV_NATIVE")
            )
        )
    }

    private fun DataSnapshot.boolean(key: String): Boolean {
        return when (val value = child(key).value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            is Number -> value.toInt() != 0
            else -> false
        }
    }

    private fun DataSnapshot.adUnitOrDefault(defaultValue: String, vararg keys: String): String {
        val remoteValue = keys.asSequence()
            .mapNotNull { key -> child(key).getValue(String::class.java)?.trim() }
            .firstOrNull { it.isValidAdUnitId() }

        return remoteValue ?: defaultValue
    }

    private fun String.isValidAdUnitId(): Boolean {
        return matches(Regex("^ca-app-pub-\\d{16}/\\d+$"))
    }

    private val GoogleAdsConfig.phoneIds: DeviceAdUnitIds
        get() = if (useTestAds) phoneTest else phoneProduction

    private val GoogleAdsConfig.tvIds: DeviceAdUnitIds
        get() = if (useTestAds) tvTest else tvProduction

    private data class GoogleAdsConfig(
        val useTestAds: Boolean,
        val phoneProduction: DeviceAdUnitIds,
        val phoneTest: DeviceAdUnitIds,
        val tvProduction: DeviceAdUnitIds,
        val tvTest: DeviceAdUnitIds
    )

    private data class DeviceAdUnitIds(
        val banner: String,
        val interstitial: String,
        val rewarded: String,
        val rewardedInterstitial: String,
        val appOpen: String,
        val native: String
    )
}
