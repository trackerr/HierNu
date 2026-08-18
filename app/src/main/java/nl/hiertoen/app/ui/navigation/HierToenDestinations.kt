package nl.hiertoen.app.ui.navigation

/**
 * Topniveau-schermen van de MVP. Het actieve rijscherm (§4.2) en de fotoweergave (§4.3)
 * volgen in stap 3 van de bouwvolgorde en krijgen dan hun eigen route(s) met tripId-argument.
 */
sealed class HierToenDestination(val route: String) {
    data object Start : HierToenDestination("start")
    data object Trips : HierToenDestination("trips")
    data object Settings : HierToenDestination("settings")
}
