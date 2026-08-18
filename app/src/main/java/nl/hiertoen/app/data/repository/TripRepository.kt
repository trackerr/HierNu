package nl.hiertoen.app.data.repository

import kotlinx.coroutines.flow.Flow
import nl.hiertoen.app.data.local.entity.TrackPointEntity
import nl.hiertoen.app.data.local.entity.TripEntity
import nl.hiertoen.app.data.local.entity.TripMomentEntity
import nl.hiertoen.app.data.local.entity.TripStatus

/**
 * Toegang tot ritten, trackpunten en momenten. Zit tussen de Room-laag en de rest van de app
 * (motion engine, UI, export) zodat die niet rechtstreeks van Room-DAO's afhangen — §9.3.
 */
interface TripRepository {
    fun observeTrips(): Flow<List<TripEntity>>
    suspend fun getTrip(id: String): TripEntity?
    suspend fun getTripsByStatus(status: TripStatus): List<TripEntity>
    suspend fun saveTrip(trip: TripEntity)
    suspend fun deleteTrip(trip: TripEntity)

    fun observeTrackPoints(tripId: String): Flow<List<TrackPointEntity>>
    suspend fun appendTrackPoint(point: TrackPointEntity)
    suspend fun appendTrackPoints(points: List<TrackPointEntity>)

    fun observeMoments(tripId: String): Flow<List<TripMomentEntity>>
    suspend fun saveMoment(moment: TripMomentEntity)
}
