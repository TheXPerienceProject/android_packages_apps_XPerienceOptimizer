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
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
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

    private lateinit var fabExit: FloatingActionButton

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

        fabExit = findViewById(R.id.fabExit)
        fabExit.setOnClickListener { finish() }

        observeWorker()
    }

    private fun observeWorker() {
        val optimizerWorkRequest = OneTimeWorkRequestBuilder<OptimizerWorker>()
            .build()
        val workId = optimizerWorkRequest.id
        val workManager = WorkManager.getInstance(applicationContext)

            workManager.getWorkInfoByIdLiveData(workId)
                .observe(this) { workInfo ->
                    if (workInfo != null) {
                        val progress = workInfo.progress.getInt(OptimizerWorker.PROGRESS_KEY, 0)
                        // actualizar ProgressBar si tienes
                        if (progress == 100 && workInfo.state.isFinished) {
                            // mostrar FAB
                            fabExit.show()
                            Toast.makeText(this, "Optimización completada!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
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
                        val currentPackage = workInfo.progress.getString(OptimizerWorker.CURRENT_PACKAGE_KEY)
                        val currentApp = workInfo.progress.getString(OptimizerWorker.CURRENT_APP_NAME_KEY)

                        tvPercentage.text = "$progress%"
                        progressBar.progress = progress

                        // upgrade text and icon
                        tvCurrentApp.text = "Optimizing ${currentApp ?: ""}"
                        val appIcon: Drawable? = try {
                            if (currentPackage != null) packageManager.getApplicationIcon(currentPackage)
                            else null
                        } catch (e: PackageManager.NameNotFoundException) {
                            getDrawable(R.drawable.ic_android)
                        }
                        findViewById<ImageView>(R.id.ivCurrentAppIcon).setImageDrawable(appIcon)

                        updateAppStatus(currentApp, progress)
                        showInProgressNotification(progress, currentApp ?: "")
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        tvPercentage.text = "100%"
                        progressBar.progress = 100
                        tvCurrentApp.text = "Optimized!!!"
                        markAllDone()
                        showCompletionNotification()
                        fabExit.show()
                        Toast.makeText(this, "Optimización completada!", Toast.LENGTH_SHORT).show()
                    }
                    WorkInfo.State.FAILED -> {
                        showErrorNotification()
                        tvCurrentApp.text = "Optimization failed"
                        fabExit.show()
                        Toast.makeText(this, "Optimización failed!", Toast.LENGTH_SHORT).show()
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
