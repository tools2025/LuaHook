/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.common

/**
 * Singleton class that represents a non-programming language without keywords,
 * operators etc.
 */
class LanguageNonProg private constructor() : Language() {
    init {
        super.keywords = Companion.keywords
        super.setOperators(operators)
    }

    override val isProgLang: Boolean
        get() = false

    public override fun isEscapeChar(c: Char): Boolean {
        return false
    }

    public override fun isDelimiterA(c: Char): Boolean {
        return false
    }

    public override fun isDelimiterB(c: Char): Boolean {
        return false
    }

    public override fun isLineAStart(c: Char): Boolean {
        return false
    }

    public override fun isLineStart(c0: Char, c1: Char): Boolean {
        return false
    }

    public override fun isMultilineStartDelimiter(c0: Char, c1: Char): Boolean {
        return false
    }

    public override fun isMultilineEndDelimiter(c0: Char, c1: Char): Boolean {
        return false
    }

    companion object {
        private var _theOne: Language? = null

        private val keywords = arrayOf<String?>()

        private val operators = charArrayOf()


        val instance: Language
            get() {
                if (_theOne == null) {
                    _theOne = LanguageNonProg()
                }
                return _theOne!!
            }
    }
}
