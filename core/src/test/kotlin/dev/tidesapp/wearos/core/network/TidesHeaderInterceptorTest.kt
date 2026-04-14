package dev.tidesapp.wearos.core.network

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TidesHeaderInterceptorTest {

    private val clientVersion = "2.187.0"
    private val interceptor = TidesHeaderInterceptor(clientVersion)

    @Test
    fun `intercept adds UA, client version, and Accept-Encoding headers`() {
        val chain = StubChain(
            Request.Builder()
                .url("https://api.tidal.com/v2/home/feed/STATIC")
                .build()
        )

        interceptor.intercept(chain)

        val sent = chain.lastRequest()
        assertEquals("TIDAL_ANDROID_TV/2.187.0 null", sent.header("User-Agent"))
        assertEquals("2.187.0", sent.header("x-tidal-client-version"))
        assertEquals("gzip", sent.header("Accept-Encoding"))
    }

    @Test
    fun `intercept preserves existing Authorization header`() {
        val chain = StubChain(
            Request.Builder()
                .url("https://api.tidal.com/v2/home/feed/STATIC")
                .header("Authorization", "Bearer eyJraWQiOiJ0ZXN0In0.payload.sig")
                .build()
        )

        interceptor.intercept(chain)

        val sent = chain.lastRequest()
        assertEquals("Bearer eyJraWQiOiJ0ZXN0In0.payload.sig", sent.header("Authorization"))
        assertNotNull(sent.header("User-Agent"))
    }

    @Test
    fun `intercept does not duplicate headers on re-entry`() {
        val original = Request.Builder()
            .url("https://api.tidal.com/v2/home/feed/STATIC")
            .build()

        val first = StubChain(original)
        interceptor.intercept(first)
        val afterFirst = first.lastRequest()

        // Simulate the request being passed through the interceptor a second time
        // (e.g. retry or nested chain). Each header should appear exactly once.
        val second = StubChain(afterFirst)
        interceptor.intercept(second)
        val afterSecond = second.lastRequest()

        assertEquals(1, afterSecond.headers("User-Agent").size)
        assertEquals(1, afterSecond.headers("x-tidal-client-version").size)
        assertEquals(1, afterSecond.headers("Accept-Encoding").size)
        assertEquals("TIDAL_ANDROID_TV/2.187.0 null", afterSecond.header("User-Agent"))
    }

    @Test
    fun `intercept uses injected client version`() {
        val customInterceptor = TidesHeaderInterceptor("9.9.9")
        val chain = StubChain(
            Request.Builder().url("https://api.tidal.com/v2/ping").build()
        )

        customInterceptor.intercept(chain)

        val sent = chain.lastRequest()
        assertEquals("TIDAL_ANDROID_TV/9.9.9 null", sent.header("User-Agent"))
        assertEquals("9.9.9", sent.header("x-tidal-client-version"))
    }

    /**
     * Minimal stub [Interceptor.Chain] — no Robolectric, no mockk needed.
     * Records the request passed to [proceed] and returns a canned 200 response.
     */
    private class StubChain(private var request: Request) : Interceptor.Chain {
        fun lastRequest(): Request = request

        override fun request(): Request = request

        override fun proceed(request: Request): Response {
            this.request = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody(null))
                .build()
        }

        override fun connection() = null
        override fun call() = throw UnsupportedOperationException()
        override fun connectTimeoutMillis() = 0
        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun readTimeoutMillis() = 0
        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun writeTimeoutMillis() = 0
        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    }
}
