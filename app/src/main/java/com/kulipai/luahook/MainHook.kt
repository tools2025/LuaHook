package com.kulipai.luahook


import LuaHttp
import LuaImport
import LuaJson
import Luafile
import android.content.pm.PackageManager
import com.kulipai.luahook.util.d
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.cast


class MainHook : IXposedHookZygoteInit, IXposedHookLoadPackage {


    companion object {
        const val MODULE_PACKAGE = "com.kulipai.luahook"  // 模块包名
        const val PREFS_NAME = "xposed_prefs"
        const val APPS = "apps"
    }


    lateinit var luaScript: String
    lateinit var appsScript: String
    lateinit var SelectAppsString: String
    lateinit var apps: XSharedPreferences
    lateinit var selectApps: XSharedPreferences
    lateinit var SelectAppsList: MutableList<String>

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        val pref = XSharedPreferences(MODULE_PACKAGE, PREFS_NAME)
        apps = XSharedPreferences(MODULE_PACKAGE, APPS)
        pref.makeWorldReadable()
        apps.makeWorldReadable()

        selectApps = XSharedPreferences(MODULE_PACKAGE, "MyAppPrefs")
        selectApps.makeWorldReadable()

        luaScript = pref.getString("lua", "nil").toString()


    }


    private fun canHook(lpparam: LoadPackageParam) {
        if (lpparam.packageName == MODULE_PACKAGE) {
            XposedHelpers.findAndHookMethod(
                "com.kulipai.luahook.fragment.HomeFragment",
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

        SelectAppsString = selectApps.getString("selectApps", "").toString()

        if (SelectAppsString.isNotEmpty()) {
            SelectAppsList = SelectAppsString.split(",").toMutableList()
        } else {
            SelectAppsList = mutableListOf()
        }

        canHook(lpparam)


        val globals: Globals = JsePlatform.standardGlobals()

        //加载Lua模块
        LuaJson().call(globals)
        LuaHttp().call(globals)
        Luafile().call(globals)
        globals["import"] = LuaImport(lpparam.classLoader, globals)


        val LuaFile = object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val path = arg.checkjstring()
                val file = File(path)
                return CoerceJavaToLua.coerce(file)
            }
        }

        globals["File"] = LuaFile

        globals["lpparam"] = CoerceJavaToLua.coerce(lpparam)

        globals["getField"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                return try {
                    val targetObject = args.arg(1)
                    val fieldName = args.checkjstring(2)

                    if (targetObject.isuserdata(Any::class.java)) {
                        val target = targetObject.touserdata(Any::class.java)
                        val result = XposedHelpers.getObjectField(target, fieldName)
                        CoerceJavaToLua.coerce(result)
                    } else {
                        NIL
                    }
                } catch (e: Exception) {
                    println("getField error: ${e.message}")
                    NIL
                }
            }
        }

        globals["setField"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                return try {
                    val targetObject = args.arg(1)
                    val fieldName = args.checkjstring(2)
                    val fieldValue = fromLuaValue(args.arg(3))

                    if (targetObject.isuserdata(Any::class.java)) {
                        val target = targetObject.touserdata(Any::class.java)
                        XposedHelpers.setObjectField(target, fieldName, fieldValue)
                    }
                    NIL
                } catch (e: Exception) {
                    println("setField error: ${e.message}")
                    NIL
                }
            }
        }

        globals["getStaticField"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                return try {
                    val className = args.checkjstring(1)
                    val fieldName = args.checkjstring(2)
                    val clazz = XposedHelpers.findClass(className, lpparam.classLoader)
                    val result = XposedHelpers.getStaticObjectField(clazz, fieldName)
                    CoerceJavaToLua.coerce(result)
                } catch (e: Exception) {
                    println("getStaticField error: ${e.message}")
                    NIL
                }
            }
        }

        globals["setStaticField"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                return try {
                    val className = args.checkjstring(1)
                    val fieldName = args.checkjstring(2)
                    val fieldValue = fromLuaValue(args.arg(3))
                    val clazz = XposedHelpers.findClass(className, lpparam.classLoader)
                    XposedHelpers.setStaticObjectField(clazz, fieldName, fieldValue)
                    NIL
                } catch (e: Exception) {
                    println("setStaticField error: ${e.message}")
                    NIL
                }
            }
        }

        globals["log"] = object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val message = arg.tojstring()
                message.d()
                XposedBridge.log(message)
                return NIL
            }
        }

        globals["findClass"] = object : TwoArgFunction() {
            override fun call(self: LuaValue, classLoader: LuaValue): LuaValue {
                return try {
                    // 确保 self 是字符串
                    val className = self.checkjstring()
                    val loader = classLoader.checkuserdata(ClassLoader::class.java) as ClassLoader

                    // 查找类
                    val clazz = XposedHelpers.findClass(className, loader)
                    CoerceJavaToLua.coerce(clazz) // 返回 Java Class 对象
                } catch (e: Exception) {
                    println("findClass error: ${e.message}")
                    NIL
                }
            }
        }

        globals["invoke"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                try {
                    val obj = args.arg(1)  // Lua 传入的第一个参数
                    val methodName = args.checkjstring(2) // 方法名

                    // 获取 Java 对象
                    val targetObject: Any? = when {
                        obj.isuserdata(XC_MethodHook.MethodHookParam::class.java) -> {
                            val param =
                                obj.touserdata(XC_MethodHook.MethodHookParam::class.java) as XC_MethodHook.MethodHookParam
                            param.thisObject // 获取 thisObject
                        }

                        obj.isuserdata(Any::class.java) -> obj.touserdata(Any::class.java)
                        else -> throw IllegalArgumentException("invoke 需要 Java 对象")
                    }

                    // 收集参数
                    val javaParams = mutableListOf<Any?>()
                    for (i in 3..args.narg()) {
                        javaParams.add(fromLuaValue(args.arg(i)))
                    }

                    // 反射调用方法
                    val result = XposedHelpers.callMethod(
                        targetObject,
                        methodName,
                        *javaParams.toTypedArray()
                    )

                    return CoerceJavaToLua.coerce(result) // 把 Java 结果返回 Lua
                } catch (e: Exception) {
                    println("invoke error: ${e.message}")
                    e.printStackTrace()
                }
                return NIL
            }
        }

        globals["hook"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                try {
                    val classNameOrClass = args.arg(1)
                    val classLoader: ClassLoader? = null
                    val methodName: String
                    if (classNameOrClass.isstring()) { /////////string,
                        val classLoader =
                            args.optuserdata(2, lpparam.javaClass.classLoader) as ClassLoader
//                        classLoader.toString().d()
//                        lpparam.classLoader.toString().d()
                        val className = classNameOrClass.tojstring()
                        methodName = args.checkjstring(3)

                        // 动态处理参数类型
                        val paramTypes = mutableListOf<Class<*>>()
                        val luaParams = mutableListOf<LuaValue>()

                        // 收集参数类型
                        for (i in 4 until args.narg() - 1) {
                            val param = args.arg(i)
                            when {
                                param.isstring() -> {
                                    // 支持更多类型转换
                                    val typeStr = param.tojstring()
                                    val type = when (typeStr) {
                                        "int" -> Int::class.javaPrimitiveType
                                        "long" -> Long::class.javaPrimitiveType
                                        "boolean" -> Boolean::class.javaPrimitiveType
                                        "double" -> Double::class.javaPrimitiveType
                                        "float" -> Float::class.javaPrimitiveType
                                        "String" -> String::class.java
                                        else -> Class.forName(typeStr, true, classLoader)
                                    }
                                    paramTypes.add(type!!)
                                    luaParams.add(param)
                                }
                                // 可以扩展更多类型的处理
                                else -> {
                                    throw IllegalArgumentException("Unsupported parameter type: ${param.type()}")
                                }
                            }
                        }

                        val beforeFunc = args.optfunction(args.narg() - 1, null)
                        val afterFunc = args.optfunction(args.narg(), null)

                        XposedHelpers.findAndHookMethod(
                            className,
                            classLoader,
                            methodName,
                            *paramTypes.toTypedArray(),
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam?) {
                                    beforeFunc?.let { func ->
                                        val luaParam = CoerceJavaToLua.coerce(param)

                                        // 允许在Lua中修改参数
                                        val modifiedParam = func.call(luaParam)

                                        // 如果Lua函数返回了修改后的参数，则替换原参数
                                        if (!modifiedParam.isnil()) {
                                            // 假设返回的是一个表，包含修改后的参数
                                            if (modifiedParam.istable()) {
                                                val table = modifiedParam.checktable()
                                                val argsTable = table.get("args")

                                                if (argsTable.istable()) {
                                                    val argsModified = argsTable.checktable()
                                                    for (i in 1..argsModified.length()) {
                                                        // 将Lua的参数转换回Java类型
                                                        param?.args?.set(
                                                            i - 1,
                                                            fromLuaValue(argsModified.get(i))
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                override fun afterHookedMethod(param: MethodHookParam?) {
                                    afterFunc?.let { func ->
                                        val luaParam = CoerceJavaToLua.coerce(param)

                                        // 允许在Lua中修改返回值
                                        val modifiedResult = func.call(luaParam)

                                        // 如果Lua函数返回了修改后的结果，则替换原返回值
                                        if (!modifiedResult.isnil()) {

                                            param?.result = fromLuaValue(modifiedResult)
                                        }
                                    }
                                }
                            }
                        )


                    } else if (classNameOrClass.isuserdata(Class::class.java)) {   ///classs
                        var hookClass = classNameOrClass.touserdata(Class::class.java) as Class<*>
                        methodName = args.checkjstring(2)

                        // 动态处理参数类型
                        val paramTypes = mutableListOf<Class<*>>()
                        val luaParams = mutableListOf<LuaValue>()

                        // 收集参数类型
                        for (i in 3 until args.narg() - 1) {
                            val param = args.arg(i)
                            when {
                                param.isstring() -> {
                                    // 支持更多类型转换
                                    val typeStr = param.tojstring()
                                    val type = when (typeStr) {
                                        "int" -> Int::class.javaPrimitiveType
                                        "long" -> Long::class.javaPrimitiveType
                                        "boolean" -> Boolean::class.javaPrimitiveType
                                        "double" -> Double::class.javaPrimitiveType
                                        "float" -> Float::class.javaPrimitiveType
                                        "String" -> String::class.java
                                        else -> Class.forName(typeStr, true, classLoader)
                                    }
                                    paramTypes.add(type!!)
                                    luaParams.add(param)
                                }
                                // 可以扩展更多类型的处理
                                else -> {
                                    throw IllegalArgumentException("Unsupported parameter type: ${param.type()}")
                                }
                            }
                        }

                        val beforeFunc = args.optfunction(args.narg() - 1, null)
                        val afterFunc = args.optfunction(args.narg(), null)

                        XposedHelpers.findAndHookMethod(
                            hookClass,
                            methodName,
                            *paramTypes.toTypedArray(),
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam?) {
                                    beforeFunc?.let { func ->
                                        val luaParam = CoerceJavaToLua.coerce(param)

                                        // 允许在Lua中修改参数
                                        val modifiedParam = func.call(luaParam)

                                        // 如果Lua函数返回了修改后的参数，则替换原参数
                                        if (!modifiedParam.isnil()) {
                                            // 假设返回的是一个表，包含修改后的参数
                                            if (modifiedParam.istable()) {
                                                val table = modifiedParam.checktable()
                                                val argsTable = table.get("args")

                                                if (argsTable.istable()) {
                                                    val argsModified = argsTable.checktable()
                                                    for (i in 1..argsModified.length()) {
                                                        // 将Lua的参数转换回Java类型
                                                        param?.args?.set(
                                                            i - 1,
                                                            fromLuaValue(argsModified.get(i))
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                override fun afterHookedMethod(param: MethodHookParam?) {
                                    afterFunc?.let { func ->
                                        val luaParam = CoerceJavaToLua.coerce(param)

                                        // 允许在Lua中修改返回值
                                        val modifiedResult = func.call(luaParam)

                                        // 如果Lua函数返回了修改后的结果，则替换原返回值
                                        if (!modifiedResult.isnil()) {

                                            param?.result = fromLuaValue(modifiedResult)
                                        }
                                    }
                                }
                            }
                        )


                    }

                    PackageManager.PERMISSION_GRANTED

                } catch (e: Exception) {
                    println("Hook error: ${e.message}")
                    e.printStackTrace()
                }
                return NIL
            }
        }

        //全局脚本
        try {
            //排除自己
            if (lpparam.packageName != MODULE_PACKAGE) {
                val chunk: LuaValue = globals.load(luaScript)
                chunk.call()
            }
        } catch (e: Exception) {
            e.toString().d()
        }


        //app单独脚本
        try {
            if (lpparam.packageName in SelectAppsList) {
                appsScript = apps.getString(lpparam.packageName, "nil").toString()
                globals.load(appsScript).call()
            }
        } catch (e: Exception) {
            e.toString().d()
        }


    }

    // 将Lua值转换回Java类型
    fun fromLuaValue(value: LuaValue?): Any? {
        return when {
            value == null || value.isnil() -> null
            value.isboolean() -> value.toboolean()
            value.isint() -> value.toint()
            value.islong() -> value.tolong()
            value.isnumber() -> value.todouble()
            value.isstring() -> value.tojstring()
            value.isuserdata() -> value.touserdata()
            else -> null
        }
    }




}