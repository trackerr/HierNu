package nl.hiertoen.app.ui.screens.activetrip

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import nl.hiertoen.app.R
import nl.hiertoen.app.data.local.entity.TripMode
import nl.hiertoen.app.data.local.entity.TripStatus
import nl.hiertoen.app.motion.MotionState
import nl.hiertoen.app.settings.SettingsRepository
import nl.hiertoen.app.settings.UserSettings
import nl.hiertoen.app.tracking.DisplayedPhotoInfo
import nl.hiertoen.app.tracking.TrackingSessionState
import java.util.Locale

/**
 * Rijscherm — §4.2. Rustig tijdens beweging: geen foto, geen complexe bediening. Zodra
 * TrackingService een foto vrijgeeft (alleen bij STILL, §12.3) verschijnt de fotoweergave
 * (§4.3) als overlay; ze sluit zonder animatie of bevestiging zodra beweging hervat — de
 * StateFlow-update van de service is daarvoor voldoende, deze compositie voegt zelf geen
 * animatie toe.
 */
@Composable
fun ActiveTripScreen(mode: TripMode, onTripEnded: () -> Unit, resumeTripId: String? = null) {
    val (handle, sessionState) = rememberTrackingServiceHandle(mode, resumeTripId)
    val session = sessionState.value

    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val settings by settingsRepository.settings.collectAsState(initial = UserSettings())

    var showStopConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.active_trip_place_saved)
    val notSavedMessage = stringResource(R.string.active_trip_place_not_saved)

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (session) {
                    is TrackingSessionState.Active -> ActiveTripContent(session, settings)
                    TrackingSessionState.NoActiveTrip -> Text(
                        text = "Rit wordt gestart…",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val saved = handle.saveCurrentPlace()
                                snackbarHostState.showSnackbar(if (saved) savedMessage else notSavedMessage)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                    ) {
                        Text(stringResource(R.string.active_trip_save_place), style = MaterialTheme.typography.titleLarge)
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        val controlsEnabled = (session as? TrackingSessionState.Active)?.motionState?.let {
                            it != MotionState.MOVING && it != MotionState.SLOW
                        } ?: true

                        val isPaused = (session as? TrackingSessionState.Active)?.status == TripStatus.PAUSED

                        OutlinedButton(
                            enabled = controlsEnabled,
                            onClick = { if (isPaused) handle.resume() else handle.pause() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(if (isPaused) R.string.active_trip_resume else R.string.active_trip_pause))
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        OutlinedButton(
                            enabled = controlsEnabled,
                            onClick = { showStopConfirm = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.active_trip_stop))
                        }
                    }
                }
            }

            // Alleen op de voorgrond wanneer de service 'm doorlaat (STILL) — geen eigen
            // AnimatedVisibility hier, dat zou de "geen animatie bij hervatte beweging"-eis breken.
            val photo = (session as? TrackingSessionState.Active)?.displayedPhoto
            if (photo != null) {
                PhotoOverlay(photo)
            }
        }
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text(stringResource(R.string.active_trip_stop_confirm_title)) },
            text = { Text(stringResource(R.string.active_trip_stop_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    handle.stop()
                    onTripEnded()
                }) { Text(stringResource(R.string.active_trip_stop)) }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) { Text(stringResource(R.string.permission_rationale_dismiss)) }
            },
        )
    }
}

@Composable
private fun ActiveTripContent(session: TrackingSessionState.Active, settings: UserSettings) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = motionStateLabel(session.motionState), style = MaterialTheme.typography.titleLarge)

        if (settings.showSpeed) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "%.0f".format(Locale.getDefault(), session.currentSpeedKmh),
                style = MaterialTheme.typography.displayLarge,
            )
            Text(text = "km/u", style = MaterialTheme.typography.bodyMedium)
        }

        if (settings.showDistanceTime) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "%.1f km".format(Locale.getDefault(), session.distanceM / 1000.0), style = MaterialTheme.typography.titleLarge)
                    Text(text = "afstand", style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = formatDuration(session.elapsedMs), style = MaterialTheme.typography.titleLarge)
                    Text(text = "tijd", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * Fotoweergave bij stilstand — §4.3. Vult het grootste deel van het scherm, groot jaartal
 * (of expliciet "Datum onbekend" — nooit verzonnen), "Deze plek", afstand en bron.
 */
@Composable
private fun PhotoOverlay(photo: DisplayedPhotoInfo) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painter = rememberAsyncImagePainter(photo.imageUrl),
            contentDescription = photo.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                .padding(24.dp),
        ) {
            Text(
                text = photo.year?.toString() ?: "Datum onbekend",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
            )
            Text(text = "Deze plek", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                text = "%.0f m van de fotopositie — %s".format(Locale.getDefault(), photo.distanceM, photo.attribution),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
    }
}

private fun motionStateLabel(state: MotionState): String = when (state) {
    MotionState.MOVING -> "Rijden"
    MotionState.SLOW -> "Langzaam"
    MotionState.STOP_CANDIDATE -> "Mogelijk stil"
    MotionState.STILL -> "Stil"
    MotionState.PAUSED -> "Gepauzeerd"
    MotionState.GPS_UNRELIABLE -> "GPS onnauwkeurig"
    MotionState.IDLE -> "—"
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
