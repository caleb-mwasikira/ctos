package com.ctos.client

import kotlinx.coroutines.channels.Channel

/** What a toast is about: sets its icon and color. */
enum class ToastKind { Info, Success, Error }

/** One popup message. */
data class Toast(
    val message: String,
    val kind: ToastKind = ToastKind.Info,
)

/**
 * A queue rather than a single slot: a toast arriving while another is showing waits its turn
 * instead of replacing it. ToastHost drains it, one at a time.
 */
object ToastManager {
    val toasts = Channel<Toast>(Channel.UNLIMITED)

    fun show(message: String) {
        toasts.trySend(Toast(message))
    }

    fun success(message: String) {
        toasts.trySend(Toast(message, ToastKind.Success))
    }

    fun error(message: String) {
        toasts.trySend(Toast(message, ToastKind.Error))
    }
}
