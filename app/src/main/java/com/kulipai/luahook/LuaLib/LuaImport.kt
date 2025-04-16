import org.luaj.vm2.*
import org.luaj.vm2.lib.*
import de.robv.android.xposed.XposedHelpers
import org.luaj.vm2.lib.jse.CoerceJavaToLua

class LuaImport(private val classLoader: ClassLoader,private val env:LuaValue) : OneArgFunction() {
    override fun call(classNameValue: LuaValue): LuaValue {
        return try {
            val className = classNameValue.checkjstring()
            val clazz = XposedHelpers.findClass(className, classLoader)
            val luaClass = CoerceJavaToLua.coerce(clazz)

            // 提取简名作为全局变量（例如 java.io.File -> File）
            val simpleName = className.substringAfterLast('.')
            env.set(simpleName, luaClass)
            luaClass
        } catch (e: Exception) {
            println("import error: ${e.message}")
            NIL
        }
    }


}
