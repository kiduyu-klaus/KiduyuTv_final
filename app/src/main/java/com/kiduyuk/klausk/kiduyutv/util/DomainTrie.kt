package com.kiduyuk.klausk.kiduyutv.util

/**
 * Efficient domain matcher using reversed trie
 * Supports subdomains:
 *   ads.google.com → matches google.com
 */
class DomainTrie {

    private val root = TrieNode()

    fun insert(domain: String) {
        val cleanDomain = domain
            .lowercase()
            .trim()
            .removeSuffix(".")

        if (cleanDomain.isEmpty()) return

        var node = root
        val parts = cleanDomain.split(".").reversed()

        for (part in parts) {
            node = node.children.getOrPut(part) { TrieNode() }
        }

        node.isEnd = true
    }

    /**
     * Match against HOST (not full URL)
     */
    fun matches(host: String): Boolean {
        val cleanHost = host
            .lowercase()
            .trim()
            .removeSuffix(".")

        if (cleanHost.isEmpty()) return false

        val parts = cleanHost.split(".").reversed()
        var node = root

        for (part in parts) {
            val next = node.children[part] ?: return false
            node = next

            if (node.isEnd) return true
        }

        return false
    }

    private class TrieNode {
        val children = HashMap<String, TrieNode>()
        var isEnd = false
    }
}