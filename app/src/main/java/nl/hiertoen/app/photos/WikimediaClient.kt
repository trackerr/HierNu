package nl.hiertoen.app.photos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

interface WikimediaClient {
    /** Geeft een lege lijst terug bij netwerkfouten of onbruikbare respons — §15: "geen internet"/"bronlimiet" faalt stil, niet hard. */
    suspend fun search(lat: Double, lon: Double, radiusM: Int, limit: Int = 20): List<WikimediaCandidate>
}

/**
 * MediaWiki generator=geosearch in namespace 6 (bestanden), zoals §8.1 voorschrijft. Gebruikt
 * bewust geen extra HTTP-library (Retrofit/Ktor) voor deze ene endpoint — past bij "zo veel
 * mogelijk zonder eigen backend" en scheelt een dependency voor precies één aanroep.
 */
class WikimediaHttpClient : WikimediaClient {
    override suspend fun search(lat: Double, lon: Double, radiusM: Int, limit: Int): List<WikimediaCandidate> =
        withContext(Dispatchers.IO) {
            try {
                val connection = (buildUrl(lat, lon, radiusM, limit).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                val body = try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext emptyList()
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
                parseResponse(body)
            } catch (e: IOException) {
                emptyList()
            } catch (e: JSONException) {
                emptyList()
            }
        }

    private fun buildUrl(lat: Double, lon: Double, radiusM: Int, limit: Int): URL {
        val coord = URLEncoder.encode("$lat|$lon", "UTF-8")
        val cappedRadius = radiusM.coerceIn(10, MAX_RADIUS_M)
        return URL(
            "$API_BASE?action=query&generator=geosearch&ggscoord=$coord&ggsradius=$cappedRadius" +
                "&ggsnamespace=6&ggslimit=$limit&prop=imageinfo|coordinates" +
                "&iiprop=url|extmetadata|size&iiurlwidth=800&format=json",
        )
    }

    // internal (i.p.v. private) zodat de parsing apart getest kan worden met een JSON-fixture,
    // zonder een echte HTTP-call of een fake server op te tuigen.
    internal fun parseResponse(body: String): List<WikimediaCandidate> {
        val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        val results = mutableListOf<WikimediaCandidate>()

        val keys = pages.keys()
        while (keys.hasNext()) {
            val page = pages.getJSONObject(keys.next())
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val coord = page.optJSONArray("coordinates")?.optJSONObject(0) ?: continue
            if (!coord.has("lat") || !coord.has("lon")) continue

            val extmeta = info.optJSONObject("extmetadata")
            fun meta(key: String): String? = extmeta?.optJSONObject(key)?.optString("value")?.takeIf { it.isNotBlank() }

            results += WikimediaCandidate(
                pageId = page.optString("pageid", page.optString("title")),
                title = page.optString("title"),
                imageUrl = info.optString("url"),
                sourcePageUrl = info.optString("descriptionurl"),
                thumbUrl = info.optString("thumburl", info.optString("url")),
                lat = coord.getDouble("lat"),
                lon = coord.getDouble("lon"),
                rawDate = meta("DateTimeOriginal") ?: meta("DateTime"),
                license = meta("LicenseShortName") ?: meta("License"),
                author = meta("Artist")?.let(::stripHtml),
                description = meta("ImageDescription")?.let(::stripHtml),
                widthPx = if (info.has("width")) info.optInt("width") else null,
                heightPx = if (info.has("height")) info.optInt("height") else null,
                headingDeg = meta("GPSImgDirection")?.toFloatOrNull(),
            )
        }
        return results
    }

    private fun stripHtml(value: String): String = HTML_TAG_REGEX.replace(value, "").trim()

    companion object {
        private const val API_BASE = "https://commons.wikimedia.org/w/api.php"
        private const val TIMEOUT_MS = 10_000
        private const val MAX_RADIUS_M = 10_000

        // Wikimedia's API-etiquette vereist een herleidbare User-Agent; een generieke Java-agent
        // wordt eerder geweigerd of harder gerate-limit.
        private const val USER_AGENT = "HierToen/0.1 (https://github.com/trackerr/HierNu)"

        private val HTML_TAG_REGEX = Regex("<[^>]*>")
    }
}
