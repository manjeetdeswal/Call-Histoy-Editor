package com.thenotesgiver.callhistoryeditorandbackup.sms_ie

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.ActivityCompat


class Sims {

    private var sims: MutableList<String?> = ArrayList<String?>()

    fun loadSims(context: Context?) {
        val from: SubscriptionManager
        val activeSubscriptionInfoList: List<Any>?
        var subscriptionId: Int
        var iccId: String
        var iccId2: String
        var iccId3: String
        sims = ArrayList()
        if (ActivityCompat.checkSelfPermission(
                context!!,
                if (Build.VERSION.SDK_INT <= 29) "android.permission.READ_PHONE_STATE" else "android.permission.READ_PHONE_NUMBERS"
            ) != 0
        ) {
            return
        }
        try {
            from = SubscriptionManager.from(context)
            activeSubscriptionInfoList = from.activeSubscriptionInfoList
            if (activeSubscriptionInfoList == null) {
                return
            }
            /*for (obj in activeSubscriptionInfoList) {
                val m: SubscriptionInfo = SubscriptionManager.
                try {
                    try {
                        iccId = m.iccId
                        if (!iccId.matches("".toRegex())) {
                            iccId2 = m.iccId
                            if (iccId2 != null) {
                                val list = sims
                                iccId3 = m.iccId
                                list.add(iccId3)
                            }
                        }
                    } catch (unused: Exception) {
                        Log.v("E", "Err")
                    }
                } catch (unused2: Exception) {
                    val list2 = sims
                    val sb = StringBuilder()
                    sb.append("")
                    subscriptionId = m.subscriptionId
                    sb.append(subscriptionId)
                    list2.add(sb.toString())
                }
            }*/
        } catch (unused3: Exception) {
            Log.v("FML", "FuMyLi")
        }
    }

    fun simNumberFromId(str: String?): Int {
        val indexOf = sims.indexOf(str)
        return if (indexOf >= 0) {
            1 + indexOf
        } else 1
    }

    fun isDualSim(): Boolean {
        return sims.size == 2
    }

}