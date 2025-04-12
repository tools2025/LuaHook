/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.common

import kotlin.math.abs

/**
 * A LRU cache that stores the last seek line and its corresponding index so
 * that future lookups can start from the cached position instead of
 * the beginning of the file
 *
 * _cache.Pair.First = line index
 * _cache.Pair.Second = character offset of first character in that line
 *
 * TextBufferCache always has one valid entry (0,0) signifying that in line 0,
 * the first character is at offset 0. This is true even for an "empty" file,
 * which is not really empty because TextBuffer inserts a EOF character in it.
 *
 * Therefore, _cache[0] is always occupied by the entry (0,0). It is not affected
 * by invalidateCache, cache miss, etc. operations
 */
class TextBufferCache {
    private val _cache = arrayOfNulls<Pair>(CACHE_SIZE)

    init {
        _cache[0] = Pair(0, 0) // invariant lineIndex and charOffset relation
        for (i in 1..<CACHE_SIZE) {
            _cache[i] = Pair(-1, -1)
            // -1 line index is used implicitly in calculations in getNearestMatch
        }
    }

    //TODO consider extracting common parts with getNearestCharOffset(int)
    fun getNearestLine(lineIndex: Int): Pair? {
        var nearestMatch = 0
        var nearestDistance = Int.Companion.MAX_VALUE
        for (i in 0..<CACHE_SIZE) {
            val distance = abs((lineIndex - _cache[i]!!.first).toDouble()).toInt()
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestMatch = i
            }
        }

        val nearestEntry = _cache[nearestMatch]
        makeHead(nearestMatch)
        return nearestEntry
    }

    fun getNearestCharOffset(charOffset: Int): Pair? {
        var nearestMatch = 0
        var nearestDistance = Int.Companion.MAX_VALUE
        for (i in 0..<CACHE_SIZE) {
            val distance = abs((charOffset - _cache[i]!!.second).toDouble()).toInt()
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestMatch = i
            }
        }

        val nearestEntry = _cache[nearestMatch]
        makeHead(nearestMatch)
        return nearestEntry
    }

    /**
     * Place _cache[newHead] at the top of the list
     */
    private fun makeHead(newHead: Int) {
        if (newHead == 0) {
            return
        }

        val temp = _cache[newHead]
        for (i in newHead downTo 2) {
            _cache[i] = _cache[i - 1]
        }
        _cache[1] = temp // _cache[0] is always occupied by (0,0)
    }

    fun updateEntry(lineIndex: Int, charOffset: Int) {
        if (lineIndex <= 0) {
            // lineIndex 0 always has 0 charOffset; ignore. Also ignore negative lineIndex
            return
        }

        if (!replaceEntry(lineIndex, charOffset)) {
            insertEntry(lineIndex, charOffset)
        }
    }

    private fun replaceEntry(lineIndex: Int, charOffset: Int): Boolean {
        for (i in 1..<CACHE_SIZE) {
            if (_cache[i]!!.first == lineIndex) {
                _cache[i]!!.second = charOffset
                return true
            }
        }
        return false
    }

    private fun insertEntry(lineIndex: Int, charOffset: Int) {
        makeHead(CACHE_SIZE - 1) // rotate right list of entries
        // replace head (most recently used entry) with new entry
        _cache[1] = Pair(lineIndex, charOffset)
    }

    /**
     * Invalidate all cache entries that have char offset >= fromCharOffset
     */
    fun invalidateCache(fromCharOffset: Int) {
        for (i in 1..<CACHE_SIZE) {
            if (_cache[i]!!.second >= fromCharOffset) {
                _cache[i] = Pair(-1, -1)
            }
        }
    }

    companion object {
        private const val CACHE_SIZE = 4 // minimum = 1
    }
}
