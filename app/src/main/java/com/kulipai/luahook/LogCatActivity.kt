package com.kulipai.luahook

import LogAdapter
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Visibility
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kulipai.luahook.MainActivity
import com.kulipai.luahook.util.LogcatHelper
import com.kulipai.luahook.util.RootHelper
import org.w3c.dom.Text

class LogCatActivity : AppCompatActivity() {

    private val LogRecyclerView: RecyclerView by lazy { findViewById<RecyclerView>(R.id.LogRecyclerView) }

    private val toolbar: MaterialToolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar) }
    private val noPower: TextView by lazy { findViewById<TextView>(R.id.noPower) }
    private val reresh: FloatingActionButton by lazy { findViewById<FloatingActionButton>(R.id.fab) }

    private lateinit var adapter: LogAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyIfAvailable(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_log_cat)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setSupportActionBar(toolbar)

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


        if(RootHelper.canGetRoot()){
            var logs = LogcatHelper.getSystemLogsByTagSince("LuaXposed")
            LogRecyclerView.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
            adapter = LogAdapter(logs as MutableList<String>)
            LogRecyclerView.adapter = adapter
        } else {
            noPower.visibility = View.VISIBLE
            LogRecyclerView.visibility = View.INVISIBLE
        }

        reresh.setOnClickListener {
            var logs = LogcatHelper.getSystemLogsByTagSince("LuaXposed")
            adapter.updateLogs(logs)

        }


    }//onCreate
}