package com.itrepos.aiotv.data.remote.iptv

/**
 * Pure classifier: maps a category name (or channel name as fallback) to one of:
 * US, UK, EN, LATAM, EU, MENA, OTHER.
 *
 * Rules are applied in order; first match wins. Matching is done on the upper-cased
 * input so comparisons are case-insensitive. This is intentionally best-effort —
 * unrecognised input returns OTHER, and global channel search always ignores region
 * so no channel is ever unreachable.
 */
object RegionClassifier {

    fun classify(name: String): String {
        if (name.isBlank()) return "OTHER"
        val upper = name.uppercase()

        // LATAM must be checked before US so "LATIN AMERICA" → LATAM not US,
        // and before AR/AF so "AR" alone (Argentina) can land in LATAM.
        if (upper.contains("LATIN AMERICA")) return "LATAM"
        if (upper.contains("LATINO")) return "LATAM"

        // Country codes that unambiguously indicate LATAM (order matters — check
        // before the generic "AMERICA" catch below).
        // We match as whole tokens (surrounded by non-alpha or at string boundary)
        // to avoid false positives (e.g. "AMERICAN" should → US, not LATAM).
        if (containsToken(upper, "DO")) return "LATAM"   // Dominican Republic
        if (containsToken(upper, "EC")) return "LATAM"   // Ecuador
        if (containsToken(upper, "HN")) return "LATAM"   // Honduras
        if (containsToken(upper, "HT")) return "LATAM"   // Haiti
        if (containsToken(upper, "PA")) return "LATAM"   // Panama
        if (containsToken(upper, "MX")) return "LATAM"   // Mexico
        if (containsToken(upper, "CO")) return "LATAM"   // Colombia
        // AR by itself = Argentina (LATAM); but "AR/AF" is handled as MENA below.
        // We defer plain "AR" to MENA check so "AR/AF" wins; if no MENA token
        // matches but "AR" alone is present → LATAM.

        // MENA — check before US so "AR/AF" (combined Arabic/Africa bucket) → MENA.
        if (upper.contains("AR/AF")) return "MENA"
        if (upper.contains("ARAB")) return "MENA"
        if (upper.contains("AFRICA")) return "MENA"
        if (upper.contains("ASIAN")) return "MENA"
        // "AF" as a lone token (Afghanistan / Africa) → MENA
        if (containsToken(upper, "AF")) return "MENA"

        // Now it is safe to check standalone "AR" for Argentina → LATAM
        if (containsToken(upper, "AR")) return "LATAM"

        // US
        if (containsToken(upper, "USA")) return "US"
        if (containsToken(upper, "US")) return "US"
        if (upper.contains("AMERICAN")) return "US"
        // "AMERICA" alone (without LATIN) → LATAM per spec §6 table
        if (upper.contains("AMERICA")) return "LATAM"

        // UK
        if (containsToken(upper, "UK")) return "UK"
        if (containsToken(upper, "GB")) return "UK"
        if (upper.contains("UNITED KINGDOM")) return "UK"
        if (upper.contains("BRITISH")) return "UK"
        if (upper.contains("ENGLAND")) return "UK"
        if (upper.contains("IRELAND")) return "UK"
        if (upper.contains("IRISH")) return "UK"

        // EN — only an explicit "ENGLISH" marker. Do NOT treat all "24/7" bundles as English:
        // the provider has "24/7 | CHINA", "24/7 | FRENCH", etc., which must not pollute the
        // English default scope. Unlabelled bundles fall through to OTHER (reachable via filter/search).
        if (upper.contains("ENGLISH")) return "EN"

        // EU
        if (upper.contains("EUROP")) return "EU"    // "EUROPE", "WEST EUROP", "EAST EUROP"

        return "OTHER"
    }

    /**
     * Returns true if [token] appears in [upper] as a whole "word" — i.e. the characters
     * immediately before and after the token (if they exist) are non-alphanumeric. This
     * prevents "US" from matching inside "MUSIC" or "RUSSIAN".
     */
    private fun containsToken(upper: String, token: String): Boolean {
        var idx = upper.indexOf(token)
        while (idx != -1) {
            val before = if (idx == 0) true else !upper[idx - 1].isLetterOrDigit()
            val after = if (idx + token.length >= upper.length) true
                        else !upper[idx + token.length].isLetterOrDigit()
            if (before && after) return true
            idx = upper.indexOf(token, idx + 1)
        }
        return false
    }
}
