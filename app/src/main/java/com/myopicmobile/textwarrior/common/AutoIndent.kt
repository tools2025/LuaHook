package com.myopicmobile.textwarrior.common

import com.androlua.LuaLexer
import com.androlua.LuaTokenTypes
import java.io.IOException
import kotlin.math.max

object AutoIndent {
    @JvmStatic
    fun createAutoIndent(text: CharSequence?): Int {
        val lexer = LuaLexer(text)
        var idt = 0
        try {
            while (true) {
                val type = lexer.advance()
                if (type == null) {
                    break
                }
                if (lexer.yytext() == "switch") idt += 1
                else idt += indent(type)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return idt
    }


    private fun indent(t: LuaTokenTypes): Int {
        when (t) {
            LuaTokenTypes.FOR, LuaTokenTypes.WHILE, LuaTokenTypes.FUNCTION, LuaTokenTypes.IF, LuaTokenTypes.REPEAT, LuaTokenTypes.LCURLY, LuaTokenTypes.SWITCH -> return 1
            LuaTokenTypes.UNTIL, LuaTokenTypes.END, LuaTokenTypes.RCURLY -> return -1
            else -> return 0
        }
    }

    @JvmStatic
    fun format(text: CharSequence?, width: Int): CharSequence {
        val builder = StringBuilder()
        var isNewLine = true
        val lexer = LuaLexer(text)
        try {
            var idt = 0

            while (true) {
                val type = lexer.advance()
                if (type == null) break

                if (type == LuaTokenTypes.NEW_LINE) {
                    if (builder.length > 0 && builder.get(builder.length - 1) == ' ') builder.deleteCharAt(
                        builder.length - 1
                    )
                    isNewLine = true
                    builder.append('\n')
                    idt = max(0.0, idt.toDouble()).toInt()
                } else if (isNewLine) {
                    when (type) {
                        LuaTokenTypes.WHITE_SPACE -> {}
                        LuaTokenTypes.ELSE, LuaTokenTypes.ELSEIF, LuaTokenTypes.CASE, LuaTokenTypes.DEFAULT -> {
                            //idt--;
                            builder.append(createIndent(idt * width - width / 2))
                            builder.append(lexer.yytext())
                            //idt++;
                            isNewLine = false
                        }

                        LuaTokenTypes.DOUBLE_COLON, LuaTokenTypes.AT -> {
                            builder.append(lexer.yytext())
                            isNewLine = false
                        }

                        LuaTokenTypes.END, LuaTokenTypes.UNTIL, LuaTokenTypes.RCURLY -> {
                            idt--
                            builder.append(createIndent(idt * width))
                            builder.append(lexer.yytext())
                            isNewLine = false
                        }

                        else -> {
                            builder.append(createIndent(idt * width))
                            builder.append(lexer.yytext())
                            idt += indent(type)
                            isNewLine = false
                        }
                    }
                } else if (type == LuaTokenTypes.WHITE_SPACE) {
                    builder.append(' ')
                } else {
                    builder.append(lexer.yytext())
                    idt += indent(type)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return builder
    }

    private fun createIndent(n: Int): CharArray? {
        if (n < 0) return CharArray(0)
        val idts = CharArray(n)
        for (i in 0..<n) idts[i] = ' '
        return idts
    }
}
