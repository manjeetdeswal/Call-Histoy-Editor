package com.manjeet_deswal.callhistoryeditorandbackup

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.ContentResolver
import android.content.ContentValues
import android.os.Bundle
import android.provider.CallLog
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

import com.manjeet_deswal.callhistoryeditorandbackup.databinding.EditHistoryBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class EditDialog(
    private val mContext: AppCompatActivity,
    private val historyModel: HistoryModel,
    private val myViewModel: HistoryViewModel
) : Dialog(mContext) {

    private lateinit var binding: EditHistoryBinding
    private var updatedDate: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EditHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check permission safely
        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_CALL_LOG
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                mContext,
                arrayOf(android.Manifest.permission.WRITE_CALL_LOG),
                1
            )
            return
        }

        binding.cancel.setOnClickListener {
            this.dismiss()
        }

        val simpleDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss aaa", Locale.getDefault())
        val cal = Calendar.getInstance()

        val isNewEntry = historyModel.id.isNullOrEmpty()

        // 1. Handle Date parsing safely
        if (isNewEntry || historyModel.dateTime.isNullOrEmpty()) {
            // New entry: Use current time
            updatedDate = cal.timeInMillis
            binding.date.text = simpleDateFormat.format(cal.time)
        } else {
            // Edit entry: Parse existing time
            try {
                val date = simpleDateFormat.parse(historyModel.dateTime)
                if (date != null) {
                    cal.timeInMillis = date.time
                }
                updatedDate = cal.timeInMillis
                binding.date.text = simpleDateFormat.format(cal.time)
            } catch (e: Exception) {
                // Fallback to current time if parsing fails
                updatedDate = cal.timeInMillis
                binding.date.text = simpleDateFormat.format(cal.time)
            }
        }

        // 2. Handle Duration parsing safely
        val hourHistory = historyModel.duration
        if (!hourHistory.isNullOrEmpty() && hourHistory.length >= 8) {
            val durHour = hourHistory.substring(0, 2)
            val durMin = hourHistory.substring(3, 5)
            val durSec = hourHistory.substring(6, 8)
            binding.durationH.setText(durHour)
            binding.durationM.setText(durMin)
            binding.durationS.setText(durSec)
        } else {
            // New entry or invalid data: Default to 0
            binding.durationH.setText("00")
            binding.durationM.setText("00")
            binding.durationS.setText("00")
        }

        // 3. Set Phone Number and Call Type
        binding.phonenumber.setText(historyModel.number ?: "")

        when (historyModel.type?.toIntOrNull()) {
            1 -> binding.incomeOption.isChecked = true
            2 -> binding.outgoingOption.isChecked = true
            3 -> binding.missedOption.isChecked = true
            5 -> binding.rejectedOption.isChecked = true
            else -> binding.incomeOption.isChecked = true // Default for new entries
        }

        // 4. Update UI Buttons based on mode (Create vs Edit)
        if (isNewEntry) {
            binding.update.text = "Save"
            binding.delete.visibility = View.GONE
        } else {
            binding.update.text = "Update"
            binding.delete.visibility = View.VISIBLE
        }

        // Date Picker setup
        binding.updateTime.setOnClickListener {
            val startYear = cal.get(Calendar.YEAR)
            val startMonth = cal.get(Calendar.MONTH)
            val startDay = cal.get(Calendar.DAY_OF_MONTH)
            val startHour = cal.get(Calendar.HOUR_OF_DAY)
            val startMinute = cal.get(Calendar.MINUTE)

            DatePickerDialog(mContext, { _, year, month, day ->
                TimePickerDialog(mContext, { _, hour, minute ->
                    val pickedDateTime = Calendar.getInstance()
                    pickedDateTime.set(year, month, day, hour, minute)
                    updatedDate = pickedDateTime.timeInMillis
                    binding.date.text = simpleDateFormat.format(pickedDateTime.time)
                }, startHour, startMinute, false).show()
            }, startYear, startMonth, startDay).show()
        }

        // Save/Update Button
        binding.update.setOnClickListener {
            val updatedType = when (binding.callTypeRadio.checkedRadioButtonId) {
                R.id.incomeOption -> 1
                R.id.outgoingOption -> 2
                R.id.missedOption -> 3
                R.id.rejectedOption -> 5
                else -> 1 // Default incoming
            }

            // Format single digits to double digits (e.g., "5" becomes "05") to prevent conversion crashes
            val upHour = binding.durationH.text.toString().padStart(2, '0')
            val upMin = binding.durationM.text.toString().padStart(2, '0')
            val upSec = binding.durationS.text.toString().padStart(2, '0')
            val upTime = "$upHour:$upMin:$upSec"

            val textNumber = binding.phonenumber.text.toString()

            saveCallLog(
                callLogId = historyModel.id,
                newNumber = textNumber,
                newType = updatedType,
                newDate = updatedDate ?: System.currentTimeMillis(),
                newDuration = convertTimeToSeconds(upTime).toLong()
            )

            this.dismiss()
        }

        // Delete Button
        binding.delete.setOnClickListener {
            if (!isNewEntry) {
                myViewModel.removeItem(historyModel)
                deleteCallLogEntry(mContext.contentResolver, historyModel.id!!)
            }
            this.dismiss()
        }
    }

    private fun deleteCallLogEntry(contentResolver: ContentResolver, callLogId: String) {
        contentResolver.delete(CallLog.Calls.CONTENT_URI, "_id=$callLogId", null)
    }

    private fun saveCallLog(
        callLogId: String?,
        newNumber: String,
        newType: Int,
        newDate: Long,
        newDuration: Long
    ) {
        val contentResolver: ContentResolver = mContext.contentResolver

        // Step 1: If editing an old log, delete it first
        if (!callLogId.isNullOrEmpty()) {
            contentResolver.delete(CallLog.Calls.CONTENT_URI, "_id=$callLogId", null)
            myViewModel.removeItem(historyModel)
        }

        // Step 2: Insert the new call log entry
        val values = ContentValues()
        values.put(CallLog.Calls.NUMBER, newNumber)
        values.put(CallLog.Calls.TYPE, newType)
        values.put(CallLog.Calls.DATE, newDate)
        values.put(CallLog.Calls.DURATION, newDuration)

        contentResolver.insert(CallLog.Calls.CONTENT_URI, values)
    }

    private fun convertTimeToSeconds(time: String): Int {
        if (time.isNotEmpty()) {
            val units = time.split(":").toTypedArray()
            if (units.size >= 3) {
                try {
                    val hours = units[0].toInt()
                    val minutes = units[1].toInt()
                    val seconds = units[2].toInt()
                    return (TimeUnit.HOURS.toSeconds(hours.toLong()) +
                            TimeUnit.MINUTES.toSeconds(minutes.toLong()) +
                            seconds).toInt()
                } catch (e: NumberFormatException) {
                    return 0
                }
            }
        }
        return 0
    }
}