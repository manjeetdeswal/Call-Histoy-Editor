package com.manjeet_deswal.callhistoryeditorandbackup.sms_ie

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.manjeet_deswal.callhistoryeditorandbackup.CHANNEL_ID
import com.manjeet_deswal.callhistoryeditorandbackup.LOG_TAG
import com.manjeet_deswal.callhistoryeditorandbackup.MessageTotal
import com.manjeet_deswal.callhistoryeditorandbackup.R
import com.manjeet_deswal.callhistoryeditorandbackup.getCurrentDateTime
import com.manjeet_deswal.callhistoryeditorandbackup.toString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

class ExportWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    // Fixed: Moved to companion object as const val to resolve naming and scope warnings
    companion object {
        const val EXPORT_DIR = "export_dir"
        const val EXPORT_WORK_TAG = "EXPORT_WORK_TAG"
    }

    override fun doWork(): Result {
        val context = applicationContext
        var result = Result.success()
        var messageTotal = MessageTotal()
        var callsTotal = 0
        var contacts = 0
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val treeUri = Uri.parse(prefs.getString(EXPORT_DIR, ""))
        val documentTree = context.let { DocumentFile.fromTreeUri(context, treeUri) }
        val date = getCurrentDateTime()
        val dateInString = "-${date.toString("yyyy-MM-dd")}"

        CoroutineScope(Dispatchers.IO).launch {
            if (prefs.getBoolean("export_messages", true)) {
                val file =
                    documentTree?.createFile("application/zip", "messages$dateInString.zip")
                val fileUri = file?.uri
                if (fileUri != null) {
                    Log.i(LOG_TAG, "Beginning messages export ...")
                    messageTotal = exportMessages(context, fileUri, null, null)
                    Log.i(
                        LOG_TAG,
                        "Messages export successful: ${messageTotal.sms} SMSs and ${messageTotal.mms} MMSs exported"
                    )
                    deleteOldExports(prefs, documentTree, file, "messages")
                } else {
                    Log.e(LOG_TAG, "Messages export failed - could not create file")
                    result = Result.failure()
                }
            }
            if (prefs.getBoolean("export_calls", true)) {
                val file =
                    documentTree?.createFile("application/json", "calls$dateInString.json")
                val fileUri = file?.uri
                if (fileUri != null) {
                    Log.i(LOG_TAG, "Beginning call log export ...")
                    val total = exportCallLog(context, fileUri, null, null)
                    callsTotal = total.sms
                    Log.i(
                        LOG_TAG,
                        "Call log export successful: $callsTotal calls exported"
                    )
                    deleteOldExports(prefs, documentTree, file, "calls")
                } else {
                    Log.e(LOG_TAG, "Call log export failed - could not create file")
                    result = Result.failure()
                }
            }
            if (prefs.getBoolean("export_contacts", true)) {
                val file =
                    documentTree?.createFile("application/json", "contacts$dateInString.json")
                val fileUri = file?.uri
                if (fileUri != null) {
                    Log.i(LOG_TAG, "Beginning contacts export ...")
                    contacts = exportContacts(context, fileUri, null, null)
                    Log.i(
                        LOG_TAG,
                        "Contacts export successful: $contacts contacts exported"
                    )
                    deleteOldExports(prefs, documentTree, file, "contacts")
                } else {
                    Log.e(LOG_TAG, "Contacts export failed - could not create file")
                    result = Result.failure()
                }
            }

            // Fixed: Removed redundant qualifier (com.thenotesgiver...)
            val notification = if (result == Result.success()) context.getString(
                R.string.scheduled_export_success,
                messageTotal.sms,
                messageTotal.mms,
                callsTotal,
                contacts
            ) else context.getString(R.string.scheduled_export_failure)

            val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_scheduled_export_done)
                .setContentTitle(context.getString(R.string.scheduled_export_executed))
                .setContentText(notification)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            with(NotificationManagerCompat.from(applicationContext)) {
                notify(0, builder.build())
            }
        }
        updateExportWork(context)
        return result
    }
}

fun updateExportWork(context: Context) {
    // Fixed: Referenced EXPORT_WORK_TAG via the ExportWorker companion object
    WorkManager.getInstance(context)
        .cancelAllWorkByTag(ExportWorker.EXPORT_WORK_TAG)
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    if (prefs.getBoolean("schedule_export", false)) {
        val now = Calendar.getInstance()
        val exportTime = Calendar.getInstance()
        exportTime.set(Calendar.HOUR_OF_DAY, 0)
        exportTime.set(Calendar.MINUTE, 0)
        exportTime.set(Calendar.SECOND, 0)
        exportTime.set(Calendar.MILLISECOND, 0)
        exportTime.add(Calendar.MINUTE, prefs.getInt("export_time", 0))
        if (exportTime < now) {
            exportTime.add(Calendar.DAY_OF_MONTH, 1)
        }
        val deferMillis = exportTime.timeInMillis - now.timeInMillis
        Log.d(LOG_TAG, "Scheduling backup for $deferMillis milliseconds from now")
        val exportRequest: WorkRequest =
            OneTimeWorkRequestBuilder<ExportWorker>()
                .addTag(ExportWorker.EXPORT_WORK_TAG)
                .setInitialDelay(deferMillis, TimeUnit.MILLISECONDS)
                .build()
        WorkManager
            .getInstance(context)
            .enqueue(exportRequest)
    }
}

fun deleteOldExports(
    prefs: SharedPreferences,
    documentTree: DocumentFile,
    newExport: DocumentFile?,
    prefix: String
) {
    if (prefs.getBoolean("delete_old_exports", false)) {
        Log.i(LOG_TAG, "Deleting old exports ...")
        val newFilename = newExport?.name.toString()
        val files = documentTree.listFiles()
        var total = 0
        val extension = if (prefix == "messages") "zip" else "json"
        files.forEach {
            val name = it.name
            if (name != null && name != newFilename && name.startsWith(prefix) && name.endsWith(
                    ".$extension"
                )
            ) {
                it.delete()
                total++
            }
        }
        if (prefs.getBoolean("remove_datestamps_from_filenames", false)) {
            newExport?.renameTo("$prefix.$extension")
        }
        Log.i(LOG_TAG, "$total exports deleted")
    }
}