package nl.hiertoen.app.ui.screens.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.hiertoen.app.R
import nl.hiertoen.app.data.local.entity.TripEntity
import nl.hiertoen.app.data.repository.RepositoryFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Ritoverzicht — §4.4 (ritdetail volgt daarna). Toont alle lokaal opgeslagen ritten (§12.1:
 * local-first, geen account nodig) en opent bij een tik het ritdetail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(onBack: () -> Unit, onOpenTrip: (String) -> Unit) {
    val context = LocalContext.current
    val repository = remember { RepositoryFactory.tripRepository(context) }
    val trips by repository.observeTrips().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trips_screen_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
            )
        },
    ) { padding ->
        if (trips.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = stringResource(R.string.trips_screen_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(trips) { trip ->
                    TripRow(trip = trip, onClick = { onOpenTrip(trip.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TripRow(trip: TripEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = trip.name ?: formatStartedAt(trip.startedAt), style = MaterialTheme.typography.titleLarge)
        Text(
            text = "%.1f km — %s".format(Locale.getDefault(), trip.distanceM / 1000.0, trip.status.name),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatStartedAt(timestampMs: Long): String {
    val time = Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault())
    return time.format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.getDefault()))
}
