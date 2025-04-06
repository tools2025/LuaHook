package com.kulipai.luahook

import ManAdapter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.ChangeBounds
import androidx.transition.ChangeImageTransform
import androidx.transition.ChangeTransform
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import androidx.core.view.isVisible
import com.google.android.material.transition.MaterialContainerTransform


class Manual : AppCompatActivity(), OnCardExpandListener {

    private val toolbar: MaterialToolbar by lazy { findViewById(R.id.toolbar) }
    private val rec: RecyclerView by lazy { findViewById(R.id.rec) }
    private val container: CoordinatorLayout by lazy { findViewById(R.id.main) }
    private val detail: MaterialCardView by lazy { findViewById(R.id.detail) }

    private var currentCard: View? = null

    private lateinit var layoutA: ViewGroup
    private lateinit var layoutB: ViewGroup

    // 仅在 Android 13+ 使用
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private lateinit var backCallback: OnBackInvokedCallback


    override fun onCardExpanded(startView: View) {
        currentCard = startView // 保存当前展开的卡片 View
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


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
            listOf("获取包名", "hook介绍", "")

        val body = listOf(
            """
                lpparam.packageName
            """.trimIndent(),
            """
                hook("class",--类名
                lpparam.classLoader,--类加载器
                "fun",--函数名
                --"type",--参数类型
                function(it)--after修改参数
                  --log(it.args[1])

                end,
                function(it)--before可以修改返回值
                  --log(it.result)
                end)
            """.trimIndent(),
        )

        rec.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rec.adapter = ManAdapter(title, body, container, detail, this)


        //Todo : 手册功能，代码示例，左右瀑布流布局，点开元素共享动画，可以复制，插入编辑框等操作

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
