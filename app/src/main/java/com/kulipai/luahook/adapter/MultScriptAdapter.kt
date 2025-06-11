package com.kulipai.luahook.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.kulipai.luahook.Activity.AppsEdit
import com.kulipai.luahook.R
import com.kulipai.luahook.util.LShare
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

class MultScriptAdapter(
    private val conf: MutableList<MutableMap.MutableEntry<String, Any?>>,
    private val currentPackageName: String,
    private val appName: String,
    private val context: Context,
    private val launcher: ActivityResultLauncher<Intent>
) :


    RecyclerView.Adapter<MultScriptAdapter.MultScriptViewHolder>() {

    val path = LShare.AppConf + "/" + currentPackageName + ".txt"


    @SuppressLint("NotifyDataSetChanged", "MissingInflatedId", "InflateParams")
    @OptIn(DelicateCoroutinesApi::class)
    inner class MultScriptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.card)
        val scrptName: TextView = itemView.findViewById(R.id.name)
        val description: TextView = itemView.findViewById(R.id.description)
        val logoText: TextView = itemView.findViewById(R.id.logoText)
        val switchWidget: MaterialSwitch = itemView.findViewById(R.id.switchWidget)


        init {

            card.setOnClickListener {

                val innerList = conf[bindingAdapterPosition].value as org.json.JSONArray
                // 进入编辑界面
                val intent = Intent(context, AppsEdit::class.java)
                intent.putExtra("packageName", currentPackageName)
                intent.putExtra("appName", appName)
                intent.putExtra("scripName", conf[bindingAdapterPosition].key)
                intent.putExtra("scriptDescription", innerList[1].toString())
                launcher.launch(intent)

            }
            card.setOnLongClickListener {

                val view = LayoutInflater.from(context).inflate(R.layout.dialog_scriptdetail, null)
                val bsd_logoText: TextView = view.findViewById<TextView>(R.id.logoText)
                val bsd_name: TextView = view.findViewById<TextView>(R.id.name)
                val bsd_description: TextView = view.findViewById<TextView>(R.id.description)
                val bsd_author: TextView = view.findViewById<TextView>(R.id.author)

                var bsd = BottomSheetDialog(context)

                bsd_logoText.text = conf[bindingAdapterPosition].key[0].toString()
                bsd_name.text = conf[bindingAdapterPosition].key

                val innerList = conf[bindingAdapterPosition].value as org.json.JSONArray

                bsd_description.text = (innerList[1] as String).takeUnless { it.isEmpty() } ?: "无"

                val param =
                    LShare.parseParameters(read(LShare.DIR + "/" + LShare.AppScript + "/" + currentPackageName + "/" + conf[bindingAdapterPosition].key + ".lua"))

                bsd_author.text = param?.author ?: "无"


                val window = bsd.window
                window?.setFlags(
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                )
                bsd.setContentView(view)
                bsd.show()



                true
            }

            switchWidget.setOnClickListener {

                val innerList = conf[bindingAdapterPosition].value as org.json.JSONArray

                conf[bindingAdapterPosition].setValue(
                    org.json.JSONArray(
                        arrayOf(
                            switchWidget.isChecked,
                            innerList[1],
                            innerList[2]
                        )
                    )
                )

                GlobalScope.launch(Dispatchers.IO) {
                    LShare.writeMap(path, conf.associate { it.key to it.value }.toMutableMap())
                }
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MultScriptViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_multscript, parent, false) // 加载自定义布局
        return MultScriptViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MultScriptViewHolder, position: Int) {
        val innerList = conf[position].value as org.json.JSONArray
        holder.scrptName.text = conf[position].key
        holder.logoText.text = conf[position].key[0].toString()
        holder.switchWidget.isChecked = innerList[0] as Boolean
        if (innerList[1].toString().isNotEmpty()) {
            holder.description.text = innerList[1] as String
        }


//            holder.timeText.text = logs[position].substringBefore(".")
//            holder.logText.text = logs[position].substringAfter("LuaXposed: ")
    }

    override fun getItemCount(): Int {
        return conf.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(new: MutableList<MutableMap.MutableEntry<String, Any?>>) {
        conf.clear()
        conf.addAll(new)
        notifyDataSetChanged() // 通知 RecyclerView 更新数据
    }

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("NotifyDataSetChanged")
    fun removeItem(position: Int, context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle("提示")
            .setMessage("确定删除吗?")
            .setPositiveButton("确定") { dialog, _ ->
                LShare.rm(LShare.AppScript + "/" + currentPackageName + "/" + conf[position].key + ".lua")
                conf.removeAt(position)
                GlobalScope.launch(Dispatchers.IO) {
                    LShare.writeMap(
                        path,
                        conf.associate { it.key to it.value }.toMutableMap()
                    )

                }
                notifyDataSetChanged()
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                notifyDataSetChanged()
                dialog.dismiss()
            }
            .setOnDismissListener {
                notifyDataSetChanged()
            }
            .show()


    }

    fun read(path: String): String {
        if (File(path).exists()) {
            return File(path).readText()
        }
        return ""
    }
}