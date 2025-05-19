package com.kulipai.luahook.fragment

import AppListViewModel
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kulipai.luahook.MyApplication
import com.kulipai.luahook.R
import com.kulipai.luahook.SelectApps
import com.kulipai.luahook.adapter.AppsAdapter
import com.kulipai.luahook.util.LShare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val appName: String,
    val packageName: String,
//    val icon: Drawable,
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
            val icon = pm.getApplicationIcon(app)

            try {
                val packageInfo = pm.getPackageInfo(packageName, 0)
                val versionName = packageInfo.versionName ?: "N/A"
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }

//                apps.add(AppInfo(appName, packageName, icon, versionName, versionCode))
                apps.add(AppInfo(appName, packageName, versionName, versionCode))
            } catch (e: PackageManager.NameNotFoundException) {
                // 忽略未找到的包
            }
        }
    }

    return apps.sortedBy { it.appName.lowercase() }
}


fun loadAppsAsync(context: Context, onResult: (List<AppInfo>) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        val apps = getInstalledApps(context)
        withContext(Dispatchers.Main) {
            onResult(apps)
        }
    }
}


class AppsFragment : Fragment() {

    private val viewModel by activityViewModels<AppListViewModel>()
    private val RESULT_OK = 0
    private lateinit var adapter: AppsAdapter
    private var appInfoList: List<AppInfo> = emptyList()


    // --- **修改点 1：将 launcher 的初始化移到这里，作为成员变量** ---
    private val launcher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // 使用 Activity.RESULT_OK 进行比较

            // 在处理 Fragment 视图相关的操作时，使用 viewLifecycleOwner.lifecycleScope 更安全
            lifecycleScope.launch {
                if (canHook()) {
                    val savedList = getStringList(requireContext(), "selectApps")
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


        lifecycleScope.launch {
            if (canHook()) {
                val savedList = getStringList(requireContext(), "selectApps")
                if (savedList.isEmpty()) {
                    // 列表为空的逻辑
                } else {
                    appInfoList = MyApplication.Companion.instance.getAppInfoList(savedList)
                    adapter.updateData(appInfoList)
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
            val input = s.toString()
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

        //fab添加app
        fab.setOnClickListener {
            if (canHook()) {


                val intent = Intent(requireContext(), SelectApps::class.java)
                launcher.launch(intent)
            } else {
                Toast.makeText(requireContext(), "未激活模块", Toast.LENGTH_SHORT).show()
                
            }

        }
        return view
    }


    fun saveStringList(context: Context, key: String, list: List<String>) {
        LShare.write("/apps.txt",list.joinToString(","))
//        val prefs = context.getSharedPreferences("MyAppPrefs", MODE_WORLD_READABLE)
//        val serialized = list.joinToString(",")
//        prefs.edit { putString(key, serialized) }
    }

    fun getStringList(context: Context, key: String): MutableList<String> {
//        val prefs = context.getSharedPreferences("MyAppPrefs", MODE_WORLD_READABLE)
//        val serialized = prefs.getString(key, "") ?: ""
        val serialized = LShare.read("/apps.txt")
        return if (serialized!="") {
            serialized.split(",").toMutableList()
        } else {
            mutableListOf()
        }
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


}