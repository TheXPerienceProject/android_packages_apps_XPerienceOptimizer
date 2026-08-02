// SPDX-License-Identifier: Apache-2.0
// Copyright 2025 XPerience Project

package mx.xperience.optimizer.receivers

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import mx.xperience.optimizer.R
import mx.xperience.optimizer.jobs.SetupWizardWaitJobService
import mx.xperience.optimizer.ui.OptimizerActivity

class PostUpdateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PostUpdateReceiver"
        private const val SETUP_WIZARD_WAIT_JOB_ID = 4242
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val prefs = context.getSharedPreferences("system_version", Context.MODE_PRIVATE)
                val currentBuildTimestamp = getBuildTimestamp()

                if (currentBuildTimestamp == 0L) {
                    Log.w(TAG, "No se pudo obtener el timestamp de build")
                    return
                }

                val lastBuildTimestamp = prefs.getLong("last_build_timestamp", 0L)

                if (lastBuildTimestamp == 0L || lastBuildTimestamp != currentBuildTimestamp) {
                    Log.i(TAG, context.getString(R.string.system_update_detected))
                    prefs.edit().putLong("last_build_timestamp", currentBuildTimestamp).apply()

                    if (isDeviceProvisioned(context)) {
                        launchOptimizer(context)
                    } else {
                        Log.d(TAG, "SetupWizard is running, the optimizer will be postponed until it finishes")
                        scheduleForAfterSetupWizard(context)
                    }
                } else {
                    Log.d(TAG, context.getString(R.string.no_update_detected))
                }
            }
        }
    }

    private fun isDeviceProvisioned(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver, Settings.Global.DEVICE_PROVISIONED, 0
        ) == 1
    }

    private fun launchOptimizer(context: Context) {
        val optimizerIntent = Intent(context, OptimizerActivity::class.java)
        optimizerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(optimizerIntent)
    }

    private fun scheduleForAfterSetupWizard(context: Context) {
        val jobScheduler = context.getSystemService(JobScheduler::class.java)
        val componentName = ComponentName(context, SetupWizardWaitJobService::class.java)

        val jobInfo = JobInfo.Builder(SETUP_WIZARD_WAIT_JOB_ID, componentName)
            .addTriggerContentUri(
                JobInfo.TriggerContentUri(
                    Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED),
                    JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                )
            )
            .setTriggerContentMaxDelay(0) // It fires as soon as it detects the change
            .build()

        jobScheduler.schedule(jobInfo)
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
                if (timestamp != -1L && timestamp > 0) return timestamp
            } catch (e: Exception) { /* siguiente propiedad */ }
        }
        return Build.TIME / 1000
    }

    private fun getSystemPropertyLong(key: String, defaultValue: Long): Long {
        return try {
            val cls = Class.forName("android.os.SystemProperties")
            val method = cls.getMethod("getLong", String::class.java, Long::class.javaPrimitiveType)
            method.invoke(null, key, defaultValue) as Long
        } catch (e: Exception) {
            defaultValue
        }
    }
}