package com.kiduyuk.klausk.kiduyutv.util

/**
 * Created by Kiduyu Klaus on 3/30/2026.
 */


class DomainTrie {
    
    private val root = TrieNode()
    private var domainCount = 0
    
    fun insert(domain: String) {
        if (domain.isEmpty() || domain.length > 255) return
        
        var node = root
        // Handle wildcards
        val parts = if (domain.startsWith("*.")) {
            domain.substring(2).split(".").reversed()
        } else {
            domain.split(".").reversed()
        }
        
        for ((index, part) in parts.withIndex()) {
            if (part.isEmpty()) continue
            
            // Limit depth to prevent memory issues
            if (index > 20) break
            
            node = node.children.getOrPut(part) { TrieNode() }
        }
        
        if (!node.isEnd) {
            node.isEnd = true
            domainCount++
        }
    }
    
    fun matches(url: String): Boolean {
        val host = try {
            val uri = java.net.URI(url)
            uri.host ?: return false
        } catch (e: Exception) {
            // Try to extract host manually if URI parsing fails
            val match = Regex("://([^/]+)").find(url)
            match?.groupValues?.get(1) ?: return false
        }
        
        val parts = host.split(".").reversed()
        
        // Check exact match
        if (checkMatch(parts)) return true
        
        // Check parent domains
        for (i in 1 until parts.size) {
            val subParts = parts.slice(i until parts.size)
            if (checkMatch(subParts)) return true
        }
        
        return false
    }
    
    private fun checkMatch(parts: List<String>): Boolean {
        var node = root
        
        for (part in parts) {
            node = node.children[part] ?: return false
            if (node.isEnd) return true
        }
        
        return node.isEnd
    }
    
    fun size(): Int = domainCount
    
    fun getAllDomains(): List<String> {
        val domains = mutableListOf<String>()
        collectDomains(root, mutableListOf(), domains)
        return domains
    }
    
    private fun collectDomains(node: TrieNode, current: MutableList<String>, result: MutableList<String>) {
        if (node.isEnd) {
            result.add(current.reversed().joinToString("."))
        }
        
        for ((part, child) in node.children) {
            current.add(part)
            collectDomains(child, current, result)
            current.removeAt(current.size - 1)
        }
    }
    
    private class TrieNode {
        val children = LinkedHashMap<String, TrieNode>()
        var isEnd = false
    }
}