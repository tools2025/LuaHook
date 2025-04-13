/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.common

import com.myopicmobile.textwarrior.common.Lexer.Companion.language
import com.myopicmobile.textwarrior.common.TextWarriorException.Companion.fail
import kotlin.math.max
import kotlin.math.min


class LinearSearchStrategy : SearchStrategy {
    // only applicable to replaceAll operation
    override var progress: Int = 0
        private set

    override fun wrappedFind(
        src: DocumentProvider?,
        target: String?,
        start: Int,
        isCaseSensitive: Boolean,
        isWholeWord: Boolean
    ): Int {
        // search towards end of doc first...

        var foundOffset = find(
            src, target, start, src!!.docLength(),
            isCaseSensitive, isWholeWord
        )
        // ...then from beginning of doc
        if (foundOffset < 0) {
            foundOffset = find(
                src, target, 0, start,
                isCaseSensitive, isWholeWord
            )
        }

        return foundOffset
    }

    override fun find(
        src: DocumentProvider?,
        target: String?,
        start: Int,
        end: Int,
        isCaseSensitive: Boolean,
        isWholeWord: Boolean
    ): Int {
        var start = start
        var end = end
        if (target?.length == 0) {
            return -1
        }
        if (start < 0) {
            fail("TextBuffer.find: Invalid start position")
            start = 0
        }
        if (end > src!!.docLength()) {
            fail("TextBuffer.find: Invalid end position")
            end = src.docLength()
        }

        end = min(end.toDouble(), (src.docLength() - target!!.length + 1).toDouble()).toInt()
        var offset = start
        while (offset < end) {
            if (equals(src, target.toString(), offset, isCaseSensitive) &&
                (!isWholeWord || isSandwichedByWhitespace(src, offset, target.length))
            ) {
                break
            }

            ++offset
            ++this.progress
        }

        if (offset < end) {
            return offset
        } else {
            return -1
        }
    }

    override fun wrappedFindBackwards(
        src: DocumentProvider?,
        target: String?,
        start: Int,
        isCaseSensitive: Boolean,
        isWholeWord: Boolean
    ): Int {
        // search towards beginning of doc first...

        var foundOffset = findBackwards(
            src, target, start, -1,
            isCaseSensitive, isWholeWord
        )
        // ...then from end of doc
        if (foundOffset < 0) {
            foundOffset = findBackwards(
                src, target, src!!.docLength() - 1, start,
                isCaseSensitive, isWholeWord
            )
        }

        return foundOffset
    }


    override fun findBackwards(
        src: DocumentProvider?,
        target: String?,
        start: Int,
        end: Int,
        isCaseSensitive: Boolean,
        isWholeWord: Boolean
    ): Int {
        var start = start
        var end = end
        if (target?.length == 0) {
            return -1
        }
        if (start >= src!!.docLength()) {
            fail("Invalid start position given to TextBuffer.find")
            start = src.docLength() - 1
        }
        if (end < -1) {
            fail("Invalid end position given to TextBuffer.find")
            end = -1
        }
        var offset = min(start.toDouble(), (src.docLength() - target!!.length).toDouble()).toInt()
        while (offset > end) {
            if (equals(src, target.toString(), offset, isCaseSensitive) &&
                (!isWholeWord || isSandwichedByWhitespace(src, offset, target.length))
            ) {
                break
            }

            --offset
        }

        if (offset > end) {
            return offset
        } else {
            return -1
        }
    }

    override fun replaceAll(
        src: DocumentProvider?,
        searchText: String?,
        replacementText: String?,
        mark: Int,
        isCaseSensitive: Boolean,
        isWholeWord: Boolean
    ): Pair? {
        var replacementCount = 0
        var anchor = mark
        this.progress = 0

        val replacement = replacementText?.toCharArray()
        var foundIndex = find(
            src, searchText, 0, src!!.docLength(),
            isCaseSensitive, isWholeWord
        )
        val timestamp = System.nanoTime()

        src.beginBatchEdit()
        while (foundIndex != -1) {
            src.deleteAt(foundIndex, searchText!!.length, timestamp)
            src.insertBefore(replacement, foundIndex, timestamp)
            if (foundIndex < anchor) {
                // adjust anchor because of differences in doc length
                // after word replacement
                anchor += replacementText!!.length - searchText.length
            }
            ++replacementCount
            this.progress += searchText.length //skip replaced chars
            foundIndex = find(
                src,
                searchText,
                foundIndex + replacementText!!.length,
                src.docLength(),
                isCaseSensitive,
                isWholeWord
            )
        }
        src.endBatchEdit()

        return Pair(replacementCount, max(anchor.toDouble(), 0.0).toInt())
    }


    protected fun equals(
        src: DocumentProvider, target: String,
        srcOffset: Int, isCaseSensitive: Boolean
    ): Boolean {
        if ((src.docLength() - srcOffset) < target.length) {
            //compared range in src must at least be as long as target
            return false
        }

        var i: Int
        i = 0
        while (i < target.length) {
            if (isCaseSensitive &&
                target.get(i) != src.get(i + srcOffset)
            ) {
                return false
            }
            // for case-insensitive search, compare both strings in lower case
            if (!isCaseSensitive &&
                target.get(i).lowercaseChar() != src.get(i + srcOffset).lowercaseChar()
            ) {
                return false
            }

            ++i
        }

        return true
    }

    /**
     * Checks if a word starting at startPosition with size length is bounded
     * by whitespace.
     */
    protected fun isSandwichedByWhitespace(
        src: DocumentProvider,
        start: Int, length: Int
    ): Boolean {
        val charSet = language
        val startWithWhitespace = if (start == 0)
            true
        else
            charSet.isWhitespace(src.get(start - 1))

        val end = start + length
        val endWithWhitespace = if (end == src.docLength())
            true
        else
            charSet.isWhitespace(src.get(end))

        return (startWithWhitespace && endWithWhitespace)
    }
}
