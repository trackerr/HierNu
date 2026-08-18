package nl.hiertoen.app.ui.photo

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import nl.hiertoen.app.photos.PhotoSearchService
import java.util.Locale

/**
 * Fotoweergave — §4.3. Gedeeld tussen het rijscherm (live, tijdens STILL) en het ritdetail
 * (terugkijken uit de log): zelfde opmaak, maar altijd met een zichtbare sluitknop. Op het
 * rijscherm ontbrak die eerder, waardoor de app "vastzat" op de foto als een rit niet netjes
 * werd afgesloten.
 */
@Composable
fun FullScreenPhotoViewer(
    imageUrl: String,
    title: String,
    year: Int?,
    distanceM: Double,
    attribution: String,
    provider: String,
    sourcePageUrl: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painter = rememberAsyncImagePainter(imageUrl),
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
        ) {
            Text(text = "←", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                .padding(24.dp),
        ) {
            Text(text = year?.toString() ?: "Datum onbekend", style = MaterialTheme.typography.displayLarge, color = Color.White)
            Text(text = "Deze plek", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                text = "%.0f m van de fotopositie — %s".format(Locale.getDefault(), distanceM, attribution),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )

            // Alleen Street View heeft een echte interactieve 360°-panoramaviewer op Google's
            // eigen infrastructuur; Wikimedia-foto's zijn vrijwel nooit equirectangulaire
            // panorama's, dus daar tonen we deze knop bewust niet.
            if (provider == PhotoSearchService.PROVIDER_STREETVIEW) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourcePageUrl)))
                }) {
                    Text("Bekijk interactief panorama")
                }
            }
        }
    }
}
