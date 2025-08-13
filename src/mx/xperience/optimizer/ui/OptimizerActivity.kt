package mx.xperience.optimizer.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Data
import androidx.work.WorkInfo
import mx.xperience.optimizer.R
import mx.xperience.optimizer.workers.OptimizerWorker

class OptimizerActivity : AppCompatActivity() {

    private lateinit var tvProgress: TextView
    private lateinit var tvCurrentApp: TextView

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startOptimization()
            } else {
                Toast.makeText(this, "Permiso de notificaciones denegado", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_optimizer)

        tvProgress = findViewById(R.id.tv_progress)
        tvCurrentApp = findViewById(R.id.tv_current_app)

        checkNotificationPermissionAndOptimize()
    }

    private fun checkNotificationPermissionAndOptimize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                startOptimization()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startOptimization()
        }
    }

    private fun startOptimization() {
        createNotificationChannel()

        val optimizationWorkRequest = OneTimeWorkRequestBuilder<OptimizerWorker>().build()
        val workManager = WorkManager.getInstance(this)
        workManager.enqueue(optimizationWorkRequest)

        // Observa el estado del trabajo y actualiza la UI
        workManager.getWorkInfoByIdLiveData(optimizationWorkRequest.id)
            .observe(this) { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            val progress = workInfo.progress.getInt(OptimizerWorker.PROGRESS_KEY, 0)
                            tvProgress.text = "$progress%"
                            // Lógica para actualizar tvCurrentApp si necesitas, aunque requiere más lógica en el worker
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            showCompletionNotification()
                            finish()
                        }
                        WorkInfo.State.FAILED -> {
                            Toast.makeText(this, "Optimización fallida", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        else -> {
                            // Ignorar otros estados
                        }
                    }
                }
            }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android 8.0+
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("optimizer_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showCompletionNotification() {
        val builder = NotificationCompat.Builder(this, "optimizer_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.optimization_complete))
            .setContentText(getString(R.string.device_ready))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, builder.build())
    }
}