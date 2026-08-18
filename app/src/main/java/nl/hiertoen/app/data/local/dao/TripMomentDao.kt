package nl.hiertoen.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import nl.hiertoen.app.data.local.entity.TripMomentEntity

@Dao
interface TripMomentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(moment: TripMomentEntity)

    @Update
    suspend fun update(moment: TripMomentEntity)

    @Query("SELECT * FROM trip_moments WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun observeForTrip(tripId: String): Flow<List<TripMomentEntity>>

    @Query("SELECT * FROM trip_moments WHERE id = :id")
    suspend fun getById(id: String): TripMomentEntity?
}
