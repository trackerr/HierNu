package nl.hiertoen.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * §10.3 noemt PHOTO_SHOWN/PHOTO_NOT_FOUND en de aanleiding (MANUAL_BOOKMARK e.d.) allebei onder
 * "Momenttypen", maar functioneel zijn dat twee verschillende dingen: wat het moment veroorzaakte
 * versus de huidige uitkomst van de beeldzoekopdracht. Hier gesplitst in [MomentType] en
 * [MomentState] zodat een moment niet tegelijk "MANUAL_BOOKMARK" en "PHOTO_SHOWN" als type heeft.
 */
enum class MomentType {
    MANUAL_BOOKMARK,
    AUTO_STOP,
    LONG_STOP,
    ROUTE_GAP,
}

/** Status van de beeldzoekopdracht voor dit moment — zie de hoofdstroom in §3.4/§3.5. */
enum class MomentState {
    NO_IMAGE_YET,
    PENDING_LOOKUP,
    PHOTO_SHOWN,
    PHOTO_NOT_FOUND,
}

/**
 * Eén gemarkeerde locatie tijdens een rit — automatisch (stop) of handmatig ("Deze plek
 * bewaren", §3.5). `accuracyM` staat niet met zoveel woorden in de §10.1-tabel maar is nodig
 * voor het randgeval "Gebruiker markeert tijdens slecht GPS" (§15: bewaren met waarschuwing en
 * nauwkeurigheidsradius).
 */
@Entity(
    tableName = "trip_moments",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class TripMomentEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val timestamp: Long,
    val lat: Double,
    val lon: Double,
    val bearingDeg: Float?,
    val accuracyM: Float,
    val type: MomentType,
    val source: String?,
    val state: MomentState,
    val note: String?,
)
