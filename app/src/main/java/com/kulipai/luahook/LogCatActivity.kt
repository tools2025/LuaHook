package com.kulipai.luahook

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kulipai.luahook.adapter.LogAdapter
import com.kulipai.luahook.util.LogcatHelper
import com.kulipai.luahook.util.RootHelper
import kotlinx.coroutines.launch


class LogCatActivity : AppCompatActivity() {

    private val LogRecyclerView: RecyclerView by lazy { findViewById<RecyclerView>(R.id.LogRecyclerView) }

    private val toolbar: MaterialToolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar) }
    private val noPower: TextView by lazy { findViewById<TextView>(R.id.noPower) }
    private val reresh: FloatingActionButton by lazy { findViewById<FloatingActionButton>(R.id.fab) }

    private lateinit var adapter: LogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_log_cat)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setSupportActionBar(toolbar)

        RootHelper.canGetRoot()

        // 启用导航按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {

                finish()
            }
        }

        // 将回调添加到 OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, callback)

        // 设置 Toolbar 的导航点击监听器（仍然需要，用于触发 onBackPressedDispatcher）
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
//        var currentTime = LogcatHelper.getCurrentLogcatTimeFormat()


        if (RootHelper.isRoot()) {
            lifecycleScope.launch {
                var logs = LogcatHelper.getSystemLogsByTagSince("LuaXposed",getSharedPreferences("cache",MODE_PRIVATE).getString("logClearTime",null))
                LogRecyclerView.layoutManager =
                    LinearLayoutManager(this@LogCatActivity, LinearLayoutManager.VERTICAL, false)
                adapter = LogAdapter(logs as MutableList<String>)
                LogRecyclerView.adapter = adapter
            }
        } else {
            noPower.visibility = View.VISIBLE
            LogRecyclerView.visibility = View.INVISIBLE
        }

        reresh.setOnClickListener {
            lifecycleScope.launch {
                var logs = LogcatHelper.getSystemLogsByTagSince("LuaXposed",
                    getSharedPreferences("cache",MODE_PRIVATE).getString("logClearTime",null)
                )
                adapter.updateLogs(logs)
            }

        }


    }//onCreate


    //菜单
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {

        menu?.add(0, 0, 0, "Clear")
            ?.setIcon(R.drawable.cleaning_services_24px)
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

//        menu?.add(0, 1, 0, "Undo")
//            ?.setIcon(R.drawable.undo_24px)
//            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
//
//        menu?.add(0, 2, 0, "Redo")
//            ?.setIcon(R.drawable.redo_24px)
//            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
//        menu?.add(0, 3, 0, resources.getString(R.string.format))
//            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
//        menu?.add(0, 4, 0, resources.getString(R.string.log))  //LogCat
//            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
//        menu?.add(0, 5, 0, resources.getString(R.string.manual))
//            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
//        menu?.add(0, 9, 0, "搜索")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            0 -> {
                getSharedPreferences("cache",MODE_PRIVATE).edit().apply{
                    putString("logClearTime", LogcatHelper.getCurrentLogcatTimeFormat())
                    apply()
                }
                lifecycleScope.launch {
                    var logs = LogcatHelper.getSystemLogsByTagSince("LuaXposed",
                        getSharedPreferences("cache",MODE_PRIVATE).getString("logClearTime",null)
                    )
                    adapter.updateLogs(logs)
                }
                true
            }

            else -> false
        }
    }
}