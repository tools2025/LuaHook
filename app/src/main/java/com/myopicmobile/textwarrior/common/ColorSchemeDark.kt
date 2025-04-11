/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.common


class ColorSchemeDark : ColorScheme() {
    init {
        setColor(Colorable.FOREGROUND, OFF_WHITE)
        setColor(Colorable.BACKGROUND, OFF_BLACK)
        //setColor(Colorable.SELECTION_FOREGROUND, OFF_WHITE);
        //setColor(Colorable.SELECTION_BACKGROUND, OCEAN_BLUE);
        //setColor(Colorable.CARET_FOREGROUND, OFF_BLACK);
        //setColor(Colorable.CARET_BACKGROUND, FLUORESCENT_YELLOW);
        //setColor(Colorable.CARET_DISABLED, LIGHT_GREY);
        //setColor(Colorable.LINE_HIGHLIGHT, 0xFf00FF00);
        setColor(Colorable.NON_PRINTING_GLYPH, DARK_GREY)
        //setColor(Colorable.COMMENT, JUNGLE_GREEN);
        //setColor(Colorable.KEYWORD, MARINE);
        //setColor(Colorable.LITERAL, PEACH);
        //setColor(Colorable.SECONDARY, BEIGE);
    }

    override val isDark: Boolean
        get() = true

    companion object {
        private const val BEIGE = -0x284583
        private const val DARK_GREY = -0x9f9fa0
        private const val FLUORESCENT_YELLOW = -0x100e6d
        private const val JUNGLE_GREEN = -0x9f74b2
        private const val LIGHT_GREY = -0x2c2c2d
        private const val MARINE = -0xa9632a
        private const val OCEAN_BLUE = -0xda9c6b
        private const val OFF_BLACK = -0xfbfbfc
        private const val OFF_WHITE = -0x2f2d2d
        private const val PEACH = -0x29627b
    }
}
