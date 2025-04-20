package com.kulipai.luahook.util

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogcatHelper {

    private const val TAG = "LogcatHelper"

    suspend fun getSystemLogsByTagSince(tag: String, since: String? = null): List<String> {
        val command = if (since.isNullOrEmpty()) {
            "logcat -d $tag:* *:S"
        } else {
            "logcat -d -T $since $tag:* *:S"
        }

        return withContext(Dispatchers.IO) {
            try {
                val result = Shell.cmd(command).exec()
                if (result.isSuccess) {
                    result.out
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    fun getCurrentLogcatTimeFormat(): String {
        val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return dateFormat.format(Date())
    }
}
