package nl.hiertoen.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import nl.hiertoen.app.data.local.entity.TrackPointEntity

@Dao
interface TrackPointDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(trackPoint: TrackPointEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(trackPoints: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun observeForTrip(tripId: String): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getForTrip(tripId: String): List<TrackPointEntity>

    @Query("SELECT COUNT(*) FROM track_points WHERE tripId = :tripId")
    suspend fun countForTrip(tripId: String): Int
}
