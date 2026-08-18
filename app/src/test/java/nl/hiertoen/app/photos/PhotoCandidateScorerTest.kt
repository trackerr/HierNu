package nl.hiertoen.app.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCandidateScorerTest {

    private val queryLat = 52.2215
    private val queryLon = 6.8937

    private fun context(radiusM: Double = 500.0, headingDeg: Float? = null, preferOldest: Boolean = true) = PhotoCandidateScorer.Context(
        queryLat = queryLat,
        queryLon = queryLon,
        queryHeadingDeg = headingDeg,
        searchRadiusM = radiusM,
        currentYear = 2026,
        preferOldest = preferOldest,
    )

    private fun candidate(
        lat: Double = queryLat,
        lon: Double = queryLon,
        rawDate: String? = null,
        license: String? = "CC BY-SA 4.0",
        author: String? = "Jane Doe",
        headingDeg: Float? = null,
        widthPx: Int? = 3000,
        heightPx: Int? = 2000,
    ) = WikimediaCandidate(
        pageId = "1",
        title = "File:Test.jpg",
        imageUrl = "https://example.org/test.jpg",
        sourcePageUrl = "https://commons.wikimedia.org/wiki/File:Test.jpg",
        thumbUrl = "https://example.org/thumb.jpg",
        lat = lat,
        lon = lon,
        rawDate = rawDate,
        license = license,
        author = author,
        description = null,
        widthPx = widthPx,
        heightPx = heightPx,
        headingDeg = headingDeg,
    )

    @Test
    fun `foto exact op de locatie uit 1936 wint van een oudere foto 400m verderop`() {
        // §7.4: "Een foto van exact de locatie uit 1936 krijgt doorgaans voorrang boven een
        // opname uit 1910 op 400 meter afstand."
        val exactRecent = PhotoCandidateScorer.score(
            candidate(lat = queryLat, lon = queryLon, rawDate = "1936"),
            context(),
        )
        val farOlder = PhotoCandidateScorer.score(
            candidate(lat = queryLat + 0.0036, lon = queryLon, rawDate = "1910"), // ~400m noordelijker
            context(),
        )

        assertTrue(exactRecent.score > farOlder.score)
    }

    @Test
    fun `ontbrekend jaartal wordt neutraal gescoord, niet afgewezen`() {
        val withYear = PhotoCandidateScorer.score(candidate(rawDate = "1936"), context())
        val withoutYear = PhotoCandidateScorer.score(candidate(rawDate = null), context())

        assertTrue(withoutYear.score > 0.0)
        assertTrue(withYear.score >= withoutYear.score)
    }

    @Test
    fun `ontbrekende camerakoers is neutraal, niet afwijzend`() {
        val scored = PhotoCandidateScorer.score(
            candidate(headingDeg = null),
            context(headingDeg = 90f),
        )
        assertTrue(scored.score > 0.0)
    }

    @Test
    fun `dichterbij scoort hoger dan verder weg, bij verder gelijke kandidaten`() {
        val close = PhotoCandidateScorer.score(candidate(lat = queryLat, lon = queryLon), context())
        val far = PhotoCandidateScorer.score(candidate(lat = queryLat + 0.003, lon = queryLon), context())

        assertTrue(close.score > far.score)
    }

    @Test
    fun `bron zonder licentie is niet bruikbaar`() {
        val scored = PhotoCandidateScorer.score(candidate(license = null), context())
        assertFalse(scored.hasUsableLicense)
    }

    @Test
    fun `bron met licentie is bruikbaar`() {
        val scored = PhotoCandidateScorer.score(candidate(license = "Public domain"), context())
        assertTrue(scored.hasUsableLicense)
    }

    @Test
    fun `jaartal wordt overgenomen van de ruwe datumtekst`() {
        val scored = PhotoCandidateScorer.score(candidate(rawDate = "circa 1928"), context())
        assertEquals(1928, scored.year)
    }

    @Test
    fun `preferOldest UIT negeert het jaartal, dichterbij wint puur op afstand`() {
        // Zonder de voorkeur voor oud zou een recentere foto exact op de locatie moeten winnen
        // van een oudere foto verderop, puur omdat proximity het volle gewicht krijgt.
        val exactRecent = PhotoCandidateScorer.score(
            candidate(lat = queryLat, lon = queryLon, rawDate = "2020"),
            context(preferOldest = false),
        )
        val farOlder = PhotoCandidateScorer.score(
            candidate(lat = queryLat + 0.0036, lon = queryLon, rawDate = "1900"),
            context(preferOldest = false),
        )

        assertTrue(exactRecent.score > farOlder.score)
    }
}
