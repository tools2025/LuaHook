package com.kulipai.luahook.Activity

import LuaDrawableLoader
import LuaHttp
import LuaImport
import LuaJson
import LuaResourceBridge
import LuaSharedPreferences
import Luafile
import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kulipai.luahook.R
import com.kulipai.luahook.util.d
import com.myopicmobile.textwarrior.common.LuaParser
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.luaj.Globals
import org.luaj.LuaValue
import org.luaj.android.loadlayout
import org.luaj.lib.OneArgFunction
import org.luaj.lib.ZeroArgFunction
import org.luaj.lib.jse.CoerceJavaToLua
import org.luaj.lib.jse.JsePlatform
import org.luckypray.dexkit.DexKitBridge
import top.sacz.xphelper.XpHelper
import top.sacz.xphelper.dexkit.DexFinder
import java.io.File
import java.io.StringReader

class ScriptSetActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)





        val path = intent.getStringExtra("path").toString()
        val script = read(path)
        val func = extractLuaFunctionByLabel(script, "set")
        func?.let {
            val callfunc = getFunctionName(func)
            CreateGlobals().load("$func\n$callfunc()").call()
        }


    }

    fun read(path: String): String {
        if (File(path).exists()) {
            return File(path).readText()
        }
        return ""
    }

    fun getFunctionName(functionString: String): String? {
        // 正则表达式匹配 'function ' 后面跟着的函数名
        // 函数名由字母、数字和下划线组成，且不能以数字开头
        val functionNamePattern = Regex("function\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(.*\\)")

        val matchResult = functionNamePattern.find(functionString)

        // 返回第一个捕获组，即函数名
        return matchResult?.groups?.get(1)?.value
    }

    fun extractLuaFunctionByLabel(luaCode: String, label: String): String? {
        // 构建一个正则表达式，用于匹配两种标签格式，后面跟着任意数量的空格或换行符，
        // 然后是 "function <函数名>()" 和函数体，直到匹配到对应的 "end"。
        // 注意：这里的正则表达式需要处理 Lua 的嵌套函数和 `end` 关键字的匹配。
        // 这通常需要更复杂的解析逻辑，但我们可以尝试用一个相对宽松的正则表达式来捕获。

        // 考虑到标签可以是 ::label::, @label@, ::label@, @label::, ::label, @label
        // 我们将标签匹配部分做得更灵活。
        // (?:[:@]{1,2}${label}[:@]{0,2}|[:@]{0,2}${label}[:@]{1,2})
        // 这是一个非捕获组，用于匹配标签。
        // ::label:: 允许 `::label` 或 `::label::`
        // @label@ 允许 `@label` 或 `@label@`
        // 总之，在传入的label字符串前后，可以有0-2个 ':' 或 '@' 字符

        val labelPattern = Regex(
            "(?m)^\\s*(?:::{1,2}|@){0,2}${Regex.escape(label)}(?:::{1,2}|@){0,2}\\s*\\n" + // 匹配标签行
                    "\\s*(function\\s+[a-zA-Z_][a-zA-Z0-9_]*\\s*\\(.*\\)\\s*\\n" + // 匹配函数声明
                    "(?:.*\\n)*?" + // 非贪婪匹配任意行，直到匹配到结束的 'end'
                    "^end\\s*\\n)", // 匹配顶层函数的 'end'，使用 ^ 和 $ 确保匹配整行
            setOf(RegexOption.MULTILINE,  RegexOption.DOT_MATCHES_ALL) // MULTILINE 让 ^ 和 $ 匹配行首行尾，DOT_ALL 让 . 匹配换行符
        )

        val matchResult = labelPattern.find(luaCode)

        return matchResult?.groups?.get(1)?.value // 捕获第一个捕获组，即整个函数字符串
    }





    fun CreateGlobals(): Globals {
        val globals: Globals = JsePlatform.standardGlobals()

        //加载Lua模块
        globals["this"] = CoerceJavaToLua.coerce(this)
        globals["enableEdgeToEdge"] = CoerceJavaToLua.coerce(object : ZeroArgFunction() {
            override fun call(): LuaValue? {
                enableEdgeToEdge()
                return NIL
            }
        })
        globals["activity"] = CoerceJavaToLua.coerce(this)
        globals["loadlayout"] = CoerceJavaToLua.coerce(loadlayout(this, globals))
        LuaJson().call(globals)
        LuaHttp().call(globals)
        Luafile().call(globals)
        LuaSharedPreferences().call(globals)
        globals["imports"] = LuaImport(this::class.java.classLoader!!, this::class.java.classLoader!!, globals)
        LuaResourceBridge().registerTo(globals)
        LuaDrawableLoader().registerTo(globals)
        return globals
    }

}