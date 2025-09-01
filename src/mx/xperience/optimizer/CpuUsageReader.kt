// SPDX-License-Identifier: Apache-2.0
// Copyright 2024 XPerience Project

package mx.xperience.optimizer

import java.io.BufferedReader
import java.io.FileReader

class CpuUsageReader {

    private val coreLastTotal = mutableMapOf<Int, Long>()
    private val coreLastIdle = mutableMapOf<Int, Long>()
    private var isFirstRead = true

    data class CpuUsageData(
        val totalUsage: Int,
        val coreUsages: List<Int>,
        val coreFrequencies: List<Int>
    )

    fun readUsage(): CpuUsageData {
        return try {
            // Leer TODO en una sola pasada para consistencia
            val reader = BufferedReader(FileReader("/proc/stat"))
            val lines = reader.readLines()
            reader.close()

            // Leer uso total y de cores
            val totalUsage = readTotalCpuUsage(lines)
            val coreUsages = readCoreUsages(lines)
            
            // Leer frecuencias
            val coreFrequencies = readCoreFrequencies()

            if (isFirstRead) {
                isFirstRead = false
                // Primera lectura: solo establecer valores base, devolver 0
                CpuUsageData(0, coreUsages, coreFrequencies)
            } else {
                // Lecturas posteriores: datos reales
                CpuUsageData(totalUsage, coreUsages, coreFrequencies)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            CpuUsageData(0, emptyList(), emptyList())
        }
    }

    private fun readTotalCpuUsage(lines: List<String>): Int {
        val totalLine = lines.firstOrNull { it.startsWith("cpu ") } ?: return 0
        
        val parts = totalLine.split("\\s+".toRegex())
        if (parts.size < 6) return 0

        val user = parts[1].toLong()
        val nice = parts[2].toLong()
        val system = parts[3].toLong()
        val idle = parts[4].toLong()
        val iowait = parts[5].toLong()

        val total = user + nice + system + idle + iowait
        val totalIdle = idle + iowait

        val lastTotal = coreLastTotal.getOrPut(-1) { total }
        val lastIdle = coreLastIdle.getOrPut(-1) { totalIdle }

        val totalDelta = total - lastTotal
        val idleDelta = totalIdle - lastIdle

        coreLastTotal[-1] = total
        coreLastIdle[-1] = totalIdle

        return if (totalDelta > 0) {
            ((totalDelta - idleDelta) * 100 / totalDelta).toInt()
        } else {
            0
        }
    }

    private fun readCoreUsages(lines: List<String>): List<Int> {
        val usages = mutableListOf<Int>()
        
        val coreLines = lines.filter { it.startsWith("cpu") && !it.startsWith("cpu ") }
        
        coreLines.forEach { line ->
            val parts = line.split("\\s+".toRegex())
            if (parts.size >= 6) {
                val coreId = parts[0].removePrefix("cpu").toInt()
                
                val user = parts[1].toLong()
                val nice = parts[2].toLong()
                val system = parts[3].toLong()
                val idle = parts[4].toLong()
                val iowait = parts[5].toLong()

                val total = user + nice + system + idle + iowait
                val totalIdle = idle + iowait

                val lastTotal = coreLastTotal.getOrPut(coreId) { total }
                val lastIdle = coreLastIdle.getOrPut(coreId) { totalIdle }

                val totalDelta = total - lastTotal
                val idleDelta = totalIdle - lastIdle

                coreLastTotal[coreId] = total
                coreLastIdle[coreId] = totalIdle

                val usage = if (totalDelta > 0) {
                    ((totalDelta - idleDelta) * 100 / totalDelta).toInt()
                } else {
                    0
                }
                
                usages.add(usage)
            }
        }
        
        return usages
    }

    private fun readCoreFrequencies(): List<Int> {
        val frequencies = mutableListOf<Int>()
        var cpuIndex = 0
        
        try {
            while (true) {
                val path = "/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/scaling_cur_freq"
                val file = java.io.File(path)
                if (!file.exists()) break
                
                BufferedReader(FileReader(file)).use { reader ->
                    val freq = reader.readLine().toInt() / 1000 // Convertir a MHz
                    frequencies.add(freq)
                }
                cpuIndex++
            }
        } catch (e: Exception) {
            // Ignorar errores, algunos cores pueden estar offline
        }
        
        return frequencies
    }
}