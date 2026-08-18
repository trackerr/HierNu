package nl.hiertoen.app.data.repository

import kotlinx.coroutines.flow.Flow
import nl.hiertoen.app.data.local.dao.PhotoCandidateDao
import nl.hiertoen.app.data.local.dao.TrackPointDao
import nl.hiertoen.app.data.local.dao.TripDao
import nl.hiertoen.app.data.local.dao.TripMomentDao
import nl.hiertoen.app.data.local.entity.PhotoCandidateEntity
import nl.hiertoen.app.data.local.entity.TrackPointEntity
import nl.hiertoen.app.data.local.entity.TripEntity
import nl.hiertoen.app.data.local.entity.TripMomentEntity
import nl.hiertoen.app.data.local.entity.TripStatus

class TripRepositoryImpl(
    private val tripDao: TripDao,
    private val trackPointDao: TrackPointDao,
    private val tripMomentDao: TripMomentDao,
    private val photoCandidateDao: PhotoCandidateDao,
) : TripRepository {
    override fun observeTrips(): Flow<List<TripEntity>> = tripDao.observeAll()

    override suspend fun getTrip(id: String): TripEntity? = tripDao.getById(id)

    override suspend fun getTripsByStatus(status: TripStatus): List<TripEntity> = tripDao.getByStatus(status)

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

    override fun observeMoments(tripId: String): Flow<List<TripMomentEntity>> = tripMomentDao.observeForTrip(tripId)

    override suspend fun saveMoment(moment: TripMomentEntity) {
        if (tripMomentDao.getById(moment.id) == null) {
            tripMomentDao.insert(moment)
        } else {
            tripMomentDao.update(moment)
        }
    }

    override fun observeCandidates(momentId: String): Flow<List<PhotoCandidateEntity>> =
        photoCandidateDao.observeForMoment(momentId)

    override suspend fun getBestCandidate(momentId: String): PhotoCandidateEntity? =
        photoCandidateDao.getBestForMoment(momentId)

    override suspend fun saveCandidates(candidates: List<PhotoCandidateEntity>) = photoCandidateDao.insertAll(candidates)
}
