package com.streamsphere.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.*
import com.streamsphere.app.MainActivity
import com.streamsphere.app.data.api.AppDatabase
import com.streamsphere.app.data.model.FavouriteChannel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun appDatabase(): AppDatabase
}

class ChannelWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Room query on IO dispatcher.
        val channel: FavouriteChannel? = withContext(Dispatchers.IO) {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    WidgetEntryPoint::class.java
                )
                entryPoint.appDatabase().favouritesDao().getWidgetChannels().first().firstOrNull()
            } catch (e: Exception) {
                null
            }
        }

        // Bitmap download on IO dispatcher — never on the Glance main thread.
        val logoBitmap: Bitmap? = channel?.logoUrl?.let { url ->
            withContext(Dispatchers.IO) {
                downloadBitmap(url)
            }
        }

        provideContent {
            SquareWidgetContent(context = context, channel = channel, logoBitmap = logoBitmap)
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun downloadBitmap(urlString: String): Bitmap? {
        return try {
            val url        = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 4_000
            connection.readTimeout    = 4_000
            connection.doInput        = true
            connection.connect()
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { BitmapFactory.decodeStream(it) }
            } else null
        } catch (e: IOException) {
            null
        }
    }
}

@Composable
private fun SquareWidgetContent(
    context: Context,
    channel: FavouriteChannel?,
    logoBitmap: Bitmap?
) {
    val bgColor      = ColorProvider(Color(0xFF111827), Color(0xFF111827))
    val textColor    = ColorProvider(Color(0xFFE8EDF5), Color(0xFFE8EDF5))
    val accentColor  = ColorProvider(Color(0xFF4F8EF7), Color(0xFF4F8EF7))
    val overlayColor = ColorProvider(Color(0xCC000000), Color(0xCC000000))
    val mutedColor   = ColorProvider(Color(0xFF6B7A99), Color(0xFF6B7A99))

    val intent = if (channel != null) {
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(MainActivity.EXTRA_CHANNEL_ID, channel.id)
            putExtra(MainActivity.EXTRA_STREAM_URL, channel.streamUrl)
            putExtra(MainActivity.EXTRA_FULLSCREEN, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    } else Intent(context, MainActivity::class.java)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .cornerRadius(14.dp)
            .clickable(actionStartActivity(intent)),
        contentAlignment = Alignment.Center
    ) {
        if (channel == null) {
            Column(
                modifier            = GlanceModifier.fillMaxSize().padding(6.dp),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("📺", style = TextStyle(fontSize = 26.sp, color = textColor))
                Spacer(modifier = GlanceModifier.height(3.dp))
                Text("Add channel", style = TextStyle(fontSize = 8.sp, color = mutedColor), maxLines = 2)
            }
        } else {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (logoBitmap != null) {
                    Image(
                        provider           = ImageProvider(logoBitmap),
                        contentDescription = channel.name,
                        contentScale       = ContentScale.Fit,
                        modifier           = GlanceModifier.fillMaxSize().padding(10.dp)
                    )
                } else {
                    val initial = channel.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                    Text(
                        text  = initial,
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, color = accentColor)
                    )
                }

                // Bottom name bar
                Column(
                    modifier            = GlanceModifier.fillMaxSize(),
                    verticalAlignment   = Alignment.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier         = GlanceModifier
                            .fillMaxWidth()
                            .background(overlayColor)
                            .padding(horizontal = 3.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text    = channel.name,
                            style   = TextStyle(fontSize = 7.sp, color = textColor, fontWeight = FontWeight.Medium),
                            maxLines = 1
                        )
                    }
                }

                // Live dot — top right
                if (channel.streamUrl != null) {
                    Column(
                        modifier            = GlanceModifier.fillMaxSize().padding(4.dp),
                        verticalAlignment   = Alignment.Top,
                        horizontalAlignment = Alignment.End
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .width(6.dp)
                                .height(6.dp)
                                .background(ColorProvider(Color(0xFFE53E3E), Color(0xFFE53E3E)))
                                .cornerRadius(3.dp)
                        ) {}
                    }
                }
            }
        }
    }
}

class ChannelWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ChannelWidget()
}
