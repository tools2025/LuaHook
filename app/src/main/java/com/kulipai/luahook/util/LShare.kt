package com.kulipai.luahook.util

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter



object LShare {

    private const val DIR: String = "/data/local/tmp/LuaHook"


    var isok: Boolean = false

    fun read(file: String): String {
        var (context,isok) = ShellManager.shell("cat '$DIR$file'")
        if (isok) {
            return context
        }
        return ""
    }

    fun write(file: String, content: String): Boolean {
        val path = "$DIR/$file"

        // 将内容编码为 Base64，然后解码写入文件
        val base64Content = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP) // NO_WRAP 避免换行

        val script = """
        rm -f "$path"
        touch "$path"
        echo "$base64Content" | base64 -d > "$path"
    """.trimIndent()

        val (output, success) = ShellManager.shell(script)
        return success
    }

//    fun write(file: String, content: String): Boolean {
//        val path = "$DIR/$file"
//        val sb = StringBuilder()
//
//        content.lines().forEach { line ->
//            val escaped = line.replace("\"", "\\\"")
//            // 使用 printf "%s\n" 添加换行符
//            sb.append("printf \"%s\\n\" \"$escaped\" >> \"$path\"\n")
//        }
//
//        val script = """
//        rm -f "$path"
//        touch "$path"
//        ${sb.toString()}
//    """.trimIndent()
//
//        val (output, success) = ShellManager.shell(script)
//        return success
//    }


//    fun write(file: String, content: String): Boolean {
//        var path = DIR+file
//        val sb = StringBuilder()
//        content.lines().forEach { line ->
//            val escaped = line.replace("\"", "\\\"")
//            sb.append("echo \"$escaped\" >> \"$path\"\n")
//        }
//        val script = """
//        rm -f "$path"
//        touch "$path"
//        ${sb.toString()}
//    """.trimIndent()
//
//        val (output, success) = ShellManager.shell(script)
//        return success
//    }




    fun writeTmp(packageName: String, content: String): Boolean {
        var tmpPath = "/tmp"

        if (isok && ensureDirectoryExists(DIR+tmpPath)) {
            return write("$tmpPath/$packageName.lua", content)
        }
        return false
    }

    fun init(context: Context) {
        ensureDirectoryExists(DIR)
        isok = true
    }


//    private fun md5(str: String): String {
//
//        val bytes = MessageDigest.getInstance("MD5").digest(str.toByteArray())
//        return bytes.joinToString("") { "%02x".format(it) }
//    }

    fun DirectoryExists(path: String): Boolean {
        // 使用 ls 判断目录是否存在
        val (checkOutput, checkSuccess) = ShellManager.shell("ls $path")
        if (checkSuccess) {
            // 目录存在
            return true
        }
        return false
    }

    fun ensureDirectoryExists(path: String): Boolean {

        if(DirectoryExists(path)){
            return true
        }

        // 不存在则尝试创建
        val (mkdirOutput, mkdirSuccess) = ShellManager.shell("mkdir -p $path")
        return mkdirSuccess
    }


}