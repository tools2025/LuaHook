package com.kulipai.luahook.fragment

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.transition.MaterialContainerTransform
import com.kulipai.luahook.LuaEditor
import com.kulipai.luahook.OnCardExpandListener
import com.kulipai.luahook.R
import com.kulipai.luahook.adapter.ManAdapter

class ManualFragment : Fragment(),OnCardExpandListener {


    var currentCard: View? = null
    lateinit var detail: MaterialCardView
    lateinit var containers: CoordinatorLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 加载 Fragment 的布局
        val view = inflater.inflate(R.layout.manual, container, false)
        val rec: RecyclerView by lazy { view.findViewById(R.id.rec) }
        containers = view.findViewById(R.id.main)
        detail = view.findViewById(R.id.detail)
        val editor: LuaEditor by lazy { view.findViewById(R.id.editor) }

        // 设置rec的bottom高度适配
        activity?.findViewById<BottomNavigationView>(R.id.bottomBar)?.let { bottomNavigationView ->
            val bottomBarHeight = bottomNavigationView.height
            containers.setPadding(
                rec.paddingLeft,
                rec.paddingTop,
                rec.paddingRight,
                bottomBarHeight
            )
        }


//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            requireActivity().onBackInvokedDispatcher.registerOnBackInvokedCallback(
//                OnBackInvokedDispatcher.PRIORITY_DEFAULT
//            ) {
//                requireActivity().onBackPressed() // 触发你刚刚写的逻辑
//            }
//        }

        val title =
            listOf(
                "获取包名",
                "hook介绍",
                "获取类",
                "调用函数",
                "修改/获取类字段",
                "http请求",
                "file操作",
                "json操作",
                "import操作"
            )

        val body = listOf(
            """
                lpparam.packageName
            """.trimIndent(),
            """
                hook("class",--类名
                lpparam.classLoader,--类加载器
                "fun",--函数名
                --"type",--参数类型例如"int","string","float","com.xxx"
                function(it)--before修改参数
                 --log(it.args[1])
                 --it.args[1]=1  --修改参数1
                end,
                function(it)--after可以修改返回值
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
            """.trimIndent(),
            """
                -- 方法1: 直接使用Java的File类
                File("path"):isFile()
                -- ...
                
                -- 方法2: 使用分装函数file类
                -- 判断文件或目录是否存在
                print("是否是文件:", file.isFile("/sdcard/demo.txt"))
                print("是否是目录:", file.isDir("/sdcard/"))
                print("是否存在:", file.isExists("/sdcard/demo.txt"))

                -- 写入字符串
                file.write("/sdcard/demo.txt", "Hello, LuaJ!")
                -- 追加字符串
                file.append("/sdcard/demo.txt", "\nAppend line 1.")
                -- 读取内容
                local content = file.read("/sdcard/demo.txt")
                print("文件内容:\n" .. content)

                -- 写入二进制（可用于图片、音频等）
                file.writeBytes("/sdcard/demo.bin", "binary\x00data")
                -- 追加二进制
                file.appendBytes("/sdcard/demo.bin", "\xFF\xAA")
                -- 读取二进制
                local bin = file.readBytes("/sdcard/demo.bin")
                print("读取的二进制内容（字符串显示）:\n" .. bin)

                -- 文件复制与移动
                file.copy("/sdcard/demo.txt", "/sdcard/demo_copy.txt")
                file.move("/sdcard/demo_copy.txt", "/sdcard/demo_moved.txt")

                -- 文件重命名（同目录）
                file.rename("/sdcard/demo_moved.txt", "demo_renamed.txt")

                -- 删除文件
                file.delete("/sdcard/demo_renamed.txt")

                -- 获取文件名与大小
                print("文件名:", file.getName("/sdcard/demo.txt"))
                print("文件大小:", file.getSize("/sdcard/demo.txt"), "bytes")
            """.trimIndent(),
            """
                --decode / encode
                
                local data = json.decode('{"name":"Xposed","version":1.0}')
                print("模块名:", data.name)
    
                local encoded = json.encode({status="ok", count=3})
                print("编码结果:", encoded)
            """.trimIndent(),
            """
                --全局引入一个类
                import "android.widget.Toast"
                Toast:makeText(activity,"hello",1000):show()
                
                --自定义名称
                AAA = import "android.widget.Toast"
                AAA:makeText(activity,"hello",1000):show()
            """.trimIndent()
        )

        rec.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        rec.adapter = ManAdapter(title, body, containers, detail, editor, this)





        // 在这里可以找到布局中的 View 并进行操作
//        val textView = view.view.findViewById<TextView>(R.id.textViewHome)
//        textView?.text = "这是首页 Fragment"

        return view
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 创建 OnBackPressedCallback
        val callback = object : OnBackPressedCallback(true /* enabled by default */) {
            override fun handleOnBackPressed() {
                if (detail.isVisible && currentCard != null) {

                    isEnabled = true // 启用
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

                    TransitionManager.beginDelayedTransition(containers, transform)
                    detail.visibility = View.INVISIBLE
                    currentCard!!.visibility = View.VISIBLE

                    currentCard = null // 重置
                } else {
                    isEnabled = false // 禁用当前的回调
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }

        // 注册回调到 Activity 的 OnBackPressedDispatcher
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }


    override fun onCardExpanded(startView: View) {
        currentCard = startView // 保存当前展开的卡片 View
    }

}