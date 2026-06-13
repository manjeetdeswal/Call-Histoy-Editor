package com.manjeet_deswal.callhistoryeditorandbackup.sms_ie

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.BaseColumns
import android.provider.CallLog
import android.util.JsonReader
import android.util.JsonWriter
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.manjeet_deswal.callhistoryeditorandbackup.LOG_TAG
import com.manjeet_deswal.callhistoryeditorandbackup.MessageTotal
import com.manjeet_deswal.callhistoryeditorandbackup.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

suspend fun exportCallLog(
    appContext: Context,
    file: Uri,
    progressBar: ProgressBar?,
    statusReportText: TextView?
): MessageTotal {
    return withContext(Dispatchers.IO) {
        val totals = MessageTotal()
        val displayNames = mutableMapOf<String, String?>()
        appContext.contentResolver.openOutputStream(file).use { outputStream ->
            BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                val jsonWriter = JsonWriter(writer)
                jsonWriter.setIndent("  ")
                jsonWriter.beginArray()
                totals.sms = callLogToJSON(
                    appContext,
                    jsonWriter,
                    displayNames,
                    progressBar,
                    statusReportText
                )
                jsonWriter.endArray()
            }
        }
        totals
    }
}

private suspend fun callLogToJSON(
    appContext: Context,
    jsonWriter: JsonWriter,
    displayNames: MutableMap<String, String?>,
    progressBar: ProgressBar?,
    statusReportText: TextView?
): Int {
    val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
    var total = 0

    // Fixed: Explicitly handled SecurityException for permissions
    val callCursor = try {
        appContext.contentResolver.query(
            Uri.parse("content://call_log/calls"),
            null, null, null, null
        )
    } catch (e: SecurityException) {
        Log.e(LOG_TAG, "Permission denied while querying call logs.", e)
        return 0
    }

    callCursor?.use {
        if (it.moveToFirst()) {
            val totalCalls = it.count
            initProgressBar(progressBar, it)
            val addressIndex = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            do {
                jsonWriter.beginObject()
                it.columnNames.forEachIndexed { i, columnName ->
                    val value = it.getString(i)
                    if (value != null) jsonWriter.name(columnName).value(value)
                }
                val address = it.getString(addressIndex)
                if (address != null) {
                    val displayName = lookupDisplayName(appContext, displayNames, address)
                    if (displayName != null) jsonWriter.name("display_name").value(displayName)
                }
                jsonWriter.endObject()
                total++
                incrementProgress(progressBar)
                setStatusText(
                    statusReportText,
                    appContext.getString(R.string.call_log_export_progress, total, totalCalls)
                )
                if (total == (prefs.getString("max_records", "")?.toIntOrNull() ?: -1)) break
            } while (it.moveToNext())
            hideProgressBar(progressBar)
        }
    }
    return total
}

suspend fun importCallLog(
    appContext: Context,
    uri: Uri,
    progressBar: ProgressBar,
    statusReportText: TextView
): Int {
    return withContext(Dispatchers.IO) {
        val callLogColumns = mutableSetOf<String>()

        // Fixed: Explicitly handled SecurityException for permissions
        val callLogCursor = try {
            appContext.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null, null, null, null
            )
        } catch (e: SecurityException) {
            Log.e(LOG_TAG, "Permission denied while checking call log columns.", e)
            null
        }

        callLogCursor?.use { callLogColumns.addAll(it.columnNames) }
        var callLogCount = 0
        initIndeterminateProgressBar(progressBar)
        uri.let {
            appContext.contentResolver.openInputStream(it).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val jsonReader = JsonReader(reader)
                    val callLogMetadata = ContentValues()
                    try {
                        jsonReader.beginArray()
                        while (jsonReader.hasNext()) {
                            jsonReader.beginObject()
                            callLogMetadata.clear()
                            while (jsonReader.hasNext()) {
                                val name = jsonReader.nextName()
                                val value = jsonReader.nextString()
                                if ((callLogColumns.contains(name)) and (name !in setOf(
                                        BaseColumns._ID,
                                        BaseColumns._COUNT
                                    ))
                                ) {
                                    callLogMetadata.put(name, value)
                                }
                            }
                            var insertUri: Uri? = null
                            if (callLogMetadata.keySet().contains(CallLog.Calls.NUMBER) && callLogMetadata.getAsString(CallLog.Calls.TYPE) != "4") {
                                // Fixed: Explicitly handled SecurityException for insert
                                try {
                                    insertUri = appContext.contentResolver.insert(
                                        CallLog.Calls.CONTENT_URI,
                                        callLogMetadata
                                    )
                                } catch (e: SecurityException) {
                                    Log.e(LOG_TAG, "Permission denied while inserting call log.", e)
                                }
                            }
                            if (insertUri == null) {
                                Log.v(LOG_TAG, "Call log insert failed!")
                            } else {
                                callLogCount++
                                setStatusText(
                                    statusReportText,
                                    appContext.getString(
                                        R.string.call_log_import_progress,
                                        callLogCount
                                    )
                                )
                            }
                            jsonReader.endObject()
                        }
                        jsonReader.endArray()
                    } catch (e: Exception) {
                        displayError(
                            appContext,
                            e,
                            "Error importing call log",
                            "Error parsing JSON"
                        )
                    }
                }
            }
            hideProgressBar(progressBar)
            callLogCount
        }
    }
}