package nl.hiertoen.app.ui.screens.tripdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import nl.hiertoen.app.data.local.entity.MomentState
import nl.hiertoen.app.data.local.entity.MomentType
import nl.hiertoen.app.data.local.entity.TripEntity
import nl.hiertoen.app.data.local.entity.TripMomentEntity
import nl.hiertoen.app.data.repository.RepositoryFactory
import nl.hiertoen.app.data.repository.TripRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Ritdetail — §4.4. Kaart, route en export volgen in stap 8; deze eerste versie levert het
 * stopcriterium van stap 5 ("marker zichtbaar in ritdetail") met een samenvatting en een
 * tijdlijn van momenten.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(tripId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { RepositoryFactory.tripRepository(context) }

    var trip by remember { mutableStateOf<TripEntity?>(null) }
    LaunchedEffect(tripId) { trip = repository.getTrip(tripId) }
    val moments by repository.observeMoments(tripId).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip?.name ?: "Rit") },
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { trip?.let { TripSummaryCard(it) } }
            item {
                Text(
                    text = "Momenten",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (moments.isEmpty()) {
                item { Text("Nog geen momenten opgeslagen.", style = MaterialTheme.typography.bodyLarge) }
            } else {
                items(moments) { moment ->
                    MomentRow(moment, repository)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TripSummaryCard(trip: TripEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                SummaryStat(label = "Afstand", value = "%.1f km".format(Locale.getDefault(), trip.distanceM / 1000.0))
                SummaryStat(label = "Rijtijd", value = formatDuration(trip.movingMs))
                SummaryStat(label = "Stiltijd", value = formatDuration(trip.stoppedMs))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 8.dp)) {
                SummaryStat(label = "Gem. snelheid", value = "%.0f km/u".format(Locale.getDefault(), trip.avgSpeedKmh))
                SummaryStat(label = "Max snelheid", value = "%.0f km/u".format(Locale.getDefault(), trip.maxSpeedKmh))
                SummaryStat(label = "Status", value = trip.status.name)
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column {
        Text(text = value, style = MaterialTheme.typography.titleLarge)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MomentRow(moment: TripMomentEntity, repository: TripRepository) {
    val candidates by repository.observeCandidates(moment.id).collectAsState(initial = emptyList())
    val best = candidates.maxByOrNull { it.score }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = momentTypeLabel(moment.type), style = MaterialTheme.typography.titleLarge)
                Text(text = formatTime(moment.timestamp), style = MaterialTheme.typography.bodyMedium)
            }
            Text(text = momentStateLabel(moment.state), style = MaterialTheme.typography.bodyMedium)
        }
        // Stopcriterium §17.1 stap 6: bron, jaar en licentie zichtbaar.
        if (moment.state == MomentState.PHOTO_SHOWN && best != null) {
            Text(
                text = "${best.title} — ${yearLabel(best.yearFrom)} — ${best.license ?: "onbekende licentie"}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(text = best.attribution, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun yearLabel(year: Int?): String = year?.toString() ?: "datum onbekend"

private fun momentTypeLabel(type: MomentType): String = when (type) {
    MomentType.MANUAL_BOOKMARK -> "Handmatig bewaard"
    MomentType.AUTO_STOP -> "Automatische stop"
    MomentType.LONG_STOP -> "Lange stop"
    MomentType.ROUTE_GAP -> "Onderbreking in route"
}

private fun momentStateLabel(state: MomentState): String = when (state) {
    MomentState.NO_IMAGE_YET -> "Geen beeld (nog)"
    MomentState.PENDING_LOOKUP -> "Zoeken…"
    MomentState.PHOTO_SHOWN -> "Beeld getoond"
    MomentState.PHOTO_NOT_FOUND -> "Geen beeld gevonden"
}

private fun formatTime(timestampMs: Long): String {
    val time = Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault())
    return time.format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault()))
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}u ${minutes}m" else "${minutes}m"
}
