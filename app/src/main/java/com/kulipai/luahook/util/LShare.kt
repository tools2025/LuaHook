package com.kulipai.luahook.util

import android.content.Context
import android.util.Base64
import org.json.JSONObject


object LShare {

    const val DIR: String = "/data/local/tmp/LuaHook"
    const val Project : String = "/Project"
    const val AppConf : String = "/AppConf"
    const val AppScript : String = "/AppScript"
    const val Plugin : String = "/Plugin"



    var isok: Boolean = false

    fun read(file: String): String {
        var (context, isok) = ShellManager.shell("cat '$DIR$file'")
        if (isok) {
            return context
        }
        return ""
    }

    fun write(file: String, content: String): Boolean {
        val path = "$DIR/$file"

        // 将内容编码为 Base64，然后解码写入文件
        val base64Content = Base64.encodeToString(
            content.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        ) // NO_WRAP 避免换行

        val script = """
        rm -f "$path"
        touch "$path"
        echo "$base64Content" | base64 -d > "$path"
    """.trimIndent()

        val (output, success) = ShellManager.shell(script)
        return success
    }



    /**
     * 保存任意 Kotlin Map 到文件，作为 JSON 对象。
     *
     * @param file 文件名
     * @param data 要保存的 Map (键为 String，值为 Any)
     * @return 是否保存成功
     */
    fun writeMap(file: String, data: MutableMap<String, Any?>): Boolean {
        val jsonObject = JSONObject(data) // JSONObject 可以直接从 Map<String, Any> 构建
        val jsonString = jsonObject.toString()
        return write(file, jsonString)
    }

    /**
     * 从文件读取 JSON 字符串并解析为 Map<String, Any?>。
     *
     * @param file 文件名
     * @return 读取到的 Map，如果失败则返回 null
     */
    fun readMap(file: String): MutableMap<String, Any?> {
        val jsonString = read(file)
        if (jsonString.isEmpty()) {
            return mutableMapOf()
        }
        return try {
            val jsonObject = JSONObject(jsonString)
            val map = mutableMapOf<String, Any?>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = jsonObject.get(key)
            }
            map
        } catch (e: Exception) {
            ("Error decoding arbitrary object from $file: ${e.message}").d()
            mutableMapOf()
        }
    }

    fun writeStringList(path: String, list: List<String>) {
        write(path, list.joinToString(","))
    }


    fun readStringList(path: String): MutableList<String> {
        val serialized = read(path)
        return if (serialized != "") {
            serialized.split(",").toMutableList()
        } else {
            mutableListOf()
        }
    }

    fun writeTmp(packageName: String, content: String): Boolean {
        var tmpPath = "/tmp"

        if (isok && ensureDirectoryExists(DIR + tmpPath)) {
            return write("$tmpPath/$packageName.lua", content)
        }
        return false
    }

    fun init(context: Context) {
        ensureDirectoryExists(DIR)
        ensureDirectoryExists(DIR+Project)
        ensureDirectoryExists(DIR+AppConf)
        ensureDirectoryExists(DIR+AppScript)
        ensureDirectoryExists(DIR+Plugin)
        isok = true
    }


//    private fun md5(str: String): String {
//
//        val bytes = MessageDigest.getInstance("MD5").digest(str.toByteArray())
//        return bytes.joinToString("") { "%02x".format(it) }
//    }

    fun DirectoryExists(path: String): Boolean {
        // 使用 ls 判断目录是否存在
        val (checkOutput, checkSuccess) = ShellManager.shell("ls \"$path\"")
        if (checkSuccess) {
            // 目录存在
            return true
        }
        return false
    }

    /**
     * 完整路径
     * @param path 路径
     * @return 如果创建成功就True否则false
     */
    fun ensureDirectoryExists(path: String): Boolean {

        if (DirectoryExists(path)) {
            return true
        }


        // 不存在则尝试创建
        val (mkdirOutput, mkdirSuccess) = ShellManager.shell("mkdir -p \"$path\"")
        return mkdirSuccess
    }


}