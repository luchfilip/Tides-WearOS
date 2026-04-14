package dev.tidesapp.wearos.library.domain.mapper

import dev.tidesapp.wearos.core.domain.model.HomeFeedItem
import dev.tidesapp.wearos.core.domain.model.HomeFeedSection
import dev.tidesapp.wearos.library.data.dto.HomeFeedV2ResponseDto
import dev.tidesapp.wearos.library.data.dto.ViewAllResponseDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Semantic tests for [HomeFeedV2Mapper] driven by the real-shaped fixture in
 * `src/test/resources/home_v2_feed_static.json`. Every acceptance rule in TIDES-M2B
 * is covered by at least one named test here.
 */
class HomeFeedV2MapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private lateinit var mapper: HomeFeedV2Mapper

    @Before
    fun setUp() {
        mapper = HomeFeedV2Mapper(json)
    }

    private fun loadResponse(): HomeFeedV2ResponseDto {
        val raw = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("home_v2_feed_static.json"),
        ) { "fixture home_v2_feed_static.json not found on test classpath" }
            .bufferedReader().use { it.readText() }
        return json.decodeFromString(raw)
    }

    private fun mapAll(): List<HomeFeedSection> = mapper.map(loadResponse())

    private fun sectionByTitle(title: String): HomeFeedSection? =
        mapAll().firstOrNull { it.title == title }

    // ---------- module-type coverage -----------------------------------------------------------

    @Test
    fun `SHORTCUT_LIST drops DEEP_LINK and keeps PLAYLIST plus MIX`() {
        val section = sectionByTitle("Shortcuts")
        assertNotNull(section)
        // 3 entries in, DEEP_LINK dropped → 2 survive: playlist + mix.
        assertEquals(2, section!!.items.size)
        val playlist = section.items[0] as HomeFeedItem.Playlist
        assertEquals("6cb7b0cb-5701-4fcd-b51a-b127abfffe2e", playlist.id)
        assertEquals("Ben Böhmer", playlist.title)
        // creator.type = USER, creator.name = Patricio → preferred over promotedArtists fallback.
        assertEquals("Patricio", playlist.creator)
        // squareImage is preferred over image.
        assertEquals(
            "https://resources.tidal.com/images/eec0b56b/9b3b/4b22/9b38/1fcb9fc99f8e/320x320.jpg",
            playlist.imageUrl,
        )
        val mix = section.items[1] as HomeFeedItem.Mix
        assertEquals("00155914c3b5c1a6d3a83a27b4f941", mix.id)
        assertEquals("What Else Is There?", mix.title)
        assertEquals("Röyksopp", mix.subTitle)
        // SMALL preferred over MEDIUM.
        assertEquals("https://resources.tidal.com/mix/small.jpg", mix.imageUrl)
    }

    @Test
    fun `GRID_CARD maps playlists and uses editorial creator fallback`() {
        val section = sectionByTitle("Popular playlists")
        assertNotNull(section)
        // Fixture has 7 entries, MAX_ITEMS_PER_SECTION = 5 → truncated.
        assertEquals(5, section!!.items.size)
        val first = section.items.first() as HomeFeedItem.Playlist
        assertEquals("aaaaaaaa-0000-0000-0000-000000000001", first.id)
        // creator.name is null (TIDAL curator) → falls back to promoted artist name.
        assertEquals("Promoted Artist", first.creator)

        val second = section.items[1] as HomeFeedItem.Playlist
        // creator.name null AND promotedArtists empty → "TIDAL" default.
        assertEquals("TIDAL", second.creator)
    }

    @Test
    fun `COMPACT_GRID_CARD drops TRACK and keeps MIX`() {
        val section = sectionByTitle("Continue listening")
        assertNotNull(section)
        assertEquals(1, section!!.items.size)
        val mix = section.items.first() as HomeFeedItem.Mix
        assertEquals("001daily0000000000000000000001", mix.id)
        assertEquals("Daily Mix 1", mix.title)
        assertEquals("For you", mix.subTitle)
        // Only MEDIUM present → that's what we get.
        assertEquals("https://resources.tidal.com/mix/daily-medium.jpg", mix.imageUrl)
    }

    @Test
    fun `GRID_CARD_WITH_CONTEXT ignores header anchor and maps carousel items`() {
        val section = sectionByTitle("Because you listened to Sample Album")
        assertNotNull(section)
        assertEquals(2, section!!.items.size)
        val album = section.items.first() as HomeFeedItem.Album
        assertEquals("111111111", album.id)
        assertEquals("Sample Album", album.title)
        // artists[].main = true preferred over the featured guest.
        assertEquals("Sample Artist", album.artistName)
        assertEquals(
            "https://resources.tidal.com/images/52fc589d/48f9/46c1/acca/e7e0e6adaed6/320x320.jpg",
            album.imageUrl,
        )

        val second = section.items[1] as HomeFeedItem.Album
        // No main=true artist → falls back to first artist.
        assertEquals("Other Artist", second.artistName)
    }

    // ---------- drop rules ---------------------------------------------------------------------

    @Test
    fun `ARTIST_LIST module is dropped entirely`() {
        assertNull(sectionByTitle("Your favorite artists"))
    }

    @Test
    fun `unknown module type is dropped without throwing`() {
        assertNull(sectionByTitle("Something we do not know about"))
    }

    @Test
    fun `modules with blank title are dropped`() {
        val sections = mapAll()
        assertTrue(sections.none { it.title.isBlank() })
    }

    @Test
    fun `module whose items are all unsupported kinds yields no section`() {
        assertNull(sectionByTitle("All unsupported inside"))
    }

    @Test
    fun `sections are truncated to MAX_ITEMS_PER_SECTION`() {
        val section = sectionByTitle("Popular playlists")
        assertNotNull(section)
        assertEquals(5, section!!.items.size)
        assertFalse(
            "6th fixture item should have been dropped by truncation",
            section.items.any { it.id == "aaaaaaaa-0000-0000-0000-000000000006" },
        )
    }

    // ---------- structural -------------------------------------------------------------------

    @Test
    fun `only four sections survive from the fixture`() {
        // Shortcuts, Popular playlists, Continue listening, Because you listened to Sample Album.
        assertEquals(4, mapAll().size)
    }

    @Test
    fun `empty response yields empty list`() {
        val empty = HomeFeedV2ResponseDto()
        assertTrue(mapper.map(empty).isEmpty())
    }

    // ---------- viewAllPath propagation (TIDES-M3) ---------------------------------------------

    @Test
    fun `map populates HomeFeedSection viewAllPath when module carries viewAll`() {
        val popular = sectionByTitle("Popular playlists")
        assertNotNull(popular)
        assertEquals(
            "home/pages/POPULAR_PLAYLISTS/view-all",
            popular!!.viewAllPath,
        )
        // Sanity: the BECAUSE_YOU_LISTENED_TO_ALBUM module's viewAll has a query segment and
        // should survive intact — the repository prepends v2/ and Retrofit merges params.
        val because = sectionByTitle("Because you listened to Sample Album")
        assertEquals(
            "home/pages/BECAUSE_YOU_LISTENED_TO_ALBUM/view-all?itemId=abc",
            because?.viewAllPath,
        )
    }

    @Test
    fun `map leaves HomeFeedSection viewAllPath null when module has no viewAll`() {
        val shortcuts = sectionByTitle("Shortcuts")
        assertNotNull(shortcuts)
        assertEquals(null, shortcuts!!.viewAllPath)
    }

    // ---------- mapViewAll ---------------------------------------------------------------------

    @Test
    fun `mapViewAll decodes every supported item kind and does not truncate`() {
        // 8 items (more than MAX_ITEMS_PER_SECTION = 5 in home feed) to prove no truncation
        // happens on the view-all path.
        val raw = """
            {
              "title": "Popular playlists",
              "subtitle": "Editor's picks",
              "itemLayout": "GRID_CARD",
              "items": [
                { "type": "PLAYLIST", "data": {
                    "uuid": "p-1", "type": "EDITORIAL",
                    "creator": { "id": 0, "name": null, "picture": null, "type": "TIDAL" },
                    "title": "Pl 1", "image": null, "squareImage": null,
                    "promotedArtists": [{ "id": 1, "name": "Promoted", "picture": null, "type": "ARTIST", "main": true }]
                } },
                { "type": "PLAYLIST", "data": {
                    "uuid": "p-2", "type": "EDITORIAL",
                    "creator": { "id": 0, "name": "Curator", "picture": null, "type": "USER" },
                    "title": "Pl 2", "image": null, "squareImage": null, "promotedArtists": []
                } },
                { "type": "PLAYLIST", "data": {
                    "uuid": "p-3", "type": "EDITORIAL",
                    "creator": { "id": 0, "name": "Curator", "picture": null, "type": "USER" },
                    "title": "Pl 3", "image": null, "squareImage": null, "promotedArtists": []
                } },
                { "type": "PLAYLIST", "data": {
                    "uuid": "p-4", "type": "EDITORIAL",
                    "creator": { "id": 0, "name": "Curator", "picture": null, "type": "USER" },
                    "title": "Pl 4", "image": null, "squareImage": null, "promotedArtists": []
                } },
                { "type": "PLAYLIST", "data": {
                    "uuid": "p-5", "type": "EDITORIAL",
                    "creator": { "id": 0, "name": "Curator", "picture": null, "type": "USER" },
                    "title": "Pl 5", "image": null, "squareImage": null, "promotedArtists": []
                } },
                { "type": "PLAYLIST", "data": {
                    "uuid": "p-6", "type": "EDITORIAL",
                    "creator": { "id": 0, "name": "Curator", "picture": null, "type": "USER" },
                    "title": "Pl 6", "image": null, "squareImage": null, "promotedArtists": []
                } },
                { "type": "PLAYLIST", "data": {
                    "uuid": "p-7", "type": "EDITORIAL",
                    "creator": { "id": 0, "name": "Curator", "picture": null, "type": "USER" },
                    "title": "Pl 7", "image": null, "squareImage": null, "promotedArtists": []
                } },
                { "type": "PLAYLIST", "data": {
                    "uuid": "p-8", "type": "EDITORIAL",
                    "creator": { "id": 0, "name": "Curator", "picture": null, "type": "USER" },
                    "title": "Pl 8", "image": null, "squareImage": null, "promotedArtists": []
                } }
              ]
            }
        """.trimIndent()
        val response = json.decodeFromString<ViewAllResponseDto>(raw)
        val page = mapper.mapViewAll(response)

        assertEquals("Popular playlists", page.title)
        assertEquals("Editor's picks", page.subtitle)
        assertEquals(8, page.items.size)
        assertTrue(page.items.all { it is HomeFeedItem.Playlist })
    }

    @Test
    fun `mapViewAll drops unknown item kinds same as map`() {
        val raw = """
            {
              "title": "Mixed",
              "subtitle": null,
              "itemLayout": "GRID_CARD",
              "items": [
                { "type": "ARTIST",   "data": { "id": 1, "name": "A" } },
                { "type": "TRACK",    "data": { "id": 2, "title": "T" } },
                { "type": "DEEP_LINK","data": { "title": "L", "id": "x", "url": "u" } },
                { "type": "PLAYLIST", "data": {
                    "uuid": "p-keep", "type": "EDITORIAL",
                    "creator": { "id": 0, "name": "K", "picture": null, "type": "USER" },
                    "title": "Kept", "image": null, "squareImage": null, "promotedArtists": []
                } }
              ]
            }
        """.trimIndent()
        val response = json.decodeFromString<ViewAllResponseDto>(raw)
        val page = mapper.mapViewAll(response)

        assertEquals(1, page.items.size)
        val pl = page.items.single() as HomeFeedItem.Playlist
        assertEquals("p-keep", pl.id)
    }
}
