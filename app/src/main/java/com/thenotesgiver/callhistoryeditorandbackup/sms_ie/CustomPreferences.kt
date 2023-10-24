
package com.thenotesgiver.callhistoryeditorandbackup.sms_ie

import android.content.Context
import java.util.concurrent.TimeUnit
import android.util.AttributeSet
import androidx.preference.DialogPreference

// from: https://old.black/2020/09/18/building-custom-timepicker-dialog-preference-in-android-kotlin/
class TimePickerPreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs) {

    // Get saved preference value (in minutes from midnight, so 1 AM is represented as 1*60 here
    fun getPersistedMinutesFromMidnight(): Int {
        return super.getPersistedInt(DEFAULT_MINUTES_FROM_MIDNIGHT)
    }

    // Save preference
    fun persistMinutesFromMidnight(minutesFromMidnight: Int) {
        super.persistInt(minutesFromMidnight)
        notifyChanged()
        updateExportWork(context)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        super.onSetInitialValue(defaultValue)
        summary = minutesFromMidnightToHourlyTime(getPersistedMinutesFromMidnight())
    }

    // Mostly for default values
    companion object {
        // default is 2:00 a.m.
        private const val DEFAULT_HOUR = 2
        const val DEFAULT_MINUTES_FROM_MIDNIGHT = DEFAULT_HOUR * 60
    }
}

// from: https://stackoverflow.com/a/8916605
fun minutesFromMidnightToHourlyTime(minutesFromMidnight: Int): CharSequence {
    val hours = TimeUnit.MINUTES.toHours(minutesFromMidnight.toLong())
    val remainMinutes = minutesFromMidnight - TimeUnit.HOURS.toMinutes(hours)
    return String.format("%02d:%02d", hours, remainMinutes)
}
