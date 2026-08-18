package nl.hiertoen.app.export

import nl.hiertoen.app.data.local.entity.PhotoCandidateEntity
import nl.hiertoen.app.data.local.entity.TrackPointEntity
import nl.hiertoen.app.data.local.entity.TrackPointValidity
import nl.hiertoen.app.data.local.entity.TripEntity
import nl.hiertoen.app.data.local.entity.TripMomentEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** GeoJSON (RFC 7946) — §6.5. Punt-properties volgen het voorbeeld uit §10.4. */
object GeoJsonExporter {
    fun export(
        trip: TripEntity,
        trackPoints: List<TrackPointEntity>,
        moments: List<TripMomentEntity>,
        bestCandidateByMomentId: Map<String, PhotoCandidateEntity> = emptyMap(),
    ): String {
        val validPoints = trackPoints.filter { it.validity == TrackPointValidity.VALID }.sortedBy { it.timestamp }
        val segments = validPoints.groupBy { it.segmentIndex }.toSortedMap()

        val features = JSONArray()

        for ((segmentIndex, points) in segments) {
            if (points.size < 2) continue // RFC 7946: een LineString heeft minstens 2 posities nodig
            val coordinates = JSONArray()
            points.forEach { coordinates.put(JSONArray(listOf(it.lon, it.lat))) }
            val geometry = JSONObject().put("type", "LineString").put("coordinates", coordinates)
            val properties = JSONObject().put("tripId", trip.id).put("segmentIndex", segmentIndex)
            features.put(JSONObject().put("type", "Feature").put("geometry", geometry).put("properties", properties))
        }

        for (moment in moments.sortedBy { it.timestamp }) {
            val geometry = JSONObject().put("type", "Point").put("coordinates", JSONArray(listOf(moment.lon, moment.lat)))
            val properties = JSONObject()
                .put("tripId", trip.id)
                .put("type", moment.type.name)
                .put("timestamp", Instant.ofEpochMilli(moment.timestamp).toString())

            bestCandidateByMomentId[moment.id]?.let { candidate ->
                properties.put("imageProvider", candidate.provider)
                properties.put("imageYear", candidate.yearFrom ?: JSONObject.NULL)
                properties.put("sourceUrl", candidate.sourcePageUrl)
                properties.put("attribution", candidate.attribution)
            }

            features.put(JSONObject().put("type", "Feature").put("geometry", geometry).put("properties", properties))
        }

        return JSONObject().put("type", "FeatureCollection").put("features", features).toString(2)
    }
}
