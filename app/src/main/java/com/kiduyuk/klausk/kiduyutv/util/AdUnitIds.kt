package com.kiduyuk.klausk.kiduyutv.util

import com.kiduyuk.klausk.kiduyutv.BuildConfig

object AdUnitIds {

    // Set to true during development to use Google test ad units
    private const val useTestIds = false

    // ── Phone (AdMob) ─────────────────────────────────────────────────────

    val PHONE_BANNER: String get() = if (useTestIds)
        "ca-app-pub-3940256099942544/6300978111"           // Google test banner
    else
        "ca-app-pub-3803477439180910/7183108212"           // AdMob banner unit

    val PHONE_INTERSTITIAL: String get() = if (useTestIds)
        "ca-app-pub-3940256099942544/1033173712"           // Google test interstitial
    else
        "ca-app-pub-3803477439180910/5295324788"           // AdMob interstitial unit

    val PHONE_REWARDED: String get() = if (useTestIds)
        "ca-app-pub-3940256099942544/5224354917"           // Google test rewarded
    else
        "ca-app-pub-3803477439180910/3982243116"           // AdMob rewarded unit

    // ── TV (Google Ad Manager) ────────────────────────────────────────────

    val TV_BANNER: String get() = if (useTestIds)
        "/6499/example/banner"                             // GAM test banner
    else
        "ca-app-pub-3803477439180910/7183108212"           // AdMob banner unit

    val TV_INTERSTITIAL: String get() = if (useTestIds)
        "/6499/example/interstitial"                       // GAM test interstitial
    else
        "ca-app-pub-3803477439180910/5295324788"           // AdMob interstitial unit
}
