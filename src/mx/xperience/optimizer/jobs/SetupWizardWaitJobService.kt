// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 XPerience Project

package mx.xperience.optimizer.jobs

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.provider.Settings
import android.util.Log
import mx.xperience.optimizer.ui.OptimizerActivity

/**
 * Wait for the SetupWizard to finish (DEVICE_PROVISIONED == 1) before
 * displaying the optimization screen, so as not to interrupt the OOBE.
 */
class SetupWizardWaitJobService : JobService() {

    companion object {
        private const val TAG = "SetupWizardWaitJob"
    }

    override fun onStartJob(params: JobParameters): Boolean {
        val provisioned = Settings.Global.getInt(
            contentResolver, Settings.Global.DEVICE_PROVISIONED, 0
        ) == 1

        if (provisioned) {
            Log.d(TAG, "SetupWizard terminado, lanzando optimizador")
            val intent = Intent(this, OptimizerActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            jobFinished(params, false)
        } else {
            // Not yet provisioned (false positive from the trigger); retry
            jobFinished(params, true)
        }
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
