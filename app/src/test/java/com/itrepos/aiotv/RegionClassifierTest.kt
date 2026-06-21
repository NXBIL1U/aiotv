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
    // The bare LATAM code "DO" was dropped as too collision-prone, so this no longer
    // matches a LATAM rule and falls through to OTHER (still reachable via filter/search).
    @Test fun latamDo() = assertRegion("OTHER", "FIFA-DO| CDN DEPORTES")
    @Test fun latamArgentina() = assertRegion("LATAM", "AR | DEPORTES")
    @Test fun latamMexico() = assertRegion("LATAM", "MX | CANALES")
    @Test fun latino() = assertRegion("LATAM", "VIP | LATINO")
    @Test fun ukSports() = assertRegion("UK", "UK SPORTS")
    @Test fun emptyString() = assertRegion("OTHER", "")

    // Regression: the high-collision bare code "CO" (Colombia) must NOT pull an English
    // category like "DISCOVERY CO" into LATAM (out of the default scope).
    @Test fun discoveryCoIsNotLatam() {
        val actual = RegionClassifier.classify("DISCOVERY CO")
        assert(actual != "LATAM") { "Expected NOT LATAM for \"DISCOVERY CO\" but was $actual" }
    }

    // Regression: non-English "24/7" bundles must NOT be tagged EN (would pollute the
    // English default scope). Only an explicit "ENGLISH" marker is EN.
    @Test fun twentyFourSevenChinaIsNotEn() = assertRegion("OTHER", "24/7 | CHINA")
    @Test fun twentyFourSevenFrenchIsNotEn() = assertRegion("OTHER", "24/7 | FRENCH")
}
