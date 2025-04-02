package com.kulipai.luahook


import LogAdapter
import ToolAdapter
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kulipai.luahook.util.RootHelper

import kotlin.system.exitProcess


class MainActivity : AppCompatActivity() {

    fun Context.softRestartApp(delayMillis: Long = 100) {
        val packageManager = packageManager
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(intent)
            // 结束当前应用的所有 Activity
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(0)
        }, delayMillis)
    }

    fun isNightMode(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    //菜单
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "Undo")
            ?.setIcon(R.drawable.undo_24px)
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)

        menu?.add(0, 2, 0, "Redo")
            ?.setIcon(R.drawable.redo_24px)
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)

        menu?.add(0, 3, 0, "格式化")
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 4, 0, "LogCat")
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 5, 0, "More")
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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
                val intent = Intent(this, LogCatActivity::class.java)
                startActivity(intent)
//                    // 处理 "More" 子菜单项 2 的点击事件
//                    val logs = LogcatHelper.getSystemLogsByTagSince("Demo", "04-01 13:08:00.000")
//                    logs.toString().d()
                true
            }

            5 -> {
                editor.startGotoMode()
                true
            }

            else -> false // 返回 false 表示事件未被处理，可以传递给其他监听器（如果存在）
        }
    }

    private val editor: LuaEditor by lazy { findViewById(R.id.editor) }
    private val fab: FloatingActionButton by lazy { findViewById(R.id.fab) }
    private val toolbar: MaterialToolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar) }
    private val rootLayout: CoordinatorLayout by lazy { findViewById<CoordinatorLayout>(R.id.main) }
    private val bottomSymbolBar: LinearLayout by lazy { findViewById<LinearLayout>(R.id.bottomBar) }


    companion object {
        const val PREFS_NAME = "xposed_prefs"
//        const val TAG = "XposedModule"
    }


    // 写入 SharedPreferences 并修改权限
    fun savePrefs(context: Context, text: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().apply {
            putString("lua", text)
            apply()

        }
    }

    fun readPrefs(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getString("lua", "") ?: ""
    }

    override fun onStop() {
        super.onStop()
        savePrefs(this@MainActivity, editor.text.toString())
//        makePrefsWorldReadable()
    }

    override fun onPause() {
        super.onPause()
        savePrefs(this@MainActivity, editor.text.toString())
//        makePrefsWorldReadable()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyIfAvailable(this)
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)


        setSupportActionBar(toolbar)


        val symbols =
            listOf("hook", "lp", "(", ")", "\"", ":", "=", "[", "]", "{", "}", "+", "-", "?", "!")
        val symbolRecyclerView: RecyclerView = findViewById(R.id.symbolRecyclerView)
        symbolRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        symbolRecyclerView.adapter = ToolAdapter(symbols, editor)




        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navigationBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())

            // 调整底部符号栏的位置，使其位于输入法上方
            bottomSymbolBar.translationY = -imeInsets.bottom.toFloat()
            fab.translationY = -imeInsets.bottom.toFloat()

            // 设置根布局的底部内边距
            if (imeInsets.bottom > 0) {
                // 输入法可见时，不需要额外的底部内边距来避免被导航栏遮挡，
                // 因为 bottomSymbolBar 已经移动到输入法上方
                view.setPadding(
                    navigationBarInsets.left,
                    statusBarInsets.top,
                    navigationBarInsets.right,
                    0
                )

            } else {
                // 输入法不可见时，设置底部内边距以避免内容被导航栏遮挡
                view.setPadding(
                    navigationBarInsets.left,
                    statusBarInsets.top,
                    navigationBarInsets.right,
                    navigationBarInsets.bottom
                )
            }

            insets
        }

        // 确保在布局稳定后请求 WindowInsets，以便监听器能够正确工作
        ViewCompat.requestApplyInsets(rootLayout)


        editor.setDark(isNightMode(this))

        var luaScript = readPrefs(this)
        if (luaScript == "") {
//            makePrefsWorldReadable()
            var lua = """
        """.trimIndent()
            savePrefs(this, lua)
        }

        editor.setText(luaScript)
//        makePrefsWorldReadable()


        fab.setOnClickListener {
//            makePrefsWorldReadable()
            savePrefs(this@MainActivity, editor.text.toString())
//            makePrefsWorldReadable()
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
            softRestartApp()

        }


    }

}