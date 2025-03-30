package com.kulipai.luahook


import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import com.google.android.material.floatingactionbutton.FloatingActionButton


import java.io.File

class MainActivity : AppCompatActivity() {


    fun isNightMode(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }


    companion object {
        const val PREFS_NAME = "xposed_prefs"
        const val TAG = "XposedModule"
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        val editor = findViewById<LuaEditor>(R.id.editor)





        // 写入 SharedPreferences 并修改权限
        fun savePrefs(context: Context, text: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("lua", text)
                apply()

            }
        }

        fun readPrefs(context: Context): String {
            val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString("lua", "") ?: ""
        }

        if (isNightMode(this)) {
            editor.setDark(true)
        } else
        {
            editor.setDark(false)
        }
        var luaScript = readPrefs(this)
        if (luaScript == "") {
            makePrefsWorldReadable()
            var lua = """
            log(lpparam.packageName)
                package=lpparam.packageName
                if package == "com.ad2001.frida0x1" then
                    local MainActivity = findClass("com.ad2001.frida0x1.MainActivity",lpparam.classLoader)
                    log(MainActivity)
                    if MainActivity then
                        hook(MainActivity, "get_random",
                            function(param)
                                log("【Before get_random】参数: " .. tostring(param.args[1]))
                                --return { args = {"Modified Parameter"} }
                            end,
                            function(param)
                                log("【After get_random】返回值: " .. tostring(param.result))
                                --return 1
                            end
                        )

                         hook(MainActivity, "check", "int", "int",
                            function(param)
                                log("【Before check】参数: " .. tostring(param.args[1]) .. tostring(param.args[2]))
                                return { args = {1,6} }
                            end,
                            function(param)
                                log("【After check】参数: " .. tostring(param.result))
                            end
                        )
                        --[[
                        hook(MainActivity, "onCreate", "android.os.Bundle",
                            function(param)
                                log("【Before onCreate】")
                            end,
                            function(param)
                                log("【After onCreate】")
                                local activity = param.thisObject
                                log(activity)
                                local Toast = findClass("android.widget.Toast")
                                log(Toast)
                                --Toast.makeText(activity, "0x11 Hook成功！", Toast.LENGTH_SHORT):show()
                                --invoke(param,"check", 1, 6)
                            end
                        )
                        ]]

                    else
                        log("MainActivity class not found!")
                    end
                end
        """.trimIndent()
            savePrefs(this, lua)
        }

        editor.setText(luaScript)
        makePrefsWorldReadable()


        var fab = findViewById<FloatingActionButton>(R.id.fab)

        fab.setOnClickListener {
//            makePrefsWorldReadable()
            savePrefs(this@MainActivity, editor.text.toString())
            makePrefsWorldReadable()
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()

        }


    }


    private fun makePrefsWorldReadable() {
        try {
            // SharedPreferences 默认存储路径: /data/data/包名/shared_prefs/文件名.xml
            val prefsFile = File(applicationInfo.dataDir, "shared_prefs/$PREFS_NAME.xml")
            if (prefsFile.exists()) {
                Runtime.getRuntime().exec("chmod 666 ${prefsFile.absolutePath}")
                Log.d(TAG, "Prefs 文件已设置为全局可读: ${prefsFile.absolutePath}")
            } else {
                Log.d(TAG, "Prefs 文件不存在：${prefsFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "修改 SharedPreferences 权限失败", e)
        }
    }
}