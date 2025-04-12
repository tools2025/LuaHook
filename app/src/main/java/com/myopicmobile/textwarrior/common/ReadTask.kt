package com.myopicmobile.textwarrior.common

import android.R
import android.app.ProgressDialog
import android.os.AsyncTask
import com.kulipai.luahook.LuaEditor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

class ReadTask(private val _edit: LuaEditor, private val _file: File) :
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
        _dlg.setTitle("正在打开")
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
            val fi = FileInputStream(_file)
            val buf = readAll(fi)
            return String(buf)
        } catch (e: Exception) {
            _dlg.setMessage(e.message)
        }
        return ""
    }

    override fun onPostExecute(result: Any?) {
        // TODO: Implement this method
        super.onPostExecute(result)
        _edit.setText(result as String?)
        _dlg.dismiss()
    }

    override fun onProgressUpdate(values: Array<Any?>) {
        // TODO: Implement this method
        _dlg.setProgress(current)
        super.onProgressUpdate(*values)
    }


    @Throws(IOException::class)
    private fun readAll(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream(4096)
        val buffer = ByteArray(4096)
        var n = 0
        current = 0
        while (-1 != (input.read(buffer).also { n = it })) {
            output.write(buffer, 0, n)
            current += n
            publishProgress()
        }
        val ret = output.toByteArray()
        output.close()
        return ret
    }

    companion object {
        var current: Int = 0
            get() =// TODO: Implement this method
                field
            private set
    }
}
