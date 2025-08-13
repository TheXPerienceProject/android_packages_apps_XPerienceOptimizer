// SPDX-License-Identifier: Apache-2.0
// Copyright 2025 XPerience Project

package mx.xperience.optimizer.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.progressindicator.CircularProgressIndicator
import mx.xperience.optimizer.R
import mx.xperience.optimizer.workers.OptimizerWorker

import java.util.UUID

class OptimizerActivity : AppCompatActivity() {

    // UI Components
    private lateinit var circularProgress: CircularProgressIndicator
    private lateinit var tvPercentage: TextView
    private lateinit var btnOptimize: Button
    private lateinit var cbStore: CheckBox
    private lateinit var cbPrivateCode: CheckBox

    // WorkManager
    private var workRequestId: UUID? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_optimizer)

        // Inicializar vistas
        circularProgress = findViewById(R.id.circular_progress)
        tvPercentage = findViewById(R.id.tv_percentage)
        btnOptimize = findViewById(R.id.btn_optimize)
        cbStore = findViewById(R.id.cb_store)
        cbPrivateCode = findViewById(R.id.cb_private_code)

        // Configure listeners
        btnOptimize.setOnClickListener {
            startOptimizationWithWorkManager()
        }

        // Initialize progress
        updateProgressUI(0)
        createNotificationChannel()
    }

    private fun startOptimizationWithWorkManager() {
        btnOptimize.isEnabled = false
        
        val workRequest = OneTimeWorkRequest.Builder(OptimizerWorker::class.java).build()
        workRequestId = workRequest.id
        WorkManager.getInstance(this).enqueue(workRequest)

        // Observe progress
        WorkManager.getInstance(this)
            .getWorkInfoByIdLiveData(workRequest.id)
            .observe(this) { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getInt(OptimizerWorker.PROGRESS_KEY, 0)
                        val currentApp = workInfo.progress.getString(OptimizerWorker.CURRENT_APP_NAME_KEY)
                        
                        updateProgressUI(progress)
                        updateCheckboxes(progress)
                        
                        currentApp?.let {
                            showInProgressNotification(progress, it)
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        updateProgressUI(100)
                        cbStore.isChecked = true
                        cbPrivateCode.isChecked = true
                        showCompletionNotification()
                        finish()
                    }
                    WorkInfo.State.FAILED -> {
                        btnOptimize.isEnabled = true
                        showErrorNotification()
                    }
                    else -> {}
                }
            }
    }

    private fun updateProgressUI(progress: Int) {
        circularProgress.progress = progress
        tvPercentage.text = "$progress%"
    }

    private fun updateCheckboxes(progress: Int) {
        cbStore.isChecked = progress >= 30
        cbPrivateCode.isChecked = progress >= 70
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("optimizer_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showInProgressNotification(progress: Int, currentApp: String) {
        val builder = NotificationCompat.Builder(this, "optimizer_channel")
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle("Optimizing: $progress%")
            .setContentText(currentApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress, false)
            .setOngoing(true)

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, builder.build())
    }

    private fun showCompletionNotification() {
        val builder = NotificationCompat.Builder(this, "optimizer_channel")
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle(getString(R.string.optimization_complete))
            .setContentText(getString(R.string.device_ready))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(2, builder.build())
    }

    private fun showErrorNotification() {
        val builder = NotificationCompat.Builder(this, "optimizer_channel")
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle("Optimization Failed")
            .setContentText("Tap to retry")
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(3, builder.build())
    }
}