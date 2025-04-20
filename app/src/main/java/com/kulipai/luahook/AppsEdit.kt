package com.kulipai.luahook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kulipai.luahook.adapter.SymbolAdapter
import com.kulipai.luahook.adapter.ToolAdapter
import com.topjohnwu.superuser.Shell


class AppsEdit : AppCompatActivity() {

    //ui绑定区
    private val toolbar: MaterialToolbar by lazy { findViewById(R.id.toolbar) }
    private val editor: LuaEditor by lazy { findViewById(R.id.editor) }
    private val fab: FloatingActionButton by lazy { findViewById(R.id.fab) }
    private val rootLayout: CoordinatorLayout by lazy { findViewById(R.id.main) }
    private val bottomSymbolBar: LinearLayout by lazy { findViewById(R.id.bottomBar) }
    private val symbolRecyclerView: RecyclerView by lazy { findViewById(R.id.symbolRecyclerView) }
    private val ToolRec: RecyclerView by lazy { findViewById(R.id.toolRec) }


    //全局变量
    private lateinit var currentPackageName: String
    private lateinit var appName: String


    companion object {
        const val PREFS_NAME = "apps"
    }

    override fun onStop() {
        super.onStop()
        savePrefs(currentPackageName, editor.text.toString())
    }

    override fun onPause() {
        super.onPause()
        savePrefs(currentPackageName, editor.text.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_apps_edit)
        setSupportActionBar(toolbar)


        //窗口处理
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


        //接收传递信息
        val intent = getIntent()
        if (intent != null) {
            currentPackageName = intent.getStringExtra("packageName").toString()
            appName = intent.getStringExtra("appName").toString()
//            toolbar.title = appName
            title = appName


        }

        val symbols =
            listOf(
                "log",
                "lp",
                "(",
                ")",
                "\"",
                ":",
                ",",
                "=",
                "[",
                "]",
                "+",
                "-",
                "{",
                "}",
                "?",
                "!",

                )

        symbolRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        symbolRecyclerView.adapter = SymbolAdapter(symbols, editor)


        val tool =
            listOf(
                "Hook方法",
                "Hook构造",
                "方法签名"
            )

        ToolRec.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        ToolRec.adapter = ToolAdapter(tool, editor, this)


        val prefs = getSharedPreferences(PREFS_NAME, MODE_WORLD_READABLE)

        val script = prefs.getString(currentPackageName, "")

        editor.setText(script)

        fab.setOnClickListener {
            savePrefs(currentPackageName, editor.text.toString())
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
        }


    }

    //菜单
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {

        menu?.add(0, 0, 0, "Run")
            ?.setIcon(R.drawable.play_arrow_24px)
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

        menu?.add(0, 1, 0, "Undo")
            ?.setIcon(R.drawable.undo_24px)
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

        menu?.add(0, 2, 0, "Redo")
            ?.setIcon(R.drawable.redo_24px)
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu?.add(0, 3, 0, resources.getString(R.string.format))
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 4, 0, resources.getString(R.string.log))  //LogCat
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 5, 0, resources.getString(R.string.manual))
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 9, 0, "搜索")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            0 -> {
                savePrefs(currentPackageName, editor.text.toString())
//                operateAppAdvanced(this,currentPackageName)
                Shell.cmd("am force-stop $currentPackageName").exec()
                launchApp(this, currentPackageName)
                true

            }

            1 -> {
                // "Undo"
                editor.undo()
                true
            }

            2 -> {
                // "Redo"
                editor.redo()
                true
            }

            3 -> {
                // 格式化
                editor.format()
                true
            }

            4 -> {
                //LogCat
                val intent = Intent(this, LogCatActivity::class.java)
                startActivity(intent)
                true
            }

            5 -> {
                //示例
                val intent = Intent(this, Manual::class.java)
                startActivity(intent)
                true
            }

            9->{
                editor.search()
                true
            }


            else -> false
        }
    }


    // 写入 SharedPreferences 并修改权限
    fun savePrefs(packageName: String, text: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
        prefs.edit().apply {
            putString(packageName, text)
            apply()
        }
    }


    fun operateAppAdvanced(context: Context, packageName: String) {
        Shell.getShell { shell ->
            if (shell.isRoot) {
                // 1. 强制停止应用 (可选)
                val forceStopResult = Shell.cmd("am force-stop $packageName").exec()


                // 2. 使用 pm dump 查找主 Activity
                val dumpResult =
                    Shell.cmd("pm dump $packageName | grep -E \"android\\.intent\\.action\\.MAIN.*category android\\.intent\\.category\\.LAUNCHER\" -B 1")
                        .exec()
                if (dumpResult.out.isNotEmpty()) {
                    val outputLines = dumpResult.out
                    var activityName: String? = null

                    // 查找包含 android:name 的行
                    for (line in outputLines) {
                        val nameRegex = Regex("""android:name="([^"]+)"""")
                        val matchResult = nameRegex.find(line)
                        if (matchResult != null) {
                            activityName = matchResult.groupValues[1]
                            break
                        }
                    }

                    if (!activityName.isNullOrEmpty()) {
                        // 构建 ComponentName
                        val componentName = if (activityName.startsWith(".")) {
                            ComponentName(packageName, packageName + activityName)
                        } else {
                            ComponentName(packageName, activityName)
                        }

                        // 3. 使用 am start -n 启动主 Activity
                        val startIntent = Intent(Intent.ACTION_MAIN)
                        startIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                        startIntent.component = componentName
                        startIntent.flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED

                        try {
                            context.startActivity(startIntent)
                            Log.d(
                                "RootShell",
                                "Successfully launched $packageName with component: $componentName"
                            )
                        } catch (e: Exception) {
                            Log.e("RootShell", "Failed to launch $packageName: ${e.message}")
                            Toast.makeText(
                                context,
                                "启动应用失败: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                    } else {
                        Log.w(
                            "RootShell",
                            "Could not find the main activity for $packageName in pm dump output."
                        )
                        Toast.makeText(context, "找不到应用的主 Activity", Toast.LENGTH_SHORT)
                            .show()
                    }

                } else {
                    Log.w("RootShell", "Failed to execute pm dump or no matching output found.")
                    Toast.makeText(context, "无法获取应用信息", Toast.LENGTH_SHORT).show()
                }

            } else {
                Log.w("RootShell", "Root access denied.")
                Toast.makeText(context, "需要 Root 权限才能执行此操作", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchApp(context: Context, packageName: String): Boolean {
        val packageManager = context.packageManager
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            try {
                context.startActivity(launchIntent)
                Log.i("LaunchApp", "Successfully launched $packageName")
                true
            } catch (e: Exception) {
                Log.e("LaunchApp", "Failed to launch $packageName: ${e.message}")
                false
            }
        } else {
            Log.w("LaunchApp", "Launch intent not found for $packageName")
            false
        }
    }


}