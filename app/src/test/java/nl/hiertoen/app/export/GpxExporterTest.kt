package nl.hiertoen.app.export

import nl.hiertoen.app.data.local.TestFixtures
import nl.hiertoen.app.data.local.entity.TrackPointValidity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class GpxExporterTest {

    private fun parse(gpx: String): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(gpx.toByteArray()))

    @Test
    fun `produceert welgevormde XML met een trkpt per geldig punt`() {
        val trip = TestFixtures.trip()
        val points = listOf(
            TestFixtures.trackPoint(id = "p1", timestamp = 1_000L, validity = TrackPointValidity.VALID),
            TestFixtures.trackPoint(id = "p2", timestamp = 2_000L, validity = TrackPointValidity.VALID),
            TestFixtures.trackPoint(id = "p3", timestamp = 3_000L, validity = TrackPointValidity.LOW_ACCURACY),
            TestFixtures.trackPoint(id = "p4", timestamp = 4_000L, validity = TrackPointValidity.IMPLAUSIBLE_JUMP),
        )

        val gpx = GpxExporter.export(trip, points, emptyList())
        val doc = parse(gpx) // gooit een SAXException bij niet-welgevormde XML

        assertEquals(2, doc.getElementsByTagName("trkpt").length)
    }

    @Test
    fun `splitst segmenten in aparte trkseg-elementen`() {
        val trip = TestFixtures.trip()
        val points = listOf(
            TestFixtures.trackPoint(id = "p1", timestamp = 1_000L).copy(segmentIndex = 0),
            TestFixtures.trackPoint(id = "p2", timestamp = 2_000L).copy(segmentIndex = 0),
            TestFixtures.trackPoint(id = "p3", timestamp = 3_000L).copy(segmentIndex = 1),
        )

        val doc = parse(GpxExporter.export(trip, points, emptyList()))

        assertEquals(2, doc.getElementsByTagName("trkseg").length)
    }

    @Test
    fun `bevat een wpt per moment`() {
        val trip = TestFixtures.trip()
        val moments = listOf(TestFixtures.tripMoment(id = "m1"), TestFixtures.tripMoment(id = "m2", timestamp = 2_000L))

        val doc = parse(GpxExporter.export(trip, emptyList(), moments))

        assertEquals(2, doc.getElementsByTagName("wpt").length)
    }

    @Test
    fun `escaped speciale tekens in de ritnaam blijven welgevormd`() {
        val trip = TestFixtures.trip().copy(name = "Rit langs de A1 & terug <thuis>")

        val gpx = GpxExporter.export(trip, emptyList(), emptyList())
        val doc = parse(gpx)

        val nameNode = doc.getElementsByTagName("name").item(0)
        assertEquals("Rit langs de A1 & terug <thuis>", nameNode.textContent)
        assertTrue(gpx.contains("&amp;"))
    }

    @Test
    fun `lege rit levert nog steeds valide GPX op`() {
        val doc = parse(GpxExporter.export(TestFixtures.trip(), emptyList(), emptyList()))
        assertEquals(0, doc.getElementsByTagName("trkpt").length)
        assertEquals(1, doc.getElementsByTagName("trk").length)
    }
}
