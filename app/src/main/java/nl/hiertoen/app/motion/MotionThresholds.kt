package nl.hiertoen.app.motion

/**
 * Startwaarden per §5.1/§5.4. Het document benoemt deze zelf als kalibratie-aannames die
 * veldtests vereisen — daarom hier gecentraliseerd en instelbaar (§17.3), niet verspreid
 * over de code.
 */
data class MotionThresholds(
    val movingSpeedKmh: Double = 8.0,
    val movingSustainMs: Long = 3_000L,
    val stillSpeedKmh: Double = 1.5,
    val stopDelayMs: Long = 4_000L,
    val stopDisplacementM: Double = 8.0,
    val resumeSpeedKmh: Double = 3.0,
    val resumeDisplacementM: Double = 10.0,
    val maxAccuracyM: Double = 35.0,
) {
    companion object {
        /** Kalibratieprofiel "Auto" — §5.4. */
        val CAR = MotionThresholds()

        /** Kalibratieprofiel "Fiets" — zelfde stopvertraging, kleinere afstanden i.v.m. snellere koersverandering. */
        val BICYCLE = MotionThresholds(
            stopDisplacementM = 5.0,
            resumeDisplacementM = 6.0,
        )
    }
}
