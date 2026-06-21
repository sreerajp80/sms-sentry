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
    private val REMINDER_KEYWORDS = listOf(
        "due on", "due date", "pay by", "bill payment", "scheduled", "reminder", "appointment",
        "expires", "outstanding", "recharge before", "renew"
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

    // Simple date extractors
    private val dateRegex1 = Pattern.compile(
        "(\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4})" // DD-MM-YYYY or DD/MM/YY
    )
    private val dateRegex2 = Pattern.compile(
        "(\\d{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s*\\d{0,4})",
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
        customContacts: Map<String, String> // Map of Phone -> targetCategory (or "Blocked")
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
                    else -> return runExtractions(normalizeCategory(category), body, sender)
                }
            }
        }

        // 2. Check custom keyword rules
        for ((kw, cat) in customKeywords) {
            if (normalizedBody.contains(kw.lowercase(Locale.ROOT))) {
                val isBlocked = (cat == "Spam" || cat == "Blocked")
                val finalCat = if (isBlocked) "Spam" else normalizeCategory(cat)
                return runExtractions(finalCat, body, sender, isBlocked)
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
            isPromoOffer -> runExtractions("Promotions", body, sender)
            containsMoneyKeyword -> runExtractions("Others", body, sender)
            containsReminderKeyword -> runExtractions("Others", body, sender)
            containsPromoKeyword -> runExtractions("Promotions", body, sender)
            containsServicesKeyword -> runExtractions("Others", body, sender)
            else -> runExtractions("Personal", body, sender)
        }
    }

    private fun runExtractions(
        category: String,
        body: String,
        sender: String,
        isBlocked: Boolean = false
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
        val isReminder = REMINDER_KEYWORDS.any { normalizedBody.contains(it) } || body.contains("due", true)

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
            // Search for date:
            var parsedTime: Long? = null

            // Try DD-MM-YYYY format
            val matcher1 = dateRegex1.matcher(body)
            if (matcher1.find()) {
                val dateStr = matcher1.group(1) ?: ""
                parsedTime = parseDate(dateStr)
            }

            // Try Alpha Month format (e.g., 25 May)
            if (parsedTime == null) {
                val matcher2 = dateRegex2.matcher(body)
                if (matcher2.find()) {
                    val dateStr = matcher2.group(1) ?: ""
                    parsedTime = parseAlphaDate(dateStr)
                }
            }

            // Default tomorrow if not found
            if (parsedTime == null) {
                if (body.contains("tomorrow", true)) {
                    parsedTime = System.currentTimeMillis() + 24 * 3600 * 1000L
                } else if (body.contains("today", true)) {
                    parsedTime = System.currentTimeMillis()
                } else {
                    parsedTime = System.currentTimeMillis() + 3 * 24 * 3600 * 1000L // 3 days fallback
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
        val formats = listOf("dd-MM-yyyy", "dd/MM/yyyy", "dd.MM.yyyy", "dd-MM-yy", "dd/MM/yy", "dd.MM.yy")
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

    private fun parseAlphaDate(dateStr: String): Long? {
        val formats = listOf("dd MMM yyyy", "dd MMM", "d MMM yyyy", "d MMM")
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        for (f in formats) {
            try {
                val sdf = SimpleDateFormat(f, Locale.ROOT)
                sdf.isLenient = false
                val date = sdf.parse(dateStr)
                if (date != null) {
                    val cal = Calendar.getInstance()
                    cal.time = date
                    if (cal.get(Calendar.YEAR) == 1970 && !f.contains("yyyy")) {
                        cal.set(Calendar.YEAR, currentYear)
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
