package com.musicedge.visualizer.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-way channel from the engine (listener service) to the settings UI, both of
 * which live in the same process. Holds no Context and no framework object, so the
 * Activity can observe it without keeping the service alive.
 */
object VisualizerStatus {

    data class Snapshot(
        val state: VisualizerState = VisualizerState.DISABLED,
        val listenerConnected: Boolean = false,
        val appLabel: String? = null,
        val title: String? = null,
        val artist: String? = null,
    )

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    internal fun update(transform: (Snapshot) -> Snapshot) {
        _snapshot.value = transform(_snapshot.value)
    }

    internal fun reset() {
        _snapshot.value = Snapshot()
    }
}
