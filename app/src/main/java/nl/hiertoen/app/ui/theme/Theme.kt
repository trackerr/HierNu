package nl.hiertoen.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * HierToen is bewust altijd donker (§1.3: "donker, rustig"). Het rijscherm mag niet
 * verrassend van kleurschema wisselen op basis van het systeemthema, dus we bieden
 * geen lichte variant aan in de MVP.
 */
private val HierToenColorScheme = darkColorScheme(
    background = HierToenBackground,
    onBackground = HierToenOnBackground,
    surface = HierToenSurface,
    onSurface = HierToenOnBackground,
    surfaceVariant = HierToenSurfaceVariant,
    onSurfaceVariant = HierToenOnSurfaceMuted,
    primary = HierToenAccent,
    onPrimary = HierToenOnAccent,
    primaryContainer = HierToenAccentContainer,
    onPrimaryContainer = HierToenAccent,
    outline = HierToenOutline,
)

@Composable
fun HierToenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HierToenColorScheme,
        typography = HierToenTypography,
        content = content,
    )
}
