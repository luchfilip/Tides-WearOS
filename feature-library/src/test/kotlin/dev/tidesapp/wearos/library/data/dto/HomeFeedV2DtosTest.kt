package dev.tidesapp.wearos.library.data.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deserialization round-trip against the real-shaped fixture
 * `src/test/resources/home_v2_feed_static.json`. Uses the same `Json` configuration
 * as `core.NetworkModule.provideJson`.
 */
class HomeFeedV2DtosTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun loadFixture(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("home_v2_feed_static.json")) {
            "fixture home_v2_feed_static.json not found on test classpath"
        }.bufferedReader().use { it.readText() }

    @Test
    fun `parses fixture without throwing and roundtrips top-level fields`() {
        val response = json.decodeFromString<HomeFeedV2ResponseDto>(loadFixture())

        assertEquals("b72f0972-fffc-491c-9d1a-d538ee872eb6", response.uuid)
        assertEquals("UE9QVUxBUl9QTEFZTElTVFM=", response.page.cursor)
        val vibes = response.header?.vibes?.items
        assertNotNull(vibes)
        assertEquals(3, vibes!!.size)
        assertEquals("For you", vibes[0].name)
        assertEquals("STATIC", vibes[0].type)
        assertEquals("EDITORIAL", vibes[1].type)
        assertEquals("UPLOADS", vibes[2].type)
    }

    @Test
    fun `parses modules envelope fields`() {
        val response = json.decodeFromString<HomeFeedV2ResponseDto>(loadFixture())
        val first = response.items.first()

        assertEquals("SHORTCUT_LIST", first.type)
        assertEquals("SHORTCUT_LIST", first.moduleId)
        assertEquals("Shortcuts", first.title)
        assertNull(first.subtitle)
        assertTrue(first.icons.isEmpty())
        assertNull(first.viewAll)
        assertEquals(3, first.items.size)
    }

    @Test
    fun `inner item data is parsed as raw JsonElement`() {
        val response = json.decodeFromString<HomeFeedV2ResponseDto>(loadFixture())
        val shortcuts = response.items.first()
        val deepLink = shortcuts.items.first()

        assertEquals("DEEP_LINK", deepLink.type)
        // Stays a raw JsonElement — mapper will decode it when it knows the type.
        assertNotNull(deepLink.data)
    }

    @Test
    fun `header is nullable for pagination continuation pages`() {
        val continuation = """
            {
              "uuid": "continuation",
              "page": { "cursor": null },
              "header": null,
              "items": []
            }
        """.trimIndent()

        val response = json.decodeFromString<HomeFeedV2ResponseDto>(continuation)
        assertNull(response.header)
        assertNull(response.page.cursor)
        assertTrue(response.items.isEmpty())
    }

    @Test
    fun `missing fields fall back to defaults`() {
        // Absolute minimum the server could return — every field defaulted.
        val minimal = "{}"
        val response = json.decodeFromString<HomeFeedV2ResponseDto>(minimal)

        assertEquals("", response.uuid)
        assertNull(response.page.cursor)
        assertNull(response.header)
        assertTrue(response.items.isEmpty())
    }

    @Test
    fun `unknown fields on envelopes are silently ignored`() {
        val withUnknowns = """
            {
              "uuid": "u",
              "page": { "cursor": "c", "totalPages": 42 },
              "header": { "vibes": { "items": [] }, "newThing": true },
              "items": [
                {
                  "type": "GRID_CARD",
                  "moduleId": "M",
                  "title": "t",
                  "unknownModuleField": "x",
                  "header": { "anchor": "ignored" },
                  "items": []
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<HomeFeedV2ResponseDto>(withUnknowns)
        assertEquals("u", response.uuid)
        assertEquals("c", response.page.cursor)
        assertEquals(1, response.items.size)
        assertEquals("M", response.items.first().moduleId)
    }
}
