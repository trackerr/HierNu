package nl.hiertoen.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import nl.hiertoen.app.data.local.dao.TrackPointDao
import nl.hiertoen.app.data.local.dao.TripDao
import nl.hiertoen.app.data.local.dao.TripMomentDao
import nl.hiertoen.app.data.local.entity.TrackPointEntity
import nl.hiertoen.app.data.local.entity.TripEntity
import nl.hiertoen.app.data.local.entity.TripMomentEntity

/**
 * Versie 2: Trip + TrackPoint (stap 2) + TripMoment (stap 5, "Deze plek bewaren" — §10.1).
 * PhotoCandidate en de overige entiteiten uit §10.1 komen erbij in stap 6. Nooit destructive
 * migreren (§10.5, §17.2): elke versiesprong krijgt een expliciete [Migration].
 */
@Database(
    entities = [TripEntity::class, TrackPointEntity::class, TripMomentEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(HierToenTypeConverters::class)
abstract class HierToenDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun tripMomentDao(): TripMomentDao

    companion object {
        const val DATABASE_NAME = "hiertoen.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `trip_moments` (
                        `id` TEXT NOT NULL,
                        `tripId` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `lat` REAL NOT NULL,
                        `lon` REAL NOT NULL,
                        `bearingDeg` REAL,
                        `accuracyM` REAL NOT NULL,
                        `type` TEXT NOT NULL,
                        `source` TEXT,
                        `state` TEXT NOT NULL,
                        `note` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_moments_tripId` ON `trip_moments` (`tripId`)")
            }
        }

        @Volatile
        private var instance: HierToenDatabase? = null

        fun getInstance(context: Context): HierToenDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HierToenDatabase::class.java,
                    DATABASE_NAME,
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
