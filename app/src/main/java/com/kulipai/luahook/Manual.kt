package com.kulipai.luahook

import ManAdapter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.transition.MaterialContainerTransform


class Manual : AppCompatActivity(), OnCardExpandListener {

    private val toolbar: MaterialToolbar by lazy { findViewById(R.id.toolbar) }
    private val rec: RecyclerView by lazy { findViewById(R.id.rec) }
    private val container: CoordinatorLayout by lazy { findViewById(R.id.main) }
    private val detail: MaterialCardView by lazy { findViewById(R.id.detail) }
    private val editor: LuaEditor by lazy { findViewById(R.id.editor) }

    private var currentCard: View? = null

    // 仅在 Android 13+ 使用
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private lateinit var backCallback: OnBackInvokedCallback


    override fun onCardExpanded(startView: View) {
        currentCard = startView // 保存当前展开的卡片 View
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyIfAvailable(this)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                onBackPressed() // 触发你刚刚写的逻辑
            }
        }



        enableEdgeToEdge()
        setContentView(R.layout.activity_manual)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        val title =
            listOf("获取包名", "hook介绍", "获取类", "调用函数", "修改/获取类字段", "http请求")

        val body = listOf(
            """
                lpparam.packageName
            """.trimIndent(),
            """
                hook("class",--类名
                lpparam.classLoader,--类加载器
                "fun",--函数名
                --"type",--参数类型例如"int","string","float","com.xxx"
                function(it)--after修改参数
                 --log(it.args[1])
                 --it.args[1]=1  --修改参数1
                end,
                function(it)--before可以修改返回值
                  --log(it.result)
                  --it.result="1" --修改返回值
                end)
            """.trimIndent(),
            """
                findClass("com.xxx",lpparam.classLoader)
            """.trimIndent(),
            """
                invoke(类名,方法名,参数1,参数2,...)
                
                --例如
                hook("class",
                lpparam.classLoader,
                "fun",
                "int",
                function(it)
                  invoke(it,"check",1)
                end,
                function(it)
                end)
                
                --例如
                class = findClass("com.xxx",lpparam.classLoader)
                invoke(class,"login","123","abc")
            """.trimIndent(),
            """
                --获取
                getField(类,字段名)
                
                --设置
                setField(类,字段名,设置值)
                
                 --例如
                hook("class",
                lpparam.classLoader,
                "fun",
                "int",
                function(it)  --假设arg1是个类 == Class{"name":"hhi",isVip=0}
                  log(getField(it.args[1],"name"))
                  setField(it.args[1],"isVip",1)
                end,
                function(it)
                end)
                
            """.trimIndent(),
            """
                http.get(url [,head,cookie],callback)
                
                http.post(url,data[,head,cookie],callback)
                
                --例如
                http.get("https://baidu.com",function(a,b)
                    --a 状态码 
                    --b body内容
                    if a==200 then
                        log(b)
                    end
                end)
                
                --例如
                http.post("https://baidu.com","name=a&password=b",function(a,b)
                    --a 状态码 
                    --b body内容
                    if a==200 then
                        log(b)
                    end
                end)
            """.trimIndent()
        )

        rec.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rec.adapter = ManAdapter(title, body, container, detail, editor, this)


        //Todo 插入编辑框等操作

    }


    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        if (detail.isVisible && currentCard != null) {
            // 执行反向动画
            val transform = MaterialContainerTransform().apply {
                startView = detail
                endView = currentCard
                addTarget(currentCard!!)
                duration = 300
                scrimColor = Color.TRANSPARENT
                fadeMode = MaterialContainerTransform.FADE_MODE_OUT
                fitMode = MaterialContainerTransform.FIT_MODE_AUTO
            }

            TransitionManager.beginDelayedTransition(container, transform)
            detail.visibility = View.INVISIBLE
            currentCard!!.visibility = View.VISIBLE

            currentCard = null // 重置
        } else {
            super.onBackPressed() // 正常返回
        }
    }


}


interface OnCardExpandListener {
    fun onCardExpanded(startView: View)
}
