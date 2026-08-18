package nl.hiertoen.app.photos

import nl.hiertoen.app.core.GeoMath
import kotlin.math.abs

/** Resultaat van het scoren van één kandidaat — §7.3. */
data class ScoredCandidate(
    val candidate: WikimediaCandidate,
    val distanceM: Double,
    val year: Int?,
    val score: Double,
    val hasUsableLicense: Boolean,
)

/**
 * Implementeert de "aanbevolen eerste score" uit §7.3 en de selectieregels uit §7.4.
 * Puur en deterministisch (geen netwerk/klok), zodat scoring los van [WikimediaHttpClient]
 * getest kan worden — §14.1: "Kandidatenscore en bronprioriteit".
 */
object PhotoCandidateScorer {
    data class Context(
        val queryLat: Double,
        val queryLon: Double,
        val queryHeadingDeg: Float?,
        val searchRadiusM: Double,
        val currentYear: Int,
        /** §11 "Voorkeur": Aan = oudste bruikbare beeld, Uit = dichtstbijzijnde (proximity krijgt dan het volle gewicht). */
        val preferOldest: Boolean = true,
    )

    private const val EARLIEST_PLAUSIBLE_YEAR = 1850

    fun score(candidate: WikimediaCandidate, context: Context): ScoredCandidate {
        val distanceM = GeoMath.haversineMeters(context.queryLat, context.queryLon, candidate.lat, candidate.lon)
        val year = YearParser.extractYear(candidate.rawDate)

        val proximity = (1.0 - distanceM / context.searchRadiusM).coerceIn(0.0, 1.0)
        val agePreference = if (context.preferOldest) agePreferenceScore(year, context.currentYear) else 0.5
        val headingMatch = headingMatchScore(context.queryHeadingDeg, candidate.headingDeg)
        val imageQuality = imageQualityScore(candidate.widthPx, candidate.heightPx)
        val metadataReliability = metadataReliabilityScore(candidate)

        val score = 0.40 * proximity +
            0.25 * agePreference +
            0.20 * headingMatch +
            0.10 * imageQuality +
            0.05 * metadataReliability

        return ScoredCandidate(
            candidate = candidate,
            distanceM = distanceM,
            year = year,
            score = score,
            hasUsableLicense = !candidate.license.isNullOrBlank(),
        )
    }

    /**
     * Onbekend jaartal telt neutraal mee (0.5), niet als afwijzing — dezelfde regel als voor
     * ontbrekende camerakoers in §7.4 ("mag heading_match neutraal zijn; niet automatisch afwijzen").
     */
    private fun agePreferenceScore(year: Int?, currentYear: Int): Double {
        if (year == null) return 0.5
        if (year <= EARLIEST_PLAUSIBLE_YEAR) return 1.0
        if (year >= currentYear) return 0.0
        val span = (currentYear - EARLIEST_PLAUSIBLE_YEAR).toDouble()
        return (1.0 - (year - EARLIEST_PLAUSIBLE_YEAR) / span).coerceIn(0.0, 1.0)
    }

    private fun headingMatchScore(queryHeadingDeg: Float?, candidateHeadingDeg: Float?): Double {
        if (queryHeadingDeg == null || candidateHeadingDeg == null) return 0.5
        val diff = angleDifferenceDeg(queryHeadingDeg, candidateHeadingDeg)
        return (1.0 - diff / 180.0).coerceIn(0.0, 1.0)
    }

    private fun angleDifferenceDeg(a: Float, b: Float): Double {
        val diff = abs(a - b) % 360.0
        return if (diff > 180.0) 360.0 - diff else diff
    }

    /** Ruwe heuristiek (megapixels) bij gebrek aan een echte beeldkwaliteitsmeting; ontbrekende afmetingen zijn neutraal. */
    private fun imageQualityScore(widthPx: Int?, heightPx: Int?): Double {
        val w = widthPx ?: return 0.5
        val h = heightPx ?: return 0.5
        val megapixels = (w.toLong() * h.toLong()) / 1_000_000.0
        return (megapixels / 8.0).coerceIn(0.0, 1.0)
    }

    private fun metadataReliabilityScore(candidate: WikimediaCandidate): Double {
        var points = 0.0
        if (!candidate.license.isNullOrBlank()) points += 0.4
        if (!candidate.author.isNullOrBlank()) points += 0.3
        if (!candidate.rawDate.isNullOrBlank()) points += 0.3
        return points.coerceIn(0.0, 1.0)
    }
}
