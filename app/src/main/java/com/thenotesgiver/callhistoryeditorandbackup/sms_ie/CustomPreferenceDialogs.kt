

package com.thenotesgiver.callhistoryeditorandbackup.sms_ie

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TimePicker
import androidx.preference.PreferenceDialogFragmentCompat



class TimePreferenceDialog : PreferenceDialogFragmentCompat() {

    private lateinit var timePicker: TimePicker

    override fun onCreateDialogView(context: Context): View {
        timePicker = TimePicker(context)
        return timePicker
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        val minutesAfterMidnight = (preference as TimePickerPreference)
            .getPersistedMinutesFromMidnight()
        //timePicker.setIs24HourView(true)
        timePicker.hour = minutesAfterMidnight / 60
        timePicker.minute = minutesAfterMidnight % 60
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        // Save settings
        if(positiveResult) {
            val minutesAfterMidnight = (timePicker.hour * 60) + timePicker.minute
            (preference as TimePickerPreference).persistMinutesFromMidnight(minutesAfterMidnight)
            preference.summary = minutesFromMidnightToHourlyTime(minutesAfterMidnight)
        }
    }

    companion object {
        fun newInstance(key: String): TimePreferenceDialog {
            val fragment = TimePreferenceDialog()
            val bundle = Bundle(1)
            bundle.putString(ARG_KEY, key)
            fragment.arguments = bundle
            return fragment
        }
    }
}
