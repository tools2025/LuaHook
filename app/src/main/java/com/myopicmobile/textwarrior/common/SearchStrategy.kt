/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.common

interface SearchStrategy {
    /**
     * Searches for target, starting from start (inclusive),
     * and stopping at end (exclusive).
     *
     * @return charOffset of found string; -1 if not found
     */
    fun find(
        src: DocumentProvider?, target: String?, start: Int, end: Int,
        isCaseSensitive: Boolean, isWholeWord: Boolean
    ): Int

    /**
     * Searches for target, starting from start (inclusive),
     * wrapping around to the beginning of document and
     * stopping at start (exclusive).
     *
     * @return charOffset of found string; -1 if not found
     */
    fun wrappedFind(
        src: DocumentProvider?, target: String?, start: Int,
        isCaseSensitive: Boolean, isWholeWord: Boolean
    ): Int

    /**
     * Searches backwards from startCharOffset (inclusive),
     * and stopping at end (exclusive).
     *
     * @return charOffset of found string; -1 if not found
     */
    fun findBackwards(
        src: DocumentProvider?, target: String?, start: Int, end: Int,
        isCaseSensitive: Boolean, isWholeWord: Boolean
    ): Int

    /**
     * Searches backwards from start (inclusive), wrapping around to
     * the end of document and stopping at start (exclusive).
     *
     * @return charOffset of found string; -1 if not found
     */
    fun wrappedFindBackwards(
        src: DocumentProvider?, target: String?, start: Int,
        isCaseSensitive: Boolean, isWholeWord: Boolean
    ): Int

    /**
     * Replace all matches of searchText in src with replacementText.
     *
     * @param mark Optional. A position in src that can be tracked for changes.
     * After replacements are made, the position may be shifted because of
     * insertion/deletion of text before it. The new position of mark is
     * returned in Pair.second. If mark is an invalid position, Pair.second
     * is undefined.
     *
     * @return Pair.first is the number of replacements made.
     * Pair.second is new position of mark after replacements are made.
     */
    fun replaceAll(
        src: DocumentProvider?, searchText: String?,
        replacementText: String?, mark: Int,
        isCaseSensitive: Boolean, isWholeWord: Boolean
    ): Pair?


    /**
     * The number of characters that have been examined by the current find
     * operation. This method is not synchronized, and the value returned
     * may be outdated.
     *
     * @return The number of characters searched so far
     */
    val progress: Int
}
