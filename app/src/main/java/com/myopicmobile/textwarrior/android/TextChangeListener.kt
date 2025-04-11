package com.myopicmobile.textwarrior.android

interface TextChangeListener {
    fun onNewLine(c: String?, _caretPosition: Int, p2: Int)


    fun onDel(text: CharSequence?, _caretPosition: Int, newCursorPosition: Int)

    fun onAdd(text: CharSequence?, _caretPosition: Int, newCursorPosition: Int)
}
