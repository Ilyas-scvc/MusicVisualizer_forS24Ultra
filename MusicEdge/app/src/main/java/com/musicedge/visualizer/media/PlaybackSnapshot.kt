package com.musicedge.visualizer.media

/**
 * What the media layer knows right now. [packageName] is the owner of the media
 * session, never "the app currently on screen".
 */
data class PlaybackSnapshot(
    val packageName: String?,
    val appLabel: String?,
    val isPlaying: Boolean,
    val title: String?,
    val artist: String?,
) {
    companion object {
        val NONE = PlaybackSnapshot(
            packageName = null,
            appLabel = null,
            isPlaying = false,
            title = null,
            artist = null,
        )
    }
}
