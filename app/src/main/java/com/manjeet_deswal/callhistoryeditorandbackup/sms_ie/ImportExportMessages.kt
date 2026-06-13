package com.manjeet_deswal.callhistoryeditorandbackup.sms_ie

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.provider.Telephony
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import com.manjeet_deswal.callhistoryeditorandbackup.LOG_TAG
import com.manjeet_deswal.callhistoryeditorandbackup.MessageTotal
import com.manjeet_deswal.callhistoryeditorandbackup.PDU_HEADERS_FROM
import com.manjeet_deswal.callhistoryeditorandbackup.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class MmsBinaryPart(val uri: Uri, val filename: String)

suspend fun exportMessages(
    appContext: Context, file: Uri, progressBar: ProgressBar?, statusReportText: TextView?
): MessageTotal {
    val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
    return withContext(Dispatchers.IO) {
        val totals = MessageTotal()
        val displayNames = mutableMapOf<String, String?>()
        appContext.contentResolver.openOutputStream(file).use { outputStream ->
            ZipOutputStream(outputStream).use { zipOutputStream ->
                val jsonZipEntry = ZipEntry("messages.ndjson")
                zipOutputStream.putNextEntry(jsonZipEntry)
                if (prefs.getBoolean("sms", true)) {
                    totals.sms = smsToJSON(
                        appContext, zipOutputStream, displayNames, progressBar, statusReportText
                    )
                }
                val mmsPartList = mutableListOf<MmsBinaryPart>()
                if (prefs.getBoolean("mms", true)) {
                    totals.mms = mmsToJSON(
                        appContext,
                        zipOutputStream,
                        displayNames,
                        mmsPartList,
                        progressBar,
                        statusReportText
                    )
                }
                zipOutputStream.closeEntry()
                if (prefs.getBoolean("mms", true)) {
                    setStatusText(
                        statusReportText, appContext.getString(R.string.copying_mms_binary_data)
                    )
                    val buffer = ByteArray(1048576)
                    mmsPartList.forEach {
                        val partZipEntry = ZipEntry(it.filename)
                        zipOutputStream.putNextEntry(partZipEntry)
                        try {
                            appContext.contentResolver.openInputStream(it.uri)?.use { inputStream ->
                                var n = inputStream.read(buffer)
                                while (n > -1) {
                                    zipOutputStream.write(buffer, 0, n)
                                    n = inputStream.read(buffer)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(
                                LOG_TAG,
                                "Error accessing binary data for MMS message part " + it.filename,
                                e
                            )
                        }
                        zipOutputStream.closeEntry()
                    }
                }
            }
        }
        totals
    }
}

private suspend fun smsToJSON(
    appContext: Context,
    zipOutputStream: ZipOutputStream,
    displayNames: MutableMap<String, String?>,
    progressBar: ProgressBar?,
    statusReportText: TextView?
): Int {
    val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
    var total = 0

    // Fixed: Explicitly handled SecurityException for permissions
    val smsCursor = try {
        appContext.contentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null, null)
    } catch (e: SecurityException) {
        Log.e(LOG_TAG, "Permission denied while querying SMS.", e)
        return 0
    }

    smsCursor?.use {
        if (it.moveToFirst()) {
            initProgressBar(progressBar, it)
            val totalSms = it.count
            val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            do {
                val smsMessage = JSONObject()
                it.columnNames.forEachIndexed { i, columnName ->
                    val value = it.getString(i)
                    if (value != null) smsMessage.put(columnName, value)
                }
                val address = it.getString(addressIndex)
                if (address != null) {
                    val displayName = lookupDisplayName(appContext, displayNames, address)
                    if (displayName != null) smsMessage.put("__display_name", displayName)
                }
                zipOutputStream.write((smsMessage.toString() + "\n").toByteArray())
                total++
                incrementProgress(progressBar)
                setStatusText(
                    statusReportText,
                    appContext.getString(R.string.sms_export_progress, total, totalSms)
                )
                if (total == (prefs.getString("max_records", "")?.toIntOrNull() ?: -1)) break
            } while (it.moveToNext())
            hideProgressBar(progressBar)
        }
    }
    return total
}

private suspend fun mmsToJSON(
    appContext: Context,
    zipOutputStream: ZipOutputStream,
    displayNames: MutableMap<String, String?>,
    mmsPartList: MutableList<MmsBinaryPart>,
    progressBar: ProgressBar?,
    statusReportText: TextView?
): Int {
    val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
    var total = 0

    // Fixed: Explicitly handled SecurityException for permissions
    val mmsCursor = try {
        appContext.contentResolver.query(Telephony.Mms.CONTENT_URI, null, null, null, null)
    } catch (e: SecurityException) {
        Log.e(LOG_TAG, "Permission denied while querying MMS.", e)
        return 0
    }

    mmsCursor?.use {
        if (it.moveToFirst()) {
            val totalMms = it.count
            initProgressBar(progressBar, it)
            val msgIdIndex = it.getColumnIndexOrThrow("_id")
            do {
                val mmsMessage = JSONObject()
                it.columnNames.forEachIndexed { i, columnName ->
                    val value = it.getString(i)
                    if (value != null) mmsMessage.put(columnName, value)
                }
                val msgId = it.getString(msgIdIndex)
                val addressCursor = appContext.contentResolver.query(
                    Uri.parse("content://mms/$msgId/addr"), null, null, null, null
                )
                addressCursor?.use { address ->
                    val addressTypeIndex =
                        addressCursor.getColumnIndexOrThrow(Telephony.Mms.Addr.TYPE)
                    val addressIndex =
                        addressCursor.getColumnIndexOrThrow(Telephony.Mms.Addr.ADDRESS)
                    if (address.moveToFirst()) {
                        do {
                            if (addressTypeIndex.let { x -> address.getString(x) } == PDU_HEADERS_FROM) {
                                val mmsSenderAddress = JSONObject()
                                address.columnNames.forEachIndexed { i, columnName ->
                                    val value = address.getString(i)
                                    if (value != null) mmsSenderAddress.put(columnName, value)
                                }
                                val displayName = lookupDisplayName(
                                    appContext, displayNames, address.getString(addressIndex)
                                )
                                if (displayName != null) mmsSenderAddress.put(
                                    "__display_name", displayName
                                )
                                mmsMessage.put("__sender_address", mmsSenderAddress)
                                break
                            }
                        } while (address.moveToNext())
                    }
                    if (address.moveToFirst()) {
                        val mmsRecipientAddresses = JSONArray()
                        do {
                            if (addressTypeIndex.let { x -> address.getString(x) } != PDU_HEADERS_FROM) {
                                val mmsRecipientAddress = JSONObject()
                                address.columnNames.forEachIndexed { i, columnName ->
                                    val value = address.getString(i)
                                    if (value != null) mmsRecipientAddress.put(columnName, value)
                                }
                                val displayName = lookupDisplayName(
                                    appContext, displayNames, address.getString(addressIndex)
                                )
                                if (displayName != null) mmsRecipientAddress.put(
                                    "__display_name", displayName
                                )
                                mmsRecipientAddresses.put(mmsRecipientAddress)
                            }
                        } while (address.moveToNext())
                        mmsMessage.put("__recipient_addresses", mmsRecipientAddresses)
                    }
                }
                val partCursor = appContext.contentResolver.query(
                    Uri.parse("content://mms/part"),
                    null, "mid=?", arrayOf(msgId), "seq ASC"
                )
                partCursor?.use { part ->
                    if (part.moveToFirst()) {
                        val mmsParts = JSONArray()
                        val partIdIndex = part.getColumnIndexOrThrow("_id")
                        val dataIndex = part.getColumnIndexOrThrow("_data")
                        do {
                            val mmsPart = JSONObject()
                            part.columnNames.forEachIndexed { i, columnName ->
                                val value = part.getString(i)
                                if (value != null) mmsPart.put(columnName, value)
                            }
                            if (prefs.getBoolean("include_binary_data", true) && part.getString(
                                    dataIndex
                                ) != null
                            ) {
                                var filename =
                                    Uri.parse(mmsPart.getString(Telephony.Mms.Part._DATA)).lastPathSegment
                                if (filename == null) {
                                    filename =
                                        "MISSING_FILENAME" + System.currentTimeMillis() + mmsPart.getString(
                                            Telephony.Mms.Part.CONTENT_LOCATION
                                        )
                                    mmsPart.put(Telephony.Mms.Part._DATA, filename)
                                }
                                filename = "data/$filename"
                                mmsPartList.add(
                                    MmsBinaryPart(
                                        Uri.parse(
                                            "content://mms/part/" + part.getString(partIdIndex)
                                        ), filename
                                    )
                                )
                            }
                            mmsParts.put(mmsPart)
                        } while (part.moveToNext())
                        mmsMessage.put("__parts", mmsParts)
                    }
                }
                zipOutputStream.write((mmsMessage.toString() + "\n").toByteArray())
                total++
                incrementProgress(progressBar)
                setStatusText(
                    statusReportText,
                    appContext.getString(R.string.mms_export_progress, total, totalMms)
                )
                if (total == (prefs.getString("max_records", "")?.toIntOrNull() ?: -1)) break
            } while (it.moveToNext())
            hideProgressBar(progressBar)
        }
    }
    return total
}

@RequiresApi(Build.VERSION_CODES.M)
suspend fun importMessages(
    appContext: Context, uri: Uri, progressBar: ProgressBar?, statusReportText: TextView?
): MessageTotal {
    val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
    val deduplication = prefs.getBoolean("message_deduplication", false)
    return withContext(Dispatchers.IO) {
        val totals = MessageTotal()

        // Fixed: Explicitly handled SecurityException for permissions
        val smsColumns = mutableSetOf<String>()
        try {
            appContext.contentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null, null)?.use {
                smsColumns.addAll(it.columnNames)
                smsColumns.removeAll(setOf("_id", "thread_id"))
            }
        } catch (e: SecurityException) {
            Log.e(LOG_TAG, "Permission denied querying SMS columns.", e)
        }

        val mmsColumns = mutableSetOf<String>()
        try {
            appContext.contentResolver.query(Telephony.Mms.CONTENT_URI, null, null, null, null)?.use {
                mmsColumns.addAll(it.columnNames)
                mmsColumns.removeAll(setOf("_id", "thread_id"))
            }
        } catch (e: SecurityException) {
            Log.e(LOG_TAG, "Permission denied querying MMS columns.", e)
        }

        val partColumns = mutableSetOf<String>()
        val partTableUri =
            if (SDK_INT >= 29) Telephony.Mms.Part.CONTENT_URI else Uri.parse("content://mms/part")
        try {
            appContext.contentResolver.query(partTableUri, null, null, null, null)?.use {
                partColumns.addAll(it.columnNames)
                partColumns.removeAll(
                    setOf(
                        Telephony.Mms.Part.MSG_ID,
                        Telephony.Mms.Part._ID,
                        Telephony.Mms.Part._DATA,
                        Telephony.Mms.Part._COUNT
                    )
                )
            }
        } catch (e: SecurityException) {
            Log.e(LOG_TAG, "Permission denied querying MMS parts.", e)
        }

        val addressExcludedKeys = setOf(
            Telephony.Mms.Addr._ID,
            Telephony.Mms.Addr._COUNT,
            Telephony.Mms.Addr.MSG_ID,
            "__display_name"
        )
        val threadIdMap = HashMap<String, String>()
        uri.let { zipUri ->
            initIndeterminateProgressBar(progressBar)
            val mmsPartMap = mutableMapOf<String, Uri>()
            appContext.contentResolver.openInputStream(zipUri).use { inputStream ->
                ZipInputStream(inputStream).use { zipInputStream ->
                    var zipEntry = zipInputStream.nextEntry
                    while (zipEntry != null) {
                        if (zipEntry.name == "messages.ndjson") {
                            break
                        }
                        zipEntry = zipInputStream.nextEntry
                    }
                    if (zipEntry == null) {
                        displayError(
                            appContext,
                            null,
                            "Can't find 'messages.ndjson'",
                            "Please make sure that the provided file is a ZIP file in the correct format"
                        )
                        return@let
                    }
                    setStatusText(
                        statusReportText, appContext.getString(R.string.importing_messages)
                    )
                    BufferedReader(InputStreamReader(zipInputStream)).useLines { lines ->
                        lines.forEachIndexed JSONLine@{ lineNumber, line ->
                            try {
                                val messageMetadata = ContentValues()
                                val messageJSON = JSONObject(line)
                                val oldThreadId = messageJSON.optString("thread_id")
                                if (oldThreadId in threadIdMap) {
                                    messageMetadata.put(
                                        "thread_id", threadIdMap[oldThreadId]
                                    )
                                }
                                if (!messageJSON.has("m_type")) {
                                    if (!prefs.getBoolean(
                                            "sms", true
                                        ) || totals.sms == (prefs.getString(
                                            "max_records", ""
                                        )?.toIntOrNull() ?: -1)
                                    ) {
                                        return@JSONLine
                                    }
                                    if (deduplication) {
                                        try {
                                            appContext.contentResolver.query(
                                                Telephony.Sms.CONTENT_URI,
                                                arrayOf(Telephony.Sms._ID),
                                                "${Telephony.Sms.ADDRESS}=? AND ${Telephony.Sms.TYPE}=? AND ${Telephony.Sms.DATE}=? AND ${Telephony.Sms.BODY}=?",
                                                arrayOf(
                                                    messageJSON.optString(Telephony.Sms.ADDRESS),
                                                    messageJSON.optString(Telephony.Sms.TYPE),
                                                    messageJSON.optString(Telephony.Sms.DATE),
                                                    messageJSON.optString(Telephony.Sms.BODY)
                                                ),
                                                null
                                            )?.use {
                                                if (it.moveToFirst()) return@JSONLine
                                            }
                                        } catch (e: SecurityException) {
                                            Log.e(LOG_TAG, "SecurityException during deduplication", e)
                                        }
                                    }
                                    messageJSON.keys().forEach { key ->
                                        if (key in smsColumns) messageMetadata.put(
                                            key, messageJSON.getString(key)
                                        )
                                    }
                                    if (!messageMetadata.containsKey("thread_id")) {
                                        val newThreadId = Telephony.Threads.getOrCreateThreadId(
                                            appContext,
                                            messageMetadata.getAsString(Telephony.TextBasedSmsColumns.ADDRESS)
                                        )
                                        messageMetadata.put("thread_id", newThreadId)
                                        if (oldThreadId != "") {
                                            threadIdMap[oldThreadId] = newThreadId.toString()
                                        }
                                    }

                                    // Fixed: Explicitly handled SecurityException for insert
                                    val insertUri = try {
                                        appContext.contentResolver.insert(
                                            Telephony.Sms.CONTENT_URI, messageMetadata
                                        )
                                    } catch (e: SecurityException) {
                                        Log.e(LOG_TAG, "Permission denied inserting SMS.", e)
                                        null
                                    }

                                    if (insertUri == null) {
                                        Log.e(LOG_TAG, "SMS insert failed!")
                                    } else {
                                        totals.sms++
                                        setStatusText(
                                            statusReportText, appContext.getString(
                                                R.string.message_import_progress,
                                                totals.sms,
                                                totals.mms
                                            )
                                        )
                                    }
                                } else {
                                    if (!prefs.getBoolean(
                                            "mms", true
                                        ) || totals.mms == (prefs.getString(
                                            "max_records", ""
                                        )?.toIntOrNull() ?: -1)
                                    ) {
                                        return@JSONLine
                                    }
                                    if (deduplication) {
                                        val messageID =
                                            messageJSON.optString(Telephony.Mms.MESSAGE_ID)
                                        val contentLocation =
                                            messageJSON.optString(Telephony.Mms.CONTENT_LOCATION)
                                        var selection =
                                            "${Telephony.Mms.DATE}=? AND ${Telephony.Mms.MESSAGE_BOX}=?"
                                        var selectionArgs = arrayOf(
                                            messageJSON.optString(Telephony.Mms.DATE),
                                            messageJSON.optString(Telephony.Mms.MESSAGE_BOX)
                                        )
                                        if (messageID != "") {
                                            selection =
                                                "$selection AND ${Telephony.Mms.MESSAGE_ID}=?"
                                            selectionArgs += messageJSON.optString(Telephony.Mms.MESSAGE_ID)
                                        } else if (contentLocation != "") {
                                            selection =
                                                "$selection AND ${Telephony.Mms.CONTENT_LOCATION}=?"
                                            selectionArgs += messageJSON.optString(Telephony.Mms.CONTENT_LOCATION)
                                        }
                                        try {
                                            appContext.contentResolver.query(
                                                Telephony.Mms.CONTENT_URI,
                                                arrayOf(Telephony.Mms._ID),
                                                selection,
                                                selectionArgs,
                                                null
                                            )?.use {
                                                if (it.moveToFirst()) return@JSONLine
                                            }
                                        } catch (e: SecurityException) {
                                            Log.e(LOG_TAG, "SecurityException during deduplication", e)
                                        }
                                    }
                                    messageJSON.keys().forEach { key ->
                                        if (key in mmsColumns) messageMetadata.put(
                                            key, messageJSON.getString(key)
                                        )
                                    }
                                    val addresses = mutableSetOf<ContentValues>()
                                    val senderAddress =
                                        messageJSON.optJSONObject("__sender_address")
                                    senderAddress?.let {
                                        val address = ContentValues()
                                        it.keys().forEach { addressKey ->
                                            if (addressKey !in addressExcludedKeys) address.put(
                                                addressKey, senderAddress.getString(addressKey)
                                            )
                                        }
                                        addresses.add(address)
                                    }
                                    val recipientAddresses =
                                        messageJSON.optJSONArray("__recipient_addresses")
                                    recipientAddresses?.let {
                                        for (i in 0 until recipientAddresses.length()) {
                                            val recipientAddress =
                                                recipientAddresses.getJSONObject(i)
                                            val address = ContentValues()
                                            for (recipientAddressKey in recipientAddress.keys()) {
                                                if (recipientAddressKey !in addressExcludedKeys) {
                                                    address.put(
                                                        recipientAddressKey,
                                                        recipientAddress.getString(
                                                            recipientAddressKey
                                                        )
                                                    )
                                                }
                                                addresses.add(address)
                                            }
                                        }
                                    }
                                    if (!messageMetadata.containsKey("thread_id")) {
                                        val newThreadId = Telephony.Threads.getOrCreateThreadId(
                                            appContext,
                                            addresses.map { x -> x.getAsString(Telephony.Mms.Addr.ADDRESS) }
                                                .toSet())
                                        messageMetadata.put("thread_id", newThreadId)
                                        if (oldThreadId != "") {
                                            threadIdMap[oldThreadId] = newThreadId.toString()
                                        }
                                    }

                                    // Fixed: Explicitly handled SecurityException for insert
                                    val insertUri = try {
                                        appContext.contentResolver.insert(
                                            Telephony.Mms.CONTENT_URI, messageMetadata
                                        )
                                    } catch (e: SecurityException) {
                                        Log.e(LOG_TAG, "Permission denied inserting MMS.", e)
                                        null
                                    }

                                    if (insertUri == null) {
                                        Log.e(LOG_TAG, "MMS insert failed!")
                                    } else {
                                        totals.mms++
                                        setStatusText(
                                            statusReportText, appContext.getString(
                                                R.string.message_import_progress,
                                                totals.sms,
                                                totals.mms
                                            )
                                        )
                                        val messageId = insertUri.lastPathSegment
                                        val addressUri = Uri.parse("content://mms/$messageId/addr")
                                        addresses.forEach { address ->
                                            address.put(
                                                Telephony.Mms.Addr.MSG_ID, messageId
                                            )
                                            try {
                                                val insertAddressUri =
                                                    appContext.contentResolver.insert(
                                                        addressUri, address
                                                    )
                                                if (insertAddressUri == null) Log.e(
                                                    LOG_TAG,
                                                    "MMS address insert failed!"
                                                )
                                            } catch (e: SecurityException) {
                                                Log.e(LOG_TAG, "SecurityException inserting address", e)
                                            }
                                        }
                                        val messageParts = messageJSON.optJSONArray("__parts")
                                        messageParts?.let {
                                            val partUri = Uri.parse("content://mms/$messageId/part")
                                            for (i in 0 until messageParts.length()) {
                                                val messagePart = messageParts.getJSONObject(i)
                                                val part = ContentValues()
                                                part.put(Telephony.Mms.Part.MSG_ID, messageId)
                                                for (partKey in messagePart.keys()) {
                                                    if (partKey in partColumns) part.put(
                                                        partKey, messagePart.getString(partKey)
                                                    )
                                                }
                                                try {
                                                    val insertPartUri =
                                                        appContext.contentResolver.insert(
                                                            partUri, part
                                                        )
                                                    if (insertPartUri == null)
                                                        Log.e(LOG_TAG, "MMS part insert failed!")
                                                    else {
                                                        if (prefs.getBoolean(
                                                                "include_binary_data", true
                                                            )
                                                        ) {
                                                            val filename =
                                                                messagePart.optString(Telephony.Mms.Part._DATA)
                                                            if (filename != "") {
                                                                mmsPartMap[Uri.parse(filename).lastPathSegment.toString()] =
                                                                    insertPartUri
                                                            }
                                                        }
                                                    }
                                                } catch (e: SecurityException) {
                                                    Log.e(LOG_TAG, "SecurityException inserting part", e)
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                displayError(
                                    appContext,
                                    e,
                                    "Error importing messages",
                                    "An error was encountered while importing messages"
                                )
                            }
                        }
                    }
                }
            }
            if (prefs.getBoolean("include_binary_data", true)) {
                setStatusText(
                    statusReportText, appContext.getString(R.string.copying_mms_binary_data)
                )
                val buffer = ByteArray(1048576)
                appContext.contentResolver.openInputStream(zipUri).use { inputStream ->
                    ZipInputStream(inputStream).use { zipInputStream ->
                        var zipEntry = zipInputStream.nextEntry
                        while (zipEntry != null) {
                            if (zipEntry.name.startsWith("data/")) {
                                val partUri = mmsPartMap[zipEntry.name.substring(5)]
                                partUri?.let {
                                    try {
                                        appContext.contentResolver.openOutputStream(
                                            partUri
                                        )?.use { outputStream ->
                                            var n = zipInputStream.read(
                                                buffer
                                            )
                                            while (n > -1) {
                                                outputStream.write(
                                                    buffer, 0, n
                                                )
                                                n = zipInputStream.read(
                                                    buffer
                                                )
                                            }
                                        } ?: Log.e(
                                            LOG_TAG,
                                            "Error opening OutputStream to write MMS binary data"
                                        )
                                    } catch (e: SecurityException) {
                                        Log.e(LOG_TAG, "SecurityException writing binary data", e)
                                    }
                                }
                            }
                            zipEntry = zipInputStream.nextEntry
                        }
                    }
                }
            }
        }
        hideProgressBar(progressBar)
        totals
    }
}