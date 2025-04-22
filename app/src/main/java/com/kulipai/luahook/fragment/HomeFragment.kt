package com.kulipai.luahook.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.kulipai.luahook.EditActivity
import com.kulipai.luahook.R



fun canHook(): Boolean {
    return false
}

class HomeFragment : Fragment() {



    private fun getAppVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName!!
        } catch (e: PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }


    fun getAppVersionCode(context: Context): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.longVersionCode // 注意这里使用 longVersionCode，在旧版本中是 versionCode (Int)
        } catch (e: PackageManager.NameNotFoundException) {
            -1 // 或者其他表示未找到的数值
        }
    }

    fun getDynamicColor(context: Context, @AttrRes colorAttributeResId: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(colorAttributeResId, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(context, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }



    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.home, container, false)
        val card1: MaterialCardView by lazy { view.findViewById(R.id.card1) }
        val card: MaterialCardView by lazy { view.findViewById(R.id.card) }
        val img: ImageView by lazy { view.findViewById(R.id.img) }
        val status: TextView by lazy { view.findViewById(R.id.status) }
        val version: TextView by lazy { view.findViewById(R.id.version) }

        version.text = getAppVersionName(requireContext()).toString()+" ("+getAppVersionCode(requireContext()).toString()+")"

        if(canHook()) {
            status.text=resources.getString(R.string.Xposed_status_ok)
            card.setCardBackgroundColor(getDynamicColor(requireContext(),com.google.android.material.R.attr.colorPrimary))
            status.setTextColor(getDynamicColor(requireContext(),com.google.android.material.R.attr.colorOnPrimary))
            version.setTextColor(getDynamicColor(requireContext(),com.google.android.material.R.attr.colorOnPrimary))
            img.setImageResource(R.drawable.check_circle_24px)
            img.setColorFilter(getDynamicColor(requireContext(),com.google.android.material.R.attr.colorOnPrimary))
        }


        card1.setOnClickListener{
            if (canHook()) {
                val intent = Intent(requireActivity(), EditActivity::class.java)
                startActivity(intent)
            }else{
                Toast.makeText(requireContext(), "未激活模块", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }
}