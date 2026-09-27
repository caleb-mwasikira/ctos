package com.ctos.client.network

import kotlinx.coroutines.channels.Channel

/**
 * Raised when the API answers 401 (Unauthenticated) to a request that carried the saved login
 * token: the token was revoked (a password reset signs every device out), expired, or was issued
 * by a different server than the one the app is now talking to. The app listens and sends the
 * user back to the login screen instead of leaving it showing errors.
 */
object SessionEvents {
    // Conflated: a burst of failing requests must produce one sign-out, not many.
    val expired = Channel<Unit>(Channel.CONFLATED)

    fun notifyExpired() {
        expired.trySend(Unit)
    }
}
