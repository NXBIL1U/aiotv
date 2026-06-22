package com.itrepos.aiotv.domain

import com.itrepos.aiotv.domain.model.Codec
import com.itrepos.aiotv.domain.model.Quality
import com.itrepos.aiotv.domain.model.Stream
import com.itrepos.aiotv.domain.playback.DeviceProfile
import kotlin.math.abs

object StreamRanker {
    const val SIZE_CAP_BYTES = 20L * 1024 * 1024 * 1024 // 20 GB hard cap on auto-picked sources

    /** A source the device can decode + display + stream (<= 20 GB). Auto-pick uses only these. */
    fun isAutoEligible(s: Stream, profile: DeviceProfile): Boolean =
        (s.codec == Codec.UNKNOWN || s.codec in profile.decodableCodecs) &&
            s.quality.rank <= profile.maxResolution.rank &&
            (s.sizeBytes == null || s.sizeBytes <= SIZE_CAP_BYTES)

    /**
     * Returns the FULL list (nothing dropped) sorted best-for-this-device first, so the manual
     * Sources list can still show every source. Ineligible (over-cap/undecodable/over-res) sink
     * to the bottom. [target] = min(user preferredQuality, profile.maxResolution).
     */
    fun rank(streams: List<Stream>, profile: DeviceProfile, target: Quality): List<Stream> =
        streams.sortedWith(
            compareByDescending<Stream> { isAutoEligible(it, profile) }
                .thenByDescending { it.isCached }
                .thenByDescending { it.languageScore }
                .thenByDescending { qualityFit(it.quality, target) }
                .thenByDescending { streamability(it) }
                .thenByDescending { seederScore(it.seeders) },
        )

    /** Back-compat overload (existing callers/tests): permissive profile, target = preferred or max. */
    fun rank(streams: List<Stream>, preferred: Quality? = null): List<Stream> =
        rank(streams, DeviceProfile.PERMISSIVE, preferred ?: Quality.UHD_2160)

    // Highest quality at/under target wins; anything over target is demoted below at-target picks.
    private fun qualityFit(q: Quality, target: Quality): Int =
        if (q.rank <= target.rank) q.rank else target.rank - 1

    // Within a resolution: prefer a sane bitrate band — penalise both bloat and ultra-low. Higher = better.
    private fun streamability(s: Stream): Int {
        val size = s.sizeBytes ?: return 0 // unknown size: neutral
        val gb = size.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val ideal = when (s.quality) {
            Quality.UHD_2160 -> 15.0
            Quality.HD_1080 -> 8.0
            Quality.HD_720 -> 3.0
            else -> 2.0
        }
        return -abs(gb - ideal).toInt() // 0 at ideal, more negative as it deviates
    }

    private fun seederScore(seeders: Int?): Int = when {
        seeders == null -> 0
        seeders <= 1 -> -1 // 1-seeder penalty (AutoStream)
        else -> seeders
    }
}
