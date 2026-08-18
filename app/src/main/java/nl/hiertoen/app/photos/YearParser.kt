package nl.hiertoen.app.photos

/**
 * Wikimedia-datummetadata is vrije tekst ("1936", "1910-05-02", "circa 1920s", soms leeg).
 * We proberen alleen een plausibel jaartal (1500-2099) te herkennen; bij twijfel geven we
 * niets terug. Nooit een jaartal verzinnen — §4.3, §7.4.
 */
object YearParser {
    // Optionele 's' zodat decennium-notaties ("1920s") ook het jaartal opleveren.
    private val YEAR_REGEX = Regex("""\b(1[5-9]\d{2}|20\d{2})s?\b""")

    fun extractYear(rawDate: String?): Int? {
        if (rawDate.isNullOrBlank()) return null
        val match = YEAR_REGEX.find(rawDate) ?: return null
        return match.groupValues[1].toIntOrNull()
    }
}
