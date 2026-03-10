package dev.tidesapp.wearos.player.playback

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.tidal.sdk.player.common.model.AudioQuality
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QualityManager @Inject constructor(
    private val connectivityManager: ConnectivityManager,
) {

    fun resolveQuality(): AudioQuality {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

        return when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ->
                AudioQuality.LOSSLESS

            else -> AudioQuality.HIGH
        }
    }
}
