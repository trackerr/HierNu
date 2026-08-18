package nl.hiertoen.app.photos

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** org.json vereist Robolectric in unit tests, zie WikimediaHttpClientParseTest. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StreetViewClientTest {
    private val client = GoogleStreetViewClient(apiKey = "test-key")

    @Test
    fun `parseert een geldige metadata-respons`() {
        val body = """
            {
              "status": "OK",
              "pano_id": "CAoSLEFGMVFpcE9YMVJqTDNyRHVCQlZ4",
              "date": "2021-06",
              "location": {"lat": 52.2215, "lng": 6.8937}
            }
        """.trimIndent()

        val panorama = client.parseMetadata(body)

        requireNotNull(panorama)
        assertEquals("CAoSLEFGMVFpcE9YMVJqTDNyRHVCQlZ4", panorama.panoId)
        assertEquals(2021, panorama.capturedYear)
        assertEquals(52.2215, panorama.lat, 0.0001)
        assertEquals(6.8937, panorama.lon, 0.0001)
        assertEquals(true, panorama.imageUrl.contains("pano=CAoSLEFGMVFpcE9YMVJqTDNyRHVCQlZ4"))
    }

    @Test
    fun `ZERO_RESULTS geeft null, geen verzonnen panorama`() {
        val body = """{"status": "ZERO_RESULTS"}"""
        assertNull(client.parseMetadata(body))
    }

    @Test
    fun `ontbrekend pano_id geeft null`() {
        val body = """{"status": "OK", "location": {"lat": 1.0, "lng": 2.0}}"""
        assertNull(client.parseMetadata(body))
    }

    @Test
    fun `ontbrekende locatie geeft null`() {
        val body = """{"status": "OK", "pano_id": "abc"}"""
        assertNull(client.parseMetadata(body))
    }

    @Test
    fun `ontbrekende datum wordt null, niet verzonnen`() {
        val body = """
            {"status": "OK", "pano_id": "abc", "location": {"lat": 1.0, "lng": 2.0}}
        """.trimIndent()

        val panorama = client.parseMetadata(body)

        assertNull(requireNotNull(panorama).capturedYear)
    }
}
