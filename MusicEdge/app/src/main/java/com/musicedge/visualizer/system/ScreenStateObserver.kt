package com.musicedge.visualizer.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager

/**
 * Tracks display interactivity. ACTION_SCREEN_ON / ACTION_SCREEN_OFF are
 * protected broadcasts that can only be received by a runtime-registered receiver,
 * which is why this is not a manifest entry.
 */
class ScreenStateObserver(
    private val context: Context,
    private val onScreenStateChanged: (interactive: Boolean) -> Unit,
) {

    private val powerManager: PowerManager =
        requireNotNull(context.getSystemService(PowerManager::class.java))
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> onScreenStateChanged(true)
                Intent.ACTION_SCREEN_OFF -> onScreenStateChanged(false)
            }
        }
    }

    val isInteractive: Boolean get() = powerManager.isInteractive

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        registered = true
    }

    fun stop() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
    }
}
