package com.termux.gui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Stops the GUI service when requested by a local client through an explicit broadcast.
 */
class ServiceShutdownReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.stopService(Intent(context, GUIService::class.java))
    }
}
