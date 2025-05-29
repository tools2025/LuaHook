package com.kulipai.luahook

import HookLib
import LuaDrawableLoader
import LuaHttp
import LuaImport
import LuaJson
import LuaResourceBridge
import LuaSharedPreferences
import Luafile
import com.kulipai.luahook.util.LShare
import com.kulipai.luahook.util.d
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.json.JSONArray
import org.json.JSONObject
import org.luaj.Globals
import org.luaj.LuaValue
import org.luaj.lib.jse.CoerceJavaToLua
import org.luaj.lib.jse.JsePlatform
import org.luckypray.dexkit.DexKitBridge
import top.sacz.xphelper.XpHelper
import top.sacz.xphelper.dexkit.DexFinder
import java.io.File

//


class MainHook : IXposedHookZygoteInit, IXposedHookLoadPackage {

    companion object {
//        init {
//            System.loadLibrary("dexkit")
//        }

        const val MODULE_PACKAGE = "com.kulipai.luahook"  // 模块包名
        val PATH = "/data/local/tmp/LuaHook"
//        const val PREFS_NAME = "xposed_prefs"
//        const val APPS = "apps"
    }


    lateinit var luaScript: String
    lateinit var appsScript: String
    lateinit var SelectAppsString: String

    //    lateinit var apps: XSharedPreferences
//    lateinit var selectApps: XSharedPreferences
    lateinit var SelectAppsList: MutableList<String>

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {

        XpHelper.initZygote(startupParam)

//        val pref = XSharedPreferences(MODULE_PACKAGE, PREFS_NAME)
//        apps = XSharedPreferences(MODULE_PACKAGE, APPS)
//        pref.makeWorldReadable()
//        apps.makeWorldReadable()

//        selectApps = XSharedPreferences(MODULE_PACKAGE, "MyAppPrefs")
//        selectApps.makeWorldReadable()

//        luaScript = pref.getString("lua", "nil").toString()


    }


    private fun canHook(lpparam: LoadPackageParam) {
        if (lpparam.packageName == MODULE_PACKAGE) {
            XposedHelpers.findAndHookMethod(
                "com.kulipai.luahook.fragment.HomeFragmentKt",
                lpparam.classLoader,
                "canHook",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {

                    }

                    override fun afterHookedMethod(param: MethodHookParam?) {
                        if (param != null) {
                            param.result = true
                        }
                    }
                }
            )
        }
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {

        SelectAppsString = read(PATH + "/apps.txt").replace("\n", "")

        luaScript = read(PATH + "/global.lua")

        if (SelectAppsString.isNotEmpty() && SelectAppsString != "") {
            SelectAppsList = SelectAppsString.split(",").toMutableList()
        } else {
            SelectAppsList = mutableListOf()
        }

        canHook(lpparam)


        //全局脚本
        try {
            //排除自己
            if (lpparam.packageName != MODULE_PACKAGE) {
                val chunk: LuaValue = CreateGlobals(lpparam).load(luaScript)
                chunk.call()
            }
        } catch (e: Exception) {
            e.toString().d()
        }


//        app单独脚本
        try {
            if (lpparam.packageName in SelectAppsList) {

                for ((k, v) in readMap("$PATH/${LShare.AppConf}/${lpparam.packageName}.txt")) {
                    if (v is Boolean) {
                        CreateGlobals(lpparam).load(read("$PATH/${LShare.AppScript}/${lpparam.packageName}/$k.lua"))
                            .call()
                    } else if ((v is JSONArray)) {
                        if ((v as JSONArray)[0] as Boolean) {
                            CreateGlobals(lpparam).load(read("$PATH/${LShare.AppScript}/${lpparam.packageName}/$k.lua"))
                                .call()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.toString().d()
        }


    }

    fun CreateGlobals(lpparam: LoadPackageParam): Globals {
        val globals: Globals = JsePlatform.standardGlobals()

        //加载Lua模块
        globals["XpHelper"] = CoerceJavaToLua.coerce(XpHelper::class.java)
        globals["DexFinder"] = CoerceJavaToLua.coerce(DexFinder::class.java)
        globals["XposedHelpers"] = CoerceJavaToLua.coerce(XposedHelpers::class.java)
        globals["XposedBridge"] = CoerceJavaToLua.coerce(XposedBridge::class.java)
        globals["DexKitBridge"] = CoerceJavaToLua.coerce(DexKitBridge::class.java)
        globals["this"] = CoerceJavaToLua.coerce(this)
        HookLib(lpparam).call(globals)
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


}