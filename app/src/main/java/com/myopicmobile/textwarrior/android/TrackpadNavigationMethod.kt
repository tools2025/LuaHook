/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.android

import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.atan2

class TrackpadNavigationMethod(textField: FreeScrollingTextField) :
    TouchNavigationMethod(textField) {
    private var fling = 0

    override fun onDown(e: MotionEvent): Boolean {
        fling = 0
        MOVEMENT_PIXELS = _textField!!.rowHeight() * 2
        return true
    }

    override fun onUp(e: MotionEvent?): Boolean {
        _xAccum = 0.0f
        _yAccum = 0.0f
        fling = 0
        super.onUp(e)
        return true
    }

    override fun onScroll(
        e1: MotionEvent?, e2: MotionEvent, distanceX: Float,
        distanceY: Float
    ): Boolean {
        /*if (fling == 0)
                   if (Math.abs(distanceX) > Math.abs(distanceY))
                       fling = 1;
                   else
                       fling = -1;*/

        var distanceX = distanceX
        var distanceY = distanceY
        if (fling == 1) distanceY = 0f
        else if (fling == -1) distanceX = 0f

        moveCaretWithTrackpad(-distanceX, -distanceY)

        //TODO find out if ACTION_UP events are actually passed to onScroll
        if ((e2.getAction() and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
            onUp(e2)
        }

        return true
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        onUp(e2)
        return true
    }


    private var _xAccum = 0.0f
    private var _yAccum = 0.0f

    init {
        MOVEMENT_PIXELS = _textField!!.rowHeight() * 2
    }

    private fun moveCaretWithTrackpad(distanceX: Float, distanceY: Float) {
        //reset accumulators when polarity of displacement changes
        if ((_xAccum < 0 && distanceX > 0) || (_xAccum > 0 && distanceX < 0)) {
            _xAccum = 0f
        }
        if ((_yAccum < 0 && distanceY > 0) || (_yAccum > 0 && distanceY < 0)) {
            _yAccum = 0f
        }

        val angle = atan2(abs(distanceX.toDouble()), abs(distanceY.toDouble()))

        if (angle >= MIN_ATAN) {
            //non-negligible x-axis movement
            val x = _xAccum + distanceX
            var xUnits: Int = (x.toInt()) / MOVEMENT_PIXELS
            _xAccum = x - (xUnits * MOVEMENT_PIXELS)

            while (xUnits > 0) {
                _textField!!.moveCaretRight()
                --xUnits
                if (fling == 0) fling = 1
            }
            while (xUnits < 0) {
                _textField!!.moveCaretLeft()
                ++xUnits
                if (fling == 0) fling = 1
            }
        }

        if ((Math.PI / 2 - angle) >= MIN_ATAN) {
            //non-negligible y-axis movement
            val y = _yAccum + distanceY
            val yUnits: Int = (y.toInt()) / MOVEMENT_PIXELS
            _yAccum = y - (yUnits * MOVEMENT_PIXELS)

            for (i in yUnits downTo 1) {
                _textField!!.moveCaretDown()
                if (fling == 0) fling = -1
            }
            for (i in yUnits..-1) {
                _textField!!.moveCaretUp()
                if (fling == 0) fling = -1
            }
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        return super.onSingleTapConfirmed(e)
    }

    override fun onLongPress(e: MotionEvent) {
        _textField!!.setSelected(!_textField!!.isSelectText)
        _textField!!.setSelectionRange(_textField!!.caretPosition, 0)
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        _textField!!.setSelected(!_textField!!.isSelectText)
        _textField!!.setSelectionRange(_textField!!.caretPosition, 0)
        return true
    }

    companion object {
        //number of pixels to scroll to move the caret one unit
        private var MOVEMENT_PIXELS = 16

        //for use in determining whether the displacement is mainly on the x or y axis
        private const val MIN_ATAN = 0.322 // == atan(1/3)
    }
}
