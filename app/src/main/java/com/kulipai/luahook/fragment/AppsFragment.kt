package com.kulipai.luahook.fragment

import AViewModel
import android.animation.Animator.AnimatorListener
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kulipai.luahook.Activity.AppsEdit
import com.kulipai.luahook.Activity.SelectApps
import com.kulipai.luahook.MyApplication
import com.kulipai.luahook.R
import com.kulipai.luahook.adapter.AppsAdapter
import com.kulipai.luahook.util.LShare
import com.kulipai.luahook.util.ShellManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

data class AppInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long
)


fun getInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val apps = mutableListOf<AppInfo>()
    val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

    for (app in packages) {
        // 过滤掉系统应用（有启动项的）
        if (pm.getLaunchIntentForPackage(app.packageName) != null) {
            val appName = pm.getApplicationLabel(app).toString()
            val packageName = app.packageName
            pm.getApplicationIcon(app)

            try {
                val packageInfo = pm.getPackageInfo(packageName, 0)
                val versionName = packageInfo.versionName ?: "N/A"
                val versionCode =
                    packageInfo.longVersionCode

//                apps.add(AppInfo(appName, packageName, icon, versionName, versionCode))
                apps.add(AppInfo(appName, packageName, versionName, versionCode))
            } catch (e: PackageManager.NameNotFoundException) {
                // 忽略未找到的包
            }
        }
    }

    return apps.sortedBy { it.appName.lowercase() }
}


class AppsFragment : Fragment() {
    private lateinit var adapter: AppsAdapter
    private var appInfoList: List<AppInfo> = emptyList()
    private val viewModel by viewModels<AViewModel>()


    // --- **修改点 1：将 launcher 的初始化移到这里，作为成员变量** ---
    private val launcher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // 使用 Activity.RESULT_OK 进行比较

            // 在处理 Fragment 视图相关的操作时，使用 viewLifecycleOwner.lifecycleScope 更安全
            lifecycleScope.launch {
                if (ShellManager.getMode() != ShellManager.Mode.NONE) {
                    val savedList = LShare.readStringList("/apps.txt")
                    if (savedList.isEmpty()) {
                        // 列表为空的逻辑
                    } else {
                        val appInfoList = MyApplication.Companion.instance.getAppInfoList(savedList)
                        adapter.updateData(appInfoList)
                    }
                }
            }
        }


    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        var searchJob: Job? = null
        // 加载 Fragment 的布局
        val view = inflater.inflate(R.layout.apps, container, false)
        val rec: RecyclerView by lazy { view.findViewById(R.id.rec) }
        val fab1: MaterialButton by lazy { view.findViewById(R.id.fab1) }
        val fab2: MaterialButton by lazy { view.findViewById(R.id.fab2) }
        val fab: FloatingActionButton by lazy { view.findViewById(R.id.fab) }
        val searchEdit: EditText by lazy { view.findViewById(R.id.search_bar_text_view) }
        val clearImage: ImageView by lazy { view.findViewById(R.id.clear_text) }
        val searchbar: MaterialCardView by lazy { view.findViewById(R.id.searchbar) }

        // 设置rec的bottom高度适配
        activity?.findViewById<BottomNavigationView>(R.id.bottomBar)?.let { bottomNavigationView ->
            val bottomBarHeight = bottomNavigationView.height
            rec.setPadding(
                rec.paddingLeft,
                rec.paddingTop,
                rec.paddingRight,
                bottomBarHeight
            )
        }

        adapter = AppsAdapter(emptyList(), requireContext()) // 先传空列表
        rec.layoutManager = LinearLayoutManager(requireContext())
        rec.adapter = adapter

        viewModel.data.observe(requireActivity()) {
            lifecycleScope.launch {
                if (ShellManager.getMode() != ShellManager.Mode.NONE) {
                    val savedList = LShare.readStringList("/apps.txt")
                    if (savedList.isEmpty()) {
                        // 列表为空的逻辑
                    } else {
                        appInfoList = MyApplication.Companion.instance.getAppInfoList(savedList)
                        adapter.updateData(appInfoList)
                    }
                }
            }
        }

        searchbar.setOnClickListener {
            searchEdit.requestFocus()
            // 显示软键盘
            val imm =
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchEdit, InputMethodManager.SHOW_IMPLICIT)
        }


        //搜索
        searchEdit.doAfterTextChanged { s ->
            s.toString()
            searchJob?.cancel()
            searchJob = CoroutineScope(Dispatchers.Main).launch {
                if (appInfoList.isNotEmpty()) {
                    delay(100) // 延迟300ms
                    filterAppList(s.toString().trim(), clearImage)
                }
            }
        }

        clearImage.setOnClickListener {
            searchEdit.setText("")
            clearImage.visibility = View.INVISIBLE
        }


        rec.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                if (parent.getChildAdapterPosition(view) == 0) {
                    outRect.top = (88 * resources.displayMetrics.density).toInt()
                }
            }
        })


        fab1.setOnClickListener {
            if (ShellManager.getMode() != ShellManager.Mode.NONE) {
                val intent = Intent(requireContext(), SelectApps::class.java)
                launcher.launch(intent)
            } else {
                Toast.makeText(requireContext(), "未激活模块", Toast.LENGTH_SHORT).show()
            }
        }

        fab2.setOnClickListener {
            openFilePicker()
        }

        //fab点击
        var isOpen: Boolean = false
        fab.setOnClickListener {
            val rotateAnimator =
                ObjectAnimator.ofFloat(fab, "rotation", fab.rotation, fab.rotation + 45f)
            rotateAnimator.duration = 300
            rotateAnimator.start()
            if (isOpen) {
                hideFabWithAnimation(fab1)
                hideFabWithAnimation(fab2, 300)
                isOpen = !isOpen
            } else {
                showFabWithAnimation(fab2)
                showFabWithAnimation(fab1, 350)
                isOpen = !isOpen
            }
        }

//        fab.setOnClickListener {
//            if (ShellManager.getMode() != ShellManager.Mode.NONE) {
//                val intent = Intent(requireContext(), SelectApps::class.java)
//                launcher.launch(intent)
//            } else {
//                Toast.makeText(requireContext(), "未激活模块", Toast.LENGTH_SHORT).show()
//
//            }
//
//        }
        return view
    }


    private fun filterAppList(query: String, clearImage: ImageView) {
        val filteredList = if (query.isEmpty()) {
            clearImage.visibility = View.INVISIBLE
            appInfoList // 显示全部
        } else {
            clearImage.visibility = View.VISIBLE
            appInfoList.filter {
                it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }
        adapter.updateData(filteredList)
    }


    fun showFabWithAnimation(fab: View, time: Long = 300) {
        // 如果 FAB 已经可见，就不再执行进入动画
        if (fab.isVisible && fab.alpha == 1f && fab.scaleX == 1f) {
            return
        }

        // 重置状态，确保动画从正确位置开始
        fab.alpha = 0f
        fab.scaleX = 0f
        fab.scaleY = 0f
        fab.visibility = View.VISIBLE // 动画开始前先让视图可见，否则无法动画

        val scaleXAnimator = ObjectAnimator.ofFloat(fab, "scaleX", 0f, 1f)
        val scaleYAnimator = ObjectAnimator.ofFloat(fab, "scaleY", 0f, 1f)
        val alphaAnimator = ObjectAnimator.ofFloat(fab, "alpha", 0f, 1f)

        val animatorSet = AnimatorSet().apply {
            playTogether(scaleXAnimator, scaleYAnimator, alphaAnimator)
            duration = time // 动画持续时间，例如 300 毫秒
            interpolator = android.view.animation.AccelerateDecelerateInterpolator() // 加速然后减速的插值器
        }
        animatorSet.start()
    }


    fun hideFabWithAnimation(fab: View, time: Long = 250) {
        // 如果 FAB 已经不可见，就不再执行退出动画
        if (fab.isInvisible && fab.alpha == 0f && fab.scaleX == 0f) {
            return
        }

        val scaleXAnimator = ObjectAnimator.ofFloat(fab, "scaleX", 1f, 0f)
        val scaleYAnimator = ObjectAnimator.ofFloat(fab, "scaleY", 1f, 0f)
        val alphaAnimator = ObjectAnimator.ofFloat(fab, "alpha", 1f, 0f)

        val animatorSet = AnimatorSet().apply {
            playTogether(scaleXAnimator, scaleYAnimator, alphaAnimator)
            duration = time // 退出动画通常可以快一点
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            // 动画结束后将视图设置为不可见，不占据空间
            addListener(object : AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    fab.visibility = View.GONE
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
        }
        animatorSet.start()
    }


    // ActivityResultLauncher 用于处理文件选择器的结果
    val pickFileLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            // 当文件选择器返回结果时会调用此 lambda
            uri?.let {
                // 处理选择的文件 URI
                readFileContent(it)
            } ?: run {
                // 用户取消了文件选择
            }
        }

    /**
     * 调用系统文件选择器来选择文件。
     * 使用 Storage Access Framework (SAF) 的 ACTION_OPEN_DOCUMENT。
     */
    fun openFilePicker() {
        // 参数是一个字符串数组，表示你想要选择的文件类型（MIME 类型）。
        // 例如：
        // arrayOf("image/*") 选择所有图片文件
        // arrayOf("application/pdf") 选择PDF文件
        // arrayOf("text/plain") 选择文本文件
        // arrayOf("*/*") 选择所有文件类型
        pickFileLauncher.launch(arrayOf("*/*"))
    }

    /**
     * 读取指定 URI 的文件内容并显示。
     *
     * @param fileUri 文件的 Uri，通过文件选择器获取。
     */
    fun readFileContent(fileUri: Uri) {
        val stringBuilder = StringBuilder()
        "未知文件"

        // 获取文件名 (可选)
//        contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
//            if (cursor.moveToFirst()) {
//                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
//                if (displayNameIndex != -1) {
//                    fileName = cursor.getString(displayNameIndex)
//                }
//            }
//        }

        try {
            // 打开文件输入流
            requireContext().contentResolver.openInputStream(fileUri)?.use { inputStream ->
                // 使用 BufferedReader 读取文本内容
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line).append("\n")
                    }
                }
            }
            loadScript(stringBuilder.toString())


//            fileContentTextView.text = "文件名: $fileName\n\n文件内容:\n${stringBuilder.toString()}"
        } catch (e: Exception) {
            Log.e("FilePicker", "读取文件失败: ${e.message}", e)
//            fileContentTextView.text = "读取文件失败: ${e.message}"
        }
    }


    @OptIn(DelicateCoroutinesApi::class)
    fun loadScript(script: String) {
        // 解析参数
        val param = parseParameters(script)
        if (param?.name.isNullOrEmpty() || param.packageName.isNullOrEmpty()) {
            Toast.makeText(requireActivity(), "脚本格式错误！", Toast.LENGTH_SHORT).show()
        } else {
            // apps列表
            val appList = LShare.readStringList("/apps.txt")
            if (appList.isEmpty() || !appList.contains(param.packageName.toString())) {
                appList.add(param.packageName.toString())
                LShare.writeStringList("/apps.txt", appList)
            }


            // appconf
            // 写配置
            var path = LShare.AppConf + "/" + param.packageName + ".txt"
            var map = LShare.readMap(path)
            map[param.name] = arrayOf(true, param.descript, "v1.0")
            LShare.writeMap(path, map)
            LShare.ensureDirectoryExists(LShare.DIR + "/" + LShare.AppScript + "/" + param.packageName)


            // appscript
            val path2 = LShare.AppScript + "/" + param.packageName + "/" + param.name + ".lua"
            LShare.write(path2, script)

            lifecycleScope.launch {
                // 更新页面
                val appInfoList = MyApplication.Companion.instance.getAppInfoList(appList)
                adapter.updateData(appInfoList)

            }

            // 打开页面
            // 进入编辑界面
            val intent = Intent(requireContext(), AppsEdit::class.java)
            intent.putExtra("packageName", param.packageName)
            intent.putExtra("scripName", param.name)
            intent.putExtra("scriptDescription", param.descript)
            launcher.launch(intent)
        }

    }


    // 定义一个数据类来存储解析后的参数
    data class FileParameters(
        val name: String? = null,
        val descript: String? = null,
        val packageName: String? = null, // 注意：'package' 是 Kotlin 的关键字，用 packageName
        val author: String? = null,
        val otherParams: Map<String, String> = emptyMap() // 用于存储其他未定义的参数
    )

    /**
     * 从文件内容的开头解析标准参数。
     * 参数格式：-- key: value
     *
     * @param fileContent 文件的完整内容字符串。
     * @return 包含解析后参数的 FileParameters 对象。
     */
    fun parseParameters(fileContent: String): FileParameters? {
        try {
            val lines = fileContent.split("\n") // 将文件内容按行分割
            val parsedParams = mutableMapOf<String, String>()

            // 遍历文件开头的行，直到遇到不符合参数格式的行
            for (line in lines) {
                val trimmedLine = line.trim() // 去除行首尾空格

                // 检查是否是参数行
                if (trimmedLine.startsWith("--")) {
                    // 移除 "--" 前缀
                    val content = trimmedLine.substring(2).trim()

                    // 查找第一个冒号的位置
                    val colonIndex = content.indexOf(":")

                    if (colonIndex != -1) {
                        val key = content.substring(0, colonIndex).trim()
                        val value = content.substring(colonIndex + 1).trim()
                        parsedParams[key] = value
                    } else {
                        // 如果一行以 -- 开头但没有冒号，则停止解析参数
                        break
                    }
                } else if (trimmedLine.isEmpty()) {
                    // 忽略空行
                    continue
                } else {
                    // 遇到不以 "--" 开头且非空的行，表示参数部分结束
                    break
                }
            }

            return FileParameters(
                name = parsedParams["name"],
                descript = parsedParams["descript"],
                packageName = parsedParams["package"], // 对应文件中的 "package"
                author = parsedParams["author"],
                otherParams = parsedParams.filterKeys {
                    it != "name" && it != "descript" && it != "package" && it != "author"
                }
            )
        } catch (e: Exception) {
            Toast.makeText(requireActivity(), "脚本格式异常！", Toast.LENGTH_SHORT).show()
        }
        return null
    }


}