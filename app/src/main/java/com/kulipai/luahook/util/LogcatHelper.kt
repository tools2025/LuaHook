package com.kulipai.luahook.util

import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogcatHelper {

    private const val TAG = "LogcatHelper"

    fun getSystemLogsByTagSince(tag: String, since: String? = null): List<String> {
        val logList = mutableListOf<String>()
        try {
            val command = if (since.isNullOrEmpty()) {
                "logcat -d $tag:* *:S"
            } else {
                "logcat -d -T $since $tag:* *:S"
            }
            // 执行 logcat 命令，使用 root 权限，todo 并筛选从指定时间之后的日志
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                logList.add(line!!)
            }

            reader.close()
            process.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return logList
    }

    fun getCurrentLogcatTimeFormat(): String {
        val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return dateFormat.format(Date())
    }

}