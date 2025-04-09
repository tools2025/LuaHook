package com.kulipai.luahook
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.fragment.app.Fragment

class AppsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 加载 Fragment 的布局
        val view = inflater.inflate(R.layout.home, container, false)

        // 在这里可以找到布局中的 View 并进行操作
//        val textView = view.findViewById<TextView>(R.id.textViewHome)
//        textView?.text = "这是首页 Fragment"

        return view
    }
}