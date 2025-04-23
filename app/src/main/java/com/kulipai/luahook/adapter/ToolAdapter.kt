package com.kulipai.luahook.adapter

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.androlua.LuaEditor
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.kulipai.luahook.R
import com.kulipai.luahook.util.d

class ToolAdapter(
    private val symbols: List<String>,
    private val editor: LuaEditor,
    private val context: Context
) :
    RecyclerView.Adapter<ToolAdapter.ToolViewHolder>() {


    @SuppressLint("MissingInflatedId")
    inner class ToolViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val toolTextView: TextView = itemView.findViewById(R.id.toolTextView) // 使用自定义布局中的 ID
        val toolItem: MaterialCardView = itemView.findViewById(R.id.toolItem) // 使用自定义布局中的 ID

        init {
            toolItem.setOnClickListener {
                val symbol = symbols[adapterPosition]
                var idx = editor.selectionStart
                if (editor.isSelected && symbol == "\"") {
                    editor.insert(editor.selectionStart, symbol)
                    editor.insert(editor.selectionEnd, symbol)
                }
                when (adapterPosition) {
                    0 -> {
                        val view = LayoutInflater.from(context).inflate(R.layout.dialog_hook, null)

//                        val bottomSheetDialog = BottomSheetDialog(context)
//                        bottomSheetDialog.setTitle("Hook方法")
//                        bottomSheetDialog.setContentView(view)
//                        val ok = view.findViewById<MaterialButton>(R.id.ok)
//                        ok.setOnClickListener {
//                            val className: String =
//                                view.findViewById<TextInputEditText>(R.id.className).text.toString()
//                            val funcName: String =
//                                view.findViewById<TextInputEditText>(R.id.funcName).text.toString()
//                            val param: String =
//                                view.findViewById<TextInputEditText>(R.id.param).text.toString()
//                            var p = param.split(",").joinToString(",") { "\"${it.trim()}\"" }
//                            val hookLua =
//                                "hook(\"$className\",\nlpparam.classLoader,\n\"$funcName\",\n$p,\nfunction(it)\n\nend,\nfunction(it)\n\nend)"
//                            editor.insert(editor.selectionStart, hookLua)
//                            bottomSheetDialog.dismiss()
//                        }
//                        val cancel = view.findViewById<MaterialButton>(R.id.cancel)
//                        cancel.setOnClickListener {
//                            bottomSheetDialog.dismiss()
//                        }
//                        bottomSheetDialog.show()
                        MaterialAlertDialogBuilder(context)
                            .setTitle("Hook方法")
                            .setView(view)
                            .setPositiveButton("确定") { dialog, which ->
                                val className: String =
                                    view.findViewById<TextInputEditText>(R.id.className).text.toString()
                                val funcName: String =
                                    view.findViewById<TextInputEditText>(R.id.funcName).text.toString()
                                val param: String =
                                    view.findViewById<TextInputEditText>(R.id.param).text.toString()
                                var p = param.split(",").joinToString(",") { "\"${it.trim()}\"" }
                                val hookLua =
                                    "hook(\"$className\",\nlpparam.classLoader,\n\"$funcName\",\n$p,\nfunction(it)\n\nend,\nfunction(it)\n\nend)"
                                editor.insert(editor.selectionStart, hookLua)
                                dialog.dismiss()
                            }
                            .setNegativeButton("取消") { dialog, which ->
                                dialog.dismiss()
                            }
                            .show()
                    }

                    1 -> {
                        val view =
                            LayoutInflater.from(context).inflate(R.layout.dialog_hookctotr, null)

//                        val bottomSheetDialog = BottomSheetDialog(context)
//                        bottomSheetDialog.setTitle("Hook构造方法")
//                        bottomSheetDialog.setContentView(view)
//                        val ok = view.findViewById<MaterialButton>(R.id.ok)
//                        ok.setOnClickListener {
//                            val className: String =
//                                view.findViewById<TextInputEditText>(R.id.className).text.toString()
//                            val param: String =
//                                view.findViewById<TextInputEditText>(R.id.param).text.toString()
//                            var p = param.split(",").joinToString(",") { "\"${it.trim()}\"" }
//
//                            val hookLua =
//                                "hookcotr(\"$className\",\nlpparam.classLoader,\n$p,\nfunction(it)\n\nend,\nfunction(it)\n\nend)"
//                            editor.insert(editor.selectionStart, hookLua)
//                            bottomSheetDialog.dismiss()
//                        }
//                        val cancel = view.findViewById<MaterialButton>(R.id.cancel)
//                        cancel.setOnClickListener {
//                            bottomSheetDialog.dismiss()
//                        }
//                        bottomSheetDialog.show()
                        MaterialAlertDialogBuilder(context)
                            .setTitle("Hook构造函数")
                            .setView(view)
                            .setPositiveButton("确定") { dialog, which ->
                                val className: String =
                                    view.findViewById<TextInputEditText>(R.id.className).text.toString()
                                val param: String =
                                    view.findViewById<TextInputEditText>(R.id.param).text.toString()
                                var p = param.split(",").joinToString(",") { "\"${it.trim()}\"" }

                                val hookLua =
                                    "hookcotr(\"$className\",\nlpparam.classLoader,\n$p,\nfunction(it)\n\nend,\nfunction(it)\n\nend)"
                                editor.insert(editor.selectionStart, hookLua)
                                dialog.dismiss()
                            }
                            .setNegativeButton("取消") { dialog, which ->
                                dialog.dismiss()
                            }
                            .show()
                    }

                    2 -> {

                        val view =
                            LayoutInflater.from(context).inflate(R.layout.dialog_funcsign, null)

//                        val bottomSheetDialog = BottomSheetDialog(context)
//                        bottomSheetDialog.setTitle("Hook方法")
//                        bottomSheetDialog.setContentView(view)
//                        val ok = view.findViewById<MaterialButton>(R.id.ok)
//                        ok.setOnClickListener {
//                            val input: String =
//                                view.findViewById<TextInputEditText>(R.id.input).text.toString()
//
//                            var methodInfo: MethodInfo
//                            try {
//                                methodInfo = parseMethodSignature(input)
//
//                            } catch (e: Exception) {
//                                Toast.makeText(context, "参数错误", Toast.LENGTH_SHORT).show()
//                                return@setOnClickListener
//                            }
//
//
//                            val par = methodInfo.parameterTypes
//                            par.size.toString().d()
//                            var p =
//                                if (par.isEmpty()) "" else "\n" + par.joinToString(",") { "\"${it.trim()}\"" } + ","
//
//                            var hookLua: String
//                            if (methodInfo.methodName == "<init>") {
//                                hookLua =
//                                    "hookcotr(\"${methodInfo.className}\",\nlpparam.classLoader,$p\nfunction(it)\n\nend,\nfunction(it)\n\nend)"
//                            } else {
//                                hookLua =
//                                    "hook(\"${methodInfo.className}\",\nlpparam.classLoader,\n\"${methodInfo.methodName}\",$p\nfunction(it)\n\nend,\nfunction(it)\n\nend)"
//
//                            }
//
//                            editor.insert(editor.selectionStart, hookLua)
//                            bottomSheetDialog.dismiss()
//                        }
//                        val cancel = view.findViewById<MaterialButton>(R.id.cancel)
//                        cancel.setOnClickListener {
//                            bottomSheetDialog.dismiss()
//                        }
//                        bottomSheetDialog.show()
                        MaterialAlertDialogBuilder(context)
                            .setTitle("导入方法签名")
                            .setView(view)
                            .setPositiveButton("确定") { dialog, which ->
                                val input: String =
                                    view.findViewById<TextInputEditText>(R.id.input).text.toString()

                                var methodInfo: MethodInfo
                                try {
                                    methodInfo = parseMethodSignature(input)

                                } catch (e: Exception) {
                                    Toast.makeText(context, "参数错误", Toast.LENGTH_SHORT).show()
                                    return@setPositiveButton
                                }


                                val par = methodInfo.parameterTypes
                                par.size.toString().d()
                                var p =
                                    if (par.isEmpty()) "" else "\n" + par.joinToString(",") { "\"${it.trim()}\"" } + ","

                                var hookLua: String
                                if (methodInfo.methodName == "<init>") {
                                    hookLua =
                                        "hookcotr(\"${methodInfo.className}\",\nlpparam.classLoader,$p\nfunction(it)\n\nend,\nfunction(it)\n\nend)"
                                } else {
                                    hookLua =
                                        "hook(\"${methodInfo.className}\",\nlpparam.classLoader,\n\"${methodInfo.methodName}\",$p\nfunction(it)\n\nend,\nfunction(it)\n\nend)"

                                }

                                editor.insert(editor.selectionStart, hookLua)
                                dialog.dismiss()
                            }
                            .setNegativeButton("取消") { dialog, which ->
                                dialog.dismiss()
                            }
                            .show()
                        view.findViewById<TextInputEditText>(R.id.input).setText(getClipboardText())
                    }

                    else -> {}
                }


                // 在实际应用中，这里会将符号插入到编辑框中
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tool, parent, false) // 加载自定义布局
        return ToolViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
        holder.toolTextView.text = symbols[position]
    }

    override fun getItemCount(): Int {
        return symbols.size
    }


    fun parseMethodSignature(signature: String): MethodInfo {
        // 正则表达式匹配方法签名
        val regex =
            """L(?<className>[^;]+);->(?<methodName>[^\\(]+)\((?<parameterTypes>.*)\)(?<returnType>.+)""".toRegex()
        val matchResult = regex.matchEntire(signature)

        if (matchResult != null) {
            val classNameWithSlashes = matchResult.groups["className"]?.value ?: ""
            val className = classNameWithSlashes.replace("/", ".")
            val methodName = matchResult.groups["methodName"]?.value ?: ""
            val parameterTypesStr = matchResult.groups["parameterTypes"]?.value ?: ""
            val returnTypeSig = matchResult.groups["returnType"]?.value ?: ""

            val parameterTypes = parseParameterTypes(parameterTypesStr)
            val returnType = parseType(returnTypeSig)

            return MethodInfo(className, methodName, parameterTypes, returnType)
        } else {
            throw IllegalArgumentException("Invalid method signature: $signature")
        }
    }

    fun parseParameterTypes(parameterTypesStr: String): List<String> {
        val types = mutableListOf<String>()
        var i = 0
        while (i < parameterTypesStr.length) {
            val type = parseType(parameterTypesStr.substring(i))
            types.add(type)
            i += getTypeLength(parameterTypesStr.substring(i))
        }
        return types
    }

    fun parseType(typeSig: String): String {
        return when (typeSig.first()) {
            'L' -> {
                val semiIndex = typeSig.indexOf(';')
                if (semiIndex == -1) throw IllegalArgumentException("Invalid object type signature: $typeSig")
                typeSig.substring(1, semiIndex).replace("/", ".")
            }

            '[' -> parseType(typeSig.substring(1)) + "[]"
            'Z' -> "boolean"
            'B' -> "byte"
            'C' -> "char"
            'D' -> "double"
            'F' -> "float"
            'I' -> "int"
            'J' -> "long"
            'S' -> "short"
            'V' -> "void"
            else -> throw IllegalArgumentException("Unknown type signature: $typeSig")
        }
    }

    fun getTypeLength(typeSig: String): Int {
        return when (typeSig.first()) {
            'L' -> typeSig.indexOf(';') + 1
            '[' -> 1 + getTypeLength(typeSig.substring(1))
            else -> 1
        }

    }

    data class MethodInfo(
        val className: String,
        val methodName: String,
        val parameterTypes: List<String>,
        val returnType: String
    )

    private fun getClipboardText(): String {
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboardManager.primaryClip

        if (clipData != null && clipData.itemCount > 0) {
            val item = clipData.getItemAt(0)
            val text = item.text
            if (text != null) {
                return text.toString()
            }
        }
        return ""
    }

}