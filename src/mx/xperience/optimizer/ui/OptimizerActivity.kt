// SPDX-License-Identifier: Apache-2.0
// Copyright 2025 XPerience Project

package mx.xperience.optimizer.ui

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import mx.xperience.optimizer.CpuUsageReader
import mx.xperience.optimizer.R
import mx.xperience.optimizer.ui.adapters.AppStatusDynamic
import mx.xperience.optimizer.ui.adapters.Status
import mx.xperience.optimizer.workers.OptimizerWorker

import java.io.File

class OptimizerActivity : AppCompatActivity() {

    private lateinit var tvCPU: TextView
    private lateinit var lpiCPU: LinearProgressIndicator
    private lateinit var tvRAM: TextView
    private lateinit var lpiRAM: LinearProgressIndicator
    private lateinit var tvTemp: TextView
    private lateinit var lpiTemp: LinearProgressIndicator
    private lateinit var tvPercentage: TextView
    private lateinit var tvCurrentApp: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var appList: MutableList<AppStatusDynamic>

    private lateinit var fabExit: FloatingActionButton

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 1000L // 1 sec
    private val cpuReader = CpuUsageReader()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_optimizer)

        progressBar = findViewById(R.id.circular_progress)
        tvPercentage = findViewById(R.id.tv_percentage)
        tvCurrentApp = findViewById(R.id.tv_current_app)


        tvCPU = findViewById(R.id.tvCPU)
        lpiCPU = findViewById(R.id.lpiCPU)
        tvRAM = findViewById(R.id.tvRAM)
        lpiRAM = findViewById(R.id.lpiRAM)
        tvTemp = findViewById(R.id.tvTemp)
        lpiTemp = findViewById(R.id.lpiTemp)

        // Load apps
        appList = loadInstalledApps()
        startUpdatingStats()

        createNotificationChannel()
        startOptimization()

        fabExit = findViewById(R.id.fabExit)
        fabExit.setOnClickListener { finish() }


        observeWorker()
    }

    private fun startUpdatingStats() {
        handler.post(object : Runnable {
            override fun run() {
                updateCPUUsage()
                updateRAMUsage()
                updateTemp()
                handler.postDelayed(this, updateInterval)
            }
        })
    }

    private fun updateCPUUsage() {
        val usage = cpuReader.readUsage()
        tvCPU.text = "CPU: $usage%"
        lpiCPU.progress = usage
        lpiCPU.setIndicatorColor(
            when {
                usage < 50 -> Color.GREEN
                usage < 75 -> Color.YELLOW
                else -> Color.RED
            }
        )
    }

    private fun updateRAMUsage() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val usedMB = ((memInfo.totalMem - memInfo.availMem) / 1024 / 1024).toInt()
        val totalMB = (memInfo.totalMem / 1024 / 1024).toInt()
        val percent = (usedMB * 100 / totalMB)
        tvRAM.text = "RAM: $usedMB/$totalMB MB"
        lpiRAM.progress = percent
        lpiRAM.setIndicatorColor(
            when {
                percent < 50 -> Color.GREEN
                percent < 75 -> Color.YELLOW
                else -> Color.RED
            }
        )
    }


    private fun updateTemp() {
        val batteryIntent = registerReceiver(
            null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        )
        val temp = (batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

        tvTemp.text = "Temp: ${temp}°C"
        tvTemp.setTextColor(
            when {
                temp < 40 -> 0xFF00FF00.toInt() // verde
                temp < 60 -> 0xFFFFFF00.toInt() // amarillo
                else -> 0xFFFF0000.toInt()    // rojo
            }
        )
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
        val packageNames =
            appList.map { it.packageName }.toTypedArray().toList().toTypedArray() as Array<String?>

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
                        val currentPackage =
                            workInfo.progress.getString(OptimizerWorker.CURRENT_PACKAGE_KEY)
                        val currentApp =
                            workInfo.progress.getString(OptimizerWorker.CURRENT_APP_NAME_KEY)

                        tvPercentage.text = "$progress%"
                        progressBar.progress = progress

                        // upgrade text and icon
                        tvCurrentApp.text = "Optimizing ${currentApp ?: ""}"
                        val appIcon: Drawable? = try {
                            if (currentPackage != null) packageManager.getApplicationIcon(
                                currentPackage
                            )
                            else null
                        } catch (e: PackageManager.NameNotFoundException) {
                            getDrawable(R.drawable.ic_android)
                        }
                        findViewById<ImageView>(R.id.ivCurrentAppIcon).setImageDrawable(appIcon)


                        showInProgressNotification(progress, currentApp ?: "")
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        tvPercentage.text = "100%"
                        progressBar.progress = 100
                        tvCurrentApp.text = "Optimized!!!"
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
