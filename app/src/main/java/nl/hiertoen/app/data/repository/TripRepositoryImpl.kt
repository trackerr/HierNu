package nl.hiertoen.app.data.repository

import kotlinx.coroutines.flow.Flow
import nl.hiertoen.app.data.local.dao.TrackPointDao
import nl.hiertoen.app.data.local.dao.TripDao
import nl.hiertoen.app.data.local.entity.TrackPointEntity
import nl.hiertoen.app.data.local.entity.TripEntity

class TripRepositoryImpl(
    private val tripDao: TripDao,
    private val trackPointDao: TrackPointDao,
) : TripRepository {
    override fun observeTrips(): Flow<List<TripEntity>> = tripDao.observeAll()

    override suspend fun getTrip(id: String): TripEntity? = tripDao.getById(id)

    override suspend fun saveTrip(trip: TripEntity) {
        if (tripDao.getById(trip.id) == null) {
            tripDao.insert(trip)
        } else {
            tripDao.update(trip)
        }
    }

    override suspend fun deleteTrip(trip: TripEntity) = tripDao.delete(trip)

    override fun observeTrackPoints(tripId: String): Flow<List<TrackPointEntity>> =
        trackPointDao.observeForTrip(tripId)

    override suspend fun appendTrackPoint(point: TrackPointEntity) = trackPointDao.insert(point)

    override suspend fun appendTrackPoints(points: List<TrackPointEntity>) = trackPointDao.insertAll(points)
}
