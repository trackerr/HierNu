package nl.hiertoen.app.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YearParserTest {
    @Test
    fun `herkent een los jaartal`() {
        assertEquals(1936, YearParser.extractYear("1936"))
    }

    @Test
    fun `herkent een jaartal in een volledige datum`() {
        assertEquals(1910, YearParser.extractYear("1910-05-02"))
    }

    @Test
    fun `herkent een jaartal in vrije tekst`() {
        assertEquals(1920, YearParser.extractYear("circa 1920s, photographer unknown"))
    }

    @Test
    fun `geeft null bij ontbrekende datum`() {
        assertNull(YearParser.extractYear(null))
        assertNull(YearParser.extractYear(""))
        assertNull(YearParser.extractYear("   "))
    }

    @Test
    fun `geeft null bij tekst zonder plausibel jaartal`() {
        assertNull(YearParser.extractYear("onbekend"))
        assertNull(YearParser.extractYear("scan #4821"))
    }

    @Test
    fun `verzint geen jaartal buiten het plausibele bereik`() {
        assertNull(YearParser.extractYear("negatief 0099"))
    }
}
