package com.kulipai.luahook.util

import android.content.Context
import androidx.core.content.edit
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object LShare {
    private const val DIR: String = "/data/local/tmp/"


    fun isReady(): Boolean {
        return false
    }

    fun path(): String {

        return ""
    }

    fun init(context: Context) {
        //生成随机哈希路径
        val currentDateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        val thisTime = currentDateTime.format(formatter)

        val path = DIR + "LuaHook_" + md5(thisTime)


        Shell.getShell { shell ->
            if (shell.isRoot) {
                if (Shell.cmd("mkdir $path").exec().isSuccess) {
                    context.getSharedPreferences("config", Context.MODE_PRIVATE).edit {
                        putString("path", path)
                        apply()
                    }
                } else {
                    // 命令执行失败
                }
            } else {
                //no root
            }
        }


    }


    private fun md5(str: String): String {

        val bytes = MessageDigest.getInstance("MD5").digest(str.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }


}