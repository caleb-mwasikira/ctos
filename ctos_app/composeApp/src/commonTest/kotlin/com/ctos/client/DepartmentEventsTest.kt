package com.ctos.client

import com.ctos.client.data.DepartmentEvents
import com.ctos.client.network.WsEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DepartmentEventsTest {
    @Test
    fun itDoesNothingWhileNobodyIsSignedIn() = runTest {
        var attempts = 0
        val job = backgroundScope.launch {
            DepartmentEvents.run(
                isSignedIn = { false },
                listen = { _, _ -> attempts++ },
                onConnected = {},
                onEvent = {},
            )
        }

        advanceTimeBy(10_000)

        assertEquals(0, attempts)
        job.cancel()
    }

    @Test
    fun itDeliversWhatTheServerSaysAndReportsTheConnection() = runTest {
        val received = mutableListOf<WsEvent>()
        var connected = 0
        val hold = CompletableDeferred<Unit>()
        backgroundScope.launch {
            DepartmentEvents.run(
                isSignedIn = { true },
                listen = { onOpen, onEvent ->
                    onOpen()
                    onEvent(WsEvent("department.changed"))
                    hold.await()
                },
                onConnected = { connected++ },
                onEvent = { received += it },
            )
        }

        advanceTimeBy(100)

        assertEquals(1, connected)
        assertEquals(listOf("department.changed"), received.map { it.type })
    }

    @Test
    fun itReconnectsWithAPauseThatDoublesUpToThirtySeconds() = runTest {
        val attemptTimes = mutableListOf<Long>()
        backgroundScope.launch {
            DepartmentEvents.run(
                isSignedIn = { true },
                listen = { _, _ ->
                    attemptTimes += currentTime
                    error("the server is down")
                },
                onConnected = {},
                onEvent = {},
            )
        }

        advanceTimeBy(200_000)

        // 0, +1s, +2s, +4s, +8s, +16s, then 30s each time (the longest pause)
        assertEquals(listOf(0L, 1_000L, 3_000L, 7_000L, 15_000L, 31_000L, 61_000L, 91_000L), attemptTimes.take(8))
        assertTrue(attemptTimes.zipWithNext { a, b -> b - a }.all { it <= 30_000 })
    }

    @Test
    fun aSuccessfulConnectionStartsThePausesOverAgain() = runTest {
        val attemptTimes = mutableListOf<Long>()
        var failuresLeft = 2
        backgroundScope.launch {
            DepartmentEvents.run(
                isSignedIn = { true },
                listen = { onOpen, _ ->
                    attemptTimes += currentTime
                    if (failuresLeft-- > 0) error("down")
                    onOpen()
                    error("dropped after connecting")
                },
                onConnected = {},
                onEvent = {},
            )
        }

        advanceTimeBy(10_000)

        // two failures (pauses 1s then 2s), a success that drops, then the pause is 1s again
        assertEquals(listOf(0L, 1_000L, 3_000L, 4_000L), attemptTimes.take(4))
    }
}
