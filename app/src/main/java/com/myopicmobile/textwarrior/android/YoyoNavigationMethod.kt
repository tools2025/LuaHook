/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.android

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.TypedValue
import android.view.MotionEvent
import com.myopicmobile.textwarrior.common.ColorScheme
import com.myopicmobile.textwarrior.common.ColorScheme.Colorable
import com.myopicmobile.textwarrior.common.Pair

class YoyoNavigationMethod(textField: FreeScrollingTextField) : TouchNavigationMethod(textField) {
    private val _yoyoCaret: Yoyo
    private val _yoyoStart: Yoyo
    private val _yoyoEnd: Yoyo

    private var _isStartHandleTouched = false
    private var _isEndHandleTouched = false
    private var _isCaretHandleTouched = false
    private var _isShowYoyoCaret = false

    private val _yoyoSize: Int

    init {
        val dm = textField.getContext().getResources().getDisplayMetrics()
        _yoyoSize = TypedValue.applyDimension(
            2,
            (FreeScrollingTextField.BASE_TEXT_SIZE_PIXELS * 1.5).toFloat(),
            dm
        ).toInt()
        _yoyoCaret = Yoyo()
        _yoyoStart = Yoyo()
        _yoyoEnd = Yoyo()
    }

    override fun onDown(e: MotionEvent): Boolean {
        super.onDown(e)
        if (!_isCaretTouched) {
            val x = e.getX().toInt() + _textField.getScrollX()
            val y = e.getY().toInt() + _textField.getScrollY()
            _isCaretHandleTouched = _yoyoCaret.isInHandle(x, y)
            _isStartHandleTouched = _yoyoStart.isInHandle(x, y)
            _isEndHandleTouched = _yoyoEnd.isInHandle(x, y)

            if (_isCaretHandleTouched) {
                _isShowYoyoCaret = true
                _yoyoCaret.setInitialTouch(x, y)
                _yoyoCaret.invalidateHandle()
            } else if (_isStartHandleTouched) {
                _yoyoStart.setInitialTouch(x, y)
                _textField.focusSelectionStart()
                _yoyoStart.invalidateHandle()
            } else if (_isEndHandleTouched) {
                _yoyoEnd.setInitialTouch(x, y)
                _textField.focusSelectionEnd()
                _yoyoEnd.invalidateHandle()
            }
        }

        return true
    }

    override fun onUp(e: MotionEvent?): Boolean {
        _isCaretHandleTouched = false
        _isStartHandleTouched = false
        _isEndHandleTouched = false
        _yoyoCaret.clearInitialTouch()
        _yoyoStart.clearInitialTouch()
        _yoyoEnd.clearInitialTouch()
        super.onUp(e)
        return true
    }

    override fun onScroll(
        e1: MotionEvent?, e2: MotionEvent, distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (_isCaretHandleTouched) {
            //TODO find out if ACTION_UP events are actually passed to onScroll
            if ((e2.getAction() and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                onUp(e2)
            } else {
                _isShowYoyoCaret = true
                moveHandle(_yoyoCaret, e2)
            }

            return true
        } else if (_isStartHandleTouched) {
            //TODO find out if ACTION_UP events are actually passed to onScroll
            if ((e2.getAction() and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                onUp(e2)
            } else {
                moveHandle(_yoyoStart, e2)
            }

            return true
        } else if (_isEndHandleTouched) {
            //TODO find out if ACTION_UP events are actually passed to onScroll
            if ((e2.getAction() and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                onUp(e2)
            } else {
                moveHandle(_yoyoEnd, e2)
            }

            return true
        } else {
            return super.onScroll(e1, e2, distanceX, distanceY)
        }
    }

    private fun moveHandle(_yoyo: Yoyo, e: MotionEvent) {
        val foundIndex = _yoyo.findNearestChar(e.getX().toInt(), e.getY().toInt())
        val newCaretIndex = foundIndex.first

        if (newCaretIndex >= 0) {
            _textField.moveCaret(newCaretIndex)
            //snap the handle to the caret
            val newCaretBounds = _textField.getBoundingBox(newCaretIndex)
            val newX = newCaretBounds.left + _textField.getPaddingLeft()
            val newY = newCaretBounds.bottom + _textField.getPaddingTop()

            _yoyo.attachYoyo(newX, newY)
        }
    }


    override fun onSingleTapUp(e: MotionEvent): Boolean {
        val x = e.getX().toInt() + _textField.getScrollX()
        val y = e.getY().toInt() + _textField.getScrollY()

        //ignore taps on handle
        if (_yoyoCaret.isInHandle(x, y) || _yoyoStart.isInHandle(x, y) || _yoyoEnd.isInHandle(
                x,
                y
            )
        ) {
            return true
        } else {
            _isShowYoyoCaret = true
            return super.onSingleTapUp(e)
        }
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        val x = e.getX().toInt() + _textField.getScrollX()
        val y = e.getY().toInt() + _textField.getScrollY()

        //ignore taps on handle
        if (_yoyoCaret.isInHandle(x, y)) {
            _textField.selectText(true)
            return true
        } else if (_yoyoStart.isInHandle(x, y)) {
            return true
        } else {
            return super.onDoubleTap(e)
        }
    }

    override fun onLongPress(e: MotionEvent) {
        // TODO: Implement this method
        onDoubleTap(e)
    }


    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (_isCaretHandleTouched || _isStartHandleTouched || _isEndHandleTouched) {
            onUp(e2)
            return true
        } else {
            return super.onFling(e1, e2!!, velocityX, velocityY)
        }
    }

    override fun onTextDrawComplete(canvas: Canvas) {
        if (!_textField.isSelectText2()) {
            _yoyoCaret.show()
            _yoyoStart.hide()
            _yoyoEnd.hide()

            if (!_isCaretHandleTouched) {
                val caret = _textField.getBoundingBox(_textField.getCaretPosition())
                val x = caret.left + _textField.getPaddingLeft()
                val y = caret.bottom + _textField.getPaddingTop()
                _yoyoCaret.setRestingCoord(x, y)
            }
            if (_isShowYoyoCaret) _yoyoCaret.draw(canvas, _isCaretHandleTouched)
            _isShowYoyoCaret = false
        } else {
            _yoyoCaret.hide()
            _yoyoStart.show()
            _yoyoEnd.show()

            if (!(_isStartHandleTouched && _isEndHandleTouched)) {
                val caret = _textField.getBoundingBox(_textField.getSelectionStart())
                val x = caret.left + _textField.getPaddingLeft()
                val y = caret.bottom + _textField.getPaddingTop()
                _yoyoStart.setRestingCoord(x, y)

                val caret2 = _textField.getBoundingBox(_textField.getSelectionEnd())
                val x2 = caret2.left + _textField.getPaddingLeft()
                val y2 = caret2.bottom + _textField.getPaddingTop()
                _yoyoEnd.setRestingCoord(x2, y2)
            }

            _yoyoStart.draw(canvas, _isStartHandleTouched)
            _yoyoEnd.draw(canvas, _isStartHandleTouched)
        }
    }

    override fun getCaretBloat(): Rect {
        return _yoyoCaret.HANDLE_BLOAT
    }

    override fun onColorSchemeChanged(colorScheme: ColorScheme) {
        // TODO: Implement this method
        _yoyoCaret.setHandleColor(colorScheme.getColor(Colorable.CARET_BACKGROUND))
    }

    private inner class Yoyo {
        private val YOYO_STRING_RESTING_HEIGHT = _yoyoSize / 3
        private val HANDLE_RECT = Rect(0, 0, _yoyoSize, _yoyoSize)
        val HANDLE_BLOAT: Rect

        //coordinates where the top of the yoyo string is attached
        private var _anchorX = 0
        private var _anchorY = 0

        //coordinates of the top-left corner of the yoyo handle
        private var _handleX = 0
        private var _handleY = 0

        //the offset where the handle is first touched,
        //(0,0) being the top-left of the handle
        private var _xOffset = 0
        private var _yOffset = 0

        private val _brush: Paint

        var isShow: Boolean = false
            private set

        init {
            val radius = this.radius
            HANDLE_BLOAT = Rect(
                radius,
                0,
                0,
                HANDLE_RECT.bottom + YOYO_STRING_RESTING_HEIGHT
            )

            _brush = Paint()
            _brush.setColor(_textField.getColorScheme().getColor(Colorable.CARET_BACKGROUND))
            //,_brush.setStrokeWidth(2);
            _brush.setAntiAlias(true)
        }

        fun setHandleColor(color: Int) {
            // TODO: Implement this method
            _brush.setColor(color)
        }

        /**
         * Draws the yoyo handle and string. The Yoyo handle can extend into
         * the padding region.
         *
         * @param canvas
         * @param activated True if the yoyo is activated. This causes a
         * different image to be loaded.
         */
        fun draw(canvas: Canvas, activated: Boolean) {
            val radius = this.radius

            canvas.drawLine(
                _anchorX.toFloat(), _anchorY.toFloat(),
                (_handleX + radius).toFloat(), (_handleY + radius).toFloat(), _brush
            )
            canvas.drawArc(
                RectF(
                    (_anchorX - radius).toFloat(),
                    (_anchorY - radius / 2 - YOYO_STRING_RESTING_HEIGHT).toFloat(),
                    (_handleX + radius * 2).toFloat(),
                    (_handleY + radius / 2).toFloat()
                ), 60f, 60f, true, _brush
            )
            canvas.drawOval(
                RectF(
                    _handleX.toFloat(),
                    _handleY.toFloat(),
                    (_handleX + HANDLE_RECT.right).toFloat(),
                    (_handleY + HANDLE_RECT.bottom).toFloat()
                ), _brush
            )
        }

        val radius: Int
            get() = HANDLE_RECT.right / 2

        /**
         * Clear the yoyo at the current position and attaches it to (x, y),
         * with the handle hanging directly below.
         */
        fun attachYoyo(x: Int, y: Int) {
            invalidateYoyo() //clear old position
            setRestingCoord(x, y)
            invalidateYoyo() //update new position
        }


        /**
         * Sets the yoyo string to be attached at (x, y), with the handle
         * hanging directly below, but does not trigger any redrawing
         */
        fun setRestingCoord(x: Int, y: Int) {
            _anchorX = x
            _anchorY = y
            _handleX = x - this.radius
            _handleY = y + YOYO_STRING_RESTING_HEIGHT
        }

        fun invalidateYoyo() {
            val handleCenter = _handleX + this.radius
            val x0: Int
            val x1: Int
            val y0: Int
            val y1: Int
            if (handleCenter >= _anchorX) {
                x0 = _anchorX
                x1 = handleCenter + 1
            } else {
                x0 = handleCenter
                x1 = _anchorX + 1
            }

            if (_handleY >= _anchorY) {
                y0 = _anchorY
                y1 = _handleY
            } else {
                y0 = _handleY
                y1 = _anchorY
            }

            //invalidate the string area
            _textField.invalidate(x0, y0, x1, y1)
            invalidateHandle()
        }

        fun invalidateHandle() {
            val handleExtent = Rect(
                _handleX, _handleY,
                _handleX + HANDLE_RECT.right, _handleY + HANDLE_RECT.bottom
            )
            _textField.invalidate(handleExtent)
        }

        /**
         * This method projects a yoyo string directly above the handle and
         * determines which character it should be attached to, or -1 if no
         * suitable character can be found.
         *
         * (handleX, handleY) is the handle origin in screen coordinates,
         * where (0, 0) is the top left corner of the textField, regardless of
         * its internal scroll values.
         *
         * @return Pair.first contains the nearest character while Pair.second
         * is the exact character found by a strict search
         */
        fun findNearestChar(handleX: Int, handleY: Int): Pair {
            val attachedLeft = screenToViewX(handleX) - _xOffset + this.radius
            val attachedBottom = screenToViewY(handleY) - _yOffset - YOYO_STRING_RESTING_HEIGHT - 2

            return Pair(
                _textField.coordToCharIndex(attachedLeft, attachedBottom),
                _textField.coordToCharIndexStrict(attachedLeft, attachedBottom)
            )
        }

        /**
         * Records the coordinates of the initial down event on the
         * handle so that subsequent movement events will result in the
         * handle being offset correctly.
         *
         * Does not check if isInside(x, y). Calling methods have
         * to ensure that (x, y) is within the handle area.
         */
        fun setInitialTouch(x: Int, y: Int) {
            _xOffset = x - _handleX
            _yOffset = y - _handleY
        }

        fun clearInitialTouch() {
            _xOffset = 0
            _yOffset = 0
        }

        fun show() {
            this.isShow = true
        }

        fun hide() {
            this.isShow = false
        }

        fun isInHandle(x: Int, y: Int): Boolean {
            return this.isShow && (x >= _handleX && x < (_handleX + HANDLE_RECT.right) && y >= _handleY && y < (_handleY + HANDLE_RECT.bottom)
                    )
        }


    } //end inner class
}
