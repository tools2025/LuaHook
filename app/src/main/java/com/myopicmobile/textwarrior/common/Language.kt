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
 * Base class for programming language syntax.
 * By default, C-like symbols and operators are included, but not keywords.
 */
abstract class Language {
    protected var _keywords: HashMap<String?, Int?> = HashMap<String?, Int?>(0)
    protected var _names: HashMap<String?, Int?> = HashMap<String?, Int?>(0)
    protected var _bases: HashMap<String?, Array<String?>?> = HashMap<String?, Array<String?>?>(0)
    protected var _users: HashMap<String?, Int?> = HashMap<String?, Int?>(0)
    protected var _operators: HashMap<Char?, Int?> = generateOperators(BASIC_C_OPERATORS)

    private val _ueserCache = ArrayList<String?>()
    var userWord: Array<String?> = arrayOfNulls<String>(0)
        private set
    private lateinit var _keyword: Array<String?>
    private lateinit var _name: Array<String?>

    fun updateUserWord() {
        // TODO: Implement this method
        val uw = arrayOfNulls<String>(_ueserCache.size)
        this.userWord = _ueserCache.toArray<String?>(uw)
    }

    var names: Array<String?>?
        get() = _name
        set(names) {
            if (names != null) {
                _name = names
            }
            val buf = ArrayList<String?>()
            _names = HashMap<String?, Int?>(names!!.size)
            for (i in names.indices) {
                if (!buf.contains(names[i])) buf.add(names[i])
                _names.put(names[i], Lexer.NAME)
            }
            _name = arrayOfNulls<String>(buf.size)
            buf.toArray<String?>(_name)
        }

    fun getBasePackage(name: String?): Array<String?>? {
        return _bases.get(name)
    }

    var keywords: Array<String?>?
        get() = _keyword
        set(keywords) {
            if (keywords != null) {
                _keyword = keywords
            }
            _keywords = HashMap<String?, Int?>(keywords!!.size)
            for (i in keywords.indices) {
                _keywords.put(keywords[i], Lexer.KEYWORD)
            }
        }

    fun addBasePackage(name: String?, names: Array<String?>?) {
        _bases.put(name, names)
    }

    fun removeBasePackage(name: String?) {
        _bases.remove(name)
    }

    fun clearUserWord() {
        _ueserCache.clear()
        _users.clear()
    }

    fun addUserWord(name: String?) {
        if (!_ueserCache.contains(name) && !_names.containsKey(name)) _ueserCache.add(name)
        _users.put(name, Lexer.NAME)
    }

    protected fun setOperators(operators: CharArray) {
        _operators = generateOperators(operators)
    }

    private fun generateOperators(operators: CharArray): HashMap<Char?, Int?> {
        val operatorsMap = HashMap<Char?, Int?>(operators.size)
        for (i in operators.indices) {
            operatorsMap.put(operators[i], Lexer.OPERATOR)
        }
        return operatorsMap
    }

    fun isOperator(c: Char): Boolean {
        return _operators.containsKey(Character.valueOf(c))
    }

    fun isKeyword(s: String?): Boolean {
        return _keywords.containsKey(s)
    }

    fun isName(s: String?): Boolean {
        return _names.containsKey(s)
    }

    fun isBasePackage(s: String?): Boolean {
        return _bases.containsKey(s)
    }

    fun isBaseWord(p: String?, s: String?): Boolean {
        val pkg = _bases.get(p)
        for (n in pkg!!) {
            if (n == s) return true
        }
        return false
    }

    fun isUserWord(s: String?): Boolean {
        return _users.containsKey(s)
    }

    private fun contains(a: Array<String>, s: String?): Boolean {
        for (n in a) {
            if (n == s) return true
        }
        return false
    }

    private fun contains(a: ArrayList<String>, s: String?): Boolean {
        for (n in a) {
            if (n == s) return true
        }
        return false
    }

    fun isWhitespace(c: Char): Boolean {
        return (c == ' ' || c == '\n' || c == '\t' || c == '\r' || c == '\u000c' || c == EOF)
    }

    fun isSentenceTerminator(c: Char): Boolean {
        return (c == '.')
    }

    open fun isEscapeChar(c: Char): Boolean {
        return (c == '\\')
    }

    open val isProgLang: Boolean
        /**
         * Derived classes that do not do represent C-like programming languages
         * should return false; otherwise return true
         */
        get() = true

    /**
     * Whether the word after c is a token
     */
    fun isWordStart(c: Char): Boolean {
        return false
    }

    /**
     * Whether cSc is a token, where S is a sequence of characters that are on the same line
     */
    open fun isDelimiterA(c: Char): Boolean {
        return (c == '"')
    }

    /**
     * Same concept as isDelimiterA(char), but Language and its subclasses can
     * specify a second type of symbol to use here
     */
    open fun isDelimiterB(c: Char): Boolean {
        return (c == '\'')
    }

    /**
     * Whether cL is a token, where L is a sequence of characters until the end of the line
     */
    open fun isLineAStart(c: Char): Boolean {
        return (c == '#')
    }

    /**
     * Same concept as isLineAStart(char), but Language and its subclasses can
     * specify a second type of symbol to use here
     */
    fun isLineBStart(c: Char): Boolean {
        return false
    }

    /**
     * Whether c0c1L is a token, where L is a sequence of characters until the end of the line
     */
    open fun isLineStart(c0: Char, c1: Char): Boolean {
        return (c0 == '/' && c1 == '/')
    }

    /**
     * Whether c0c1 signifies the start of a multi-line token
     */
    open fun isMultilineStartDelimiter(c0: Char, c1: Char): Boolean {
        return (c0 == '/' && c1 == '*')
    }

    /**
     * Whether c0c1 signifies the end of a multi-line token
     */
    open fun isMultilineEndDelimiter(c0: Char, c1: Char): Boolean {
        return (c0 == '*' && c1 == '/')
    }

    companion object {
        const val EOF: Char = '\uFFFF'
        const val NULL_CHAR: Char = '\u0000'
        const val NEWLINE: Char = '\n'
        const val BACKSPACE: Char = '\b'
        const val TAB: Char = '\t'
        const val GLYPH_NEWLINE: String = "\u21b5"
        const val GLYPH_SPACE: String = "\u00b7"
        const val GLYPH_TAB: String = "\u00bb"


        private val BASIC_C_OPERATORS = charArrayOf(
            '(', ')', '{', '}', '.', ',', ';', '=', '+', '-',
            '/', '*', '&', '!', '|', ':', '[', ']', '<', '>',
            '?', '~', '%', '^'
        )
    }
}
