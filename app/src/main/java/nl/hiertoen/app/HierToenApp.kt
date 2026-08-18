package nl.hiertoen.app

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class HierToenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // OSM-tile-etiquette vereist een herleidbare User-Agent (anders worden requests
        // geweigerd); cache in de eigen app-cache i.p.v. gedeelde externe opslag, zodat er
        // geen extra opslagpermissie nodig is — zie ritdetail-kaart (§4.4, §18).
        val config = Configuration.getInstance()
        config.userAgentValue = packageName
        config.osmdroidBasePath = File(cacheDir, "osmdroid")
        config.osmdroidTileCache = File(config.osmdroidBasePath, "tiles")
    }
}
