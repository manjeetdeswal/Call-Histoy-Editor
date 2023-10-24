package com.thenotesgiver.callhistoryeditorandbackup

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.ContentResolver
import android.content.ContentValues
import android.os.Bundle
import android.provider.CallLog
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.thenotesgiver.callhistoryeditorandbackup.databinding.EditHistoryBinding
import java.text.SimpleDateFormat
import java.util.Calendar
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


        if (ActivityCompat.checkSelfPermission(
                context,
                "android.permission.WRITE_CALL_LOG"
            ) != 0
        ) {
            requestPermissions(
                ownerActivity!!.parent,
                arrayOf("android.permission.WRITE_CALL_LOG"),
                1
            );


            return
        }


        val simpleDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss aaa")
        val date = simpleDateFormat.parse(historyModel.dateTime)
        val formattedDate = simpleDateFormat.format(date!!)

        val cal = Calendar.getInstance()
        cal.timeInMillis = date.time


binding.cancel.setOnClickListener {
    this.hide()
}


        binding.date.text = formattedDate.toString()

        val hourHistory = historyModel.duration
        val durHour = hourHistory?.substring(0, 2)
        val durMin = hourHistory?.substring(3, 5)
        val durSec = hourHistory?.substring(6, 8)

        binding.durationH.setText(durHour)
        binding.durationM.setText(durMin)
        binding.durationS.setText(durSec)



        when (historyModel.type?.toInt()) {

            1 -> binding.incomeOption.isChecked = true
            2 -> binding.outgoingOption.isChecked = true
            3 -> binding.missedOption.isChecked = true
            5 -> binding.rejectedOption.isChecked = true
        }

        binding.phonenumber.setText(historyModel.number)




        updatedDate = cal.timeInMillis

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

                }, startHour, startMinute, false).show()
            }, startYear, startMonth, startDay).show()
        }


        binding.update.setOnClickListener {


            val updatedType = when (binding.callTypeRadio.checkedRadioButtonId) {

                R.id.incomeOption -> {
                    1
                }

                R.id.outgoingOption -> {
                    2
                }

                R.id.missedOption -> {
                    3
                }

                R.id.rejectedOption -> {
                    5
                }

                else -> {
                    5
                }
            }
            val upHour = binding.durationH.text.toString()
            val upMin = binding.durationM.text.toString()
            val upSec = binding.durationS.text.toString()
            val upTime = "$upHour:$upMin:$upSec"

            val text = binding.phonenumber.text.toString()

            updateCallLog(
                historyModel.id!!, text, updatedType, updatedDate!!, convertTimeToSeconds(
                    upTime
                ).toLong()
            )

            this.dismiss()


        }
        binding.delete.setOnClickListener {

            myViewModel.removeItem(historyModel)
            deleteCallLogEntry(mContext.contentResolver, historyModel.id!!)
            this.hide()
        }


    }

    private fun deleteCallLogEntry(contentResolver: ContentResolver, callLogId: String) {

        contentResolver.delete(CallLog.Calls.CONTENT_URI, "_id=$callLogId", null)
    }


    private fun updateCallLog(
        callLogId: String,
        newNumber: String,
        newType: Int,
        newDate: Long,
        newDuration: Long
    ) {
        // Step 1: Create new call log entry
        val contentResolver: ContentResolver = mContext.contentResolver
        contentResolver.delete(CallLog.Calls.CONTENT_URI, "_id=$callLogId", null)
        myViewModel.removeItem(historyModel)
        val values = ContentValues()

        values.put(CallLog.Calls.NUMBER, newNumber)
        values.put(CallLog.Calls.TYPE, newType) // Call type: Incoming (1), Outgoing (2), Missed (3)
        values.put(CallLog.Calls.DATE, newDate) // Call date in milliseconds
        values.put(CallLog.Calls.DURATION, newDuration) // Call duration in seconds

        contentResolver.insert(CallLog.Calls.CONTENT_URI, values)


        // Step 2: Delete the old call log entry


    }

    private fun convertTimeToSeconds(time: String): Int {

        if (time.isEmpty().not()) {
            val units = time.split(":").toTypedArray()
            if (units.isNotEmpty() && units.size >= 3) {
                val hours = units[0].toInt()
                val minutes = units[1].toInt()
                val seconds = units[2].toInt()
                return (TimeUnit.HOURS.toSeconds(hours.toLong()) +
                        TimeUnit.MINUTES.toSeconds(minutes.toLong()) +
                        seconds).toInt()
            }

        }
        return 0
    }

}
