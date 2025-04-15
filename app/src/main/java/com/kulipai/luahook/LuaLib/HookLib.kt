import android.content.pm.PackageManager
import com.kulipai.luahook.util.d
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException

class HookLib(private val lpparam: LoadPackageParam) : OneArgFunction() {

    override fun call(globals: LuaValue): LuaValue {


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






//        globals["new"] = object : VarArgFunction() {
//            override fun invoke(args: Varargs): LuaValue {
//                try {
//                    val classNameOrClass = args.arg(1)
//                    val clazz: Class<*> = when {
//                        //字符串
//                        classNameOrClass.isstring() -> {
//                            val className = args.checkjstring(1)
//                            Class.forName(className)
//                        }
//                        //类
//                        classNameOrClass.isuserdata(Any::class.java) -> classNameOrClass.touserdata(Any::class.java)
//                        else -> {
//                            throw IllegalArgumentException("First argument must be class name (String) or Class object")
//                        }
//                    } as Class<*>
//
//                    val params = mutableListOf<Any?>()
//                    val paramTypes = mutableListOf<Class<*>>()
//                    for (i in 2..args.narg()) {
//                        val value = fromLuaValue(args.arg(i))
//                        params.add(value)
//                        paramTypes.add(value?.javaClass ?: Any::class.java)
//                    }
//
//                    "111:${clazz}".d()
//                    val constructor = clazz.getConstructor(*paramTypes.toTypedArray())
//                    constructor.isAccessible = true // 允许访问非公共构造函数
//                    "222:$constructor".d()
//                    val instance = constructor.newInstance(*params.toTypedArray())
//                    "333:$instance".d()
//                    return CoerceJavaToLua.coerce(instance)
//
//                } catch (e: Exception) {
//                    println("newInstance error: ${e.message}")
//                    e.toString().d()
//                }
//                return NIL
//            }
//        }




        // 封装获取构造函数的 Lua 函数
        globals["getConstructor"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (args.narg() < 1 || !args.arg(1).isuserdata(Class::class.java)) {
                    return error("Usage: getConstructor(class, argType1, argType2, ...)")
                }

                val clazz = args.arg(1).touserdata(Class::class.java) as Class<*>
                val paramTypes = mutableListOf<Class<*>>()

                for (i in 2..args.narg()) {
                    val typeName = args.checkjstring(i)
                    try {
                        val type = when (typeName) {
                            "int" -> Int::class.javaPrimitiveType
                            "boolean" -> Boolean::class.javaPrimitiveType
                            "float" -> Float::class.javaPrimitiveType
                            "double" -> Double::class.javaPrimitiveType
                            "long" -> Long::class.javaPrimitiveType
                            "char" -> Char::class.javaPrimitiveType
                            "byte" -> Byte::class.javaPrimitiveType
                            "short" -> Short::class.javaPrimitiveType
                            else -> Class.forName(typeName)
                        }
                        paramTypes.add(type as Class<*>)
                    } catch (e: ClassNotFoundException) {
                        return error("Class not found: $typeName")
                    }
                }

                return try {
                    val constructor = clazz.getConstructor(*paramTypes.toTypedArray())
                    CoerceJavaToLua.coerce(constructor)
                } catch (e: NoSuchMethodException) {
                    LuaValue.NIL
                }
            }
        }

        // 封装创建新实例的 Lua 函数
        globals["newInstance"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (args.narg() < 1 || !args.arg(1).isuserdata(Constructor::class.java)) {
                    return error("Usage: newInstance(constructor, arg1, arg2, ...)")
                }

                val constructor = args.arg(1).touserdata(Constructor::class.java) as Constructor<*>
                val params = mutableListOf<Any?>()

                for (i in 2..args.narg()) {
                    params.add(fromLuaValue(args.arg(i)))
                }

                return try {
                    constructor.isAccessible = true // 允许访问非公共构造函数
                    val instance = constructor.newInstance(*params.toTypedArray())
                    CoerceJavaToLua.coerce(instance)
                } catch (e: InstantiationException) {
                    error("Cannot instantiate object: ${e.message}")
                } catch (e: IllegalAccessException) {
                    error("Illegal access to constructor: ${e.message}")
                } catch (e: InvocationTargetException) {
                    error("Exception during constructor invocation: ${e.targetException?.message}")
                } catch (e: IllegalArgumentException) {
                    error("Illegal arguments for constructor: ${e.message}")
                } catch (e: Exception) {
                    error("An unexpected error occurred: ${e.message}")
                }
                return LuaValue.NIL
            }
        }






        globals["new"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                try {
                    val classNameOrClass = args.arg(1)
                    val clazz: Class<*> = when {
                        //字符串
                        classNameOrClass.isstring() -> {
                            val className = args.checkjstring(1)
                            Class.forName(className)
                        }
                        //类
                        classNameOrClass.isuserdata(Class::class.java) -> classNameOrClass.touserdata(
                            Class::class.java
                        ) as Class<*>

                        else -> {
                            throw IllegalArgumentException("First argument must be class name (String) or Class object")
                        }
                    }

                    val params = mutableListOf<Any?>()
                    val paramTypes = mutableListOf<Class<*>>()
                    for (i in 2..args.narg()) {
                        val luaValue = args.arg(i)
                        val value = fromLuaValue(luaValue)
                        params.add(value)
                        paramTypes.add(
                            value?.javaClass ?: Any::class.java
                        ) // Still using this for simplicity, see note below
                    }

                    val constructor = try {
                        clazz.getConstructor(*paramTypes.toTypedArray())
                    } catch (e: NoSuchMethodException) {
                        // Attempt to find constructor with more flexible type matching
                        var foundConstructor: java.lang.reflect.Constructor<*>? = null
                        for (ctor in clazz.constructors) {
                            if (ctor.parameterCount == params.size) {
                                val ctorParamTypes = ctor.parameterTypes
                                var match = true
                                for (i in params.indices) {
                                    val param = params[i]
                                    val ctorParamType = ctorParamTypes[i]
                                    if (param != null && !ctorParamType.isAssignableFrom(param.javaClass)) {
                                        match = false
                                        break
                                    } else if (param == null && ctorParamType.isPrimitive) {
                                        match = false
                                        break
                                    }
                                }
                                if (match) {
                                    foundConstructor = ctor
                                    break
                                }
                            }
                        }
                        if (foundConstructor == null) {
                            throw e // Re-throw the original NoSuchMethodException if no flexible match found
                        } else {
                            foundConstructor
                        }
                    }

                    constructor.isAccessible = true // 允许访问非公共构造函数
                    val instance = constructor.newInstance(*params.toTypedArray())
                    return CoerceJavaToLua.coerce(instance)

                } catch (e: ClassNotFoundException) {
                    println("newInstance error: Class not found: ${e.message}")
                    e.toString().d()
                    return NIL
                } catch (e: NoSuchMethodException) {
                    println("newInstance error: Constructor not found for ${args.arg(1)} with parameter types:")
                    e.toString().d()
                    return NIL
                } catch (e: InstantiationException) {
                    println("newInstance error: Cannot instantiate ${args.arg(1)}: ${e.message}")
                    e.toString().d()
                    return NIL
                } catch (e: IllegalAccessException) {
                    println("newInstance error: Illegal access to constructor of ${args.arg(1)}: ${e.message}")
                    e.toString().d()
                    return NIL
                } catch (e: InvocationTargetException) {
                    println(
                        "newInstance error: Exception occurred during constructor invocation of ${
                            args.arg(
                                1
                            )
                        }: ${e.targetException?.message}"
                    )
                    e.toString().d()
                    e.targetException?.toString()?.d()
                    return NIL
                } catch (e: IllegalArgumentException) {
                    println("newInstance error: Illegal arguments provided: ${e.message}")
                    e.toString().d()
                    return NIL
                } catch (e: Exception) {
                    println("newInstance error: An unexpected error occurred: ${e.message}")
                    e.toString().d()
                    return NIL
                }
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



        return NIL
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
