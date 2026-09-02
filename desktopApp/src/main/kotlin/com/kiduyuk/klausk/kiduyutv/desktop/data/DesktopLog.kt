package com.kiduyuk.klausk.kiduyutv.desktop.data

import org.slf4j.LoggerFactory

/** Centralized desktop diagnostics for troubleshooting playback and runtime issues. */
object DesktopLog {
    val logger = LoggerFactory.getLogger("KiduyuTV")
}

fun String.logSafe(maxLength: Int = 500): String =
    replace(Regex("(?i)(token|authorization|cookie)=[^&\\s]+"), "$1=<redacted>")
        .take(maxLength)
