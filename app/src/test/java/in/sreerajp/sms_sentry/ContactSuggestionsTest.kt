package `in`.sreerajp.sms_sentry

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import `in`.sreerajp.sms_sentry.util.ContactNameResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContactSuggestionsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        
        // Setup FakeContactsProvider for the contacts authority
        Robolectric.setupContentProvider(FakeContactsProvider::class.java, ContactsContract.AUTHORITY)
        
        // Grant READ_CONTACTS permission
        val shadowApp = org.robolectric.Shadows.shadowOf(context.applicationContext as android.app.Application)
        shadowApp.grantPermissions(android.Manifest.permission.READ_CONTACTS)
    }

    @Test
    fun `test contact query matching name and number`() {
        // Query suggestions with empty query - should return all contacts
        val suggestionsEmpty = ContactNameResolver.queryContacts(context, "")
        assertEquals(1, suggestionsEmpty.size)
        assertEquals("Alice Smith", suggestionsEmpty[0].name)
        assertEquals("+1 555-010-1234", suggestionsEmpty[0].number)

        // Query suggestions matching name
        val suggestionsMatchName = ContactNameResolver.queryContacts(context, "Alice")
        assertEquals(1, suggestionsMatchName.size)
        assertEquals("Alice Smith", suggestionsMatchName[0].name)

        // Query suggestions matching number
        val suggestionsMatchNumber = ContactNameResolver.queryContacts(context, "1234")
        assertEquals(1, suggestionsMatchNumber.size)
        assertEquals("Alice Smith", suggestionsMatchNumber[0].name)

        // Query suggestions with no match
        val suggestionsNoMatch = ContactNameResolver.queryContacts(context, "Bob")
        assertTrue(suggestionsNoMatch.isEmpty())
    }

    class FakeContactsProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? {
            val cursor = MatrixCursor(projection ?: arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            ))

            // The query string is appended to the URI when using CONTENT_FILTER_URI
            val query = if (uri.pathSegments.size > 2) Uri.decode(uri.lastPathSegment) else ""

            if (query.isEmpty() || "Alice Smith".contains(query, ignoreCase = true) || "+1 555-010-1234".contains(query)) {
                cursor.addRow(arrayOf("Alice Smith", "+1 555-010-1234", null))
            }
            return cursor
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun getType(uri: Uri): String? = null
    }
}

