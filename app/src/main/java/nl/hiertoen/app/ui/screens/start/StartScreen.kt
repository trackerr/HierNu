package nl.hiertoen.app.ui.screens.start

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.hiertoen.app.R
import nl.hiertoen.app.data.local.entity.TripEntity
import nl.hiertoen.app.data.local.entity.TripMode
import nl.hiertoen.app.data.local.entity.TripStatus
import nl.hiertoen.app.data.repository.RepositoryFactory
import nl.hiertoen.app.data.repository.TripRepository
import nl.hiertoen.app.settings.SettingsRepository
import nl.hiertoen.app.ui.permissions.TrackingPermissions
import nl.hiertoen.app.ui.theme.HierToenTheme
import java.util.concurrent.TimeUnit

/**
 * Startscherm — §4.1. "Start rit" is de primaire, grote knop; eerdere ritten en instellingen
 * zijn secundair. Vervoerswijze wordt hier handmatig gekozen (automatische detectie is Fase 2,
 * §2.2/§11) en de benodigde toestemmingen (§12.4) worden pas gevraagd op het moment van actie.
 *
 * Bij het openen wordt ook opgeruimd volgens de routebewaring-instelling (§11) en gecontroleerd
 * op een rit die niet netjes is afgesloten (§4.5 "App herstart -> Actieve rit herstellen?").
 */
@Composable
fun StartScreen(
    onStartTrip: (TripMode) -> Unit,
    onResumeTrip: (String, TripMode) -> Unit,
    onOpenTrips: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { RepositoryFactory.tripRepository(context) }
    val settingsRepository = remember { SettingsRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    var selectedMode by remember { mutableStateOf(TripMode.CAR) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var recoverableTrip by remember { mutableStateOf<TripEntity?>(null) }

    LaunchedEffect(Unit) {
        cleanUpExpiredTrips(repository, settingsRepository)
        recoverableTrip = repository.getTripsByStatus(TripStatus.RECOVERABLE).maxByOrNull { it.startedAt }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            onStartTrip(selectedMode)
        } else {
            showPermissionRationale = true
        }
    }

    fun requestStart() {
        if (TrackingPermissions.allGranted(context)) {
            onStartTrip(selectedMode)
        } else {
            permissionLauncher.launch(TrackingPermissions.required())
        }
    }

    Scaffold { padding ->
        StartScreenContent(
            padding = padding,
            selectedMode = selectedMode,
            onModeSelected = { selectedMode = it },
            onStartTrip = ::requestStart,
            onOpenTrips = onOpenTrips,
            onOpenSettings = onOpenSettings,
        )
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text(stringResource(R.string.permission_rationale_title)) },
            text = { Text(stringResource(R.string.permission_rationale_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    permissionLauncher.launch(TrackingPermissions.required())
                }) { Text(stringResource(R.string.permission_rationale_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text(stringResource(R.string.permission_rationale_dismiss))
                }
            },
        )
    }

    recoverableTrip?.let { trip ->
        AlertDialog(
            onDismissRequest = { /* bewust geen dismiss-buiten-tik: gebruiker moet kiezen, §4.5 */ },
            title = { Text(stringResource(R.string.recover_trip_title)) },
            text = { Text(stringResource(R.string.recover_trip_body)) },
            confirmButton = {
                TextButton(onClick = {
                    val tripToResume = trip
                    recoverableTrip = null
                    if (TrackingPermissions.allGranted(context)) {
                        onResumeTrip(tripToResume.id, tripToResume.mode)
                    } else {
                        selectedMode = tripToResume.mode
                        permissionLauncher.launch(TrackingPermissions.required())
                    }
                }) { Text(stringResource(R.string.recover_trip_resume)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    val tripToFinish = trip
                    recoverableTrip = null
                    coroutineScope.launch {
                        repository.saveTrip(tripToFinish.copy(status = TripStatus.COMPLETED, endedAt = System.currentTimeMillis()))
                    }
                }) { Text(stringResource(R.string.recover_trip_finish)) }
            },
        )
    }
}

/** §11 Routebewaring: alleen afgeronde ritten opruimen, nooit een actieve/herstelbare/gepauzeerde. */
private suspend fun cleanUpExpiredTrips(repository: TripRepository, settingsRepository: SettingsRepository) {
    val retentionDays = settingsRepository.current().routeRetentionDays ?: return
    val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
    repository.observeTrips().first()
        .filter { it.status == TripStatus.COMPLETED && it.startedAt < cutoff }
        .forEach { repository.deleteTrip(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartScreenContent(
    padding: PaddingValues,
    selectedMode: TripMode,
    onModeSelected: (TripMode) -> Unit,
    onStartTrip: () -> Unit,
    onOpenTrips: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Text(text = stringResource(R.string.tagline), style = MaterialTheme.typography.bodyLarge)

        Column(modifier = Modifier.padding(top = 40.dp).fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = selectedMode == TripMode.CAR,
                    onClick = { onModeSelected(TripMode.CAR) },
                    label = { Text("Auto") },
                )
                FilterChip(
                    selected = selectedMode == TripMode.BICYCLE,
                    onClick = { onModeSelected(TripMode.BICYCLE) },
                    label = { Text("Fiets") },
                )
            }

            Button(
                onClick = onStartTrip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(top = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.start_screen_start_trip),
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            TextButton(onClick = onOpenTrips, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(stringResource(R.string.start_screen_previous_trips))
            }
            TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.start_screen_settings))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StartScreenPreview() {
    HierToenTheme {
        StartScreen(onStartTrip = {}, onResumeTrip = { _, _ -> }, onOpenTrips = {}, onOpenSettings = {})
    }
}
