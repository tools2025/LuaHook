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
}