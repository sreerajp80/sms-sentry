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

    // Genuine spam / scam patterns (lottery, prize bait, crypto pump, etc.).
    private val SPAM_KEYWORDS = listOf(
        "win", "winner", "won", "prize", "lottery", "jackpot", "casino", "free bonus",
        "claim", "unlocked", "congrats", "congratulations", "invest", "crypto", "bitcoin"
    )

    // Marketing / advertising patterns → Promotions (legitimate offers, not scams).
    private val PROMO_KEYWORDS = listOf(
        "offer", "discount", "promo", "cashback", "sale", "deal", "% off", "percent off",
        "coupon", "voucher", "reward", "gift card", "limited time", "buy now", "shop now",
        "lowest price", "flat ", "sign up now", "subscribe"
    )

    // Money-movement tags → flags the message as finance (ledger), category becomes "Others".
    private val MONEY_KEYWORDS = listOf(
        "debited", "credited", "spent", "withdrawn", "withdrew", "paid", "received", "transfer",
        "txn", "transaction", "a/c balance", "available bal", "bank", "payment of"
    )

    // Reminder tags → flags the message as a reminder (due dates), category becomes "Others".
    // Forward-looking obligations only. Deliberately excludes "scheduled" (appears in *past*
    // transaction confirmations like "Txn … is Scheduled for <past date>") and bare "due".
    private val REMINDER_KEYWORDS = listOf(
        "due on", "due date", "due by", "payment due", "pay by", "last date", "bill due",
        "outstanding", "overdue", "expires on", "expiring", "expiry", "valid till",
        "valid until", "validity", "renew", "renewal", "recharge before", "appointment",
        "reminder", "kindly pay", "please pay"
    )

    // Strong, unambiguous "you must do something by a date" phrases. Used to override the
    // receipt exclusion below: a completed-transaction SMS that *also* carries one of these is
    // still a genuine reminder. Excludes the soft "reminder"/"appointment" cues on purpose.
    private val STRONG_DUE_KEYWORDS = listOf(
        "due on", "due date", "due by", "payment due", "pay by", "last date", "bill due",
        "outstanding", "overdue", "expires on", "expiring", "expiry", "valid till",
        "valid until", "validity", "renew", "renewal", "recharge before", "kindly pay",
        "please pay"
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
        "ticket", "confirmed", "recharge"
    )

    // Regex compiled for speed.
    // A currency-tagged number anywhere in the body. The Indian `/-` suffix and `13,97,889`
    // grouping are tolerated: the trailing `/-` is outside the captured group and commas are
    // stripped before parsing.
    private val currencyRegex = Pattern.compile(
        "(?:rs\\.?|inr|₹|\\$|usd)\\s*([\\d,]+(?:\\.\\d{1,2})?)",
        Pattern.CASE_INSENSITIVE
    )
    // Balance context keyword; the balance figure is the first currency number that follows it.
    private val balanceKeywordRegex = Pattern.compile(
        "avail(?:able)?\\.?\\s*bal(?:ance)?|a/c\\s*bal(?:ance)?|closing\\s*bal(?:ance)?|bal(?:ance)?",
        Pattern.CASE_INSENSITIVE
    )
    // Money-movement verbs; used both to detect direction and to locate the transaction amount.
    private val movementRegex = Pattern.compile(
        "credited|debited|spent|withdrawn|withdrew|paid|sent|received|contribution|transferred|purchase|refunded|refund|deposited|deposit",
        Pattern.CASE_INSENSITIVE
    )
    // Movement verbs that mean money came IN. Anything else (debited/spent/paid/…) is a debit.
    private val creditWords = setOf("credited", "received", "contribution", "refunded", "refund", "deposited", "deposit")

    // Simple date extractors.
    // Date separator characters: ASCII space/dot/slash/hyphen PLUS the Unicode dashes that
    // bulk SMS (e.g. MoRTH/government senders) often use instead of an ASCII hyphen —
    // non-breaking hyphen (U+2011), figure dash (U+2012), en dash (U+2013), em dash (U+2014),
    // and minus sign (U+2212). Without these, "16–Jun–2026" (en dash) fails to match.
    private const val DATE_SEP = "\\s./\\-\\u2011\\u2012\\u2013\\u2014\\u2212"
    // ISO yyyy-MM-dd (e.g. "validity is 2027-06-12"). Matched first so the 4-digit year is not
    // mistaken for a DD-MM-YY date by dateRegex1 (which would turn 2027-06-12 into 27-06-12).
    private val isoDateRegex = Pattern.compile(
        "(\\d{4}[$DATE_SEP]\\d{1,2}[$DATE_SEP]\\d{1,2})"
    )
    private val dateRegex1 = Pattern.compile(
        "(\\d{1,2}[$DATE_SEP]\\d{1,2}[$DATE_SEP]\\d{2,4})" // DD-MM-YYYY or DD/MM/YY
    )
    // Alpha month with flexible separators: "16 Jun 2026", "16-Jun-2026", "16–Jun–2026" (en dash),
    // "16.Jun.2026", "16/Jun/2026" all match. Separators are normalized to spaces before parsing.
    private val dateRegex2 = Pattern.compile(
        "(\\d{1,2}[$DATE_SEP]+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[$DATE_SEP]*\\d{0,4})",
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

        // Is it SPAM?
        val containsSpamKeyword = SPAM_KEYWORDS.any { normalizedBody.contains(it) }
        // Spam messages usually come from alphabetic headers without typical transactional tags
        val isProbableSpamSender = sender.length > 5 && !sender.any { it.isDigit() } &&
                !(sender.contains("bank", true) || sender.contains("pay", true) || sender.contains("remi", true))

        if (!allowlisted && (containsSpamKeyword || (isProbableSpamSender && normalizedBody.contains("promo")))) {
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

            // Alpha month format (e.g., 25 May, 16-Jun-2026).
            val matcher2 = dateRegex2.matcher(body)
            while (matcher2.find()) {
                parseAlphaDate(matcher2.group(1) ?: "", referenceTime)?.let { candidates.add(it) }
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
            val v = cm.group(1)?.replace(",", "")?.toDoubleOrNull()
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
        val knownBanks = listOf("HDFC", "SBI", "ICICI", "AXIS", "HSBC", "CITI", "CHASE", "BOFA", "KOTAK", "PNB", "BOI", "YESBK", "INDUS")
        for (bk in knownBanks) {
            if (cleanSender.contains(bk) || body.contains(bk, ignoreCase = true)) {
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
        // Normalize hyphen/dot/slash/Unicode-dash separators to spaces so "16-Jun-2026" and
        // "16–Jun–2026" (en dash) parse with the space-delimited format list (covers
        // "16 Jun 2026", "16.Jun.2026", "16/Jun/2026" too).
        val normalized = dateStr.replace(Regex("[$DATE_SEP]+"), " ").trim()
        val formats = listOf("dd MMM yyyy", "dd MMM", "d MMM yyyy", "d MMM")
        val referenceYear = Calendar.getInstance().apply { timeInMillis = referenceTime }.get(Calendar.YEAR)
        for (f in formats) {
            try {
                val sdf = SimpleDateFormat(f, Locale.ROOT)
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
