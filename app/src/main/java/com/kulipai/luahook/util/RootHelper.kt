package com.kulipai.luahook.util

import com.topjohnwu.superuser.Shell

object RootHelper {


    fun isRoot(): Boolean {
        return Shell.isAppGrantedRoot() == true
    }

    fun canGetRoot(): Boolean {
        Shell.getShell()
        return Shell.isAppGrantedRoot() == true
    }

    fun shell(cmd: String): Pair<String, Boolean> {
        lateinit var result: String
        var isok: Boolean = false
        Shell.getShell { shell ->
            var sh = Shell.cmd(cmd).exec()
            if (sh.isSuccess) {
                result = sh.out.toString()
                isok = true
            } else {
                result = sh.err.toString()
                isok = false
            }
        }
        return Pair(result,isok)
    }


}