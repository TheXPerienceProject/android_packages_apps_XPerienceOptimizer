// SPDX-License-Identifier: Apache-2.0
// Copyright 2025 XPerience Project

package mx.xperience.optimizer.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import mx.xperience.optimizer.R
import mx.xperience.optimizer.workers.OptimizerWorker

class OptimizerActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var tvCurrentApp: TextView
    private lateinit var btnStart: Button
    private lateinit var btnCancel: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_optimizer)

        // Inicializar vistas
        progressBar = findViewById(R.id.progress_bar)
        tvProgress = findViewById(R.id.tv_progress)
        tvCurrentApp = findViewById(R.id.tv_current_app)
        btnStart = findViewById(R.id.btn_start)
        btnCancel = findViewById(R.id.btn_cancel)

        btnStart.setOnClickListener {
            startOptimization()
        }

        btnCancel.setOnClickListener {
            cancelOptimization()
            finish()
        }

        createNotificationChannel()
    }

    private fun startOptimization() {
        btnStart.isEnabled = false
        btnCancel.isEnabled = true
        progressBar.progress = 0
        tvProgress.text = "0%"
        tvCurrentApp.text = getString(R.string.preparing_optimization)

        val workRequest = OneTimeWorkRequest.Builder(OptimizerWorker::class.java).build()
        WorkManager.getInstance(this).enqueue(workRequest)

        WorkManager.getInstance(this)
            .getWorkInfoByIdLiveData(workRequest.id)
            .observe(this) { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getInt(OptimizerWorker.PROGRESS_KEY, 0)
                        val appName = workInfo.progress.getString(OptimizerWorker.CURRENT_APP_NAME_KEY)
                        
                        progressBar.progress = progress
                        tvProgress.text = "$progress%"
                        tvCurrentApp.text = getString(R.string.optimizing_app, appName ?: "")
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        showCompletionNotification()
                        finish()
                    }
                    WorkInfo.State.FAILED -> {
                        tvCurrentApp.text = getString(R.string.optimization_failed)
                        btnStart.isEnabled = true
                    }
                    else -> {}
                }
            }
    }

    private fun cancelOptimization() {
        WorkManager.getInstance(this).cancelAllWork()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "optimizer_channel",
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.channel_description)
            }

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun showCompletionNotification() {
        NotificationCompat.Builder(this, "optimizer_channel")
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle(getString(R.string.optimization_complete))
            .setContentText(getString(R.string.device_ready))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
            .let { notification ->
                getSystemService(NotificationManager::class.java)
                    .notify(1, notification)
            }
    }
}