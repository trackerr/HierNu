package nl.hiertoen.app.photos

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * org.json vereist een echte implementatie (niet de lege android.jar-stub), dus deze test
 * draait via Robolectric net als de Room-tests, ook al raakt hij de database niet aan.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WikimediaHttpClientParseTest {
    private val client = WikimediaHttpClient()

    @Test
    fun `parseert een geldige geosearch-respons`() {
        val body = """
            {
              "query": {
                "pages": {
                  "12345": {
                    "pageid": 12345,
                    "title": "File:Enschede_marktplein_1936.jpg",
                    "imageinfo": [
                      {
                        "url": "https://upload.wikimedia.org/full.jpg",
                        "descriptionurl": "https://commons.wikimedia.org/wiki/File:Enschede_marktplein_1936.jpg",
                        "thumburl": "https://upload.wikimedia.org/800px-full.jpg",
                        "width": 3000,
                        "height": 2000,
                        "extmetadata": {
                          "DateTimeOriginal": {"value": "1936"},
                          "LicenseShortName": {"value": "CC BY-SA 4.0"},
                          "Artist": {"value": "<a href=\"//example.org\">Jane Doe</a>"},
                          "ImageDescription": {"value": "Marktplein <b>Enschede</b>"},
                          "GPSImgDirection": {"value": "91.5"}
                        }
                      }
                    ],
                    "coordinates": [{"lat": 52.2215, "lon": 6.8937, "primary": ""}]
                  }
                }
              }
            }
        """.trimIndent()

        val results = client.parseResponse(body)

        assertEquals(1, results.size)
        val result = results.first()
        assertEquals("File:Enschede_marktplein_1936.jpg", result.title)
        assertEquals("1936", result.rawDate)
        assertEquals("CC BY-SA 4.0", result.license)
        assertEquals("Jane Doe", result.author)
        assertEquals("Marktplein Enschede", result.description)
        assertEquals(52.2215, result.lat, 0.0001)
        assertEquals(6.8937, result.lon, 0.0001)
        assertEquals(91.5f, result.headingDeg)
        assertEquals(3000, result.widthPx)
        assertEquals(2000, result.heightPx)
    }

    @Test
    fun `slaat pagina's zonder coordinaten over`() {
        val body = """
            {
              "query": {
                "pages": {
                  "1": {
                    "title": "File:Zonder_locatie.jpg",
                    "imageinfo": [{"url": "https://example.org/x.jpg", "extmetadata": {}}]
                  }
                }
              }
            }
        """.trimIndent()

        assertTrue(client.parseResponse(body).isEmpty())
    }

    @Test
    fun `geeft lege lijst bij ontbrekende query-sleutel`() {
        assertTrue(client.parseResponse("""{"batchcomplete": ""}""").isEmpty())
    }

    @Test
    fun `ontbrekende metadata wordt null in plaats van verzonnen`() {
        val body = """
            {
              "query": {
                "pages": {
                  "1": {
                    "title": "File:Geen_metadata.jpg",
                    "imageinfo": [{"url": "https://example.org/x.jpg", "extmetadata": {}}],
                    "coordinates": [{"lat": 1.0, "lon": 2.0}]
                  }
                }
              }
            }
        """.trimIndent()

        val result = client.parseResponse(body).first()
        assertNull(result.rawDate)
        assertNull(result.license)
        assertNull(result.author)
        assertNull(result.headingDeg)
    }
}
