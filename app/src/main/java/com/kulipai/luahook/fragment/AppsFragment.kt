package com.kulipai.luahook.fragment

import AppListViewModel
import com.kulipai.luahook.adapter.AppsAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kulipai.luahook.MyApplication
import com.kulipai.luahook.R
import com.kulipai.luahook.SelectApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable,
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

                apps.add(AppInfo(appName, packageName, icon, versionName, versionCode))
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


    // --- **修改点 1：将 launcher 的初始化移到这里，作为成员变量** ---
    private val launcher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // 使用 Activity.RESULT_OK 进行比较

                // 在处理 Fragment 视图相关的操作时，使用 viewLifecycleOwner.lifecycleScope 更安全
                lifecycleScope.launch {
                    val savedList = getStringList(requireContext(), "selectApps")
                    if (savedList.isEmpty()) {
                        // 列表为空的逻辑
                    } else {
                        val appInfoList = MyApplication.Companion.instance.getAppInfoList(savedList)
                        adapter.updateData(appInfoList)
                    }
                }
        }


    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 加载 Fragment 的布局
        val view = inflater.inflate(R.layout.apps, container, false)
        val rec: RecyclerView by lazy { view.findViewById(R.id.rec) }
        val fab: FloatingActionButton by lazy { view.findViewById(R.id.fab) }

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

            val savedList = getStringList(requireContext(), "selectApps")
            if (savedList.isEmpty()) {
                // 列表为空的逻辑
            } else {
                val appInfoList = MyApplication.Companion.instance.getAppInfoList(savedList)
                adapter.updateData(appInfoList)
            }


        }


        //        launcher = requireActivity().registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//            if (result.resultCode == Activity.RESULT_OK) {
//                lifecycleScope.launch {
//                    val savedList = getStringList(requireContext(), "selectApps")
//                    if (savedList.isEmpty()) {
//                        // 列表为空的逻辑
//                    } else {
//                        val appInfoList = MyApplication.instance.getAppInfoList(savedList)
//                        adapter.updateData(appInfoList)
//                    }
//                }
//            }
//        }
//        viewModel.isLoaded.observe(viewLifecycleOwner) { loaded ->
//            if (loaded) {
//                val list = viewModel.appList.value ?: emptyList()
//                adapter.updateData(list)
//            }
//        }


        //fab添加app
        fab.setOnClickListener {
            val intent = Intent(requireContext(), SelectApps::class.java)
            launcher.launch(intent)
        }


        return view
    }


    fun saveStringList(context: Context, key: String, list: MutableList<String>) {
        val sharedPreferences = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val serializedList = list.joinToString(",") // 使用逗号作为分隔符
        sharedPreferences.edit {
            putString(key, serializedList)
        }
    }

    fun getStringList(context: Context, key: String): MutableList<String> {
        val sharedPreferences = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val serializedList = sharedPreferences.getString(key, "") ?: ""
        return if (serializedList.isNotEmpty()) {
            serializedList.split(",").toMutableList()
        } else {
            mutableListOf()
        }
    }


}