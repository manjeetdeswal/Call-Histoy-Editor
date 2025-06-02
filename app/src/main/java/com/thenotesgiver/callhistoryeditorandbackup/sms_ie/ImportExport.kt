

package com.thenotesgiver.callhistoryeditorandbackup.sms_ie

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.thenotesgiver.callhistoryeditorandbackup.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun checkReadSMSContactsPermissions(appContext: Context): Boolean {
    if (ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    ) return true
    /*else {
        Toast.makeText(
            appContext,
            appContext.getString(R.string.sms_permissions_required),
            Toast.LENGTH_LONG
        ).show()
    }*/
    return false
}

fun checkReadCallLogsContactsPermissions(appContext: Context): Boolean {
    if (ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    ) return true
    /*else {
        Toast.makeText(
            appContext,
            appContext.getString(R.string.call_logs_permissions_required),
            Toast.LENGTH_LONG
        ).show()
    }*/
    return false
}

fun checkReadWriteCallLogPermissions(appContext: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        appContext, Manifest.permission.WRITE_CALL_LOG
    ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
        appContext, Manifest.permission.READ_CALL_LOG
    ) == PackageManager.PERMISSION_GRANTED
}

fun checkReadContactsPermission(appContext: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        appContext, Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED
}

fun checkWriteContactsPermission(appContext: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        appContext, Manifest.permission.WRITE_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED
}

fun lookupDisplayName(
    appContext: Context, displayNames: MutableMap<String, String?>, address: String
): String? {
//        look up display name by phone number
    if (address == "") return null
    if (displayNames[address] != null) return displayNames[address]
    val displayName: String?
    val uri = Uri.withAppendedPath(
        ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)
    )
    val nameCursor = appContext.contentResolver.query(
        uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
    )
    nameCursor.use {
        displayName = if (it != null && it.moveToFirst()) it.getString(
            it.getColumnIndexOrThrow(
                ContactsContract.PhoneLookup.DISPLAY_NAME
            )
        )
        else null
    }
    displayNames[address] = displayName
    return displayName
}

suspend fun wipeSmsAndMmsMessages(
    appContext: Context, statusReportText: TextView, progressBar: ProgressBar
) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
    withContext(Dispatchers.IO) {
        if (prefs.getBoolean("sms", true)) {
            setStatusText(statusReportText, appContext.getString(R.string.wiping_sms_messages))
            initIndeterminateProgressBar(progressBar)
            appContext.contentResolver.delete(Telephony.Sms.CONTENT_URI, null, null)
            hideProgressBar(progressBar)
        }
        if (prefs.getBoolean("mms", true)) {
            setStatusText(statusReportText, appContext.getString(R.string.wiping_mms_messages))
            initIndeterminateProgressBar(progressBar)
            appContext.contentResolver.delete(Telephony.Mms.CONTENT_URI, null, null)
            hideProgressBar(progressBar)
        }
    }
}

suspend fun initProgressBar(progressBar: ProgressBar?, cursor: Cursor) {
    withContext(Dispatchers.Main) {
        progressBar?.isIndeterminate = false
        progressBar?.progress = 0
        progressBar?.visibility = View.VISIBLE
        progressBar?.setMax(cursor.count)
    }
}

suspend fun initIndeterminateProgressBar(progressBar: ProgressBar?) {
    withContext(Dispatchers.Main) {
        progressBar?.isIndeterminate = true
        progressBar?.visibility = View.VISIBLE
    }
}

suspend fun hideProgressBar(progressBar: ProgressBar?) {
    withContext(Dispatchers.Main) {
        progressBar?.visibility = View.INVISIBLE
    }
}

suspend fun setStatusText(statusReportText: TextView?, message: String) {
    withContext(Dispatchers.Main) { statusReportText?.text = message }
}

suspend fun incrementProgress(progressBar: ProgressBar?) {
    withContext(Dispatchers.Main) {
        progressBar?.incrementProgressBy(1)
    }
}

// From: https://stackoverflow.com/a/18143773
suspend fun displayError(appContext: Context, e: Exception?, title: String, message: String) {
    val messageExpanded = if (e != null) {
        e.printStackTrace()
        "$message:\n\n\"$e\"\n\nSee logcat for more information."
    } else {
        message
    }
    val errorBox = AlertDialog.Builder(appContext)
    errorBox.setTitle(title).setMessage(messageExpanded)
    //errorBox.setTitle(title).setMessage("$message:\n\n\"$e\"\n\nSee logcat for more information.")
        .setCancelable(false).setNeutralButton("Okay", null)
    withContext(Dispatchers.Main) {
        errorBox.show()
    }
}
