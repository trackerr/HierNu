package nl.hiertoen.app.ui.screens.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nl.hiertoen.app.R
import nl.hiertoen.app.ui.theme.HierToenTheme

/**
 * Startscherm — §4.1. "Start rit" is de primaire, grote knop; eerdere ritten en
 * instellingen zijn secundair. Toestemmingswaarschuwingen (alleen bij actie nodig)
 * volgen in stap 3 samen met de TrackingService.
 */
@Composable
fun StartScreen(
    onStartTrip: () -> Unit,
    onOpenTrips: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold { padding ->
        StartScreenContent(
            padding = padding,
            onStartTrip = onStartTrip,
            onOpenTrips = onOpenTrips,
            onOpenSettings = onOpenSettings,
        )
    }
}

@Composable
private fun StartScreenContent(
    padding: PaddingValues,
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
            Button(
                onClick = onStartTrip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
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
        StartScreen(onStartTrip = {}, onOpenTrips = {}, onOpenSettings = {})
    }
}
