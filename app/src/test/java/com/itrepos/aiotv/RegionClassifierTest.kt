package com.itrepos.aiotv

import com.itrepos.aiotv.data.remote.iptv.RegionClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class RegionClassifierTest {

    private fun assertRegion(expected: String, input: String) {
        val actual = RegionClassifier.classify(input)
        assertEquals("classify(\"$input\")", expected, actual)
    }

    @Test fun usaSports() = assertRegion("US", "AM | USA SPORTS")
    @Test fun usaNews() = assertRegion("US", "AM | USA NEWS")
    @Test fun englishBundle() = assertRegion("EN", "24/7 | ENGLISH")
    @Test fun euWest() = assertRegion("EU", "VIP | FIFA WC26 WEST EUROP")
    @Test fun menaArAf() = assertRegion("MENA", "VIP | FIFA WC26 AR/AF")
    @Test fun latamDo() = assertRegion("LATAM", "FIFA-DO| CDN DEPORTES")
    @Test fun ukSports() = assertRegion("UK", "UK SPORTS")
    @Test fun emptyString() = assertRegion("OTHER", "")

    // Regression: non-English "24/7" bundles must NOT be tagged EN (would pollute the
    // English default scope). Only an explicit "ENGLISH" marker is EN.
    @Test fun twentyFourSevenChinaIsNotEn() = assertRegion("OTHER", "24/7 | CHINA")
    @Test fun twentyFourSevenFrenchIsNotEn() = assertRegion("OTHER", "24/7 | FRENCH")
}
