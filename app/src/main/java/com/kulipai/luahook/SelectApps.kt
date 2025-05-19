package com.kulipai.luahook

import SelectAppsAdapter
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kulipai.luahook.fragment.AppInfo
import com.kulipai.luahook.util.LShare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SelectApps : AppCompatActivity() {

    var selectApps = mutableListOf<String>()
    var searchJob: Job? = null
    lateinit var availableAppsToShow: List<AppInfo>
    lateinit var adapter: SelectAppsAdapter
    var isLoaded = false


    private val rec: RecyclerView by lazy { findViewById(R.id.rec) }
    private val fab: FloatingActionButton by lazy { findViewById(R.id.fab) }
    private val searchEdit: EditText by lazy { findViewById(R.id.search_bar_text_view) }
    private val clearImage: ImageView by lazy { findViewById(R.id.clear_text) }
    private val searchbar: MaterialCardView by lazy { findViewById(R.id.searchbar) }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_select_apps)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }

        val selectedPackageNames = getStringList(this@SelectApps, "selectApps")
        selectApps = selectedPackageNames

        adapter = SelectAppsAdapter(emptyList(), this, selectApps) // 先传空列表
        rec.layoutManager = LinearLayoutManager(this)
        rec.adapter = adapter

        val app = application as MyApplication
        lifecycleScope.launch {
            val apps = app.getAppListAsync()
            //去除已选择app

            val selectedPackagesSet: Set<String> = selectedPackageNames.toSet()
            availableAppsToShow = apps.filter { appInfo ->
                !selectedPackagesSet.contains(appInfo.packageName)
                // 或者写成: appInfo.packageName !in selectedPackagesSet
            }

            adapter.updateData(availableAppsToShow)
            isLoaded = true
        }


        rec.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                if (parent.getChildAdapterPosition(view) == 0) {
                    outRect.top = (88 * resources.displayMetrics.density).toInt()
                }
            }
        })


        searchbar.setOnClickListener {
            searchEdit.requestFocus()
            // 显示软键盘
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchEdit, InputMethodManager.SHOW_IMPLICIT)
        }

        //搜索
        searchEdit.doAfterTextChanged { s ->
            val input = s.toString()
            searchJob?.cancel()
            searchJob = CoroutineScope(Dispatchers.Main).launch {
                if (isLoaded) {
                    delay(100) // 延迟300ms
                    filterAppList(s.toString().trim(), clearImage)
                }
            }
        }

        clearImage.setOnClickListener {
            searchEdit.setText("")
            clearImage.visibility = View.INVISIBLE
        }

        fab.setOnClickListener {
            saveStringList(this, "selectApps", selectApps)
            finish()
        }


    }

    fun saveStringList(context: Context, key: String, list: List<String>) {
        LShare.write("/apps.txt",list.joinToString(","))
//        val prefs = context.getSharedPreferences("MyAppPrefs", MODE_WORLD_READABLE)
//        val serialized = list.joinToString(",")
//        prefs.edit { putString(key, serialized) }
    }

    fun getStringList(context: Context, key: String): MutableList<String> {
//        val prefs = context.getSharedPreferences("MyAppPrefs", MODE_WORLD_READABLE)
//        val serialized = prefs.getString(key, "") ?: ""
        val serialized = LShare.read("/apps.txt")
        return if (serialized!="") {
            serialized.split(",").toMutableList()
        } else {
            mutableListOf()
        }
    }

    private fun filterAppList(query: String, clearImage: ImageView) {
        val filteredList = if (query.isEmpty()) {
            clearImage.visibility = View.INVISIBLE
            availableAppsToShow // 显示全部
        } else {
            clearImage.visibility = View.VISIBLE
            availableAppsToShow.filter {
                it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }
        adapter.updateData(filteredList)
    }


}