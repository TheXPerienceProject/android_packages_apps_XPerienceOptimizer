// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 XPerience Project

package mx.xperience.optimizer.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
//import androidx.appcompat.app.AppCompatActivity
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import mx.xperience.optimizer.R
import mx.xperience.optimizer.ui.theme.XPerienceOptimizerTheme
import mx.xperience.optimizer.ui.adapters.Status
import mx.xperience.optimizer.workers.OptimizerWorker

class OptimizerActivity : ComponentActivity() {

    private val appList = mutableStateListOf<AppUiState>()
    private var optimizationProgress by mutableStateOf(0)
    private var currentAppName by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        loadInstalledApps()
        createNotificationChannel()
        startOptimization()

        setContent {
            XPerienceOptimizerTheme {
                AppOptimizationScreen(
                    progress = optimizationProgress,
                    currentAppName = currentAppName,
                    appList = appList,
                    onBackClick = { finish() }
                )
            }
        }
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString() }

        val iconSize = 96

        val apps = packages.map { appInfo ->
            val drawable = appInfo.loadIcon(pm)

            val bitmap = drawable.toBitmap(
                width = iconSize,
                height = iconSize
            )

            AppUiState(
                name = appInfo.loadLabel(pm).toString(),
                icon = BitmapPainter(bitmap.asImageBitmap()),
                status = Status.PENDING,
                packageName = appInfo.packageName
            )
        }

        appList.clear()
        appList.addAll(apps)
    }

    private fun startOptimization() {
        // WorkManager tiene un límite de 10KB, por lo que el Worker consultará
        // los paquetes directamente para evitar excepciones de serialización.
        val workRequest = OneTimeWorkRequest.Builder(OptimizerWorker::class.java)
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)

        WorkManager.getInstance(this)
            .getWorkInfoByIdLiveData(workRequest.id)
            .observe(this) { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> {
                        val currentProgress = workInfo.progress.getInt(OptimizerWorker.PROGRESS_KEY, 0)
                        val currentPackage = workInfo.progress.getString(OptimizerWorker.CURRENT_PACKAGE_KEY)
                        val name = workInfo.progress.getString(OptimizerWorker.CURRENT_APP_NAME_KEY)

                        optimizationProgress = currentProgress
                        currentAppName = name

                        currentPackage?.let {
                            updateAppStatus(it, Status.RUNNING)
                        }

                        showInProgressNotification(currentProgress, name ?: "")
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        optimizationProgress = 100
                        currentAppName = getString(R.string.optimized)
                        showCompletionNotification()
                        Toast.makeText(this, getString(R.string.optimization_completed), Toast.LENGTH_SHORT).show()
                    }

                    WorkInfo.State.FAILED -> {
                        showErrorNotification()
                        Toast.makeText(this, getString(R.string.optimization_failed), Toast.LENGTH_SHORT).show()
                    }

                    else -> {}
                }
            }
    }

    private fun updateAppStatus(packageName: String, status: Status) {
        val index = appList.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            val app = appList[index]
            // Marcar las anteriores como DONE
            for (i in 0 until index) {
                if (appList[i].status != Status.DONE) {
                    appList.set(i, appList[i].copy(status = Status.DONE))
                }
            }
            appList.set(index, app.copy(status = status))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("optimizer_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showInProgressNotification(progress: Int, currentApp: String) {
        val builder = NotificationCompat.Builder(this, "optimizer_channel")
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle("Optimizing: $progress%")
            .setContentText(currentApp)
            .setProgress(100, progress, false)
            .setOngoing(true)

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, builder.build())
    }

    private fun showCompletionNotification() {
        val builder = NotificationCompat.Builder(this, "optimizer_channel")
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle(getString(R.string.optimization_complete))
            .setContentText(getString(R.string.device_ready))
            .setOngoing(false)

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, builder.build())
    }

    private fun showErrorNotification() {
        val builder = NotificationCompat.Builder(this, "optimizer_channel")
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle(getString(R.string.optimization_failed))
            .setContentText(getString(R.string.retry_optimization))
            .setOngoing(false)

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, builder.build())
    }
}
