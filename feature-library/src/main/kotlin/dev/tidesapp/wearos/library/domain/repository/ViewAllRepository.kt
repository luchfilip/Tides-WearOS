package dev.tidesapp.wearos.library.domain.repository

import dev.tidesapp.wearos.core.domain.model.ViewAllPage

/**
 * Repository abstraction for the generic "See all" endpoint driven by the opaque
 * `viewAll` path carried on home-feed modules. See `.docs/03-home-feed.md §4`.
 *
 * Intentionally uncached — every nav into a view-all screen fetches fresh. The
 * server returns a single page (`limit=50`); no cursor pagination is exposed.
 */
interface ViewAllRepository {
    suspend fun getViewAll(viewAllPath: String): Result<ViewAllPage>
}
