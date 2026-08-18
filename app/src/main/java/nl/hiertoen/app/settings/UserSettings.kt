package nl.hiertoen.app.settings

/**
 * Alleen instellingen die daadwerkelijk gedrag beïnvloeden — §11 noemt ook toggles voor
 * bronnen die nog niet bestaan (Mapillary, Google fallback, archiefbronnen, cloudback-up,
 * automatische ritdetectie). Die laten we bewust weg: een schakelaar die niets doet is
 * misleidend, geen MVP-functionaliteit.
 */
data class UserSettings(
    val autoPhotoEnabled: Boolean = true,
    val stopDelaySeconds: Int = 4,
    val showSpeed: Boolean = true,
    val showDistanceTime: Boolean = true,
    val searchRadiusM: Int = 100,
    val preferOldest: Boolean = true,
    val wikimediaEnabled: Boolean = true,
    val mobileDataAllowed: Boolean = true,
    /** null = onbeperkt bewaren. */
    val routeRetentionDays: Int? = null,
) {
    companion object {
        val STOP_DELAY_OPTIONS = listOf(2, 4, 6, 8)
        val SEARCH_RADIUS_OPTIONS = listOf(50, 100, 250, 500)
        val RETENTION_OPTIONS = listOf(7, 30, 90, null)
    }
}
