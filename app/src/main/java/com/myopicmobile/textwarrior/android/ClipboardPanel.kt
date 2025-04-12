package com.myopicmobile.textwarrior.android

import android.R
import android.annotation.SuppressLint
import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem

class ClipboardPanel(protected var _textField: FreeScrollingTextField) {
    val context: Context
    private var _clipboardActionMode: ActionMode? = null

    init {
        this.context = _textField.getContext()
    }


    fun show() {
        startClipboardAction()
    }

    fun hide() {
        stopClipboardAction()
    }

    fun startClipboardAction() {
        // TODO: Implement this method
        if (_clipboardActionMode == null) _textField.startActionMode(object : ActionMode.Callback {
            @SuppressLint("ResourceType")
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                // TODO: Implement this method
                _clipboardActionMode = mode
                mode.setTitle(R.string.selectTextMode)
                val array = context.getTheme().obtainStyledAttributes(
                    intArrayOf(
                        R.attr.actionModeSelectAllDrawable,
                        R.attr.actionModeCutDrawable,
                        R.attr.actionModeCopyDrawable,
                        R.attr.actionModePasteDrawable,
                    )
                )
                menu.add(0, 0, 0, context.getString(R.string.selectAll))
                    .setShowAsActionFlags(2)
                    .setAlphabeticShortcut('a')
                    .setIcon(array.getDrawable(0))

                menu.add(0, 1, 0, context.getString(R.string.cut))
                    .setShowAsActionFlags(2)
                    .setAlphabeticShortcut('x')
                    .setIcon(array.getDrawable(1))

                menu.add(0, 2, 0, context.getString(R.string.copy))
                    .setShowAsActionFlags(2)
                    .setAlphabeticShortcut('c')
                    .setIcon(array.getDrawable(2))

                menu.add(0, 3, 0, context.getString(R.string.paste))
                    .setShowAsActionFlags(2)
                    .setAlphabeticShortcut('v')
                    .setIcon(array.getDrawable(3))
                array.recycle()
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                // TODO: Implement this method
                return false
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                // TODO: Implement this method
                when (item.getItemId()) {
                    0 -> _textField.selectAll()
                    1 -> {
                        _textField.cut()
                        mode.finish()
                    }

                    2 -> {
                        _textField.copy()
                        mode.finish()
                    }

                    3 -> {
                        _textField.paste()
                        mode.finish()
                    }
                }
                return false
            }

            override fun onDestroyActionMode(p1: ActionMode?) {
                // TODO: Implement this method
                _textField.selectText(false)
                _clipboardActionMode = null
            }
        })
    }

    fun stopClipboardAction() {
        if (_clipboardActionMode != null) {
            _clipboardActionMode!!.finish()
            _clipboardActionMode = null
        }
    }
}
