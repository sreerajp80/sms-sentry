package `in`.sreerajp.sms_sentry

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import `in`.sreerajp.sms_sentry.data.SMSMessage
import `in`.sreerajp.sms_sentry.ui.Conversation
import `in`.sreerajp.sms_sentry.util.ContactNameResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ContactGroupingTest {

    private lateinit var context: Context
    private lateinit var app: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        app = ApplicationProvider.getApplicationContext<Application>()
        ContactNameResolver.clearCache()

        Robolectric.setupContentProvider(MultiNumberContactsProvider::class.java, ContactsContract.AUTHORITY)

        val shadowApp = org.robolectric.Shadows.shadowOf(app)
        shadowApp.grantPermissions(android.Manifest.permission.READ_CONTACTS)
    }

    @Test
    fun `test contact resolver gives same conversation key for multiple numbers of same contact`() {
        val num1 = "+1 555-000-1111"
        val num2 = "+1 555-000-2222"

        val info1 = ContactNameResolver.resolve(context, num1)
        val info2 = ContactNameResolver.resolve(context, num2)

        assertEquals("John Doe", info1.name)
        assertEquals("John Doe", info2.name)
        assertEquals("0r1-john-doe", info1.lookupKey)
        assertEquals("0r1-john-doe", info2.lookupKey)
        assertEquals("contact:0r1-john-doe", info1.conversationKey(num1))
        assertEquals("contact:0r1-john-doe", info2.conversationKey(num2))
        assertEquals(info1.conversationKey(num1), info2.conversationKey(num2))
    }

    @Test
    fun `test unknown numbers and sender ids have distinct conversation keys`() {
        val unknownNum = "+1 555-999-8888"
        val senderId = "HDFCBK"

        val unknownInfo = ContactNameResolver.resolve(context, unknownNum)
        val senderIdInfo = ContactNameResolver.resolve(context, senderId)

        assertEquals(unknownNum, unknownInfo.conversationKey(unknownNum))
        assertEquals(senderId, senderIdInfo.conversationKey(senderId))
        assertNotEquals(unknownInfo.conversationKey(unknownNum), senderIdInfo.conversationKey(senderId))
    }

    @Test
    fun `test messages from multiple numbers of same contact group into single conversation`() {
        val num1 = "+1 555-000-1111"
        val num2 = "+1 555-000-2222"
        val otherNum = "+1 555-999-8888"

        val msg1 = SMSMessage(
            id = 1L,
            sender = num1,
            body = "Hello from phone 1",
            timestamp = 1000L,
            category = "Personal",
            simId = 1,
            type = SMSMessage.TYPE_INBOX,
            isRead = true
        )
        val msg2 = SMSMessage(
            id = 2L,
            sender = num2,
            body = "Hello from phone 2",
            timestamp = 2000L,
            category = "Personal",
            simId = 1,
            type = SMSMessage.TYPE_INBOX,
            isRead = false
        )
        val msg3 = SMSMessage(
            id = 3L,
            sender = otherNum,
            body = "Hello from unknown",
            timestamp = 1500L,
            category = "Personal",
            simId = 1,
            type = SMSMessage.TYPE_INBOX,
            isRead = true
        )

        val messages = listOf(msg1, msg2, msg3)

        // Group by conversation key resolved from ContactNameResolver
        val conversations = messages
            .groupBy { ContactNameResolver.conversationKey(context, it.sender) }
            .map { (key, msgs) ->
                val latest = msgs.maxByOrNull { it.timestamp }!!
                val senders = msgs.mapTo(HashSet()) { it.sender }
                Conversation(
                    threadKey = key,
                    sender = latest.sender,
                    senders = senders,
                    latest = latest,
                    unreadCount = msgs.count { !it.isRead && it.type == SMSMessage.TYPE_INBOX },
                    total = msgs.size
                )
            }
            .sortedByDescending { it.latest.timestamp }

        // There should be 2 conversations: John Doe (combining num1 and num2) and the otherNum
        assertEquals(2, conversations.size)

        val johnConv = conversations.first { it.threadKey == "contact:0r1-john-doe" }
        assertEquals("Hello from phone 2", johnConv.latest.body)
        assertEquals(2, johnConv.total)
        assertEquals(1, johnConv.unreadCount)
        assertTrue(johnConv.senders.contains(num1))
        assertTrue(johnConv.senders.contains(num2))

        val otherConv = conversations.first { it.threadKey == otherNum }
        assertEquals("Hello from unknown", otherConv.latest.body)
        assertEquals(1, otherConv.total)
    }

    class MultiNumberContactsProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? {
            val cursor = MatrixCursor(projection ?: arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_URI,
                ContactsContract.PhoneLookup.LOOKUP_KEY,
                ContactsContract.PhoneLookup._ID
            ))

            val number = if (uri.pathSegments.isNotEmpty()) Uri.decode(uri.lastPathSegment) else ""

            if (number.contains("1111") || number.contains("2222")) {
                cursor.addRow(arrayOf<Any?>(
                    "John Doe",
                    "content://com.android.contacts/photos/1",
                    "0r1-john-doe",
                    1L
                ))
            }
            return cursor
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun getType(uri: Uri): String? = null
    }
}
