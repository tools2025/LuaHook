package com.kulipai.luahook.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.edit
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kulipai.luahook.AppsEdit
import com.kulipai.luahook.R
import com.kulipai.luahook.fragment.AppInfo

class AppsAdapter(private var apps: List<AppInfo>, private val context: Context) :
    RecyclerView.Adapter<AppsAdapter.AppsViewHolder>() {

    inner class AppsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.name)
        val icon: ImageView = itemView.findViewById(R.id.icon)
        val packageName: TextView = itemView.findViewById(R.id.packageName)
        val version: TextView = itemView.findViewById(R.id.version)
        val card: MaterialCardView = itemView.findViewById(R.id.card)


        init {

            //跳转到app脚本编辑
            card.setOnClickListener{
                val intent = Intent(context, AppsEdit::class.java)
                intent.putExtra("packageName", apps[adapterPosition].packageName)
                intent.putExtra("appName", apps[adapterPosition].appName)
                context.startActivity(intent)
            }

            card.setOnLongClickListener {

                MaterialAlertDialogBuilder(context)
                    .setTitle("提示")
                    .setMessage("确定删除吗?")
                    .setPositiveButton("确定") { dialog, _ ->
                        val savedList = getStringList(context, "selectApps")
                        savedList.remove(apps[adapterPosition].packageName)
                        saveStringList(context,"selectApps",savedList)
                        val availableAppsToShow: List<AppInfo> = apps.filter { appInfo ->
                            savedList.contains(appInfo.packageName)
                            // 或者写成: appInfo.packageName !in selectedPackagesSet
                        }
                        apps = availableAppsToShow
                        notifyDataSetChanged()
                        dialog.dismiss()
                    }
                    .setNegativeButton("取消") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
                true
            }


        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppsViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_apps, parent, false) // 加载自定义布局
        return AppsViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: AppsViewHolder, position: Int) {
        holder.name.text= apps[position].appName
        holder.packageName.text= apps[position].packageName
        holder.version.text= apps[position].versionName
        holder.icon.setImageDrawable(apps[position].icon)

    }

    override fun getItemCount(): Int {
        return apps.size
    }


    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newList: List<AppInfo>) {
        apps = newList
        notifyDataSetChanged()
    }


    fun saveStringList(context: Context, key: String, list: MutableList<String>) {
        val sharedPreferences = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val serializedList = list.joinToString(",") // 使用逗号作为分隔符
        sharedPreferences.edit {
            putString(key, serializedList)
        }
    }

    fun getStringList(context: Context, key: String): MutableList<String> {
        val sharedPreferences = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val serializedList = sharedPreferences.getString(key, "") ?: ""
        return if (serializedList.isNotEmpty()) {
            serializedList.split(",").toMutableList()
        } else {
            mutableListOf()
        }
    }
}