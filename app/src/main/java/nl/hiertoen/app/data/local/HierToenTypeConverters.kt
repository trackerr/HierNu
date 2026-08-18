package nl.hiertoen.app.data.local

import androidx.room.TypeConverter
import nl.hiertoen.app.core.ActivityType
import nl.hiertoen.app.data.local.entity.MomentState
import nl.hiertoen.app.data.local.entity.MomentType
import nl.hiertoen.app.data.local.entity.TrackPointValidity
import nl.hiertoen.app.data.local.entity.TripMode
import nl.hiertoen.app.data.local.entity.TripStatus

/**
 * Enums als tekst opslaan i.p.v. ordinal, zodat de betekenis van bestaande rijen niet
 * verschuift wanneer een enum later een waarde erbij krijgt (§10.5: geen destructive migrations).
 */
class HierToenTypeConverters {
    @TypeConverter
    fun fromTripStatus(value: TripStatus): String = value.name

    @TypeConverter
    fun toTripStatus(value: String): TripStatus = TripStatus.valueOf(value)

    @TypeConverter
    fun fromTripMode(value: TripMode): String = value.name

    @TypeConverter
    fun toTripMode(value: String): TripMode = TripMode.valueOf(value)

    @TypeConverter
    fun fromActivityType(value: ActivityType): String = value.name

    @TypeConverter
    fun toActivityType(value: String): ActivityType = ActivityType.valueOf(value)

    @TypeConverter
    fun fromTrackPointValidity(value: TrackPointValidity): String = value.name

    @TypeConverter
    fun toTrackPointValidity(value: String): TrackPointValidity = TrackPointValidity.valueOf(value)

    @TypeConverter
    fun fromMomentType(value: MomentType): String = value.name

    @TypeConverter
    fun toMomentType(value: String): MomentType = MomentType.valueOf(value)

    @TypeConverter
    fun fromMomentState(value: MomentState): String = value.name

    @TypeConverter
    fun toMomentState(value: String): MomentState = MomentState.valueOf(value)
}
