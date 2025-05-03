package com.kulipai.luahook.util

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.internal.Utils
import java.util.Locale

object RootHelper {

    fun isRoot(): Boolean {
        return Shell.isAppGrantedRoot() == true
    }

    fun canGetRoot(): Boolean {
        Shell.getShell()
        return Shell.isAppGrantedRoot() == true
    }
}