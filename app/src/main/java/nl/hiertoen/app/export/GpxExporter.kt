package nl.hiertoen.app.export

import nl.hiertoen.app.data.local.entity.TrackPointEntity
import nl.hiertoen.app.data.local.entity.TrackPointValidity
import nl.hiertoen.app.data.local.entity.TripEntity
import nl.hiertoen.app.data.local.entity.TripMomentEntity
import java.time.Instant

/**
 * GPX 1.1 — §6.5. Alleen VALID-trackpunten vormen de routegeometrie (§6.3: geen precieze route
 * verzinnen van onbetrouwbare punten); segmenten volgen segmentIndex, zodat een tijdsgat
 * (tunnel, geen GPS) niet als één rechte lijn over het gat heen wordt getekend.
 */
object GpxExporter {
    fun export(trip: TripEntity, trackPoints: List<TrackPointEntity>, moments: List<TripMomentEntity>): String {
        val validPoints = trackPoints.filter { it.validity == TrackPointValidity.VALID }.sortedBy { it.timestamp }
        val segments = validPoints.groupBy { it.segmentIndex }.toSortedMap()

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"HierToen\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <trk>\n")
        sb.append("    <name>").append(escapeXml(trip.name ?: "HierToen-rit")).append("</name>\n")
        for ((_, points) in segments) {
            if (points.isEmpty()) continue
            sb.append("    <trkseg>\n")
            for (point in points) {
                sb.append("      <trkpt lat=\"").append(point.lat).append("\" lon=\"").append(point.lon).append("\">\n")
                point.altitude?.let { sb.append("        <ele>").append(it).append("</ele>\n") }
                sb.append("        <time>").append(Instant.ofEpochMilli(point.timestamp)).append("</time>\n")
                sb.append("      </trkpt>\n")
            }
            sb.append("    </trkseg>\n")
        }
        sb.append("  </trk>\n")
        for (moment in moments.sortedBy { it.timestamp }) {
            sb.append("  <wpt lat=\"").append(moment.lat).append("\" lon=\"").append(moment.lon).append("\">\n")
            sb.append("    <name>").append(escapeXml(moment.type.name)).append("</name>\n")
            sb.append("    <time>").append(Instant.ofEpochMilli(moment.timestamp)).append("</time>\n")
            moment.note?.let { sb.append("    <desc>").append(escapeXml(it)).append("</desc>\n") }
            sb.append("  </wpt>\n")
        }
        sb.append("</gpx>\n")
        return sb.toString()
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
