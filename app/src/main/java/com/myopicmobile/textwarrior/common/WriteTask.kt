package com.myopicmobile.textwarrior.common

import android.R
import android.app.ProgressDialog
import android.os.AsyncTask
import com.kulipai.luahook.LuaEditor
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

/**
 * Created by Administrator on 2018/07/18 0018.
 */
class WriteTask(private val _edit: LuaEditor, private val _file: File) :
    AsyncTask<Any?, Any?, Any?>() {
    private val _dlg: ProgressDialog


    val min: Int
        get() =//new Future;
            // TODO: Implement this method
            0


    val max: Int
        get() =// TODO: Implement this method
            _len.toInt()


    protected val _buf: Document

    private val _len: Long

    constructor(edit: LuaEditor, fileName: String) : this(edit, File(fileName))

    init {
        _len = _file.length()
        _buf = Document(_edit)
        _dlg = ProgressDialog(_edit.getContext())
        _dlg.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        _dlg.setTitle("正在保存")
        _dlg.setIcon(R.drawable.ic_dialog_info)
        _dlg.setMax(_len.toInt())
    }

    fun start() {
        // TODO: Implement this method
        execute()
        _dlg.show()
    }

    override fun doInBackground(p1: Array<Any?>?): Any {
        // TODO: Implement this method
        try {
            val fi =
                BufferedWriter(OutputStreamWriter(BufferedOutputStream(FileOutputStream(_file))))
            fi.write(_edit.text.toString())
            return true
        } catch (e: Exception) {
            _dlg.setMessage(e.message)
        }
        return ""
    }

    override fun onPostExecute(result: Any?) {
        // TODO: Implement this method
        super.onPostExecute(result)
        _dlg.dismiss()
    }

    override fun onProgressUpdate(values: Array<Any?>) {
        // TODO: Implement this method
        _dlg.setProgress(current)
        super.onProgressUpdate(*values)
    }


    companion object {
        val current: Int = 0
            get() =// TODO: Implement this method
                field
    }
}
