package nl.hiertoen.app.photos

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Eén gevonden panorama — §7.1 rang 4: alleen een actuele fallback, nooit een historische garantie. */
data class StreetViewPanorama(
    val panoId: String,
    val imageUrl: String,
    /** Opnamejaar van dít (actuele) panorama, indien Google dat meegeeft — geen historische selectie (§8.3). */
    val capturedYear: Int?,
    val lat: Double,
    val lon: Double,
)

interface StreetViewClient {
    /** Geeft null terug zonder sleutel, zonder dekking, of bij een fout — nooit een harde crash. */
    suspend fun findPanorama(lat: Double, lon: Double): StreetViewPanorama?
}

/**
 * Street View Static API — §8.3. Bewuste vereenvoudiging t.o.v. de aanbevolen aanpak: de sleutel
 * staat direct (met package-restrictie) in de app in plaats van achter een server-side
 * URL-signing-backend. Prima voor dit veldtest-gebruik, niet voor een publieke release —
 * zie README voor de afweging.
 */
class GoogleStreetViewClient(private val apiKey: String) : StreetViewClient {
    override suspend fun findPanorama(lat: Double, lon: Double): StreetViewPanorama? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext null
            val metadataUrl = buildMetadataUrl(lat, lon)
            try {
                val connection = (metadataUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                }
                val body = try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        Log.w(TAG, "Street View metadata gaf HTTP ${connection.responseCode}")
                        return@withContext null
                    }
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
                parseMetadata(body)
            } catch (e: IOException) {
                Log.w(TAG, "netwerkfout bij Street View-metadata", e)
                null
            } catch (e: JSONException) {
                Log.w(TAG, "kon Street View-metadata niet parsen", e)
                null
            }
        }

    private fun buildMetadataUrl(lat: Double, lon: Double): URL {
        val key = URLEncoder.encode(apiKey, "UTF-8")
        return URL("$METADATA_BASE?location=$lat,$lon&key=$key")
    }

    private fun imageUrlFor(panoId: String): String {
        val key = URLEncoder.encode(apiKey, "UTF-8")
        return "$IMAGE_BASE?size=800x600&pano=$panoId&key=$key"
    }

    // internal zodat de parsing apart getest kan worden met een JSON-fixture, zoals bij WikimediaHttpClient.
    internal fun parseMetadata(body: String): StreetViewPanorama? {
        val json = JSONObject(body)
        if (json.optString("status") != "OK") return null

        val panoId = json.optString("pano_id").takeIf { it.isNotBlank() } ?: return null
        val location = json.optJSONObject("location") ?: return null
        if (!location.has("lat") || !location.has("lng")) return null

        return StreetViewPanorama(
            panoId = panoId,
            imageUrl = imageUrlFor(panoId),
            capturedYear = YearParser.extractYear(json.optString("date").ifBlank { null }),
            lat = location.getDouble("lat"),
            lon = location.getDouble("lng"),
        )
    }

    companion object {
        private const val TAG = "HierToen/StreetView"
        private const val METADATA_BASE = "https://maps.googleapis.com/maps/api/streetview/metadata"
        private const val IMAGE_BASE = "https://maps.googleapis.com/maps/api/streetview"
        private const val TIMEOUT_MS = 10_000
    }
}
