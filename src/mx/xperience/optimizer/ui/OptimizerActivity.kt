// SPDX-License-Identifier: Apache-2.0
// Copyright 2025 XPerience Project

package mx.xperience.optimizer.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.progressindicator.CircularProgressIndicator
import mx.xperience.optimizer.R
import mx.xperience.optimizer.ui.adapters.AppStatusAdapterDynamic
import mx.xperience.optimizer.ui.adapters.AppStatusDynamic
import mx.xperience.optimizer.ui.adapters.Status
import mx.xperience.optimizer.workers.OptimizerWorker

class OptimizerActivity : AppCompatActivity() {

    private lateinit var circularProgress: CircularProgressIndicator
    private lateinit var tvPercentage: TextView
    private lateinit var tvCurrentApp: TextView
    private lateinit var rvAppList: RecyclerView

    private lateinit var appList: MutableList<AppStatusDynamic>
    private lateinit var adapter: AppStatusAdapterDynamic

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_optimizer)

        circularProgress = findViewById(R.id.circular_progress)
        tvPercentage = findViewById(R.id.tv_percentage)
        tvCurrentApp = findViewById(R.id.tv_current_app)
        rvAppList = findViewById(R.id.rv_app_list)

        appList = loadInstalledApps()
        adapter = AppStatusAdapterDynamic(appList)
        rvAppList.layoutManager = LinearLayoutManager(this)
        rvAppList.adapter = adapter

        createNotificationChannel()
        startOptimization()
    }

    private fun loadInstalledApps(): MutableList<AppStatusDynamic> {
        val pm = packageManager
        val apps = mutableListOf<AppStatusDynamic>()

        val packages = pm.getInstalledApplications(0)
        for (appInfo in packages) {
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                val name = pm.getApplicationLabel(appInfo).toString()
                val icon: Drawable = pm.getApplicationIcon(appInfo)
                apps.add(AppStatusDynamic(name, icon, Status.PENDING, appInfo.packageName))
            }
        }
        return apps
    }

    private fun startOptimization() {
        // Pasar lista de packageNames al Worker
        val packageNames = appList.map { it.packageName }.toTypedArray().toList().toTypedArray() as Array<String?>
        val inputData = Data.Builder()
            .putStringArray(OptimizerWorker.PACKAGE_LIST_KEY, packageNames)
            .build()

        val workRequest = OneTimeWorkRequest.Builder(OptimizerWorker::class.java)
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)

        WorkManager.getInstance(this)
            .getWorkInfoByIdLiveData(workRequest.id)
            .observe(this) { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getInt(OptimizerWorker.PROGRESS_KEY, 0)
                        val currentApp = workInfo.progress.getString(OptimizerWorker.CURRENT_APP_NAME_KEY)

                        tvPercentage.text = "$progress%"
                        circularProgress.progress = progress
                        tvCurrentApp.text = "Optimizing ${currentApp ?: ""}"

                        updateAppStatus(currentApp, progress)
                        showInProgressNotification(progress, currentApp ?: "")
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        tvPercentage.text = "100%"
                        circularProgress.progress = 100
                        markAllDone()
                        showCompletionNotification()
                    }
                    WorkInfo.State.FAILED -> {
                        showErrorNotification()
                    }
                    else -> {}
                }
            }
    }

    private fun updateAppStatus(currentApp: String?, progress: Int) {
        appList.forEachIndexed { index, app ->
            when {
                app.name == currentApp -> app.status = Status.RUNNING
                progress >= ((index + 1).toFloat() / appList.size * 100) -> app.status = Status.DONE
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun markAllDone() {
        appList.forEach { it.status = Status.DONE }
        adapter.notifyDataSetChanged()
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

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(2, builder.build())
    }

    private fun showErrorNotification() {
        val builder = NotificationCompat.Builder(this, "optimizer_channel")
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle("Optimization Failed")
            .setContentText("Tap to retry")

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(3, builder.build())
    }
}