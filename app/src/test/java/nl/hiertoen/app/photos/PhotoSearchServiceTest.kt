package nl.hiertoen.app.photos

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.hiertoen.app.data.local.HierToenDatabase
import nl.hiertoen.app.data.local.TestFixtures
import nl.hiertoen.app.data.local.entity.MomentState
import nl.hiertoen.app.data.repository.TripRepository
import nl.hiertoen.app.data.repository.TripRepositoryImpl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private class FakeWikimediaClient(private val results: List<WikimediaCandidate>) : WikimediaClient {
    override suspend fun search(lat: Double, lon: Double, radiusM: Int, limit: Int): List<WikimediaCandidate> = results
}

private fun rawCandidate(license: String? = "CC BY-SA 4.0", rawDate: String? = "1936", lat: Double = 52.2215, lon: Double = 6.8937) =
    WikimediaCandidate(
        pageId = "1",
        title = "File:Test.jpg",
        imageUrl = "https://example.org/full.jpg",
        sourcePageUrl = "https://commons.wikimedia.org/wiki/File:Test.jpg",
        thumbUrl = "https://example.org/thumb.jpg",
        lat = lat,
        lon = lon,
        rawDate = rawDate,
        license = license,
        author = "Jane Doe",
        description = null,
        widthPx = 3000,
        heightPx = 2000,
        headingDeg = null,
    )

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PhotoSearchServiceTest {
    private lateinit var database: HierToenDatabase
    private lateinit var repository: TripRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HierToenDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = TripRepositoryImpl(
            database.tripDao(),
            database.trackPointDao(),
            database.tripMomentDao(),
            database.photoCandidateDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `resultaat met bruikbare licentie zet moment op PHOTO_SHOWN en bewaart de kandidaat`() = runTest {
        repository.saveTrip(TestFixtures.trip())
        val moment = TestFixtures.tripMoment(id = "m1")
        repository.saveMoment(moment)

        val service = PhotoSearchService(FakeWikimediaClient(listOf(rawCandidate())), repository)
        service.searchForMoment(moment)

        val updated = repository.observeMoments("trip-1").first().first { it.id == "m1" }
        assertEquals(MomentState.PHOTO_SHOWN, updated.state)
        assertEquals("wikimedia", updated.source)

        val candidates = repository.observeCandidates("m1").first()
        assertEquals(1, candidates.size)
        assertEquals(1936, candidates.first().yearFrom)
    }

    @Test
    fun `geen resultaten zet moment op PHOTO_NOT_FOUND`() = runTest {
        repository.saveTrip(TestFixtures.trip())
        val moment = TestFixtures.tripMoment(id = "m2")
        repository.saveMoment(moment)

        val service = PhotoSearchService(FakeWikimediaClient(emptyList()), repository)
        service.searchForMoment(moment)

        val updated = repository.observeMoments("trip-1").first().first { it.id == "m2" }
        assertEquals(MomentState.PHOTO_NOT_FOUND, updated.state)
        assertNull(updated.source)
    }

    @Test
    fun `kandidaten zonder licentie worden niet bewaard en tellen niet mee`() = runTest {
        repository.saveTrip(TestFixtures.trip())
        val moment = TestFixtures.tripMoment(id = "m3")
        repository.saveMoment(moment)

        val service = PhotoSearchService(FakeWikimediaClient(listOf(rawCandidate(license = null))), repository)
        service.searchForMoment(moment)

        val updated = repository.observeMoments("trip-1").first().first { it.id == "m3" }
        assertEquals(MomentState.PHOTO_NOT_FOUND, updated.state)
        assertTrue(repository.observeCandidates("m3").first().isEmpty())
    }
}
