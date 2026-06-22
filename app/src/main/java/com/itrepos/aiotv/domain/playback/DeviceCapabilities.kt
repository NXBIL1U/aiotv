package com.itrepos.aiotv.domain.playback

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import android.view.WindowManager
import com.itrepos.aiotv.domain.model.Codec
import com.itrepos.aiotv.domain.model.Quality
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Probes the device once for what it can decode (codecs + max resolution) and display (screen
 * resolution + HDR), exposing a [DeviceProfile]. Any failure degrades to [DeviceProfile.PERMISSIVE]
 * so ranking never crashes over a capability query.
 */
@Singleton
class DeviceCapabilities @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val profile: DeviceProfile by lazy {
        runCatching { probe() }.getOrElse {
            Log.w(TAG, "capability probe failed; using permissive profile", it)
            DeviceProfile.PERMISSIVE
        }
    }

    private fun probe(): DeviceProfile {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val codecs = mutableSetOf<Codec>()
        var maxDecodeRank = Quality.SD.rank
        val mimes = mapOf("video/avc" to Codec.AVC, "video/hevc" to Codec.HEVC, "video/av01" to Codec.AV1)
        for ((mime, codec) in mimes) {
            val info = list.codecInfos.firstOrNull { ci ->
                !ci.isEncoder && ci.supportedTypes.any { it.equals(mime, true) }
            } ?: continue
            codecs += codec
            val vc = runCatching { info.getCapabilitiesForType(mime).videoCapabilities }.getOrNull() ?: continue
            val w = vc.supportedWidths.upper
            val h = vc.supportedHeights.upper
            val rank = qualityRankFor(maxOf(w, h))
            if (rank > maxDecodeRank) maxDecodeRank = rank
        }
        if (codecs.isEmpty()) return DeviceProfile.PERMISSIVE

        val dm = context.resources.displayMetrics
        val screenRank = qualityRankFor(maxOf(dm.widthPixels, dm.heightPixels))
        val maxRank = minOf(maxDecodeRank, screenRank)
        val maxRes = Quality.values().firstOrNull { it.rank == maxRank } ?: Quality.HD_1080

        val hdr = runCatching {
            val display = if (Build.VERSION.SDK_INT >= 30) {
                context.display
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            }
            @Suppress("DEPRECATION")
            (display?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty()) == true
        }.getOrDefault(false)

        val p = DeviceProfile(maxRes, codecs, hdr)
        Log.i(TAG, "DeviceProfile = $p (decodeMaxRank=$maxDecodeRank, screenRank=$screenRank)")
        return p
    }

    // Map a pixel dimension (longest side) to a Quality rank.
    private fun qualityRankFor(longestSidePx: Int): Int = when {
        longestSidePx >= 3840 -> Quality.UHD_2160.rank
        longestSidePx >= 1920 -> Quality.HD_1080.rank
        longestSidePx >= 1280 -> Quality.HD_720.rank
        else -> Quality.SD.rank
    }

    private companion object {
        const val TAG = "DeviceCapabilities"
    }
}
