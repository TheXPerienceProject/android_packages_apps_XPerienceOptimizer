// SPDX-License-Identifier: Apache-2.0
// Copyright 2025 XPerience Project

package mx.xperience.optimizer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemProperties
import android.util.Log
import mx.xperience.optimizer.ui.OptimizerActivity
import mx.xperience.optimizer.R
import java.lang.reflect.Method

class PostUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val prefs = context.getSharedPreferences("system_version", Context.MODE_PRIVATE)
                
                // Obtener timestamp de build actual usando reflexión para SystemProperties
                val currentBuildTimestamp = getBuildTimestamp()
                
                if (currentBuildTimestamp == 0L) {
                    Log.w("PostUpdateReceiver", "No se pudo obtener el timestamp de build")
                    return
                }

                val lastBuildTimestamp = prefs.getLong("last_build_timestamp", 0L)

                if (lastBuildTimestamp == 0L || lastBuildTimestamp != currentBuildTimestamp) {
                    Log.i("PostUpdateReceiver", context.getString(R.string.system_update_detected))
                    Log.d("PostUpdateReceiver", "Timestamp anterior: $lastBuildTimestamp, nuevo: $currentBuildTimestamp")
                    
                    prefs.edit().putLong("last_build_timestamp", currentBuildTimestamp).apply()

                    val optimizerIntent = Intent(context, OptimizerActivity::class.java)
                    optimizerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(optimizerIntent)
                } else {
                    Log.d("PostUpdateReceiver", context.getString(R.string.no_update_detected))
                    Log.d("PostUpdateReceiver", "Timestamp actual: $currentBuildTimestamp")
                }
            }
        }
    }

    private fun getBuildTimestamp(): Long {
        val timestampProperties = listOf(
            "ro.vendor.build.date.utc",
            "ro.build.date.utc", 
            "ro.system.build.date.utc",
            "ro.bootimage.build.date.utc"
        )

        for (prop in timestampProperties) {
            try {
                val timestamp = getSystemPropertyLong(prop, -1L)
                if (timestamp != -1L && timestamp > 0) {
                    Log.d("PostUpdateReceiver", "Timestamp obtenido de $prop: $timestamp")
                    return timestamp
                }
            } catch (e: Exception) {
                // Continuar con la siguiente propiedad
            }
        }

        // Fallback a Build.TIME
        Log.d("PostUpdateReceiver", "Usando Build.TIME como fallback: ${Build.TIME}")
        return Build.TIME / 1000
    }

    private fun getSystemPropertyLong(key: String, defaultValue: Long): Long {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getLongMethod = systemPropertiesClass.getMethod(
                "getLong", 
                String::class.java, 
                Long::class.javaPrimitiveType
            )
            getLongMethod.invoke(null, key, defaultValue) as Long
        } catch (e: Exception) {
            defaultValue
        }
    }
}
