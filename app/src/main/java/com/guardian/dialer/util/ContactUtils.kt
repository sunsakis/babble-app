package com.guardian.dialer.util

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object ContactUtils {

    /** Returns the contact display name if the number exists in contacts, null otherwise. */
    fun lookupContact(context: Context, phoneNumber: String): String? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }

    /** Returns true if the phone number is NOT in the user's contacts. */
    fun isUnknownNumber(context: Context, phoneNumber: String): Boolean {
        return lookupContact(context, phoneNumber) == null
    }
}
