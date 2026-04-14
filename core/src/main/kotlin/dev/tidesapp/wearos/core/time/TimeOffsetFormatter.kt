package dev.tidesapp.wearos.core.time

import java.time.Clock
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Formats the pair of time-related values the v2 home feed endpoints require:
 *
 *  - `refreshId`: an integer epoch-millis timestamp that a client reuses for every
 *    home-related call in the same refresh cycle (.docs/03-home-feed.md §2.1).
 *  - `timeOffset`: the device's current UTC offset as `±HH:MM` (e.g. `-04:00`,
 *    `+05:30`, `+00:00`). UTC is emitted as `+00:00`.
 *
 * Pure JVM — no Android imports, zero static state. Both [Clock] and [ZoneId] are
 * constructor-injected via Hilt (see `core.di.TimeModule`) so tests can pin them.
 */
@Singleton
class TimeOffsetFormatter @Inject constructor(
    private val clock: Clock,
    private val zoneId: ZoneId,
) {

    /** Current epoch-millis from the injected [Clock]. Matches what the phone app sends. */
    fun refreshId(): Long = clock.millis()

    /**
     * Current UTC offset formatted as `±HH:MM`. Always includes the sign; hours and
     * minutes are zero-padded; UTC is `+00:00`.
     */
    fun timeOffset(): String {
        val offset: ZoneOffset = zoneId.rules.getOffset(clock.instant())
        val totalSeconds = offset.totalSeconds
        val sign = if (totalSeconds < 0) "-" else "+"
        val abs = kotlin.math.abs(totalSeconds)
        val hours = abs / 3600
        val minutes = (abs % 3600) / 60
        return "%s%02d:%02d".format(sign, hours, minutes)
    }
}
