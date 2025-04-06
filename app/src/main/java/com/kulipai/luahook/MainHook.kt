package com.kulipai.luahook


import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.kulipai.luahook.util.d
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.IOException



class MainHook : IXposedHookZygoteInit, IXposedHookLoadPackage {

    private val client = OkHttpClient()  // 确保 OkHttpClient 实例已定义



    companion object {
        const val MODULE_PACKAGE = "com.kulipai.luahook"  // 模块包名
        const val PREFS_NAME = "xposed_prefs"

    }


    lateinit var luaScript: String

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        val pref = XSharedPreferences(MODULE_PACKAGE, PREFS_NAME)
        pref.makeWorldReadable()

        luaScript = pref.getString("lua","nil").toString()
    }


    private fun hookPriorityPermissions(lpparam: LoadPackageParam) {
        // 1. Hook ActivityThread的handleBindApplication方法，这发生在Application.onCreate之前
        XposedHelpers.findAndHookMethod(
            "android.app.ActivityThread",
            lpparam.classLoader,
            "handleBindApplication",
            "android.app.ActivityThread\$AppBindData",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    XposedBridge.log("提前处理应用绑定，添加网络权限")

                    // 通过AppBindData获取ApplicationInfo对象
                    val appBindData = param.args[0]
                    val appInfo = XposedHelpers.getObjectField(appBindData, "appInfo") as ApplicationInfo

                    // 确保enableOnBackInvokedCallback属性为true
                    XposedHelpers.setObjectField(appInfo, "enableOnBackInvokedCallback", true)
                    XposedBridge.log("已提前设置enableOnBackInvokedCallback为true")
                }
            }
        )

        // 2. 直接hook关键的权限检查方法
        // Hook Context中的网络权限检查方法
        hookPermissionMethod(lpparam, "android.app.ContextImpl", "checkPermission")
        hookPermissionMethod(lpparam, "android.app.ContextImpl", "checkCallingPermission")
        hookPermissionMethod(lpparam, "android.app.ContextImpl", "checkCallingOrSelfPermission")
        hookPermissionMethod(lpparam, "android.app.ContextImpl", "checkSelfPermission")

        // 3. Hook网络操作相关的安全管理器检查
        hookSecurityManager(lpparam)

        // 4. 提前修改PackageManager的权限检查
        hookPackageManagerMethods(lpparam)
    }

    private fun hookPermissionMethod(lpparam: LoadPackageParam, className: String, methodName: String) {
        try {
            XposedHelpers.findAndHookMethod(
                className,
                lpparam.classLoader,
                methodName,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val permission = param.args[0] as String
                        if (permission == "android.permission.INTERNET") {
                            param.result = PackageManager.PERMISSION_GRANTED
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            // 某些Android版本可能方法签名不同，捕获异常防止模块崩溃
            XposedBridge.log("Hook方法 $methodName 失败: ${e.message}")

            // 尝试不同的方法签名
            try {
                XposedHelpers.findAndHookMethod(
                    className,
                    lpparam.classLoader,
                    methodName,
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val permission = param.args[0] as String
                            if (permission == "android.permission.INTERNET") {
                                param.result = PackageManager.PERMISSION_GRANTED
                            }
                        }
                    }
                )
            } catch (e: Throwable) {
                XposedBridge.log("尝试备用方法签名也失败: ${e.message}")
            }
        }
    }

    private fun hookSecurityManager(lpparam: LoadPackageParam) {
        // Hook SecurityManager的checkPermission方法
        try {
            XposedHelpers.findAndHookMethod(
                "java.lang.SecurityManager",
                lpparam.classLoader,
                "checkPermission",
                java.security.Permission::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val permission = param.args[0]
                        val permName = permission.toString()
                        if (permName.contains("internet") || permName.contains("socket")) {
                            // 跳过这个检查
                            param.result = null
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("Hook SecurityManager失败: ${e.message}")
        }
    }

    private fun hookPackageManagerMethods(lpparam: LoadPackageParam) {
        // Hook PackageManager的getPackageInfo方法注入权限
        XposedHelpers.findAndHookMethod(
            "android.app.ApplicationPackageManager",
            lpparam.classLoader,
            "getPackageInfo",
            String::class.java,
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val packageInfo = param.result as PackageInfo?
//                    param.args[0] as String

                    if (packageInfo != null) {
                        // 确保权限列表存在
                        if (packageInfo.requestedPermissions == null) {
                            packageInfo.requestedPermissions = arrayOf("android.permission.INTERNET")
                            packageInfo.requestedPermissionsFlags = intArrayOf(PackageInfo.REQUESTED_PERMISSION_GRANTED)
                        } else {
                            // 检查是否已有该权限
                            val permList = packageInfo.requestedPermissions!!.toMutableList()
                            if (!permList.contains("android.permission.INTERNET")) {
                                permList.add("android.permission.INTERNET")
                                packageInfo.requestedPermissions = permList.toTypedArray()

                                val newFlags = if (packageInfo.requestedPermissionsFlags != null) {
                                    packageInfo.requestedPermissionsFlags!!.toMutableList()
                                } else {
                                    mutableListOf()
                                }
                                newFlags.add(PackageInfo.REQUESTED_PERMISSION_GRANTED)
                                packageInfo.requestedPermissionsFlags = newFlags.toIntArray()
                            }
                        }
                    }
                }
            }
        )
    }


    override fun handleLoadPackage(lpparam: LoadPackageParam) {

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
                else -> null // Add more type conversions as needed
            }
        }




        hookPriorityPermissions(lpparam)




        val globals: Globals = JsePlatform.standardGlobals()

        val http = LuaValue.tableOf()
       http["get"] = object : VarArgFunction() {
           override fun invoke(args: Varargs): LuaValue {
               val url = args.checkjstring(1)
               val callback = args.checkfunction(args.narg())
               val headers = if (args.narg() > 2 && args.istable(2)) args.checktable(2) else null
               val cookie = if (args.narg() > 3) args.optjstring(3, null) else null

               val requestBuilder = Request.Builder().url(url)

               // 添加请求头
               headers?.let {
                   val headerMap = mutableMapOf<String, String>()

                   // 遍历 LuaTable
                   var key: LuaValue = LuaValue.NIL
                   while (true) {
                       val nextPair = headers.next(key)
                       key = nextPair.arg1()  // 获取下一个键
                       val value = nextPair.arg(2)  // 获取键对应的值

                       if (key.isnil()) break  // 遍历完所有键，退出循环

                       headerMap[key.tojstring()] = value.tojstring()
                   }



               for ((k, v) in headerMap) {
                       requestBuilder.addHeader(k, v)
                   }
               }

               // 添加 Cookie
               cookie?.let {
                   requestBuilder.addHeader("Cookie", it)
               }

               val request = requestBuilder.build()

               client.newCall(request).enqueue(object : Callback {
                   override fun onFailure(call: Call, e: IOException) {
                       callback.call(LuaValue.valueOf(-1), LuaValue.valueOf(e.message ?: ""))
                   }

                   override fun onResponse(call: Call, response: Response) {
                       val body = response.body?.string() ?: ""
                       callback.call(LuaValue.valueOf(response.code), LuaValue.valueOf(body))
                   }
               })

               return LuaValue.NIL
           }
       }


        http["post"] = object :VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val url = args.checkjstring(1)
                val postData = args.checkjstring(2)
                val callback = args.checkfunction(args.narg())
                val headers = if (args.narg() > 3 && args.istable(3)) args.checktable(3) else null
                val cookie = if (args.narg() > 4) args.optjstring(4, null) else null

                val requestBody = RequestBody.create("application/x-www-form-urlencoded".toMediaTypeOrNull(), postData)

                val requestBuilder = Request.Builder().url(url).post(requestBody)

                // 添加请求头
                headers?.let {
                    val headerMap = mutableMapOf<String, String>()

                    // 遍历 LuaTable
                    var key: LuaValue = LuaValue.NIL
                    while (true) {
                        val nextPair = headers.next(key)
                        key = nextPair.arg1()  // 获取下一个键
                        val value = nextPair.arg(2)  // 获取键对应的值

                        if (key.isnil()) break  // 遍历完所有键，退出循环

                        headerMap[key.tojstring()] = value.tojstring()
                    }
                    for ((k, v) in headerMap) {
                        requestBuilder.addHeader(k, v)
                    }
                }

                // 添加 Cookie
                cookie?.let {
                    requestBuilder.addHeader("Cookie", it)
                }

                val request = requestBuilder.build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback.call(LuaValue.valueOf(-1), LuaValue.valueOf(e.message ?: ""))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body?.string() ?: ""
                        callback.call(LuaValue.valueOf(response.code), LuaValue.valueOf(body))
                    }
                })

                return LuaValue.NIL
            }
        }

        globals["http"] = http



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
                    var classLoader: ClassLoader? = null
                    var methodName: String
                    if (classNameOrClass.isstring()) { /////////string,
                        val classLoader =
                            args.optuserdata(2, lpparam.javaClass.classLoader) as ClassLoader
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


                    //val methodName = args.checkjstring(3)
//                    val clazz: Class<*> = when {
//                        classNameOrClass.isstring() -> {
//                            XposedHelpers.findClass(classNameOrClass.tojstring(), classLoader)
//
//                        }
//                        classNameOrClass.isuserdata(Class::class.java) -> classNameOrClass.touserdata(Class::class.java) as Class<*>
//                        else -> throw IllegalArgumentException("Invalid class name or Class object")
//                    }


                } catch (e: Exception) {
                    println("Hook error: ${e.message}")
                    e.printStackTrace()
                }
                return NIL
            }
        }




        val chunk: LuaValue = globals.load(luaScript)
        chunk.call()


    }
}