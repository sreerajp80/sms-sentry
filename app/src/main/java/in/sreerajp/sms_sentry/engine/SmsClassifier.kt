package `in`.sreerajp.sms_sentry.engine

import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

data class ClassificationResult(
    val category: String, // "Personal", "Promotions", "Others", "Spam"
    val isBlocked: Boolean,
    // Content flags, independent of [category]: a finance/reminder message now lives under
    // "Others" but is still flagged so the ledger / reminders features keep their data.
    val isFinance: Boolean = false,
    val isReminder: Boolean = false,
    val bankName: String? = null,
    val amount: Double? = null,
    val isCredit: Boolean? = null,
    val balance: Double? = null,
    val dueDate: Long? = null,
    val reminderTitle: String? = null
)

object SmsClassifier {

    // ---- Spam scoring engine -------------------------------------------------------------
    // Spam is decided by a weighted score, not a single-keyword trip. Every scam/urgency signal
    // is matched on word/phrase boundaries (see [boundaryPattern]/[spamScore]) so a substring
    // like "invest" inside "Investment" can never flag a message. A message is Spam only when the
    // score reaches [SPAM_THRESHOLD]; a trusted institutional sender or genuine transactional
    // context pulls the score back down. This is only the offline fallback — custom CONTACT and
    // KEYWORD rules in [classify] run first and keep full priority.
    private const val SPAM_THRESHOLD = 3

    // Strong scam / fraud indicators — curated phrases plus a few unambiguous whole words. Each
    // distinct hit adds [SCAM_WEIGHT]. Bare generic finance words (e.g. "invest") are deliberately
    // absent so real investment/statement messages are never spam.
    private const val SCAM_WEIGHT = 3
    private val SCAM_PHRASES = listOf(
        "lottery", "jackpot", "casino", "you won", "you have won", "won a prize", "won rs",
        "claim your prize", "claim your reward", "claim your gift", "free bonus", "lucky winner",
        "lucky draw", "selected winner", "guaranteed returns", "double your money", "get rich",
        "work from home", "earn money", "earn daily", "part time job", "loan approved",
        "pre-approved loan", "kyc suspended", "account suspended", "crypto", "bitcoin",
        "electricity will be disconnected", "power will be disconnected", "electricity power will be cut",
        "download apk", "install apk", ".apk", "pan card blocked", "sim blocked", "sim deactivated",
        "aadhaar suspended", "debit card blocked", "telegram task", "daily income", "earn daily income",
        "whatsapp job", "contact on whatsapp"
    )

    // URL shorteners commonly used to hide scam landing pages. Weak on their own (+[LINK_WEIGHT]);
    // matched as plain substrings since these tokens are distinctive.
    private const val LINK_WEIGHT = 1
    private val URL_SHORTENERS = listOf(
        "bit.ly", "tinyurl", "goo.gl", "t.co/", "is.gd", "cutt.ly", "rb.gy", "tiny.cc", "ow.ly",
        "wa.me/", "t.me/", "bit.do", "v.gd", "s.id", "qr.ae", "shorturl.at"
    )

    // High-pressure urgency cues. Weak on their own (+[URGENCY_WEIGHT]); meaningful only when
    // stacked with scam bait.
    private const val URGENCY_WEIGHT = 1
    private val URGENCY_PHRASES = listOf(
        "act now", "hurry", "last chance", "expires today", "don't miss", "dont miss",
        "urgent", "immediately", "limited seats", "offer ends", "immediate action required",
        "within 24 hours", "account will be closed", "action required"
    )

    // Known legitimate bulk senders (banks, pensions, insurers, govt, telecom). A DLT header
    // matching one of these is a trusted institution and earns a strong negative weight. This is
    // only a default safeguard — it is strictly weaker than an explicit user CONTACT->Spam rule,
    // which short-circuits before scoring.
    private const val TRUSTED_WEIGHT = 5
    private val TRUSTED_ENTITIES = listOf(
        // Banks
        "HDFC", "SBIBNK", "SBI", "ICICI", "AXIS", "HSBC", "CITI", "KOTAK", "PNB", "BOI",
        "YESBK", "INDUS", "CANBNK", "CANARA", "UNIONB", "UNION", "IDFC", "IDFCFIRST",
        "RBLBNK", "RBL", "FEDERAL", "FEDBNK", "IOB", "IOBBNK", "CBI", "PAYTM", "PAYTMBK",
        "AIRTEL", "AIRPBNK", "IPPB", "BANDHAN", "BOB", "BARODA", "SCB", "STANCHAR",
        // NPS / pensions
        "PTNNPS", "NPSCRA", "PROTEAN", "NSDL", "PFRDA", "CAMSKRA",
        // Insurers
        "NIALTD", "NIACL", "LICIND", "HDFCLI", "ICICIP", "SBILIF", "MAXLIF", "STARHE",
        // Govt / utility / telecom
        "MORTH", "ITDCPC", "UIDAI", "EPFO", "IRCTC", "AIRTEL", "JIONET", "VODAFON", "BSNL"
    )

    // Transactional / informational context a real scam almost never carries. When present it
    // pulls the score down by [CONTEXT_WEIGHT] (once), so a genuine bank/statement/OTP/policy
    // message is not spam even if it happens to use a strong-sounding word.
    private const val CONTEXT_WEIGHT = 2
    private val LEGIT_CONTEXT_KEYWORDS = listOf(
        "debited", "credited", "a/c", "account", "available bal", "avail bal", "avl bal", "avl balance",
        "balance", "net bal", "clear bal", "updated bal", "statement", "policy", "premium", "otp",
        "one time password", "transaction", "txn", "e-mandate", "mandate", "installment", "investment value",
        "portfolio", "tier i", "tier ii", "deducted", "charged", "withdrawal", "deposited", "auto-debited",
        "auto debited"
    )

    // Boundary-anchored patterns for the scam/urgency phrases, compiled once. "\b…\b" prevents
    // substring accidents (e.g. "invest" matching "investment").
    private fun boundaryPattern(term: String): Pattern =
        Pattern.compile("\\b" + Pattern.quote(term) + "\\b", Pattern.CASE_INSENSITIVE)

    private val scamPatterns = SCAM_PHRASES.map(::boundaryPattern)
    private val urgencyPatterns = URGENCY_PHRASES.map(::boundaryPattern)

    // Marketing / advertising patterns → Promotions (legitimate offers, not scams).
    private val PROMO_KEYWORDS = listOf(
        "offer", "discount", "promo", "cashback", "sale", "deal", "% off", "percent off",
        "coupon", "voucher", "reward", "gift card", "limited time", "buy now", "shop now",
        "lowest price", "flat ", "sign up now", "subscribe"
    )

    // Money-movement tags → flags the message as finance (ledger), category becomes "Others".
    private val MONEY_KEYWORDS = listOf(
        "debited", "credited", "spent", "withdrawn", "withdrew", "withdrawal", "paid", "received", "transfer",
        "transferred", "txn", "transaction", "a/c balance", "available bal", "avail bal", "avl bal",
        "avl balance", "net bal", "bank", "payment of", "deducted", "charged", "auto-debited",
        "auto debited", "deposited", "deposit", "spent on", "added to wallet", "paid to", "paid via"
    )

    // Reminder tags → flags the message as a reminder (due dates), category becomes "Others".
    // Forward-looking obligations only. Deliberately excludes "scheduled" (appears in *past*
    // transaction confirmations like "Txn … is Scheduled for <past date>") and bare "due".
    private val REMINDER_KEYWORDS = listOf(
        "due on", "due date", "due by", "payment due", "pay by", "last date", "bill due",
        "outstanding", "overdue", "expires on", "expiring", "expiry", "valid till",
        "valid until", "validity", "renew", "renewal", "recharge before", "appointment",
        "reminder", "kindly pay", "please pay", "pay before", "pay bill"
    )

    // Strong, unambiguous "you must do something by a date" phrases. Used to override the
    // receipt exclusion below: a completed-transaction SMS that *also* carries one of these is
    // still a genuine reminder. Excludes the soft "reminder"/"appointment" cues on purpose.
    private val STRONG_DUE_KEYWORDS = listOf(
        "due on", "due date", "due by", "payment due", "pay by", "last date", "bill due",
        "outstanding", "overdue", "expires on", "expiring", "expiry", "valid till",
        "valid until", "validity", "renew", "renewal", "recharge before", "kindly pay",
        "please pay", "pay before", "pay bill"
    )

    // Completed-transaction / receipt markers. When one of these is present and there is no
    // STRONG_DUE_KEYWORD, the message is a confirmation — not an actionable reminder.
    private val RECEIPT_MARKERS = listOf(
        "credited", "debited", "thank you for the payment", "payment received",
        "received your payment", "txn ref", "ref no", " successful", "has been credited",
        "is credited"
    )

    // Unambiguous advertising markers. A genuine bank/transaction SMS won't contain these, so
    // when they co-occur with a money verb (e.g. a coupon that says "Rs.300 credited!"), the
    // message is a promotion, not a ledger transaction.
    private val COUPON_OFFER_MARKERS = listOf(
        "coupon", "voucher", "% off", "percent off", "use code", "promo code",
        "shop now", "shop at", "buy now"
    )

    // Non-money transactional / informational alerts (OTP / delivery / booking) → "Others".
    private val SERVICES_KEYWORDS = listOf(
        "otp", "one time password", "verification code", "verify", "delivered",
        "out for delivery", "shipped", "dispatched", "order", "booking", "booked",
        "ticket", "confirmed", "recharge", "pnr", "boarding", "flight", "train",
        "tracking", "service request", "ticket no"
    )

    // Regex compiled for speed.
    // A currency-tagged number anywhere in the body. Prefix currency ("Rs. 500", "INR 1,200", "₹50")
    // and suffix currency ("500 Rs", "1000 INR") are supported with optional colon/dash delimiters.
    // Indian grouping ('13,97,889') and trailing '/-' are tolerated.
    private val currencyRegex = Pattern.compile(
        "(?:(?:rs\\.?|inr\\.?|₹|\\$|usd|eur|€|gbp|£)\\s*[:\\-]?\\s*([\\d,]+(?:\\.\\d{1,2})?))|([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:rs\\.?|inr\\.?|₹|\\$|usd|eur|€|gbp|£)",
        Pattern.CASE_INSENSITIVE
    )
    // Balance context keyword; the balance figure is the first currency number that follows it.
    private val balanceKeywordRegex = Pattern.compile(
        "avail(?:able)?\\.?\\s*bal(?:ance)?|avl\\.?\\s*bal(?:ance)?|a/c\\s*bal(?:ance)?|closing\\s*bal(?:ance)?|net\\s*bal(?:ance)?|clear\\s*bal(?:ance)?|updated\\s*bal(?:ance)?|total\\s*bal(?:ance)?|bal(?:ance)?",
        Pattern.CASE_INSENSITIVE
    )
    // Money-movement verbs; used both to detect direction and to locate the transaction amount.
    private val movementRegex = Pattern.compile(
        "credited|debited|spent|withdrawn|withdrew|withdrawal|paid|sent|received|contribution|transferred|purchase|refunded|refund|deposited|deposit|deducted|charged|auto-debited|auto debited",
        Pattern.CASE_INSENSITIVE
    )
    // Movement verbs that mean money came IN. Anything else (debited/spent/paid/deducted/…) is a debit.
    private val creditWords = setOf("credited", "received", "contribution", "refunded", "refund", "deposited", "deposit")

    // Simple date extractors.
    // Date separator characters: ASCII space/dot/slash/hyphen/comma PLUS the Unicode dashes that
    // bulk SMS (e.g. MoRTH/government senders) often use instead of an ASCII hyphen —
    // non-breaking hyphen (U+2011), figure dash (U+2012), en dash (U+2013), em dash (U+2014),
    // and minus sign (U+2212).
    private const val DATE_SEP = "\\s./\\-,\\u2011\\u2012\\u2013\\u2014\\u2212"
    // ISO yyyy-MM-dd (e.g. "validity is 2027-06-12"). Matched first so the 4-digit year is not
    // mistaken for a DD-MM-YY date by dateRegex1 (which would turn 2027-06-12 into 27-06-12).
    private val isoDateRegex = Pattern.compile(
        "(\\d{4}[$DATE_SEP]\\d{1,2}[$DATE_SEP]\\d{1,2})"
    )
    private val dateRegex1 = Pattern.compile(
        "(\\d{1,2}[$DATE_SEP]\\d{1,2}[$DATE_SEP]\\d{2,4})" // DD-MM-YYYY or DD/MM/YY
    )
    // Alpha month with flexible separators, supporting both day-first ("16 Jun 2026", "25th May",
    // "16–Jun–2026") and month-first ("May 25, 2026", "June 16") formats.
    private val dateRegexAlphaDayFirst = Pattern.compile(
        "(\\b\\d{1,2}(?:st|nd|rd|th)?[$DATE_SEP]+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[$DATE_SEP]*\\d{0,4}\\b)",
        Pattern.CASE_INSENSITIVE
    )
    private val dateRegexAlphaMonthFirst = Pattern.compile(
        "(\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[$DATE_SEP]+\\d{1,2}(?:st|nd|rd|th)?(?:[$DATE_SEP]+\\d{2,4})?\\b)",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Map any category string — including legacy values from older installs / seeded rules
     * (`Accounts`, `Reminder`, `Services`) — onto the current four-category taxonomy.
     * Finance/reminder behaviour is no longer carried by the category (see [ClassificationResult]
     * flags), so every legacy non-spam/personal bucket collapses into `Others`.
     */
    fun normalizeCategory(raw: String): String = when (raw) {
        "Personal" -> "Personal"
        "Promotions" -> "Promotions"
        "Spam", "Blocked" -> "Spam"
        "Others" -> "Others"
        else -> "Others" // Accounts, Reminder, Services, and any unknown legacy value
    }

    fun classify(
        sender: String,
        body: String,
        customKeywords: List<Pair<String, String>>, // list of Keyword -> targetCategory
        customContacts: Map<String, String>, // Map of Phone -> targetCategory (or "Blocked")
        // Reference time for reminder date logic (future/past selection, year-less alpha-date year,
        // and the tomorrow/today/3-day fallback). Defaults to wall-clock; callers should pass the
        // message's own timestamp so imported/synced/re-classified messages are dated correctly.
        referenceTime: Long = System.currentTimeMillis()
    ): ClassificationResult {
        val normalizedBody = body.lowercase(Locale.ROOT)
        val normalizedSender = sender.trim().lowercase(Locale.ROOT)

        // 1. Check custom contacts rules
        var allowlisted = false
        for ((contact, category) in customContacts) {
            val normalizedContact = contact.trim().lowercase(Locale.ROOT)
            if (normalizedSender.contains(normalizedContact) || normalizedContact.contains(normalizedSender)) {
                when (category) {
                    "Spam", "Blocked" ->
                        return ClassificationResult(category = "Spam", isBlocked = true)
                    // Allowlist: never auto-spam this sender, but still classify normally below.
                    "NotSpam" -> allowlisted = true
                    else -> return runExtractions(normalizeCategory(category), body, sender, referenceTime = referenceTime)
                }
            }
        }

        // 2. Check custom keyword rules
        for ((kw, cat) in customKeywords) {
            if (normalizedBody.contains(kw.lowercase(Locale.ROOT))) {
                val isBlocked = (cat == "Spam" || cat == "Blocked")
                val finalCat = if (isBlocked) "Spam" else normalizeCategory(cat)
                return runExtractions(finalCat, body, sender, isBlocked, referenceTime)
            }
        }

        // 3. Fallback to basic heuristics (offline classification rules)

        // Is it SPAM? Weighted, sender-aware score instead of a single-keyword trip (see the
        // scoring-engine block above). Only the offline fallback: custom CONTACT/KEYWORD rules
        // handled above already had their say, and the NotSpam allowlist still fully overrides.
        if (!allowlisted && spamScore(sender, normalizedBody) >= SPAM_THRESHOLD) {
            return ClassificationResult(category = "Spam", isBlocked = true)
        }

        // Priority: money movement and reminders take precedence (they drive ledger/reminders),
        // then marketing (Promotions), then other transactional alerts (Services) — all of the
        // non-Personal, non-Promotions buckets surface under "Others".
        val containsMoneyKeyword = MONEY_KEYWORDS.any { normalizedBody.contains(it) }
        val containsReminderKeyword = REMINDER_KEYWORDS.any { normalizedBody.contains(it) }
        val containsPromoKeyword = PROMO_KEYWORDS.any { normalizedBody.contains(it) }
        val containsServicesKeyword = SERVICES_KEYWORDS.any { normalizedBody.contains(it) }
        val isPromoOffer = isPromotionalOffer(normalizedBody)

        return when {
            // A coupon/offer that happens to mention a money verb ("Rs.300 credited!") is
            // marketing, not a transaction — promo wins over money in this case only.
            isPromoOffer -> runExtractions("Promotions", body, sender, referenceTime = referenceTime)
            containsMoneyKeyword -> runExtractions("Others", body, sender, referenceTime = referenceTime)
            containsReminderKeyword -> runExtractions("Others", body, sender, referenceTime = referenceTime)
            containsPromoKeyword -> runExtractions("Promotions", body, sender, referenceTime = referenceTime)
            containsServicesKeyword -> runExtractions("Others", body, sender, referenceTime = referenceTime)
            // Personal is a property of the sender, not the body: only an SMS from a saved
            // contact or a dialable mobile number is Personal. A saved contact is just a phone
            // number in the address book, so both reduce to "the sender looks like a phone
            // number" here. Alphanumeric DLT headers / short codes fall back to Others.
            looksLikePhoneNumber(sender) -> runExtractions("Personal", body, sender, referenceTime = referenceTime)
            else -> runExtractions("Others", body, sender, referenceTime = referenceTime)
        }
    }

    /**
     * True when [sender] is plausibly a dialable number (digits with optional + / spaces /
     * dashes / parens). Mirrors `ContactNameResolver.isPhoneNumberLike`; kept local so the
     * classifier stays free of any `android.*` dependency.
     */
    private fun looksLikePhoneNumber(sender: String): Boolean {
        var digits = 0
        for (ch in sender) {
            when {
                ch.isDigit() -> digits++
                ch == '+' || ch == ' ' || ch == '-' || ch == '(' || ch == ')' -> {}
                else -> return false
            }
        }
        return digits >= 3
    }

    /**
     * Weighted spam score for the offline fallback. Positive signals (scam phrases, shortened
     * links, urgency) push toward Spam; a trusted institutional sender and genuine transactional
     * context pull it back down. The caller marks Spam only when the score reaches
     * [SPAM_THRESHOLD]. [normalizedBody] must already be lower-cased.
     */
    private fun spamScore(sender: String, normalizedBody: String): Int {
        var score = 0

        // Positive signals.
        score += scamPatterns.count { it.matcher(normalizedBody).find() } * SCAM_WEIGHT
        if (URL_SHORTENERS.any { normalizedBody.contains(it) }) score += LINK_WEIGHT
        if (urgencyPatterns.any { it.matcher(normalizedBody).find() }) score += URGENCY_WEIGHT

        // Negative signals (each applied once).
        if (isTrustedSender(sender)) score -= TRUSTED_WEIGHT
        if (LEGIT_CONTEXT_KEYWORDS.any { normalizedBody.contains(it) }) score -= CONTEXT_WEIGHT

        return score
    }

    /**
     * True when [sender] is a known legitimate bulk sender (bank / pension / insurer / govt /
     * telecom DLT header). A purely dialable number is a person, never an institution, so it is
     * not trusted here — body signals alone decide for those.
     */
    private fun isTrustedSender(sender: String): Boolean {
        if (looksLikePhoneNumber(sender)) return false
        val cleaned = sender.uppercase(Locale.ROOT).replace(Regex("[^A-Z]"), "")
        if (cleaned.isEmpty()) return false
        return TRUSTED_ENTITIES.any { cleaned.contains(it) }
    }

    private fun runExtractions(
        category: String,
        body: String,
        sender: String,
        isBlocked: Boolean = false,
        referenceTime: Long = System.currentTimeMillis()
    ): ClassificationResult {
        if (category == "Spam" || isBlocked) {
            return ClassificationResult(category = "Spam", isBlocked = true)
        }

        val normalizedBody = body.lowercase(Locale.ROOT)

        // Content flags are derived from the body, not the (collapsed) category, so a finance or
        // reminder message still feeds the ledger / reminders even though it sits under "Others".
        // A promotional coupon is never a ledger transaction, even if it says "credited".
        val isFinance = MONEY_KEYWORDS.any { normalizedBody.contains(it) } &&
                !isPromotionalOffer(normalizedBody)
        // A message is a reminder only when it carries a forward-looking obligation keyword and
        // is not a marketing offer. A completed-transaction receipt ("credited", "payment
        // received", …) is excluded unless it also carries a strong "do this by <date>" phrase.
        val hasReminderKeyword = REMINDER_KEYWORDS.any { normalizedBody.contains(it) }
        val hasStrongDuePhrase = STRONG_DUE_KEYWORDS.any { normalizedBody.contains(it) }
        val isReceipt = RECEIPT_MARKERS.any { normalizedBody.contains(it) }
        val isReminder = hasReminderKeyword && !isPromotionalOffer(normalizedBody) &&
                (!isReceipt || hasStrongDuePhrase)

        var bankName: String? = null
        var amount: Double? = null
        var isCredit: Boolean? = null
        var balance: Double? = null
        var dueDate: Long? = null
        var reminderTitle: String? = null

        if (isFinance) {
            bankName = discoverBank(sender, body)
            val fields = extractFinanceFields(body)
            amount = fields.amount
            isCredit = fields.isCredit
            balance = fields.balance
        }

        if (isReminder) {
            reminderTitle = "Bill/Task: " + (if (sender.length < 15) sender else sender.take(8) + "...")
            // Collect every date the body mentions, then pick the most reminder-relevant one:
            // the earliest *future* date (the actual deadline), falling back to the latest past
            // date only when no future date exists. This keeps a receipt's past transaction date
            // from being chosen when a real due/expiry date is also present.
            val candidates = mutableListOf<Long>()

            // ISO yyyy-MM-dd first (so the 4-digit year isn't mis-read as a DD-MM-YY date).
            val matcherIso = isoDateRegex.matcher(body)
            while (matcherIso.find()) {
                parseDate(matcherIso.group(1) ?: "")?.let { candidates.add(it) }
            }

            // DD-MM-YYYY / DD-MM-YY formats.
            val matcher1 = dateRegex1.matcher(body)
            while (matcher1.find()) {
                parseDate(matcher1.group(1) ?: "")?.let { candidates.add(it) }
            }

            // Alpha month format with day-first or month-first (e.g., 25th May 2026, May 25 2026, 16-Jun-2026).
            val matcher2 = dateRegexAlphaDayFirst.matcher(body)
            while (matcher2.find()) {
                parseAlphaDate(matcher2.group(1) ?: "", referenceTime)?.let { candidates.add(it) }
            }
            val matcher3 = dateRegexAlphaMonthFirst.matcher(body)
            while (matcher3.find()) {
                parseAlphaDate(matcher3.group(1) ?: "", referenceTime)?.let { candidates.add(it) }
            }

            // Reckon "future"/"past" and the fallback relative to the message's own time so
            // imported/synced/re-classified messages aren't skewed by the current wall-clock.
            var parsedTime: Long? = candidates.filter { it >= referenceTime }.minOrNull()
                ?: candidates.maxOrNull()

            // Default tomorrow if not found
            if (parsedTime == null) {
                if (body.contains("tomorrow", true)) {
                    parsedTime = referenceTime + 24 * 3600 * 1000L
                } else if (body.contains("today", true)) {
                    parsedTime = referenceTime
                } else {
                    parsedTime = referenceTime + 3 * 24 * 3600 * 1000L // 3 days fallback
                }
            }
            dueDate = parsedTime
        }

        return ClassificationResult(
            category = category,
            isBlocked = false,
            isFinance = isFinance,
            isReminder = isReminder,
            bankName = bankName,
            amount = amount,
            isCredit = isCredit,
            balance = balance,
            dueDate = dueDate,
            reminderTitle = reminderTitle
        )
    }

    /** True when the body carries unambiguous advertising markers (coupon/voucher/offer code). */
    private fun isPromotionalOffer(normalizedBody: String): Boolean =
        COUPON_OFFER_MARKERS.any { normalizedBody.contains(it) }

    private data class FinanceFields(val amount: Double?, val isCredit: Boolean?, val balance: Double?)

    private data class CurrencyHit(val value: Double, val start: Int, val end: Int)

    /**
     * Pull the transaction amount, balance, and credit/debit direction out of a money SMS.
     *
     * The key correctness rule: the *balance* figure (e.g. "passbook balance … is Rs 13,97,889")
     * must never be mistaken for the transaction amount. We therefore locate the balance first —
     * the first currency number following a balance keyword — and exclude it from the amount
     * candidates. The amount is the remaining currency number nearest a money-movement verb; if the
     * only currency figure present is the balance (a pure "your balance is Rs X" info SMS), there
     * is no amount and the caller creates no ledger entry.
     */
    private fun extractFinanceFields(body: String): FinanceFields {
        val hits = mutableListOf<CurrencyHit>()
        val cm = currencyRegex.matcher(body)
        while (cm.find()) {
            val numStr = cm.group(1) ?: cm.group(2)
            val v = numStr?.replace(",", "")?.toDoubleOrNull()
            if (v != null) hits.add(CurrencyHit(v, cm.start(), cm.end()))
        }
        if (hits.isEmpty()) return FinanceFields(null, null, null)

        // Balance = first currency number that appears after a balance keyword.
        var balanceHit: CurrencyHit? = null
        val bm = balanceKeywordRegex.matcher(body)
        while (balanceHit == null && bm.find()) {
            balanceHit = hits.firstOrNull { it.start >= bm.end() }
        }

        // Movement verb positions (with the verb text) for direction + locating the amount.
        val movements = mutableListOf<Pair<Int, String>>()
        val mm = movementRegex.matcher(body)
        while (mm.find()) {
            movements.add(mm.start() to (mm.group()?.lowercase(Locale.ROOT) ?: ""))
        }

        // Amount candidates exclude the balance figure.
        val candidates = hits.filter { it !== balanceHit }
        val amountHit: CurrencyHit? = when {
            candidates.isEmpty() -> null
            movements.isEmpty() -> candidates.first()
            else -> candidates.minByOrNull { c ->
                movements.minOf { (pos, _) -> kotlin.math.abs(c.start - pos) }
            }
        }

        // Direction from the movement verb nearest the chosen amount; default to debit when none.
        val isCredit: Boolean? = if (movements.isEmpty()) {
            null
        } else {
            val ref = amountHit?.start ?: 0
            val nearestVerb = movements.minByOrNull { (pos, _) -> kotlin.math.abs(ref - pos) }?.second
            nearestVerb in creditWords
        }

        // "payment received for X" is a receipt of money the user *paid* — a debit, not a credit.
        // Only this receipt phrasing flips; "Rs X credited", "received from/in a/c" stay credits.
        val lower = body.lowercase(Locale.ROOT)
        val isPaymentReceipt = lower.contains("payment received") ||
                lower.contains("received your payment") || lower.contains("received towards")
        val finalCredit = if (isCredit == true && isPaymentReceipt) false else isCredit

        return FinanceFields(amount = amountHit?.value, isCredit = finalCredit, balance = balanceHit?.value)
    }

    private fun discoverBank(sender: String, body: String): String {
        val uppercaseSender = sender.uppercase(Locale.ROOT)
        // Common banking sender headers have names, e.g., "VM-HDFCBK"
        val cleanSender = uppercaseSender.replace(Regex("[^A-Z]"), "")
        val knownBanks = listOf(
            "HDFC", "SBIBNK", "SBI", "ICICI", "AXIS", "HSBC", "CITI", "CHASE", "BOFA", "KOTAK", "PNB", "BOI",
            "YESBK", "INDUS", "CANBNK", "CANARA", "UNIONB", "UNION", "IDFCFIRST", "IDFC",
            "RBLBNK", "RBL", "FEDBNK", "FEDERAL", "IOBBNK", "IOB", "CBI", "PAYTMBK", "PAYTM",
            "AIRPBNK", "AIRTEL", "IPPB", "BANDHAN", "BARODA", "BOB", "STANCHAR", "SCB"
        )
        for (bk in knownBanks) {
            if (cleanSender.contains(bk)) {
                return bk
            }
        }
        for (bk in knownBanks) {
            if (body.contains(bk, ignoreCase = true)) {
                return bk
            }
        }
        return if (cleanSender.length >= 4) {
            // Take the last 6 characters representing the provider ID (e.g., "HDFCBK" from "AD-HDFCBK")
            val bkName = cleanSender.takeLast(6)
            if (bkName.length >= 3) bkName else "PROV-$cleanSender"
        } else {
            "Unknown Bank"
        }
    }

    private fun parseDate(dateStr: String): Long? {
        val formats = listOf(
            "yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd",
            "dd-MM-yyyy", "dd/MM/yyyy", "dd.MM.yyyy", "dd-MM-yy", "dd/MM/yy", "dd.MM.yy"
        )
        for (f in formats) {
            try {
                val sdf = SimpleDateFormat(f, Locale.ROOT)
                sdf.isLenient = false
                return sdf.parse(dateStr)?.time
            } catch (e: Exception) {
                // ignore
            }
        }
        return null
    }

    private fun parseAlphaDate(dateStr: String, referenceTime: Long = System.currentTimeMillis()): Long? {
        // Strip ordinal suffixes (e.g., 25th -> 25, 1st -> 1, 2nd -> 2, 3rd -> 3)
        val cleaned = dateStr.replace(Regex("(?i)(\\d+)(?:st|nd|rd|th)"), "$1")
        // Normalize hyphen/dot/slash/comma/Unicode-dash separators to spaces so "16-Jun-2026" and
        // "16–Jun–2026" (en dash) parse with the space-delimited format list.
        val normalized = cleaned.replace(Regex("[$DATE_SEP]+"), " ").trim()
        val formats = listOf(
            "dd MMM yyyy", "dd MMMM yyyy", "d MMM yyyy", "d MMMM yyyy",
            "dd MMM", "dd MMMM", "d MMM", "d MMMM",
            "MMM dd yyyy", "MMMM dd yyyy", "MMM d yyyy", "MMMM d yyyy",
            "MMM dd", "MMMM dd", "MMM d", "MMMM d"
        )
        val referenceYear = Calendar.getInstance().apply { timeInMillis = referenceTime }.get(Calendar.YEAR)
        for (f in formats) {
            try {
                val sdf = SimpleDateFormat(f, Locale.ENGLISH)
                sdf.isLenient = false
                val date = sdf.parse(normalized)
                if (date != null) {
                    val cal = Calendar.getInstance()
                    cal.time = date
                    if (cal.get(Calendar.YEAR) == 1970 && !f.contains("yyyy")) {
                        cal.set(Calendar.YEAR, referenceYear)
                    }
                    return cal.timeInMillis
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        return null
    }
}
