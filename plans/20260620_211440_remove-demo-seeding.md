# Remove demo seeding entirely

## Issue

On first launch with an empty inbox and no `READ_SMS` permission, the app auto-seeds
fake demo messages and filter rules into its own Room database
(`SmsOrganizerViewModel.init` → `repository.seedDemoData()`). These fake messages
(e.g. the "HDFCBK ... debited Rs. 1,200" and "Credit Card bill ... Rs. 3,500" entries)
appear only inside SMS Sentry and confuse the user into thinking they are real SMS.

The user wants demo seeding removed completely.

## Files to change

1. `app/src/main/java/in/sreerajp/sms_sentry/ui/SmsOrganizerViewModel.kt`
   - In `init { ... }` (lines ~109-121), remove the `else` branch that seeds demo
     data when `READ_SMS` is not granted. Keep the `importSystemSms()` path for when
     permission is granted. Result: when there is no SMS permission, the inbox simply
     stays empty (no fake data).

2. `app/src/main/java/in/sreerajp/sms_sentry/data/SmsRepository.kt`
   - Delete the entire `seedDemoData()` function (lines ~443-477).

3. `docs/architecture.md`
   - Remove/adjust the bullet (lines ~93-94) that states: "On first launch with an
     empty inbox, the ViewModel auto-seeds demo data (`repository.seedDemoData()`)..."
     since that behavior no longer exists.

## Notes / verification

- No tests reference `seedDemoData()` (grep confirms only the ViewModel call site,
  the function definition, docs, and old plan files reference it). Old plan files under
  `plans/` are historical and will be left untouched.
- After the change, a fresh install with no SMS permission shows an empty dashboard;
  granting `READ_SMS` and importing still works unchanged.

## The 12 demo messages to delete from the phone/app

These are the hardcoded seed messages (sender — text). All can be deleted from within
SMS Sentry's Inbox. They never touched the system SMS provider, so they are not on the
real phone SMS app.

1. HDFCBK — "Your A/c x4492 has been debited for Rs. 1,200.00 via GPay. Avail.Bal: Rs. 43,450.00"
2. ICICIB — "Salary of Rs. 45,000.00 credited to Account x9284 on 22nd May. Bal: Rs. 88,450.00"
3. SBIPAY — "Txn of USD 12.50 processed at Amazon. Avail Balance Rs. 87,625.00"
4. AXISBK — "Dear customer, payment of Rs. 2,450.00 made to Netflix. Total Balance: Rs. 85,175.00"
5. AIRTEL — "Your postpaid bill due on 28-05-2026 is Rs. 599.00. Please recharge to avoid disconnection."
6. HDFCBK — "Warning: Credit Card bill payment is scheduled for 30-05-2026. Minimum due Rs. 3,500.00."
7. AMZNIN — "Your OTP for login is 884213. Do not share it with anyone."
8. BLUDRT — "Your order #IN8842 has been shipped and is out for delivery today."
9. MYNTRA — "Flat 50% OFF + extra 10% cashback on the End of Season Sale. Shop now before it ends!"
10. ZOMATO — "Your exclusive coupon: use code EAT60 for 60% discount on your next order. Limited time offer."
11. +180055501 — "CONGRATULATIONS! You won a mystery jackpot of $1000 GIFT. Claim now at freejackpot.com"
12. +9198765432 — "Hey! Are we meeting for coffee today at 5 PM?"
13. +165032890 — "Okay, I have received the documents. See you tomorrow."

Also seeded (not messages, but demo filter rules added to the DB):
- KEYWORD "WINNER" → Spam
- KEYWORD "GIFT" → Spam
- KEYWORD "OTP" → Others
- KEYWORD "RECHARGE" → Others
- KEYWORD "PAYMENT" → Others
