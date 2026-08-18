package nl.hiertoen.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Mag een afbeelding lokaal gecachet worden? §13.2: alleen als bronlicentie/API-voorwaarden dat toestaan. */
enum class CachePolicy {
    ALLOWED,
    NOT_ALLOWED,
}

/**
 * Eén kandidaat-beeld voor een moment, vóór en na scoring (§7.3/§7.4) — §10.1. Alle kandidaten
 * worden bewaard (niet alleen de winnaar), zodat de tijdlijn later alternatieven kan tonen
 * (§7.4: "bewaar alternatieven voor de tijdlijn").
 */
@Entity(
    tableName = "photo_candidates",
    foreignKeys = [
        ForeignKey(
            entity = TripMomentEntity::class,
            parentColumns = ["id"],
            childColumns = ["momentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("momentId")],
)
data class PhotoCandidateEntity(
    @PrimaryKey val id: String,
    val momentId: String,
    val provider: String,
    val providerId: String,
    val imageUrl: String,
    val sourcePageUrl: String,
    val thumbUrl: String,
    val title: String,
    val description: String?,
    val yearFrom: Int?,
    val yearTo: Int?,
    val author: String?,
    val license: String?,
    val attribution: String,
    val lat: Double,
    val lon: Double,
    val headingDeg: Float?,
    val distanceM: Double,
    val score: Double,
    val cachePolicy: CachePolicy,
)
