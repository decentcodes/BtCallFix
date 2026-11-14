package com.example.btcallfix.call

import com.example.btcallfix.data.Prefs
import com.example.btcallfix.shizuku.ShizukuBridge
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = i.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val sp = Prefs.sp(c)
        val last = sp.getString("last", "") ?: ""
        if (state == last) return                         // duplicate broadcast
        val allow = when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> true  // answered / dialing
            TelephonyManager.EXTRA_STATE_IDLE -> false    // call ended
            TelephonyManager.EXTRA_STATE_RINGING ->
                if (last == TelephonyManager.EXTRA_STATE_OFFHOOK) return else false  // ignore call-waiting
            else -> return
        }
        sp.edit().putString("last", state).apply()
        val addr = Prefs.address(c) ?: return
        val pr = goAsync()
        ShizukuBridge.setProfile(c, addr, allow) { msg ->
            Prefs.log(c, "$state -> $msg")
            pr.finish()
        }
    }
}
