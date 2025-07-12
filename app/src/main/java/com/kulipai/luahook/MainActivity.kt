package com.kulipai.luahook

import AViewModel
import DataRepository.ShellInit
import LanguageUtil
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kulipai.luahook.activity.AppsEdit
import com.kulipai.luahook.activity.EditActivity
import com.kulipai.luahook.activity.SettingsActivity
import com.kulipai.luahook.fragment.AppsFragment
import com.kulipai.luahook.fragment.HomeFragment
import com.kulipai.luahook.fragment.PluginsFragment
import com.kulipai.luahook.util.LShare
import com.kulipai.luahook.util.ShellManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.sui.Sui


class MainActivity : AppCompatActivity() {



    lateinit var thisLanguage: String

    private val shizukuRequestCode = 100


    private lateinit var settingsLauncher: ActivityResultLauncher<Intent>

    private val bottomBar: BottomNavigationView by lazy { findViewById(R.id.bottomBar) }
    private val toolbar: MaterialToolbar by lazy { findViewById(R.id.toolbar) }
    private val viewPager2: ViewPager2 by lazy { findViewById(R.id.viewPager2) }
    private val viewModel by viewModels<AViewModel>()



    override fun onCreate(savedInstanceState: Bundle?) {

        LanguageUtil.applyLanguage(this)

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)


        viewModel.data.observe(this) {
            //没有root则shizuku
            if (Shell.isAppGrantedRoot() == false && Shizuku.getBinder() != null && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.addBinderReceivedListener(binderReceivedListener)
                Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
                updatePermissionStatus()


            }
        }



        // 注册 ActivityResultLauncher
        settingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (LanguageUtil.getCurrentLanguage(this)!=thisLanguage){
                recreate()
            }
        }

        //setting
        toolbar.menu.add(0, 1, 0, "Setting").setIcon(R.drawable.settings_24px).setShowAsAction(1)

        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> {
                    thisLanguage = LanguageUtil.getCurrentLanguage(this)
                    val intent = Intent(this, SettingsActivity::class.java)

                    settingsLauncher.launch(intent)
                    true
                }

                else -> true
            }
        }

        //状态检查
        val prefs = getSharedPreferences("status", MODE_PRIVATE)
        val current = prefs.getString("current", "null")
        if (current == "apps") {
            val intent = Intent(this, AppsEdit::class.java)
            intent.putExtra("packageName", prefs.getString("packageName", ""))
            intent.putExtra("appName", prefs.getString("appName", ""))
            startActivity(intent)
            prefs.edit {
                putString("current", "null")
            }
        } else if (current == "global") {
            val intent = Intent(this, EditActivity::class.java)
            startActivity(intent)
            prefs.edit {
                putString("current", "null")
            }
        }


        // 可以选择在这里观察是否加载完（调试用）
        val app = application as MyApplication
        lifecycleScope.launch {
            app.getAppListAsync()
            if (ShellManager.getMode() != ShellManager.Mode.NONE) {
                val savedList = getStringList()
                if (savedList.isEmpty()) {
                    // 列表为空的逻辑
                } else {
                    MyApplication.instance.getAppInfoList(savedList)
                    // 加载 appInfoList
                }
            }


        }



        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }


        val menu: Menu = bottomBar.menu



        menu.add(Menu.NONE, 0, 0, resources.getString(R.string.home))
            .setIcon(R.drawable.home_24px)

        menu.add(Menu.NONE, 1, 1, resources.getString(R.string.apps))
            .setIcon(R.drawable.apps_24px)

        menu.add(Menu.NONE, 2, 2, resources.getString(R.string.plugins))
            .setIcon(R.drawable.extension_24px)


        val fragmentList = listOf(
            HomeFragment(),
            AppsFragment(),
            PluginsFragment(),
        )

        // 创建 FragmentStateAdapter
        val adapter = object : FragmentStateAdapter(this) {
            override fun createFragment(position: Int): Fragment {
                return fragmentList[position]
            }

            override fun getItemCount(): Int {
                return fragmentList.size
            }
        }

        viewPager2.adapter = adapter

        //同步 BottomNavigationView 的选中状态
        viewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomBar.menu[position].isChecked = true
                menu[0].setIcon(R.drawable.home_24px)
                menu[2].setIcon(R.drawable.extension_24px)

                if (position == 0) {
                    menu[0].setIcon(R.drawable.home_fill_24px)
                } else if (position == 2) {

                    menu[2].setIcon(R.drawable.extension_fill_24px)


                }
            }
        })

        // 同步 ViewPager2 的页面
        bottomBar.setOnItemSelectedListener { item ->
            when (item.itemId) {
                0 -> {
                    viewPager2.currentItem = 0
                    true
                }

                1 -> {
                    viewPager2.currentItem = 1
                    true
                }

                2 -> {
                    viewPager2.currentItem = 2
                    true
                }

                else -> false
            }
        }


    }


    fun getStringList(): MutableList<String> {
//        val prefs = context.getSharedPreferences("MyAppPrefs", MODE_WORLD_READABLE)
//        val serialized = prefs.getString(key, "") ?: ""
        val serialized = LShare.read("/apps.txt")
        return if (serialized != "") {
            serialized.split(",").toMutableList()
        } else {
            mutableListOf()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updatePermissionStatus()
    }
    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == shizukuRequestCode) {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    Toast.makeText(this, "Shizuku 权限已授予", Toast.LENGTH_SHORT).show()
                    updatePermissionStatus()
                } else {
                    Toast.makeText(this, "Shizuku 权限被拒绝", Toast.LENGTH_SHORT).show()
                    updatePermissionStatus()
                }
            }
        }


    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }

    private fun isShizukuAvailable(): Boolean {
        return Shizuku.pingBinder()
    }

    private fun checkShizukuPermission(): Boolean {
        return if (Shizuku.isPreV11()) {
//            permissionStatusTextView.text = "Shizuku 版本过低，不支持权限请求"
            false
        } else {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestShizukuPermission() {
        if (!Shizuku.isPreV11() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(shizukuRequestCode)
        } else if (Shizuku.isPreV11()) {
            Toast.makeText(this, "Shizuku 版本过低，请使用 ADB 启动", Toast.LENGTH_LONG).show()
        } else {
//            Toast.makeText(this, "Shizuku 权限已存在", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePermissionStatus() {
        if (!isShizukuAvailable()) {
//            Toast.makeText(this, "Shizuku 服务未运行", Toast.LENGTH_SHORT).show()

        } else if (checkShizukuPermission()) {
//            Toast.makeText(this, "Shizuku 权限已授予2", Toast.LENGTH_SHORT).show()
            ShellInit(applicationContext)

            Sui.init(packageName)
        } else {
//            Toast.makeText(this, "Shizuku 权限未授予", Toast.LENGTH_SHORT).show()
//            permissionStatusTextView.text = "Shizuku 权限未授予"
//            executeCommandButton.isEnabled = false
            requestShizukuPermission() // 尝试请求权限
        }
    }


}


