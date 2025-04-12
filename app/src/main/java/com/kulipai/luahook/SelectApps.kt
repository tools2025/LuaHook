package com.kulipai.luahook

import SelectAppsAdapter
import android.content.Context
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kulipai.luahook.fragment.AppInfo
import kotlinx.coroutines.launch
import kotlin.collections.mutableListOf
import kotlin.getValue

class SelectApps : AppCompatActivity() {

    var selectApps = mutableListOf<String>()


    private val rec: RecyclerView by lazy { findViewById(R.id.rec) }
    private val fab: FloatingActionButton by lazy { findViewById(R.id.fab) }
    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_select_apps)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val selectedPackageNames = getStringList(this@SelectApps,"selectApps")
        selectApps = selectedPackageNames

        val adapter = SelectAppsAdapter(emptyList(),this,selectApps) // 先传空列表
        rec.layoutManager = LinearLayoutManager(this)
        rec.adapter = adapter

        val app = application as MyApplication
        lifecycleScope.launch {
            val apps = app.getAppListAsync()
            //去除已选择app

            val selectedPackagesSet: Set<String> = selectedPackageNames.toSet()
            val availableAppsToShow: List<AppInfo> = apps.filter { appInfo ->
                !selectedPackagesSet.contains(appInfo.packageName)
                // 或者写成: appInfo.packageName !in selectedPackagesSet
            }
            adapter.updateData(availableAppsToShow)
        }

        fab.setOnClickListener {
            saveStringList(this,"selectApps",selectApps)
            finish()
        }




    }

    fun saveStringList(context: Context, key: String, list: List<String>) {
        val prefs = context.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val serialized = list.joinToString(",")
        prefs.edit { putString(key, serialized) }
    }

    fun getStringList(context: Context, key: String): MutableList<String> {
        val prefs = context.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val serialized = prefs.getString(key, "") ?: ""
        return if (serialized.isNotEmpty()) {
            serialized.split(",").toMutableList()
        } else {
            mutableListOf()
        }
    }

}