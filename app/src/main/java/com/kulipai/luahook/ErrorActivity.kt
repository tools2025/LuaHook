package com.kulipai.luahook

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView

/**
 * ErrorActivity 是一个专门用于显示应用程序错误信息的 Activity。
 * 它从 Intent 中接收错误消息和堆栈跟踪，并将其显示在 UI 上。
 */
class ErrorActivity : AppCompatActivity() {

    companion object {
        // 用于 Intent 传递错误信息的键
        const val EXTRA_ERROR_MESSAGE = "error_message"
        const val EXTRA_STACK_TRACE = "stack_trace"
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_error) // 确保您有 activity_error.xml 布局文件
        enableEdgeToEdge()


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }

        val tvErrorMessage: TextView = findViewById(R.id.tvErrorMessage)

        val btnRestartApp: MaterialCardView = findViewById(R.id.btnRestartApp)

        // 从 Intent 中获取错误信息
        val errorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE) ?: "未知的错误发生。"
        intent.getStringExtra(EXTRA_STACK_TRACE) ?: "无堆栈跟踪信息。"

        // 显示错误信息
        tvErrorMessage.text = errorMessage


        // 设置重启应用程序按钮的点击监听器
        btnRestartApp.setOnClickListener {
            // 重启应用程序到主 Activity
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish() // 关闭当前错误 Activity
        }
    }
}