package com.ctos.client.data

import com.ctos.client.network.WsEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * Keeps a WebSocket open to the API while somebody is signed in, and hands over what it says.
 * A message is only a nudge ("department 7 changed"), so a missed one is harmless: the app
 * refreshes everything each time the connection is made again.
 */
object DepartmentEvents {
    private const val FIRST_WAIT_MILLIS = 1_000L
    private const val LONGEST_WAIT_MILLIS = 30_000L

    /**
     * Runs until cancelled. If the connection cannot be made or drops, it tries again after a
     * pause that doubles each time (up to 30 seconds), so a server that is down is not hammered.
     */
    suspend fun run(
        isSignedIn: () -> Boolean,
        listen: suspend (onOpen: () -> Unit, onEvent: (WsEvent) -> Unit) -> Unit,
        onConnected: () -> Unit,
        onEvent: (WsEvent) -> Unit,
    ) {
        var wait = FIRST_WAIT_MILLIS
        while (true) {
            if (!isSignedIn()) {
                delay(FIRST_WAIT_MILLIS)
                continue
            }
            try {
                listen({ wait = FIRST_WAIT_MILLIS; onConnected() }, onEvent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("DepartmentEvents: connection failed: ${e.message}")
            }
            delay(wait)
            wait = min(wait * 2, LONGEST_WAIT_MILLIS)
        }
    }
}
