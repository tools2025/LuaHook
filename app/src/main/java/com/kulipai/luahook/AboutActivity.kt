package com.kulipai.luahook

import android.animation.ArgbEvaluator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import kotlin.math.abs

// 导入 OkHttp 相关类
import okhttp3.*
import java.io.IOException
// 导入 JSON 解析相关类
import org.json.JSONObject
import org.json.JSONException
import androidx.core.view.isVisible
import androidx.core.view.isInvisible
import androidx.core.net.toUri


class AboutActivity : AppCompatActivity() {
    private val toolbar: MaterialToolbar by lazy { findViewById(R.id.toolbar) }
    private val app_bar: AppBarLayout by lazy { findViewById(R.id.app_bar) }
    private val cardDonate: MaterialCardView by lazy { findViewById(R.id.card_donate) }
    private val appLogo: ImageView by lazy { findViewById(R.id.app_logo) }
//    开源协议部分
//    private val cardLicense: MaterialCardView by lazy { findViewById(R.id.card_license) }

    private val developerKuliPaiRow: MaterialCardView by lazy { findViewById(R.id.developer_kuli_pai_row) }
    private val padi: MaterialCardView by lazy { findViewById(R.id.padi) }
    private val developerAnotherRow: MaterialCardView by lazy { findViewById(R.id.developer_another_row) }

    private val cardCheckUpdate: MaterialCardView by lazy { findViewById(R.id.card_check_update) }
    private val tvUpdateStatus: MaterialTextView by lazy { findViewById(R.id.tv_update_status) }

    // 获取 NestedScrollView 内部的内容布局引用
    private val contentLayout: LinearLayout by lazy { findViewById(R.id.content_layout) }

    private val mainLayout: CoordinatorLayout by lazy { findViewById(R.id.main) }

    // 定义展开和折叠状态下的颜色
    private var expandedToolbarColor: Int = 0
    private var collapsedToolbarColor: Int = 0

    // 用于颜色插值器
    private val argbEvaluator = ArgbEvaluator()

    // OkHttp 客户端实例
    private val okHttpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        enableEdgeToEdge()

        // 获取展开和折叠状态的颜色
        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        expandedToolbarColor = ContextCompat.getColor(this, typedValue.resourceId)

        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        collapsedToolbarColor = ContextCompat.getColor(this, typedValue.resourceId)

        // 设置返回点击监听器
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // --- 设置 Toolbar 标题字符串 ---
        toolbar.title = getString(R.string.about)
        toolbar.setTitleTextColor(expandedToolbarColor)
        toolbar.navigationIcon?.let {
            DrawableCompat.setTint(it.mutate(), expandedToolbarColor)
        }

        // --- 开发者卡片点击事件 ---
        val kuliPaiGithubUrl = "https://github.com/KuLiPai"
        val anotherDeveloperGithubUrl = "https://github.com/Samzhaohx"
        val padiGithub = "https://github.com/paditianxiu"
        developerKuliPaiRow.setOnClickListener { openGithubUrl(kuliPaiGithubUrl) }
        developerAnotherRow.setOnClickListener { openGithubUrl(anotherDeveloperGithubUrl) }
        padi.setOnClickListener { openGithubUrl(padiGithub) }

        // --- 许可协议卡片功能 ---
//        cardLicense.setOnClickListener {
//            // TODO: 实现查看许可协议详情的逻辑，例如打开一个 WebView 或新的 Activity 显示许可文本
//            Toast.makeText(this, "许可协议卡片被点击了！请实现查看详情逻辑。", Toast.LENGTH_SHORT).show()
//        }

        // --- 检查更新卡片点击事件 ---
        cardCheckUpdate.setOnClickListener { checkUpdate() }

        // --- 赞赏卡片点击事件 ---
        cardDonate.setOnClickListener { showDonatePopup(it) }

        // Insets 处理
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            app_bar.setPadding(0, systemBars.top, 0, 0)

            val contentPaddingTop = contentLayout.paddingTop
            contentLayout.setPadding(
                systemBars.left,
                contentPaddingTop,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        // 用于控制 App Logo 透明度 和 Toolbar 内容颜色渐变
        app_bar.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val totalScrollRange = appBarLayout.totalScrollRange
            val currentScroll = abs(verticalOffset)

            val scrollRatio = if (totalScrollRange == 0) 0f else currentScroll.toFloat() / totalScrollRange.toFloat()

            // --- 控制 App Logo 的透明度 ---
            val appLogoAlpha = (1f - scrollRatio).coerceIn(0f, 1f)
            appLogo.alpha = appLogoAlpha

            if (appLogoAlpha == 0f && appLogo.isVisible) {
                appLogo.visibility = View.INVISIBLE
            } else if (appLogoAlpha > 0f && appLogo.isInvisible) {
                appLogo.visibility = View.VISIBLE
            }
            if (verticalOffset == 0) {
                appLogo.visibility = View.VISIBLE
            }

            // --- 控制 Toolbar 内容颜色渐变 ---
            val colorFadeStartRatio = 0.5f
            val colorFadeEndRatio = 0.8f

            val colorInterpolationRatio = if (scrollRatio <= colorFadeStartRatio) {
                0f
            } else if (scrollRatio >= colorFadeEndRatio) {
                1f
            } else {
                (scrollRatio - colorFadeStartRatio) / (colorFadeEndRatio - colorFadeStartRatio)
            }

            val interpolatedColor = argbEvaluator.evaluate(
                colorInterpolationRatio,
                expandedToolbarColor,
                collapsedToolbarColor
            ) as Int

            toolbar.setTitleTextColor(interpolatedColor)
            toolbar.navigationIcon?.let {
                DrawableCompat.setTint(it.mutate(), interpolatedColor)
            }
        }

        // TODO: 实现动态加载应用名称和版本号
        // 获取并显示当前版本号（如果需要显示在UI上）
        try {
            val currentVersion = packageManager.getPackageInfo(packageName, 0).versionName // 使用 0 作为 flags
            // 可以考虑将版本号显示在某个 TextView 中
            // val appVersionTextView: MaterialTextView? = findViewById(R.id.app_version)
            // appVersionTextView?.text = "V${currentVersion}"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            // 处理错误
        }
    }

    // --- 打开 GitHub URL 函数 ---
    private fun openGithubUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "无法打开链接，请确保已安装浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    // --- 检查更新函数 (使用 OkHttp 获取 GitHub Release) ---
    private fun checkUpdate() {
        // 立即更新状态文本
        tvUpdateStatus.text = "检查中..."

        // 替换为你的 GitHub 仓库信息
        val owner = "KuLiPai" // GitHub 用户名或组织名
        val repo = "LuaHook"   // GitHub 仓库名

        // GitHub API 获取最新 Release 的 URL
        val githubApiUrl = "https://api.github.com/repos/KuLiPai/LuaHook/releases/latest"

        val request = Request.Builder()
            .url(githubApiUrl)
            .build()

        // 使用 OkHttp 进行异步网络请求
        okHttpClient.newCall(request).enqueue(object : Callback {
            @SuppressLint("SetTextI18n")
            override fun onFailure(call: Call, e: IOException) {
                // 网络请求失败，切换到 UI 线程更新 UI
                runOnUiThread {
                    // 检查 Activity 是否仍然有效
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    tvUpdateStatus.text = "检查失败: ${e.message}" // 显示错误信息
                    Toast.makeText(this@AboutActivity, "检查更新失败，请稍后重试", Toast.LENGTH_SHORT).show()
                }
            }

            @SuppressLint("SetTextI18n")
            override fun onResponse(call: Call, response: Response) {
                // 网络请求成功，切换到 UI 线程更新 UI
                runOnUiThread {
                    // 检查 Activity 是否仍然有效
                    if (isFinishing || isDestroyed) return@runOnUiThread

                    if (response.isSuccessful) {
                        try {
                            val responseBody = response.body?.string()
                            if (responseBody != null) {
                                // 解析 JSON 响应
                                val jsonObject = JSONObject(responseBody)
                                val latestVersion = jsonObject.getString("tag_name").removePrefix("v") // 获取 tag_name，并移除前缀 'v' (如果存在)
                                val releasePageUrl = jsonObject.getString("html_url") // 获取 Release 页面的 URL

                                // 获取当前应用版本号
                                val currentVersion = try {
                                    packageManager.getPackageInfo(packageName, 0).versionName // 使用 0 作为 flags
                                } catch (e: PackageManager.NameNotFoundException) {
                                    e.printStackTrace()
                                    "0.0.0" // 获取失败时设为默认值
                                }

                                // 比较版本号
                                if (compareVersions(latestVersion, currentVersion ?: "0.0.0") > 0) {
                                    // 有新版本
                                    tvUpdateStatus.text = "有新版本: $latestVersion"
                                    AlertDialog.Builder(this@AboutActivity)
                                        .setTitle("发现新版本")
                                        .setMessage("当前版本: $currentVersion\n最新版本: $latestVersion\n\n是否前往 GitHub Release 页面查看并更新？")
                                        .setPositiveButton("前往 GitHub Release") { dialog, _ ->
                                            // 打开 Release 页面链接
                                            val intent = Intent(Intent.ACTION_VIEW,
                                                releasePageUrl.toUri())
                                            if (intent.resolveActivity(packageManager) != null) {
                                                startActivity(intent)
                                            } else {
                                                Toast.makeText(this@AboutActivity, "无法打开链接，请手动前往 ${releasePageUrl}", Toast.LENGTH_LONG).show()
                                            }
                                            dialog.dismiss()
                                        }
                                        .setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
                                        .show()
                                } else {
                                    // 已是最新版本
                                    tvUpdateStatus.text = "已是最新版本"
                                    Toast.makeText(this@AboutActivity, "已是最新版本", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                // 响应体为空
                                tvUpdateStatus.text = "检查失败: 无响应数据"
                                Toast.makeText(this@AboutActivity, "检查更新失败: 无响应数据", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: JSONException) {
                            // JSON 解析错误
                            tvUpdateStatus.text = "检查失败: 数据解析错误"
                            Toast.makeText(this@AboutActivity, "检查更新失败: 响应数据格式错误", Toast.LENGTH_SHORT).show()
                            e.printStackTrace()
                        } catch (e: Exception) {
                            // 其他可能的异常
                            tvUpdateStatus.text = "检查失败: ${e.message}"
                            Toast.makeText(this@AboutActivity, "检查更新时发生未知错误", Toast.LENGTH_SHORT).show()
                            e.printStackTrace()
                        } finally {
                            // OkHttp 的 enqueue 异步回调会自动关闭响应体，通常不需要手动调用 response.body?.close()
                        }
                    } else {
                        // HTTP 状态码不是 2xx
                        tvUpdateStatus.text = "检查失败: ${response.code}"
                        Toast.makeText(this@AboutActivity, "检查更新失败: HTTP错误 ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }


    // --- 版本号比较辅助函数 ---
    // 返回值：大于 0 表示 version1 更新，小于 0 表示 version2 更新，等于 0 表示相同
    private fun compareVersions(version1: String, version2: String): Int {
        val parts1 = version1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = version2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 } // 如果索引越界，取 0
            val p2 = parts2.getOrElse(i) { 0 } // 如果索引越界，取 0
            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }
        return 0 // 版本号完全相同
    }


    // --- 赞赏弹窗逻辑 ---
    private fun showDonatePopup(anchor: View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.qrcode, null)
        val popupWindow = PopupWindow(popupView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            true // 可点击外部关闭
        ).apply {
            isOutsideTouchable = true
            animationStyle = R.style.PopupAnimation // 假设你有一个名为 PopupAnimation 的 Style
            elevation = 16f

            // 应用高斯模糊（如果支持且需要）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val renderEffect = RenderEffect.createBlurEffect(
                        50f, 50f, Shader.TileMode.CLAMP
                    )
                    // 确保 mainLayout 不为 null
                    mainLayout?.setRenderEffect(renderEffect)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // 处理不支持 RenderEffect 的情况，或者模糊值过大的情况
                }
            }

            // 弹窗关闭时的监听器
            setOnDismissListener {
                // 移除高斯模糊
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // 确保 mainLayout 不为 null
                    mainLayout?.setRenderEffect(null)
                }
            }
        }

        // 点击弹窗视图本身关闭弹窗
        popupView.setOnClickListener { popupWindow.dismiss() }

        // 显示弹窗
        // 确保 mainLayout 不为 null
        if (mainLayout != null) {
            popupWindow.showAtLocation(mainLayout, Gravity.CENTER, 0, 0)
        } else {
            // 如果 mainLayout 为 null，弹窗可能无法正确显示，可以考虑使用其他 View 作为 anchor
            popupWindow.showAtLocation(anchor.rootView, Gravity.CENTER, 0, 0)
        }
    }

    // 为了代码的完整性，保留了 XML 中使用的字符串资源占位符。
    // 你需要在 res/values/strings.xml 中定义这些字符串：
    /*
    <resources>
        <string name="app_name">LuaHook</string>
        <string name="about">关于</string>
        <string name="developers">开发者</string>
        <string name="open_source_protocol">开源协议</string>
        <string name="open_source_protocol2">点击查看许可详情</string> // 示例文本
        <string name="tipping">赞赏</string>
        <string name="tipping2">如果觉得应用好用，请作者喝杯咖啡吧！</string> // 示例文本
        <string name="check_update_title">检查更新</string>
        <string name="check_update_subtitle">点击检查最新版本</string>
        <string name="update_available">发现新版本！</string>
        <string name="no_update_available">已是最新版本</string>
        <string name="update_dialog_message">有新版本 %s 可用，是否前往下载？</string> // 示例，%s 会被版本号替换
        </resources>
    */

    // 为了代码的完整性，保留了 XML 中使用的 dimen 和 style 占位符。
    // 你需要在 res/values/dimens.xml 和 res/values/styles.xml (或 themes.xml) 中定义它们：
    /*
    res/values/dimens.xml:
    <resources>
        <dimen name="card_corner_radius">8dp</dimen> // 示例值
    </resources>

    res/values/styles.xml (或 themes.xml):
    <resources>
        <style name="ShapeAppearance.App.CircleImageView" parent="ShapeAppearance.MaterialComponents.SmallComponent">
            <item name="cornerFamily">rounded</item>
            <item name="cornerSize">50%</item>
        </style>
         <style name="ShapeAppearance.App.Card.TopRounded" parent="">
            <item name="cornerFamily">rounded</item>
            <item name="cornerSizeTopLeft">@dimen/card_corner_radius</item>
            <item name="cornerSizeTopRight">@dimen/card_corner_radius</item>
            <item name="cornerSizeBottomLeft">0dp</item>
            <item name="cornerSizeBottomRight">0dp</item>
        </style>
        <style name="ShapeAppearance.App.Card.BottomRounded" parent="">
            <item name="cornerFamily">rounded</item>
            <item name="cornerSizeTopLeft">0dp</item>
            <item name="cornerSizeTopRight">0dp</item>
            <item name="cornerSizeBottomLeft">@dimen/card_corner_radius</item>
            <item name="cornerSizeBottomRight">@dimen/card_corner_radius</item>
        </style>
        <style name="PopupAnimation">
            <item name="android:windowEnterAnimation">@anim/fade_in</item> // 假设你有淡入动画
            <item name="android:windowExitAnimation">@anim/fade_out</item> // 假设你有淡出动画
        </style>
         <style name="ThemeOverlay.Material3.Dark.ActionBar" parent="">
             </style>
    </resources>
    */

    // 假设你有 res/drawable/arrow_back_24px.xml 和 res/drawable/kulipai.png, res/drawable/california.png
    // 假设你有 res/mipmap/ic_launcher.png
    // 假设你有 res/layout/qrcode.xml (用于赞赏弹窗)
    // 假设你有 res/anim/fade_in.xml 和 res/anim/fade_out.xml

}