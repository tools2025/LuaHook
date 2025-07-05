package com.kulipai.luahook

import HookLib
import LuaDrawableLoader
import LuaHttp
import LuaImport
import LuaJson
import LuaResourceBridge
import LuaSharedPreferences
import Luafile
import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import com.kulipai.luahook.LuaLib.LuaActivity
import com.kulipai.luahook.util.LShare
import com.kulipai.luahook.util.d
import com.kulipai.luahook.util.e
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import org.json.JSONArray
import org.json.JSONObject
import org.luaj.Globals
import org.luaj.LuaValue
import org.luaj.android.loadlayout
import org.luaj.lib.jse.CoerceJavaToLua
import org.luaj.lib.jse.JsePlatform
import org.luckypray.dexkit.DexKitBridge
import top.sacz.xphelper.XpHelper
import top.sacz.xphelper.dexkit.DexFinder
import java.io.File


lateinit var  LPParam_processName: String

interface LPParam {
    val packageName: String
    val classLoader: ClassLoader
    val appInfo: ApplicationInfo
    val isFirstApplication: Boolean
    val processName: String

}


class LoadPackageParamWrapper(val origin: LoadPackageParam) : LPParam {
    override val packageName get() = origin.packageName
    override val classLoader get() = origin.classLoader
    override val appInfo: ApplicationInfo get() = origin.appInfo
    override val processName: String get() = origin.processName
    override val isFirstApplication: Boolean get() = origin.isFirstApplication
}

class ModuleInterfaceParamWrapper(val origin: XposedModuleInterface.PackageLoadedParam) : LPParam {
    override val packageName get() = origin.packageName
    override val classLoader get() = origin.classLoader
    override val appInfo: ApplicationInfo get() = origin.applicationInfo
    override val processName: String get() = LPParam_processName
    override val isFirstApplication: Boolean get() = origin.isFirstPackage

}


class MainHook(base: XposedInterface, param: XposedModuleInterface.ModuleLoadedParam) : IXposedHookZygoteInit, IXposedHookLoadPackage, XposedModule(base, param) {
    companion object {
//        init {
//            System.loadLibrary("dexkit")
//        }

        const val MODULE_PACKAGE = "com.kulipai.luahook"  // 模块包名
        val PATH = "/data/local/tmp/LuaHook"
    }


    lateinit var luaScript: String
    lateinit var appsScript: String
    lateinit var SelectAppsString: String

    lateinit var SelectAppsList: MutableList<String>
    lateinit var suparam: IXposedHookZygoteInit.StartupParam



    //api 100
    init {
        LPParam_processName=param.processName
    }



    @SuppressLint("DiscouragedPrivateApi")
    override fun onPackageLoaded(lpparam: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(lpparam)
        suparam = createStartupParam(this.applicationInfo.sourceDir)
        XpHelper.initZygote(suparam)

        LuaHook_init(ModuleInterfaceParamWrapper(lpparam))

    }




    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {

        XpHelper.initZygote(startupParam)
        suparam = startupParam

    }



    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        LuaHook_init(LoadPackageParamWrapper(lpparam))
    }


    fun LuaHook_init(lpparam: LPParam) {

        SelectAppsString = read(PATH + "/apps.txt").replace("\n", "")

        luaScript = read(PATH + "/global.lua")

        if (SelectAppsString.isNotEmpty() && SelectAppsString != "") {
            SelectAppsList = SelectAppsString.split(",").toMutableList()
        } else {
            SelectAppsList = mutableListOf()
        }


        //全局脚本
        try {
            //排除自己
            if (lpparam.packageName != MODULE_PACKAGE) {
                val chunk: LuaValue = CreateGlobals(lpparam, "[GLOBAL]").load(luaScript)
                chunk.call()
            }
        } catch (e: Exception) {
            val err = simplifyLuaError(e.toString()).toString()
            "${lpparam.packageName}:[GLOBAL]:$err".e()
        }


//        app单独脚本

        if (lpparam.packageName in SelectAppsList) {

            for ((k, v) in readMap("$PATH/${LShare.AppConf}/${lpparam.packageName}.txt")) {
                try {
                    if (v is Boolean) {
                        CreateGlobals(
                            lpparam,
                            k
                        ).load(read("$PATH/${LShare.AppScript}/${lpparam.packageName}/$k.lua"))
                            .call()
                    } else if ((v is JSONArray)) {
                        if (v[0] as Boolean) {
                            CreateGlobals(
                                lpparam,
                                k
                            ).load(read("$PATH/${LShare.AppScript}/${lpparam.packageName}/$k.lua"))
                                .call()
                        }
                    }
                } catch (e: Exception) {
                    val err = simplifyLuaError(e.toString()).toString()
                    "${lpparam.packageName}:$k:$err".e()
                }
            }
        }

    }

    fun simplifyLuaError(raw: String): String {
        val lines = raw.lines()

        // 1. 优先提取第一条真正的错误信息（不是 traceback）
        val primaryErrorLine = lines.firstOrNull { it.trim().matches(Regex(""".*:\d+ .+""")) }

        if (primaryErrorLine != null) {
            val match = Regex(""".*:(\d+) (.+)""").find(primaryErrorLine)
            if (match != null) {
                val (lineNum, msg) = match.destructured
                return "line $lineNum: $msg"
            }
        }

        // 2. 其次从 traceback 提取（防止所有匹配失败）
        val fallbackLine = lines.find { it.trim().matches(Regex(""".*:\d+: .*""")) }
        if (fallbackLine != null) {
            val match = Regex(""".*:(\d+): (.+)""").find(fallbackLine)
            if (match != null) {
                val (lineNum, msg) = match.destructured
                return "line $lineNum: $msg"
            }
        }

        return raw.lines().firstOrNull()?.take(100) ?: "未知错误"
    }


    fun CreateGlobals(lpparam: LPParam, scriptName: String = ""): Globals {
        val globals: Globals = JsePlatform.standardGlobals()

        //加载Lua模块
        globals["XpHelper"] = CoerceJavaToLua.coerce(XpHelper::class.java)
        globals["DexFinder"] = CoerceJavaToLua.coerce(DexFinder::class.java)
        globals["XposedHelpers"] = CoerceJavaToLua.coerce(XposedHelpers::class.java)
        globals["XposedBridge"] = CoerceJavaToLua.coerce(XposedBridge::class.java)
        globals["DexKitBridge"] = CoerceJavaToLua.coerce(DexKitBridge::class.java)
        globals["this"] = CoerceJavaToLua.coerce(this)
        globals["suparam"] = CoerceJavaToLua.coerce(suparam)
        LuaActivity(null).call(globals)
        HookLib(lpparam, scriptName).call(globals)
        LuaJson().call(globals)
        LuaHttp().call(globals)
        Luafile().call(globals)
        LuaSharedPreferences().call(globals)
        globals["imports"] = LuaImport(lpparam.classLoader, this::class.java.classLoader!!, globals)
        LuaResourceBridge().registerTo(globals)
        LuaDrawableLoader().registerTo(globals)
        return globals
    }

    fun read(path: String): String {
        if (File(path).exists()) {
            return File(path).readText()
        }
        return ""
    }

    fun readMap(path: String): MutableMap<String, Any?> {
        val jsonString = read(path)
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
            mutableMapOf()
        }

    }


    fun createStartupParam(modulePath: String): IXposedHookZygoteInit.StartupParam {
        val clazz = IXposedHookZygoteInit.StartupParam::class.java
        val constructor = clazz.getDeclaredConstructor()
        constructor.isAccessible = true
        val instance = constructor.newInstance()

        // 设置字段值
        val fieldModulePath = clazz.getDeclaredField("modulePath")
        fieldModulePath.isAccessible = true
        fieldModulePath.set(instance, modulePath)

        return instance
    }


}