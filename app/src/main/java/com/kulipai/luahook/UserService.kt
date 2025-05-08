package com.kulipai.luahook

import android.os.RemoteException
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlin.system.exitProcess

class UserService : IUserService.Stub() {
    @Throws(RemoteException::class)
    override fun destroy() {
        exitProcess(0)
    }

    @Throws(RemoteException::class)
    override fun exit() {
        destroy()
    }

    @Throws(RemoteException::class)
    override fun exec(command: String?): String {
        val stringBuilder = StringBuilder()
        try {
            // 执行shell命令
            val process = Runtime.getRuntime().exec(command)
            // 读取执行结果
            val inputStreamReader = InputStreamReader(process.getInputStream())
            val bufferedReader = BufferedReader(inputStreamReader)
            var line: String?
            while ((bufferedReader.readLine().also { line = it }) != null) {
                stringBuilder.append(line).append("\n")
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        return stringBuilder.toString()
    }
}