package com.kulipai.luahook
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView

class HomeFragment : Fragment() {




    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 加载 Fragment 的布局
        val view = inflater.inflate(R.layout.home, container, false)
        val card1:MaterialCardView by lazy { view.findViewById(R.id.card1) }

        card1.setOnClickListener{
            val intent = Intent(requireActivity(), EditActivity::class.java)
            startActivity(intent)

        }
//        textView?.text = "这是首页 Fragment"

        return view
    }
}