package nl.hiertoen.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import nl.hiertoen.app.ui.screens.settings.SettingsScreen
import nl.hiertoen.app.ui.screens.start.StartScreen
import nl.hiertoen.app.ui.screens.trips.TripsScreen

@Composable
fun HierToenNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = HierToenDestination.Start.route) {
        composable(HierToenDestination.Start.route) {
            StartScreen(
                onStartTrip = { /* volgt in stap 3: TrackingService + actieve-rit UI */ },
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
    }
}
