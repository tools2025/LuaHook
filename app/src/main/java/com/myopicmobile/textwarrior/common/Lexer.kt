/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.common

import android.graphics.Rect
import com.androlua.LuaLexer
import com.androlua.LuaTokenTypes
import com.kulipai.luahook.util.d
import com.myopicmobile.textwarrior.common.TextWarriorException.Companion.fail

//import static com.myopicmobile.textwarrior.common.LuaTokenTypes;
/**
 * Does lexical analysis of a text for C-like languages.
 * The programming language syntax used is set as a static class variable.
 */
class Lexer(callback: LexCallback?) {
    var _callback: LexCallback? = null
    private var _hDoc: DocumentProvider? = null
    private var _workerThread: LexThread? = null

    fun tokenize(hDoc: DocumentProvider) {
        if (!language.isProgLang) {
            return
        }

        //tokenize will modify the state of hDoc; make a copy
        this.document = DocumentProvider(hDoc)
        if (_workerThread == null) {
            _workerThread = LexThread(this)
            _workerThread!!.start()
        } else {
            _workerThread!!.restart()
        }
    }

    fun tokenizeDone(result: MutableList<Pair>?) {
        if (_callback != null) {
            _callback!!.lexDone(result)
        }
        _workerThread = null
    }

    fun cancelTokenize() {
        if (_workerThread != null) {
            _workerThread!!.abort()
            _workerThread = null
        }
    }

    @get:Synchronized
    @set:Synchronized
    var document: DocumentProvider
        get() = _hDoc!!
        set(hDoc) {
            _hDoc = hDoc
        }

    interface LexCallback {
        fun lexDone(results: MutableList<Pair>?)
    }

    init {
        _callback = callback
    }

    private inner class LexThread(private val _lexManager: Lexer) : Thread() {
        /**
         * can be set by another thread to stop the scan immediately
         */
        private val _abort: Flag
        private var rescan = false
        private val max = 2 xor 18

        /**
         * A collection of Pairs, where Pair.first is the start
         * position of the token, and Pair.second is the type of the token.
         */
        private var _tokens: ArrayList<Pair>? = null

        init {
            _abort = Flag()
        }

        override fun run() {
            do {
                rescan = false
                _abort.clear()
                if (language is LanguageLua) tokenize()
                else tokenize2()
            } while (rescan)

            if (!_abort.isSet) {
                // lex complete
                _lexManager.tokenizeDone(_tokens)
            }
        }

        fun restart() {
            rescan = true
            _abort.set()
        }

        fun abort() {
            _abort.set()
        }


        fun tokenize() {
            val hDoc: DocumentProvider = document
            val rowCount = hDoc.rowCount
            val maxRow = 9999
            val tokens = ArrayList<Pair>(8196)
            val lines = ArrayList<Rect?>(8196)
            val lineStacks = ArrayList<Rect>(8196)
            val lineStacks2 = ArrayList<Rect>(8196)

            val lexer = LuaLexer(hDoc)
            val language: Language =
                language
            language.clearUserWord()
            try {
                var idx = 0

                var lastType: LuaTokenTypes? = null
                var lastType2: LuaTokenTypes? = null
                var lastType3: LuaTokenTypes? = null

                var lastName = ""
                var lastPair: Pair? = null
                var lastLen = 0
                var bul = StringBuilder()
                var isModule = false
                var hasDo = true
                var lastNameIdx = -1
                while (!_abort.isSet) {
                    var pair: Pair? = null
                    val type = lexer.advance()
                    if (type == null) break
                    val len = lexer.yylength()

                    if (isModule && lastType == LuaTokenTypes.STRING && type != LuaTokenTypes.STRING) {
                        val mod = bul.toString()
                        if (bul.length > 2) language.addUserWord(mod.substring(1, mod.length - 1))
                        bul = StringBuilder()
                        isModule = false
                    }

                    /*if (lastType2 == type && lastPair != null) {
                        lastPair.setFirst(lastLen += len);
                        continue;
                    }*/
                    lastLen = len
                    when (type) {
                        LuaTokenTypes.DO -> {
                            if (hasDo) {
                                lineStacks.add(
                                    Rect(
                                        lexer.yychar(),
                                        lexer.yyline(),
                                        0,
                                        lexer.yyline()
                                    )
                                )
                            }
                            hasDo = true
                            //关键字
                            tokens.add(Pair(len, KEYWORD))
                        }

                        LuaTokenTypes.WHILE, LuaTokenTypes.FOR -> {
                            hasDo = false
                            lineStacks.add(Rect(lexer.yychar(), lexer.yyline(), 0, lexer.yyline()))
                            //关键字
                            tokens.add(Pair(len, KEYWORD))
                        }

                        LuaTokenTypes.FUNCTION, LuaTokenTypes.IF, LuaTokenTypes.SWITCH -> {
                            lineStacks.add(Rect(lexer.yychar(), lexer.yyline(), 0, lexer.yyline()))
                            //关键字
                            tokens.add(Pair(len, KEYWORD))
                        }

                        LuaTokenTypes.END -> {
                            val size = lineStacks.size
                            if (size > 0) {
                                val rect = lineStacks.removeAt(size - 1)
                                rect.bottom = lexer.yyline()
                                rect.right = lexer.yychar()
                                if (rect.bottom - rect.top > 1) lines.add(rect)
                            }
                            //关键字
                            tokens.add(Pair(len, KEYWORD))
                            hasDo = true
                        }

                        LuaTokenTypes.TRUE, LuaTokenTypes.FALSE, LuaTokenTypes.NOT, LuaTokenTypes.AND, LuaTokenTypes.OR, LuaTokenTypes.THEN, LuaTokenTypes.ELSEIF, LuaTokenTypes.ELSE, LuaTokenTypes.IN, LuaTokenTypes.RETURN, LuaTokenTypes.BREAK, LuaTokenTypes.LOCAL, LuaTokenTypes.REPEAT, LuaTokenTypes.UNTIL, LuaTokenTypes.NIL, LuaTokenTypes.CASE, LuaTokenTypes.DEFAULT, LuaTokenTypes.CONTINUE, LuaTokenTypes.GOTO, LuaTokenTypes.LAMBDA, LuaTokenTypes.WHEN, LuaTokenTypes.DEFER ->                             //关键字
                            tokens.add(Pair(len, KEYWORD))

                        LuaTokenTypes.LCURLY -> {
                            lineStacks2.add(Rect(lexer.yychar(), lexer.yyline(), 0, lexer.yyline()))
                            //符号
                            tokens.add(Pair(len, OPERATOR).also { pair = it })
                        }

                        LuaTokenTypes.RCURLY -> {
                            val size2 = lineStacks2.size
                            if (size2 > 0) {
                                val rect = lineStacks2.removeAt(size2 - 1)
                                rect.bottom = lexer.yyline()
                                rect.right = lexer.yychar()
                                if (rect.bottom - rect.top > 1) lines.add(rect)
                            }
                            //符号
                            tokens.add(Pair(len, OPERATOR).also { pair = it })
                        }

                        LuaTokenTypes.LPAREN, LuaTokenTypes.RPAREN, LuaTokenTypes.LBRACK, LuaTokenTypes.RBRACK, LuaTokenTypes.COMMA, LuaTokenTypes.DOT ->                             //符号
                            tokens.add(Pair(len, OPERATOR).also { pair = it })

                        LuaTokenTypes.STRING, LuaTokenTypes.LONG_STRING -> {
                            //字符串
                            tokens.add(Pair(len, SINGLE_SYMBOL_DELIMITED_A).also { pair = it })
                            if (rowCount > maxRow) break

                            if (lastName == "require") {
                                isModule = true
                                bul.append(lexer.yytext())
                            }
                            if (lastName == "import") {
                                isModule = true
                                bul.append(lexer.yytext()[0]+lexer.yytext().substringAfterLast("."))
                            }



                        }

                        LuaTokenTypes.NAME -> {
                            if (rowCount > maxRow) {
                                tokens.add(Pair(len, NORMAL))
                                break
                            }
                            if (lastType2 == LuaTokenTypes.NUMBER) {
                                val p = tokens.get(tokens.size - 1)
                                p.second = NORMAL
                                p.first = p.first + len
                            }
                            val name = lexer.yytext()
                            if (lastType == LuaTokenTypes.FUNCTION) {
                                //函数名
                                tokens.add(Pair(len, LITERAL))
                                language.addUserWord(name)
                            } else if (language.isUserWord(name)) {
                                tokens.add(Pair(len, LITERAL))
                            } else if (lastType == LuaTokenTypes.GOTO || lastType == LuaTokenTypes.AT) {
                                tokens.add(Pair(len, LITERAL))
                            } else if (lastType == LuaTokenTypes.MULT && lastType3 == LuaTokenTypes.LOCAL) {
                                tokens.add(Pair(len, OPERATOR))
                            } else if (language.isBasePackage(name)) {
                                tokens.add(Pair(len, NAME))
                            } else if (lastType == LuaTokenTypes.DOT && language.isBasePackage(
                                    lastName
                                ) && language.isBaseWord(lastName, name)
                            ) {
                                //标准库函数
                                tokens.add(Pair(len, NAME))
                            } else if (language.isName(name)) {
                                tokens.add(Pair(len, NAME))
                            } else {
                                tokens.add(Pair(len, NORMAL))
                            }

                            if (lastType == LuaTokenTypes.ASSIGN && name == "require") {
                                language.addUserWord(lastName)
                                if (lastNameIdx >= 0) {
                                    val p = tokens.get(lastNameIdx - 1)
                                    p.second = LITERAL
                                    lastNameIdx = -1
                                }
                            }
                            lastNameIdx = tokens.size
                            lastName = name
                        }

                        LuaTokenTypes.SHORT_COMMENT, LuaTokenTypes.BLOCK_COMMENT, LuaTokenTypes.DOC_COMMENT ->                             //注释
                            tokens.add(Pair(len, DOUBLE_SYMBOL_LINE).also { pair = it })

                        LuaTokenTypes.NUMBER ->                             //数字
                            tokens.add(Pair(len, LITERAL))

                        else -> tokens.add(Pair(len, NORMAL).also { pair = it })
                    }
                    lastType3 = lastType
                    if (type != LuaTokenTypes.WHITE_SPACE //&& type != LuaTokenTypes.NEWLINE && type != LuaTokenTypes.NL_BEFORE_LONGSTRING
                    ) {
                        lastType = type
                    }
                    lastType2 = type
                    if (pair != null) lastPair = pair
                    idx += len
                }
            } catch (e: Exception) {
                e.printStackTrace()
                TextWarriorException.fail(e.message!!)
            }
            if (tokens.isEmpty()) {
                // return value cannot be empty
                tokens.add(Pair(0, NORMAL))
            }
            language.updateUserWord()
            Companion.lines = lines
            _tokens = tokens
        }


        /**
         * Scans the document referenced by _lexManager for tokens.
         * The result is stored internally.
         */
        fun tokenize2() {
            val hDoc: DocumentProvider = document
            val language: Language =
                language
            val tokens = ArrayList<Pair>()

            if (!language.isProgLang) {
                tokens.add(Pair(0, NORMAL))
                _tokens = tokens
                return
            }

            val candidateWord = CharArray(MAX_KEYWORD_LENGTH)
            var currentCharInWord = 0
            val currentCharStartWord = 0

            var spanStartPosition = 0
            var workingPosition = 0
            var state: Int = UNKNOWN
            var prevChar = 0.toChar()

            hDoc.seekChar(0)
            while (hDoc.hasNext() && !_abort.isSet) {
                var currentChar = hDoc.next()

                when (state) {
                    UNKNOWN, NORMAL, KEYWORD, NAME, SINGLE_SYMBOL_WORD -> {
                        var pendingState = state
                        var stateChanged = false
                        if (language.isLineStart(prevChar, currentChar)) {
                            pendingState = DOUBLE_SYMBOL_LINE
                            stateChanged = true
                        } else if (language.isMultilineStartDelimiter(prevChar, currentChar)) {
                            pendingState = DOUBLE_SYMBOL_DELIMITED_MULTILINE
                            stateChanged = true
                        } else if (language.isDelimiterA(currentChar)) {
                            pendingState = SINGLE_SYMBOL_DELIMITED_A
                            stateChanged = true
                        } else if (language.isDelimiterB(currentChar)) {
                            pendingState = SINGLE_SYMBOL_DELIMITED_B
                            stateChanged = true
                        } else if (language.isLineAStart(currentChar)) {
                            pendingState = SINGLE_SYMBOL_LINE_A
                            stateChanged = true
                        } else if (language.isLineBStart(currentChar)) {
                            pendingState = SINGLE_SYMBOL_LINE_B
                            stateChanged = true
                        }


                        if (stateChanged) {
                            if (pendingState == DOUBLE_SYMBOL_LINE ||
                                pendingState == DOUBLE_SYMBOL_DELIMITED_MULTILINE
                            ) {
                                // account for previous char
                                spanStartPosition = workingPosition - 1
                                if (tokens.get(tokens.size - 1).first == spanStartPosition) {
                                    tokens.removeAt(tokens.size - 1)
                                }
                            } else {
                                spanStartPosition = workingPosition
                            }

                            // If a span appears mid-word, mark the chars preceding
                            // it as NORMAL, if the previous span isn't already NORMAL
                            if (currentCharInWord > 0 && state != NORMAL) {
                                tokens.add(Pair(workingPosition - currentCharInWord, NORMAL))
                            }

                            state = pendingState
                            tokens.add(Pair(spanStartPosition, state))
                            currentCharInWord = 0
                        } else if (language.isWhitespace(currentChar) || language.isOperator(
                                currentChar
                            )
                        ) {
                            if (currentCharInWord > 0) {
                                // full word obtained; mark the beginning of the word accordingly
                                if (language.isWordStart(candidateWord[0])) {
                                    spanStartPosition = workingPosition - currentCharInWord
                                    state = SINGLE_SYMBOL_WORD
                                    tokens.add(Pair(spanStartPosition, state))
                                } else if (language.isKeyword(
                                        String(
                                            candidateWord,
                                            0,
                                            currentCharInWord
                                        )
                                    )
                                ) {
                                    spanStartPosition = workingPosition - currentCharInWord
                                    state = KEYWORD
                                    tokens.add(Pair(spanStartPosition, state))
                                } else if (language.isName(
                                        String(
                                            candidateWord,
                                            0,
                                            currentCharInWord
                                        )
                                    )
                                ) {
                                    spanStartPosition = workingPosition - currentCharInWord
                                    state = NAME
                                    tokens.add(Pair(spanStartPosition, state))
                                } else if (state != NORMAL) {
                                    spanStartPosition = workingPosition - currentCharInWord
                                    state = NORMAL
                                    tokens.add(Pair(spanStartPosition, state))
                                }
                                currentCharInWord = 0
                            }

                            // mark operators as normal
                            if (state != NORMAL && language.isOperator(currentChar)) {
                                state = NORMAL
                                tokens.add(Pair(workingPosition, state))
                            }
                        } else if (currentCharInWord < MAX_KEYWORD_LENGTH) {
                            // collect non-whitespace chars up to MAX_KEYWORD_LENGTH
                            candidateWord[currentCharInWord] = currentChar
                            currentCharInWord++
                        }
                    }

                    DOUBLE_SYMBOL_LINE, SINGLE_SYMBOL_LINE_A, SINGLE_SYMBOL_LINE_B -> if (language.isMultilineStartDelimiter(
                            prevChar,
                            currentChar
                        )
                    ) {
                        state = DOUBLE_SYMBOL_DELIMITED_MULTILINE
                    } else if (currentChar == '\n') {
                        state = UNKNOWN
                    }

                    SINGLE_SYMBOL_DELIMITED_A -> if ((language.isDelimiterA(currentChar) || currentChar == '\n')
                        && !language.isEscapeChar(prevChar)
                    ) {
                        state = UNKNOWN
                    } else if (language.isEscapeChar(currentChar) && language.isEscapeChar(prevChar)) {
                        currentChar = ' '
                    }

                    SINGLE_SYMBOL_DELIMITED_B -> if ((language.isDelimiterB(currentChar) || currentChar == '\n')
                        && !language.isEscapeChar(prevChar)
                    ) {
                        state = UNKNOWN
                    } else if (language.isEscapeChar(currentChar)
                        && language.isEscapeChar(prevChar)
                    ) {
                        currentChar = ' '
                    }

                    DOUBLE_SYMBOL_DELIMITED_MULTILINE -> if (language.isMultilineEndDelimiter(
                            prevChar,
                            currentChar
                        )
                    ) {
                        state = UNKNOWN
                    }

                    else -> fail("Invalid state in TokenScanner")
                }
                ++workingPosition
                prevChar = currentChar
            }


            // end state machine
            if (tokens.isEmpty()) {
                // return value cannot be empty
                tokens.add(Pair(0, NORMAL))
            }

            _tokens = tokens
        }
    } //end inner class

    companion object {
        val UNKNOWN: Int = -1
        const val NORMAL: Int = 0
        const val KEYWORD: Int = 1
        const val OPERATOR: Int = 2
        const val NAME: Int = 3
        const val LITERAL: Int = 4

        /**
         * A word that starts with a special symbol, inclusive.
         * Examples:
         * :ruby_symbol
         */
        const val SINGLE_SYMBOL_WORD: Int = 10

        /**
         * Tokens that extend from a single start symbol, inclusive, until the end of line.
         * Up to 2 types of symbols are supported per language, denoted by A and B
         * Examples:
         * #include "myCppFile"
         * #this is a comment in Python
         * %this is a comment in Prolog
         */
        const val SINGLE_SYMBOL_LINE_A: Int = 20
        const val SINGLE_SYMBOL_LINE_B: Int = 21

        /**
         * Tokens that extend from a two start symbols, inclusive, until the end of line.
         * Examples:
         * //this is a comment in C
         */
        const val DOUBLE_SYMBOL_LINE: Int = 30

        /**
         * Tokens that are enclosed between a start and end sequence, inclusive,
         * that can span multiple lines. The start and end sequences contain exactly
         * 2 symbols.
         * Examples:
         * {- this is a...
         * ...multi-line comment in Haskell -}
         */
        const val DOUBLE_SYMBOL_DELIMITED_MULTILINE: Int = 40

        /**
         * Tokens that are enclosed by the same single symbol, inclusive, and
         * do not span over more than one line.
         * Examples: 'c', "hello world"
         */
        const val SINGLE_SYMBOL_DELIMITED_A: Int = 50
        const val SINGLE_SYMBOL_DELIMITED_B: Int = 51
        private const val MAX_KEYWORD_LENGTH = 127

        @get:Synchronized
        @set:Synchronized
        var language: Language = LanguageNonProg.instance

        private fun isKeyword(t: LuaTokenTypes): Boolean {
            when (t) {
                LuaTokenTypes.TRUE, LuaTokenTypes.FALSE, LuaTokenTypes.DO, LuaTokenTypes.FUNCTION, LuaTokenTypes.NOT, LuaTokenTypes.AND, LuaTokenTypes.OR, LuaTokenTypes.IF, LuaTokenTypes.THEN, LuaTokenTypes.ELSEIF, LuaTokenTypes.ELSE, LuaTokenTypes.WHILE, LuaTokenTypes.FOR, LuaTokenTypes.IN, LuaTokenTypes.RETURN, LuaTokenTypes.BREAK, LuaTokenTypes.LOCAL, LuaTokenTypes.REPEAT, LuaTokenTypes.UNTIL, LuaTokenTypes.END, LuaTokenTypes.NIL, LuaTokenTypes.SWITCH, LuaTokenTypes.CASE, LuaTokenTypes.DEFAULT, LuaTokenTypes.CONTINUE, LuaTokenTypes.GOTO, LuaTokenTypes.LAMBDA, LuaTokenTypes.DEFER, LuaTokenTypes.WHEN -> return true
                else -> return false
            }
        }

        var lines: ArrayList<Rect?> = ArrayList<Rect?>()
            private set
    }
}