package com.itrepos.aiotv

import com.itrepos.aiotv.domain.StreamRequestTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRequestTuningTest {
    private val tok = "f64ec1c1-d30c-4aa2-8207-6eda8a579a25"

    @Test fun raisesLimitAndPreservesTokenAndOptions() {
        val base = "https://torrentio.strem.fun/sort=qualitysize%7Climit=5%7Cqualityfilter=cam,screener,3d%7Cdebridoptions=nocatalog,nodownloadlinks%7Ctorbox=$tok"
        val out = StreamRequestTuning.tuneStreamBase(base)
        assertTrue("limit raised", out.contains("limit=30"))
        assertTrue("no stale limit=5", !out.contains("limit=5"))
        assertTrue("token preserved", out.contains("torbox=$tok"))
        assertTrue("sort preserved", out.contains("sort=qualitysize"))
        assertTrue("qualityfilter preserved", out.contains("qualityfilter=cam,screener,3d"))
    }

    @Test fun injectsLimitWhenAbsent() {
        val base = "https://torrentio.strem.fun/torbox=$tok"
        val out = StreamRequestTuning.tuneStreamBase(base)
        assertTrue("limit injected", out.contains("limit=30"))
        assertTrue("token preserved", out.contains("torbox=$tok"))
    }

    @Test fun leavesNonTorrentioUnchanged() {
        val base = "https://other-addon.example.com/some/path"
        assertEquals(base, StreamRequestTuning.tuneStreamBase(base))
    }
}
