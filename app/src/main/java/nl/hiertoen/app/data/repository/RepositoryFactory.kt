package nl.hiertoen.app.data.repository

import android.content.Context
import nl.hiertoen.app.data.local.HierToenDatabase

/**
 * Eén plek om de Room-laag naar repository's te bedraden. Geen DI-framework (§9.4 noemt dit
 * als aanbeveling, maar dat is meer dan de MVP-scaffold nodig heeft) — gewoon een fabriekje
 * zodat TrackingService en de schermen niet elk hun eigen database-opzet dupliceren.
 */
object RepositoryFactory {
    fun tripRepository(context: Context): TripRepository {
        val database = HierToenDatabase.getInstance(context)
        return TripRepositoryImpl(database.tripDao(), database.trackPointDao(), database.tripMomentDao())
    }
}
