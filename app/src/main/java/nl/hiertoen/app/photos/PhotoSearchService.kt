package nl.hiertoen.app.photos

import android.util.Log
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
 * PHOTO_SHOWN/PHOTO_NOT_FOUND al naar gelang het resultaat.
 */
class PhotoSearchService(
    private val client: WikimediaClient,
    private val repository: TripRepository,
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

            if (usable.isEmpty()) {
                repository.saveMoment(moment.copy(state = MomentState.PHOTO_NOT_FOUND))
                return
            }

            val entities = usable.map { it.toEntity(moment.id) }
            repository.saveCandidates(entities)
            repository.saveMoment(moment.copy(state = MomentState.PHOTO_SHOWN, source = PROVIDER_WIKIMEDIA))
            Log.d(TAG, "moment=${moment.id} PHOTO_SHOWN, beste score=${entities.maxOf { it.score }}")
        } catch (e: Exception) {
            // Nooit stil op PENDING_LOOKUP laten hangen: een onverwachte fout (parsing, DB,
            // scoring) mag het moment niet voor altijd "aan het zoeken" laten staan, en mag
            // vooral de service niet laten crashen — de rit zelf moet gewoon doorlopen.
            Log.e(TAG, "beeldzoekopdracht voor moment=${moment.id} mislukt", e)
            repository.saveMoment(moment.copy(state = MomentState.PHOTO_NOT_FOUND))
        }
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

        // Standaard zoekradius §11; TrackingService geeft de waarde uit SettingsRepository door.
        const val DEFAULT_SEARCH_RADIUS_M = 100
    }
}
