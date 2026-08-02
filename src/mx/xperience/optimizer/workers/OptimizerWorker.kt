// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 XPerience Project

package mx.xperience.optimizer.workers

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.ServiceManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mx.xperience.optimizer.IOptimizerService

class OptimizerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val PROGRESS_KEY = "progress"
        const val CURRENT_APP_NAME_KEY = "current_app_name"
        const val CURRENT_PACKAGE_KEY = "current_package"
        const val PACKAGE_LIST_KEY = "package_list"
        private const val TAG = "OptimizerWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting optimisation process")

        try {
            val pm = applicationContext.packageManager
            
            // Obtener la lista de paquetes desde la actividad para asegurar sincronización
            val packagesQuery = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != applicationContext.packageName }
                .sortedBy { it.loadLabel(pm).toString() }

            val packageList = packagesQuery.map { it.packageName }.toTypedArray()

            if (packageList.isEmpty()) {
                Log.e(TAG, "No se encontraron aplicaciones para optimizar")
                return@withContext Result.failure()
            }

            Log.d(TAG, "Optimizando ${packageList.size} aplicaciones recibidas")

            for ((index, packageName) in packageList.withIndex()) {
                Log.d(TAG, "Procesando aplicación #${index + 1}: $packageName")

                val appName = try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: NameNotFoundException) {
                    packageName
                }

                val progress = ((index + 1) * 100 / packageList.size)
                
                // Reportar estado a la UI
                setProgress(
                    workDataOf(
                        PROGRESS_KEY to progress,
                        CURRENT_APP_NAME_KEY to appName,
                        CURRENT_PACKAGE_KEY to packageName
                    )
                )

                // Ejecutar optimización
                optimizePackage(pm, packageName)

                // Pequeña pausa para que la UI pueda mostrar el cambio
                delay(300)
            }

            Log.i(TAG, "Optimización completada exitosamente")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Error fatal en el proceso de optimización", e)
            Result.failure()
        }
    }

    private suspend fun optimizePackage(pm: PackageManager, packageName: String) {
        try {
            val binder = ServiceManager.getService("optimizer") ?: run {
                Log.e(TAG, "Servicio 'optimizer' no disponible")
                return
            }
            val service = IOptimizerService.Stub.asInterface(binder)
            val ok = service.compilePackage(packageName, "speed-profile")
            if (!ok) Log.w(TAG, "compilePackage devolvió false para $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizando $packageName", e)
        }
    }
}
