package nl.hiertoen.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.hiertoen.app.data.local.HierToenDatabase
import nl.hiertoen.app.data.local.TestFixtures
import nl.hiertoen.app.data.local.entity.MomentState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TripMomentDaoTest {
    private lateinit var database: HierToenDatabase
    private lateinit var tripDao: TripDao
    private lateinit var momentDao: TripMomentDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HierToenDatabase::class.java,
        ).allowMainThreadQueries().build()
        tripDao = database.tripDao()
        momentDao = database.tripMomentDao()

        runTest { tripDao.insert(TestFixtures.trip(id = "trip-1")) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert en observeForTrip geven het moment terug`() = runTest {
        momentDao.insert(TestFixtures.tripMoment(id = "m1"))

        val moments = momentDao.observeForTrip("trip-1").first()

        assertEquals(1, moments.size)
        assertEquals("m1", moments.first().id)
        assertEquals(MomentState.NO_IMAGE_YET, moments.first().state)
    }

    @Test
    fun `update wijzigt de status van een bestaand moment`() = runTest {
        val moment = TestFixtures.tripMoment(id = "m1")
        momentDao.insert(moment)

        momentDao.update(moment.copy(state = MomentState.PHOTO_SHOWN, source = "wikimedia"))

        val result = momentDao.getById("m1")
        assertEquals(MomentState.PHOTO_SHOWN, result?.state)
        assertEquals("wikimedia", result?.source)
    }

    @Test
    fun `getById op onbekend moment geeft null`() = runTest {
        assertNull(momentDao.getById("does-not-exist"))
    }

    @Test
    fun `moments van een verwijderde rit verdwijnen mee (cascade)`() = runTest {
        momentDao.insert(TestFixtures.tripMoment(id = "m1"))
        val trip = tripDao.getById("trip-1")!!

        tripDao.delete(trip)

        assertNull(momentDao.getById("m1"))
    }
}
