package com.kulipai.luahook

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kulipai.luahook.adapter.SymbolAdapter
import kotlin.system.exitProcess

class EditActivity : AppCompatActivity() {


    private fun getAppVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName!!
        } catch (e: PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }


    fun getAppVersionCode(context: Context): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.longVersionCode // 注意这里使用 longVersionCode，在旧版本中是 versionCode (Int)
        } catch (e: PackageManager.NameNotFoundException) {
            -1 // 或者其他表示未找到的数值
        }
    }

    fun getDynamicColor(context: Context, @AttrRes colorAttributeResId: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(colorAttributeResId, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(context, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivity(intent)
    }


    @SuppressLint("SetTextI18n")
    private fun showLsposedInfoDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_info, null)

        val appLogoImageView: ImageView = view.findViewById(R.id.app_logo)
        val appNameTextView: TextView = view.findViewById(R.id.app_name)
        val appVersionTextView: TextView = view.findViewById(R.id.app_version)
        val appDescriptionTextView: TextView = view.findViewById(R.id.app_description)

        // 设置应用信息
        appLogoImageView.setImageResource(R.drawable.logo)
        appNameTextView.text = "LuaHook"
        appVersionTextView.text = getAppVersionName(this)

        // 构建包含可点击链接的 SpannableString (与之前的示例代码相同)
        val descriptionText =
            resources.getString(R.string.find_us) + "\n" + resources.getString(R.string.find_us2)

        val spannableString = SpannableString(descriptionText)

        // 设置 GitHub 链接
        val githubStartIndex = descriptionText.indexOf("GitHub")
        val githubEndIndex = githubStartIndex + "GitHub".length
        if (githubStartIndex != -1) {
            val clickableSpanGithub = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    openUrl("https://github.com/KuLiPai/LuaHook")
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = getDynamicColor(
                        this@EditActivity,
                        com.google.android.material.R.attr.colorPrimary
                    )
                    ds.isUnderlineText = true
                }
            }
            spannableString.setSpan(
                clickableSpanGithub,
                githubStartIndex,
                githubEndIndex,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // 设置 Telegram 链接
        val telegramStartIndex = descriptionText.indexOf("Telegram")
        val telegramEndIndex = telegramStartIndex + "Telegram".length
        if (telegramStartIndex != -1) {
            val clickableSpanTelegram = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    openUrl("https://t.me/LuaXposed")
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = getDynamicColor(
                        this@EditActivity,
                        com.google.android.material.R.attr.colorPrimary
                    )
                    ds.isUnderlineText = true
                }
            }
            spannableString.setSpan(
                clickableSpanTelegram,
                telegramStartIndex,
                telegramEndIndex,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // 设置 QQ 链接
//        val qqStartIndex = descriptionText.indexOf("QQ")
//        val qqEndIndex = descriptionText.indexOf(" ", qqStartIndex) + " ".length
//        if (qqStartIndex != -1 && qqEndIndex != -1) {
//            val clickableSpanQq = object : ClickableSpan() {
//                override fun onClick(widget: View) {
//                    openUrl("https://qm.qq.com/cgi-bin/qm/qr?k=your_qq_key")
//                }
//
//                @SuppressLint("ResourceType")
//                override fun updateDrawState(ds: TextPaint) {
//                    super.updateDrawState(ds)
//                    ds.color = getDynamicColor(
//                        this@MainActivity,
//                        com.google.android.material.R.attr.colorPrimary
//                    )
//                    ds.isUnderlineText = true
//
//                }
//            }
//            spannableString.setSpan(
//                clickableSpanQq,
//                qqStartIndex,
//                qqEndIndex,
//                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
//            )
//        }

        // 设置 TextView 的文本和 MovementMethod
        appDescriptionTextView.text = spannableString
        appDescriptionTextView.movementMethod = LinkMovementMethod.getInstance()

        // 使用 MaterialAlertDialogBuilder 构建并显示对话框
        MaterialAlertDialogBuilder(this)
            .setView(view)
            .show()
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
        menu?.add(0, 3, 0, resources.getString(R.string.format))
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 4, 0, resources.getString(R.string.log))  //LogCat
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 5, 0, resources.getString(R.string.manual))
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu?.add(0, 6, 0, resources.getString(R.string.about))
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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

            6 -> {
                showLsposedInfoDialog()
                true
            }

            else -> false
        }
    }

    private val editor: LuaEditor by lazy { findViewById(R.id.editor) }
    private val fab: FloatingActionButton by lazy { findViewById(R.id.fab) }
    private val toolbar: MaterialToolbar by lazy { findViewById(R.id.toolbar) }
    private val rootLayout: CoordinatorLayout by lazy { findViewById(R.id.main) }
    private val bottomSymbolBar: LinearLayout by lazy { findViewById(R.id.bottomBar) }

    private lateinit var defaultLogo: Drawable

    companion object {
        const val PREFS_NAME = "xposed_prefs"
    }


    // 写入 SharedPreferences 并修改权限
    fun savePrefs(context: Context, text: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, MODE_WORLD_READABLE)
        prefs.edit().apply {
            putString("lua", text)
            apply()

        }
    }

    fun readPrefs(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, MODE_WORLD_READABLE)
        return prefs.getString("lua", "") ?: ""
    }

    override fun onStop() {
        super.onStop()
        savePrefs(this@EditActivity, editor.text.toString())
    }

    override fun onPause() {
        super.onPause()
        savePrefs(this@EditActivity, editor.text.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.edit)
        setSupportActionBar(toolbar)

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
        val symbolRecyclerView: RecyclerView = findViewById(R.id.symbolRecyclerView)
        symbolRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        symbolRecyclerView.adapter = SymbolAdapter(symbols, editor)


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
                    0,
                    navigationBarInsets.right,
                    0
                )

            } else {
                // 输入法不可见时，设置底部内边距以避免内容被导航栏遮挡
                view.setPadding(
                    navigationBarInsets.left,
                    0,
                    navigationBarInsets.right,
                    navigationBarInsets.bottom
                )
            }

            insets
        }

        // 确保在布局稳定后请求 WindowInsets，以便监听器能够正确工作
        ViewCompat.requestApplyInsets(rootLayout)

        var luaScript = readPrefs(this)
        if (luaScript == "") {
            var lua = """
        """.trimIndent()
            savePrefs(this, lua)
        }

        editor.setText(luaScript)

        fab.setOnClickListener {
            savePrefs(this@EditActivity, editor.text.toString())
            Toast.makeText(this, resources.getString(R.string.save_ok), Toast.LENGTH_SHORT).show()
        }


    }


}