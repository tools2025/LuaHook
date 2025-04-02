package com.kulipai.luahook.util

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

object RootHelper {


    fun canExecuteSu(): Boolean {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(arrayOf<String>("which", "su"))
            val `in` = BufferedReader(InputStreamReader(process.getInputStream()))
            if (`in`.readLine() != null) return true
            return false
        } catch (t: Throwable) {
            return false
        } finally {
            if (process != null) process.destroy()
        }
    }

    fun canGetRoot(): Boolean {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = process.getOutputStream()
            os.write("id\n".toByteArray())
            os.flush()
            os.close()
            val `is` = process.getInputStream()
            val reader = BufferedReader(InputStreamReader(`is`))
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                if (line!!.lowercase(Locale.getDefault()).contains("uid=0")) {
                    return true
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            return false
        }
        return false
    }
}