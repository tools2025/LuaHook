package com.kulipai.luahook


import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
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


class MainHook : IXposedHookZygoteInit, IXposedHookLoadPackage {

    companion object {
        const val MODULE_PACKAGE = "com.kulipai.luahook"  // 模块包名
        const val PREFS_NAME = "xposed_prefs"
        const val TAG = "XposedModule"
    }

    // 全局 XSharedPreferences 对象
    private lateinit var xPrefs: XSharedPreferences

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        // 读取自己模块的 SharedPreferences 文件（路径为 /data/data/模块包名/shared_prefs/xposed_prefs.xml）
        xPrefs = XSharedPreferences(MODULE_PACKAGE, PREFS_NAME)
        xPrefs.makeWorldReadable()
       ("XSharedPreferences 初始化，文件路径：${xPrefs.file.absolutePath}").d()
    }


    override fun handleLoadPackage(lpparam: LoadPackageParam) {



        xPrefs.reload()
        val luaScript = xPrefs.getString("lua", "return nil")


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


        val globals: Globals = JsePlatform.standardGlobals()


        // 1. 传递 lpparam (类加载器) 给 Lua
        globals["lpparam"] = CoerceJavaToLua.coerce(lpparam)

        globals["log"] = object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val message = arg.tojstring()
                message.d()
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
                            val param = obj.touserdata(XC_MethodHook.MethodHookParam::class.java) as XC_MethodHook.MethodHookParam
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
                    val result = XposedHelpers.callMethod(targetObject, methodName, *javaParams.toTypedArray())

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
                    var methodName : String
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
                        var HookClass= classNameOrClass.touserdata(Class::class.java) as Class<*>
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
                            HookClass,
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

        // 3. 运行 Lua 代码
        // 运行 Lua 代码
//        val luaScript = """
//    log(lpparam.packageName)
//    package=lpparam.packageName
//    if package == "com.ad2001.frida0x1" then
//        local MainActivity = findClass("com.ad2001.frida0x1.MainActivity",lpparam.classLoader)
//        log(MainActivity)
//        if MainActivity then
//            hook(MainActivity, "get_random",
//                function(param)
//                    log("【Before get_random】参数: " .. tostring(param.args[1]))
//                    --return { args = {"Modified Parameter"} }
//                end,
//                function(param)
//                    log("【After get_random】返回值: " .. tostring(param.result))
//                    --return 1
//                end
//            )
//
//             hook(MainActivity, "check", "int", "int",
//                function(param)
//                    log("【Before check】参数: " .. tostring(param.args[1]) .. tostring(param.args[2]))
//                    return { args = {1,6} }
//                end,
//                function(param)
//                    log("【After check】参数: " .. tostring(param.result))
//                end
//            )
//            --[[
//            hook(MainActivity, "onCreate", "android.os.Bundle",
//                function(param)
//                    log("【Before onCreate】")
//                end,
//                function(param)
//                    log("【After onCreate】")
//                    local activity = param.thisObject
//                    log(activity)
//                    local Toast = findClass("android.widget.Toast")
//                    log(Toast)
//                    --Toast.makeText(activity, "0x11 Hook成功！", Toast.LENGTH_SHORT):show()
//                    --invoke(param,"check", 1, 6)
//                end
//            )
//            ]]
//
//        else
//            log("MainActivity class not found!")
//        end
//    end
//""".trimIndent()
        luaScript?.d("luaddd")
        val chunk: LuaValue = globals.load(luaScript)
        chunk.call()


//        // 1. 创建 Lua 运行环境
//        val globals: Globals = JsePlatform.standardGlobals()
//
//        // 2. 传递变量到 Lua
//        globals["name"] = LuaValue.valueOf("Kotlin User")
//        globals["number"] = LuaValue.valueOf(42)
//        globals["lpparam"] = CoerceJavaToLua.coerce(lpparam)
////        globals["lp"] = LuaValue.valueOf(lpparam)
//
//        // 3. 传递 Kotlin 方法到 Lua
//        globals["logMessage"] = object : OneArgFunction() {
//            override fun call(arg: LuaValue): LuaValue {
//                val message = arg.tojstring()
//                return LuaValue.valueOf("Received: $message")
//            }
//        }
//
//        globals["addNumbers"] = object : TwoArgFunction() {
//            override fun call(a: LuaValue, b: LuaValue): LuaValue {
//                val sum = a.toint() + b.toint()
//                return LuaValue.valueOf(sum)
//            }
//        }
//
//        globals["showDialog"] = object : VarArgFunction() {
//            override fun invoke(varargs: Varargs): LuaValue {
//                val title = varargs.optjstring(1, "默认标题")  // 参数 1：标题
//                val message = varargs.optjstring(2, "默认内容")  // 参数 2：内容
//                val positiveText = varargs.optjstring(3, "确定")  // 参数 3：按钮1文本
//                val negativeText = varargs.optjstring(4, "取消")  // 参数 4：按钮2文本
//
//                return LuaValue.NIL
//            }
//        }
//
//        // 2. 定义 Kotlin 类
//        class Person(val name: String, val age: Int) {
//            fun greet(): String {
//                return "你好，我是 $name，今年 $age 岁！"
//            }
//        }
//
//
//        // 2. 让 Kotlin 传递 Person 类到 Lua
//        globals["createPerson"] = object : VarArgFunction() {
//            override fun invoke(varargs: Varargs): LuaValue {
//                val name = varargs.optjstring(1, "未知")
//                val age = varargs.optint(2, 18)
//                return CoerceJavaToLua.coerce(Person(name, age)) // 传递 Kotlin 类
//            }
//        }
//
//
//
//
//        // 4. Lua 代码
//        val luaScript = """
//            print("Hello from Lua! Name: " .. name .. ", Number: " .. number)
//            result = logMessage("Lua 发送的信息")
//            print("Kotlin 返回: " .. result)
//        """.trimIndent()
//
//        // 5. 运行 Lua 代码
//        val chunk: LuaValue = globals.load(luaScript)
//        val result: LuaValue = chunk.call()
////        print(result)
    }
}