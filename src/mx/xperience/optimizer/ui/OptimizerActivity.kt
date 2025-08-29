// SPDX-License-Identifier: Apache-2.0
// Copyright 2025 XPerience Project

package mx.xperience.optimizer.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import mx.xperience.optimizer.R
import mx.xperience.optimizer.ui.adapters.AppStatusAdapterDynamic
import mx.xperience.optimizer.ui.adapters.AppStatusDynamic
import mx.xperience.optimizer.ui.adapters.Status
import mx.xperience.optimizer.workers.OptimizerWorker

class OptimizerActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var tvPercentage: TextView
    private lateinit var tvCurrentApp: TextView
    private lateinit var rvAppList: RecyclerView

    private lateinit var appList: MutableList<AppStatusDynamic>
    private val visibleList: MutableList<AppStatusDynamic> = mutableListOf()
    private lateinit var adapter: AppStatusAdapterDynamic

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_optimizer)

        progressBar = findViewById(R.id.circular_progress)
        tvPercentage = findViewById(R.id.tv_percentage)
        tvCurrentApp = findViewById(R.id.tv_current_app)
        rvAppList = findViewById(R.id.rv_app_list)

        // Load apps
        appList = loadInstalledApps()
        prepareVisibleList()

        adapter = AppStatusAdapterDynamic(visibleList)
        rvAppList.layoutManager = LinearLayoutManager(this)
        rvAppList.adapter = adapter

        createNotificationChannel()
        startOptimization()
    }

    private fun prepareVisibleList() {
        //initialize visibleList with max 8 apps
        visibleList.clear()
        val toAdd = appList.take(8)
        visibleList.addAll(toAdd)
    }

    private fun loadInstalledApps(): MutableList<AppStatusDynamic> {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { it.loadLabel(pm).toString() } // O el criterio que quieras para optimizar primero

        val apps = packages.map { appInfo ->
            AppStatusDynamic(
                    name = appInfo.loadLabel(pm).toString(),
                    icon = appInfo.loadIcon(pm),
                    status = Status.PENDING,
                    packageName = appInfo.packageName
                        )
        }.toMutableList()

        return apps
    }

    private fun startOptimization() {
        // Preparar array de packageNames
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
                        progressBar.progress = progress
                        tvCurrentApp.text = "Optimizing ${currentApp ?: ""}"

                        updateAppStatus(currentApp, progress)
                        showInProgressNotification(progress, currentApp ?: "")
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        tvPercentage.text = "100%"
                        progressBar.progress = 100
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
        //  We search for the app in the actual queue.
        val appIndex = appList.indexOfFirst { it.name == currentApp }
        if (appIndex != -1) {
            val app = appList[appIndex]
            app.status = Status.DONE

            // We update visibleList if it is visible.
            val visibleIndex = visibleList.indexOf(app)
            if (visibleIndex != -1) {
                visibleList.removeAt(visibleIndex)
                adapter.notifyItemRemoved(visibleIndex)
            }

            //We add the next app from the queue if there is space.
            val nextIndex = visibleList.size
            if (nextIndex < 8 && appIndex + 1 < appList.size) {
                val nextApp = appList[appIndex + 1]
                if (!visibleList.contains(nextApp)) {
                    visibleList.add(nextApp)
                    adapter.notifyItemInserted(visibleList.size - 1)
                }
            }
        }
    }

    private fun markAllDone() {
        visibleList.forEachIndexed { index, app ->
            app.status = Status.DONE
            adapter.notifyItemChanged(index)
        }
        appList.forEach { it.status = Status.DONE }
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
            .setContentTitle("Optimization Failed")
            .setContentText("Tap to retry")
            .setOngoing(false)

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, builder.build())
    }
}
