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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.hiertoen.app.core.ActivityType
import nl.hiertoen.app.core.GeoMath
import nl.hiertoen.app.data.local.entity.MomentState
import nl.hiertoen.app.data.local.entity.MomentType
import nl.hiertoen.app.data.local.entity.TrackPointEntity
import nl.hiertoen.app.data.local.entity.TrackPointValidity
import nl.hiertoen.app.data.local.entity.TripEntity
import nl.hiertoen.app.data.local.entity.TripMode
import nl.hiertoen.app.data.local.entity.TripMomentEntity
import nl.hiertoen.app.data.local.entity.TripStatus
import nl.hiertoen.app.data.repository.RepositoryFactory
import nl.hiertoen.app.data.repository.TripRepository
import nl.hiertoen.app.motion.MotionInput
import nl.hiertoen.app.motion.MotionState
import nl.hiertoen.app.motion.MotionStateEngine
import nl.hiertoen.app.motion.MotionThresholds
import nl.hiertoen.app.BuildConfig
import nl.hiertoen.app.photos.GoogleStreetViewClient
import nl.hiertoen.app.photos.PhotoSearchService
import nl.hiertoen.app.photos.WikimediaHttpClient
import nl.hiertoen.app.settings.SettingsRepository
import nl.hiertoen.app.settings.UserSettings
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
    private lateinit var photoSearchService: PhotoSearchService
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private var currentSettings: UserSettings = UserSettings()

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
    private var lastKnownLat: Double? = null
    private var lastKnownLon: Double? = null
    private var lastKnownBearingDeg: Float? = null
    private var lastKnownAccuracyM: Float? = null

    private var lastProcessedTimestamp: Long? = null
    private var lastValidPoint: TrackPointEntity? = null
    private var lastPersistedAt: Long? = null
    private var lastPersistedLat: Double? = null
    private var lastPersistedLon: Double? = null
    private var stillPersistedForCurrentStop: Boolean = false
    private var autoStopMomentTriggered: Boolean = false
    private var currentStopMomentId: String? = null
    private var currentDisplayedPhoto: DisplayedPhotoInfo? = null
    private var currentLocationIntervalMs: Long = LOCATION_POLL_INTERVAL_MS

    override fun onCreate() {
        super.onCreate()
        repository = RepositoryFactory.tripRepository(this)
        val streetViewClient = BuildConfig.STREETVIEW_API_KEY.takeIf { it.isNotBlank() }?.let { GoogleStreetViewClient(it) }
        photoSearchService = PhotoSearchService(WikimediaHttpClient(), repository, streetViewClient)
        settingsRepository = SettingsRepository(this)
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
        resetTripBookkeeping()
        val tripId = UUID.randomUUID().toString()
        currentTripId = tripId
        startedAt = System.currentTimeMillis()

        // Instellingen (stopvertraging o.a.) bepalen de MotionStateEngine, dus die moeten
        // geladen zijn vóórdat de engine wordt aangemaakt en de eerste locatie-update kan
        // binnenkomen — vandaar dat startForeground/startLocationUpdates hier ná de settings-
        // read gebeuren i.p.v. synchroon in startTrip() zelf.
        serviceScope.launch {
            currentSettings = settingsRepository.current()
            engine = MotionStateEngine(buildThresholds(mode, currentSettings))
            recordingPolicy = AdaptiveRecordingPolicy(mode)
            engine.start()

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

            startForegroundInternal()
            startLocationUpdates()
            startActivityUpdatesInternal()
            publishState()
        }
    }

    /** Niet-suspend gemakswrapper, zodat aanroepers dezelfde stijl als [startTrip] kunnen gebruiken. */
    fun resumeExistingTrip(tripId: String) {
        serviceScope.launch { resumeExistingTripSuspend(tripId) }
    }

    /**
     * §4.5 "App herstart -> Actieve rit herstellen?": zet een RECOVERABLE rit terug op ACTIVE
     * en telt vanaf de eerder opgeslagen aggregaten verder, in een nieuw segment (§6.2) zodat
     * het gat tussen crash en herstart niet als doorgereden route wordt getekend.
     */
    private suspend fun resumeExistingTripSuspend(tripId: String) {
        if (_session.value is TrackingSessionState.Active) return
        val trip = repository.getTrip(tripId) ?: return

        currentSettings = settingsRepository.current()
        currentMode = trip.mode
        resetTripBookkeeping()
        engine = MotionStateEngine(buildThresholds(trip.mode, currentSettings))
        recordingPolicy = AdaptiveRecordingPolicy(trip.mode)
        engine.start()

        distanceM = trip.distanceM
        movingMs = trip.movingMs
        stoppedMs = trip.stoppedMs
        maxSpeedKmh = trip.maxSpeedKmh
        currentTripId = tripId
        startedAt = trip.startedAt
        segmentIndex = (repository.observeTrackPoints(tripId).first().maxOfOrNull { it.segmentIndex } ?: -1) + 1

        repository.saveTrip(trip.copy(status = TripStatus.ACTIVE, endedAt = null))

        startForegroundInternal()
        startLocationUpdates()
        startActivityUpdatesInternal()
        publishState()
    }

    private fun buildThresholds(mode: TripMode, settings: UserSettings): MotionThresholds {
        val base = if (mode == TripMode.BICYCLE) MotionThresholds.BICYCLE else MotionThresholds.CAR
        return base.copy(stopDelayMs = settings.stopDelaySeconds * 1_000L)
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

    /**
     * "Deze plek bewaren" — §3.5. Legt de laatst bekende positie vast als [TripMomentEntity]
     * en start meteen de beeldzoekopdracht (§7); het moment begint op NO_IMAGE_YET totdat
     * [PhotoSearchService] de status bijwerkt.
     */
    suspend fun saveCurrentPlace(): Boolean {
        val tripId = currentTripId ?: return false
        val lat = lastKnownLat ?: run {
            Log.d(TAG, "Deze plek bewaren: nog geen GPS-fix ontvangen, niets om te bewaren")
            return false
        }
        val lon = lastKnownLon ?: return false

        val moment = TripMomentEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            timestamp = System.currentTimeMillis(),
            lat = lat,
            lon = lon,
            bearingDeg = lastKnownBearingDeg,
            accuracyM = lastKnownAccuracyM ?: Float.MAX_VALUE,
            type = MomentType.MANUAL_BOOKMARK,
            source = null,
            state = MomentState.NO_IMAGE_YET,
            note = null,
        )
        currentStopMomentId = moment.id
        repository.saveMoment(moment)
        Log.d(TAG, "Deze plek bewaren: moment=${moment.id} opgeslagen bij ($lat, $lon)")
        // Niet awaiten: "Plek bewaard" mag meteen bevestigd worden, de zoekopdracht loopt door (§3.5).
        // publishState() laat displayedPhoto alleen door bij STILL, dus tijdens het rijden komt
        // dit resultaat niet meteen in beeld. Vereenvoudiging t.o.v. de letterlijke "wachtrij" uit
        // §3.1: als er ná deze handmatige marker nog een automatische stop volgt vóórdat de
        // zoekopdracht klaar is, wint die latere stop de weergave — de marker zelf blijft gewoon
        // bewaard en zichtbaar in het ritdetail, alleen niet met de pop-up-behandeling.
        if (shouldSearchForPhotos()) {
            serviceScope.launch {
                photoSearchService.searchForMoment(moment, currentSettings.searchRadiusM, currentSettings.preferOldest)
                refreshDisplayedPhoto(moment.id)
            }
        }
        return true
    }

    /** §11: Wikimedia-bron uit, of mobiele data niet toegestaan terwijl er geen wifi is. */
    private fun shouldSearchForPhotos(): Boolean {
        if (!currentSettings.wikimediaEnabled) return false
        if (!currentSettings.mobileDataAllowed && !isOnWifi()) return false
        return true
    }

    private fun isOnWifi(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** MVP-05: automatisch een historische foto zoeken zodra een stop bevestigd is — precies één keer per stop. */
    private fun maybeTriggerAutoStopSearch(tripId: String, motionState: MotionState) {
        if (motionState != MotionState.STILL) {
            autoStopMomentTriggered = false
            // Nieuwe stop = nieuwe zoekopdracht; een foto van de vórige stop mag niet even
            // "doorschemeren" voordat deze stop zijn eigen resultaat heeft (§13.4-geest: geen
            // verrassende/foute foto tonen tijdens de korte overgang).
            currentStopMomentId = null
            currentDisplayedPhoto = null
            return
        }
        if (autoStopMomentTriggered) return
        autoStopMomentTriggered = true
        // §11 "Automatisch beeld bij stilstand": uitgeschakeld betekent geen automatische
        // marker en geen zoekopdracht — "Deze plek bewaren" blijft wel altijd beschikbaar.
        if (!currentSettings.autoPhotoEnabled) {
            Log.d(TAG, "auto-stop overgeslagen: autoPhotoEnabled staat uit")
            return
        }

        val lat = lastKnownLat ?: return
        val lon = lastKnownLon ?: return
        val moment = TripMomentEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            timestamp = System.currentTimeMillis(),
            lat = lat,
            lon = lon,
            bearingDeg = lastKnownBearingDeg,
            accuracyM = lastKnownAccuracyM ?: Float.MAX_VALUE,
            type = MomentType.AUTO_STOP,
            source = null,
            state = MomentState.NO_IMAGE_YET,
            note = null,
        )
        currentStopMomentId = moment.id
        Log.d(TAG, "STILL bevestigd bij ($lat, $lon), moment=${moment.id} aangemaakt")
        serviceScope.launch {
            repository.saveMoment(moment)
            if (shouldSearchForPhotos()) {
                photoSearchService.searchForMoment(moment, currentSettings.searchRadiusM, currentSettings.preferOldest)
                refreshDisplayedPhoto(moment.id)
            } else {
                Log.d(
                    TAG,
                    "zoekopdracht overgeslagen: wikimediaEnabled=${currentSettings.wikimediaEnabled} " +
                        "mobileDataAllowed=${currentSettings.mobileDataAllowed}",
                )
            }
        }
    }

    /** Haalt de winnende kandidaat op en publiceert 'm — maar alleen als deze stop nog actueel is. */
    private suspend fun refreshDisplayedPhoto(momentId: String) {
        if (currentStopMomentId != momentId) {
            Log.d(TAG, "moment=$momentId is ingehaald door een nieuwere stop, foto niet meer tonen")
            return
        }
        val best = repository.getBestCandidate(momentId)
        if (best == null) {
            Log.d(TAG, "moment=$momentId heeft geen kandidaat om te tonen")
            return
        }
        currentDisplayedPhoto = DisplayedPhotoInfo(
            momentId = momentId,
            title = best.title,
            imageUrl = best.imageUrl,
            thumbUrl = best.thumbUrl,
            year = best.yearFrom,
            attribution = best.attribution,
            distanceM = best.distanceM,
        )
        Log.d(TAG, "moment=$momentId klaar om te tonen (motionState=${engine.currentState})")
        publishState()
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
        lastKnownLat = null
        lastKnownLon = null
        lastKnownBearingDeg = null
        lastKnownAccuracyM = null
        lastProcessedTimestamp = null
        lastValidPoint = null
        lastPersistedAt = null
        lastPersistedLat = null
        lastPersistedLon = null
        stillPersistedForCurrentStop = false
        autoStopMomentTriggered = false
        currentStopMomentId = null
        currentDisplayedPhoto = null
        currentLocationIntervalMs = LOCATION_POLL_INTERVAL_MS
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
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, currentLocationIntervalMs)
            .setMinUpdateIntervalMillis(LOCATION_MIN_INTERVAL_MS)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    /**
     * Zolang er een foto getoond kan worden (STILL) pollen we sneller — §13.4/§14.4 eisen dat de
     * foto binnen 1s (mediaan <500ms) verdwijnt zodra beweging hervat wordt, en dat is niet te
     * halen als we moeten wachten op de volgende trage GPS-fix van een rijdende auto.
     */
    @SuppressLint("MissingPermission")
    private fun updateLocationCadence(motionState: MotionState) {
        val desired = if (motionState == MotionState.STILL) FAST_POLL_INTERVAL_MS else LOCATION_POLL_INTERVAL_MS
        if (desired == currentLocationIntervalMs || !hasLocationPermission()) return
        currentLocationIntervalMs = desired
        startLocationUpdates()
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
        updateLocationCadence(motionState)

        lastKnownLat = location.latitude
        lastKnownLon = location.longitude
        lastKnownBearingDeg = if (location.hasBearing()) location.bearing else null
        lastKnownAccuracyM = accuracyM.toFloat()

        attributeElapsedTime(timestamp, motionState)
        lastProcessedTimestamp = timestamp

        maybePersist(tripId, location, timestamp, accuracyM, motionState)
        maybeTriggerAutoStopSearch(tripId, motionState)

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
        val motionState = engine.currentState
        _session.value = TrackingSessionState.Active(
            tripId = tripId,
            status = if (motionState == MotionState.PAUSED) TripStatus.PAUSED else TripStatus.ACTIVE,
            motionState = motionState,
            mode = currentMode,
            elapsedMs = System.currentTimeMillis() - startedAt,
            movingMs = movingMs,
            stoppedMs = stoppedMs,
            distanceM = distanceM,
            currentSpeedKmh = currentSpeedKmh,
            maxSpeedKmh = maxSpeedKmh,
            displayedPhoto = displayedPhotoFor(motionState, currentDisplayedPhoto),
        )
    }

    companion object {
        private const val TAG = "HierToen/Tracking"
        private const val ALGORITHM_VERSION = "motion-v1"
        private const val LOCATION_POLL_INTERVAL_MS = 2_000L
        // Sneller pollen zolang STILL geldt, zodat de fotoweergave op tijd verdwijnt (zie
        // updateLocationCadence). LOCATION_MIN_INTERVAL_MS moet hier gelijk aan of onder blijven,
        // anders begrenst Fused Location de effectieve leversnelheid alsnog tot het oude tempo.
        private const val FAST_POLL_INTERVAL_MS = 500L
        private const val LOCATION_MIN_INTERVAL_MS = 500L
        private const val ACTIVITY_UPDATE_INTERVAL_MS = 5_000L
        private const val MIN_ACTIVITY_CONFIDENCE = 50
        private const val SEGMENT_GAP_THRESHOLD_MS = 5 * 60 * 1_000L
        private const val ACTION_ACTIVITY_UPDATE = "nl.hiertoen.app.tracking.ACTIVITY_UPDATE"
    }
}
