package com.kiduyuk.klausk.kiduyutv.util

object AdUnitIds {
    private const val PHONE_ADAPTIVE_BANNER = "ca-app-pub-3803477439180910/7183108212"
    private const val PHONE_INTERSTITIAL_UNIT = "ca-app-pub-3803477439180910/5295324788"
    private const val PHONE_REWARDED_UNIT = "ca-app-pub-3803477439180910/3982243116"
    private const val PHONE_REWARDED_INTERSTITIAL_UNIT = "ca-app-pub-3803477439180910/1751398697"
    private const val PHONE_APP_OPEN_UNIT = "ca-app-pub-3803477439180910/9694976435"
    private const val PHONE_NATIVE_UNIT = "ca-app-pub-3803477439180910/5035465131"

    // Google AdMob sample IDs kept for TV until production TV units are provided.
    private const val TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
    private const val TEST_ADAPTIVE_BANNER = "ca-app-pub-3940256099942544/9214589741"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_REWARDED_INTERSTITIAL = "ca-app-pub-3940256099942544/5354046379"
    private const val TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110"

    // ── Phone (AdMob) ─────────────────────────────────────────────────────

    val PHONE_BANNER: String get() = PHONE_ADAPTIVE_BANNER

    val PHONE_INTERSTITIAL: String get() = PHONE_INTERSTITIAL_UNIT

    val PHONE_REWARDED: String get() = PHONE_REWARDED_UNIT

    val PHONE_REWARDED_INTERSTITIAL: String get() = PHONE_REWARDED_INTERSTITIAL_UNIT

    val PHONE_APP_OPEN: String get() = PHONE_APP_OPEN_UNIT

    val PHONE_NATIVE: String get() = PHONE_NATIVE_UNIT

    // ── TV (AdMob) ────────────────────────────────────────────────────────

    val TV_BANNER: String get() = TEST_ADAPTIVE_BANNER

    val TV_INTERSTITIAL: String get() = TEST_INTERSTITIAL

    val TV_REWARDED_INTERSTITIAL: String get() = TEST_REWARDED_INTERSTITIAL

    val TV_APP_OPEN: String get() = TEST_APP_OPEN

    val TV_NATIVE: String get() = TEST_NATIVE
}
