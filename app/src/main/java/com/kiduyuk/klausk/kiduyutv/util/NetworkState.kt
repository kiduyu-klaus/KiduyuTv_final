package com.kiduyuk.klausk.kiduyutv.util

/**
 * Represents the possible states of network connectivity.
 * Using a sealed class ensures exhaustive when expressions.
 */
sealed class NetworkState {
    
    /** Network is connected and internet is reachable */
    object Connected : NetworkState()
    
    /** No network interface is active (WiFi off, airplane mode, etc.) */
    object NotConnected : NetworkState()
    
    /** Network interface is active but internet is not reachable */
    object ConnectedNoInternet : NetworkState()
    
    /** Network state is being determined */
    object Unknown : NetworkState()
    
    /**
     * Returns true if the state represents a usable connection.
     */
    fun isConnected(): Boolean = this is Connected
    
    /**
     * Returns a user-friendly message for this state.
     */
    fun getMessage(): String = when (this) {
        is Connected -> "Connected to the internet"
        is NotConnected -> "No network connection"
        is ConnectedNoInternet -> "Connected but no internet access"
        is Unknown -> "Checking network..."
    }
    
    /**
     * Returns the dialog title for this state.
     */
    fun getTitle(): String = when (this) {
        is Connected -> "Connected"
        is NotConnected -> "No Network Connection"
        is ConnectedNoInternet -> "No Internet Access"
        is Unknown -> "Checking Connection"
    }
}
