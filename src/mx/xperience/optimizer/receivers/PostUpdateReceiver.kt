// SPDX-License-Identifier: Apache-2.0
// Copyright 2024 XPerience Project

package mx.xperience.optimizer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import mx.xperience.optimizer.ui.OptimizerActivity

class PostUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val prefs = context.getSharedPreferences("system_version", Context.MODE_PRIVATE)
                val lastFingerprint = prefs.getString("last_fingerprint", null)
                val currentFingerprint = Build.FINGERPRINT

                if (lastFingerprint == null || lastFingerprint != currentFingerprint) {
                    Log.i("PostUpdateReceiver", "System update detected on boot. Launching optimizer...")
                    prefs.edit().putString("last_fingerprint", currentFingerprint).apply()

                    val optimizerIntent = Intent(context, OptimizerActivity::class.java)
                    optimizerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(optimizerIntent)
                } else {
                    Log.d("PostUpdateReceiver", "No system update detected on boot.")
                }
            }

        }
    }
}
