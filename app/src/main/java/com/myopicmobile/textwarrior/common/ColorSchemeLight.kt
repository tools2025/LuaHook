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
 * Off-black on off-white background color scheme
 */
class ColorSchemeLight : ColorScheme() {
    init {
        setColor(Colorable.FOREGROUND, OFF_BLACK)
        setColor(Colorable.BACKGROUND, OFF_WHITE.toInt())
        setColor(Colorable.SELECTION_FOREGROUND, OFF_WHITE.toInt())
        setColor(Colorable.CARET_FOREGROUND, OFF_WHITE.toInt())
    }

    override val isDark: Boolean
        get() = false

    companion object {
        private const val OFF_WHITE = 0xFFF0F0ED
        private const val OFF_BLACK = -0xcccccd
    }
}
