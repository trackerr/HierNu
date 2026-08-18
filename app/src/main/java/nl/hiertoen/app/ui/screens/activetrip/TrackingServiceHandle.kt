package nl.hiertoen.app.ui.screens.activetrip

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import nl.hiertoen.app.data.local.entity.TripMode
import nl.hiertoen.app.tracking.TrackingService
import nl.hiertoen.app.tracking.TrackingSessionState

/**
 * Bindt aan de TrackingService zolang het rijscherm zichtbaar is. De service blijft daarna
 * gewoon op de voorgrond doorlopen (§12.3): alleen de UI-binding wordt opgeruimd bij [onDispose],
 * niet de rit zelf.
 */
class TrackingServiceHandle internal constructor(private val context: Context) {
    private var bound: TrackingService? = null

    fun pause() = bound?.pauseTrip()
    fun resume() = bound?.resumeTrip()
    fun stop() = bound?.stopTrip()
    suspend fun saveCurrentPlace(): Boolean = bound?.saveCurrentPlace() ?: false

    internal fun attach(service: TrackingService, mode: TripMode) {
        bound = service
        if (service.session.value is TrackingSessionState.NoActiveTrip) {
            service.startTrip(mode)
        }
    }

    internal fun detach() {
        bound = null
    }
}

@Composable
fun rememberTrackingServiceHandle(mode: TripMode): Pair<TrackingServiceHandle, State<TrackingSessionState>> {
    val context = LocalContext.current
    val handle = remember { TrackingServiceHandle(context) }
    var boundService by remember { mutableStateOf<TrackingService?>(null) }
    val session = remember { mutableStateOf<TrackingSessionState>(TrackingSessionState.NoActiveTrip) }

    DisposableEffect(Unit) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = (binder as TrackingService.LocalBinder).service
                handle.attach(service, mode)
                boundService = service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                handle.detach()
                boundService = null
            }
        }

        val intent = Intent(context, TrackingService::class.java)
        ContextCompat.startForegroundService(context, intent)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        onDispose { context.unbindService(connection) }
    }

    LaunchedEffect(boundService) {
        boundService?.session?.collect { session.value = it }
    }

    return handle to session
}
