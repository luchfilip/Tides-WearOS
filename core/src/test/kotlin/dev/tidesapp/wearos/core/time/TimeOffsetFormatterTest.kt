package dev.tidesapp.wearos.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class TimeOffsetFormatterTest {

    // 2026-04-14T12:34:56Z — DST is active in New York so expected offset is -04:00.
    private val fixedInstant: Instant = Instant.parse("2026-04-14T12:34:56Z")
    private val fixedClock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    @Test
    fun `timeOffset formats negative offsets with minus sign during NY DST`() {
        val formatter = TimeOffsetFormatter(fixedClock, ZoneId.of("America/New_York"))
        assertEquals("-04:00", formatter.timeOffset())
    }

    @Test
    fun `timeOffset formats half-hour positive offsets with plus sign`() {
        val formatter = TimeOffsetFormatter(fixedClock, ZoneId.of("Asia/Kolkata"))
        assertEquals("+05:30", formatter.timeOffset())
    }

    @Test
    fun `timeOffset formats UTC as plus zero`() {
        val formatter = TimeOffsetFormatter(fixedClock, ZoneOffset.UTC)
        assertEquals("+00:00", formatter.timeOffset())
    }

    @Test
    fun `refreshId returns clock millis`() {
        val formatter = TimeOffsetFormatter(fixedClock, ZoneOffset.UTC)
        assertEquals(fixedInstant.toEpochMilli(), formatter.refreshId())
    }
}
