package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Hybrid Ad Blocker:
 * - EasyList (cosmetic + domains)
 * - DNS rules (domains + whitelist)
 */
object AdvancedAdBlocker {

    private val blockTrie = DomainTrie()
    private val whitelistTrie = DomainTrie()
    private val cssSelectors = mutableListOf<String>()

    private val decisionCache = mutableMapOf<String, Boolean>()
    private val seenDomains = mutableSetOf<String>()

    private val suspiciousKeywords = listOf(
        "ads", "banner", "popup", "tracker", "doubleclick"
    )

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return

        loadFile(context, "easylist.txt", isEasyList = true)
        loadFile(context, "dns.txt", isEasyList = false)

        isInitialized = true
    }

    private fun loadFile(context: Context, fileName: String, isEasyList: Boolean) {
        val reader = BufferedReader(
            InputStreamReader(context.assets.open(fileName))
        )

        reader.forEachLine { line ->
            val rule = line.trim()

            when {
                rule.isEmpty() || rule.startsWith("!") -> {
                    // Ignore comments
                }

                // ✅ Whitelist rules
                rule.startsWith("@@||") -> {
                    val domain = rule
                        .removePrefix("@@||")
                        .substringBefore("^")

                    if (domain.isNotEmpty()) {
                        whitelistTrie.insert(domain)
                    }
                }

                // ✅ Blocking rules
                rule.startsWith("||") -> {
                    val domain = rule
                        .removePrefix("||")
                        .substringBefore("^")

                    if (domain.isNotEmpty() && seenDomains.add(domain)) {
                        blockTrie.insert(domain)
                    }
                }

                // ✅ CSS selectors (EasyList only)
                isEasyList && rule.startsWith("##") -> {
                    val selector = rule.removePrefix("##")
                    if (selector.isNotEmpty()) {
                        cssSelectors.add(selector)
                    }
                }
            }
        }
    }

    fun shouldBlock(url: String): Boolean {
        decisionCache[url]?.let { return it }

        val host = try {
            Uri.parse(url).host
        } catch (e: Exception) {
            null
        } ?: return false

        val result = when {
            // 1. Whitelist ALWAYS wins
            whitelistTrie.matches(host) -> false

            // 2. Block rules
            blockTrie.matches(host) -> true

            // 3. Heuristic fallback
            isSuspicious(url) -> true

            else -> false
        }

        decisionCache[url] = result

        if (result) {
            Log.d("AdBlocker", "Blocked: $host")
        }

        return result
    }

    private fun isSuspicious(url: String): Boolean {
        val lower = url.lowercase()
        return suspiciousKeywords.any { lower.contains(it) } &&
                (lower.contains("click") ||
                 lower.contains("track") ||
                 lower.contains("redirect"))
    }

    /**
     * Inject CSS to hide elements (EasyList cosmetic filtering)
     */
    fun getCss(): String {
        if (cssSelectors.isEmpty()) return ""

        val css = cssSelectors
            .take(150) // prevent huge injection
            .joinToString(", ")

        return """
            (function() {
                try {
                    var style = document.createElement('style');
                    style.innerHTML = '$css { display:none !important; }';
                    document.head.appendChild(style);
                } catch (e) {}
            })();
        """.trimIndent()
    }
}