package nl.hiertoen.app.ui.screens.tripdetail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.hiertoen.app.data.local.entity.MomentState
import nl.hiertoen.app.data.local.entity.MomentType
import nl.hiertoen.app.data.local.entity.PhotoCandidateEntity
import nl.hiertoen.app.data.local.entity.TrackPointEntity
import nl.hiertoen.app.data.local.entity.TrackPointValidity
import nl.hiertoen.app.data.local.entity.TripEntity
import nl.hiertoen.app.data.local.entity.TripMomentEntity
import nl.hiertoen.app.data.repository.RepositoryFactory
import nl.hiertoen.app.data.repository.TripRepository
import nl.hiertoen.app.export.GeoJsonExporter
import nl.hiertoen.app.export.GpxExporter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos

/**
 * Ritdetail — §4.4. Kaart met route/markers wordt hier bewust een lichte, kaartprovider-loze
 * Canvas-schets: §18 laat de kaartprovider nog als open beslispunt staan, dus een echte
 * MapLibre/Maps-SDK-integratie zou dat beslispunt ongevraagd voor de gebruiker maken.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(tripId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { RepositoryFactory.tripRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    var trip by remember { mutableStateOf<TripEntity?>(null) }
    LaunchedEffect(tripId) { trip = repository.getTrip(tripId) }
    val moments by repository.observeMoments(tripId).collectAsState(initial = emptyList())
    val trackPoints by repository.observeTrackPoints(tripId).collectAsState(initial = emptyList())

    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val gpxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null) {
            coroutineScope.launch { exportTrip(context, repository, tripId, uri, isGpx = true) }
        }
    }
    val geoJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/geo+json")) { uri ->
        if (uri != null) {
            coroutineScope.launch { exportTrip(context, repository, tripId, uri, isGpx = false) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip?.name ?: "Rit") },
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
                actions = {
                    IconButton(onClick = { showMenu = true }) { Text("⋮") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Hernoemen") }, onClick = { showMenu = false; showRenameDialog = true })
                        DropdownMenuItem(
                            text = { Text("Exporteer GPX") },
                            onClick = { showMenu = false; gpxLauncher.launch("${suggestedFileName(trip)}.gpx") },
                        )
                        DropdownMenuItem(
                            text = { Text("Exporteer GeoJSON") },
                            onClick = { showMenu = false; geoJsonLauncher.launch("${suggestedFileName(trip)}.geojson") },
                        )
                        DropdownMenuItem(text = { Text("Verwijderen") }, onClick = { showMenu = false; showDeleteConfirm = true })
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { trip?.let { TripSummaryCard(it) } }
            item { RoutePreview(trackPoints, moments) }
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

    if (showRenameDialog) {
        RenameDialog(
            initialName = trip?.name ?: "",
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                coroutineScope.launch {
                    trip?.let {
                        val updated = it.copy(name = newName.ifBlank { null })
                        repository.saveTrip(updated)
                        trip = updated
                    }
                }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Rit verwijderen?") },
            text = { Text("Deze rit en alle bijbehorende punten en momenten worden definitief verwijderd.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    coroutineScope.launch {
                        trip?.let { repository.deleteTrip(it) }
                        onBack()
                    }
                }) { Text("Verwijderen") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Annuleren") } },
        )
    }
}

private fun suggestedFileName(trip: TripEntity?): String = (trip?.name ?: "hiertoen-rit").replace(Regex("[^A-Za-z0-9_-]"), "_")

private suspend fun exportTrip(context: android.content.Context, repository: TripRepository, tripId: String, uri: Uri, isGpx: Boolean) {
    val trip = repository.getTrip(tripId) ?: return
    val points = repository.observeTrackPoints(tripId).first()
    val moments = repository.observeMoments(tripId).first()

    val content = if (isGpx) {
        GpxExporter.export(trip, points, moments)
    } else {
        val bestByMoment = mutableMapOf<String, PhotoCandidateEntity>()
        for (moment in moments) {
            repository.observeCandidates(moment.id).first().maxByOrNull { it.score }?.let { bestByMoment[moment.id] = it }
        }
        GeoJsonExporter.export(trip, points, moments, bestByMoment)
    }

    withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
    }
}

@Composable
private fun RenameDialog(initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rit hernoemen") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text("Opslaan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
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

/**
 * Schematische routeweergave zonder kaartprovider (§18 nog open): een eigen Canvas-projectie
 * i.p.v. echte kaarttegels. Start (groen), eind (rood) en momenten (accentkleur) als stippen.
 */
@Composable
private fun RoutePreview(trackPoints: List<TrackPointEntity>, moments: List<TripMomentEntity>) {
    val validPoints = trackPoints.filter { it.validity == TrackPointValidity.VALID }
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val routeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    if (validPoints.size < 2) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Nog geen route om te tonen.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    val minLat = validPoints.minOf { it.lat }
    val maxLat = validPoints.maxOf { it.lat }
    val minLon = validPoints.minOf { it.lon }
    val maxLon = validPoints.maxOf { it.lon }
    val latSpan = (maxLat - minLat).coerceAtLeast(0.0001)
    val lonScale = cos(Math.toRadians((minLat + maxLat) / 2.0)).coerceAtLeast(0.2)
    val lonSpan = ((maxLon - minLon) * lonScale).coerceAtLeast(0.0001)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor),
    ) {
        val pad = 16.dp.toPx()
        val width = size.width - 2 * pad
        val height = size.height - 2 * pad

        fun project(lat: Double, lon: Double): Offset {
            val x = pad + (((lon - minLon) * lonScale) / lonSpan * width).toFloat()
            val y = pad + ((1.0 - (lat - minLat) / latSpan) * height).toFloat()
            return Offset(x, y)
        }

        validPoints.groupBy { it.segmentIndex }.forEach { (_, segmentPoints) ->
            if (segmentPoints.size < 2) return@forEach
            val path = Path()
            segmentPoints.forEachIndexed { index, point ->
                val offset = project(point.lat, point.lon)
                if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
            }
            drawPath(path, color = routeColor, style = Stroke(width = 5f))
        }

        drawCircle(color = Color(0xFF4CAF50), radius = 9f, center = project(validPoints.first().lat, validPoints.first().lon))
        drawCircle(color = Color(0xFFE53935), radius = 9f, center = project(validPoints.last().lat, validPoints.last().lon))
        moments.forEach { moment -> drawCircle(color = accentColor, radius = 7f, center = project(moment.lat, moment.lon)) }
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
