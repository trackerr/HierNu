package nl.hiertoen.app.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import nl.hiertoen.app.data.local.TestFixtures
import nl.hiertoen.app.data.local.entity.CachePolicy
import nl.hiertoen.app.data.local.entity.PhotoCandidateEntity
import nl.hiertoen.app.data.local.entity.TrackPointValidity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** org.json vereist Robolectric in unit tests, zie WikimediaHttpClientParseTest. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GeoJsonExporterTest {

    @Test
    fun `levert een geldige FeatureCollection met LineString per segment`() {
        val trip = TestFixtures.trip()
        val points = listOf(
            TestFixtures.trackPoint(id = "p1", timestamp = 1_000L),
            TestFixtures.trackPoint(id = "p2", timestamp = 2_000L),
        )

        val geoJson = JSONObject(GeoJsonExporter.export(trip, points, emptyList()))

        assertEquals("FeatureCollection", geoJson.getString("type"))
        val features = geoJson.getJSONArray("features")
        assertEquals(1, features.length())
        val feature = features.getJSONObject(0)
        assertEquals("Feature", feature.getString("type"))
        val geometry = feature.getJSONObject("geometry")
        assertEquals("LineString", geometry.getString("type"))
        assertEquals(2, geometry.getJSONArray("coordinates").length())
        // GeoJSON: [lon, lat], niet [lat, lon] — RFC 7946.
        val firstCoord = geometry.getJSONArray("coordinates").getJSONArray(0)
        assertEquals(points[0].lon, firstCoord.getDouble(0), 0.0001)
        assertEquals(points[0].lat, firstCoord.getDouble(1), 0.0001)
    }

    @Test
    fun `een segment met één punt levert geen LineString op`() {
        val trip = TestFixtures.trip()
        val geoJson = JSONObject(GeoJsonExporter.export(trip, listOf(TestFixtures.trackPoint(id = "p1")), emptyList()))
        assertEquals(0, geoJson.getJSONArray("features").length())
    }

    @Test
    fun `momenten worden Point-features met de §10_4-eigenschappen`() {
        val trip = TestFixtures.trip()
        val moment = TestFixtures.tripMoment(id = "m1", timestamp = 2_000L)
        val candidate = PhotoCandidateEntity(
            id = "c1",
            momentId = "m1",
            provider = "wikimedia",
            providerId = "123",
            imageUrl = "https://example.org/full.jpg",
            sourcePageUrl = "https://commons.wikimedia.org/wiki/File:Test.jpg",
            thumbUrl = "https://example.org/thumb.jpg",
            title = "File:Test.jpg",
            description = null,
            yearFrom = 1936,
            yearTo = 1936,
            author = "Jane Doe",
            license = "CC BY-SA 4.0",
            attribution = "Jane Doe — CC BY-SA 4.0 (Wikimedia Commons)",
            lat = 52.2215,
            lon = 6.8937,
            headingDeg = null,
            distanceM = 5.0,
            score = 0.8,
            cachePolicy = CachePolicy.ALLOWED,
        )

        val geoJson = JSONObject(
            GeoJsonExporter.export(trip, emptyList(), listOf(moment), mapOf("m1" to candidate)),
        )

        val feature = geoJson.getJSONArray("features").getJSONObject(0)
        assertEquals("Point", feature.getJSONObject("geometry").getString("type"))
        val props = feature.getJSONObject("properties")
        assertEquals(trip.id, props.getString("tripId"))
        assertEquals("MANUAL_BOOKMARK", props.getString("type"))
        assertEquals("wikimedia", props.getString("imageProvider"))
        assertEquals(1936, props.getInt("imageYear"))
        assertEquals(candidate.sourcePageUrl, props.getString("sourceUrl"))
        assertTrue(props.getString("timestamp").isNotBlank())
    }

    @Test
    fun `alleen VALID-punten tellen mee in de route`() {
        val trip = TestFixtures.trip()
        val points = listOf(
            TestFixtures.trackPoint(id = "p1", timestamp = 1_000L, validity = TrackPointValidity.VALID),
            TestFixtures.trackPoint(id = "p2", timestamp = 2_000L, validity = TrackPointValidity.VALID),
            TestFixtures.trackPoint(id = "p3", timestamp = 3_000L, validity = TrackPointValidity.IMPLAUSIBLE_JUMP),
        )

        val geoJson = JSONObject(GeoJsonExporter.export(trip, points, emptyList()))
        val coords = geoJson.getJSONArray("features").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(2, coords.length())
    }
}
