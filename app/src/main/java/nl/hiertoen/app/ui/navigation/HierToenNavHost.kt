package nl.hiertoen.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import nl.hiertoen.app.data.local.entity.TripMode
import nl.hiertoen.app.ui.screens.activetrip.ActiveTripScreen
import nl.hiertoen.app.ui.screens.settings.SettingsScreen
import nl.hiertoen.app.ui.screens.start.StartScreen
import nl.hiertoen.app.ui.screens.trips.TripsScreen

@Composable
fun HierToenNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = HierToenDestination.Start.route) {
        composable(HierToenDestination.Start.route) {
            StartScreen(
                onStartTrip = { mode -> navController.navigate(HierToenDestination.ActiveTrip.routeFor(mode)) },
                onOpenTrips = { navController.navigate(HierToenDestination.Trips.route) },
                onOpenSettings = { navController.navigate(HierToenDestination.Settings.route) },
            )
        }
        composable(HierToenDestination.Trips.route) {
            TripsScreen(onBack = { navController.popBackStack() })
        }
        composable(HierToenDestination.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = HierToenDestination.ActiveTrip.route,
            arguments = listOf(navArgument(HierToenDestination.ActiveTrip.ARG_MODE) { type = NavType.StringType }),
        ) { backStackEntry ->
            val modeArg = backStackEntry.arguments?.getString(HierToenDestination.ActiveTrip.ARG_MODE)
            val mode = modeArg?.let { runCatching { TripMode.valueOf(it) }.getOrNull() } ?: TripMode.CAR
            ActiveTripScreen(
                mode = mode,
                onTripEnded = {
                    navController.popBackStack(HierToenDestination.Start.route, inclusive = false)
                },
            )
        }
    }
}
