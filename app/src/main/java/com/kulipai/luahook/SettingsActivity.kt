package com.kulipai.luahook

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {

    private val language: MaterialCardView by lazy { findViewById(R.id.language) }
    private val toolbar: MaterialToolbar by lazy { findViewById(R.id.toolbar) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById<View?>(R.id.main),
            OnApplyWindowInsetsListener { v: View?, insets: WindowInsetsCompat? ->
                val systemBars = insets!!.getInsets(WindowInsetsCompat.Type.systemBars())
                v!!.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
                insets
            })

        toolbar.setNavigationOnClickListener {
            finish()
        }



        language.setOnClickListener {
            showLanguagePickerDialog(this,)
        }



    }

    fun showLanguagePickerDialog(context: Context) {
        val languages = arrayOf("English", "简体中文")
        val languageCodes = arrayOf(LanguageUtil.LANGUAGE_ENGLISH, LanguageUtil.LANGUAGE_CHINESE)
        val currentLanguage = LanguageUtil.getCurrentLanguage(context)
        val checkedItem = languageCodes.indexOf(currentLanguage)

        MaterialAlertDialogBuilder(context)
            .setTitle(resources.getString(R.string.Select_language))
            .setSingleChoiceItems(languages, checkedItem) { dialog, which ->
                val selectedLanguageCode = languageCodes[which]
                LanguageUtil.changeLanguage(context, selectedLanguageCode)
                (context as Activity).recreate()
                dialog.dismiss()
            }
            .setNegativeButton(resources.getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}