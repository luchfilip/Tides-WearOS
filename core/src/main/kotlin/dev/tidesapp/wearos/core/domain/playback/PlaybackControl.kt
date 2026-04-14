package dev.tidesapp.wearos.core.domain.playback

import dev.tidesapp.wearos.core.domain.model.TrackItem

/**
 * App-facing playback control surface. Narrow by design (YAGNI):
 * transport controls live on `PlayerRepository`; this interface is only
 * concerned with *replacing* the queue and starting playback.
 */
interface PlaybackControl {
    /**
     * Replace the queue with [tracks] and start playback at [startIndex].
     *
     * Playback URLs are resolved per track on-demand by the session-side
     * `MediaSession.Callback.onAddMediaItems` hook — callers only need the
     * already-loaded [TrackItem] metadata and do not issue any network calls.
     *
     * [startIndex] is coerced into `[0, tracks.lastIndex]`. Passing an empty
     * list returns a failed [Result].
     */
    suspend fun playTracks(tracks: List<TrackItem>, startIndex: Int = 0): Result<Unit>
}
