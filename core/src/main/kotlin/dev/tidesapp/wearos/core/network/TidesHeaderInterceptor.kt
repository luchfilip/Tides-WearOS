package dev.tidesapp.wearos.core.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Named

/**
 * Injects TIDAL client identity headers on every outgoing request.
 *
 * - `User-Agent: TIDAL_ANDROID_TV/<version> null` — matches TIDAL's actual UA pattern.
 *   The trailing ` null` is intentional and mirrors what the reference phone build emits.
 * - `x-tidal-client-version: <version>` — expected by server-side telemetry to correlate
 *   with the TV cid family.
 * - `Accept-Encoding: gzip` — OkHttp adds this transparently, but the phone app sends it
 *   explicitly; we match that so captured traffic looks identical.
 *
 * Must be registered AFTER the query-params interceptor and BEFORE the logging
 * interceptor in [dev.tidesapp.wearos.core.di.NetworkModule.provideOkHttpClient].
 */
class TidesHeaderInterceptor @Inject constructor(
    @Named("clientVersion") private val clientVersion: String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", "TIDAL_ANDROID_TV/$clientVersion null")
            .header("x-tidal-client-version", clientVersion)
            .header("Accept-Encoding", "gzip")
            .build()
        return chain.proceed(request)
    }
}
