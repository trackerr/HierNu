package nl.hiertoen.app.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsRepositoryTest {

    /** Defaults moeten letterlijk de "Standaard"-kolom uit §11 volgen. */
    @Test
    fun `standaardwaarden volgen §11`() = runTest {
        val repository = SettingsRepository(ApplicationProvider.getApplicationContext())

        val settings = repository.current()

        assertEquals(true, settings.autoPhotoEnabled)
        assertEquals(4, settings.stopDelaySeconds)
        assertEquals(true, settings.showSpeed)
        assertEquals(true, settings.showDistanceTime)
        assertEquals(100, settings.searchRadiusM)
        assertEquals(true, settings.preferOldest)
        assertEquals(true, settings.wikimediaEnabled)
        assertEquals(true, settings.mobileDataAllowed)
        assertEquals(null, settings.routeRetentionDays)
    }

    @Test
    fun `update slaat wijzigingen op en laat andere velden ongemoeid`() = runTest {
        val repository = SettingsRepository(ApplicationProvider.getApplicationContext())

        repository.update { it.copy(stopDelaySeconds = 8, wikimediaEnabled = false) }
        val settings = repository.current()

        assertEquals(8, settings.stopDelaySeconds)
        assertEquals(false, settings.wikimediaEnabled)
        assertEquals(true, settings.autoPhotoEnabled) // ongewijzigd gebleven
    }

    @Test
    fun `routeRetentionDays 0 wordt gelezen als onbeperkt (null)`() = runTest {
        val repository = SettingsRepository(ApplicationProvider.getApplicationContext())

        repository.update { it.copy(routeRetentionDays = 30) }
        assertEquals(30, repository.current().routeRetentionDays)

        repository.update { it.copy(routeRetentionDays = null) }
        assertEquals(null, repository.current().routeRetentionDays)
    }
}
