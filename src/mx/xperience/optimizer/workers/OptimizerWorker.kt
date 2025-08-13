// SPDX-License-Identifier: Apache-2.0
// Copyright 2025 XPerience Project

package mx.xperience.optimizer.workers

import android.content.Context
import android.content.pm.PackageManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Exception

class OptimizerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val PROGRESS_KEY = "progress"
        const val COMPILE_REASON_BACKGROUND = 4
        private val CRITICAL_APPS = arrayOf(
            "com.android.systemui",
            "com.google.android.gms"
        )
    }

    private fun compilePackage(pm: PackageManager, packageName: String): Boolean {
        return try {
            val method = pm::class.java.getMethod(
                "compilePackage", 
                String::class.java, 
                Int::class.javaPrimitiveType
            )
            method.invoke(pm, packageName, COMPILE_REASON_BACKGROUND)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun doWork(): Result {
        val pm = applicationContext.packageManager
        return try {
            for ((index, packageName) in CRITICAL_APPS.withIndex()) {
                compilePackage(pm, packageName)
                val progress = ((index + 1).toFloat() / CRITICAL_APPS.size * 100).toInt()
                setProgress(workDataOf(PROGRESS_KEY to progress))
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}