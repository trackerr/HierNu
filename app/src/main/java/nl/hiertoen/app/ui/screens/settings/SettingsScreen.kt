package nl.hiertoen.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.hiertoen.app.settings.SettingsRepository
import nl.hiertoen.app.settings.UserSettings

/**
 * Instellingenscherm — §11. Alleen de instellingen die daadwerkelijk gedrag beïnvloeden
 * (zie [UserSettings]); geen dode schakelaars voor bronnen die nog niet bestaan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }
    val settings by repository.settings.collectAsState(initial = UserSettings())
    val scope = rememberCoroutineScope()

    fun update(transform: (UserSettings) -> UserSettings) {
        scope.launch { repository.update(transform) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Instellingen") },
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            SectionTitle("Fotoweergave")
            ToggleRow(
                label = "Automatisch beeld bij stilstand",
                checked = settings.autoPhotoEnabled,
                onCheckedChange = { update { s -> s.copy(autoPhotoEnabled = it) } },
            )
            ChoiceRow(
                label = "Stopvertraging",
                options = UserSettings.STOP_DELAY_OPTIONS,
                selected = settings.stopDelaySeconds,
                labelFor = { "${it}s" },
                onSelect = { update { s -> s.copy(stopDelaySeconds = it) } },
            )
            ToggleRow(
                label = "Voorkeur: oudste bruikbare beeld",
                checked = settings.preferOldest,
                onCheckedChange = { update { s -> s.copy(preferOldest = it) } },
            )
            ChoiceRow(
                label = "Zoekradius",
                options = UserSettings.SEARCH_RADIUS_OPTIONS,
                selected = settings.searchRadiusM,
                labelFor = { "${it}m" },
                onSelect = { update { s -> s.copy(searchRadiusM = it) } },
            )
            ToggleRow(
                label = "Wikimedia Commons",
                checked = settings.wikimediaEnabled,
                onCheckedChange = { update { s -> s.copy(wikimediaEnabled = it) } },
            )

            SectionTitle("Rijscherm")
            ToggleRow(
                label = "Snelheid tonen",
                checked = settings.showSpeed,
                onCheckedChange = { update { s -> s.copy(showSpeed = it) } },
            )
            ToggleRow(
                label = "Afstand en tijd tonen",
                checked = settings.showDistanceTime,
                onCheckedChange = { update { s -> s.copy(showDistanceTime = it) } },
            )

            SectionTitle("Data en opslag")
            ToggleRow(
                label = "Mobiele data toegestaan",
                checked = settings.mobileDataAllowed,
                onCheckedChange = { update { s -> s.copy(mobileDataAllowed = it) } },
            )
            ChoiceRow(
                label = "Routebewaring",
                options = UserSettings.RETENTION_OPTIONS,
                selected = settings.routeRetentionDays,
                labelFor = { it?.let { days -> "${days}d" } ?: "Onbeperkt" },
                onSelect = { update { s -> s.copy(routeRetentionDays = it) } },
            )
            DataCleanupNote()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
    HorizontalDivider()
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(label: String, options: List<T>, selected: T, labelFor: (T) -> String, onSelect: (T) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(labelFor(option)) })
            }
        }
    }
}

/** §11 Routebewaring geeft alleen de instelling; de opschoning zelf gebeurt bij het opstarten van de app (zie StartScreen). */
@Composable
private fun DataCleanupNote() {
    Text(
        text = "Oudere ritten worden automatisch opgeruimd bij het openen van de app, op basis van de bewaartermijn.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
    )
}
