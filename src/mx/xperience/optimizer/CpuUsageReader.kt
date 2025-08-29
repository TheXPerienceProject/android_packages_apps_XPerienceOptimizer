// SPDX-License-Identifier: Apache-2.0
// Copyright 2024 XPerience Project

package mx.xperience.optimizer

import java.io.BufferedReader
import java.io.InputStreamReader

class CpuUsageReader {

    private var lastTotal: Long = 0
    private var lastIdle: Long = 0

    fun readUsage(): Int {
        return try {
            // Ejecutamos un top rápido, solo una iteración (-n 1)
            val process = Runtime.getRuntime().exec(arrayOf("top", "-n", "1", "-b"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                line?.let { l ->
                    // Busca la línea que empieza con "CPU" o "%cpu"
                    if (l.contains("CPU") || l.contains("Cpu")) {
                        // Ejemplo de salida: "CPU usage: 12% user, 3% sys, 84% idle"
                        val regex = "(\\d+)%".toRegex()
                        val matches =
                            regex.findAll(l).map { it.value.replace("%", "").toInt() }.toList()

                        if (matches.isNotEmpty()) {
                            val idle = matches.last() // normalmente el último es idle
                            return 100 - idle // uso real
                        }
                    }
                }
            }
            reader.close()
            process.destroy()
            0
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }
}