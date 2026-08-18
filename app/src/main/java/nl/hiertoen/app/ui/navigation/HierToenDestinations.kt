package nl.hiertoen.app.ui.navigation

import nl.hiertoen.app.data.local.entity.TripMode

/**
 * Topniveau-schermen van de MVP. De fotoweergave (§4.3) volgt in stap 7 als overlay op
 * het rijscherm, geen aparte route.
 */
sealed class HierToenDestination(val route: String) {
    data object Start : HierToenDestination("start")
    data object Trips : HierToenDestination("trips")
    data object Settings : HierToenDestination("settings")

    data object ActiveTrip : HierToenDestination("activeTrip/{mode}") {
        const val ARG_MODE = "mode"
        fun routeFor(mode: TripMode) = "activeTrip/${mode.name}"
    }
}
