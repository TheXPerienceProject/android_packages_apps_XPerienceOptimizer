// SPDX-License-Identifier: Apache-2.0
// Copyright 2024 XPerience Project

package mx.xperience.optimizer

import java.io.File

class CpuUsageReader {
    private var lastIdle: Long = 0
    private var lastTotal: Long = 0

    fun readUsage(): Int {
        val cpuLine = File("/proc/stat").readLines()
            .firstOrNull { it.startsWith("cpu ") }
            ?: return 0

        val parts = cpuLine.split("\\s+".toRegex()).drop(1).map { it.toLong() }

        val idle = parts[3] // idle time
        val total = parts.sum()

        val diffIdle = idle - lastIdle
        val diffTotal = total - lastTotal

        lastIdle = idle
        lastTotal = total

        return if (diffTotal > 0) {
            ((diffTotal - diffIdle) * 100 / diffTotal).toInt()
        } else {
            0
        }
    }
}