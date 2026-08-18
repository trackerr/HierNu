package nl.hiertoen.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import nl.hiertoen.app.data.local.entity.PhotoCandidateEntity

@Dao
interface PhotoCandidateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(candidates: List<PhotoCandidateEntity>)

    @Query("SELECT * FROM photo_candidates WHERE momentId = :momentId ORDER BY score DESC")
    fun observeForMoment(momentId: String): Flow<List<PhotoCandidateEntity>>

    @Query("SELECT * FROM photo_candidates WHERE momentId = :momentId ORDER BY score DESC LIMIT 1")
    suspend fun getBestForMoment(momentId: String): PhotoCandidateEntity?
}
