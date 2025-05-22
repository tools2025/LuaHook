import android.content.pm.PackageManager
import com.kulipai.luahook.util.d
import com.kulipai.luahook.util.e
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.luaj.LuaError
import org.luaj.LuaTable
import org.luaj.LuaValue
import org.luaj.Varargs
import org.luaj.lib.OneArgFunction
import org.luaj.lib.TwoArgFunction
import org.luaj.lib.VarArgFunction
import org.luaj.lib.jse.CoerceJavaToLua
import org.luaj.lib.jse.CoerceLuaToJava
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy


class HookLib(private val lpparam: LoadPackageParam) : OneArgFunction() {

    override fun call(globals: LuaValue): LuaValue {


        globals["pcall"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                if (args.narg() < 1 || !args.arg1().isfunction()) {
                    return varargsOf(
                        FALSE,
                        valueOf("first argument must be a function")
                    )
                }

                val func = args.arg1().checkfunction()
                val funcArgs = args.subargs(2) // Get all arguments after the function

                try {
                    // Execute the function with provided arguments
                    val result = func.invoke(funcArgs)

                    // Return true followed by any results from the function
                    return varargsOf(TRUE, result)
                } catch (e: Exception) {
                    // Catch any Lua or Java exceptions
                    val errorMessage = e.message ?: "unknown error"

                    // Return false followed by the error message
                    return varargsOf(FALSE, valueOf(errorMessage))
                }
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
                    ("findClass error: ${e.message}").d()
                    NIL
                }
            }
        }

        globals["invoke"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                try {
                    val obj = args.arg(1)  // Lua 传入的第一个参数
                    val methodName = args.checkjstring(2) // 方法名
                    var isStatic: Boolean = false

                    // 获取 Java 对象
                    val targetObject: Any? = when {
                        obj.isuserdata(XC_MethodHook.MethodHookParam::class.java) -> {
                            val param =
                                obj.touserdata(XC_MethodHook.MethodHookParam::class.java) as XC_MethodHook.MethodHookParam
                            param.thisObject // 获取 thisObject
                        }

                        obj.isuserdata(Class::class.java) -> {
                            isStatic = true
                            obj.touserdata(Class::class.java)
                        }

                        obj.isuserdata(Any::class.java) -> obj.touserdata(Any::class.java)
                        else -> throw IllegalArgumentException("invoke 需要 Java 对象")
                    }

                    // 收集参数
                    val javaParams = mutableListOf<Any?>()
                    for (i in 3..args.narg()) {
                        javaParams.add(fromLuaValue(args.arg(i)))
                    }

                    var result: Any
                    if (isStatic) {
                        // 反射调用方法
                        result = XposedHelpers.callStaticMethod(
                            targetObject as Class<*>?,
                            methodName,
                            *javaParams.toTypedArray()
                        )

                    } else {
                        // 反射调用方法
                        result = XposedHelpers.callMethod(
                            targetObject,
                            methodName,
                            *javaParams.toTypedArray()
                        )
                    }

                    return CoerceJavaToLua.coerce(result) // 把 Java 结果返回 Lua
                } catch (e: Exception) {
                    println("invoke error: ${e.message}")
                    e.printStackTrace()
                }
                return NIL
            }
        }



        /**
         * 安全地将 LuaValue 转换为 Java Class 对象
         * 处理普通 Class 和 JavaClass（接口或类）的情况
         */
        fun safeToJavaClass(luaValue: LuaValue): Class<*>? {
            if (!luaValue.isuserdata()) return null

            val userData = luaValue.touserdata()

            // 直接是 Class 类型的情况
            if (userData is Class<*>) {
                return userData
            }

            // JavaClass 或其他包装类型的情况
            return try {
                // 尝试获取内部字段
                val possibleFields = listOf("clazz", "class", "jclass", "classObject")
                for (fieldName in possibleFields) {
                    try {
                        val field = userData.javaClass.getDeclaredField(fieldName)
                        field.isAccessible = true
                        val fieldValue = field.get(userData)
                        if (fieldValue is Class<*>) {
                            return fieldValue
                        }
                    } catch (e: NoSuchFieldException) {
                        // 继续尝试下一个字段
                    }
                }

                // 尝试调用可能的方法
                val possibleMethods = listOf("getClassObject", "toClass", "asClass", "getJavaClass")
                for (methodName in possibleMethods) {
                    try {
                        val method = userData.javaClass.getMethod(methodName)
                        val result = method.invoke(userData)
                        if (result is Class<*>) {
                            return result
                        }
                    } catch (e: NoSuchMethodException) {
                        // 继续尝试下一个方法
                    }
                }

                // 记录反射信息以便调试
                "无法转换 ${userData.javaClass.name} 为 Class 对象".d()

                // 最后尝试通过名称获取
                try {
                    // 尝试获取类名
                    val nameMethod = userData.javaClass.getMethod("getName")
                    val className = nameMethod.invoke(userData) as? String
                    if (className != null) {
                        return Class.forName(className)
                    }
                } catch (e: Exception) {
                    // 忽略并返回 null
                }

                null
            } catch (t: Throwable) {
                t.toString().e()
                null
            }
        }


        // 封装获取构造函数的 Lua 函数
        globals["getConstructor"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (args.narg() < 1 || !args.arg(1).isuserdata(Class::class.java)) {
                    return error("Usage: getConstructor(class, argType1, argType2, ...)")
                }

                val clazz = args.arg(1).touserdata(Class::class.java) as Class<*>
                val paramTypes = mutableListOf<Class<*>>()

                for (i in 2..args.narg()) {
                    try {
                        if (args.arg(i).isstring()) {
                            val typeName = args.checkjstring(i)
                            val type = parseType(typeName, lpparam.classLoader)
                            paramTypes.add(type as Class<*>)
                        } else if (args.arg(i).isuserdata()) {
                            // 使用安全的转换方法替代直接 as 转换
                            val classObj = safeToJavaClass(args.arg(i))
                            if (classObj != null) {
                                paramTypes.add(classObj)
                            } else {
                                "getConstructor: 无法将参数 $i 转换为 Class".e()
                                return error("getConstructor: 无法将参数 $i 转换为 Class")
                            }
                        } else {
                            "getConstructor: 参数 $i 类型不支持".e()
                            return error("getConstructor: 参数类型不支持")
                        }
                    } catch (e: ClassNotFoundException) {
                        "getConstructor: 参数错误".e()
                        return error("getConstructor: 参数错误")
                    } catch (e: Exception) {
                        e.toString().e()
                        return error("getConstructor: ${e.message}")
                    }
                }

                return try {
                    val constructor = clazz.getConstructor(*paramTypes.toTypedArray())
                    CoerceJavaToLua.coerce(constructor)
                } catch (e: NoSuchMethodException) {
                    NIL
                } catch (e: Exception) {
                    e.toString().e()
                    error("getConstructor: ${e.message}")
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
//                    println("newInstance error: Class not found: ${e.message}")
                    e.toString().d()
                    return NIL
                } catch (e: NoSuchMethodException) {
//                    println("newInstance error: Constructor not found for ${args.arg(1)} with parameter types:")
                    e.toString().d()
                    return NIL
                } catch (e: InstantiationException) {
//                    println("newInstance error: Cannot instantiate ${args.arg(1)}: ${e.message}")
                    e.toString().d()
                    return NIL
                } catch (e: IllegalAccessException) {
//                    println("newInstance error: Illegal access to constructor of ${args.arg(1)}: ${e.message}")
                    e.toString().d()
                    return NIL
                } catch (e: InvocationTargetException) {
//                    println(
//                        "newInstance error: Exception occurred during constructor invocation of ${
//                            args.arg(
//                                1
//                            )
//                        }: ${e.targetException?.message}"
//                    )
                    e.toString().d()
                    e.targetException?.toString()?.d()
                    return NIL
                } catch (e: IllegalArgumentException) {
//                    println("newInstance error: Illegal arguments provided: ${e.message}")
                    e.toString().d()
                    return NIL
                } catch (e: Exception) {
//                    println("newInstance error: An unexpected error occurred: ${e.message}")
                    e.toString().d()
                    return NIL
                }
            }
        }


//        globals["callMethod"] = object : VarArgFunction() {
//            override fun invoke(args: Varargs): LuaValue {
//                try {
//                    if (args.narg() < 1 || !args.arg(1).isuserdata()) {
//                        throw IllegalArgumentException("First argument must be a Method object")
//                    }
//
//                    // Extract the Method object from the JavaInstance wrapper
//                    val methodArg = args.arg(1)
//                    val methodObj = methodArg.touserdata()
//                    val method: Method
//
//                    // Handle different ways the Method might be wrapped
//                    if (methodObj is Method) {
//                        method = methodObj
//                    } else {
//                        ("Object is not a Method: ${methodObj?.javaClass?.name}").e()
//                        return NIL
//                    }
//
//                    // Get method information
//                    val isStatic = Modifier.isStatic(method.modifiers)
//                    val declaringClass = method.declaringClass
//                    val methodName = method.name
//
//                    // Convert parameters to Java objects
//                    val paramValues = if (isStatic) {
//                        // For static methods, all arguments after the method are parameters
//                        Array<Any?>(args.narg() - 1) { i ->
//                            fromLuaValue(args.arg(i + 2))
//                        }
//                    } else {
//                        // For instance methods, need at least one more argument for the instance
//                        if (args.narg() < 2) {
//                            "callMethod param1 is not method".e()
//                            return NIL
//                        }
//
//                        // First argument after method is the object instance
//                        val instance = fromLuaValue(args.arg(2))
//
//                        // Remaining arguments are method parameters
//                        val params = Array<Any?>(args.narg() - 2) { i ->
//                            fromLuaValue(args.arg(i + 3))
//                        }
//
//                        // Use XposedHelpers.callMethod
//                        val result = XposedHelpers.callMethod(instance, methodName, *params)
//                        return CoerceJavaToLua.coerce(result)
//                    }
//
//                    // Call static method using XposedHelpers
//                    val result = XposedHelpers.callStaticMethod(declaringClass, methodName, *paramValues)
//                    return CoerceJavaToLua.coerce(result)
//
//                } catch (e: Exception) {
//                    ("callMethod error: ${e.message}").e()
////                    e.printStackTrace()
//                    return NIL
//                }
//            }
//        }


        globals["callMethod"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                try {
                    if (args.narg() < 1 || !args.arg(1).isuserdata()) {
                        throw IllegalArgumentException("First argument must be a Method object")
                    }

                    // Extract the Method object from the JavaInstance wrapper
                    val methodArg = args.arg(1)
                    val methodObj = methodArg.touserdata()
                    val method: Method

                    // Handle different ways the Method might be wrapped
                    if (methodObj is Method) {
                        method = methodObj

                    } else {
                        "callMethod param1 is not method".e()
                        return NIL
                    }

                    // Check if the method is static
                    val isStatic = Modifier.isStatic(method.modifiers)

                    // For static methods, we can call directly with all arguments
                    // For instance methods, the second argument should be the instance

                    val result: Any?

                    if (isStatic) {
                        // Convert all Lua arguments to Java objects
                        val javaArgs = Array<Any?>(args.narg() - 1) { i ->
                            fromLuaValue(args.arg(i + 2))
                        }

                        // Call the static method
                        result = method.invoke(null, *javaArgs)
                    } else {
                        // Need at least 2 arguments for instance methods
                        if (args.narg() < 2) {
                            ("Instance method requires an object instance as second parameter").e()
                            return NIL
                        }

                        // Get the instance object
                        val instance = fromLuaValue(args.arg(2))

                        // Convert remaining Lua arguments to Java objects
                        val javaArgs = Array<Any?>(args.narg() - 2) { i ->
                            fromLuaValue(args.arg(i + 3))
                        }

                        // Call the instance method
                        result = method.invoke(instance, *javaArgs)
                    }

                    // Convert the result back to Lua
                    return CoerceJavaToLua.coerce(result)

                } catch (e: Exception) {
                    ("callMethod error: ${e.message}").e()
                    return NIL
                }
            }
        }


        globals["hook"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                try {
                    val classNameOrClassOrMethod = args.arg(1)
                    var classLoader: ClassLoader? = null
                    val methodName: String

                    if (classNameOrClassOrMethod.isstring()) { /////////string,
                        classLoader =
                            args.optuserdata(2, lpparam.javaClass.classLoader) as ClassLoader
                        val className = classNameOrClassOrMethod.tojstring()
                        methodName = args.checkjstring(3)


                        val paramTypes = mutableListOf<Class<*>>()

                        if (args.arg(4).istable()) {
                            for (key in args.arg(4).checktable().keys()) {
                                val param = args.arg(4).checktable().get(key)

                                when {

                                    param.isstring() -> {
                                        // 支持更多类型转换
                                        val typeStr = param.tojstring()
                                        val type = parseType(typeStr, classLoader)
                                        paramTypes.add(type!!)

                                    }
                                    // 可以扩展更多类型的处理
                                    else -> {
                                        val classObj = safeToJavaClass(param)
                                        if (classObj != null) {
                                            paramTypes.add(classObj)
                                        }
                                    }
                                }
                            }
                        } else {
                            // 收集参数类型
                            for (i in 4 until args.narg() - 1) {
                                val param = args.arg(i)
                                when {

                                    param.isstring() -> {
                                        // 支持更多类型转换
                                        val typeStr = param.tojstring()
                                        val type = parseType(typeStr, classLoader)
                                        paramTypes.add(type!!)

                                    }
                                    // 可以扩展更多类型的处理
                                    else -> {
                                        val classObj = safeToJavaClass(param)
                                        if (classObj != null) {
                                            paramTypes.add(classObj)
                                        }
                                    }
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


                    }

                    //todo 删除或者解决不传入classloader也能加载类的问题
//                    else if (classNameOrClassOrMethod.isuserdata(Class::class.java)) {   ///classs
//                        var hookClass =
//                            classNameOrClassOrMethod.touserdata(Class::class.java) as Class<*>
//                        methodName = args.checkjstring(2)
//
//                        // 动态处理参数类型
//                        val paramTypes = mutableListOf<Class<*>>()
//                        val luaParams = mutableListOf<LuaValue>()
//
//                        // 收集参数类型
//                        for (i in 3 until args.narg() - 1) {
//                            val param = args.arg(i)
//                            when {
//                                param.isstring() -> {
//                                    // 支持更多类型转换
//                                    val typeStr = param.tojstring()
//                                    val type = parseType(typeStr)
//
////                                    val type = when (typeStr) {
////                                        "int" -> Int::class.javaPrimitiveType
////                                        "long" -> Long::class.javaPrimitiveType
////                                        "boolean" -> Boolean::class.javaPrimitiveType
////                                        "double" -> Double::class.javaPrimitiveType
////                                        "float" -> Float::class.javaPrimitiveType
////                                        "String" -> String::class.java
////                                        "int[]" -> FloatArray::class.java
////                                        else -> Class.forName(typeStr, true, classLoader)
////                                    }
//                                    paramTypes.add(type!!)
//                                    luaParams.add(param)
//                                }
//                                // 可以扩展更多类型的处理
//                                else -> {
//                                    throw IllegalArgumentException("Unsupported parameter type: ${param.type()}")
//                                }
//                            }
//                        }
//
//                        val beforeFunc = args.optfunction(args.narg() - 1, null)
//                        val afterFunc = args.optfunction(args.narg(), null)
//
//                        XposedHelpers.findAndHookMethod(
//                            hookClass,
//                            methodName,
//                            *paramTypes.toTypedArray(),
//                            object : XC_MethodHook() {
//                                override fun beforeHookedMethod(param: MethodHookParam?) {
//                                    beforeFunc?.let { func ->
//                                        val luaParam = CoerceJavaToLua.coerce(param)
//
//                                        // 允许在Lua中修改参数
//                                        val modifiedParam = func.call(luaParam)
//
//                                        // 如果Lua函数返回了修改后的参数，则替换原参数
//                                        if (!modifiedParam.isnil()) {
//                                            // 假设返回的是一个表，包含修改后的参数
//                                            if (modifiedParam.istable()) {
//                                                val table = modifiedParam.checktable()
//                                                val argsTable = table.get("args")
//
//                                                if (argsTable.istable()) {
//                                                    val argsModified = argsTable.checktable()
//                                                    for (i in 1..argsModified.length()) {
//                                                        // 将Lua的参数转换回Java类型
//                                                        param?.args?.set(
//                                                            i - 1,
//                                                            fromLuaValue(argsModified.get(i))
//                                                        )
//                                                    }
//                                                }
//                                            }
//                                        }
//                                    }
//                                }
//
//                                override fun afterHookedMethod(param: MethodHookParam?) {
//                                    afterFunc?.let { func ->
//                                        val luaParam = CoerceJavaToLua.coerce(param)
//
//                                        // 允许在Lua中修改返回值
//                                        val modifiedResult = func.call(luaParam)
//
//                                        // 如果Lua函数返回了修改后的结果，则替换原返回值
//                                        if (!modifiedResult.isnil()) {
//
//                                            param?.result = fromLuaValue(modifiedResult)
//                                        }
//                                    }
//                                }
//                            }
//                        )
//
//                    }
                    else if (classNameOrClassOrMethod.isuserdata(Method::class.java)) {   ///method


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


                    }

                    PackageManager.PERMISSION_GRANTED

                } catch (e: Exception) {
                    println("Hook error: ${e.message}")
                    e.printStackTrace()
                }
                return NIL
            }
        }


//        globals["hookm"] = object : VarArgFunction() {
//            override fun invoke(args: Varargs): LuaValue {
//                try {
//
//                    if (!args.arg(1).isuserdata(Method::class.java)) {
//                        "hook参数非method".d()
//                    }
//                    val method = args.arg(1).touserdata(Method::class.java) as Method
//                    val beforeFunc = args.optfunction(2, null)
//                    val afterFunc = args.optfunction(3, null)
//
//                    XposedBridge.hookMethod(
//                        method,
//                        object : XC_MethodHook() {
//                            override fun beforeHookedMethod(param: MethodHookParam?) {
//                                beforeFunc?.let { func ->
//                                    val luaParam = CoerceJavaToLua.coerce(param)
//
//                                    // Allow modifying parameters in Lua
//                                    val modifiedParam = func.call(luaParam)
//
//                                    // If Lua function returned modified parameters, replace original parameters
//                                    if (!modifiedParam.isnil()) {
//                                        // Assuming return is a table containing modified parameters
//                                        if (modifiedParam.istable()) {
//                                            val table = modifiedParam.checktable()
//                                            val argsTable = table.get("args")
//
//                                            if (argsTable.istable()) {
//                                                val argsModified = argsTable.checktable()
//                                                for (i in 1..argsModified.length()) {
//                                                    // Convert Lua parameters back to Java types
//                                                    param?.args?.set(
//                                                        i - 1,
//                                                        fromLuaValue(argsModified.get(i))
//                                                    )
//                                                }
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//
//                            override fun afterHookedMethod(param: MethodHookParam?) {
//                                afterFunc?.let { func ->
//                                    val luaParam = CoerceJavaToLua.coerce(param)
//
//                                    // Allow modifying return value in Lua
//                                    val modifiedResult = func.call(luaParam)
//
//                                    // If Lua function returned modified result, replace original result
//                                    if (!modifiedResult.isnil()) {
//                                        param?.result = fromLuaValue(modifiedResult)
//                                    }
//                                }
//                            }
//                        }
//                    )
//
//                    return TRUE
//
//                } catch (e: Exception) {
//                    ("HookMethod error: ${e.message}").d()
//                    e.printStackTrace()
//                    return FALSE
//                }
//            }
//        }


        globals["hookAll"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                try {

                    if (!args.arg(1).isuserdata(Class::class.java)) {
                        "hook参数非Class".d()
                    }
                    val clazz = args.arg(1).touserdata(Class::class.java) as Class<*>
                    val method = args.arg(2).toString()

                    val beforeFunc = args.optfunction(3, null)
                    val afterFunc = args.optfunction(4, null)

                    XposedBridge.hookAllMethods(
                        clazz,
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
                    ("HookAllMethod error: ${e.message}").d()
                    e.printStackTrace()
                    return FALSE
                }
            }
        }





        globals["hookcotr"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                try {
                    val classNameOrClass = args.arg(1)
                    var classLoader: ClassLoader? = null

                    if (classNameOrClass.isstring()) { // If first arg is a string (class name)
                        classLoader =
                            args.optuserdata(2, lpparam.javaClass.classLoader) as ClassLoader
                        val className = classNameOrClass.tojstring()

                        // Dynamic parameter type handling
                        val paramTypes = mutableListOf<Class<*>>()

                        if (args.arg(3).istable()) {
                            for (key in args.arg(3).checktable().keys()) {
                                val param = args.arg(3).checktable().get(key)
                                when {
                                    param.isstring() -> {
                                        // Support various type conversions
                                        val typeStr = param.tojstring()
                                        val type = parseType(typeStr, classLoader)

                                        paramTypes.add(type!!)
                                    }
                                    // Can expand more type handling here
                                    else -> {
                                        val classObj = safeToJavaClass(param)
                                        if (classObj != null) {
                                            paramTypes.add(classObj)
                                        }
                                    }
                                }
                            }
                        } else {
                            // Collect parameter types (starting from index 3)
                            for (i in 3 until args.narg() - 1) {
                                val param = args.arg(i)
                                when {
                                    param.isstring() -> {
                                        // Support various type conversions
                                        val typeStr = param.tojstring()
                                        val type = parseType(typeStr, classLoader)
                                        paramTypes.add(type!!)
                                    }

                                    else -> {
                                        val classObj = safeToJavaClass(param)
                                        if (classObj != null) {
                                            paramTypes.add(classObj)
                                        }
                                    }
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

                    } else if (classNameOrClass.isuserdata(Class::class.java)) {


                        val classs = classNameOrClass as Class<*>

                        // Dynamic parameter type handling
                        val paramTypes = mutableListOf<Class<*>>()

                        if (args.arg(2).istable()) {
                            for (key in args.arg(2).checktable().keys()) {
                                val param = args.arg(2).checktable().get(key)
                                when {
                                    param.isstring() -> {
                                        // Support various type conversions
                                        val typeStr = param.tojstring()
                                        val type = parseType(typeStr, lpparam.classLoader)

//                                    val type = when (typeStr) {
//                                        "int" -> Int::class.javaPrimitiveType
//                                        "long" -> Long::class.javaPrimitiveType
//                                        "boolean" -> Boolean::class.javaPrimitiveType
//                                        "double" -> Double::class.javaPrimitiveType
//                                        "float" -> Float::class.javaPrimitiveType
//                                        "String" -> String::class.java
//                                        else -> Class.forName(typeStr, true, classLoader)
//                                    }
                                        paramTypes.add(type!!)
                                    }
                                    // Can expand more type handling here
                                    else -> {
                                        val classObj = safeToJavaClass(param)
                                        if (classObj != null) {
                                            paramTypes.add(classObj)
                                        }
                                    }
                                }
                            }
                        } else {

                            // Collect parameter types (starting from index 3)
                            for (i in 2 until args.narg() - 1) {
                                val param = args.arg(i)
                                when {
                                    param.isstring() -> {
                                        // Support various type conversions
                                        val typeStr = param.tojstring()
                                        val type = parseType(typeStr, lpparam.classLoader)

//                                    val type = when (typeStr) {
//                                        "int" -> Int::class.javaPrimitiveType
//                                        "long" -> Long::class.javaPrimitiveType
//                                        "boolean" -> Boolean::class.javaPrimitiveType
//                                        "double" -> Double::class.javaPrimitiveType
//                                        "float" -> Float::class.javaPrimitiveType
//                                        "String" -> String::class.java
//                                        else -> Class.forName(typeStr, true, classLoader)
//                                    }
                                        paramTypes.add(type!!)
                                    }
                                    // Can expand more type handling here
                                    else -> {
                                        val classObj = safeToJavaClass(param)
                                        if (classObj != null) {
                                            paramTypes.add(classObj)
                                        }
                                    }
                                }
                            }
                        }


                        val beforeFunc = args.optfunction(args.narg() - 1, null)
                        val afterFunc = args.optfunction(args.narg(), null)

                        XposedHelpers.findAndHookConstructor(
                            classs,
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


    fun parseType(
        typeStr: String,
        classLoader: ClassLoader? = Thread.currentThread().contextClassLoader
    ): Class<*>? {
        val typeMap = mapOf(
            "int" to Int::class.javaPrimitiveType,
            "long" to Long::class.javaPrimitiveType,
            "boolean" to Boolean::class.javaPrimitiveType,
            "double" to Double::class.javaPrimitiveType,
            "float" to Float::class.javaPrimitiveType,
            "char" to Char::class.javaPrimitiveType,
            "byte" to Byte::class.javaPrimitiveType,
            "short" to Short::class.javaPrimitiveType,
            "String" to String::class.java
        )

        var baseType = typeStr.trim()
        var arrayDepth = 0

        // 计算数组维度
        while (baseType.endsWith("[]")) {
            arrayDepth++
            baseType = baseType.substring(0, baseType.length - 2).trim()
        }

        val baseClass = typeMap[baseType] ?: try {
            XposedHelpers.findClass(baseType, classLoader)
        } catch (e: ClassNotFoundException) {
            "参数错误".d()
            return null
        }

        // 构建数组类型
        var resultClass = baseClass
        repeat(arrayDepth) {
            resultClass = java.lang.reflect.Array.newInstance(resultClass, 0).javaClass
        }

        return resultClass
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
