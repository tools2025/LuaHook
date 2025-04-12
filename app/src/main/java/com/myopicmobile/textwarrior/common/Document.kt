/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.common

import com.myopicmobile.textwarrior.common.TextWarriorException.Companion.fail

/**
 * A decorator of TextBuffer that adds word-wrap capabilities.
 *
 * Positions for word wrap row breaks are stored here.
 * Word-wrap is enabled by default.
 */
class Document(
    /** Contains info related to printing of characters, display size and so on  */
    private var _metrics: TextFieldMetrics
) : TextBuffer() {
    private var _isWordWrap = false

    /** A table containing the character offset of every row in the document.
     * Values are valid only in word-wrap mode  */
    private var _rowTable: ArrayList<Int?>? = null

    init {
        resetRowTable()
    }

    fun setText(text: CharSequence) {
        var lineCount = 1
        val len = text.length
        val ca = CharArray(memoryNeeded(len))
        for (i in 0..<len) {
            ca[i] = text.get(i)
            if (text.get(i) == '\n') lineCount++
        }
        setBuffer(ca, len, lineCount)
    }

    private fun resetRowTable() {
        val rowTable = ArrayList<Int?>()
        rowTable.add(0) //every document contains at least 1 row
        _rowTable = rowTable
    }

    fun setMetrics(metrics: TextFieldMetrics) {
        _metrics = metrics
    }

    var isWordWrap: Boolean
        get() = _isWordWrap
        /**
         * Enable/disable word wrap. If enabled, the document is immediately
         * analyzed for word wrap breakpoints, which might take an arbitrarily long time.
         */
        set(enable) {
            if (enable && !_isWordWrap) {
                _isWordWrap = true
                analyzeWordWrap()
            } else if (!enable && _isWordWrap) {
                _isWordWrap = false
                analyzeWordWrap()
            }
        }


    @Synchronized
    override fun delete(charOffset: Int, totalChars: Int, timestamp: Long, undoable: Boolean) {
        super.delete(charOffset, totalChars, timestamp, undoable)

        val startRow = findRowNumber(charOffset)
        val analyzeEnd = findNextLineFrom(charOffset)
        updateWordWrapAfterEdit(startRow, analyzeEnd, -totalChars)
    }

    @Synchronized
    override fun insert(c: CharArray, charOffset: Int, timestamp: Long, undoable: Boolean) {
        super.insert(c, charOffset, timestamp, undoable)

        val startRow = findRowNumber(charOffset)
        val analyzeEnd = findNextLineFrom(charOffset + c.size)
        updateWordWrapAfterEdit(startRow, analyzeEnd, c.size)
    }

    /**
     * Moves _gapStartIndex by displacement units. Note that displacement can be
     * negative and will move _gapStartIndex to the left.
     *
     * Only UndoStack should use this method to carry out a simple undo/redo
     * of insertions/deletions. No error checking is done.
     */
    @Synchronized
    override fun shiftGapStart(displacement: Int) {
        super.shiftGapStart(displacement)

        if (displacement != 0) {
            val startOffset = if (displacement > 0)
                _gapStartIndex - displacement
            else
                _gapStartIndex
            val startRow = findRowNumber(startOffset)
            val analyzeEnd = findNextLineFrom(_gapStartIndex)
            updateWordWrapAfterEdit(startRow, analyzeEnd, displacement)
        }
    }

    //No error checking is done on parameters.
    private fun findNextLineFrom(charOffset: Int): Int {
        var lineEnd = logicalToRealIndex(charOffset)

        while (lineEnd < _contents.size) {
            // skip the gap
            if (lineEnd == _gapStartIndex) {
                lineEnd = _gapEndIndex
            }

            if (_contents[lineEnd] == Language.NEWLINE ||
                _contents[lineEnd] == Language.EOF
            ) {
                break
            }

            ++lineEnd
        }

        return realToLogicalIndex(lineEnd) + 1
    }

    private fun updateWordWrapAfterEdit(startRow: Int, analyzeEnd: Int, delta: Int) {
        var startRow = startRow
        if (startRow > 0) {
            // if the first word becomes shorter or an inserted space breaks it
            // up, it may fit the previous line, so analyse that line too
            --startRow
        }
        val analyzeStart = _rowTable!!.get(startRow)!!

        //changes only affect the rows after startRow
        removeRowMetadata(startRow + 1, analyzeEnd - delta)
        adjustOffsetOfRowsFrom(startRow + 1, delta)
        analyzeWordWrap(startRow + 1, analyzeStart, analyzeEnd)
    }

    /**
     * Removes row offset info from fromRow to the row that endOffset is on,
     * inclusive.
     *
     * No error checking is done on parameters.
     */
    private fun removeRowMetadata(fromRow: Int, endOffset: Int) {
        while (fromRow < _rowTable!!.size &&
            _rowTable!!.get(fromRow)!! <= endOffset
        ) {
            _rowTable!!.removeAt(fromRow)
        }
    }

    private fun adjustOffsetOfRowsFrom(fromRow: Int, offset: Int) {
        for (i in fromRow..<_rowTable!!.size) {
            _rowTable!!.set(i, _rowTable!!.get(i)!! + offset)
        }
    }

    fun analyzeWordWrap() {
        resetRowTable()

        if (_isWordWrap && !hasMinimumWidthForWordWrap()) {
            if (_metrics.rowWidth > 0) {
                fail("Text field has non-zero width but still too small for word wrap")
            }
            // _metrics.getRowWidth() might legitmately be zero when the text field has not been layout yet
            return
        }

        analyzeWordWrap(1, 0, getTextLength())
    }

    private fun hasMinimumWidthForWordWrap(): Boolean {
        val maxWidth = _metrics.rowWidth
        //assume the widest char is 2ems wide
        return (maxWidth >= 2 * _metrics.getAdvance('M'))
    }

    //No error checking is done on parameters.
    //A word consists of a sequence of 0 or more non-whitespace characters followed by
    //exactly one whitespace character. Note that EOF is considered whitespace.
    private fun analyzeWordWrap(rowIndex: Int, startOffset: Int, endOffset: Int) {
        if (!_isWordWrap) {
            var offset = logicalToRealIndex(startOffset)
            val end = logicalToRealIndex(endOffset)
            val rowTable = ArrayList<Int?>()

            while (offset < end) {
                // skip the gap
                if (offset == _gapStartIndex) {
                    offset = _gapEndIndex
                }
                val c = _contents[offset]
                if (c == Language.NEWLINE) {
                    //start a new row
                    rowTable.add(realToLogicalIndex(offset) + 1)
                }
                ++offset
            }
            _rowTable!!.addAll(rowIndex, rowTable)
            return
        }
        if (!hasMinimumWidthForWordWrap()) {
            fail("Not enough space to do word wrap")
            return
        }

        val rowTable = ArrayList<Int?>()
        var offset = logicalToRealIndex(startOffset)
        val end = logicalToRealIndex(endOffset)
        var potentialBreakPoint = startOffset
        var wordExtent = 0
        val maxWidth = _metrics.rowWidth
        var remainingWidth = maxWidth

        while (offset < end) {
            // skip the gap
            if (offset == _gapStartIndex) {
                offset = _gapEndIndex
            }

            val c = _contents[offset]
            wordExtent += _metrics.getAdvance(c)

            val isWhitespace =
                (c == ' ' || c == Language.TAB || c == Language.NEWLINE || c == Language.EOF)

            if (isWhitespace) {
                //full word obtained
                if (wordExtent <= remainingWidth) {
                    remainingWidth -= wordExtent
                } else if (wordExtent > maxWidth) {
                    //handle a word too long to fit on one row
                    var current = logicalToRealIndex(potentialBreakPoint)
                    remainingWidth = maxWidth

                    //start the word on a new row, if it isn't already
                    if (potentialBreakPoint != startOffset && (rowTable.isEmpty() ||
                                potentialBreakPoint != rowTable.get(rowTable.size - 1))
                    ) {
                        rowTable.add(potentialBreakPoint)
                    }

                    while (current <= offset) {
                        // skip the gap
                        if (current == _gapStartIndex) {
                            current = _gapEndIndex
                        }

                        val advance = _metrics.getAdvance(_contents[current])
                        if (advance > remainingWidth) {
                            rowTable.add(realToLogicalIndex(current))
                            remainingWidth = maxWidth - advance
                        } else {
                            remainingWidth -= advance
                        }

                        ++current
                    }
                } else {
                    //invariant: potentialBreakPoint != startOffset
                    //put the word on a new row
                    rowTable.add(potentialBreakPoint)
                    remainingWidth = maxWidth - wordExtent
                }

                wordExtent = 0
                potentialBreakPoint = realToLogicalIndex(offset) + 1
            }

            if (c == Language.NEWLINE) {
                //start a new row
                rowTable.add(potentialBreakPoint)
                remainingWidth = maxWidth
            }

            ++offset
        }

        //merge with existing row table
        _rowTable!!.addAll(rowIndex, rowTable)
    }

    fun getRow(rowNumber: Int): String {
        val rowSize = getRowSize(rowNumber)
        if (rowSize == 0) {
            return ""
        }

        val startIndex = _rowTable!!.get(rowNumber)!!
        return subSequence(startIndex, rowSize).toString()
    }

    fun getRowSize(rowNumber: Int): Int {
        if (isInvalidRow(rowNumber)) {
            return 0
        }

        if (rowNumber != (_rowTable!!.size - 1)) {
            return _rowTable!!.get(rowNumber + 1)!! - _rowTable!!.get(rowNumber)!!
        } else {
            //last row
            return getTextLength() - _rowTable!!.get(rowNumber)!!
        }
    }

    val rowCount: Int
        get() = _rowTable!!.size

    fun getRowOffset(rowNumber: Int): Int {
        if (isInvalidRow(rowNumber)) {
            return -1
        }

        return _rowTable!!.get(rowNumber)!!
    }

    /**
     * Get the row number that charOffset is on
     *
     * @return The row number that charOffset is on, or -1 if charOffset is invalid
     */
    fun findRowNumber(charOffset: Int): Int {
        if (!isValid(charOffset)) {
            return -1
        }

        //binary search of _rowTable
        var right = _rowTable!!.size - 1
        var left = 0
        while (right >= left) {
            val mid = (left + right) / 2
            val nextLineOffset =
                (if ((mid + 1) < _rowTable!!.size) _rowTable!!.get(mid + 1) else getTextLength())!!
            if (charOffset >= _rowTable!!.get(mid)!! && charOffset < nextLineOffset) {
                return mid
            }

            if (charOffset >= nextLineOffset) {
                left = mid + 1
            } else {
                right = mid - 1
            }
        }

        //should not be here
        return -1
    }


    protected fun isInvalidRow(rowNumber: Int): Boolean {
        return rowNumber < 0 || rowNumber >= _rowTable!!.size
    }


    interface TextFieldMetrics {
        /**
         * Returns printed width of c.
         *
         * @param c Character to measure
         * @return Advance of character, in pixels
         */
        fun getAdvance(c: Char): Int

        /**
         * Returns the maximum width available for a row of text to be layout. This
         * should not be larger than the width of the text field.
         *
         * @return Maximum width of a row, in pixels
         */
        val rowWidth: Int
    }
}