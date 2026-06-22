package com.itrepos.aiotv.domain.playback

import com.itrepos.aiotv.domain.model.Codec
import com.itrepos.aiotv.domain.model.Quality

/** What the current device can actually decode and display. */
data class DeviceProfile(
    val maxResolution: Quality,
    val decodableCodecs: Set<Codec>,
    val hdrCapable: Boolean,
) {
    companion object {
        /** Used as a safe fallback when probing fails, and by the back-compat ranker overload. */
        val PERMISSIVE = DeviceProfile(
            maxResolution = Quality.UHD_2160,
            decodableCodecs = setOf(Codec.AVC, Codec.HEVC, Codec.AV1),
            hdrCapable = true,
        )
    }
}
