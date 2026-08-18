package nl.hiertoen.app.ui.screens.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.hiertoen.app.R
import nl.hiertoen.app.data.local.entity.TripEntity
import nl.hiertoen.app.data.local.entity.TripStatus
import nl.hiertoen.app.data.repository.RepositoryFactory
import nl.hiertoen.app.data.repository.TripRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Ritoverzicht — §4.4 (ritdetail volgt daarna). Toont alle lokaal opgeslagen ritten (§12.1:
 * local-first, geen account nodig) en opent bij een tik het ritdetail. Een rit die door een
 * crash of geforceerd afsluiten nooit netjes is beëindigd, blijft anders voor altijd op
 * ACTIVE/PAUSED staan — "Beëindigen" is de handmatige noodgreep daarvoor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(onBack: () -> Unit, onOpenTrip: (String) -> Unit) {
    val context = LocalContext.current
    val repository = remember { RepositoryFactory.tripRepository(context) }
    val trips by repository.observeTrips().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var tripToStop by remember { mutableStateOf<TripEntity?>(null) }

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
                    TripRow(
                        trip = trip,
                        onClick = { onOpenTrip(trip.id) },
                        onRequestStop = { tripToStop = trip },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    tripToStop?.let { trip ->
        AlertDialog(
            onDismissRequest = { tripToStop = null },
            title = { Text("Rit beëindigen?") },
            text = { Text("Deze rit staat nog op \"${statusLabel(trip.status)}\", vermoedelijk doordat hij niet netjes is afgesloten. Hij wordt afgerond met de laatst bekende gegevens.") },
            confirmButton = {
                TextButton(onClick = {
                    tripToStop = null
                    coroutineScope.launch { stopStuckTrip(repository, trip) }
                }) { Text("Beëindigen") }
            },
            dismissButton = { TextButton(onClick = { tripToStop = null }) { Text("Annuleren") } },
        )
    }
}

private suspend fun stopStuckTrip(repository: TripRepository, trip: TripEntity) {
    repository.saveTrip(trip.copy(status = TripStatus.COMPLETED, endedAt = System.currentTimeMillis()))
}

@Composable
private fun TripRow(trip: TripEntity, onClick: () -> Unit, onRequestStop: () -> Unit) {
    val isStuck = trip.status == TripStatus.ACTIVE || trip.status == TripStatus.PAUSED

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = trip.name ?: formatStartedAt(trip.startedAt), style = MaterialTheme.typography.titleLarge)
            Text(
                text = "%.1f km — %s".format(Locale.getDefault(), trip.distanceM / 1000.0, statusLabel(trip.status)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (isStuck) {
            OutlinedButton(onClick = onRequestStop, modifier = Modifier.padding(end = 16.dp)) {
                Text("Beëindigen")
            }
        }
    }
}

private fun statusLabel(status: TripStatus): String = when (status) {
    TripStatus.DRAFT -> "Concept"
    TripStatus.ACTIVE -> "Actief"
    TripStatus.PAUSED -> "Gepauzeerd"
    TripStatus.COMPLETED -> "Afgerond"
    TripStatus.RECOVERABLE -> "Te herstellen"
    TripStatus.DELETED_PENDING -> "Wordt verwijderd"
}

private fun formatStartedAt(timestampMs: Long): String {
    val time = Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault())
    return time.format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.getDefault()))
}
