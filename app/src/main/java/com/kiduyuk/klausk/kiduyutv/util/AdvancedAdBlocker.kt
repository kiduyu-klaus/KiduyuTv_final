package com.kiduyuk.klausk.kiduyutv.util

/**
 * Created by Kiduyu Klaus on 3/30/2026.
 */


import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

object AdvancedAdBlocker {

    private val domainTrie = DomainTrie()
    private val cssSelectors = mutableListOf<String>()

    private val suspiciousKeywords = listOf(
        "ads", "ad", "banner", "popup", "redirect", "tracker"
    )

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return

        val reader = BufferedReader(
            InputStreamReader(context.assets.open("easylist.txt"))
        )

        reader.forEachLine { line ->
            val rule = line.trim()

            when {
                rule.startsWith("||") -> {
                    val domain = rule
                        .removePrefix("||")
                        .substringBefore("^")
                    if (domain.isNotEmpty()) {
                        domainTrie.insert(domain)
                    }
                }

                rule.startsWith("##") -> {
                    val selector = rule.removePrefix("##")
                    if (selector.isNotEmpty()) {
                        cssSelectors.add(selector)
                    }
                }
            }
        }

        isInitialized = true
    }

    fun shouldBlock(url: String): Boolean {
        if (domainTrie.matches(url)) return true
        if (isSuspicious(url)) return true
        return false
    }

    private fun isSuspicious(url: String): Boolean {
        val lower = url.lowercase()
        return suspiciousKeywords.any { lower.contains(it) } &&
                (lower.contains("click") ||
                        lower.contains("track") ||
                        lower.contains("pop") ||
                        lower.contains("redirect"))
    }

    fun getCss(): String {
        if (cssSelectors.isEmpty()) return ""

        val css = cssSelectors.joinToString(", ")
        return """
            (function() {
                var style = document.createElement('style');
                style.innerHTML = '$css { display:none !important; }';
                document.head.appendChild(style);
            })();
        """.trimIndent()
    }
}