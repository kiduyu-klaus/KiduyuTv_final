package com.kiduyuk.klausk.kiduyutv.util

/**
 * Created by Kiduyu Klaus on 3/30/2026.
 */


class DomainTrie {

    private val root = TrieNode()

    fun insert(domain: String) {
        var node = root
        val parts = domain.split(".").reversed()

        for (part in parts) {
            node = node.children.getOrPut(part) { TrieNode() }
        }
        node.isEnd = true
    }

    fun matches(url: String): Boolean {
        val host = try {
            java.net.URI(url).host ?: return false
        } catch (e: Exception) {
            return false
        }

        val parts = host.split(".").reversed()
        var node = root

        for (part in parts) {
            node = node.children[part] ?: return false
            if (node.isEnd) return true
        }
        return false
    }

    private class TrieNode {
        val children = HashMap<String, TrieNode>()
        var isEnd = false
    }
}