

package com.thenotesgiver.callhistoryeditorandbackup.sms_ie

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ExportService : Service() {

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
