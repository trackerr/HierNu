package nl.hiertoen.app.photos

import android.util.Log
import nl.hiertoen.app.core.GeoMath
import nl.hiertoen.app.data.local.entity.CachePolicy
import nl.hiertoen.app.data.local.entity.MomentState
import nl.hiertoen.app.data.local.entity.PhotoCandidateEntity
import nl.hiertoen.app.data.local.entity.TripMomentEntity
import nl.hiertoen.app.data.repository.TripRepository
import java.time.Year
import java.util.UUID

/**
 * Orkestreert de beeldzoekopdracht voor één moment — §3.4/§3.5 hoofdstroom en §7.2 zoekstrategie.
 * Bewaart alle bruikbare kandidaten (niet alleen de winnaar, §7.4), en zet het moment op
 * PHOTO_SHOWN/PHOTO_NOT_FOUND al naar gelang het resultaat. Wikimedia (§7.1 rang 2) gaat altijd
 * voor; Street View (rang 4) is alleen een fallback als Wikimedia niets bruikbaars oplevert,
 * en dan expliciet als "actuele opname", niet als historisch beeld (§8.3).
 */
class PhotoSearchService(
    private val client: WikimediaClient,
    private val repository: TripRepository,
    private val streetViewClient: StreetViewClient? = null,
) {
    suspend fun searchForMoment(
        moment: TripMomentEntity,
        searchRadiusM: Int = DEFAULT_SEARCH_RADIUS_M,
        preferOldest: Boolean = true,
    ) {
        repository.saveMoment(moment.copy(state = MomentState.PENDING_LOOKUP))

        try {
            val raw = client.search(moment.lat, moment.lon, searchRadiusM)
            Log.d(TAG, "moment=${moment.id} radius=${searchRadiusM}m ruwe resultaten=${raw.size}")

            val context = PhotoCandidateScorer.Context(
                queryLat = moment.lat,
                queryLon = moment.lon,
                queryHeadingDeg = moment.bearingDeg,
                searchRadiusM = searchRadiusM.toDouble(),
                currentYear = Year.now().value,
                preferOldest = preferOldest,
            )

            val usable = raw.asSequence()
                .map { PhotoCandidateScorer.score(it, context) }
                // §7.4: een bron zonder duidelijke licentie tonen we niet, zelfs niet als alternatief.
                .filter { it.hasUsableLicense }
                .sortedByDescending { it.score }
                .toList()
            Log.d(TAG, "moment=${moment.id} bruikbaar na licentiefilter=${usable.size}")

            if (usable.isNotEmpty()) {
                val entities = usable.map { it.toEntity(moment.id) }
                repository.saveCandidates(entities)
                repository.saveMoment(moment.copy(state = MomentState.PHOTO_SHOWN, source = PROVIDER_WIKIMEDIA))
                Log.d(TAG, "moment=${moment.id} PHOTO_SHOWN via Wikimedia, beste score=${entities.maxOf { it.score }}")
                return
            }

            if (tryStreetViewFallback(moment)) return

            repository.saveMoment(moment.copy(state = MomentState.PHOTO_NOT_FOUND))
        } catch (e: Exception) {
            // Nooit stil op PENDING_LOOKUP laten hangen: een onverwachte fout (parsing, DB,
            // scoring) mag het moment niet voor altijd "aan het zoeken" laten staan, en mag
            // vooral de service niet laten crashen — de rit zelf moet gewoon doorlopen.
            Log.e(TAG, "beeldzoekopdracht voor moment=${moment.id} mislukt", e)
            repository.saveMoment(moment.copy(state = MomentState.PHOTO_NOT_FOUND))
        }
    }

    /** @return true als er een Street View-fallback gevonden en opgeslagen is. */
    private suspend fun tryStreetViewFallback(moment: TripMomentEntity): Boolean {
        val panorama = streetViewClient?.findPanorama(moment.lat, moment.lon) ?: return false
        Log.d(TAG, "moment=${moment.id} Street View-fallback: pano=${panorama.panoId}")

        val entity = PhotoCandidateEntity(
            id = UUID.randomUUID().toString(),
            momentId = moment.id,
            provider = PROVIDER_STREETVIEW,
            providerId = panorama.panoId,
            imageUrl = panorama.imageUrl,
            sourcePageUrl = "https://www.google.com/maps/@?api=1&map_action=pano&pano=${panorama.panoId}",
            thumbUrl = panorama.imageUrl,
            title = "Google Street View",
            description = null,
            yearFrom = panorama.capturedYear,
            yearTo = panorama.capturedYear,
            author = null,
            license = "Geen open licentie — Street View-gebruiksvoorwaarden van Google",
            attribution = "© Google Street View",
            lat = panorama.lat,
            lon = panorama.lon,
            headingDeg = null,
            distanceM = GeoMath.haversineMeters(moment.lat, moment.lon, panorama.lat, panorama.lon),
            // Niet vergelijkbaar met de Wikimedia-score (§7.3): dit is de laatste optie, niet
            // een concurrerende kandidaat, dus geen kunstmatig hoog cijfer verzinnen.
            score = 0.0,
            cachePolicy = CachePolicy.NOT_ALLOWED,
        )
        repository.saveCandidates(listOf(entity))
        repository.saveMoment(moment.copy(state = MomentState.PHOTO_SHOWN, source = PROVIDER_STREETVIEW))
        return true
    }

    private fun ScoredCandidate.toEntity(momentId: String): PhotoCandidateEntity = PhotoCandidateEntity(
        id = UUID.randomUUID().toString(),
        momentId = momentId,
        provider = PROVIDER_WIKIMEDIA,
        providerId = candidate.pageId,
        imageUrl = candidate.imageUrl,
        sourcePageUrl = candidate.sourcePageUrl,
        thumbUrl = candidate.thumbUrl,
        title = candidate.title,
        description = candidate.description,
        yearFrom = year,
        yearTo = year,
        author = candidate.author,
        license = candidate.license,
        attribution = buildAttribution(candidate),
        lat = candidate.lat,
        lon = candidate.lon,
        headingDeg = candidate.headingDeg,
        distanceM = distanceM,
        score = score,
        cachePolicy = CachePolicy.ALLOWED,
    )

    private fun buildAttribution(candidate: WikimediaCandidate): String {
        val author = candidate.author?.takeIf { it.isNotBlank() } ?: "onbekende maker"
        val license = candidate.license?.takeIf { it.isNotBlank() } ?: "onbekende licentie"
        return "$author — $license (Wikimedia Commons)"
    }

    companion object {
        private const val TAG = "HierToen/Photos"
        const val PROVIDER_WIKIMEDIA = "wikimedia"
        const val PROVIDER_STREETVIEW = "google_streetview"

        // Standaard zoekradius §11; TrackingService geeft de waarde uit SettingsRepository door.
        const val DEFAULT_SEARCH_RADIUS_M = 100
    }
}
