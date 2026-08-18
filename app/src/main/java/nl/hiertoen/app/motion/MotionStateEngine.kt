package nl.hiertoen.app.motion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nl.hiertoen.app.core.ActivityType
import nl.hiertoen.app.core.GeoMath

/**
 * Statusmachine voor rijden/stilstand — §5. Volgt de referentie-pseudocode uit §5.3 op de voet:
 * GPS-betrouwbaarheid gaat voor alles, MOVING wint van STILL, en STILL kent een eigen
 * hysterese-anker zodat GPS-drift niet meteen als hervatte beweging telt (§5.2).
 *
 * Puur Kotlin, geen Android-afhankelijkheden — zo blijft dit met synthetische snelheidsreeksen
 * te testen zonder emulator (§14.1, §17.3: deterministische fixtures).
 */
class MotionStateEngine(val thresholds: MotionThresholds = MotionThresholds.CAR) {

    private val _state = MutableStateFlow(MotionState.IDLE)
    val state: StateFlow<MotionState> = _state

    val currentState: MotionState get() = _state.value

    private var highSpeedSince: Long? = null
    private var stopCandidateSince: Long? = null
    private var stopAnchor: Pair<Double, Double>? = null
    private var stillAnchor: Pair<Double, Double>? = null

    fun start() {
        resetStopTracking()
        highSpeedSince = null
        setState(MotionState.STOP_CANDIDATE)
    }

    fun pause() {
        setState(MotionState.PAUSED)
    }

    fun resume() {
        resetStopTracking()
        highSpeedSince = null
        setState(MotionState.STOP_CANDIDATE)
    }

    fun stop() {
        resetStopTracking()
        highSpeedSince = null
        setState(MotionState.IDLE)
    }

    /** Verwerkt één sensormeting en geeft de resulterende status terug. */
    fun update(input: MotionInput): MotionState {
        if (_state.value == MotionState.PAUSED || _state.value == MotionState.IDLE) {
            return _state.value
        }

        if (input.accuracyM > thresholds.maxAccuracyM) {
            return setState(MotionState.GPS_UNRELIABLE)
        }

        if (input.speedKmh > thresholds.movingSpeedKmh) {
            if (highSpeedSince == null) highSpeedSince = input.timestampMs
        } else {
            highSpeedSince = null
        }
        val sustainedFast = highSpeedSince?.let { input.timestampMs - it >= thresholds.movingSustainMs } ?: false
        val activityImpliesMoving = input.activityType == ActivityType.IN_VEHICLE ||
            input.activityType == ActivityType.ON_BICYCLE

        if (sustainedFast || activityImpliesMoving) {
            resetStopTracking()
            return setState(MotionState.MOVING)
        }

        // §5.1 (statusmodel) is hier leidend boven de vereenvoudigde §5.3-pseudocode: STILL kent
        // precies één uitgangsvoorwaarde ("snelheid > 3 km/u of > 10 m verplaatst"). Zou een nieuwe
        // trage meting (<1.5 km/u) altijd terug de stopkandidaat-tak in gaan, dan zou het strengere
        // stopDisplacementM-anker (8 m) een lang geparkeerde rit bij toevallige GPS-drift alsnog naar
        // STOP_CANDIDATE terugzetten — precies de flikkering die de hysterese moet voorkomen.
        if (_state.value == MotionState.STILL) {
            val displacementFromStillAnchor = distanceMeters(stillAnchor, input)
            return if (input.speedKmh > thresholds.resumeSpeedKmh || displacementFromStillAnchor > thresholds.resumeDisplacementM) {
                resetStopTracking()
                setState(MotionState.MOVING)
            } else {
                _state.value
            }
        }

        if (input.speedKmh < thresholds.stillSpeedKmh) {
            if (stopCandidateSince == null || stopAnchor == null) {
                stopCandidateSince = input.timestampMs
                stopAnchor = input.lat to input.lon
            }

            val displacementFromStopAnchor = distanceMeters(stopAnchor, input)
            if (displacementFromStopAnchor >= thresholds.stopDisplacementM) {
                // Te veel verplaatst tijdens de dwell-periode: nieuwe stopkandidaat vanaf hier.
                stopCandidateSince = input.timestampMs
                stopAnchor = input.lat to input.lon
                return setState(MotionState.STOP_CANDIDATE)
            }

            val stableForMs = input.timestampMs - stopCandidateSince!!
            return if (stableForMs >= thresholds.stopDelayMs) {
                stillAnchor = stopAnchor
                setState(MotionState.STILL)
            } else {
                setState(MotionState.STOP_CANDIDATE)
            }
        }

        resetStopTracking()
        return setState(MotionState.SLOW)
    }

    private fun resetStopTracking() {
        stopCandidateSince = null
        stopAnchor = null
        stillAnchor = null
    }

    private fun distanceMeters(anchor: Pair<Double, Double>?, input: MotionInput): Double {
        if (anchor == null) return 0.0
        return GeoMath.haversineMeters(anchor.first, anchor.second, input.lat, input.lon)
    }

    private fun setState(newState: MotionState): MotionState {
        _state.value = newState
        return newState
    }
}
