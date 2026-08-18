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

    data object ActiveTrip : HierToenDestination("activeTrip/{mode}?resumeTripId={resumeTripId}") {
        const val ARG_MODE = "mode"
        const val ARG_RESUME_TRIP_ID = "resumeTripId"
        fun routeFor(mode: TripMode) = "activeTrip/${mode.name}"
        fun routeForResume(tripId: String, mode: TripMode) = "activeTrip/${mode.name}?resumeTripId=$tripId"
    }

    data object TripDetail : HierToenDestination("trips/{tripId}") {
        const val ARG_TRIP_ID = "tripId"
        fun routeFor(tripId: String) = "trips/$tripId"
    }
}
