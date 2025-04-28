import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.luaj.LuaTable
import org.luaj.LuaValue
import org.luaj.Varargs
import org.luaj.lib.VarArgFunction
import org.luaj.lib.jse.CoerceJavaToLua
import com.kulipai.luahook.util.d // 假设这是你的日志工具
import org.luaj.Globals
import java.lang.reflect.Modifier

/**
 * Lua资源访问桥接器
 * 提供一个Lua表，其中包含需要Context参数的资源访问函数。
 */
class LuaResourceBridge {

    // 创建并返回包含资源访问函数的Lua表
    fun createResourceAccessTable(): LuaTable {
        val resourceTable = LuaTable()

        // --- 定义需要 Context 参数的 Lua 函数 ---

        // 获取Drawable资源
        // Lua usage: resources.getDrawable(context, resourceName [, packageName])
        resourceTable.set("getDrawable", object : VarArgFunction() {
            override fun onInvoke(args: Varargs): Varargs {
                try {
                    // 参数1: Context, 参数2: resourceName (string), 参数3: packageName (string, optional)
                    val context = args.checkuserdata(1, Context::class.java) as Context
                    val resourceName = args.checkjstring(2)
                    // 如果提供了包名，则使用它；否则使用 context 的包名
                    val packageName = if (args.narg() > 2 && !args.isnil(3)) args.checkjstring(3) else context.packageName

//                    "Lua::getDrawable - Context: ${context.packageName}, ResName: $resourceName, PkgName: $packageName".d()

                    val drawable = ResourceHelper.getDrawableByName(context, resourceName, packageName)
                    return if (drawable != null) CoerceJavaToLua.coerce(drawable) else LuaValue.NIL
                } catch (e: Exception) {
                    "Lua::getDrawable - Error: ${e.message}".d()
                    e.printStackTrace()
                    return LuaValue.NIL
                }
            }
        })

        // 获取资源ID
        // Lua usage: resources.getResourceId(context, resourceName, resourceType)
        resourceTable.set("getResourceId", object : VarArgFunction() {
            override fun onInvoke(args: Varargs): Varargs {
                try {
                    // 参数1: Context, 参数2: resourceName (string), 参数3: resourceType (string)
                    val context = args.checkuserdata(1, Context::class.java) as Context
                    val resourceName = args.checkjstring(2)
                    val resourceType = args.checkjstring(3)

//                    "Lua::getResourceId - Context: ${context.packageName}, ResName: $resourceName, ResType: $resourceType".d()

                    val resId = ResourceHelper.getResourceId(context, resourceName, resourceType)
                    return LuaValue.valueOf(resId)
                } catch (e: Exception) {
                    "Lua::getResourceId - Error: ${e.message}".d()
                    e.printStackTrace()
                    return LuaValue.valueOf(0) // 返回0表示未找到或出错
                }
            }
        })

        // 获取字符串资源
        // Lua usage: resources.getString(context, resourceName)
        resourceTable.set("getString", object : VarArgFunction() {
            override fun onInvoke(args: Varargs): Varargs {
                try {
                    // 参数1: Context, 参数2: resourceName (string)
                    val context = args.checkuserdata(1, Context::class.java) as Context
                    val resourceName = args.checkjstring(2)

//                    "Lua::getString - Context: ${context.packageName}, ResName: $resourceName".d()

                    val str = ResourceHelper.getStringByName(context, resourceName)
                    return if (str != null) LuaValue.valueOf(str) else LuaValue.NIL
                } catch (e: Exception) {
                    "Lua::getString - Error: ${e.message}".d()
                    e.printStackTrace()
                    return LuaValue.NIL
                }
            }
        })

        // 获取颜色资源
        // Lua usage: resources.getColor(context, resourceName)
        resourceTable.set("getColor", object : VarArgFunction() {
            override fun onInvoke(args: Varargs): Varargs {
                try {
                    // 参数1: Context, 参数2: resourceName (string)
                    val context = args.checkuserdata(1, Context::class.java) as Context
                    val resourceName = args.checkjstring(2)

//                    "Lua::getColor - Context: ${context.packageName}, ResName: $resourceName".d()

                    // 注意：ResourceHelper.getColorByName 返回 Int
                    val colorInt = ResourceHelper.getColorByName(context, resourceName)
                    // 如果颜色是0，可能是没找到，也可能是颜色本身就是透明黑
                    // 这里直接返回值，由Lua端判断
                    return LuaValue.valueOf(colorInt)
                } catch (e: Exception) {
                    "Lua::getColor - Error: ${e.message}".d()
                    e.printStackTrace()
                    return LuaValue.valueOf(0) // 返回0表示出错
                }
            }
        })

        // 获取R类常量
        // Lua usage: resources.getRConstants(context, resourceType)
        resourceTable.set("getRConstants", object : VarArgFunction() {
            override fun onInvoke(args: Varargs): Varargs {
                try {
                    // 参数1: Context, 参数2: resourceType (string, e.g., "drawable", "string", "id")
                    val context = args.checkuserdata(1, Context::class.java) as Context
                    val resourceType = args.checkjstring(2)

//                    "Lua::getRConstants - Context: ${context.packageName}, ResType: $resourceType".d()

                    val constantsMap = ResourceHelper.getRClassConstants(context, resourceType)
                    val luaTable = LuaTable()
                    constantsMap.forEach { (key, value) ->
                        luaTable.set(key, LuaValue.valueOf(value))
                    }
                    return luaTable
                } catch (e: Exception) {
                    "Lua::getRConstants - Error: ${e.message}".d()
                    e.printStackTrace()
                    return LuaTable() // 返回空表表示出错
                }
            }
        })

        return resourceTable
    }

    /**
     * 将资源访问表注册到 Lua 环境中
     * @param globals Lua 全局环境
     * @param tableName 在Lua中暴露的表名，例如 "resources"
     */
    fun registerTo(globals: Globals, tableName: String = "resources") {
        globals.set(tableName, createResourceAccessTable())
//        "LuaResourceBridge registered as '$tableName' in Lua globals.".d()
    }
}

/**
 * 资源访问辅助类 (保持不变，因为它已经接受Context作为参数)
 * 注意：已将日志从 .d() 改为更通用的打印或移除，因为Helper不应强依赖特定日志库
 */
object ResourceHelper {

    fun getDrawableByName(context: Context, resourceName: String, packageName: String? = null): Drawable? {
        try {
            val targetPackageName = packageName ?: context.packageName
            val resources = if (targetPackageName != context.packageName) {
                // 尝试获取其他应用的资源，需要权限或在特定环境（如Xposed）下才能成功
                try {
                    context.packageManager.getResourcesForApplication(targetPackageName)
                } catch (e: Exception) {
                    println("ResourceHelper: Failed to get resources for package $targetPackageName - ${e.message}")
                    return null // 获取外部包资源失败
                }
            } else {
                context.resources
            }

            val resId = resources.getIdentifier(resourceName, "drawable", targetPackageName)

            if (resId == 0) {
                println("ResourceHelper: Drawable resource ID not found for '$resourceName' in package '$targetPackageName'")
                return null
            }

            // 使用兼容性方法获取Drawable
            return androidx.core.content.ContextCompat.getDrawable(context, resId)
            /* // 如果不想依赖 androidx.core
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                resources.getDrawable(resId, context.theme)
            } else {
                @Suppress("DEPRECATION")
                resources.getDrawable(resId)
            }
            */
        } catch (e: Resources.NotFoundException) {
            println("ResourceHelper: Drawable resource not found for '$resourceName' (ID: ${context.resources.getIdentifier(resourceName, "drawable", packageName ?: context.packageName)}) - ${e.message}")
            return null
        } catch (e: Exception) {
            println("ResourceHelper: Error getting drawable '$resourceName': ${e.javaClass.name}: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    fun getResourceId(context: Context, resourceName: String, resourceType: String): Int {
        return try {
            context.resources.getIdentifier(resourceName, resourceType, context.packageName)
        } catch (e: Exception) {
            println("ResourceHelper: Error getting resource ID for '$resourceName' (type: $resourceType): ${e.message}")
            e.printStackTrace()
            0 // Return 0 for failure
        }
    }

    fun getStringByName(context: Context, resourceName: String): String? {
        return try {
            val resId = getResourceId(context, resourceName, "string")
            if (resId == 0) {
                println("ResourceHelper: String resource ID not found for '$resourceName'")
                return null
            }
            context.resources.getString(resId)
        } catch (e: Resources.NotFoundException) {
            println("ResourceHelper: String resource not found for '$resourceName' (ID: ${getResourceId(context, resourceName, "string")}) - ${e.message}")
            return null
        } catch (e: Exception) {
            println("ResourceHelper: Error getting string '$resourceName': ${e.message}")
            e.printStackTrace()
            null
        }
    }

    fun getColorByName(context: Context, resourceName: String): Int {
        return try {
            val resId = getResourceId(context, resourceName, "color")
            if (resId == 0) {
                println("ResourceHelper: Color resource ID not found for '$resourceName'")
                return 0 // Return 0 for failure (ambiguous with black color)
            }
            // 使用兼容性方法获取Color
            androidx.core.content.ContextCompat.getColor(context, resId)
            /* // 如果不想依赖 androidx.core
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                 context.resources.getColor(resId, context.theme)
            } else {
                 @Suppress("DEPRECATION")
                 context.resources.getColor(resId)
            }
            */
        } catch (e: Resources.NotFoundException) {
            println("ResourceHelper: Color resource not found for '$resourceName' (ID: ${getResourceId(context, resourceName, "color")}) - ${e.message}")
            return 0 // Return 0 for failure
        } catch (e: Exception) {
            println("ResourceHelper: Error getting color '$resourceName': ${e.message}")
            e.printStackTrace()
            0 // Return 0 for failure
        }
    }

    fun getRClassConstants(context: Context, resourceType: String): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        try {
            val packageName = context.packageName
            // 加载内部类 R$resourceType (例如 com.example.app.R$drawable)
            val rClass = Class.forName("$packageName.R\$$resourceType")

            rClass.declaredFields.forEach { field ->
                // 只获取 public static final int 类型的字段
                if (Modifier.isStatic(field.modifiers) &&
                    Modifier.isFinal(field.modifiers) &&
                    Modifier.isPublic(field.modifiers) && // R文件中的资源ID通常是public的
                    field.type == Int::class.javaPrimitiveType) { // 确保是 int 类型
                    try {
                        // 无需 setAccessible(true)，因为它们是 public
                        val name = field.name
                        val value = field.getInt(null) // 获取静态字段的值
                        result[name] = value
                    } catch (e: Exception) {
                        // 忽略获取单个常量失败的情况
                        println("ResourceHelper: Error getting R constant value for '${field.name}': ${e.message}")
                    }
                }
            }
        } catch (e: ClassNotFoundException) {
            println("ResourceHelper: R class not found for type '$resourceType' in package '${context.packageName}': ${e.message}")
            // 这可能是因为类型名称错误（如 "layout" 而不是 "layouts"），或者R文件结构不同
        } catch (e: Exception) {
            println("ResourceHelper: Error getting R class constants for type '$resourceType': ${e.message}")
            e.printStackTrace()
        }
        return result
    }
}

// --- 如何在 Xposed 环境中使用 ---

/*
// 假设在你的 Xposed Hook 类中
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import android.app.Application
import android.content.Context
import org.luaj.Globals
import org.luaj.lib.jse.JsePlatform // 或者其他 Lua 环境

class MyXposedHook : IXposedHookLoadPackage {

    private var luaGlobals: Globals? = null
    private var hostAppContext: Context? = null

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 仅 Hook 目标应用
        if (lpparam.packageName != "com.target.app") {
            return
        }

        // 获取 Application Context 的一种方式
        XposedHelpers.findAndHookMethod(
            Application::class.java, "attach", Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    super.afterHookedMethod(param)
                    hostAppContext = param.args[0] as? Context ?: (param.thisObject as Application).applicationContext

                    if (hostAppContext != null && luaGlobals == null) {
                        // 初始化 Lua 环境 (这里仅为示例)
                        luaGlobals = JsePlatform.standardGlobals() // 或者你使用的特定Lua环境

                        // 创建并注册资源访问表
                        val resourceBridge = LuaResourceBridge()
                        resourceBridge.registerTo(luaGlobals!!) // 使用默认名称 "resources"

                        println("Xposed Hook: Lua environment initialized and resource bridge registered.")

                        // 在这里可以加载并执行你的 Lua 脚本
                        // luaGlobals.loadfile("path/to/your/script.lua").call()
                    }
                }
            }
        )

        // --- 示例：Hook某个方法，并在其中执行Lua代码 ---
        // XposedHelpers.findAndHookMethod("com.target.app.SomeClass", lpparam.classLoader, "someMethod", String::class.java, object : XC_MethodHook() {
        //     override fun beforeHookedMethod(param: MethodHookParam) {
        //         if (luaGlobals != null && hostAppContext != null) {
        //             // 准备传递给 Lua 的参数
        //             val luaContext = CoerceJavaToLua.coerce(hostAppContext)
        //             val someArg = LuaValue.valueOf(param.args[0] as String)
        //
        //             // 假设你的 Lua 脚本中有一个名为 onSomeMethodCalled 的函数
        //             val luaFunction = luaGlobals.get("onSomeMethodCalled")
        //             if (!luaFunction.isnil()) {
        //                 try {
        //                     // 调用 Lua 函数，传递 Context 和其他参数
        //                     luaFunction.call(luaContext, someArg)
        //                 } catch (e: Exception) {
        //                     println("Xposed Hook: Error calling Lua function 'onSomeMethodCalled': ${e.message}")
        //                     e.printStackTrace()
        //                 }
        //             }
        //         }
        //     }
        // })
    }
}
*/

// --- Lua 脚本示例 ---
/*
-- lua_script.lua

-- 假设 KT 层已经将 Context 对象传递给了这个脚本，或者通过某个函数可以获取
-- local hostContext = getHostContext() -- 这是一个假设的函数，你需要自己实现如何传递Context

-- 使用注册的 'resources' 表
if resources then
    print("KT Resources table found!")

    -- 示例1：获取字符串 (需要有效的 Context 对象)
    local appNameResId = resources.getResourceId(hostContext, "app_name", "string")
    if appNameResId and appNameResId ~= 0 then
        local appName = resources.getString(hostContext, "app_name")
        if appName then
            print("App Name: " .. appName)
        else
            print("Failed to get string for 'app_name'")
        end
    else
        print("Resource ID for 'app_name' not found.")
    end

    -- 示例2：获取 Drawable (需要有效的 Context 对象)
    -- 注意：直接在Lua中使用Drawable对象可能意义不大，除非你有特定的库或方法来处理它
    -- 通常更有用的是获取资源ID，然后在需要的地方使用它（例如，在其他Java调用中）
    local launcherIcon = resources.getDrawable(hostContext, "ic_launcher")
    if launcherIcon then
        print("Got launcher icon Drawable object: " .. tostring(launcherIcon))
        -- 你可能无法直接在纯Lua中显示这个Drawable
        -- 可以尝试获取它的类名等信息
        print("Icon Class: " .. launcherIcon:getClass():getName())
    else
        print("Failed to get drawable 'ic_launcher'")
    end

    -- 示例3：获取颜色 (需要有效的 Context 对象)
    local primaryColor = resources.getColor(hostContext, "colorPrimary")
    if primaryColor and primaryColor ~= 0 then -- 检查非0，因为0可能是有效颜色（透明黑）也可能是错误
        print(string.format("Primary Color (int): %d (Hex: #%08x)", primaryColor, primaryColor))
    else
        print("Failed to get color 'colorPrimary'")
    end

    -- 示例4：获取指定包名的资源 (假设目标包已安装且有权限访问)
    -- local otherAppIcon = resources.getDrawable(hostContext, "some_icon", "com.other.app")
    -- if otherAppIcon then
    --     print("Got icon from another app!")
    -- else
    --     print("Failed to get icon from com.other.app")
    -- end

    -- 示例5: 获取所有 drawable 资源的名称和 ID
    local drawableConstants = resources.getRConstants(hostContext, "drawable")
    if drawableConstants then
        print("Drawable Resources:")
        local count = 0
        for name, id in pairs(drawableConstants) do
            print(string.format("  %s = %d", name, id))
            count = count + 1
            if count > 10 then -- 限制打印数量
                 print("  ... and more")
                 break
            end
        end
    else
        print("Failed to get drawable constants.")
    end

else
    print("KT Resources table ('resources') not found in Lua globals!")
end

*/