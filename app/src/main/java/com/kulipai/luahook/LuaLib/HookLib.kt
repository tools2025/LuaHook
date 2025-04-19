import android.content.pm.PackageManager
import com.kulipai.luahook.util.d
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.CoerceLuaToJava
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class HookLib(private val lpparam: LoadPackageParam) : OneArgFunction() {

    override fun call(globals: LuaValue): LuaValue {


        globals["float"] = object : OneArgFunction() {
            override fun call(p0: LuaValue): LuaValue? {
                return CoerceJavaToLua.coerce(p0.tofloat())
            }
        }
        globals["double"] = object : OneArgFunction() {
            override fun call(p0: LuaValue): LuaValue? {
                return CoerceJavaToLua.coerce(p0.todouble())
            }
        }


        globals["arrayOf"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                try {
                    // Create a new ArrayList
                    val arrayList = ArrayList<Any?>()

                    // Add all arguments to the ArrayList
                    for (i in 1..args.narg()) {
                        val arg = args.arg(i)

                        // Convert Lua values to appropriate Java types
                        val javaValue = when {
                            arg.isnil() -> null
                            arg.isboolean() -> arg.toboolean()
                            arg.isint() -> arg.toint()
                            arg.islong() -> arg.tolong()
                            arg.isnumber() -> arg.todouble()
                            arg.isstring() -> arg.tojstring()
                            arg.istable() -> {
                                // Convert table to ArrayList (for nested arrays)
                                val nestedList = ArrayList<Any?>()
                                val table = arg.checktable()
                                for (j in 1..table.length()) {
                                    nestedList.add(fromLuaValue(table.get(j)))
                                }
                                nestedList
                            }

                            arg.isuserdata() -> arg.touserdata()
                            else -> arg.toString()
                        }

                        arrayList.add(javaValue)
                    }

                    // Return the ArrayList wrapped as a LuaValue
                    return CoerceJavaToLua.coerce(arrayList)

                } catch (e: Exception) {
                    println("arrayOf error: ${e.message}")
                    e.printStackTrace()
                    return NIL
                }
            }
        }

//        globals["arrayOfType"] = object : TwoArgFunction() {
//            override fun call(listArg: LuaValue, typeArg: LuaValue): LuaValue {
//                return try {
//                    // 获取传入的 ArrayList（通过 luajava 传入）
//                    val list = listArg.touserdata(ArrayList::class.java) as? ArrayList<*> ?: return NIL
//
//                    // 获取目标类型（通过 Class 或字符串表示）
//                    val componentType: Class<*>? = when {
//                        typeArg.isuserdata(Class::class.java) -> typeArg.touserdata(Class::class.java) as Class<*>
//                        typeArg.isstring() -> when (val name = typeArg.tojstring()) {
//                            "String" -> String::class.java
//                            "Class" -> Class::class.java
//                            "Integer" -> Int::class.javaObjectType
//                            "Long" -> Long::class.javaObjectType
//                            "Double" -> Double::class.javaObjectType
//                            "Boolean" -> Boolean::class.javaObjectType
//                            "Any" -> Any::class.java
//                            else -> throw IllegalArgumentException("Unknown type: $name")
//                        }
//                        else -> throw IllegalArgumentException("Invalid type argument: $typeArg")
//                    }
//
//                    // 创建数组
//                    val javaArray = java.lang.reflect.Array.newInstance(componentType, list.size) as Array<Any?>
//
//                    for (i in list.indices) {
//                        javaArray[i] = list[i]
//                    }
//
//                    // 返回数组包装为 LuaValue
//                    CoerceJavaToLua.coerce(javaArray)
//
//                } catch (e: Exception) {
//                    println("arrayOfType error: ${e.message}")
//                    e.printStackTrace()
//                    NIL
//                }
//            }
//        }


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
                    } catch (_: ClassNotFoundException) {
                        return error("Class not found: $typeName")
                    }
                }

                return try {
                    val constructor = clazz.getConstructor(*paramTypes.toTypedArray())
                    CoerceJavaToLua.coerce(constructor)
                } catch (_: NoSuchMethodException) {
                    NIL
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
                return NIL
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
                        foundConstructor
                            ?: throw e // Re-throw the original NoSuchMethodException if no flexible match found
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


        globals["hookm"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                try {

                    if (!args.arg(1).isuserdata(Method::class.java)) {
                        "hook参数非method".d()
                    }
                    val method = args.arg(1).touserdata(Method::class.java) as Method
                    val beforeFunc = args.optfunction(2, null)
                    val afterFunc = args.optfunction(3, null)

                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam?) {
                                beforeFunc?.let { func ->
                                    val luaParam = CoerceJavaToLua.coerce(param)

                                    // Allow modifying parameters in Lua
                                    val modifiedParam = func.call(luaParam)

                                    // If Lua function returned modified parameters, replace original parameters
                                    if (!modifiedParam.isnil()) {
                                        // Assuming return is a table containing modified parameters
                                        if (modifiedParam.istable()) {
                                            val table = modifiedParam.checktable()
                                            val argsTable = table.get("args")

                                            if (argsTable.istable()) {
                                                val argsModified = argsTable.checktable()
                                                for (i in 1..argsModified.length()) {
                                                    // Convert Lua parameters back to Java types
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

                                    // Allow modifying return value in Lua
                                    val modifiedResult = func.call(luaParam)

                                    // If Lua function returned modified result, replace original result
                                    if (!modifiedResult.isnil()) {
                                        param?.result = fromLuaValue(modifiedResult)
                                    }
                                }
                            }
                        }
                    )

                    return TRUE

                } catch (e: Exception) {
                    ("HookMethod error: ${e.message}").d()
                    e.printStackTrace()
                    return FALSE
                }
            }
        }




        globals["hookcotr"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                try {
                    val classNameOrClass = args.arg(1)
                    val classLoader: ClassLoader? = null

                    if (classNameOrClass.isstring()) { // If first arg is a string (class name)
                        val classLoader =
                            args.optuserdata(2, lpparam.javaClass.classLoader) as ClassLoader
                        val className = classNameOrClass.tojstring()

                        // Dynamic parameter type handling
                        val paramTypes = mutableListOf<Class<*>>()

                        // Collect parameter types (starting from index 3)
                        for (i in 3 until args.narg() - 1) {
                            val param = args.arg(i)
                            when {
                                param.isstring() -> {
                                    // Support various type conversions
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
                                }
                                // Can expand more type handling here
                                else -> {
                                    throw IllegalArgumentException("Unsupported parameter type: ${param.type()}")
                                }
                            }
                        }

                        val beforeFunc = args.optfunction(args.narg() - 1, null)
                        val afterFunc = args.optfunction(args.narg(), null)

                        XposedHelpers.findAndHookConstructor(
                            className,
                            classLoader,
                            *paramTypes.toTypedArray(),
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam?) {
                                    beforeFunc?.let { func ->
                                        val luaParam = CoerceJavaToLua.coerce(param)

                                        // Allow parameter modification in Lua
                                        val modifiedParam = func.call(luaParam)

                                        // Replace original parameters if modified
                                        if (!modifiedParam.isnil()) {
                                            if (modifiedParam.istable()) {
                                                val table = modifiedParam.checktable()
                                                val argsTable = table.get("args")

                                                if (argsTable.istable()) {
                                                    val argsModified = argsTable.checktable()
                                                    for (i in 1..argsModified.length()) {
                                                        // Convert Lua parameters back to Java types
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

                                        // Allow modification of return value in Lua
                                        val modifiedResult = func.call(luaParam)

                                        // Replace original return value if modified
                                        if (!modifiedResult.isnil()) {
                                            param?.result = fromLuaValue(modifiedResult)
                                        }
                                    }
                                }
                            }
                        )

                    } else if (classNameOrClass.isuserdata(Class::class.java)) { // If first arg is a Class object
                        val hookClass = classNameOrClass.touserdata(Class::class.java) as Class<*>

                        // Dynamic parameter type handling
                        val paramTypes = mutableListOf<Class<*>>()

                        // Collect parameter types (starting from index 2)
                        for (i in 2 until args.narg() - 1) {
                            val param = args.arg(i)
                            when {
                                param.isstring() -> {
                                    // Support various type conversions
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
                                }
                                // Can expand more type handling here
                                else -> {
                                    throw IllegalArgumentException("Unsupported parameter type: ${param.type()}")
                                }
                            }
                        }

                        val beforeFunc = args.optfunction(args.narg() - 1, null)
                        val afterFunc = args.optfunction(args.narg(), null)

                        XposedHelpers.findAndHookConstructor(
                            hookClass,
                            *paramTypes.toTypedArray(),
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam?) {
                                    beforeFunc?.let { func ->
                                        val luaParam = CoerceJavaToLua.coerce(param)

                                        // Allow parameter modification in Lua
                                        val modifiedParam = func.call(luaParam)

                                        // Replace original parameters if modified
                                        if (!modifiedParam.isnil()) {
                                            if (modifiedParam.istable()) {
                                                val table = modifiedParam.checktable()
                                                val argsTable = table.get("args")

                                                if (argsTable.istable()) {
                                                    val argsModified = argsTable.checktable()
                                                    for (i in 1..argsModified.length()) {
                                                        // Convert Lua parameters back to Java types
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

                                        // Allow modification of return value in Lua
                                        val modifiedResult = func.call(luaParam)

                                        // Replace original return value if modified
                                        if (!modifiedResult.isnil()) {
                                            param?.result = fromLuaValue(modifiedResult)
                                        }
                                    }
                                }
                            }
                        )
                    }

                    return NIL

                } catch (e: Exception) {
                    println("Hook constructor error: ${e.message}")
                    e.printStackTrace()
                    return NIL
                }
            }
        }





        globals["createProxy"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                // --- CORRECTED ARGUMENT PARSING ---

                // Argument 1: Interface Class (as Java Class userdata at index 1)
                val interfaceClass = args.checkuserdata(1, Class::class.java) as Class<*>
                if (!interfaceClass.isInterface) {
                    // Use argerror for better Lua-side error reporting
                    LuaValue.argerror(1, "Expected an interface class")
                }

                // Argument 2: Lua Table with method implementations (at index 2)
                val implementationTable = args.checktable(2)

                // Argument 3: Optional ClassLoader (as ClassLoader userdata at index 3)
                // Default to the interface's classloader if argument 3 is nil or absent.
                val classLoaderOrDefault = interfaceClass.classLoader // Define default value
                val classLoader = args.optuserdata(
                    3, // Index of the Lua argument
                    ClassLoader::class.java, // Expected Java type
                    classLoaderOrDefault // Default value if arg 3 is nil/absent
                ) as ClassLoader? // Cast the result (Object) to ClassLoader?
                    ?: classLoaderOrDefault // Use default if optuserdata returned null (which it shouldn't with a non-null default, but good practice)

                // Ensure we have a non-null loader (should always be true here)
                val finalClassLoader = classLoader ?: classLoaderOrDefault

                // --- END OF CORRECTIONS ---

                println("createProxy: Interface=${interfaceClass.name}, Loader=${finalClassLoader}")

                // Create the InvocationHandler
                val handler =
                    LuaInvocationHandler(implementationTable) // Assumes LuaInvocationHandler class is defined elsewhere

                try {
                    // Create the proxy instance using the specified class loader
                    val proxyInstance = Proxy.newProxyInstance(
                        finalClassLoader, // Use the resolved class loader
                        arrayOf(interfaceClass),
                        handler
                    )

                    // Return the proxy instance coerced to a LuaValue
                    return CoerceJavaToLua.coerce(proxyInstance)

                } catch (e: Exception) {
                    println("createProxy error: ${e.message}")
                    e.printStackTrace() // Log the full stack trace for debugging
                    // Consider returning a LuaError for better script handling
                    // throw LuaError("Failed to create proxy: ${e.message}")
                    return LuaValue.NIL
                }
            }
        }

//// 专门为EditText添加文本变化监听器
//        globals["registerTextWatcher"] = object : VarArgFunction() {
//            override fun invoke(args: Varargs): LuaValue {
//                try {
//                    if (args.narg() < 2) {
//                        return LuaValue.error("至少需要两个参数: EditText控件和回调函数")
//                    }
//
//                    // 获取EditText控件
//                    val editText = args.arg(1).touserdata(View::class.java) as? EditText
//                        ?: return LuaValue.error("第一个参数必须是EditText对象")
//
//                    // 获取回调函数
//                    val beforeTextChangedCallback = args.optfunction(2, null)
//                    val onTextChangedCallback = args.optfunction(3, null)
//                    val afterTextChangedCallback = args.optfunction(4, null)
//
//                    // 创建文本监听器
//                    val textWatcher = object : TextWatcher {
//                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
//                            beforeTextChangedCallback?.let {
//                                try {
//                                    val luaText = LuaValue.valueOf(s.toString())
//                                    val luaStart = LuaValue.valueOf(start)
//                                    val luaCount = LuaValue.valueOf(count)
//                                    val luaAfter = LuaValue.valueOf(after)
//                                    it.call(luaText, luaStart, luaCount, luaAfter)
//                                } catch (e: Exception) {
//                                    Log.e("LuaXposed", "beforeTextChanged回调执行错误: ${e.message}")
//                                    e.printStackTrace()
//                                }
//                            }
//                        }
//
//                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
//                            onTextChangedCallback?.let {
//                                try {
//                                    val luaText = LuaValue.valueOf(s.toString())
//                                    val luaStart = LuaValue.valueOf(start)
//                                    val luaBefore = LuaValue.valueOf(before)
//                                    val luaCount = LuaValue.valueOf(count)
//                                    it.call(luaText, luaStart, luaBefore, luaCount)
//                                } catch (e: Exception) {
//                                    Log.e("LuaXposed", "onTextChanged回调执行错误: ${e.message}")
//                                    e.printStackTrace()
//                                }
//                            }
//                        }
//
//                        override fun afterTextChanged(s: Editable?) {
//                            afterTextChangedCallback?.let {
//                                try {
//                                    val luaText = LuaValue.valueOf(s.toString())
//                                    it.call(luaText)
//                                } catch (e: Exception) {
//                                    Log.e("LuaXposed", "afterTextChanged回调执行错误: ${e.message}")
//                                    e.printStackTrace()
//                                }
//                            }
//                        }
//                    }
//
//                    // 添加监听器
//                    editText.addTextChangedListener(textWatcher)
//
//                    return LuaValue.TRUE
//                } catch (e: Exception) {
//                    Log.e("LuaXposed", "设置文本监听器错误: ${e.message}")
//                    e.printStackTrace()
//                    return LuaValue.error("设置文本监听器错误: ${e.message}")
//                }
//            }
//        }
//
//// 为ListView/RecyclerView添加条目点击监听
//        globals["registerItemClickListener"] = object : VarArgFunction() {
//            override fun invoke(args: Varargs): LuaValue {
//                try {
//                    if (args.narg() < 2) {
//                        return LuaValue.error("至少需要两个参数: ListView/RecyclerView控件和回调函数")
//                    }
//
//                    val view = args.arg(1).touserdata(View::class.java)
//                    val callback = args.checkfunction(2)
//
//                    when (view) {
//                        is ListView -> {
//                            view.setOnItemClickListener { parent, v, position, id ->
//                                try {
//                                    val luaParent = CoerceJavaToLua.coerce(parent)
//                                    val luaView = CoerceJavaToLua.coerce(v)
//                                    val luaPosition = LuaValue.valueOf(position)
//                                    val luaId = LuaValue.valueOf(id)
//                                    callback.call(luaParent, luaView, luaPosition, luaId)
//                                } catch (e: Exception) {
//                                    Log.e("LuaXposed", "列表项点击回调执行错误: ${e.message}")
//                                    e.printStackTrace()
//                                }
//                            }
//                        }
//                        is RecyclerView -> {
//                            // 为RecyclerView添加点击监听需要自定义ItemTouchListener
//                            view.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
//                                val gestureDetector = GestureDetector(view.context, object : GestureDetector.SimpleOnGestureListener() {
//                                    override fun onSingleTapUp(e: MotionEvent): Boolean {
//                                        val childView = view.findChildViewUnder(e.x, e.y)
//                                        if (childView != null) {
//                                            val position = view.getChildAdapterPosition(childView)
//                                            try {
//                                                val luaRecyclerView = CoerceJavaToLua.coerce(view)
//                                                val luaChildView = CoerceJavaToLua.coerce(childView)
//                                                val luaPosition = LuaValue.valueOf(position)
//                                                callback.call(luaRecyclerView, luaChildView, luaPosition)
//                                            } catch (e: Exception) {
//                                                Log.e("LuaXposed", "RecyclerView项点击回调执行错误: ${e.message}")
//                                                e.printStackTrace()
//                                            }
//                                            return true
//                                        }
//                                        return false
//                                    }
//                                })
//
//                                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
//                                    gestureDetector.onTouchEvent(e)
//                                    return false
//                                }
//
//                                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
//
//                                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
//                            })
//                        }
//                        else -> {
//                            return LuaValue.error("不支持的控件类型，只支持ListView或RecyclerView")
//                        }
//                    }
//
//                    return LuaValue.TRUE
//                } catch (e: Exception) {
//                    Log.e("LuaXposed", "设置列表项点击监听器错误: ${e.message}")
//                    e.printStackTrace()
//                    return LuaValue.error("设置列表项点击监听器错误: ${e.message}")
//                }
//            }
//        }
//
//// 综合手势检测器
//        globals["registerGestureDetector"] = object : VarArgFunction() {
//            override fun invoke(args: Varargs): LuaValue {
//                try {
//                    if (args.narg() < 2) {
//                        return LuaValue.error("至少需要两个参数: View控件和回调表")
//                    }
//
//                    val view = args.arg(1).touserdata(View::class.java) as? View
//                        ?: return LuaValue.error("第一个参数必须是View对象")
//
//                    val callbackTable = args.checktable(2)
//
//                    // 从表中获取各种回调函数
//                    val onDownCallback = callbackTable.get("onDown").optfunction(null)
//                    val onShowPressCallback = callbackTable.get("onShowPress").optfunction(null)
//                    val onSingleTapUpCallback = callbackTable.get("onSingleTapUp").optfunction(null)
//                    val onScrollCallback = callbackTable.get("onScroll").optfunction(null)
//                    val onLongPressCallback = callbackTable.get("onLongPress").optfunction(null)
//                    val onFlingCallback = callbackTable.get("onFling").optfunction(null)
//                    val onDoubleTapCallback = callbackTable.get("onDoubleTap").optfunction(null)
//
//                    // 创建手势检测器
//                    val gestureDetector = GestureDetector(view.context, object : GestureDetector.SimpleOnGestureListener() {
//                        override fun onDown(e: MotionEvent): Boolean {
//                            return onDownCallback?.let {
//                                try {
//                                    val luaEvent = CoerceJavaToLua.coerce(e)
//                                    val result = it.call(luaEvent)
//                                    if (result.isboolean()) result.toboolean() else super.onDown(e)
//                                } catch (e: Exception) {
//                                    Log.e("LuaXposed", "onDown回调执行错误: ${e.message}")
//                                    e.printStackTrace()
//                                    super.onDown(e)
//                                }
//                            } ?: super.onDown(e)
//                        }
//
//                        override fun onShowPress(e: MotionEvent) {
//                            onShowPressCallback?.let {
//                                try {
//                                    val luaEvent = CoerceJavaToLua.coerce(e)
//                                    it.call(luaEvent)
//                                } catch (e: Exception) {
//                                    Log.e("LuaXposed", "onShowPress回调执行错误: ${e.message}")
//                                    e.printStackTrace()
//                                }
//                            } ?: super.onShowPress(e)
//                        }
//
//                        override fun onSingleTapUp(e: MotionEvent): Boolean {
//                            return onSingleTapUpCallback?.let {
//                                try {
//                                    val luaEvent = CoerceJavaToLua.coerce(e)
//                                    val result = it.call(luaEvent)
//                                    if (result.isboolean()) result.toboolean() else super.onSingleTapUp(e)
//                                } catch (e: Exception) {
//                                    Log.e("LuaXposed", "onSingleTapUp回调执行错误: ${e.message}")
//                                    e.printStackTrace()
//                                    super.onSingleTapUp(e)
//                                }
//                            } ?: super.onSingleTapUp(e)
//                        }
//
//                        override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
//                            return onScrollCallback?.let {
//                                try {
//                                    val luaE1 = CoerceJavaToLua.coerce(e1)
//                                    val luaE2 = CoerceJavaToLua.coerce(e2)
//                                    val luaDistanceX = LuaValue.valueOf(distanceX.toDouble())
//                                    val luaDistanceY = LuaValue.valueOf(distanceY.toDouble())
//                                    val result = it.call(luaE1, luaE2, luaDistanceX, luaDistanceY)
//                                    if (result.isboolean()) result.toboolean() else super.onScroll(e1, e2, distanceX, distanceY)
//                                } catch (e: Exception) {
//                                    Log.e("LuaXposed", "onScroll回调执行错误: ${e.message}")
//                                    e.printStackTrace()
//                                    super.onScroll(e1, e2, distanceX, distanceY)
//                                }
//                            } ?: super.onScroll(e1, e2, distanceX, distanceY)
//                        }
//
//                        override fun onLongPress(e: MotionEvent) {
//                            onLongPressCallback?.let {
//                                try {
//                                    val luaEvent = CoerceJavaToLua.coerce(e)
//                                    it.call(luaEvent)
//                                } catch (e: Exception) {
//                                    Log.e("LuaXposed", "onLongPress回调执行错误: ${e.message}")
//                                    e.printStackTrace()
//                                }
//                            } ?: super.onLongPress(e)
//                        }
//
//                        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
//                            return onFlingCallback?.let {
//                                try {
//                                    val luaE1 = CoerceJavaToLua.coerce(e1)
//                                    val luaE2 = CoerceJavaToLua.coerce(e2)
//                                    val luaVelocityX = LuaValue.valueOf(velocityX.toDouble())
//                                    val luaVelocityY = LuaValue.valueOf(velocityY.toDouble())
//                                    val result = it.call(luaE1, luaE2, luaVelocityX, luaVelocityY)
//                                    if (result.isboolean()) result.toboolean() else super.onFling(e1, e2, velocityX, velocityY)
//                                } catch (e: Exception) {
//                                    Log.e("LuaXposed", "onFling回调执行错误: ${e.message}")
//                                    e.printStackTrace()
//                                    super.onFling(e1, e2, velocityX, velocityY)
//                                }
//                            } ?: super.onFling(e1, e2, velocityX, velocityY)
//                        }
//
//                        override fun onDoubleTap(e: MotionEvent): Boolean {
//                            return onDoubleTapCallback?.let {
//                                try {
//                                    val luaEvent = CoerceJavaToLua.coerce(e)
//                                    val result = it.call(luaEvent)
//                                    if (result.isboolean()) result.toboolean() else super.onDoubleTap(e)
//                                } catch (e: Exception) {
//                                    Log.e("LuaXposed", "onDoubleTap回调执行错误: ${e.message}")
//                                    e.printStackTrace()
//                                    super.onDoubleTap(e)
//                                }
//                            } ?: super.onDoubleTap(e)
//                        }
//                    })
//
//                    // 启用双击检测
//                    gestureDetector.setIsLongpressEnabled(true)
//
//                    // 添加触摸监听
//                    view.setOnTouchListener { v, event ->
//                        gestureDetector.onTouchEvent(event)
//                        true
//                    }
//
//                    return LuaValue.TRUE
//                } catch (e: Exception) {
//                    Log.e("LuaXposed", "设置手势检测器错误: ${e.message}")
//                    e.printStackTrace()
//                    return LuaValue.error("设置手势检测器错误: ${e.message}")
//                }
//            }
//        }

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


    class LuaInvocationHandler(private val luaTable: LuaTable) : InvocationHandler {

        override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
            val methodName = method.name
            val luaFunction = luaTable.get(methodName)

            // Debugging log
            // println("Proxy invoked: Method=$methodName, LuaFunction found=${!luaFunction.isnil()}")

            return if (luaFunction.isfunction()) {
                try {
                    // Convert Java arguments to LuaValue varargs
                    val luaArgs = convertArgsToLuaValues(args)
                    // Call the Lua function
                    // Note: Lua functions typically don't receive 'self' implicitly when called from Java proxies.
                    // We pass only the method arguments.
                    val result = luaFunction.invoke(luaArgs) // Use invoke for Varargs

                    // Convert the Lua return value back to the expected Java type
                    CoerceLuaToJava.coerce(
                        result.arg1(),
                        method.returnType
                    ) // result is Varargs, get first value
                } catch (e: LuaError) {
                    println("LuaError during proxy invocation of '$methodName': ${e.message}")
                    // Decide how to handle Lua errors. Re-throwing might be appropriate.
                    throw RuntimeException("Lua execution failed for method $methodName", e)
                } catch (e: Exception) {
                    println("Exception during proxy invocation of '$methodName': ${e.message}")
                    throw RuntimeException(
                        "Java exception during proxy method $methodName",
                        e
                    ) // Re-throw
                }
            } else {
                // Handle standard Object methods or missing implementations
                when (methodName) {
                    "toString" -> "LuaProxy<${proxy.javaClass.interfaces.firstOrNull()?.name ?: "UnknownInterface"}>@${
                        Integer.toHexString(
                            hashCode()
                        )
                    }"

                    "hashCode" -> luaTable.hashCode() // Or System.identityHashCode(proxy)? Or handler's hashcode? Be consistent.
                    "equals" -> proxy === args?.get(0) // Standard proxy equality check
                    else -> {
                        // No Lua function found for this method
                        println("Warning: No Lua implementation found for proxied method: $methodName")
                        // Return default value based on return type, or throw exception
                        if (method.returnType == Void.TYPE) {
                            null // Return null for void methods
                        } else if (method.returnType.isPrimitive) {
                            // Return default primitive values (0, false)
                            when (method.returnType) {
                                Boolean::class.javaPrimitiveType -> false
                                Char::class.javaPrimitiveType -> '\u0000'
                                Byte::class.javaPrimitiveType -> 0.toByte()
                                Short::class.javaPrimitiveType -> 0.toShort()
                                Int::class.javaPrimitiveType -> 0
                                Long::class.javaPrimitiveType -> 0L
                                Float::class.javaPrimitiveType -> 0.0f
                                Double::class.javaPrimitiveType -> 0.0
                                else -> throw UnsupportedOperationException("Unsupported primitive return type: ${method.returnType.name}")
                            }
                        } else {
                            // Return null for object return types
                            null
                            // Alternatively, throw an exception:
                            // throw UnsupportedOperationException("No Lua implementation for method: $methodName")
                        }
                    }
                }
            }
        }

        // Helper function to convert Java args array to LuaValue Varargs
        private fun convertArgsToLuaValues(javaArgs: Array<Any?>?): Varargs {
            if (javaArgs == null || javaArgs.isEmpty()) {
                return LuaValue.NONE
            }
            val luaArgs = javaArgs.map { CoerceJavaToLua.coerce(it) }.toTypedArray()
            // Important: Use varargsOf, not listOf, to create Varargs correctly
            return LuaValue.varargsOf(luaArgs)
        }
    }

}
