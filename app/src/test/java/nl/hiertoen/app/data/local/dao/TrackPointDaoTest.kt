package nl.hiertoen.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.hiertoen.app.data.local.HierToenDatabase
import nl.hiertoen.app.data.local.TestFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TrackPointDaoTest {
    private lateinit var database: HierToenDatabase
    private lateinit var tripDao: TripDao
    private lateinit var trackPointDao: TrackPointDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HierToenDatabase::class.java,
        ).allowMainThreadQueries().build()
        tripDao = database.tripDao()
        trackPointDao = database.trackPointDao()

        // TrackPoint heeft een foreign key naar Trip; eerst de rit aanmaken.
        runTest { tripDao.insert(TestFixtures.trip(id = "trip-1")) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insertAll en getForTrip geven punten in tijdsvolgorde terug`() = runTest {
        val points = listOf(
            TestFixtures.trackPoint(id = "p2", timestamp = 2_000L),
            TestFixtures.trackPoint(id = "p1", timestamp = 1_000L),
            TestFixtures.trackPoint(id = "p3", timestamp = 3_000L),
        )
        trackPointDao.insertAll(points)

        val result = trackPointDao.getForTrip("trip-1")

        assertEquals(listOf("p1", "p2", "p3"), result.map { it.id })
    }

    @Test
    fun `countForTrip telt alleen punten van die rit`() = runTest {
        tripDao.insert(TestFixtures.trip(id = "trip-2", startedAt = 500L))
        trackPointDao.insertAll(
            listOf(
                TestFixtures.trackPoint(id = "a", tripId = "trip-1"),
                TestFixtures.trackPoint(id = "b", tripId = "trip-1"),
                TestFixtures.trackPoint(id = "c", tripId = "trip-2"),
            ),
        )

        assertEquals(2, trackPointDao.countForTrip("trip-1"))
        assertEquals(1, trackPointDao.countForTrip("trip-2"))
    }

    @Test
    fun `observeForTrip emit huidige punten`() = runTest {
        trackPointDao.insert(TestFixtures.trackPoint(id = "p1"))

        val result = trackPointDao.observeForTrip("trip-1").first()

        assertEquals(1, result.size)
        assertEquals("p1", result.first().id)
    }
}
