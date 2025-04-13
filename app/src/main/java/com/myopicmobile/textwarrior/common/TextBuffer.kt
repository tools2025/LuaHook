/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.common

import com.myopicmobile.textwarrior.common.TextWarriorException.Companion.assertVerbose
import java.util.Vector

//TODO Have all methods work with charOffsets and move all gap handling to logicalToRealIndex()
open class TextBuffer : CharSequence {
    override val length: Int
        get() = this.textLength - 1

    override fun get(index: Int): Char {
        return _contents[logicalToRealIndex(index)]
    }

    protected var _contents: CharArray
    protected var _gapStartIndex: Int

    /** One past end of gap  */
    protected var _gapEndIndex: Int

    @get:Synchronized
    var lineCount: Int
        protected set

    /** The number of times memory is allocated for the buffer  */
    private var _allocMultiplier: Int
    private val _cache: TextBufferCache
    private val _undoStack: UndoStack

    /** Continuous seq of chars that have the same format (color, font, etc.)  */
    protected var _spans: MutableList<Pair>? = null


    init {
        _contents = CharArray(MIN_GAP_SIZE + 1) // extra char for EOF
        _contents[MIN_GAP_SIZE] = Language.EOF
        _allocMultiplier = 1
        _gapStartIndex = 0
        _gapEndIndex = MIN_GAP_SIZE
        this.lineCount = 1
        _cache = TextBufferCache()
        _undoStack = UndoStack(this)
    }

    @Synchronized
    fun setBuffer(newBuffer: CharArray, textSize: Int, lineCount: Int) {
        _contents = newBuffer
        initGap(textSize)
        this.lineCount = lineCount
        _allocMultiplier = 1
    }

    @Synchronized
    fun setBuffer(newBuffer: CharArray) {
        var lineCount = 1
        val len = newBuffer.size
        for (i in 0..<len) {
            if (newBuffer[i] == '\n') lineCount++
        }
        setBuffer(newBuffer, len, lineCount)
    }


    /**
     * Returns a string of text corresponding to the line with index lineNumber.
     *
     * @param lineNumber The index of the line of interest
     * @return The text on lineNumber, or an empty string if the line does not exist
     */
    @Synchronized
    fun getLine(lineNumber: Int): String {
        val startIndex = getLineOffset(lineNumber)

        if (startIndex < 0) {
            return ""
        }
        val lineSize = getLineSize(lineNumber)

        return subSequence(startIndex, lineSize).toString()
    }

    /**
     * Get the offset of the first character of the line with index lineNumber.
     * The offset is counted from the beginning of the text.
     *
     * @param lineNumber The index of the line of interest
     * @return The character offset of lineNumber, or -1 if the line does not exist
     */
    @Synchronized
    fun getLineOffset(lineNumber: Int): Int {
        if (lineNumber < 0) {
            return -1
        }

        // start search from nearest known lineIndex~charOffset pair
        val cachedEntry = _cache.getNearestLine(lineNumber)
        val cachedLine = cachedEntry!!.first
        val cachedOffset = cachedEntry.second

        val offset: Int
        if (lineNumber > cachedLine) {
            offset = findCharOffset(lineNumber, cachedLine, cachedOffset)
        } else if (lineNumber < cachedLine) {
            offset = findCharOffsetBackward(lineNumber, cachedLine, cachedOffset)
        } else {
            offset = cachedOffset
        }

        if (offset >= 0) {
            // seek successful
            _cache.updateEntry(lineNumber, offset)
        }

        return offset
    }

    /*
     * Precondition: startOffset is the offset of startLine
     */
    private fun findCharOffset(targetLine: Int, startLine: Int, startOffset: Int): Int {
        var workingLine = startLine
        var offset = logicalToRealIndex(startOffset)

        assertVerbose(
            isValid(startOffset),
            "findCharOffsetBackward: Invalid startingOffset given"
        )

        while ((workingLine < targetLine) && (offset < _contents.size)) {
            if (_contents[offset] == Language.NEWLINE) {
                ++workingLine
            }
            ++offset

            // skip the gap
            if (offset == _gapStartIndex) {
                offset = _gapEndIndex
            }
        }

        if (workingLine != targetLine) {
            return -1
        }
        return realToLogicalIndex(offset)
    }

    /*
     * Precondition: startOffset is the offset of startLine
     */
    private fun findCharOffsetBackward(targetLine: Int, startLine: Int, startOffset: Int): Int {
        if (targetLine == 0) {
            return 0
        }

        assertVerbose(
            isValid(startOffset),
            "findCharOffsetBackward: Invalid startOffset given"
        )

        var workingLine = startLine
        var offset = logicalToRealIndex(startOffset)
        while (workingLine > (targetLine - 1) && offset >= 0) {
            // skip behind the gap
            if (offset == _gapEndIndex) {
                offset = _gapStartIndex
            }
            --offset

            if (_contents[offset] == Language.NEWLINE) {
                --workingLine
            }
        }

        var charOffset: Int
        if (offset >= 0) {
            // now at the '\n' of the line before targetLine
            charOffset = realToLogicalIndex(offset)
            ++charOffset
        } else {
            assertVerbose(
                false,
                "findCharOffsetBackward: Invalid cache entry or line arguments"
            )
            charOffset = -1
        }

        return charOffset
    }

    /**
     * Get the line number that charOffset is on
     *
     * @return The line number that charOffset is on, or -1 if charOffset is invalid
     */
    @Synchronized
    fun findLineNumber(charOffset: Int): Int {
        if (!isValid(charOffset)) {
            return -1
        }

        val cachedEntry = _cache.getNearestCharOffset(charOffset)
        var line = cachedEntry!!.first
        var offset = logicalToRealIndex(cachedEntry.second)
        val targetOffset = logicalToRealIndex(charOffset)
        var lastKnownLine = -1
        var lastKnownCharOffset = -1

        if (targetOffset > offset) {
            // search forward
            while ((offset < targetOffset) && (offset < _contents.size)) {
                if (_contents[offset] == Language.NEWLINE) {
                    ++line
                    lastKnownLine = line
                    lastKnownCharOffset = realToLogicalIndex(offset) + 1
                }

                ++offset
                // skip the gap
                if (offset == _gapStartIndex) {
                    offset = _gapEndIndex
                }
            }
        } else if (targetOffset < offset) {
            // search backward
            while ((offset > targetOffset) && (offset > 0)) {
                // skip behind the gap
                if (offset == _gapEndIndex) {
                    offset = _gapStartIndex
                }
                --offset

                if (_contents[offset] == Language.NEWLINE) {
                    lastKnownLine = line
                    lastKnownCharOffset = realToLogicalIndex(offset) + 1
                    --line
                }
            }
        }


        if (offset == targetOffset) {
            if (lastKnownLine != -1) {
                // cache the lookup entry
                _cache.updateEntry(lastKnownLine, lastKnownCharOffset)
            }
            return line
        } else {
            return -1
        }
    }


    /**
     * Finds the number of char on the specified line.
     * All valid lines contain at least one char, which may be a non-printable
     * one like \n, \t or EOF.
     *
     * @return The number of chars in lineNumber, or 0 if the line does not exist.
     */
    @Synchronized
    fun getLineSize(lineNumber: Int): Int {
        var lineLength = 0
        var pos = getLineOffset(lineNumber)

        if (pos != -1) {
            pos = logicalToRealIndex(pos)
            //TODO consider adding check for (pos < _contents.length) in case EOF is not properly set
            while (_contents[pos] != Language.NEWLINE &&
                _contents[pos] != Language.EOF
            ) {
                ++lineLength
                ++pos

                // skip the gap
                if (pos == _gapStartIndex) {
                    pos = _gapEndIndex
                }
            }
            ++lineLength // account for the line terminator char
        }

        return lineLength
    }

    /**
     * Gets the char at charOffset
     * Does not do bounds-checking.
     *
     * @return The char at charOffset. If charOffset is invalid, the result
     * is undefined.
     */

    /**
     * Gets up to maxChars number of chars starting at charOffset
     *
     * @return The chars starting from charOffset, up to a maximum of maxChars.
     * An empty array is returned if charOffset is invalid or maxChars is
     * non-positive.
     */
    @Synchronized
    override fun subSequence(charOffset: Int, maxChars: Int): CharSequence {
        if (!isValid(charOffset) || maxChars <= 0) {
            return ""
        }
        var totalChars = maxChars
        if ((charOffset + totalChars) > this.textLength) {
            totalChars = this.textLength - charOffset
        }
        var realIndex = logicalToRealIndex(charOffset)
        val chars = CharArray(totalChars)

        for (i in 0..<totalChars) {
            chars[i] = _contents[realIndex]
            ++realIndex
            // skip the gap
            if (realIndex == _gapStartIndex) {
                realIndex = _gapEndIndex
            }
        }

        return String(chars)
    }

    /**
     * Gets charCount number of consecutive characters starting from _gapStartIndex.
     *
     * Only UndoStack should use this method. No error checking is done.
     */
    fun gapSubSequence(charCount: Int): CharArray {
        val chars = CharArray(charCount)

        for (i in 0..<charCount) {
            chars[i] = _contents[_gapStartIndex + i]
        }

        return chars
    }

    /**
     * Insert all characters in c into position charOffset.
     *
     * No error checking is done
     */
    @Synchronized
    open fun insert(
        c: CharArray, charOffset: Int, timestamp: Long,
        undoable: Boolean
    ) {
        if (undoable) {
            _undoStack.captureInsert(charOffset, c.size, timestamp)
        }

        val insertIndex = logicalToRealIndex(charOffset)

        // shift gap to insertion point
        if (insertIndex != _gapEndIndex) {
            if (isBeforeGap(insertIndex)) {
                shiftGapLeft(insertIndex)
            } else {
                shiftGapRight(insertIndex)
            }
        }

        if (c.size >= gapSize()) {
            growBufferBy(c.size - gapSize())
        }

        for (i in c.indices) {
            if (c[i] == Language.NEWLINE) {
                ++this.lineCount
            }
            _contents[_gapStartIndex] = c[i]
            ++_gapStartIndex
        }

        _cache.invalidateCache(charOffset)

        onAdd(charOffset, c.size)
    }

    /**
     * Deletes up to totalChars number of char starting from position
     * charOffset, inclusive.
     *
     * No error checking is done
     */
    @Synchronized
    open fun delete(
        charOffset: Int, totalChars: Int, timestamp: Long,
        undoable: Boolean
    ) {
        if (undoable) {
            _undoStack.captureDelete(charOffset, totalChars, timestamp)
        }

        val newGapStart = charOffset + totalChars

        // shift gap to deletion point
        if (newGapStart != _gapStartIndex) {
            if (isBeforeGap(newGapStart)) {
                shiftGapLeft(newGapStart)
            } else {
                shiftGapRight(newGapStart + gapSize())
            }
        }

        // increase gap size
        for (i in 0..<totalChars) {
            --_gapStartIndex
            if (_contents[_gapStartIndex] == Language.NEWLINE) {
                --this.lineCount
            }
        }

        _cache.invalidateCache(charOffset)
        onDel(charOffset, totalChars)
    }

    private fun onAdd(charOffset: Int, totalChars: Int) {
        val s = findSpan(charOffset)
        val p = _spans!!.get(s.first)
        p.first = p.first + totalChars
    }

    private fun onDel(charOffset: Int, totalChars: Int) {
        var totalChars = totalChars
        val len = length
        if (len == 0) {
            clearSpans()
            return
        }

        val s = findSpan2(charOffset)
        if (totalChars == 1) {
            val p = _spans!!.get(s.first)
            if (p.first > 1) {
                p.first = p.first - 1
            } else {
                _spans!!.removeAt(s.first)
            }
        } else {
            val o = s.second
            var l = charOffset - o
            val p = _spans!!.get(s.first)
            if (p.first > l) {
                p.first = p.first - l
            } else {
                _spans!!.removeAt(s.first)
            }
            totalChars -= l
            if (totalChars > 0) {
                for (i in s.first downTo 0) {
                    val p1 = _spans!!.get(i)
                    l = p1.first
                    if (totalChars > l) {
                        totalChars -= l
                        _spans!!.removeAt(i)
                    } else {
                        p1.first = p1.first - totalChars
                        break
                    }
                }
            }
        }
    }

    private fun findSpan(index: Int): Pair {
        val n = _spans!!.size
        var cur = 0
        for (i in 0..<n) {
            val l = _spans!!.get(i).first
            cur += l
            if (cur >= index) return Pair(i, cur - l)
        }
        return Pair(0, 0)
    }

    private fun findSpan2(index: Int): Pair {
        val n = _spans!!.size
        var cur = 0
        for (i in 0..<n) {
            val l = _spans!!.get(i).first
            cur += l
            if (cur > index) return Pair(i, cur - l)
        }
        return Pair(0, 0)
    }


    /**
     * Moves _gapStartIndex by displacement units. Note that displacement can be
     * negative and will move _gapStartIndex to the left.
     *
     * Only UndoStack should use this method to carry out a simple undo/redo
     * of insertions/deletions. No error checking is done.
     */
    @Synchronized
    open fun shiftGapStart(displacement: Int) {
        if (displacement >= 0) {
            onAdd(_gapStartIndex, displacement)
            this.lineCount += countNewlines(_gapStartIndex, displacement)
        } else {
            onDel(_gapStartIndex, 0 - displacement)
            this.lineCount -= countNewlines(_gapStartIndex + displacement, -displacement)
        }

        _gapStartIndex += displacement
        _cache.invalidateCache(realToLogicalIndex(_gapStartIndex - 1) + 1)
    }

    //does NOT skip the gap when examining consecutive positions
    private fun countNewlines(start: Int, totalChars: Int): Int {
        var newlines = 0
        for (i in start..<(start + totalChars)) {
            if (_contents[i] == Language.NEWLINE) {
                ++newlines
            }
        }

        return newlines
    }

    /**
     * Adjusts gap so that _gapStartIndex is at newGapStart
     */
    protected fun shiftGapLeft(newGapStart: Int) {
        while (_gapStartIndex > newGapStart) {
            --_gapEndIndex
            --_gapStartIndex
            _contents[_gapEndIndex] = _contents[_gapStartIndex]
        }
    }

    /**
     * Adjusts gap so that _gapEndIndex is at newGapEnd
     */
    protected fun shiftGapRight(newGapEnd: Int) {
        while (_gapEndIndex < newGapEnd) {
            _contents[_gapStartIndex] = _contents[_gapEndIndex]
            ++_gapStartIndex
            ++_gapEndIndex
        }
    }

    /**
     * Create a gap at the start of _contents[] and tack a EOF at the end.
     * Precondition: real contents are from _contents[0] to _contents[contentsLength-1]
     */
    protected fun initGap(contentsLength: Int) {
        var toPosition = _contents.size - 1
        _contents[toPosition--] = Language.EOF // mark end of file
        var fromPosition = contentsLength - 1
        while (fromPosition >= 0) {
            _contents[toPosition--] = _contents[fromPosition--]
        }
        _gapStartIndex = 0
        _gapEndIndex = toPosition + 1 // went one-past in the while loop
    }

    /**
     * Copies _contents into a buffer that is larger by
     * minIncrement + INITIAL_GAP_SIZE * _allocCount bytes.
     *
     * _allocMultiplier doubles on every call to this method, to avoid the
     * overhead of repeated allocations.
     */
    protected fun growBufferBy(minIncrement: Int) {
        //TODO handle new size > MAX_INT or allocation failure
        val increasedSize: Int = minIncrement + MIN_GAP_SIZE * _allocMultiplier
        val temp = CharArray(_contents.size + increasedSize)
        var i = 0
        while (i < _gapStartIndex) {
            temp[i] = _contents[i]
            ++i
        }

        i = _gapEndIndex
        while (i < _contents.size) {
            temp[i + increasedSize] = _contents[i]
            ++i
        }

        _gapEndIndex += increasedSize
        _contents = temp
        _allocMultiplier = _allocMultiplier shl 1
    }

    @get:Synchronized
    val textLength: Int
        /**
         * Returns the total number of characters in the text, including the
         * EOF sentinel char
         */
        get() = _contents.size - gapSize()

    @Synchronized
    fun isValid(charOffset: Int): Boolean {
        return (charOffset >= 0 && charOffset < this.textLength)
    }

    protected fun gapSize(): Int {
        return _gapEndIndex - _gapStartIndex
    }

    protected fun logicalToRealIndex(i: Int): Int {
        if (isBeforeGap(i)) {
            return i
        } else {
            return i + gapSize()
        }
    }

    protected fun realToLogicalIndex(i: Int): Int {
        if (isBeforeGap(i)) {
            return i
        } else {
            return i - gapSize()
        }
    }

    protected fun isBeforeGap(i: Int): Boolean {
        return i < _gapStartIndex
    }

    fun clearSpans() {
        _spans = Vector<Pair>()
        _spans!!.add(Pair(length, Lexer.NORMAL))
    }

    var spans: MutableList<Pair>
        get() = _spans!!
        /**
         * Sets the spans to use in the document.
         * Spans are continuous sequences of characters that have the same format
         * like color, font, etc.
         *
         * @param spans A collection of Pairs, where Pair.first is the start
         * position of the token, and Pair.second is the type of the token.
         */
        set(spans) {
            _spans = spans
        }

    val isBatchEdit: Boolean
        /**
         * Returns true if in batch edit mode
         */
        get() = _undoStack.isBatchEdit

    /**
     * Signals the beginning of a series of insert/delete operations that can be
     * undone/redone as a single unit
     */
    fun beginBatchEdit() {
        _undoStack.beginBatchEdit()
    }

    /**
     * Signals the end of a series of insert/delete operations that can be
     * undone/redone as a single unit
     */
    fun endBatchEdit() {
        _undoStack.endBatchEdit()
    }

    fun canUndo(): Boolean {
        return _undoStack.canUndo()
    }

    fun canRedo(): Boolean {
        return _undoStack.canRedo()
    }

    fun undo(): Int {
        return _undoStack.undo()
    }

    fun redo(): Int {
        return _undoStack.redo()
    }

    override fun toString(): String {
        // TODO: Implement this method
        val len = this.textLength
        val buf = StringBuffer()
        for (i in 0..<len) {
            val c = get(i)
            if (c == Language.EOF) break
            buf.append(c)
        }
        return String(buf)
    }


    companion object {
        // gap size must be > 0 to insert into full buffers successfully
        protected const val MIN_GAP_SIZE: Int = 50

        /**
         * Calculate the implementation size of the char array needed to store
         * textSize number of characters.
         * The implementation size may be greater than textSize because of buffer
         * space, cached characters and so on.
         *
         * @param textSize
         * @return The size, measured in number of chars, required by the
         * implementation to store textSize characters, or -1 if the request
         * cannot be satisfied
         */
        fun memoryNeeded(textSize: Int): Int {
            val bufferSize = (textSize + MIN_GAP_SIZE + 1).toLong() // extra char for EOF
            if (bufferSize < Int.Companion.MAX_VALUE) {
                return bufferSize.toInt()
            }
            return -1
        }
    }
}