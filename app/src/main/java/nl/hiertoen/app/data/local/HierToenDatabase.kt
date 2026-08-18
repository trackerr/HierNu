package nl.hiertoen.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import nl.hiertoen.app.data.local.dao.TrackPointDao
import nl.hiertoen.app.data.local.dao.TripDao
import nl.hiertoen.app.data.local.entity.TrackPointEntity
import nl.hiertoen.app.data.local.entity.TripEntity

/**
 * Versie 1: Trip + TrackPoint (bouwvolgorde §17.1, stap 2). TripMoment, PhotoCandidate en
 * de overige entiteiten uit §10.1 komen erbij zodra "Deze plek bewaren" (stap 5) en de
 * beeldbronnen (stap 6) worden gebouwd — via een expliciete Room-migratie, nooit destructive.
 */
@Database(
    entities = [TripEntity::class, TrackPointEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(HierToenTypeConverters::class)
abstract class HierToenDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun trackPointDao(): TrackPointDao

    companion object {
        const val DATABASE_NAME = "hiertoen.db"
    }
}
