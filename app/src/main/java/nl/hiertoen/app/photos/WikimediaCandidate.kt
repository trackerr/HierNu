package nl.hiertoen.app.photos

/** Ruw, ongescoord resultaat van een Wikimedia Commons geosearch-oproep — §8.1. */
data class WikimediaCandidate(
    val pageId: String,
    val title: String,
    val imageUrl: String,
    val sourcePageUrl: String,
    val thumbUrl: String,
    val lat: Double,
    val lon: Double,
    val rawDate: String?,
    val license: String?,
    val author: String?,
    val description: String?,
    val widthPx: Int?,
    val heightPx: Int?,
    val headingDeg: Float?,
)
