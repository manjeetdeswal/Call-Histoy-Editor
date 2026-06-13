package com.manjeet_deswal.callhistoryeditorandbackup

import android.Manifest
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.provider.Telephony
import android.text.format.DateUtils.formatElapsedTime
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder


import com.manjeet_deswal.callhistoryeditorandbackup.sms_ie.checkReadCallLogsContactsPermissions
import com.manjeet_deswal.callhistoryeditorandbackup.sms_ie.checkReadContactsPermission
import com.manjeet_deswal.callhistoryeditorandbackup.sms_ie.checkReadSMSContactsPermissions
import com.manjeet_deswal.callhistoryeditorandbackup.sms_ie.checkReadWriteCallLogPermissions
import com.manjeet_deswal.callhistoryeditorandbackup.sms_ie.checkWriteContactsPermission
import com.manjeet_deswal.callhistoryeditorandbackup.sms_ie.exportCallLog
import com.manjeet_deswal.callhistoryeditorandbackup.sms_ie.exportContacts
import com.manjeet_deswal.callhistoryeditorandbackup.sms_ie.exportMessages
import com.manjeet_deswal.callhistoryeditorandbackup.sms_ie.importCallLog
import com.manjeet_deswal.callhistoryeditorandbackup.sms_ie.importContacts
import com.manjeet_deswal.callhistoryeditorandbackup.sms_ie.importMessages
import com.manjeet_deswal.callhistoryeditorandbackup.sms_ie.wipeSmsAndMmsMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.jvm.java
import androidx.core.net.toUri

const val PERMISSIONS_REQUEST = 1
const val LOG_TAG = "SMSIE"
const val CHANNEL_ID = "MYCHANNEL"
const val PDU_HEADERS_FROM = "137"

data class MessageTotal(var sms: Int = 0, var mms: Int = 0)

class MainActivity : AppCompatActivity(), ConfirmWipeFragment.NoticeDialogListener,
    BecomeDefaultSMSAppFragment.NoticeDialogListener {

    private lateinit var prefs: SharedPreferences
    private var loadingDialog: AlertDialog? = null
    private var dialogStatusText: TextView? = null
    private var dialogProgressBar: ProgressBar? = null
    private lateinit var operation: () -> Unit



    private val exportMessagesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val startTime = System.nanoTime()
                showLoadingDialog("Exporting Messages...")
                CoroutineScope(Dispatchers.Main).launch {
                    val total = exportMessages(applicationContext, uri, dialogProgressBar!!, dialogStatusText!!)
                    dialogStatusText?.text = getString(
                        R.string.export_messages_results, total.sms, total.mms, formatElapsedTime(
                            TimeUnit.SECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)
                        )
                    )
                    loadingDialog?.setCancelable(true)
                }
            }
        }
    }

    private val importMessagesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val startTime = System.nanoTime()
                showLoadingDialog("Importing Messages...")
                CoroutineScope(Dispatchers.Main).launch {
                    val total = importMessages(this@MainActivity, uri, dialogProgressBar!!, dialogStatusText!!)
                    dialogStatusText?.text = getString(
                        R.string.import_messages_results, total.sms, total.mms, formatElapsedTime(
                            TimeUnit.SECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)
                        )
                    )
                    loadingDialog?.setCancelable(true)
                }
            }
        }
    }

    private val exportCallLogLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val startTime = System.nanoTime()
                showLoadingDialog("Exporting Call Logs...")
                CoroutineScope(Dispatchers.Main).launch {
                    val total = exportCallLog(applicationContext, uri, dialogProgressBar!!, dialogStatusText!!)
                    dialogStatusText?.text = getString(
                        R.string.export_call_log_results, total.sms, formatElapsedTime(
                            TimeUnit.SECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)
                        )
                    )
                    loadingDialog?.setCancelable(true)
                }
            }
        }
    }

    private val importCallLogLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val startTime = System.nanoTime()
                showLoadingDialog("Importing Call Logs...")
                CoroutineScope(Dispatchers.Main).launch {
                    val callsImported = importCallLog(this@MainActivity, uri, dialogProgressBar!!, dialogStatusText!!)
                    dialogStatusText?.text = getString(
                        R.string.import_call_log_results, callsImported, formatElapsedTime(
                            TimeUnit.SECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)
                        )
                    )
                    loadingDialog?.setCancelable(true)
                }
            }
        }
    }

    private val exportContactsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val startTime = System.nanoTime()
                showLoadingDialog("Exporting Contacts...")
                CoroutineScope(Dispatchers.Main).launch {
                    val contactsExported = exportContacts(applicationContext, uri, dialogProgressBar!!, dialogStatusText!!)
                    dialogStatusText?.text = getString(
                        R.string.export_contacts_results, contactsExported, formatElapsedTime(
                            TimeUnit.SECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)
                        )
                    )
                    loadingDialog?.setCancelable(true)
                }
            }
        }
    }

    private val importContactsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val startTime = System.nanoTime()
                showLoadingDialog("Importing Contacts...")
                CoroutineScope(Dispatchers.Main).launch {
                    val contactsImported = importContacts(this@MainActivity, uri, dialogProgressBar!!, dialogStatusText!!)
                    dialogStatusText?.text = getString(
                        R.string.import_contacts_results, contactsImported, formatElapsedTime(
                            TimeUnit.SECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)
                        )
                    )
                    loadingDialog?.setCancelable(true)
                }
            }
        }
    }

    private val becomeDefaultSmsAppLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            if (::operation.isInitialized) {
                operation()
            }
        }
    }





    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        val allPermissions = listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG
        )
        val necessaryPermissions = mutableListOf<String>()
        allPermissions.forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                necessaryPermissions.add(it)
            }
        }

        if (necessaryPermissions.any()) {
            ActivityCompat.requestPermissions(
                this,
                necessaryPermissions.toTypedArray(),
                PERMISSIONS_REQUEST
            )
        }

        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        val exportMessagesButton: Button = findViewById(R.id.export_messages_button)
        val exportCallLogButton: Button = findViewById(R.id.export_call_log_button)
        val importMessagesButton: Button = findViewById(R.id.import_messages_button)
        val importCallLogButton: Button = findViewById(R.id.import_call_log_button)
        val wipeAllMessagesButton: Button = findViewById(R.id.wipe_all_messages_button)
        val exportContactsButton: Button = findViewById(R.id.export_contacts_button)
        val websiteButton: Button = findViewById(R.id.website_button)
        val importContactsButton: Button = findViewById(R.id.import_contacts_button)

        exportMessagesButton.setOnClickListener { exportMessagesManual() }
        importMessagesButton.setOnClickListener {
            operation = ::importMessagesManual
            checkDefaultSMSApp()
        }
        exportCallLogButton.setOnClickListener { exportCallLogManual() }
        importCallLogButton.setOnClickListener { importCallLogManual() }
        exportContactsButton.setOnClickListener { exportContactsManual() }
        importContactsButton.setOnClickListener { importContactsManual() }
        websiteButton.setOnClickListener {
            val websiteIntent = Intent(Intent.ACTION_VIEW,
                "https://manjeetdeswal.github.io/portfolio/".toUri())
            startActivity(websiteIntent)
        }

        wipeAllMessagesButton.setOnClickListener {
            val intent = Intent(this, CallHistory::class.java)
            startActivity(intent)
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        if (SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
            mChannel.description = descriptionText
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }
    }

    private fun exportMessagesManual() {
        if (checkReadSMSContactsPermissions(this)) {
            val date = getCurrentDateTime()
            val dateInString = date.toString("yyyy-MM-dd")
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, "messages-$dateInString.zip")
            }
            exportMessagesLauncher.launch(intent)
        } else {
            setStatusReport(getString(R.string.sms_permissions_required))
        }
    }

    private fun importMessagesManual() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (SDK_INT < 29) "*/*" else "application/zip"
        }
        importMessagesLauncher.launch(intent)
    }

    private fun exportCallLogManual() {
        if (checkReadCallLogsContactsPermissions(this)) {
            val date = getCurrentDateTime()
            val dateInString = date.toString("yyyy-MM-dd")
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "calls-$dateInString.json")
            }
            exportCallLogLauncher.launch(intent)
        } else {
            setStatusReport(getString(R.string.call_logs_permissions_required))
        }
    }

    private fun importCallLogManual() {
        if (checkReadWriteCallLogPermissions(this)) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (SDK_INT < 29) "*/*" else "application/json"
            }
            importCallLogLauncher.launch(intent)
        } else {
            setStatusReport(getString(R.string.call_logs_read_write_permissions_required))
        }
    }

    private fun exportContactsManual() {
        if (checkReadContactsPermission(this)) {
            val date = getCurrentDateTime()
            val dateInString = date.toString("yyyy-MM-dd")
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "contacts-$dateInString.json")
            }
            exportContactsLauncher.launch(intent)
        } else {
            setStatusReport(getString(R.string.contacts_read_permission_required))
        }
    }

    private fun importContactsManual() {
        if (checkWriteContactsPermission(this)) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (SDK_INT < 29) "*/*" else "application/json"
            }
            importContactsLauncher.launch(intent)
        } else {
            setStatusReport(getString(R.string.contacts_write_permissions_required))
        }
    }

    private fun showLoadingDialog(message: String) {
        if (loadingDialog == null) {
            val view = layoutInflater.inflate(R.layout.dialog_loading, null)
            dialogStatusText = view.findViewById(R.id.dialogStatusText)
            dialogProgressBar = view.findViewById(R.id.dialogProgressBar)

            loadingDialog = MaterialAlertDialogBuilder(this)
                .setView(view)
                .setCancelable(false)
                .create()

            loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }

        dialogStatusText?.text = message
        loadingDialog?.setCancelable(false)

        if (loadingDialog?.isShowing == false) {
            loadingDialog?.show()
        }
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
    }

    override fun onWipeDialogPositiveClick(dialog: DialogFragment) {
        showLoadingDialog("Wiping messages...")
        CoroutineScope(Dispatchers.Main).launch {
            wipeSmsAndMmsMessages(applicationContext, dialogStatusText!!, dialogProgressBar!!)
            dialogStatusText?.text = getString(R.string.messages_wiped)
            loadingDialog?.setCancelable(true)
        }
    }

    override fun onWipeDialogNegativeClick(dialog: DialogFragment) {
        Toast.makeText(this, getString(R.string.wipe_cancelled), Toast.LENGTH_SHORT).show()
    }

    override fun onDefaultSMSAppDialogPositiveClick(dialog: DialogFragment) {
        if (SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            becomeDefaultSmsAppLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
        } else {
            val becomeDefaultSMSAppIntent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            becomeDefaultSMSAppIntent.putExtra(
                Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName
            )
            becomeDefaultSmsAppLauncher.launch(becomeDefaultSMSAppIntent)
        }
    }

    private fun setStatusReport(statusReport: String) {
        Toast.makeText(this, statusReport, Toast.LENGTH_LONG).show()
    }

    private fun checkDefaultSMSApp() {
        if (SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && !roleManager.isRoleHeld(
                    RoleManager.ROLE_SMS
                )
            ) {
                BecomeDefaultSMSAppFragment().show(supportFragmentManager, "become_default_sms_app")
            } else operation()
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(this) != packageName) {
                BecomeDefaultSMSAppFragment().show(supportFragmentManager, "become_default_sms_app")
            } else operation()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideLoadingDialog()
    }
}

class ConfirmWipeFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.setMessage(R.string.dialog_confirm_wipe).setPositiveButton(
                R.string.wipe
            ) { _, _ ->
                listener.onWipeDialogPositiveClick(this)
            }.setNegativeButton(
                R.string.cancel
            ) { _, _ ->
                listener.onWipeDialogNegativeClick(this)
            }.setTitle(R.string.wipe_messages)
                .setIcon(android.R.drawable.ic_dialog_alert)
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private lateinit var listener: NoticeDialogListener

    interface NoticeDialogListener {
        fun onWipeDialogPositiveClick(dialog: DialogFragment)
        fun onWipeDialogNegativeClick(dialog: DialogFragment)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = context as NoticeDialogListener
        } catch (_: ClassCastException) {
            throw ClassCastException(("$context must implement NoticeDialogListener"))
        }
    }
}

class BecomeDefaultSMSAppFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.setMessage(R.string.become_default_sms_app_warning).setPositiveButton(
                R.string.okay
            ) { _, _ ->
                listener.onDefaultSMSAppDialogPositiveClick(this)
            }.setTitle(R.string.default_sms_app_dialog_title)
                .setIcon(android.R.drawable.ic_dialog_alert)
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private lateinit var listener: NoticeDialogListener

    interface NoticeDialogListener {
        fun onDefaultSMSAppDialogPositiveClick(dialog: DialogFragment)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = context as NoticeDialogListener
        } catch (_: ClassCastException) {
            throw ClassCastException(("$context must implement NoticeDialogListener"))
        }
    }
}

fun Date.toString(format: String, locale: Locale = Locale.getDefault()): String {
    val formatter = SimpleDateFormat(format, locale)
    return formatter.format(this)
}

fun getCurrentDateTime(): Date {
    return Calendar.getInstance().time
}