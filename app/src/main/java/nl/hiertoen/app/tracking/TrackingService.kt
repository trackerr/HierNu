package nl.hiertoen.app.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.hiertoen.app.core.ActivityType
import nl.hiertoen.app.core.GeoMath
import nl.hiertoen.app.data.local.HierToenDatabase
import nl.hiertoen.app.data.local.entity.TrackPointEntity
import nl.hiertoen.app.data.local.entity.TrackPointValidity
import nl.hiertoen.app.data.local.entity.TripEntity
import nl.hiertoen.app.data.local.entity.TripMode
import nl.hiertoen.app.data.local.entity.TripStatus
import nl.hiertoen.app.data.repository.TripRepository
import nl.hiertoen.app.data.repository.TripRepositoryImpl
import nl.hiertoen.app.motion.MotionInput
import nl.hiertoen.app.motion.MotionState
import nl.hiertoen.app.motion.MotionStateEngine
import nl.hiertoen.app.motion.MotionThresholds
import java.util.UUID

/**
 * Foreground service voor ritregistratie — §17.1 stap 3. Voedt GPS + Activity Recognition
 * aan de MotionStateEngine (§5), schrijft trackpunten incrementeel weg (§6.4: niet alleen
 * in geheugen) en houdt ritstatistieken bij voor het rijscherm.
 *
 * Bewuste vereenvoudiging t.o.v. een productierijpe implementatie: geen eigen
 * proces-herstart-hersteltraject binnen de service zelf. Een rit die door het besturingssysteem
 * wordt gekilld, wordt bij de volgende koude start van de app als RECOVERABLE herkend
 * (zie [recoverStaleActiveTrips]) — dat dekt §6.4, maar herstelt de service niet automatisch.
 */
class TrackingService : Service() {

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        val service: TrackingService get() = this@TrackingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private lateinit var repository: TripRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var activityRecognitionClient: ActivityRecognitionClient

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private var engine = MotionStateEngine(MotionThresholds.CAR)
    private var recordingPolicy = AdaptiveRecordingPolicy(TripMode.CAR)

    private val _session = MutableStateFlow<TrackingSessionState>(TrackingSessionState.NoActiveTrip)
    val session: StateFlow<TrackingSessionState> = _session.asStateFlow()

    private var currentTripId: String? = null
    private var currentMode: TripMode = TripMode.CAR
    private var startedAt: Long = 0L
    private var distanceM: Double = 0.0
    private var movingMs: Long = 0L
    private var stoppedMs: Long = 0L
    private var maxSpeedKmh: Double = 0.0
    private var currentSpeedKmh: Double = 0.0
    private var segmentIndex: Int = 0
    private var lastActivityType: ActivityType = ActivityType.UNKNOWN

    private var lastProcessedTimestamp: Long? = null
    private var lastValidPoint: TrackPointEntity? = null
    private var lastPersistedAt: Long? = null
    private var lastPersistedLat: Double? = null
    private var lastPersistedLon: Double? = null
    private var stillPersistedForCurrentStop: Boolean = false

    override fun onCreate() {
        super.onCreate()
        val database = HierToenDatabase.getInstance(this)
        repository = TripRepositoryImpl(database.tripDao(), database.trackPointDao())
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        activityRecognitionClient = ActivityRecognition.getClient(this)
        TrackingNotifications.ensureChannel(this)
        registerActivityReceiver()
        serviceScope.launch { recoverStaleActiveTrips() }
    }

    override fun onDestroy() {
        stopLocationUpdates()
        stopActivityUpdatesInternal()
        unregisterReceiver(activityReceiver)
        serviceJob.cancel()
        super.onDestroy()
    }

    /** §6.4: een rit die nooit netjes is afgesloten (bv. door een crash) mag niet stilzwijgend blijven ACTIVE. */
    private suspend fun recoverStaleActiveTrips() {
        repository.getTripsByStatus(TripStatus.ACTIVE).forEach { stale ->
            repository.saveTrip(stale.copy(status = TripStatus.RECOVERABLE))
        }
    }

    fun startTrip(mode: TripMode = TripMode.CAR) {
        if (_session.value is TrackingSessionState.Active) return

        currentMode = mode
        engine = MotionStateEngine(if (mode == TripMode.BICYCLE) MotionThresholds.BICYCLE else MotionThresholds.CAR)
        recordingPolicy = AdaptiveRecordingPolicy(mode)
        engine.start()
        resetTripBookkeeping()

        val tripId = UUID.randomUUID().toString()
        currentTripId = tripId
        startedAt = System.currentTimeMillis()

        serviceScope.launch {
            repository.saveTrip(
                TripEntity(
                    id = tripId,
                    name = null,
                    startedAt = startedAt,
                    endedAt = null,
                    status = TripStatus.ACTIVE,
                    mode = mode,
                    distanceM = 0.0,
                    movingMs = 0L,
                    stoppedMs = 0L,
                    avgSpeedKmh = 0.0,
                    maxSpeedKmh = 0.0,
                    algorithmVersion = ALGORITHM_VERSION,
                    createdAt = startedAt,
                ),
            )
        }

        startForegroundInternal()
        startLocationUpdates()
        startActivityUpdatesInternal()
        publishState()
    }

    fun pauseTrip() {
        val active = _session.value as? TrackingSessionState.Active ?: return
        engine.pause()
        persistStatus(active.tripId, TripStatus.PAUSED)
        publishState()
    }

    fun resumeTrip() {
        val active = _session.value as? TrackingSessionState.Active ?: return
        engine.resume()
        persistStatus(active.tripId, TripStatus.ACTIVE)
        publishState()
    }

    fun stopTrip() {
        val active = _session.value as? TrackingSessionState.Active
        engine.stop()
        stopLocationUpdates()
        stopActivityUpdatesInternal()

        if (active != null) {
            val endedAt = System.currentTimeMillis()
            val finalDistanceM = distanceM
            val finalMovingMs = movingMs
            val finalStoppedMs = stoppedMs
            val finalMaxSpeed = maxSpeedKmh
            serviceScope.launch {
                repository.getTrip(active.tripId)?.let { trip ->
                    repository.saveTrip(
                        trip.copy(
                            status = TripStatus.COMPLETED,
                            endedAt = endedAt,
                            distanceM = finalDistanceM,
                            movingMs = finalMovingMs,
                            stoppedMs = finalStoppedMs,
                            avgSpeedKmh = averageSpeedKmh(finalDistanceM, finalMovingMs),
                            maxSpeedKmh = finalMaxSpeed,
                        ),
                    )
                }
            }
        }

        currentTripId = null
        _session.value = TrackingSessionState.NoActiveTrip
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun persistStatus(tripId: String, status: TripStatus) {
        serviceScope.launch {
            repository.getTrip(tripId)?.let { repository.saveTrip(it.copy(status = status)) }
        }
    }

    private fun averageSpeedKmh(distanceM: Double, movingMs: Long): Double {
        if (movingMs <= 0L) return 0.0
        val hours = movingMs / 3_600_000.0
        return (distanceM / 1000.0) / hours
    }

    private fun resetTripBookkeeping() {
        distanceM = 0.0
        movingMs = 0L
        stoppedMs = 0L
        maxSpeedKmh = 0.0
        currentSpeedKmh = 0.0
        segmentIndex = 0
        lastActivityType = ActivityType.UNKNOWN
        lastProcessedTimestamp = null
        lastValidPoint = null
        lastPersistedAt = null
        lastPersistedLat = null
        lastPersistedLon = null
        stillPersistedForCurrentStop = false
    }

    // --- Locatie ---

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::onNewLocation)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_POLL_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_MIN_INTERVAL_MS)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun onNewLocation(location: android.location.Location) {
        val tripId = currentTripId ?: return
        val timestamp = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6 else 0.0
        val accuracyM = if (location.hasAccuracy()) location.accuracy.toDouble() else Double.MAX_VALUE

        val motionState = engine.update(
            MotionInput(
                timestampMs = timestamp,
                lat = location.latitude,
                lon = location.longitude,
                speedKmh = speedKmh,
                accuracyM = accuracyM,
                activityType = lastActivityType,
            ),
        )

        currentSpeedKmh = speedKmh
        if (speedKmh > maxSpeedKmh) maxSpeedKmh = speedKmh
        if (motionState != MotionState.STILL) stillPersistedForCurrentStop = false

        attributeElapsedTime(timestamp, motionState)
        lastProcessedTimestamp = timestamp

        maybePersist(tripId, location, timestamp, accuracyM, motionState)

        publishState()
        updateNotification()
    }

    private fun attributeElapsedTime(timestamp: Long, motionState: MotionState) {
        val previous = lastProcessedTimestamp ?: return
        val deltaMs = timestamp - previous
        if (deltaMs <= 0L) return

        if (deltaMs > SEGMENT_GAP_THRESHOLD_MS) {
            // Groot tijdsgat (tunnel, geen GPS, app op de achtergrond): nieuw segment, geen
            // verzonnen rij-/stilstandtijd over het gat heen — §6.2.
            segmentIndex += 1
            return
        }

        when (motionState) {
            MotionState.MOVING, MotionState.SLOW -> movingMs += deltaMs
            MotionState.STILL, MotionState.STOP_CANDIDATE -> stoppedMs += deltaMs
            else -> Unit
        }
    }

    private fun maybePersist(
        tripId: String,
        location: android.location.Location,
        timestamp: Long,
        accuracyM: Double,
        motionState: MotionState,
    ) {
        val elapsedSincePersist = lastPersistedAt?.let { timestamp - it } ?: Long.MAX_VALUE
        val distanceSincePersist = if (lastPersistedLat != null && lastPersistedLon != null) {
            GeoMath.haversineMeters(lastPersistedLat!!, lastPersistedLon!!, location.latitude, location.longitude)
        } else {
            Double.MAX_VALUE
        }

        val shouldPersist = recordingPolicy.shouldPersist(
            motionState = motionState,
            elapsedSinceLastPersistMs = elapsedSincePersist,
            distanceSinceLastPersistM = distanceSincePersist,
            stillAlreadyPersisted = stillPersistedForCurrentStop,
        )
        if (!shouldPersist) return

        val validity = LocationValidator.validate(
            previous = lastValidPoint,
            lat = location.latitude,
            lon = location.longitude,
            timestampMs = timestamp,
            accuracyM = accuracyM,
            maxAccuracyM = engine.thresholds.maxAccuracyM,
        )

        val point = TrackPointEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            timestamp = timestamp,
            lat = location.latitude,
            lon = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            accuracyM = accuracyM.toFloat(),
            speedMps = if (location.hasSpeed()) location.speed else null,
            bearingDeg = if (location.hasBearing()) location.bearing else null,
            activityType = lastActivityType,
            segmentIndex = segmentIndex,
            validity = validity,
        )

        if (validity == TrackPointValidity.VALID) {
            lastValidPoint?.let { previous ->
                distanceM += GeoMath.haversineMeters(previous.lat, previous.lon, point.lat, point.lon)
            }
            lastValidPoint = point
        }

        lastPersistedAt = timestamp
        lastPersistedLat = location.latitude
        lastPersistedLon = location.longitude
        if (motionState == MotionState.STILL) stillPersistedForCurrentStop = true

        serviceScope.launch { repository.appendTrackPoint(point) }
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // --- Activity Recognition ---

    private val activityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!ActivityRecognitionResult.hasResult(intent)) return
            val result = ActivityRecognitionResult.extractResult(intent) ?: return
            val mostProbable = result.mostProbableActivity
            if (mostProbable.confidence < MIN_ACTIVITY_CONFIDENCE) return
            lastActivityType = mapDetectedActivity(mostProbable.type)
        }
    }

    private fun registerActivityReceiver() {
        val filter = IntentFilter(ACTION_ACTIVITY_UPDATE)
        ContextCompat.registerReceiver(this, activityReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun activityUpdatesPendingIntent(): PendingIntent {
        val intent = Intent(ACTION_ACTIVITY_UPDATE).setPackage(packageName)
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    @SuppressLint("MissingPermission")
    private fun startActivityUpdatesInternal() {
        if (!hasActivityRecognitionPermission()) return
        activityRecognitionClient.requestActivityUpdates(ACTIVITY_UPDATE_INTERVAL_MS, activityUpdatesPendingIntent())
    }

    private fun stopActivityUpdatesInternal() {
        activityRecognitionClient.removeActivityUpdates(activityUpdatesPendingIntent())
    }

    private fun hasActivityRecognitionPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private fun mapDetectedActivity(type: Int): ActivityType = when (type) {
        DetectedActivity.IN_VEHICLE -> ActivityType.IN_VEHICLE
        DetectedActivity.ON_BICYCLE -> ActivityType.ON_BICYCLE
        DetectedActivity.WALKING, DetectedActivity.ON_FOOT -> ActivityType.WALKING
        DetectedActivity.STILL -> ActivityType.STILL
        else -> ActivityType.UNKNOWN
    }

    // --- Notificatie & status ---

    private fun startForegroundInternal() {
        val notification = TrackingNotifications.build(this, contentTextFor(_session.value))
        ServiceCompat.startForeground(
            this,
            TrackingNotifications.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(TrackingNotifications.NOTIFICATION_ID, TrackingNotifications.build(this, contentTextFor(_session.value)))
    }

    private fun contentTextFor(state: TrackingSessionState): String = when (state) {
        is TrackingSessionState.Active -> "%.1f km — %s".format(state.distanceM / 1000.0, state.motionState.name)
        TrackingSessionState.NoActiveTrip -> "Geen actieve rit"
    }

    private fun publishState() {
        val tripId = currentTripId ?: return
        _session.value = TrackingSessionState.Active(
            tripId = tripId,
            status = if (engine.currentState == MotionState.PAUSED) TripStatus.PAUSED else TripStatus.ACTIVE,
            motionState = engine.currentState,
            mode = currentMode,
            elapsedMs = System.currentTimeMillis() - startedAt,
            movingMs = movingMs,
            stoppedMs = stoppedMs,
            distanceM = distanceM,
            currentSpeedKmh = currentSpeedKmh,
            maxSpeedKmh = maxSpeedKmh,
        )
    }

    companion object {
        private const val ALGORITHM_VERSION = "motion-v1"
        private const val LOCATION_POLL_INTERVAL_MS = 2_000L
        private const val LOCATION_MIN_INTERVAL_MS = 1_000L
        private const val ACTIVITY_UPDATE_INTERVAL_MS = 5_000L
        private const val MIN_ACTIVITY_CONFIDENCE = 50
        private const val SEGMENT_GAP_THRESHOLD_MS = 5 * 60 * 1_000L
        private const val ACTION_ACTIVITY_UPDATE = "nl.hiertoen.app.tracking.ACTIVITY_UPDATE"
    }
}
