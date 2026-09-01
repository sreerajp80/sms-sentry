package `in`.sreerajp.sms_sentry.engine

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import java.util.Locale

/**
 * Categorization of entities found within SMS message text.
 */
enum class EntityType {
    OTP,
    TRACKING_ID,
    AMOUNT,
    URL,
    ACCOUNT_NUMBER,
    PHONE_NUMBER,
    REFERENCE_ID
}

/**
 * Visual highlighting tier.
 * Primary: OTP, Tracking ID, Amount (Amber / Gold, high emphasis)
 * Secondary: URL, Account Number, Phone Number, Reference ID (Teal / Cyan / Blue, medium emphasis)
 */
enum class HighlightTier {
    PRIMARY,
    SECONDARY
}

/**
 * An extracted entity with its string value, character range in the message body, and tier.
 */
data class ExtractedEntity(
    val type: EntityType,
    val value: String,
    val rawMatch: String,
    val start: Int,
    val end: Int,
    val tier: HighlightTier
)

object MessageEntityExtractor {

    private val OTP_KEYWORDS = listOf(
        "otp", "verification", "code", "passcode", "pin", "password", "security", "one-time", "secret",
        "ലോഗിൻ", "ഒ.ടി.പി", "പാസ്‌വേഡ്", "രഹസ്യകോഡ്"
    )

    private val INVALID_PREFIX_REGEX = Regex(
        "(?i)(?:\\b(?:a/c|acct|account|card|uan|id|ref|txn|transaction|refid|reference|otp-id|otpid|number|no|ending in|ending with|rs|inr|usd)\\b[\\s:-]*|\\$[\\s]*|₹[\\s]*|[Xx\\*·•\\s-]*[Xx\\*·•]+[\\s-]*)$"
    )

    private val INVALID_SUFFIX_REGEX = Regex(
        "^(?i)[\\s:-]*(?:gb|mb|kb|tb|mah|hz|ghz|rs|inr|usd|pts|points|mins|min|sec|seconds|days|hours|weeks|months|yr|years|am|pm|v|w|kw|l|ml|kg|g|km|m|cm|inch|in|ft|yards|miles|per|percent|%|units|items|pcs|pieces)\\b"
    )

    // Tracking / Dispatch regex: matches patterns like "tracking No.5001241978590", "AWB 123456", "Tracking ID: 12345", "consignment no ..."
    private val TRACKING_REGEX = Regex(
        "(?i)(?:\\b(?:tracking\\s*(?:no\\.?|id|num\\.?|number|code|#)?|track\\s*(?:no\\.?|id|num\\.?|number|code|#|:)|awb\\s*(?:no\\.?|id|num\\.?|number|code|#)?|consignment\\s*(?:no\\.?|id|num\\.?|number|#)?|waybill\\s*(?:no\\.?|id|num\\.?|number|#)?|docket\\s*(?:no\\.?|id|num\\.?|number|#)?|shipment\\s*(?:no\\.?|id|num\\.?|number|code|#)?|order\\s*(?:no\\.?|id|num\\.?|number|code|#))\\s*[:.\\-–—#]*\\s*)([a-zA-Z0-9\\-_]{6,35})\\b"
    )

    // Currency amount regex
    private val AMOUNT_REGEX = Regex(
        "(?i)(?:(?:rs\\.?|inr\\.?|₹|\\$|usd|eur|€|gbp|£)\\s*[:\\-]?\\s*[\\d,]+(?:\\.\\d{1,2})?)|(?:[\\d,]+(?:\\.\\d{1,2})?\\s*(?:rs\\.?|inr\\.?|₹|\\$|usd|eur|€|gbp|£))"
    )

    // URL regex
    private val URL_REGEX = Regex(
        "(?i)\\b(?:https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+|www\\.[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+\\.[a-z]{2,}(?:/[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)?|[a-zA-Z0-9\\-]+\\.(?:com|org|net|in|co|me|io|ai|app|link|page|gl|ly|to|biz|info|store|shop)(?:/[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)?)"
    )

    // Account / Card regex (e.g., "A/c x0731", "A/c ending 1234", "card xx1234", "A/c XXXXXXXX5432")
    private val ACCOUNT_REGEX = Regex(
        "(?i)(?:\\b(?:a/c|acct|account|card)\\s*(?:no\\.?|num\\.?|number|ending in|ending with|ending)?\\s*[:.\\-–—#]?\\s*)([xX*•·\\d\\s-]*[xX*•·\\d]\\d{2,6})\\b"
    )

    // UAN pattern
    private val UAN_REGEX = Regex(
        "(?i)(?:\\bUAN\\s*[:.\\-–—#]?\\s*)([xX*•·\\d\\s-]+\\d{4})\\b"
    )

    // Phone numbers (toll free 1800/1860, 10-12 digit landlines/mobiles, 6-digit shortcodes like 567676)
    private val TOLL_FREE_PHONE_REGEX = Regex(
        "(?i)\\b(?:1800|1860)[\\s\\-]?\\d{3}[\\s\\-]?\\d{3,4}\\b"
    )

    private val MOBILE_PHONE_REGEX = Regex(
        "(?i)\\b(?:(?:\\+91|0)[\\-\\s]?)?[6-9]\\d{9}\\b"
    )

    private val LANDLINE_PHONE_REGEX = Regex(
        "(?i)\\b0\\d{2,4}[\\-\\s]?\\d{6,8}\\b"
    )

    private val SHORTCODE_REGEX = Regex(
        "(?i)(?:(?:send|sms|call|to)\\s+)(\\b5\\d{5}\\b|\\b567676\\b|\\b\\d{5,6}\\b)"
    )

    /**
     * Extracts all key entities from a message body, resolving overlapping ranges.
     */
    fun extractEntities(body: String): List<ExtractedEntity> {
        if (body.isBlank()) return emptyList()

        val candidates = mutableListOf<ExtractedEntity>()

        // 1. Extract OTP (Primary)
        val otpMatch = detectOtpMatch(body)
        if (otpMatch != null) {
            val raw = otpMatch.value
            val cleanVal = if (raw.startsWith("G-", ignoreCase = true)) raw.substring(2) else raw
            candidates.add(
                ExtractedEntity(
                    type = EntityType.OTP,
                    value = cleanVal,
                    rawMatch = raw,
                    start = otpMatch.range.first,
                    end = otpMatch.range.last + 1,
                    tier = HighlightTier.PRIMARY
                )
            )
        }

        // 2. Extract Tracking IDs (Primary)
        TRACKING_REGEX.findAll(body).forEach { matchResult ->
            val group = matchResult.groups[1]
            if (group != null) {
                val trackingVal = group.value.trim()
                val lower = trackingVal.lowercase(Locale.ROOT)
                // Require at least one digit and exclude URLs or common words
                if (trackingVal.length >= 6 &&
                    !lower.startsWith("http") &&
                    !lower.startsWith("www") &&
                    trackingVal.any { it.isDigit() } &&
                    !isCommonWord(trackingVal)
                ) {
                    candidates.add(
                        ExtractedEntity(
                            type = EntityType.TRACKING_ID,
                            value = trackingVal,
                            rawMatch = matchResult.value,
                            start = group.range.first,
                            end = group.range.last + 1,
                            tier = HighlightTier.PRIMARY
                        )
                    )
                }
            }
        }

        // 3. Extract Amounts (Primary)
        AMOUNT_REGEX.findAll(body).forEach { matchResult ->
            candidates.add(
                ExtractedEntity(
                    type = EntityType.AMOUNT,
                    value = matchResult.value.trim(),
                    rawMatch = matchResult.value,
                    start = matchResult.range.first,
                    end = matchResult.range.last + 1,
                    tier = HighlightTier.PRIMARY
                )
            )
        }

        // 4. Extract URLs (Secondary)
        URL_REGEX.findAll(body).forEach { matchResult ->
            var raw = matchResult.value
            var endIdx = matchResult.range.last + 1
            // Clean trailing punctuation from URLs
            while (raw.isNotEmpty() && raw.last() in listOf('.', ',', '!', '?', ')', ']', ';', ':', '\'', '"')) {
                raw = raw.dropLast(1)
                endIdx--
            }
            if (raw.length >= 5) {
                candidates.add(
                    ExtractedEntity(
                        type = EntityType.URL,
                        value = raw,
                        rawMatch = raw,
                        start = matchResult.range.first,
                        end = endIdx,
                        tier = HighlightTier.SECONDARY
                    )
                )
            }
        }

        // 5. Extract Account Numbers (Secondary)
        ACCOUNT_REGEX.findAll(body).forEach { matchResult ->
            val group = matchResult.groups[1]
            if (group != null) {
                val acctVal = group.value.trim()
                candidates.add(
                    ExtractedEntity(
                        type = EntityType.ACCOUNT_NUMBER,
                        value = acctVal,
                        rawMatch = matchResult.value,
                        start = group.range.first,
                        end = group.range.last + 1,
                        tier = HighlightTier.SECONDARY
                    )
                )
            }
        }

        UAN_REGEX.findAll(body).forEach { matchResult ->
            val group = matchResult.groups[1]
            if (group != null) {
                candidates.add(
                    ExtractedEntity(
                        type = EntityType.ACCOUNT_NUMBER,
                        value = group.value.trim(),
                        rawMatch = matchResult.value,
                        start = group.range.first,
                        end = group.range.last + 1,
                        tier = HighlightTier.SECONDARY
                    )
                )
            }
        }

        // 6. Extract Phone numbers and Shortcodes (Secondary)
        TOLL_FREE_PHONE_REGEX.findAll(body).forEach { matchResult ->
            candidates.add(
                ExtractedEntity(
                    type = EntityType.PHONE_NUMBER,
                    value = matchResult.value.trim(),
                    rawMatch = matchResult.value,
                    start = matchResult.range.first,
                    end = matchResult.range.last + 1,
                    tier = HighlightTier.SECONDARY
                )
            )
        }

        MOBILE_PHONE_REGEX.findAll(body).forEach { matchResult ->
            candidates.add(
                ExtractedEntity(
                    type = EntityType.PHONE_NUMBER,
                    value = matchResult.value.trim(),
                    rawMatch = matchResult.value,
                    start = matchResult.range.first,
                    end = matchResult.range.last + 1,
                    tier = HighlightTier.SECONDARY
                )
            )
        }

        LANDLINE_PHONE_REGEX.findAll(body).forEach { matchResult ->
            candidates.add(
                ExtractedEntity(
                    type = EntityType.PHONE_NUMBER,
                    value = matchResult.value.trim(),
                    rawMatch = matchResult.value,
                    start = matchResult.range.first,
                    end = matchResult.range.last + 1,
                    tier = HighlightTier.SECONDARY
                )
            )
        }

        SHORTCODE_REGEX.findAll(body).forEach { matchResult ->
            val group = matchResult.groups[1]
            if (group != null) {
                candidates.add(
                    ExtractedEntity(
                        type = EntityType.PHONE_NUMBER,
                        value = group.value.trim(),
                        rawMatch = matchResult.value,
                        start = group.range.first,
                        end = group.range.last + 1,
                        tier = HighlightTier.SECONDARY
                    )
                )
            }
        }

        // Resolve overlaps: Primary wins over Secondary; longer spans win over shorter spans
        return resolveOverlaps(candidates)
    }

    private fun isCommonWord(word: String): Boolean {
        val lower = word.lowercase(Locale.ROOT)
        return lower in setOf("number", "orders", "shipped", "arrived", "delivery", "delivered", "package", "details", "update", "please", "thanks", "thankyou")
    }

    private fun resolveOverlaps(candidates: List<ExtractedEntity>): List<ExtractedEntity> {
        if (candidates.isEmpty()) return emptyList()

        // Sort by Priority: PRIMARY first, then longer length, then earlier start
        val sorted = candidates.sortedWith(
            compareBy<ExtractedEntity> { if (it.tier == HighlightTier.PRIMARY) 0 else 1 }
                .thenByDescending { it.end - it.start }
                .thenBy { it.start }
        )

        val accepted = mutableListOf<ExtractedEntity>()

        for (candidate in sorted) {
            val overlaps = accepted.any { existing ->
                candidate.start < existing.end && candidate.end > existing.start
            }
            if (!overlaps) {
                accepted.add(candidate)
            }
        }

        return accepted.sortedBy { it.start }
    }

    /**
     * Extracts OTP match including exact range in message body.
     */
    private fun detectOtpMatch(body: String): MatchResult? {
        val lower = body.lowercase(Locale.getDefault())
        if (OTP_KEYWORDS.none { lower.contains(it) }) return null

        val candidateMatches = Regex("(?i)\\b(?:G-)?\\d{4,8}\\b").findAll(body).toList()
        if (candidateMatches.isEmpty()) return null

        val validCandidates = candidateMatches.filter { match ->
            val start = match.range.first
            val end = match.range.last + 1
            val prefix = body.substring(0, start)
            val suffix = body.substring(end)

            if (INVALID_PREFIX_REGEX.containsMatchIn(prefix)) return@filter false
            if (INVALID_SUFFIX_REGEX.containsMatchIn(suffix)) return@filter false
            true
        }

        if (validCandidates.isEmpty()) return null

        if (validCandidates.size == 1) {
            return validCandidates.first()
        }

        val keywordIndices = mutableListOf<Int>()
        for (keyword in OTP_KEYWORDS) {
            var idx = lower.indexOf(keyword)
            while (idx != -1) {
                keywordIndices.add(idx)
                idx = lower.indexOf(keyword, idx + 1)
            }
        }

        return if (keywordIndices.isEmpty()) {
            validCandidates.first()
        } else {
            validCandidates.minByOrNull { match ->
                val candidateStart = match.range.first
                var minDistance = Int.MAX_VALUE
                for (kwIdx in keywordIndices) {
                    val dist = kotlin.math.abs(candidateStart - kwIdx)
                    if (dist < minDistance) {
                        minDistance = dist
                    }
                }
                minDistance
            }
        }
    }

    /**
     * Builds an AnnotatedString for a message body with primary and secondary entity styles applied.
     */
    fun buildAnnotatedMessageBody(
        body: String,
        isDarkTheme: Boolean = true
    ): AnnotatedString {
        if (body.isEmpty()) return AnnotatedString("")

        val entities = extractEntities(body)
        if (entities.isEmpty()) return AnnotatedString(body)

        val primaryColor = Color(0xFFE0A900)
        val primaryBg = Color(0xFFFFB300).copy(alpha = 0.18f)

        val secondaryColor = if (isDarkTheme) Color(0xFF4FC3F7) else Color(0xFF0288D1)
        val secondaryBg = if (isDarkTheme) Color(0xFF0288D1).copy(alpha = 0.15f) else Color(0xFF0288D1).copy(alpha = 0.10f)

        return buildAnnotatedString {
            var cursor = 0
            for (entity in entities) {
                if (entity.start > cursor) {
                    append(body.substring(cursor, entity.start))
                }

                val spanStyle = when (entity.tier) {
                    HighlightTier.PRIMARY -> SpanStyle(
                        color = primaryColor,
                        fontWeight = FontWeight.ExtraBold,
                        background = primaryBg
                    )
                    HighlightTier.SECONDARY -> SpanStyle(
                        color = secondaryColor,
                        fontWeight = FontWeight.SemiBold,
                        background = secondaryBg,
                        textDecoration = if (entity.type == EntityType.URL) TextDecoration.Underline else TextDecoration.None
                    )
                }

                if (entity.type == EntityType.URL) {
                    pushStringAnnotation(tag = "URL", annotation = entity.value)
                }

                withStyle(spanStyle) {
                    append(body.substring(entity.start, entity.end))
                }

                if (entity.type == EntityType.URL) {
                    pop()
                }

                cursor = entity.end
            }

            if (cursor < body.length) {
                append(body.substring(cursor))
            }
        }
    }
}
