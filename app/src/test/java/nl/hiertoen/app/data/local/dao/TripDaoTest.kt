package nl.hiertoen.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.hiertoen.app.data.local.HierToenDatabase
import nl.hiertoen.app.data.local.TestFixtures
import nl.hiertoen.app.data.local.entity.TripStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Room-tests als JVM unit test via Robolectric, zodat ze in CI draaien zonder emulator.
 * Zie technische bouwspecificatie §14.1/§14.2 en het rapport-advies om dit vroeg te dekken.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TripDaoTest {
    private lateinit var database: HierToenDatabase
    private lateinit var tripDao: TripDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HierToenDatabase::class.java,
        ).allowMainThreadQueries().build()
        tripDao = database.tripDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert en getById geven dezelfde rit terug`() = runTest {
        val trip = TestFixtures.trip(id = "trip-a")
        tripDao.insert(trip)

        val result = tripDao.getById("trip-a")

        assertEquals(trip, result)
    }

    @Test
    fun `getById op onbekend id geeft null`() = runTest {
        assertNull(tripDao.getById("does-not-exist"))
    }

    @Test
    fun `observeAll sorteert op startedAt aflopend`() = runTest {
        tripDao.insert(TestFixtures.trip(id = "older", startedAt = 1_000L))
        tripDao.insert(TestFixtures.trip(id = "newer", startedAt = 2_000L))

        val trips = tripDao.observeAll().first().map { it.id }

        assertEquals(listOf("newer", "older"), trips)
    }

    @Test
    fun `update overschrijft velden van bestaande rit`() = runTest {
        val trip = TestFixtures.trip(id = "trip-b", status = TripStatus.ACTIVE)
        tripDao.insert(trip)

        val completed = trip.copy(status = TripStatus.COMPLETED, endedAt = 5_000L, distanceM = 1200.0)
        tripDao.update(completed)

        val result = tripDao.getById("trip-b")
        assertEquals(TripStatus.COMPLETED, result?.status)
        assertEquals(1200.0, result?.distanceM)
    }

    @Test
    fun `delete verwijdert de rit`() = runTest {
        val trip = TestFixtures.trip(id = "trip-c")
        tripDao.insert(trip)

        tripDao.delete(trip)

        assertNull(tripDao.getById("trip-c"))
    }

    @Test
    fun `getByStatus filtert correct`() = runTest {
        tripDao.insert(TestFixtures.trip(id = "active-1", status = TripStatus.ACTIVE))
        tripDao.insert(TestFixtures.trip(id = "recoverable-1", status = TripStatus.RECOVERABLE))

        val recoverable = tripDao.getByStatus(TripStatus.RECOVERABLE)

        assertEquals(listOf("recoverable-1"), recoverable.map { it.id })
    }
}
