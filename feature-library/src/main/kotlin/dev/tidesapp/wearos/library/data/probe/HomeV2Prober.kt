package dev.tidesapp.wearos.library.data.probe

import com.tidal.sdk.auth.CredentialsProvider
import dev.tidesapp.wearos.library.data.api.ProbeApi
import okhttp3.ResponseBody
import retrofit2.Response
import java.time.Clock
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raw result of a single probe request. Intentionally JVM-friendly (no Android types)
 * so [HomeV2Prober] is fully unit-testable.
 *
 * Ref: .docs/mobile-api-migration.md §4.2
 */
data class ProbeResult(
    val endpoint: String,
    val status: Int,
    val tidalHeaders: Map<String, String>,
    val bodyPreview: String,
    val error: String? = null,
)

/**
 * Throwaway probe runner for the v2 home feed migration (TIDES-M2A).
 *
 * Fires the three probes documented in `.docs/mobile-api-migration.md §4.2` and
 * returns structured [ProbeResult]s. Per-probe exceptions are captured into the
 * [ProbeResult.error] field so one failure does not abort the remaining probes.
 *
 * This class is deliberately pure Kotlin: no `android.util.Log`, no Android framework
 * imports. The caller (ViewModel/Screen) is responsible for surfacing results.
 *
 * Delete in TIDES-M2E after the home v2 migration completes.
 */
@Singleton
class HomeV2Prober @Inject constructor(
    private val probeApi: ProbeApi,
    private val credentialsProvider: CredentialsProvider,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

    /**
     * Runs all three probes against api.tidal.com using the currently logged-in user's
     * bearer token. If credentials cannot be obtained, returns a single synthetic
     * "no credentials" result and bails.
     *
     * @param playlistUuid optional playlist uuid for probe #3; if null, probe #3 is skipped.
     */
    suspend fun runAll(playlistUuid: String? = null): List<ProbeResult> {
        val token = obtainBearerToken()
            ?: return listOf(
                ProbeResult(
                    endpoint = "all",
                    status = 0,
                    tidalHeaders = emptyMap(),
                    bodyPreview = "",
                    error = "no credentials",
                ),
            )

        val refreshId = clock.millis()
        val timeOffset = currentTimeOffset()

        val results = mutableListOf<ProbeResult>()

        results += runProbe(endpoint = ENDPOINT_HOME_FEED) {
            probeApi.probeHomeV2(
                token = token,
                refreshId = refreshId,
                timeOffset = timeOffset,
                limit = 20,
            )
        }

        results += runProbe(endpoint = ENDPOINT_VIEW_ALL) {
            probeApi.probeViewAll(
                token = token,
                section = "POPULAR_PLAYLISTS",
                refreshId = refreshId,
                timeOffset = timeOffset,
                offset = 0,
                limit = 50,
            )
        }

        if (playlistUuid != null) {
            results += runProbe(endpoint = "$ENDPOINT_USER_PLAYLIST/$playlistUuid") {
                probeApi.probeUserPlaylist(token = token, uuid = playlistUuid)
            }
        }

        return results
    }

    private suspend fun obtainBearerToken(): String? {
        return try {
            val raw = credentialsProvider.getCredentials(null).successData?.token
                ?: return null
            "Bearer $raw"
        } catch (t: Throwable) {
            null
        }
    }

    private suspend inline fun runProbe(
        endpoint: String,
        crossinline call: suspend () -> Response<ResponseBody>,
    ): ProbeResult {
        return try {
            val response = call()
            ProbeResult(
                endpoint = endpoint,
                status = response.code(),
                tidalHeaders = response.tidalHeaders(),
                bodyPreview = response.body()?.string()?.take(BODY_PREVIEW_MAX) ?: "",
            )
        } catch (t: Throwable) {
            ProbeResult(
                endpoint = endpoint,
                status = 0,
                tidalHeaders = emptyMap(),
                bodyPreview = "",
                error = t.javaClass.simpleName + ": " + (t.message ?: "unknown"),
            )
        }
    }

    /**
     * Returns the device's current UTC offset formatted as `±HH:MM`
     * (e.g. `-05:00`, `+02:00`). Matches the format the phone app sends in the
     * `timeOffset` query param — see `.docs/03-home-feed.md §2.1`.
     * Retrofit will URL-encode the colon when emitting the query string.
     */
    internal fun currentTimeOffset(): String {
        val offset: ZoneOffset = zoneId.rules.getOffset(clock.instant())
        val totalSeconds = offset.totalSeconds
        val sign = if (totalSeconds < 0) "-" else "+"
        val abs = kotlin.math.abs(totalSeconds)
        val hours = abs / 3600
        val minutes = (abs % 3600) / 60
        return "%s%02d:%02d".format(sign, hours, minutes)
    }

    private fun Response<ResponseBody>.tidalHeaders(): Map<String, String> {
        return headers().toMultimap()
            .asSequence()
            .filter { (name, _) -> name.lowercase().startsWith("tidal-") }
            .associate { (name, values) -> name to values.joinToString(",") }
    }

    private companion object {
        const val BODY_PREVIEW_MAX = 2048
        const val ENDPOINT_HOME_FEED = "v2/home/feed/STATIC"
        const val ENDPOINT_VIEW_ALL = "v2/home/pages/POPULAR_PLAYLISTS/view-all"
        const val ENDPOINT_USER_PLAYLIST = "v2/user-playlists"
    }
}
