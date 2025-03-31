package com.kulipai.luahook


import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.app.Application
import android.graphics.Color
import android.os.Build
import java.io.File
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDelegate.NightMode
import androidx.core.view.WindowCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.MaterialDynamicColors

class MainActivity : AppCompatActivity() {


    fun isNightMode(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }


    private val editor: LuaEditor by lazy { findViewById(R.id.editor) }
    private val fab: FloatingActionButton by lazy { findViewById(R.id.fab) }
    private val toolbar: MaterialToolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar) }


    companion object {
        const val PREFS_NAME = "xposed_prefs"
        const val TAG = "XposedModule"
    }




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

    override fun onStop() {
        super.onStop()
        savePrefs(this@MainActivity, editor.text.toString())
        makePrefsWorldReadable()
    }

    override fun onPause() {
        super.onPause()
        savePrefs(this@MainActivity, editor.text.toString())
        makePrefsWorldReadable()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyIfAvailable(this)
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        WindowCompat.setDecorFitsSystemWindows(window, false) // 允许内容绘制在系统栏后面
        setContentView(R.layout.activity_main)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }




        toolbar.menu.add( 0,1,0,"Undo")
            .setIcon(R.drawable.undo_24px)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)

        toolbar.menu.add(0,2,0,"Redo")
            .setIcon(R.drawable.redo_24px)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)

        toolbar.menu.add(0,3,0,"格式化")
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        toolbar.menu.add(0,4,0,"More")
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        toolbar.menu.add(0,5,0,"More")
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)


        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    // 处理 "Undo" 的点击事件
                    editor.undo()
                    true // 返回 true 表示事件已被处理
                }

                2 -> {
                    // 处理 "Redo" 的点击事件
                    editor.redo()
                    true
                }

                3 -> {
                    // 处理 "More" 子菜单项 1 的点击事件
                    editor.format()
                    true
                }

                4 -> {
                    // 处理 "More" 子菜单项 2 的点击事件
                    Toast.makeText(this, "More - Item 2 clicked", Toast.LENGTH_SHORT).show()
                    true
                }

                else -> false // 返回 false 表示事件未被处理，可以传递给其他监听器（如果存在）
            }
        }




        editor.setDark(isNightMode(this))

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