package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.util.Log
import java.io.*

object AdvancedAdBlocker {
    
    private const val TAG = "AdvancedAdBlocker"
    private val domainTrie = DomainTrie()
    private val cssSelectors = mutableListOf<String>()
    private val regexRules = mutableListOf<Regex>()
    
    private val suspiciousPatterns = listOf(
        Regex("""([./])(ad|ads|advert|banner|popup|sponsor|track)(s)?[./\d]""", RegexOption.IGNORE_CASE),
        Regex("""[?&](ad|adid|adunit|bannerid|campaign|sponsored|tracking)[=_]""", RegexOption.IGNORE_CASE),
        Regex("""(doubleclick|googleadservices|googlesyndication|adservice)""", RegexOption.IGNORE_CASE)
    )
    
    private var isInitialized = false
    
    /**
     * Initialize with filters from internal storage and assets
     */
    fun init(context: Context) {
        if (isInitialized) return
        
        try {
            // Try loading from internal storage first (updated versions)
            loadFiltersFromStorage(context)
            
            // If still no rules loaded, fallback to assets
            if (!hasRulesLoaded()) {
                loadFiltersFromAssets(context)
            }
            
            // Add hardcoded rules as final fallback
            addBuiltInRules()
            
            isInitialized = true
            Log.i(TAG, "Initialized: ${domainTrie.size()} domains, ${cssSelectors.size} CSS rules, ${regexRules.size} regex rules")
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed: ${e.message}", e)
        }
    }
    
    private fun hasRulesLoaded(): Boolean {
        return domainTrie.size() > 0 || cssSelectors.isNotEmpty()
    }
    
    /**
     * Load filters from internal storage
     */
    private fun loadFiltersFromStorage(context: Context) {
        val filterFiles = listOf("easylist.txt", "easyprivacy.txt", "custom_filters.txt")
        
        filterFiles.forEach { filename ->
            val file = File(context.filesDir, filename)
            if (file.exists()) {
                try {
                    val ruleCount = parseFilterFile(file)
                    Log.d(TAG, "Loaded $ruleCount rules from storage: $filename")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load $filename from storage: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Load filters from assets
     */
    private fun loadFiltersFromAssets(context: Context) {
        val filterFiles = listOf("easylist.txt", "easyprivacy.txt", "custom_filters.txt")
        
        filterFiles.forEach { filename ->
            try {
                context.assets.open(filename).use { inputStream ->
                    val ruleCount = parseFilterStream(inputStream)
                    Log.d(TAG, "Loaded $ruleCount rules from assets: $filename")
                }
            } catch (e: IOException) {
                Log.w(TAG, "Asset $filename not found: ${e.message}")
            }
        }
    }
    
    /**
     * Parse a filter file and extract rules
     */
    private fun parseFilterFile(file: File): Int {
        return BufferedReader(FileReader(file)).use { reader ->
            parseFilterLines(reader.lineSequence())
        }
    }
    
    /**
     * Parse a filter stream and extract rules
     */
    private fun parseFilterStream(inputStream: InputStream): Int {
        return BufferedReader(InputStreamReader(inputStream)).use { reader ->
            parseFilterLines(reader.lineSequence())
        }
    }
    
    /**
     * Parse filter lines and return count of rules added
     */
    private fun parseFilterLines(lines: Sequence<String>): Int {
        var ruleCount = 0
        
        lines.forEach { line ->
            val rule = line.trim()
            
            // Skip comments and empty lines
            if (rule.isEmpty() || rule.startsWith("!") || rule.startsWith("[")) {
                return@forEach
            }
            
            // Skip whitelist rules for now
            if (rule.startsWith("@@")) {
                return@forEach
            }
            
            when {
                // Domain blocking rules (e.g., ||example.com^)
                rule.startsWith("||") -> {
                    val domain = rule
                        .removePrefix("||")
                        .substringBefore("^")
                        .substringBefore("/")
                        .substringBefore("$")
                        .trim()
                    
                    if (domain.isNotEmpty() && !domain.startsWith("*") && domain.contains(".")) {
                        domainTrie.insert(domain)
                        ruleCount++
                    }
                }
                
                // Element hiding rules (e.g., ##.ad-banner)
                rule.startsWith("##") -> {
                    val selector = rule.removePrefix("##").trim()
                    if (selector.isNotEmpty()) {
                        cssSelectors.add(selector)
                        ruleCount++
                    }
                }
                
                // Extended CSS rules (e.g., #?#.ad-container)
                rule.contains("#?#") -> {
                    val selector = rule.substringAfter("#?#").trim()
                    if (selector.isNotEmpty()) {
                        cssSelectors.add(selector)
                        ruleCount++
                    }
                }
                
                // Regex/path rules
                rule.startsWith("/") && rule.endsWith("/") -> {
                    try {
                        val pattern = rule.substring(1, rule.length - 1)
                        regexRules.add(Regex(pattern))
                        ruleCount++
                    } catch (e: Exception) {
                        // Invalid regex
                    }
                }
                
                // Simple domain rules (just a domain name)
                !rule.contains("*") && !rule.contains("^") && !rule.contains(" ") && rule.contains(".") -> {
                    domainTrie.insert(rule)
                    ruleCount++
                }
            }
        }
        
        return ruleCount
    }
    
    /**
     * Add built-in fallback rules
     */
    private fun addBuiltInRules() {
        // Add essential ad domains
        val essentialDomains = listOf(
            "doubleclick.net", "googleadservices.com", "googlesyndication.com",
            "pagead2.googlesyndication.com", "adservice.google.com",
            "amazon-adsystem.com", "criteo.com", "outbrain.com", "taboola.com"
        )
        essentialDomains.forEach { domainTrie.insert(it) }
        
        // Add essential CSS selectors
        val essentialSelectors = listOf(
            "[class*='ad-']", "[id*='ad-']", "[class*='banner']", "[id*='banner']",
            "[aria-label*='advertisement']", "[data-ad]", "[data-advertisement]",
            ".video-ads", ".ytp-ad-module"
        )
        cssSelectors.addAll(essentialSelectors)
    }
    
    /**
     * Check if a URL should be blocked
     */
    fun shouldBlock(url: String): Boolean {
        if (url.isEmpty() || !url.startsWith("http")) return false
        
        // Check domain trie (fastest)
        if (domainTrie.matches(url)) {
            Log.d(TAG, "Blocked by domain: $url")
            return true
        }
        
        // Check regex patterns
        if (regexRules.any { it.containsMatchIn(url) }) {
            Log.d(TAG, "Blocked by regex: $url")
            return true
        }
        
        // Check suspicious patterns
        if (suspiciousPatterns.any { it.containsMatchIn(url) }) {
            Log.d(TAG, "Blocked by pattern: $url")
            return true
        }
        
        return false
    }
    
    /**
     * Get CSS for ad hiding
     */
    fun getCss(): String {
        if (cssSelectors.isEmpty()) return ""
        
        val selectors = cssSelectors.take(1000) // Limit to prevent too large injection
            .joinToString(", ") { it }
        
        return """
        (function() {
            try {
                var style = document.createElement('style');
                style.id = 'adblocker-css';
                style.textContent = '$selectors { display:none !important; visibility:hidden !important; opacity:0 !important; height:0 !important; width:0 !important; position:absolute !important; top:-9999px !important; }';
                
                var existingStyle = document.getElementById('adblocker-css');
                if (existingStyle) existingStyle.remove();
                
                document.head.appendChild(style);
            } catch(e) {
                console.error('AdBlocker CSS error:', e);
            }
        })();
        """.trimIndent()
    }
    
    /**
     * Get JavaScript for additional ad blocking
     */
    fun getBlockingJavaScript(): String {
        return """
        (function() {
            // Block popups
            window.open = function() { return null; };
            
            // Remove ad elements periodically
            setInterval(function() {
                try {
                    var adSelectors = ['[class*="ad"]', '[id*="ad"]', '[class*="banner"]', 'iframe[src*="doubleclick"]'];
                    adSelectors.forEach(function(selector) {
                        var elements = document.querySelectorAll(selector);
                        elements.forEach(function(el) { 
                            if (el.offsetHeight < 50 || el.offsetWidth < 50) return;
                            el.remove(); 
                        });
                    });
                } catch(e) {}
            }, 2000);
        })();
        """.trimIndent()
    }
    
    fun isInitialized(): Boolean = isInitialized
}