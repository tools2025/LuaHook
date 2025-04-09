package com.kulipai.luahook

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.widget.Switch
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.DynamicColors


class MainActivity : AppCompatActivity() {


    fun isNightMode(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private val bottomBar: BottomNavigationView by lazy { findViewById(R.id.bottomBar) }
    private val viewPager2: ViewPager2 by lazy { findViewById(R.id.viewPager2) }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyIfAvailable(this)
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }


        val menu: Menu = bottomBar.getMenu()

        menu.add(Menu.NONE, 0, 0, "主页")
            .setIcon(R.drawable.home_24px)

        menu.add(Menu.NONE, 1, 1, "应用")
            .setIcon(R.drawable.apps_24px)

        menu.add(Menu.NONE, 2, 2, "手册")
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

                if(position == 0) {
                    menu.get(0).setIcon(R.drawable.home_fill_24px)

                } else if (position==2) {
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


}