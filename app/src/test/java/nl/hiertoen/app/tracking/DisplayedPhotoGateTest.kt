package nl.hiertoen.app.tracking

import nl.hiertoen.app.motion.MotionState
import nl.hiertoen.app.photos.PhotoSearchService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * §12.3/§14.4: de foto mag nooit zichtbaar zijn buiten STILL. Dit is de veiligheidsregel van
 * de hele fotoweergave-stap, dus expliciet getest tegen elke MotionState, niet alleen MOVING.
 */
class DisplayedPhotoGateTest {

    private val photo = DisplayedPhotoInfo(
        momentId = "m1",
        title = "File:Test.jpg",
        imageUrl = "https://example.org/full.jpg",
        thumbUrl = "https://example.org/thumb.jpg",
        year = 1936,
        attribution = "Jane Doe — CC BY-SA 4.0 (Wikimedia Commons)",
        distanceM = 12.0,
        provider = PhotoSearchService.PROVIDER_WIKIMEDIA,
        sourcePageUrl = "https://commons.wikimedia.org/wiki/File:Test.jpg",
    )

    @Test
    fun `laat de foto alleen door bij STILL`() {
        assertEquals(photo, displayedPhotoFor(MotionState.STILL, photo))
    }

    @Test
    fun `verbergt de foto bij elke andere status, ook als er een cache is`() {
        val nonStillStates = MotionState.entries.filter { it != MotionState.STILL }
        for (state in nonStillStates) {
            assertNull("verwacht null bij $state", displayedPhotoFor(state, photo))
        }
    }

    @Test
    fun `geeft null terug als er niets gecachet is, ook bij STILL`() {
        assertNull(displayedPhotoFor(MotionState.STILL, null))
    }
}
