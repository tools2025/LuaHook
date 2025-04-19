package com.kulipai.luahook

import LanguageUtil
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.material.card.MaterialCardView
import com.kulipai.luahook.fragment.AppsFragment
import com.kulipai.luahook.fragment.HomeFragment
import com.kulipai.luahook.fragment.ManualFragment
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {



    private lateinit var SettingsLauncher: ActivityResultLauncher<Intent>

    fun isNightMode(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private val bottomBar: BottomNavigationView by lazy { findViewById(R.id.bottomBar) }
    private val toolbar: MaterialToolbar by lazy { findViewById(R.id.toolbar) }
    private val viewPager2: ViewPager2 by lazy { findViewById(R.id.viewPager2) }



    override fun onCreate(savedInstanceState: Bundle?) {

        LanguageUtil.applyLanguage(this)

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)


        // 注册 ActivityResultLauncher
        SettingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            recreate()
        }

        //setting
        toolbar.menu.add(0,1,0,"Setting").setIcon(R.drawable.settings_24px).setShowAsAction(1)

        toolbar.setOnMenuItemClickListener {
            when(it.itemId) {
                1-> {
                    val itent = Intent(this,SettingsActivity::class.java)
                    SettingsLauncher.launch(itent)
                    true
                }
                else -> true
            }
        }

        //状态检查
        val prefs = getSharedPreferences("status", MODE_PRIVATE)
        val current = prefs.getString("current","null")
        if(current == "null") {

        } else if (current == "apps") {
            val intent = Intent(this, AppsEdit::class.java)
            intent.putExtra("packageName", prefs.getString("packageName",""))
            intent.putExtra("appName", prefs.getString("appName",""))
            startActivity(intent)
            prefs.edit {
                putString("current","null")
            }
        } else if (current == "global") {
            val intent = Intent(this, EditActivity::class.java)
            startActivity(intent)
            prefs.edit {
                putString("current","null")
            }
        }




        // 可以选择在这里观察是否加载完（调试用）
        val app = application as MyApplication
        lifecycleScope.launch {
            val apps = app.getAppListAsync()

            val savedList = getStringList(this@MainActivity, "selectApps")
            if (savedList.isEmpty()) {
                // 列表为空的逻辑
            } else {
                val appInfoList = MyApplication.instance.getAppInfoList(savedList)
                // 加载 appInfoList
            }


        }



        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }


        val menu: Menu = bottomBar.getMenu()



        menu.add(Menu.NONE, 0, 0, resources.getString(R.string.home))
            .setIcon(R.drawable.home_24px)

        menu.add(Menu.NONE, 1, 1, resources.getString(R.string.apps))
            .setIcon(R.drawable.apps_24px)

        menu.add(Menu.NONE, 2, 2, resources.getString(R.string.manual))
            .setIcon(R.drawable.book_24px)


        val fragmentList = listOf(
            HomeFragment(),
            AppsFragment(),
            ManualFragment(),
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
                menu.get(0).setIcon(R.drawable.home_24px)
                menu.get(2).setIcon(R.drawable.book_24px)

                if (position == 0) {
                    menu.get(0).setIcon(R.drawable.home_fill_24px)

                } else if (position == 2) {

                    menu.get(2).setIcon(R.drawable.book_fill_24px)

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




    fun saveStringList(context: Context, key: String, list: List<String>) {
        val prefs = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val serialized = list.joinToString(",")
        prefs.edit { putString(key, serialized) }
    }

    fun getStringList(context: Context, key: String): MutableList<String> {
        val prefs = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val serialized = prefs.getString(key, "") ?: ""
        return if (serialized.isNotEmpty()) {
            serialized.split(",").toMutableList()
        } else {
            mutableListOf()
        }
    }



}


