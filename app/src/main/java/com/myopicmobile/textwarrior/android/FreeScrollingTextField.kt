/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights "eserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
/*
 *****************************************************************************
 *
 * --------------------------------- row length
 * Hello World(\n)                 | 12
 * This is a test of the caret(\n) | 28
 * func|t|ions(\n)                 | 10
 * of this program(EOF)            | 16
 * ---------------------------------
 *
 * The figure illustrates the convention for counting characters.
 * Rows 36 to 39 of a hypothetical text file are shown.
 * The 0th char of the file is off-screen.
 * Assume the first char on screen is the 257th char.
 * The caret is before the char 't' of the word "functions". The caret is drawn
 * as a filled blue rectangle enclosing the 't'.
 *
 * _caretPosition == 257 + 12 + 28 + 4 == 301
 *
 * Note 1: EOF (End Of File) is a real char with a length of 1
 * Note 2: Characters enclosed in parentheses are non-printable
 *
 *****************************************************************************
 *
 * There is a difference between rows and lines in TextWarrior.
 * Rows are displayed while lines are a pure logical construct.
 * When there is no word-wrap, a line of text is displayed as a row on screen.
 * With word-wrap, a very long line of text may be split across several rows
 * on screen.
 *
 */
package com.myopicmobile.textwarrior.android

import android.R
import android.content.Context
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.text.ClipboardManager
import android.text.InputType
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.method.CharacterPickerDialog
import android.util.AttributeSet
import android.util.Log
import android.util.SparseArray
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityRecord
import android.view.animation.AnimationUtils
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Scroller
import com.myopicmobile.textwarrior.android.KeysInterpreter.isNavigationKey
import com.myopicmobile.textwarrior.android.KeysInterpreter.keyEventToPrintableChar
import com.myopicmobile.textwarrior.common.AutoIndent.createAutoIndent
import com.myopicmobile.textwarrior.common.AutoIndent.format
import com.myopicmobile.textwarrior.common.ColorScheme
import com.myopicmobile.textwarrior.common.ColorScheme.Colorable
import com.myopicmobile.textwarrior.common.ColorSchemeLight
import com.myopicmobile.textwarrior.common.Document.TextFieldMetrics
import com.myopicmobile.textwarrior.common.DocumentProvider
import com.myopicmobile.textwarrior.common.Language
import com.myopicmobile.textwarrior.common.LanguageLua.Companion.instance
import com.myopicmobile.textwarrior.common.Lexer
import com.myopicmobile.textwarrior.common.Lexer.Companion.language
import com.myopicmobile.textwarrior.common.Lexer.Companion.lines
import com.myopicmobile.textwarrior.common.Lexer.LexCallback
import com.myopicmobile.textwarrior.common.Pair
import com.myopicmobile.textwarrior.common.RowListener
import com.myopicmobile.textwarrior.common.TextWarriorException.Companion.assertVerbose
import com.myopicmobile.textwarrior.common.TextWarriorException.Companion.fail
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * A custom text view that uses a solid shaded caret (aka cursor) instead of a
 * blinking caret and allows a variety of navigation methods to be easily
 * integrated.
 *
 *
 * It also has a built-in syntax highlighting feature. The global programming
 * language syntax to use is specified with Lexer.setLanguage(Language).
 * To disable syntax highlighting, simply pass LanguageNonProg to that function.
 *
 *
 * Responsibilities
 * 1. Display text
 * 2. Display padding
 * 3. Scrolling
 * 4. Store and display caret position and selection range
 * 5. Store font type, font size, and tab length
 * 6. Interpret non-touch input events and shortcut keystrokes, triggering
 * the appropriate inner class controller actions
 * 7. Reset view, set cursor position and selection range
 *
 *
 * Inner class controller responsibilities
 * 1. Caret movement
 * 2. Activate/deactivate selection mode
 * 3. Cut, copy, paste, delete, insert
 * 4. Schedule areas to repaint and analyze for spans in response to edits
 * 5. Directs scrolling if caret movements or edits causes the caret to be off-screen
 * 6. Notify rowListeners when caret row changes
 * 7. Provide helper methods for InputConnection to setComposingText from the IME
 *
 *
 * This class is aware that the underlying text buffer uses an extra char (EOF)
 * to mark the end of the text. The text size reported by the text buffer includes
 * this extra char. Some bounds manipulation is done so that this implementation
 * detail is hidden from client classes.
 */
open class FreeScrollingTextField : View, TextFieldMetrics {
    private val _scroller: Scroller

    //---------------------------------------------------------------------
    //-------------------------- Paint methods ----------------------------
    // this used to be isDirty(), but was renamed to avoid conflicts with Android API 11
    var isEdited: Boolean = false // whether the text field is dirtied
    protected var _navMethod: TouchNavigationMethod
    protected var _hDoc: DocumentProvider // the model in MVC
    var caretPosition: Int = 0
        protected set
    protected var _selectionAnchor: Int = -1 // inclusive
    protected var _selectionEdge: Int = -1 // exclusive
    protected var _tabLength: Int = DEFAULT_TAB_LENGTH_SPACES
    protected var _colorScheme: ColorScheme = ColorSchemeLight()
    protected var _isHighlightRow: Boolean = false
    protected var _showNonPrinting: Boolean = false
    protected var _isAutoIndent: Boolean = true
    var autoIndentWidth: Int = 4
    protected var _isLongPressCaps: Boolean = false
    protected var _autoCompletePanel: AutoCompletePanel? = null
    protected var _isAutoComplete: Boolean = true
    private var _fieldController: TextFieldController? = null // the controller in MVC
    private var _inputConnection: TextFieldInputConnection? = null
    private var _rowLis: RowListener? = null
    private var _selModeLis: OnSelectionChangedListener? = null
    var caretRow: Int = 0 // can be calculated, but stored for efficiency purposes
        private set
    private var _brush: Paint? = null
    protected var _isEdited: Boolean = false // whether the text field is dirtied

    /**
     * Max amount that can be scrolled horizontally based on the longest line
     * displayed on screen so far
     */
    private var _xExtent = 0
    var leftOffset: Int = 0
        private set
    var isShowLineNumbers: Boolean = false
    private var _clipboardPanel: ClipboardPanel? = null
    private var _clipboardManager: ClipboardManager? = null
    private var _zoomFactor = 1f
    var caretX: Int = 0
        private set
    var caretY: Int = 0
        private set
    private var _textLis: TextChangeListener? = null
    var topOffset: Int = 0
        private set
    private var _defTypeface: Typeface? = Typeface.DEFAULT
    private var _boldTypeface: Typeface? = Typeface.DEFAULT_BOLD
    private var _italicTypeface: Typeface? = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
    private var _emoji = 0.toChar()
    private var _isLayout = false
    private var _brushLine: Paint? = null
    private var _alphaWidth = 0
    private val _scrollCaretDownTask: Runnable = object : Runnable {
        override fun run() {
            _fieldController!!.moveCaretDown()
            if (!caretOnLastRowOfFile()) {
                postDelayed(_scrollCaretDownTask, SCROLL_PERIOD)
            }
        }
    }
    private val _scrollCaretUpTask: Runnable = object : Runnable {
        override fun run() {
            _fieldController!!.moveCaretUp()
            if (!caretOnFirstRowOfFile()) {
                postDelayed(_scrollCaretUpTask, SCROLL_PERIOD)
            }
        }
    }
    private val _scrollCaretLeftTask: Runnable = object : Runnable {
        override fun run() {
            _fieldController!!.moveCaretLeft(false)
            if (caretPosition > 0 &&
                caretRow == _hDoc.findRowNumber(caretPosition - 1)
            ) {
                postDelayed(_scrollCaretLeftTask, SCROLL_PERIOD)
            }
        }
    }
    private val _scrollCaretRightTask: Runnable = object : Runnable {
        override fun run() {
            _fieldController!!.moveCaretRight(false)
            if (!caretOnEOF() &&
                caretRow == _hDoc.findRowNumber(caretPosition + 1)
            ) {
                postDelayed(_scrollCaretRightTask, SCROLL_PERIOD)
            }
        }
    }
    private var _spaceWidth = 0

    /**
     * Like [View.scrollBy], but scroll smoothly instead of immediately.
     *
     * @param dx the number of pixels to scroll by on the X axis
     * @param dy the number of pixels to scroll by on the Y axis
     */
    private var mLastScroll: Long = 0
    var isAccessibilityEnabled: Boolean = false
        private set

    constructor(context: Context?) : super(context) {
        _hDoc = DocumentProvider(this)
        _navMethod = TouchNavigationMethod(this)
        _scroller = Scroller(context)
        initView()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        _hDoc = DocumentProvider(this)
        _navMethod = TouchNavigationMethod(this)
        _scroller = Scroller(context)
        initView()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        _hDoc = DocumentProvider(this)
        _navMethod = TouchNavigationMethod(this)
        _scroller = Scroller(context)
        initView()
    }

    val textSize: Float
        get() = _brush!!.getTextSize()

    fun setTextSize(pix: Int) {
        if (pix <= 8 || pix >= 80 || pix.toFloat() == _brush!!.getTextSize()) {
            return
        }
        val oldHeight = rowHeight().toDouble()
        val oldWidth = getAdvance('a').toDouble()
        _zoomFactor = (pix / BASE_TEXT_SIZE_PIXELS).toFloat()
        _brush!!.setTextSize(pix.toFloat())
        _brushLine!!.setTextSize(pix.toFloat())
        if (_hDoc.isWordWrap) _hDoc.analyzeWordWrap()
        _fieldController!!.updateCaretRow()
        val x = getScrollX() * (getAdvance('a').toDouble() / oldWidth)
        val y = getScrollY() * (rowHeight().toDouble() / oldHeight)
        scrollTo(x.toInt(), y.toInt())
        _alphaWidth = _brush!!.measureText("a").toInt()
        _spaceWidth = _brush!!.measureText(" ").toInt()
        //int idx=coordToCharIndex(getScrollX(), getScrollY());
        //if (!makeCharVisible(idx))
        run {
            invalidate()
        }
    }

    fun replaceText(from: Int, charCount: Int, text: String?) {
        _hDoc.beginBatchEdit()
        _fieldController!!.replaceText(from, charCount, text)
        _fieldController!!.stopTextComposing()
        _hDoc.endBatchEdit()
    }

    fun format() {
        selectText(false)
        val text = format(
            DocumentProvider(_hDoc),
            this.autoIndentWidth
        )
        _hDoc.beginBatchEdit()
        _hDoc.deleteAt(0, _hDoc.docLength() - 1, System.nanoTime())
        _hDoc.insertBefore(text.toString().toCharArray(), 0, System.nanoTime())
        _hDoc.endBatchEdit()
        _hDoc.clearSpans()
        respan()
        invalidate()
    }

    val length: Int
        get() = _hDoc.docLength()

    private fun initView() {
        val accessibilityManager =
            getContext().getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        isAccessibilityEnabled = accessibilityManager.isTouchExplorationEnabled()
        _fieldController = this.TextFieldController()
        _clipboardManager =
            getContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        _brush = Paint()
        _brush!!.setAntiAlias(true)
        _brush!!.setTextSize(BASE_TEXT_SIZE_PIXELS.toFloat())
        _brushLine = Paint()
        _brushLine!!.setAntiAlias(true)
        _brushLine!!.setTextSize(BASE_TEXT_SIZE_PIXELS.toFloat())
        //setBackgroundColor(_colorScheme.getColor(Colorable.BACKGROUND));
        setLongClickable(true)
        setFocusableInTouchMode(true)
        setHapticFeedbackEnabled(true)

        _rowLis = object : RowListener {
            override fun onRowChange(newRowIndex: Int) {
                // Do nothing
            }
        }

        _selModeLis = object : OnSelectionChangedListener {
            override fun onSelectionChanged(active: Boolean, selStart: Int, selEnd: Int) {
                // TODO: Implement this method
                if (active) _clipboardPanel!!.show()
                else _clipboardPanel!!.hide()
            }
        }

        _textLis = object : TextChangeListener {
            override fun onNewLine(c: String?, _caretPosition: Int, p2: Int) {
                // TODO: Implement this method
                if (isAccessibilityEnabled) {
                    val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)
                    event.setFromIndex(_caretPosition - 1)
                    event.setAddedCount(1)
                    sendAccessibilityEventUnchecked(event)
                }
                _autoCompletePanel!!.dismiss()
            }


            override fun onDel(text: CharSequence?, caretPosition: Int, delCount: Int) {
                if (isAccessibilityEnabled) {
                    val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)
                    event.setFromIndex(caretPosition - delCount)
                    event.setRemovedCount(delCount)
                    event.setBeforeText(_hDoc)
                    sendAccessibilityEventUnchecked(event)
                }
                _autoCompletePanel!!.dismiss()
            }

            override fun onAdd(text: CharSequence?, caretPosition: Int, addCount: Int) {
                if (isAccessibilityEnabled) {
                    val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)
                    event.setFromIndex(caretPosition - addCount)
                    event.setAddedCount(addCount)
                    sendAccessibilityEventUnchecked(event)
                }
                if (!_isAutoComplete) return
                var curr: Int = caretPosition
                while (curr >= 0) {
                    val c = _hDoc.get(curr - 1)
                    if (!(Character.isLetterOrDigit(c) || c == '_' || c == '.')) {
                        break
                    }
                    curr--
                }
                if (caretPosition - curr > 0) _autoCompletePanel!!.update(
                    _hDoc.subSequence(
                        curr,
                        caretPosition - curr
                    )
                )
                else _autoCompletePanel!!.dismiss()
            }
        }
        resetView()
        _clipboardPanel = ClipboardPanel(this)
        _autoCompletePanel = AutoCompletePanel(this)
        _autoCompletePanel!!.setLanguage(instance)
        //TODO find out if this function works
        //setScrollContainer(true);
        invalidate()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
//The input source is a pointing device associated with a display.
//输入源为可显示的指针设备，如：mouse pointing device(鼠标指针),stylus pointing device(尖笔设备)
        if (0 != (event.getSource() and InputDevice.SOURCE_CLASS_POINTER)) {
            when (event.getAction()) {
                MotionEvent.ACTION_SCROLL -> {
                    //获得垂直坐标上的滚动方向,也就是滚轮向下滚
                    /*if( event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f){
                        //Log.i("fortest::onGenericMotionEvent", "down" );
                    }
                    //获得垂直坐标上的滚动方向,也就是滚轮向上滚
                    else{
                        //Log.i("fortest::onGenericMotionEvent", "up" );
                    }*/
                    scrollView(0f, -event.getAxisValue(MotionEvent.AXIS_VSCROLL) * rowHeight())
                    return true
                }
            }
        }
        return super.onGenericMotionEvent(event)
    }

    private fun scrollView(distanceX: Float, distanceY: Float) {
        var newX = distanceX.toInt() + getScrollX()
        var newY = distanceY.toInt() + getScrollY()

        // If scrollX and scrollY are somehow more than the recommended
        // max scroll values, use them as the new maximum
        // Also take into account the size of the caret,
        // which may extend beyond the text boundaries
        val maxWidth = max(
            this.maxScrollX.toDouble(),
            getScrollX().toDouble()
        ).toInt()
        if (newX > maxWidth) {
            newX = maxWidth
        } else if (newX < 0) {
            newX = 0
        }

        val maxHeight = max(
            this.maxScrollY.toDouble(),
            getScrollY().toDouble()
        ).toInt()
        if (newY > maxHeight) {
            newY = maxHeight
        } else if (newY < 0) {
            newY = 0
        }
        //_textField.scrollTo(newX, newY);
        smoothScrollTo(newX, newY)
    }

    @Suppress("deprecation")
    override fun createAccessibilityNodeInfo(): AccessibilityNodeInfo {
        val node = super.createAccessibilityNodeInfo()
        if (Build.VERSION.SDK_INT > 20) {
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)
        } else {
            if (Build.VERSION.SDK_INT > 15) {
                node.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                node.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                node.addAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
                node.addAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            node.setTextSelection(this.selectionStart, this.selectionEnd)
        }
        node.setFocusable(true)
        if (Build.VERSION.SDK_INT >= 18) node.setEditable(true)
        if (Build.VERSION.SDK_INT >= 19) node.setMultiLine(true)
        return node
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (Build.VERSION.SDK_INT < 16) return super.performAccessibilityAction(action, arguments)

        when (action) {
            AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY -> {
                when (arguments?.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT)) {
                    AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE -> moveCaretDown()
                    AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER -> moveCaretRight()
                }
                return true
            }

            AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY -> {
                when (arguments?.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT)) {
                    AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE -> moveCaretUp()
                    AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER -> moveCaretLeft()
                }
                return true
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private fun resetView() {
        caretPosition = 0
        caretRow = 0
        _xExtent = 0
        _fieldController!!.isSelectText = false
        _fieldController!!.stopTextComposing()
        _hDoc.clearSpans()
        if (this.contentWidth > 0 || !_hDoc.isWordWrap) {
            _hDoc.analyzeWordWrap()
        }
        _rowLis!!.onRowChange(0)
        scrollTo(0, 0)
    }

    /**
     * Sets the text displayed to the document referenced by hDoc. The view
     * state is reset and the view is invalidated as a side-effect.
     */
    fun setDocumentProvider(hDoc: DocumentProvider) {
        _hDoc = hDoc
        resetView()
        _fieldController!!.cancelSpanning() //stop existing lex threads
        _fieldController!!.determineSpans()
        invalidate()
        if (isAccessibilityEnabled) {
            setContentDescription(_hDoc)
        }
    }

    /**
     * Returns a DocumentProvider that references the same Document used by the
     * FreeScrollingTextField.
     */
    fun createDocumentProvider(): DocumentProvider {
        return DocumentProvider(_hDoc)
    }

    fun setRowListener(rLis: RowListener) {
        _rowLis = rLis
    }

    fun setOnSelectionChangedListener(sLis: OnSelectionChangedListener) {
        _selModeLis = sLis
    }

    /**
     * Sets the caret navigation method used by this text field
     */
    fun setNavigationMethod(navMethod: TouchNavigationMethod) {
        _navMethod = navMethod
    }

    fun setChirality(isRightHanded: Boolean) {
        _navMethod.onChiralityChanged(isRightHanded)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        outAttrs.inputType = (InputType.TYPE_CLASS_TEXT
                or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        outAttrs.imeOptions = (EditorInfo.IME_FLAG_NO_ENTER_ACTION
                or EditorInfo.IME_ACTION_DONE
                or EditorInfo.IME_FLAG_NO_EXTRACT_UI)
        if (_inputConnection == null) {
            _inputConnection = this.TextFieldInputConnection(this)
        } else {
            _inputConnection!!.resetComposingState()
        }
        return _inputConnection
    }

    override fun onCheckIsTextEditor(): Boolean {
        return true
    }

    override fun isSaveEnabled(): Boolean {
        return true
    }

    //---------------------------------------------------------------------
    //------------------------- Layout methods ----------------------------
    //TODO test with height less than 1 complete row
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        //super.onMeasure(widthMeasureSpec,heightMeasureSpec);
        setMeasuredDimension(
            useAllDimensions(widthMeasureSpec),
            useAllDimensions(heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // TODO: Implement this method
        if (changed) {
            val rect = Rect()
            getWindowVisibleDisplayFrame(rect)
            this.topOffset = rect.top + rect.height() - getHeight()
            if (!_isLayout) respan()
            _isLayout = right > 0
            invalidate()
            _autoCompletePanel!!.setWidth(getWidth() / 2)
        }
        super.onLayout(changed, left, top, right, bottom)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (_hDoc.isWordWrap && oldw != w) _hDoc.analyzeWordWrap()
        _fieldController!!.updateCaretRow()
        if (h < oldh) makeCharVisible(caretPosition)
    }

    private fun useAllDimensions(measureSpec: Int): Int {
        val specMode = MeasureSpec.getMode(measureSpec)
        var result = MeasureSpec.getSize(measureSpec)
        if (specMode != MeasureSpec.EXACTLY && specMode != MeasureSpec.AT_MOST) {
            result = Int.Companion.MAX_VALUE
            fail("MeasureSpec cannot be UNSPECIFIED. Setting dimensions to max.")
        }

        return result
    }

    protected val numVisibleRows: Int
        get() = ceil(this.contentHeight.toDouble() / rowHeight()).toInt()

    fun rowHeight(): Int {
        val metrics = _brush!!.getFontMetricsInt()
        return (metrics.descent - metrics.ascent)
    }

    val contentHeight: Int
        /*
              The only methods that have to worry about padding are invalidate, draw
              and computeVerticalScrollRange() methods. Other methods can assume that
              the text completely fills a rectangular viewport given by getContentWidth()
              and getContentHeight()
              */
        get() = getHeight() - getPaddingTop() - getPaddingBottom()

    val contentWidth: Int
        get() = getWidth() - getPaddingLeft() - getPaddingRight()

    /**
     * Determines if the View has been layout or is still being constructed
     */
    fun hasLayout(): Boolean {
        return (getWidth() == 0) // simplistic implementation, but should work for most cases
    }

    /**
     * The first row of text to paint, which may be partially visible.
     * Deduced from the clipping rectangle given to onDraw()
     */
    private fun getBeginPaintRow(canvas: Canvas): Int {
        val bounds = canvas.getClipBounds()
        return bounds.top / rowHeight()
    }

    /**
     * The last row of text to paint, which may be partially visible.
     * Deduced from the clipping rectangle given to onDraw()
     */
    private fun getEndPaintRow(canvas: Canvas): Int {
        //clip top and left are inclusive; bottom and right are exclusive
        val bounds = canvas.getClipBounds()
        return (bounds.bottom - 1) / rowHeight()
    }

    /**
     * @return The x-value of the baseline for drawing text on the given row
     */
    fun getPaintBaseline(row: Int): Int {
        val metrics = _brush!!.getFontMetricsInt()
        return (row + 1) * rowHeight() - metrics.descent
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()

        //translate clipping region to create padding around edges
        canvas.clipRect(
            getScrollX() + getPaddingLeft(),
            getScrollY() + getPaddingTop(),
            getScrollX() + getWidth() - getPaddingRight(),
            getScrollY() + getHeight() - getPaddingBottom()
        )
        canvas.translate(getPaddingLeft().toFloat(), getPaddingTop().toFloat())
        realDraw(canvas)

        canvas.restore()

        _navMethod.onTextDrawComplete(canvas)
    }

    private fun realDraw(canvas: Canvas) {
        //----------------------------------------------
        // initialize and set up boundaries
        //----------------------------------------------
        var currRowNum = getBeginPaintRow(canvas)
        var currIndex = _hDoc.getRowOffset(currRowNum)
        if (currIndex < 0) {
            return
        }
        val len = _hDoc.length
        var currLineNum =
            if (this.isWordWrap) _hDoc.findLineNumber(currIndex) + 1 else currRowNum + 1
        var lastLineNum = 0
        if (this.isShowLineNumbers) this.leftOffset =
            _brushLine!!.measureText(_hDoc.rowCount.toString() + " ").toInt()
        val endRowNum = getEndPaintRow(canvas)
        var paintX = 0
        var paintY = getPaintBaseline(currRowNum)

        //----------------------------------------------
        // set up initial span color
        //----------------------------------------------
        var spanIndex = 0
        val spans = _hDoc.spans

        // There must be at least one span to paint, even for an empty file,
        // where the span contains only the EOF character
        assertVerbose(
            !spans.isEmpty(),
            "No spans to paint in TextWarrior.paint()"
        )
        if (spans.isEmpty()) spans.add(Pair(0, Lexer.Companion.NORMAL))

        //TODO use binary search
        var nextSpan = spans.get(spanIndex++)

        var currSpan: Pair?
        var spanOffset = 0
        val spanSize = spans.size
        do {
            currSpan = nextSpan
            spanOffset += currSpan!!.first
            if (spanIndex < spanSize) {
                nextSpan = spans.get(spanIndex++)
            } else {
                nextSpan = null
            }
        } while (nextSpan != null && spanOffset <= currIndex)
        var currType = currSpan.second
        var lastType = currType

        when (currSpan.second) {
            Lexer.KEYWORD -> _brush!!.setTypeface(_boldTypeface)
            Lexer.DOUBLE_SYMBOL_LINE -> _brush!!.setTypeface(_italicTypeface)
            else -> _brush!!.setTypeface(_defTypeface)
        }
        var spanColor = _colorScheme.getTokenColor(currSpan.second)
        _brush!!.setColor(spanColor)

        //----------------------------------------------
        // start painting!
        //----------------------------------------------
        val rowCount = _hDoc.rowCount
        if (this.isShowLineNumbers) {
            _brushLine!!.setColor(_colorScheme.getColor(Colorable.NON_PRINTING_GLYPH))
            canvas.drawLine(
                (this.leftOffset - _spaceWidth / 2).toFloat(),
                getScrollY().toFloat(),
                (this.leftOffset - _spaceWidth / 2).toFloat(),
                (getScrollY() + getHeight()).toFloat(),
                _brushLine!!
            )
            if (this.maxScrollY > getHeight()) {
                val s = getScrollY() + (getHeight() * getScrollY() / this.maxScrollY)
                var e =
                    getScrollY() + (getHeight() * (getScrollY() + getHeight()) / this.maxScrollY)
                if (e - s < _alphaWidth / 4) e = s + _alphaWidth / 4
                //_brushLine.setColor(_colorScheme.getColor(Colorable.CARET_FOREGROUND));
                canvas.drawLine(
                    (this.leftOffset - _spaceWidth / 2 - _alphaWidth / 4).toFloat(),
                    s.toFloat(),
                    (this.leftOffset - _spaceWidth / 2 - _alphaWidth / 4).toFloat(),
                    e.toFloat(),
                    _brushLine!!
                )
            }
        }

        var lastTypeface: Typeface?
        when (currType) {
            Lexer.KEYWORD -> lastTypeface = _boldTypeface
            Lexer.DOUBLE_SYMBOL_LINE -> lastTypeface = _italicTypeface
            else -> lastTypeface = _defTypeface
        }

        _brush!!.setTypeface(lastTypeface)

        while (currRowNum <= endRowNum) {
            var spanLen = spanOffset - currIndex

            /*String row = _hDoc.getRow(currRowNum);
            boolean charDraw = false;
            if (row.contains("\t")) {
                charDraw = true;
            } else if (currRowNum == rowCount - 1) {
                charDraw = true;
            } else if (currRowNum == _caretRow) {
                charDraw = true;
            } else if (isSelectText()) {
                charDraw = true;
            }
*/
            val rowLen = _hDoc.getRowSize(currRowNum)
            if (currRowNum >= rowCount) {
                break
            }

            if (this.isShowLineNumbers && currLineNum != lastLineNum) {
                lastLineNum = currLineNum
                val num = currLineNum.toString()
                drawLineNum(canvas, num, 0, paintY)
            }
            paintX = this.leftOffset

            var i = 0

            while (i < rowLen) {
                // check if formatting changes are needed
                if (nextSpan != null && currIndex >= spanOffset) {
                    currSpan = nextSpan

                    spanLen = currSpan.first
                    spanOffset += spanLen
                    lastType = currType
                    currType = currSpan.second

                    if (lastType != currType) {
                        val currTypeface: Typeface?
                        when (currType) {
                            Lexer.KEYWORD -> currTypeface = _boldTypeface
                            Lexer.DOUBLE_SYMBOL_LINE -> currTypeface = _italicTypeface
                            else -> currTypeface = _defTypeface
                        }

                        if (lastTypeface !== currTypeface) {
                            _brush!!.setTypeface(currTypeface)
                            lastTypeface = currTypeface
                        }

                        spanColor = _colorScheme.getTokenColor(currType)
                        _brush!!.setColor(spanColor)
                    }
                    if (spanIndex < spanSize) {
                        nextSpan = spans.get(spanIndex++)
                    } else {
                        nextSpan = null
                    }
                }

                //if (charDraw) {
                if (currIndex == caretPosition) {
                    drawCaret(canvas, paintX, paintY)
                }

                val c = _hDoc.get(currIndex)

                if (_fieldController!!.inSelectionRange(currIndex)) {
                    paintX += drawSelectedText(canvas, c, paintX, paintY)
                } else {
                    paintX += drawChar(canvas, c, paintX, paintY)
                }
                ++currIndex
                ++i
                spanLen--
                /*} else {
                    if (i + spanLen > rowLen)
                        spanLen = rowLen - i;
                    int end = i + spanLen;
                    currIndex += spanLen;
                    paintX += drawString(canvas, row, i, end, paintX, paintY);
                    i += spanLen;
                }*/
            }

            if (_hDoc.get(currIndex - 1) == Language.NEWLINE) ++currLineNum

            paintY += rowHeight()
            if (paintX > _xExtent) {
                // record widest line seen so far
                _xExtent = paintX
            }
            ++currRowNum
        } // end while

        doOptionHighlightRow(canvas)
        if (!this.isWordWrap) doBlockLine(canvas)
    }

    private fun doBlockLine(canvas: Canvas) {
        val lines: ArrayList<Rect> = lines as ArrayList<Rect>
        if (lines == null || lines.isEmpty()) return
        val bounds = canvas.getClipBounds()
        val bt = bounds.top
        val bb = bounds.bottom
        var curr: Rect? = null
        for (rect in lines) {
            /*if(rect.top==_caretRow){
                doBlockRow(canvas,rect.bottom);
            }else if(rect.bottom==_caretRow){
                doBlockRow(canvas,rect.top);
            }*/
            val top = (rect.top + 1) * rowHeight()
            val bottom = rect.bottom * rowHeight()
            if (bottom < bt || top > bb) continue
            val left = min(
                getCharExtent(rect.left).first.toDouble(),
                getCharExtent(rect.right).first.toDouble()
            ).toInt()
            if (rect.left < caretPosition && rect.right >= caretPosition) {
                if (curr == null || curr.left < rect.left) curr = rect
            }
            canvas.drawLine(
                left.toFloat(),
                top.toFloat(),
                left.toFloat(),
                bottom.toFloat(),
                _brushLine!!
            )
        }
        if (curr != null) {
            val top = (curr.top + 1) * rowHeight()
            val bottom = curr.bottom * rowHeight()
            if (bottom < bt || top > bb) return
            val left = min(
                getCharExtent(curr.left).first.toDouble(),
                getCharExtent(curr.right).first.toDouble()
            ).toInt()
            _brushLine!!.setColor(_colorScheme.getColor(Colorable.CARET_FOREGROUND))
            canvas.drawLine(
                left.toFloat(),
                top.toFloat(),
                left.toFloat(),
                bottom.toFloat(),
                _brushLine!!
            )
            _brushLine!!.setColor(_colorScheme.getColor(Colorable.NON_PRINTING_GLYPH))
        }
    }

    private fun doBlockRow(canvas: Canvas, _caretRow: Int) {
        if (_isHighlightRow) {
            val y = getPaintBaseline(_caretRow)
            val originalColor = _brush!!.getColor()
            _brush!!.setColor(_colorScheme.getColor(Colorable.LINE_HIGHLIGHT))

            val lineLength = max(_xExtent.toDouble(), this.contentWidth.toDouble()).toInt()
            //canvas.drawRect(0, y+1, lineLength, y+2, _brush);
            drawTextBackground(canvas, 0, y, lineLength)
            //_brush.setColor(0x88000000);
            _brush!!.setColor(originalColor)
        }
    }

    /**
     * Underline the caret row if the option for highlighting it is set
     */
    private fun doOptionHighlightRow(canvas: Canvas) {
        if (_isHighlightRow) {
            val y = getPaintBaseline(caretRow)
            val originalColor = _brush!!.getColor()
            _brush!!.setColor(_colorScheme.getColor(Colorable.LINE_HIGHLIGHT))

            val lineLength = max(_xExtent.toDouble(), this.contentWidth.toDouble()).toInt()
            //canvas.drawRect(0, y+1, lineLength, y+2, _brush);
            drawTextBackground(canvas, 0, y, lineLength)
            //_brush.setColor(0x88000000);
            _brush!!.setColor(originalColor)
        }
    }

    private fun drawString(
        canvas: Canvas,
        s: String,
        start: Int,
        end: Int,
        paintX: Int,
        paintY: Int
    ): Int {
        var end = end
        val len = s.length
        if (end >= len) end = len - 1
        val charWidth = _brush!!.measureText(s, start, end).toInt()
        if (paintX > getScrollX() || paintX < (getScrollX() + this.contentWidth)) canvas.drawText(
            s,
            start,
            end,
            paintX.toFloat(),
            paintY.toFloat(),
            _brush!!
        )
        return charWidth
    }

    private fun drawSelectedString(canvas: Canvas, s: String, paintX: Int, paintY: Int): Int {
        val advance = _brush!!.measureText(s).toInt()
        val oldColor = _brush!!.getColor()

        _brush!!.setColor(_colorScheme.getColor(Colorable.SELECTION_BACKGROUND))
        drawTextBackground(canvas, paintX, paintY, advance)

        _brush!!.setColor(_colorScheme.getColor(Colorable.SELECTION_FOREGROUND))
        canvas.drawText(s, paintX.toFloat(), paintY.toFloat(), _brush!!)

        _brush!!.setColor(oldColor)
        return advance
    }

    private fun drawChar(canvas: Canvas, c: Char, paintX: Int, paintY: Int): Int {
        val originalColor = _brush!!.getColor()
        val charWidth = getAdvance(c, paintX)

        if (paintX > getScrollX() || paintX < (getScrollX() + this.contentWidth)) when (c) {
            0xd83c.toChar(), 0xd83d.toChar() -> _emoji = c
            ' ' -> if (_showNonPrinting) {
                _brush!!.setColor(_colorScheme.getColor(Colorable.NON_PRINTING_GLYPH))
                canvas.drawText(
                    Language.GLYPH_SPACE,
                    0,
                    1,
                    paintX.toFloat(),
                    paintY.toFloat(),
                    _brush!!
                )
                _brush!!.setColor(originalColor)
            } else {
                canvas.drawText(" ", 0, 1, paintX.toFloat(), paintY.toFloat(), _brush!!)
            }

            Language.EOF, Language.NEWLINE -> if (_showNonPrinting) {
                _brush!!.setColor(_colorScheme.getColor(Colorable.NON_PRINTING_GLYPH))
                canvas.drawText(
                    Language.GLYPH_NEWLINE,
                    0,
                    1,
                    paintX.toFloat(),
                    paintY.toFloat(),
                    _brush!!
                )
                _brush!!.setColor(originalColor)
            }

            Language.TAB -> if (_showNonPrinting) {
                _brush!!.setColor(_colorScheme.getColor(Colorable.NON_PRINTING_GLYPH))
                canvas.drawText(
                    Language.GLYPH_TAB,
                    0,
                    1,
                    paintX.toFloat(),
                    paintY.toFloat(),
                    _brush!!
                )
                _brush!!.setColor(originalColor)
            }

            else -> if (_emoji.code != 0) {
                canvas.drawText(
                    charArrayOf(_emoji, c),
                    0,
                    2,
                    paintX.toFloat(),
                    paintY.toFloat(),
                    _brush!!
                )
                _emoji = 0.toChar()
            } else {
                val ca = charArrayOf(c)
                canvas.drawText(ca, 0, 1, paintX.toFloat(), paintY.toFloat(), _brush!!)
            }
        }

        return charWidth
    }

    // paintY is the baseline for text, NOT the top extent
    private fun drawTextBackground(
        canvas: Canvas, paintX: Int, paintY: Int,
        advance: Int
    ) {
        val metrics = _brush!!.getFontMetricsInt()
        canvas.drawRect(
            paintX.toFloat(),
            (paintY + metrics.ascent).toFloat(),
            (paintX + advance).toFloat(),
            (paintY + metrics.descent).toFloat(),
            _brush!!
        )
    }

    private fun drawSelectedText(canvas: Canvas, c: Char, paintX: Int, paintY: Int): Int {
        val oldColor = _brush!!.getColor()
        val advance = getAdvance(c)

        _brush!!.setColor(_colorScheme.getColor(Colorable.SELECTION_BACKGROUND))
        drawTextBackground(canvas, paintX, paintY, advance)

        _brush!!.setColor(_colorScheme.getColor(Colorable.SELECTION_FOREGROUND))
        drawChar(canvas, c, paintX, paintY)

        _brush!!.setColor(oldColor)
        return advance
    }

    private fun drawCaret(canvas: Canvas, paintX: Int, paintY: Int) {
        val originalColor = _brush!!.getColor()
        this.caretX = paintX
        this.caretY = paintY

        val caretColor = _colorScheme.getColor(Colorable.CARET_DISABLED)
        _brush!!.setColor(caretColor)
        // draw full caret
        drawTextBackground(canvas, paintX - 1, paintY, 2)
        _brush!!.setColor(originalColor)
    }

    private fun drawLineNum(canvas: Canvas, s: String, paintX: Int, paintY: Int): Int {
        //int originalColor = _brush.getColor();
        //_brush.setColor(_colorScheme.getColor(Colorable.NON_PRINTING_GLYPH));
        canvas.drawText(s, paintX.toFloat(), paintY.toFloat(), _brushLine!!)
        //_brush.setColor(originalColor);
        return 0
    }

    override val rowWidth: Int
        get() = this.contentWidth - this.leftOffset

    /**
     * Returns printed width of c.
     *
     *
     * Takes into account user-specified tab width and also handles
     * application-defined widths for NEWLINE and EOF
     *
     * @param c Character to measure
     * @return Advance of character, in pixels
     */
    override fun getAdvance(c: Char): Int {
        val advance: Int

        when (c) {
            0xd83c.toChar(), 0xd83d.toChar() -> advance = 0
            ' ' -> advance = this.spaceAdvance
            Language.NEWLINE, Language.EOF -> advance =
                this.eOLAdvance

            Language.TAB -> advance = this.tabAdvance
            else -> if (_emoji.code != 0) {
                val ca = charArrayOf(_emoji, c)
                advance = _brush!!.measureText(ca, 0, 2).toInt()
            } else {
                val ca = charArrayOf(c)
                advance = _brush!!.measureText(ca, 0, 1).toInt()
            }
        }

        return advance
    }

    fun getAdvance(c: Char, x: Int): Int {
        val advance: Int

        when (c) {
            0xd83c.toChar(), 0xd83d.toChar() -> advance = 0
            ' ' -> advance = this.spaceAdvance
            Language.NEWLINE, Language.EOF -> advance =
                this.eOLAdvance

            Language.TAB -> advance = getTabAdvance(x)
            else -> if (_emoji.code != 0) {
                val ca = charArrayOf(_emoji, c)
                advance = _brush!!.measureText(ca, 0, 2).toInt()
            } else {
                val ca = charArrayOf(c)
                advance = _brush!!.measureText(ca, 0, 1).toInt()
            }
        }

        return advance
    }

    fun getCharAdvance(c: Char): Int {
        val advance: Int
        val ca = charArrayOf(c)
        advance = _brush!!.measureText(ca, 0, 1).toInt()
        return advance
    }

    protected val spaceAdvance: Int
        get() {
            if (_showNonPrinting) {
                return _brush!!.measureText(
                    Language.GLYPH_SPACE,
                    0, Language.GLYPH_SPACE.length
                ).toInt()
            } else {
                return _spaceWidth
            }
        }


    protected val eOLAdvance: Int
        //---------------------------------------------------------------------
        get() {
            if (_showNonPrinting) {
                return _brush!!.measureText(
                    Language.GLYPH_NEWLINE,
                    0, Language.GLYPH_NEWLINE.length
                ).toInt()
            } else {
                return (EMPTY_CARET_WIDTH_SCALE * _brush!!.measureText(
                    " ",
                    0,
                    1
                )).toInt()
            }
        }

    protected val tabAdvance: Int
        get() {
            if (_showNonPrinting) {
                return _tabLength * _brush!!.measureText(
                    Language.GLYPH_SPACE,
                    0, Language.GLYPH_SPACE.length
                ).toInt()
            } else {
                return _tabLength * _spaceWidth
            }
        }

    protected fun getTabAdvance(x: Int): Int {
        if (_showNonPrinting) {
            return _tabLength * _brush!!.measureText(
                Language.GLYPH_SPACE,
                0, Language.GLYPH_SPACE.length
            ).toInt()
        } else {
            val i = (x - this.leftOffset) / _spaceWidth % _tabLength
            return (_tabLength - i) * _spaceWidth
        }
    }

    /**
     * Invalidate rows from startRow (inclusive) to endRow (exclusive)
     */
    private fun invalidateRows(startRow: Int, endRow: Int) {
        assertVerbose(
            startRow <= endRow && startRow >= 0,
            "Invalid startRow and/or endRow"
        )

        val caretSpill = _navMethod.getCaretBloat()
        //TODO The ascent of (startRow+1) may jut inside startRow, so part of
        // that rows have to be invalidated as well.
        // This is a problem for Thai, Vietnamese and Indic scripts
        val metrics = _brush!!.getFontMetricsInt()
        var top = startRow * rowHeight() + getPaddingTop()
        top = (top - max(caretSpill.top.toDouble(), metrics.descent.toDouble())).toInt()
        top = max(0.0, top.toDouble()).toInt()

        super.invalidate(
            0,
            top,
            getScrollX() + getWidth(),
            endRow * rowHeight() + getPaddingTop() + caretSpill.bottom
        )
    }

    /**
     * Invalidate rows from startRow (inclusive) to the end of the field
     */
    private fun invalidateFromRow(startRow: Int) {
        assertVerbose(
            startRow >= 0,
            "Invalid startRow"
        )

        val caretSpill = _navMethod.getCaretBloat()
        //TODO The ascent of (startRow+1) may jut inside startRow, so part of
        // that rows have to be invalidated as well.
        // This is a problem for Thai, Vietnamese and Indic scripts
        val metrics = _brush!!.getFontMetricsInt()
        var top = startRow * rowHeight() + getPaddingTop()
        top = (top - max(caretSpill.top.toDouble(), metrics.descent.toDouble())).toInt()
        top = max(0.0, top.toDouble()).toInt()

        super.invalidate(
            0,
            top,
            getScrollX() + getWidth(),
            getScrollY() + getHeight()
        )
    }

    private fun invalidateCaretRow() {
        invalidateRows(caretRow, caretRow + 1)
    }

    private fun invalidateSelectionRows() {
        val startRow = _hDoc.findRowNumber(_selectionAnchor)
        val endRow = _hDoc.findRowNumber(_selectionEdge)

        invalidateRows(startRow, endRow + 1)
    }

    /**
     * Scrolls the text horizontally and/or vertically if the character
     * specified by charOffset is not in the visible text region.
     * The view is invalidated if it is scrolled.
     *
     * @param charOffset The index of the character to make visible
     * @return True if the drawing area was scrolled horizontally
     * and/or vertically
     */
    private fun makeCharVisible(charOffset: Int): Boolean {
        assertVerbose(
            charOffset >= 0 && charOffset < _hDoc.docLength(),
            "Invalid charOffset given"
        )
        val scrollVerticalBy = makeCharRowVisible(charOffset)
        val scrollHorizontalBy = makeCharColumnVisible(charOffset)

        if (scrollVerticalBy == 0 && scrollHorizontalBy == 0) {
            return false
        } else {
            scrollBy(scrollHorizontalBy, scrollVerticalBy)
            return true
        }
    }

    /**
     * Calculates the amount to scroll vertically if the char is not
     * in the visible region.
     *
     * @param charOffset The index of the character to make visible
     * @return The amount to scroll vertically
     */
    private fun makeCharRowVisible(charOffset: Int): Int {
        var scrollBy = 0
        val charTop = _hDoc.findRowNumber(charOffset) * rowHeight()
        val charBottom = charTop + rowHeight()

        if (charTop < getScrollY()) {
            scrollBy = charTop - getScrollY()
        } else if (charBottom > (getScrollY() + this.contentHeight)) {
            scrollBy = charBottom - getScrollY() - this.contentHeight
        }

        return scrollBy
    }

    /**
     * Calculates the amount to scroll horizontally if the char is not
     * in the visible region.
     *
     * @param charOffset The index of the character to make visible
     * @return The amount to scroll horizontally
     */
    private fun makeCharColumnVisible(charOffset: Int): Int {
        var scrollBy = 0
        val visibleRange = getCharExtent(charOffset)

        val charLeft = visibleRange.first
        val charRight = visibleRange.second

        if (charRight > (getScrollX() + this.contentWidth)) {
            scrollBy = charRight - getScrollX() - this.contentWidth
        }

        if (charLeft < getScrollX() + _alphaWidth) {
            scrollBy = charLeft - getScrollX() - _alphaWidth
        }

        return scrollBy
    }

    /**
     * Calculates the x-coordinate extent of charOffset.
     *
     * @return The x-values of left and right edges of charOffset. Pair.first
     * contains the left edge and Pair.second contains the right edge
     */
    protected fun getCharExtent(charOffset: Int): Pair {
        val row = _hDoc.findRowNumber(charOffset)
        val rowOffset = _hDoc.getRowOffset(row)
        var left = this.leftOffset
        var right = this.leftOffset
        var isEmoji = false
        val rowText = _hDoc.getRow(row)
        var i = 0

        val len = rowText.length
        while (rowOffset + i <= charOffset && i < len) {
            val c = rowText.get(i)
            left = right
            when (c) {
                0xd83c.toChar(), 0xd83d.toChar() -> {
                    isEmoji = true
                    val ca = charArrayOf(c, rowText.get(i + 1))
                    right += _brush!!.measureText(ca, 0, 2).toInt()
                }

                Language.NEWLINE, Language.EOF -> right += this.eOLAdvance
                ' ' -> right += this.spaceAdvance
                Language.TAB -> right += getTabAdvance(right)
                else -> if (isEmoji) isEmoji = false
                else right += getCharAdvance(c)
            }
            ++i
        }
        return Pair(left, right)
    }

    /**
     * Returns the bounding box of a character in the text field.
     * The coordinate system used is one where (0, 0) is the top left corner
     * of the text, before padding is added.
     *
     * @param charOffset The character offset of the character of interest
     * @return Rect(left, top, right, bottom) of the character bounds,
     * or Rect(-1, -1, -1, -1) if there is no character at that coordinate.
     */
    fun getBoundingBox(charOffset: Int): Rect {
        if (charOffset < 0 || charOffset >= _hDoc.docLength()) {
            return Rect(-1, -1, -1, -1)
        }

        val row = _hDoc.findRowNumber(charOffset)
        val top = row * rowHeight()
        val bottom = top + rowHeight()

        val xExtent = getCharExtent(charOffset)
        val left = xExtent.first
        val right = xExtent.second

        return Rect(left, top, right, bottom)
    }

    var colorScheme: ColorScheme
        get() = _colorScheme
        set(colorScheme) {
            _colorScheme = colorScheme
            _navMethod.onColorSchemeChanged(colorScheme)
            setBackgroundColor(colorScheme.getColor(Colorable.BACKGROUND))
        }

    /**
     * Maps a coordinate to the character that it is on. If the coordinate is
     * on empty space, the nearest character on the corresponding row is returned.
     * If there is no character on the row, -1 is returned.
     *
     *
     * The coordinates passed in should not have padding applied to them.
     *
     * @param x x-coordinate
     * @param y y-coordinate
     * @return The index of the closest character, or -1 if there is
     * no character or nearest character at that coordinate
     */
    fun coordToCharIndex(x: Int, y: Int): Int {
        val row = y / rowHeight()
        if (row > _hDoc.rowCount) return _hDoc.docLength() - 1

        val charIndex = _hDoc.getRowOffset(row)
        if (charIndex < 0) {
            //non-existent row
            return -1
        }

        if (x < 0) {
            return charIndex // coordinate is outside, to the left of view
        }

        val rowText = _hDoc.getRow(row)

        var extent = this.leftOffset
        var i = 0
        var isEmoji = false

        //x-=getAdvance('a')/2;
        val len = rowText.length
        while (i < len) {
            val c = rowText.get(i)
            when (c) {
                0xd83c.toChar(), 0xd83d.toChar() -> {
                    isEmoji = true
                    val ca = charArrayOf(c, rowText.get(i + 1))
                    extent += _brush!!.measureText(ca, 0, 2).toInt()
                }

                Language.NEWLINE, Language.EOF -> extent += this.eOLAdvance
                ' ' -> extent += this.spaceAdvance
                Language.TAB -> extent += getTabAdvance(extent)
                else -> if (isEmoji) isEmoji = false
                else extent += getCharAdvance(c)

            }

            if (extent >= x) {
                break
            }

            ++i
        }


        if (i < rowText.length) {
            return charIndex + i
        }
        //nearest char is last char of line
        return charIndex + i - 1
    }

    /**
     * Maps a coordinate to the character that it is on.
     * Returns -1 if there is no character on the coordinate.
     *
     *
     * The coordinates passed in should not have padding applied to them.
     *
     * @param x x-coordinate
     * @param y y-coordinate
     * @return The index of the character that is on the coordinate,
     * or -1 if there is no character at that coordinate.
     */
    fun coordToCharIndexStrict(x: Int, y: Int): Int {
        val row = y / rowHeight()
        val charIndex = _hDoc.getRowOffset(row)

        if (charIndex < 0 || x < 0) {
            //non-existent row
            return -1
        }

        val rowText = _hDoc.getRow(row)

        var extent = 0
        var i = 0
        var isEmoji = false

        //x-=getAdvance('a')/2;
        val len = rowText.length
        while (i < len) {
            val c = rowText.get(i)
            when (c) {
                0xd83c.toChar(), 0xd83d.toChar() -> {
                    isEmoji = true
                    val ca = charArrayOf(c, rowText.get(i + 1))
                    extent += _brush!!.measureText(ca, 0, 2).toInt()
                }

                Language.NEWLINE, Language.EOF -> extent += this.eOLAdvance
                ' ' -> extent += this.spaceAdvance
                Language.TAB -> extent += getTabAdvance(extent)
                else -> if (isEmoji) isEmoji = false
                else extent += getCharAdvance(c)

            }

            if (extent >= x) {
                break
            }

            ++i
        }

        if (i < rowText.length) {
            return charIndex + i
        }

        //no char enclosing x
        return -1
    }

    val maxScrollX: Int
        /**
         * Not private to allow access by TouchNavigationMethod
         *
         * @return The maximum x-value that can be scrolled to for the current rows
         * of text in the viewport.
         */
        get() {
            if (this.isWordWrap) return this.leftOffset
            else return max(
                0.0,
                (_xExtent - this.contentWidth + _navMethod.getCaretBloat().right + _alphaWidth).toDouble()
            ).toInt()
        }

    val maxScrollY: Int
        /**
         * Not private to allow access by TouchNavigationMethod
         *
         * @return The maximum y-value that can be scrolled to.
         */
        get() = max(
            0.0,
            (_hDoc.rowCount * rowHeight() - this.contentHeight / 2 + _navMethod.getCaretBloat().bottom).toDouble()
        ).toInt()

    override fun computeVerticalScrollOffset(): Int {
        return getScrollY()
    }

    override fun computeVerticalScrollRange(): Int {
        return _hDoc.rowCount * rowHeight() + getPaddingTop() + getPaddingBottom()
    }

    override fun computeScroll() {
        if (_scroller.computeScrollOffset()) {
            scrollTo(_scroller.getCurrX(), _scroller.getCurrY())
            postInvalidate()
        }
    }

    fun smoothScrollBy(dx: Int, dy: Int) {
        if (getHeight() == 0) {
            // Nothing to do.
            return
        }
        val duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll
        if (duration > 250) {
            //final int maxY = getMaxScrollX();
            val scrollY = getScrollY()
            val scrollX = getScrollX()

            //dy = Math.max(0, Math.min(scrollY + dy, maxY)) - scrollY;
            _scroller.startScroll(scrollX, scrollY, dx, dy)
            postInvalidate()
        } else {
            if (!_scroller.isFinished()) {
                _scroller.abortAnimation()
            }
            scrollBy(dx, dy)
        }
        mLastScroll = AnimationUtils.currentAnimationTimeMillis()
    }

    /**
     * Like [.scrollTo], but scroll smoothly instead of immediately.
     *
     * @param x the position where to scroll on the X axis
     * @param y the position where to scroll on the Y axis
     */
    fun smoothScrollTo(x: Int, y: Int) {
        smoothScrollBy(x - getScrollX(), y - getScrollY())
    }

    /**
     * Start fling scrolling
     */
    fun flingScroll(velocityX: Int, velocityY: Int) {
        _scroller.fling(
            getScrollX(), getScrollY(), velocityX, velocityY,
            0, this.maxScrollX, 0, this.maxScrollY
        )
        // Keep on drawing until the animation has finished.
        postInvalidate()
        //postInvalidateOnAnimation();
    }

    val isFlingScrolling: Boolean
        get() = !_scroller.isFinished()


    //---------------------------------------------------------------------
    //------------------------- Caret methods -----------------------------
    fun stopFlingScrolling() {
        _scroller.forceFinished(true)
    }

    /**
     * Starting scrolling continuously in scrollDir.
     * Not private to allow access by TouchNavigationMethod.
     *
     * @return True if auto-scrolling started
     */
    fun autoScrollCaret(scrollDir: Int): Boolean {
        var scrolled = false
        when (scrollDir) {
            SCROLL_UP -> {
                removeCallbacks(_scrollCaretUpTask)
                if ((!caretOnFirstRowOfFile())) {
                    post(_scrollCaretUpTask)
                    scrolled = true
                }
            }

            SCROLL_DOWN -> {
                removeCallbacks(_scrollCaretDownTask)
                if (!caretOnLastRowOfFile()) {
                    post(_scrollCaretDownTask)
                    scrolled = true
                }
            }

            SCROLL_LEFT -> {
                removeCallbacks(_scrollCaretLeftTask)
                if (caretPosition > 0 &&
                    caretRow == _hDoc.findRowNumber(caretPosition - 1)
                ) {
                    post(_scrollCaretLeftTask)
                    scrolled = true
                }
            }

            SCROLL_RIGHT -> {
                removeCallbacks(_scrollCaretRightTask)
                if (!caretOnEOF() &&
                    caretRow == _hDoc.findRowNumber(caretPosition + 1)
                ) {
                    post(_scrollCaretRightTask)
                    scrolled = true
                }
            }

            else -> fail("Invalid scroll direction")
        }
        return scrolled
    }

    /**
     * Stops automatic scrolling initiated by autoScrollCaret(int).
     * Not private to allow access by TouchNavigationMethod
     */
    fun stopAutoScrollCaret() {
        removeCallbacks(_scrollCaretDownTask)
        removeCallbacks(_scrollCaretUpTask)
        removeCallbacks(_scrollCaretLeftTask)
        removeCallbacks(_scrollCaretRightTask)
    }

    /**
     * Stops automatic scrolling in scrollDir direction.
     * Not private to allow access by TouchNavigationMethod
     */
    fun stopAutoScrollCaret(scrollDir: Int) {
        when (scrollDir) {
            SCROLL_UP -> removeCallbacks(_scrollCaretUpTask)
            SCROLL_DOWN -> removeCallbacks(_scrollCaretDownTask)
            SCROLL_LEFT -> removeCallbacks(_scrollCaretLeftTask)
            SCROLL_RIGHT -> removeCallbacks(_scrollCaretRightTask)
            else -> fail("Invalid scroll direction")
        }
    }

    /**
     * Sets the caret to position i, scrolls it to view and invalidates
     * the necessary areas for redrawing
     *
     * @param i The character index that the caret should be set to
     */
    fun moveCaret(i: Int) {
        _fieldController!!.moveCaret(i)
    }

    /**
     * Sets the caret one position back, scrolls it on screen, and invalidates
     * the necessary areas for redrawing.
     *
     *
     * If the caret is already on the first character, nothing will happen.
     */
    fun moveCaretLeft() {
        _fieldController!!.moveCaretLeft(false)
    }

    /**
     * Sets the caret one position forward, scrolls it on screen, and
     * invalidates the necessary areas for redrawing.
     *
     *
     * If the caret is already on the last character, nothing will happen.
     */
    fun moveCaretRight() {
        _fieldController!!.moveCaretRight(false)
    }

    /**
     * Sets the caret one row down, scrolls it on screen, and invalidates the
     * necessary areas for redrawing.
     *
     *
     * If the caret is already on the last row, nothing will happen.
     */
    fun moveCaretDown() {
        _fieldController!!.moveCaretDown()
    }

    /**
     * Sets the caret one row up, scrolls it on screen, and invalidates the
     * necessary areas for redrawing.
     *
     *
     * If the caret is already on the first row, nothing will happen.
     */
    fun moveCaretUp() {
        _fieldController!!.moveCaretUp()
    }

    /**
     * Scrolls the caret into view if it is not on screen
     */
    fun focusCaret() {
        makeCharVisible(caretPosition)
    }


    //---------------------------------------------------------------------
    //------------------------- Text Selection ----------------------------
    /**
     * @return The column number where charOffset appears on
     */
    protected fun getColumn(charOffset: Int): Int {
        val row = _hDoc.findRowNumber(charOffset)
        assertVerbose(
            row >= 0,
            "Invalid char offset given to getColumn"
        )
        val firstCharOfRow = _hDoc.getRowOffset(row)
        return charOffset - firstCharOfRow
    }

    protected fun caretOnFirstRowOfFile(): Boolean {
        return (caretRow == 0)
    }

    protected fun caretOnLastRowOfFile(): Boolean {
        return (caretRow == (_hDoc.rowCount - 1))
    }

    protected fun caretOnEOF(): Boolean {
        return (caretPosition == (_hDoc.docLength() - 1))
    }

    val isSelectText: Boolean
        get() = _fieldController!!.isSelectText

    val isSelectText2: Boolean
        get() = _fieldController!!.isSelectText2

    /**
     * Enter or exit select mode.
     * Invalidates necessary areas for repainting.
     *
     * @param mode If true, enter select mode; else exit select mode
     */
    fun selectText(mode: Boolean) {
        if (_fieldController!!.isSelectText && !mode) {
            invalidateSelectionRows()
            _fieldController!!.isSelectText = false
        } else if (!_fieldController!!.isSelectText && mode) {
            invalidateCaretRow()
            _fieldController!!.isSelectText = true
        }
    }

    fun selectAll() {
        _fieldController!!.setSelectionRange(0, _hDoc.docLength() - 1, false, true)
    }

    fun setSelection(beginPosition: Int, numChars: Int) {
        _fieldController!!.setSelectionRange(beginPosition, numChars, true, false)
    }

    fun setSelectionRange(beginPosition: Int, numChars: Int) {
        _fieldController!!.setSelectionRange(beginPosition, numChars, true, true)
    }

    fun inSelectionRange(charOffset: Int): Boolean {
        return _fieldController!!.inSelectionRange(charOffset)
    }

    val selectionStart: Int
        get() {
            if (_selectionAnchor < 0) return caretPosition
            else return _selectionAnchor
        }

    val selectionEnd: Int
        get() {
            if (_selectionEdge < 0) return caretPosition
            else return _selectionEdge
        }

    fun focusSelectionStart() {
        _fieldController!!.focusSelection(true)
    }

    fun focusSelectionEnd() {
        _fieldController!!.focusSelection(false)
    }

    fun cut() {
        if (_selectionAnchor != _selectionEdge) _fieldController!!.cut(_clipboardManager!!)
    }

    fun copy() {
        if (_selectionAnchor != _selectionEdge) _fieldController!!.copy(_clipboardManager!!)
        selectText(false)
    }

    //---------------------------------------------------------------------
    //------------------------- Formatting methods ------------------------
    fun paste() {
        val text = _clipboardManager!!.getText()
        if (text != null) _fieldController!!.paste(text.toString())
    }

    fun cut(cb: ClipboardManager) {
        _fieldController!!.cut(cb)
    }

    fun copy(cb: ClipboardManager) {
        _fieldController!!.copy(cb)
    }

    fun paste(text: String?) {
        _fieldController!!.paste(text)
    }

    private fun reachedNextSpan(charIndex: Int, span: Pair?): Boolean {
        return span != null && (charIndex == span.first)
    }

    fun respan() {
        _fieldController!!.determineSpans()
    }

    fun cancelSpanning() {
        _fieldController!!.cancelSpanning()
    }

    /**
     * Sets the text to use the new typeface, scrolls the view to display the
     * caret if needed, and invalidates the entire view
     */
    fun setTypeface(typeface: Typeface?) {
        _defTypeface = typeface
        _boldTypeface = Typeface.create(typeface, Typeface.BOLD)
        _italicTypeface = Typeface.create(typeface, Typeface.ITALIC)
        _brush!!.setTypeface(typeface)
        _brushLine!!.setTypeface(typeface)
        if (_hDoc.isWordWrap) _hDoc.analyzeWordWrap()
        _fieldController!!.updateCaretRow()
        if (!makeCharVisible(caretPosition)) {
            invalidate()
        }
    }

    fun setItalicTypeface(typeface: Typeface?) {
        _italicTypeface = typeface
    }

    fun setBoldTypeface(typeface: Typeface?) {
        _boldTypeface = typeface
    }

    open var isWordWrap: Boolean
        get() = _hDoc.isWordWrap
        set(enable) {
            _hDoc.isWordWrap = enable

            if (enable) {
                _xExtent = 0
                scrollTo(0, 0)
            }

            _fieldController!!.updateCaretRow()

            if (!makeCharVisible(caretPosition)) {
                invalidate()
            }
        }

    var zoom: Float
        get() = _zoomFactor
        /**
         * Sets the text size to be factor of the base text size, scrolls the view
         * to display the caret if needed, and invalidates the entire view
         */
        set(factor) {
            if (factor <= 0.5 || factor >= 5 || factor == _zoomFactor) {
                return
            }
            _zoomFactor = factor
            val newSize =
                (factor * BASE_TEXT_SIZE_PIXELS).toInt()
            _brush!!.setTextSize(newSize.toFloat())
            _brushLine!!.setTextSize(newSize.toFloat())
            if (_hDoc.isWordWrap) _hDoc.analyzeWordWrap()
            _fieldController!!.updateCaretRow()
            _alphaWidth = _brush!!.measureText("a").toInt()
            //if(!makeCharVisible(_caretPosition)){
            invalidate()
            //}
        }

    /**
     * Sets the length of a tab character, scrolls the view to display the
     * caret if needed, and invalidates the entire view
     *
     * @param spaceCount The number of spaces a tab represents
     */
    fun setTabSpaces(spaceCount: Int) {
        if (spaceCount < 0) {
            return
        }

        _tabLength = spaceCount
        if (_hDoc.isWordWrap) _hDoc.analyzeWordWrap()
        _fieldController!!.updateCaretRow()
        if (!makeCharVisible(caretPosition)) {
            invalidate()
        }
    }

    /**
     * Enable/disable auto-indent
     */
    fun setAutoIndent(enable: Boolean) {
        _isAutoIndent = enable
    }

    fun setAutoComplete(enable: Boolean) {
        _isAutoComplete = enable
    }


    /**
     * Enable/disable long-pressing capitalization.
     * When enabled, a long-press on a hardware key capitalizes that letter.
     * When disabled, a long-press on a hardware key bring up the
     * CharacterPickerDialog, if there are alternative characters to choose from.
     */
    fun setLongPressCaps(enable: Boolean) {
        _isLongPressCaps = enable
    }

    /**
     * Enable/disable highlighting of the current row. The current row is also
     * invalidated
     */
    fun setHighlightCurrentRow(enable: Boolean) {
        _isHighlightRow = enable
        invalidateCaretRow()
    }

    /**
     * Enable/disable display of visible representations of non-printing
     * characters like spaces, tabs and end of lines
     * Invalidates the view if the enable state changes
     */
    fun setNonPrintingCharVisibility(enable: Boolean) {
        if (enable xor _showNonPrinting) {
            _showNonPrinting = enable
            if (_hDoc.isWordWrap) _hDoc.analyzeWordWrap()
            _fieldController!!.updateCaretRow()
            if (!makeCharVisible(caretPosition)) {
                invalidate()
            }
        }
    }

    //---------------------------------------------------------------------
    //------------------------- Event handlers ----------------------------
    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        //Intercept multiple key presses of printing characters to implement
        //long-press caps, because the IME may consume them and not pass the
        //event to onKeyDown() for long-press caps logic to work.
        //TODO Technically, long-press caps should be implemented in the IME,
        //but is put here for end-user's convenience. Unfortunately this may
        //cause some IMEs to break. Remove this feature in future.
        if (_isLongPressCaps
            && event.getRepeatCount() == 1 && event.getAction() == KeyEvent.ACTION_DOWN
        ) {
            val c = keyEventToPrintableChar(event)
            if (Character.isLowerCase(c)
                && c == _hDoc.get(caretPosition - 1).lowercaseChar()
            ) {
                _fieldController!!.onPrintableChar(Language.BACKSPACE)
                _fieldController!!.onPrintableChar(c.uppercaseChar())
                return true
            }
        }

        return super.onKeyPreIme(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Let touch navigation method intercept key event first
        if (_navMethod.onKeyDown(keyCode, event)) {
            return true
        }

        //check if direction or symbol key
        if (isNavigationKey(event)) {
            handleNavigationKey(keyCode, event)
            return true
        } else if (keyCode == KeyEvent.KEYCODE_SYM ||
            keyCode == KeyCharacterMap.PICKER_DIALOG_INPUT.code
        ) {
            showCharacterPicker(
                PICKER_SETS.get(KeyCharacterMap.PICKER_DIALOG_INPUT.code), false
            )
            return true
        }

        //check if character is printable
        val c = keyEventToPrintableChar(event)
        if (c == Language.NULL_CHAR) {
            return super.onKeyDown(keyCode, event)
        }

        val repeatCount = event.getRepeatCount()
        //handle multiple (held) key presses
        if (repeatCount == 1) {
            if (_isLongPressCaps) {
                handleLongPressCaps(c)
            } else {
                handleLongPressDialogDisplay(c)
            }
        } else if (repeatCount == 0 || _isLongPressCaps && !Character.isLowerCase(c) || !_isLongPressCaps && PICKER_SETS.get(
                c.code
            ) == null
        ) {
            _fieldController!!.onPrintableChar(c)
        }

        return true
    }

    private fun handleNavigationKey(keyCode: Int, event: KeyEvent) {
        if (event.isShiftPressed() && !this.isSelectText) {
            invalidateCaretRow()
            _fieldController!!.isSelectText = true
        } else if (!event.isShiftPressed() && this.isSelectText) {
            invalidateSelectionRows()
            _fieldController!!.isSelectText = false
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> _fieldController!!.moveCaretRight(false)
            KeyEvent.KEYCODE_DPAD_LEFT -> _fieldController!!.moveCaretLeft(false)
            KeyEvent.KEYCODE_DPAD_DOWN -> _fieldController!!.moveCaretDown()
            KeyEvent.KEYCODE_DPAD_UP -> _fieldController!!.moveCaretUp()
            else -> {}
        }
    }

    private fun handleLongPressCaps(c: Char) {
        if (Character.isLowerCase(c)
            && c == _hDoc.get(caretPosition - 1)
        ) {
            _fieldController!!.onPrintableChar(Language.BACKSPACE)
            _fieldController!!.onPrintableChar(c.uppercaseChar())
        } else {
            _fieldController!!.onPrintableChar(c)
        }
    }

    //Precondition: If c is alphabetical, the character before the caret is
    //also c, which can be lower- or upper-case
    private fun handleLongPressDialogDisplay(c: Char) {
        //workaround to get the appropriate caps mode to use
        val isCaps = Character.isUpperCase(_hDoc.get(caretPosition - 1))
        val base = if (isCaps) c.uppercaseChar() else c

        val candidates: String? = PICKER_SETS.get(base.code)
        if (candidates != null) {
            _fieldController!!.stopTextComposing()
            showCharacterPicker(candidates, true)
        } else {
            _fieldController!!.onPrintableChar(c)
        }
    }

    /**
     * @param candidates A string of characters to for the user to choose from
     * @param replace    If true, the character before the caret will be replaced
     * with the user-selected char. If false, the user-selected char will
     * be inserted at the caret position.
     */
    private fun showCharacterPicker(candidates: String?, replace: Boolean) {
        val shouldReplace = replace
        val dummyString = SpannableStringBuilder()
        Selection.setSelection(dummyString, 0)

        val dialog = CharacterPickerDialog(
            getContext(),
            this, dummyString, candidates, true
        )

        dialog.setOnDismissListener(object : DialogInterface.OnDismissListener {
            override fun onDismiss(dialog: DialogInterface?) {
                if (dummyString.length > 0) {
                    if (shouldReplace) {
                        _fieldController!!.onPrintableChar(Language.BACKSPACE)
                    }
                    _fieldController!!.onPrintableChar(dummyString.get(0))
                }
            }
        })
        dialog.show()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (_navMethod.onKeyUp(keyCode, event)) {
            return true
        }

        return super.onKeyUp(keyCode, event)
    }

    override fun onTrackballEvent(event: MotionEvent): Boolean {
        // TODO Test on real device
        var deltaX = Math.round(event.getX())
        var deltaY = Math.round(event.getY())
        while (deltaX > 0) {
            _fieldController!!.moveCaretRight(false)
            --deltaX
        }
        while (deltaX < 0) {
            _fieldController!!.moveCaretLeft(false)
            ++deltaX
        }
        while (deltaY > 0) {
            _fieldController!!.moveCaretDown()
            --deltaY
        }
        while (deltaY < 0) {
            _fieldController!!.moveCaretUp()
            ++deltaY
        }
        return true
    }

    private var mMotionEvent: MotionEvent? = null
    private var mX = 0f
    private var mY = 0f

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (isAccessibilityEnabled) {
            val x = event.getX()
            val y = event.getY()

            when (event.getAction()) {
                MotionEvent.ACTION_HOVER_ENTER -> mMotionEvent = event
                MotionEvent.ACTION_HOVER_MOVE -> _navMethod.onScroll(
                    mMotionEvent,
                    event,
                    mX - x,
                    mY - y
                )

                MotionEvent.ACTION_HOVER_EXIT -> _navMethod.onUp(event)
            }
            mX = x
            mY = y
        }
        return super.onHoverEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isFocused()) {
            _navMethod.onTouchEvent(event)
        } else {
            if ((event.getAction() and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP
                && isPointInView(event.getX().toInt(), event.getY().toInt())
            ) {
                // somehow, the framework does not automatically change the focus
                // to this view when it is touched
                requestFocus()
            }
        }
        return true
    }

    private fun isPointInView(x: Int, y: Int): Boolean {
        return (x >= 0 && x < getWidth() && y >= 0 && y < getHeight())
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidateCaretRow()
    }

    /**
     * Not public to allow access by [TouchNavigationMethod]
     */
    fun showIME(show: Boolean) {
        val im = getContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (show) {
            im.showSoftInput(this, 0)
        } else {
            im.hideSoftInputFromWindow(this.getWindowToken(), 0)
        }
    }

    /**
     * Some navigation methods use sensors or have states for their widgets.
     * They should be notified of application lifecycle events so they can
     * start/stop sensing and load/store their GUI state.
     */
    fun onPause() {
        _navMethod.onPause()
    }

    fun onResume() {
        _navMethod.onResume()
    }

    fun onDestroy() {
        _fieldController!!.cancelSpanning()
    }

    val uiState: Parcelable
        //*********************************************************************
        get() = TextFieldUiState(this)

    fun restoreUiState(state: Parcelable?) {
        val uiState = state as TextFieldUiState
        val caretPosition = uiState._caretPosition
        // If the text field is in the process of being created, it may not
        // have its width and height set yet.
        // Therefore, post UI restoration tasks to run later.
        if (uiState._selectMode) {
            val selStart = uiState._selectBegin
            val selEnd = uiState._selectEnd

            post(object : Runnable {
                override fun run() {
                    setSelectionRange(selStart, selEnd - selStart)
                    if (caretPosition < selEnd) {
                        focusSelectionStart() //caret at the end by default
                    }
                }
            })
        } else {
            post(object : Runnable {
                override fun run() {
                    moveCaret(caretPosition)
                }
            })
        }
    }

    //*********************************************************************
    //**************** UI State for saving and restoring ******************
    //*********************************************************************
    //TODO change private
    class TextFieldUiState : Parcelable {
        val _caretPosition: Int
        val _scrollX: Int
        val _scrollY: Int
        val _selectMode: Boolean
        val _selectBegin: Int
        val _selectEnd: Int

        constructor(textField: FreeScrollingTextField) {
            _caretPosition = textField.caretPosition
            _scrollX = textField.getScrollX()
            _scrollY = textField.getScrollY()
            _selectMode = textField.isSelectText
            _selectBegin = textField.selectionStart
            _selectEnd = textField.selectionEnd
        }

        private constructor(`in`: Parcel) {
            _caretPosition = `in`.readInt()
            _scrollX = `in`.readInt()
            _scrollY = `in`.readInt()
            _selectMode = `in`.readInt() != 0
            _selectBegin = `in`.readInt()
            _selectEnd = `in`.readInt()
        }

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            out.writeInt(_caretPosition)
            out.writeInt(_scrollX)
            out.writeInt(_scrollY)
            out.writeInt(if (_selectMode) 1 else 0)
            out.writeInt(_selectBegin)
            out.writeInt(_selectEnd)
        }

        companion object {
            @JvmField
            val CREATOR
                    : Parcelable.Creator<TextFieldUiState?> =
                object : Parcelable.Creator<TextFieldUiState?> {
                    override fun createFromParcel(`in`: Parcel): TextFieldUiState {
                        return TextFieldUiState(`in`)
                    }

                    override fun newArray(size: Int): Array<TextFieldUiState?> {
                        return arrayOfNulls<TextFieldUiState>(size)
                    }
                }
        }
    }

    private inner class TextFieldController

        : LexCallback { //end inner controller class
        private val _lexer = Lexer(this)
        private var _isInSelectionMode = false
        var isSelectText2: Boolean = false
            private set

        /**
         * Analyze the text for programming language keywords and redraws the
         * text view when done. The global programming language used is set with
         * the static method Lexer.setLanguage(Language)
         *
         *
         * Does nothing if the Lexer language is not a programming language
         */
        fun determineSpans() {
            _lexer.tokenize(_hDoc)
        }

        fun cancelSpanning() {
            _lexer.cancelTokenize()
        }

        //This is usually called from a non-UI thread


        //- TextFieldController -----------------------------------------------
        //---------------------------- Key presses ----------------------------
        //TODO minimise invalidate calls from moveCaret(), insertion/deletion and word wrap
        fun onPrintableChar(c: Char) {
            // delete currently selected text, if any

            var selectionDeleted = false
            if (_isInSelectionMode) {
                selectionDelete()
                selectionDeleted = true
            }

            var originalRow: Int = caretRow
            val originalOffset = _hDoc.getRowOffset(originalRow)

            when (c) {
                Language.BACKSPACE -> {
                    if (selectionDeleted) {
                        return
                    }

                    if (caretPosition > 0) {
                        _textLis!!.onDel(c.toString() + "", caretPosition, 1)
                        _hDoc.deleteAt(caretPosition - 1, System.nanoTime())
                        if (_hDoc.get(caretPosition - 2).code == 0xd83d || _hDoc.get(caretPosition - 2).code == 0xd83c) {
                            _hDoc.deleteAt(caretPosition - 2, System.nanoTime())
                            moveCaretLeft(true)
                        }

                        moveCaretLeft(true)

                        if (caretRow < originalRow) {
                            // either a newline was deleted or the caret was on the
                            // first word and it became short enough to fit the prev
                            // row
                            invalidateFromRow(caretRow)
                        } else if (_hDoc.isWordWrap) {
                            if (originalOffset != _hDoc.getRowOffset(originalRow)) {
                                //invalidate previous row too if its wrapping changed
                                --originalRow
                            }
                            //TODO invalidate damaged rows only
                            invalidateFromRow(originalRow)
                        }
                    }
                }

                Language.NEWLINE -> {
                    if (_isAutoIndent) {
                        val indent = createAutoIndent()
                        _hDoc.insertBefore(indent, caretPosition, System.nanoTime())
                        moveCaret(caretPosition + indent.size)
                    } else {
                        _hDoc.insertBefore(c, caretPosition, System.nanoTime())
                        moveCaretRight(true)
                    }

                    if (_hDoc.isWordWrap && originalOffset != _hDoc.getRowOffset(originalRow)) {
                        //invalidate previous row too if its wrapping changed
                        --originalRow
                    }

                    _textLis!!.onNewLine(c.toString() + "", caretPosition, 1)

                    invalidateFromRow(originalRow)
                }

                else -> {
                    _hDoc.insertBefore(c, caretPosition, System.nanoTime())
                    moveCaretRight(true)
                    _textLis!!.onAdd(c.toString() + "", caretPosition, 1)

                    if (_hDoc.isWordWrap) {
                        if (originalOffset != _hDoc.getRowOffset(originalRow)) {
                            //invalidate previous row too if its wrapping changed
                            --originalRow
                        }
                        //TODO invalidate damaged rows only
                        invalidateFromRow(originalRow)
                    }
                }
            }

            _isEdited = true
            determineSpans()
        }

        /**
         * Return a char[] with a newline as the 0th element followed by the
         * leading spaces and tabs of the line that the caret is on
         */
        fun createAutoIndent(): CharArray {
            val lineNum = _hDoc.findLineNumber(caretPosition)
            val startOfLine = _hDoc.getLineOffset(lineNum)
            var whitespaceCount = 0
            _hDoc.seekChar(startOfLine)
            while (_hDoc.hasNext()) {
                val c = _hDoc.next()
                if ((c != ' ' && c != Language.TAB) || startOfLine + whitespaceCount >= caretPosition) {
                    break
                }
                ++whitespaceCount
            }

            whitespaceCount += autoIndentWidth * createAutoIndent(
                _hDoc.subSequence(
                    startOfLine,
                    caretPosition - startOfLine
                )
            )
            if (whitespaceCount < 0) return charArrayOf(Language.NEWLINE)

            val indent = CharArray(1 + whitespaceCount)
            indent[0] = Language.NEWLINE

            _hDoc.seekChar(startOfLine)
            for (i in 0..<whitespaceCount) {
                indent[1 + i] = ' '
            }
            return indent
        }

        fun moveCaretDown() {
            if (!caretOnLastRowOfFile()) {
                val currCaret: Int = caretPosition
                val currRow: Int = caretRow
                val newRow = currRow + 1
                val currColumn = getColumn(currCaret)
                val currRowLength = _hDoc.getRowSize(currRow)
                val newRowLength = _hDoc.getRowSize(newRow)

                if (currColumn < newRowLength) {
                    // Position at the same column as old row.
                    caretPosition += currRowLength
                } else {
                    // Column does not exist in the new row (new row is too short).
                    // Position at end of new row instead.
                    caretPosition +=
                        currRowLength - currColumn + newRowLength - 1
                }
                ++caretRow

                updateSelectionRange(currCaret, caretPosition)
                if (!makeCharVisible(caretPosition)) {
                    invalidateRows(currRow, newRow + 1)
                }
                _rowLis!!.onRowChange(newRow)
                stopTextComposing()
            }
        }

        fun moveCaretUp() {
            if (!caretOnFirstRowOfFile()) {
                val currCaret: Int = caretPosition
                val currRow: Int = caretRow
                val newRow = currRow - 1
                val currColumn = getColumn(currCaret)
                val newRowLength = _hDoc.getRowSize(newRow)

                if (currColumn < newRowLength) {
                    // Position at the same column as old row.
                    caretPosition -= newRowLength
                } else {
                    // Column does not exist in the new row (new row is too short).
                    // Position at end of new row instead.
                    caretPosition -= (currColumn + 1)
                }
                --caretRow

                updateSelectionRange(currCaret, caretPosition)
                if (!makeCharVisible(caretPosition)) {
                    invalidateRows(newRow, currRow + 1)
                }
                _rowLis!!.onRowChange(newRow)
                stopTextComposing()
            }
        }

        /**
         * @param isTyping Whether caret is moved to a consecutive position as
         * a result of entering text
         */
        fun moveCaretRight(isTyping: Boolean) {
            if (!caretOnEOF()) {
                val originalRow: Int = caretRow
                ++caretPosition
                updateCaretRow()
                updateSelectionRange(caretPosition - 1, caretPosition)
                if (!makeCharVisible(caretPosition)) {
                    invalidateRows(originalRow, caretRow + 1)
                }

                if (!isTyping) {
                    stopTextComposing()
                }
            }
        }

        /**
         * @param isTyping Whether caret is moved to a consecutive position as
         * a result of deleting text
         */
        fun moveCaretLeft(isTyping: Boolean) {
            if (caretPosition > 0) {
                val originalRow: Int = caretRow
                --caretPosition
                updateCaretRow()
                updateSelectionRange(caretPosition + 1, caretPosition)
                if (!makeCharVisible(caretPosition)) {
                    invalidateRows(caretRow, originalRow + 1)
                }

                if (!isTyping) {
                    stopTextComposing()
                }
            }
        }

        fun moveCaret(i: Int) {
            if (i < 0 || i >= _hDoc.docLength()) {
                fail("Invalid caret position")
                return
            }
            updateSelectionRange(caretPosition, i)

            caretPosition = i
            updateAfterCaretJump()
        }

        fun updateAfterCaretJump() {
            val oldRow: Int = caretRow
            updateCaretRow()
            if (!makeCharVisible(caretPosition)) {
                invalidateRows(oldRow, oldRow + 1) //old caret row
                invalidateCaretRow() //new caret row
            }
            stopTextComposing()
        }


        /**
         * This helper method should only be used by internal methods after setting
         * _caretPosition, in order to to recalculate the new row the caret is on.
         */
        fun updateCaretRow() {
            val newRow = _hDoc.findRowNumber(caretPosition)
            if (caretRow != newRow) {
                caretRow = newRow
                _rowLis!!.onRowChange(newRow)
            }
        }

        fun stopTextComposing() {
            val im = getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            // This is an overkill way to inform the InputMethod that the caret
            // might have changed position and it should re-evaluate the
            // caps mode to use.
            im.restartInput(this@FreeScrollingTextField)
            if (_inputConnection != null && _inputConnection!!.isComposingStarted) {
                _inputConnection!!.resetComposingState()
            }
        }

        var isSelectText: Boolean
            //- TextFieldController -----------------------------------------------
            get() = _isInSelectionMode
            /**
             * Enter or exit select mode.
             * Does not invalidate view.
             *
             * @param mode If true, enter select mode; else exit select mode
             */
            public set(mode) {
                if (!(mode xor _isInSelectionMode)) {
                    return
                }

                if (mode) {
                    _selectionAnchor = caretPosition
                    _selectionEdge = caretPosition
                } else {
                    _selectionAnchor = -1
                    _selectionEdge = -1
                }
                _isInSelectionMode = mode
                this.isSelectText2 = mode
                _selModeLis!!.onSelectionChanged(mode, selectionStart, selectionEnd)
            }

        fun inSelectionRange(charOffset: Int): Boolean {
            if (_selectionAnchor < 0) {
                return false
            }

            return (_selectionAnchor <= charOffset &&
                    charOffset < _selectionEdge)
        }

        /**
         * Selects numChars count of characters starting from beginPosition.
         * Invalidates necessary areas.
         *
         * @param beginPosition
         * @param numChars
         * @param scrollToStart If true, the start of the selection will be scrolled
         * into view. Otherwise, the end of the selection will be scrolled.
         */
        fun setSelectionRange(
            beginPosition: Int, numChars: Int,
            scrollToStart: Boolean, mode: Boolean
        ) {
            assertVerbose(
                (beginPosition >= 0) && numChars <= (_hDoc.docLength() - 1) && numChars >= 0,
                "Invalid range to select"
            )

            if (_isInSelectionMode) {
                // unhighlight previous selection
                invalidateSelectionRows()
            } else {
                // unhighlight caret
                invalidateCaretRow()
                if (mode) this.isSelectText = true
                else _isInSelectionMode = true
            }

            _selectionAnchor = beginPosition
            _selectionEdge = _selectionAnchor + numChars

            caretPosition = _selectionEdge
            stopTextComposing()
            updateCaretRow()
            if (mode) _selModeLis!!.onSelectionChanged(
                this.isSelectText,
                _selectionAnchor,
                _selectionEdge
            )
            var scrolled = makeCharVisible(_selectionEdge)

            if (scrollToStart) {
                //TODO reduce unnecessary scrolling and write a method to scroll
                // the beginning of multi-line selections as far left as possible
                scrolled = makeCharVisible(_selectionAnchor)
            }

            if (!scrolled) {
                invalidateSelectionRows()
            }
        }

        /**
         * Moves the caret to an edge of selected text and scrolls it to view.
         *
         * @param start If true, moves the caret to the beginning of
         * the selection. Otherwise, moves the caret to the end of the selection.
         * In all cases, the caret is scrolled to view if it is not visible.
         */
        fun focusSelection(start: Boolean) {
            if (_isInSelectionMode) {
                if (start && caretPosition != _selectionAnchor) {
                    caretPosition = _selectionAnchor
                    updateAfterCaretJump()
                } else if (!start && caretPosition != _selectionEdge) {
                    caretPosition = _selectionEdge
                    updateAfterCaretJump()
                }
            }
        }


        /**
         * Used by internal methods to update selection boundaries when a new
         * caret position is set.
         * Does nothing if not in selection mode.
         */
        fun updateSelectionRange(oldCaretPosition: Int, newCaretPosition: Int) {
            if (isAccessibilityEnabled && Build.VERSION.SDK_INT >= 16) {
                AccessibilityRecord.obtain()
                val event =
                    AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY)
                if ((oldCaretPosition - newCaretPosition) * (oldCaretPosition - newCaretPosition) == 1) event.setMovementGranularity(
                    AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER
                )
                if (oldCaretPosition > newCaretPosition) event.setAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)
                else event.setAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
                event.setFromIndex(
                    min(
                        oldCaretPosition.toDouble(),
                        newCaretPosition.toDouble()
                    ).toInt()
                )
                event.setToIndex(
                    max(
                        oldCaretPosition.toDouble(),
                        newCaretPosition.toDouble()
                    ).toInt()
                )
                sendAccessibilityEventUnchecked(event)
            }

            if (!_isInSelectionMode) {
                return
            }

            if (oldCaretPosition < _selectionEdge) {
                if (newCaretPosition > _selectionEdge) {
                    _selectionAnchor = _selectionEdge
                    _selectionEdge = newCaretPosition
                } else {
                    _selectionAnchor = newCaretPosition
                }
            } else {
                if (newCaretPosition < _selectionAnchor) {
                    _selectionEdge = _selectionAnchor
                    _selectionAnchor = newCaretPosition
                } else {
                    _selectionEdge = newCaretPosition
                }
            }
        }


        //- TextFieldController -----------------------------------------------
        //------------------------ Cut, copy, paste ---------------------------
        /**
         * Convenience method for consecutive copy and paste calls
         */
        fun cut(cb: ClipboardManager) {
            copy(cb)
            selectionDelete()
        }

        /**
         * Copies the selected text to the clipboard.
         *
         *
         * Does nothing if not in select mode.
         */
        fun copy(cb: ClipboardManager) {
            //TODO catch OutOfMemoryError
            if (_isInSelectionMode &&
                _selectionAnchor < _selectionEdge
            ) {
                val contents = _hDoc.subSequence(
                    _selectionAnchor,
                    _selectionEdge - _selectionAnchor
                )
                cb.setText(contents)
            }
        }

        /**
         * Inserts text at the caret position.
         * Existing selected text will be deleted and select mode will end.
         * The deleted area will be invalidated.
         *
         *
         * After insertion, the inserted area will be invalidated.
         */
        fun paste(text: String?) {
            if (text == null) {
                return
            }

            _hDoc.beginBatchEdit()
            selectionDelete()

            val originalRow: Int = caretRow
            val originalOffset = _hDoc.getRowOffset(originalRow)
            _hDoc.insertBefore(text.toCharArray(), caretPosition, System.nanoTime())
            //_textLis.onAdd(text, _caretPosition, text.length());
            _hDoc.endBatchEdit()

            caretPosition += text.length
            updateCaretRow()

            _isEdited = true
            determineSpans()
            stopTextComposing()

            if (!makeCharVisible(caretPosition)) {
                var invalidateStartRow = originalRow
                //invalidate previous row too if its wrapping changed
                if (_hDoc.isWordWrap &&
                    originalOffset != _hDoc.getRowOffset(originalRow)
                ) {
                    --invalidateStartRow
                }

                if (originalRow == caretRow && !_hDoc.isWordWrap) {
                    //pasted text only affects caret row
                    invalidateRows(invalidateStartRow, invalidateStartRow + 1)
                } else {
                    //TODO invalidate damaged rows only
                    invalidateFromRow(invalidateStartRow)
                }
            }
        }

        /**
         * Deletes selected text, exits select mode and invalidates deleted area.
         * If the selected range is empty, this method exits select mode and
         * invalidates the caret.
         *
         *
         * Does nothing if not in select mode.
         */
        fun selectionDelete() {
            if (!_isInSelectionMode) {
                return
            }

            val totalChars = _selectionEdge - _selectionAnchor

            if (totalChars > 0) {
                val originalRow = _hDoc.findRowNumber(_selectionAnchor)
                val originalOffset = _hDoc.getRowOffset(originalRow)
                val isSingleRowSel = _hDoc.findRowNumber(_selectionEdge) == originalRow
                _textLis!!.onDel("", caretPosition, totalChars)
                _hDoc.deleteAt(_selectionAnchor, totalChars, System.nanoTime())
                caretPosition = _selectionAnchor
                updateCaretRow()
                _isEdited = true
                determineSpans()
                this.isSelectText = false
                stopTextComposing()

                if (!makeCharVisible(caretPosition)) {
                    var invalidateStartRow = originalRow
                    //invalidate previous row too if its wrapping changed
                    if (_hDoc.isWordWrap &&
                        originalOffset != _hDoc.getRowOffset(originalRow)
                    ) {
                        --invalidateStartRow
                    }

                    if (isSingleRowSel && !_hDoc.isWordWrap) {
                        //pasted text only affects current row
                        invalidateRows(invalidateStartRow, invalidateStartRow + 1)
                    } else {
                        //TODO invalidate damaged rows only
                        invalidateFromRow(invalidateStartRow)
                    }
                }
            } else {
                this.isSelectText = false
                invalidateCaretRow()
            }
        }

        fun replaceText(from: Int, charCount: Int, text: String?) {
            var invalidateStartRow: Int
            var originalOffset: Int
            var isInvalidateSingleRow = true
            var dirty = false
            //delete selection
            if (_isInSelectionMode) {
                invalidateStartRow = _hDoc.findRowNumber(_selectionAnchor)
                originalOffset = _hDoc.getRowOffset(invalidateStartRow)

                val totalChars = _selectionEdge - _selectionAnchor

                if (totalChars > 0) {
                    caretPosition = _selectionAnchor
                    _hDoc.deleteAt(_selectionAnchor, totalChars, System.nanoTime())

                    if (invalidateStartRow != caretRow) {
                        isInvalidateSingleRow = false
                    }
                    dirty = true
                }

                this.isSelectText = false
            } else {
                invalidateStartRow = caretRow
                originalOffset = _hDoc.getRowOffset(caretRow)
            }

            //delete requested chars
            if (charCount > 0) {
                val delFromRow = _hDoc.findRowNumber(from)
                if (delFromRow < invalidateStartRow) {
                    invalidateStartRow = delFromRow
                    originalOffset = _hDoc.getRowOffset(delFromRow)
                }

                if (invalidateStartRow != caretRow) {
                    isInvalidateSingleRow = false
                }

                caretPosition = from
                _hDoc.deleteAt(from, charCount, System.nanoTime())
                dirty = true
            }

            //insert
            if (text != null && text.length > 0) {
                val insFromRow = _hDoc.findRowNumber(from)
                if (insFromRow < invalidateStartRow) {
                    invalidateStartRow = insFromRow
                    originalOffset = _hDoc.getRowOffset(insFromRow)
                }

                _hDoc.insertBefore(text.toCharArray(), caretPosition, System.nanoTime())
                caretPosition += text.length
                dirty = true
            }

            if (dirty) {
                _isEdited = true
                determineSpans()
            }

            val originalRow: Int = caretRow
            updateCaretRow()
            if (originalRow != caretRow) {
                isInvalidateSingleRow = false
            }

            if (!makeCharVisible(caretPosition)) {
                //invalidate previous row too if its wrapping changed
                if (_hDoc.isWordWrap &&
                    originalOffset != _hDoc.getRowOffset(invalidateStartRow)
                ) {
                    --invalidateStartRow
                }

                if (isInvalidateSingleRow && !_hDoc.isWordWrap) {
                    //replaced text only affects current row
                    invalidateRows(caretRow, caretRow + 1)
                } else {
                    //TODO invalidate damaged rows only
                    invalidateFromRow(invalidateStartRow)
                }
            }
        }

        //- TextFieldController -----------------------------------------------
        //----------------- Helper methods for InputConnection ----------------
        /**
         * Deletes existing selected text, then deletes charCount number of
         * characters starting at from, and inserts text in its place.
         *
         *
         * Unlike paste or selectionDelete, does not signal the end of
         * text composing to the IME.
         */
        fun replaceComposingText(from: Int, charCount: Int, text: String?) {
            var invalidateStartRow: Int
            var originalOffset: Int
            var isInvalidateSingleRow = true
            var dirty = false

            //delete selection
            if (_isInSelectionMode) {
                invalidateStartRow = _hDoc.findRowNumber(_selectionAnchor)
                originalOffset = _hDoc.getRowOffset(invalidateStartRow)

                val totalChars = _selectionEdge - _selectionAnchor

                if (totalChars > 0) {
                    caretPosition = _selectionAnchor
                    _hDoc.deleteAt(_selectionAnchor, totalChars, System.nanoTime())

                    if (invalidateStartRow != caretRow) {
                        isInvalidateSingleRow = false
                    }
                    dirty = true
                }

                this.isSelectText = false
            } else {
                invalidateStartRow = caretRow
                originalOffset = _hDoc.getRowOffset(caretRow)
            }

            //delete requested chars
            if (charCount > 0) {
                val delFromRow = _hDoc.findRowNumber(from)
                if (delFromRow < invalidateStartRow) {
                    invalidateStartRow = delFromRow
                    originalOffset = _hDoc.getRowOffset(delFromRow)
                }

                if (invalidateStartRow != caretRow) {
                    isInvalidateSingleRow = false
                }

                caretPosition = from
                _hDoc.deleteAt(from, charCount, System.nanoTime())
                dirty = true
            }

            //insert
            if (text != null && text.length > 0) {
                val insFromRow = _hDoc.findRowNumber(from)
                if (insFromRow < invalidateStartRow) {
                    invalidateStartRow = insFromRow
                    originalOffset = _hDoc.getRowOffset(insFromRow)
                }

                _hDoc.insertBefore(text.toCharArray(), caretPosition, System.nanoTime())
                caretPosition += text.length
                dirty = true
            }

            _textLis!!.onAdd(text, caretPosition, text!!.length - charCount)
            if (dirty) {
                _isEdited = true
                determineSpans()
            }

            val originalRow: Int = caretRow
            updateCaretRow()
            if (originalRow != caretRow) {
                isInvalidateSingleRow = false
            }

            if (!makeCharVisible(caretPosition)) {
                //invalidate previous row too if its wrapping changed
                if (_hDoc.isWordWrap &&
                    originalOffset != _hDoc.getRowOffset(invalidateStartRow)
                ) {
                    --invalidateStartRow
                }

                if (isInvalidateSingleRow && !_hDoc.isWordWrap) {
                    //replaced text only affects current row
                    invalidateRows(caretRow, caretRow + 1)
                } else {
                    //TODO invalidate damaged rows only
                    invalidateFromRow(invalidateStartRow)
                }
            }
        }

        /**
         * Delete leftLength characters of text before the current caret
         * position, and delete rightLength characters of text after the current
         * cursor position.
         *
         *
         * Unlike paste or selectionDelete, does not signal the end of
         * text composing to the IME.
         */
        fun deleteAroundComposingText(left: Int, right: Int) {
            var start: Int = caretPosition - left
            if (start < 0) {
                start = 0
            }
            var end: Int = caretPosition + right
            val docLength = _hDoc.docLength()
            if (end > (docLength - 1)) { //exclude the terminal EOF
                end = docLength - 1
            }
            replaceComposingText(start, end - start, "")
        }

        fun getTextAfterCursor(maxLen: Int): String {
            val docLength = _hDoc.docLength()
            if ((caretPosition + maxLen) > (docLength - 1)) {
                //exclude the terminal EOF
                return _hDoc.subSequence(caretPosition, docLength - caretPosition - 1)
                    .toString()
            }

            return _hDoc.subSequence(caretPosition, maxLen).toString()
        }

        fun getTextBeforeCursor(maxLen: Int): String {
            var start: Int = caretPosition - maxLen
            if (start < 0) {
                start = 0
            }
            return _hDoc.subSequence(start, caretPosition - start).toString()
        }


        override fun lexDone(results: MutableList<Pair>?) {
            post(object : Runnable {
                override fun run() {
                    _hDoc.spans = results as MutableList<Pair?>
                    invalidate()
                }
            })
        }
    }


    //*********************************************************************
    //************************** InputConnection **************************
    //*********************************************************************
    /*
     * Does not provide ExtractedText related methods
	 */
    private inner class TextFieldInputConnection(v: FreeScrollingTextField) :
        BaseInputConnection(v, true) {
        /**
         * Only true when the InputConnection has not been used by the IME yet.
         * Can be programatically cleared by resetComposingState()
         */
        var isComposingStarted: Boolean = false
            private set
        private var _composingCharCount = 0

        fun resetComposingState() {
            _composingCharCount = 0
            this.isComposingStarted = false
            _hDoc.endBatchEdit()
        }

        override fun performContextMenuAction(id: Int): Boolean {
            when (id) {
                R.id.copy -> copy()
                R.id.cut -> cut()
                R.id.paste -> paste()
                R.id.startSelectingText, R.id.stopSelectingText, R.id.selectAll -> selectAll()
            }

            return false
        }


        override fun sendKeyEvent(event: KeyEvent): Boolean {
            when (event.getKeyCode()) {
                KeyEvent.KEYCODE_SHIFT_LEFT -> if (isSelectText) selectText(false)
                else selectText(true)

                KeyEvent.KEYCODE_DPAD_LEFT -> moveCaretLeft()
                KeyEvent.KEYCODE_DPAD_UP -> moveCaretUp()
                KeyEvent.KEYCODE_DPAD_RIGHT -> moveCaretRight()
                KeyEvent.KEYCODE_DPAD_DOWN -> moveCaretDown()
                KeyEvent.KEYCODE_MOVE_HOME -> moveCaret(0)
                KeyEvent.KEYCODE_MOVE_END -> moveCaret(_hDoc.length)
                else -> return super.sendKeyEvent(event)
            }
            return true
        }

        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
            this.isComposingStarted = true
            if (!_hDoc.isBatchEdit) {
                _hDoc.beginBatchEdit()
            }

            _fieldController!!.replaceComposingText(
                caretPosition - _composingCharCount,
                _composingCharCount,
                text.toString()
            )
            _composingCharCount = text.length

            //TODO reduce invalidate calls
            if (newCursorPosition > 1) {
                _fieldController!!.moveCaret(caretPosition + newCursorPosition - 1)
            } else if (newCursorPosition <= 0) {
                _fieldController!!.moveCaret(caretPosition - text.length - newCursorPosition)
            }
            return true
        }

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            _fieldController!!.replaceComposingText(
                caretPosition - _composingCharCount,
                _composingCharCount,
                text.toString()
            )
            _composingCharCount = 0
            _hDoc.endBatchEdit()

            //TODO reduce invalidate calls
            if (newCursorPosition > 1) {
                _fieldController!!.moveCaret(caretPosition + newCursorPosition - 1)
            } else if (newCursorPosition <= 0) {
                _fieldController!!.moveCaret(caretPosition - text.length - newCursorPosition)
            }
            this.isComposingStarted = false
            return true
        }


        override fun deleteSurroundingText(leftLength: Int, rightLength: Int): Boolean {
            if (_composingCharCount != 0) {
                Log.i(
                    "lua",
                    "Warning: Implmentation of InputConnection.deleteSurroundingText" +
                            " will not skip composing text"
                )
            }

            _fieldController!!.deleteAroundComposingText(leftLength, rightLength)
            return true
        }

        override fun finishComposingText(): Boolean {
            resetComposingState()
            return true
        }

        override fun getCursorCapsMode(reqModes: Int): Int {
            var capsMode = 0

            // Ignore InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS; not used in TextWarrior
            if ((reqModes and InputType.TYPE_TEXT_FLAG_CAP_WORDS)
                == InputType.TYPE_TEXT_FLAG_CAP_WORDS
            ) {
                val prevChar: Int = caretPosition - 1
                if (prevChar < 0 || language.isWhitespace(_hDoc.get(prevChar))) {
                    capsMode = capsMode or InputType.TYPE_TEXT_FLAG_CAP_WORDS

                    //set CAP_SENTENCES if client is interested in it
                    if ((reqModes and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
                        == InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    ) {
                        capsMode = capsMode or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    }
                }
            } else {
                val lang = language

                var prevChar: Int = caretPosition - 1
                var whitespaceCount = 0
                var capsOn = true

                // Turn on caps mode only for the first char of a sentence.
                // A fresh line is also considered to start a new sentence.
                // The position immediately after a period is considered lower-case.
                // Examples: "abc.com" but "abc. Com"
                while (prevChar >= 0) {
                    val c = _hDoc.get(prevChar)
                    if (c == Language.NEWLINE) {
                        break
                    }

                    if (!lang.isWhitespace(c)) {
                        if (whitespaceCount == 0 || !lang.isSentenceTerminator(c)) {
                            capsOn = false
                        }
                        break
                    }

                    ++whitespaceCount
                    --prevChar
                }

                if (capsOn) {
                    capsMode = capsMode or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                }
            }

            return capsMode
        }

        override fun getTextAfterCursor(maxLen: Int, flags: Int): CharSequence {
            return _fieldController!!.getTextAfterCursor(maxLen) //ignore flags
        }

        override fun getTextBeforeCursor(maxLen: Int, flags: Int): CharSequence {
            return _fieldController!!.getTextBeforeCursor(maxLen) //ignore flags
        }

        override fun setSelection(start: Int, end: Int): Boolean {
            if (start == end) {
                _fieldController!!.moveCaret(start)
            } else {
                _fieldController!!.setSelectionRange(start, end - start, false, true)
            }
            return true
        }

        override fun reportFullscreenMode(enabled: Boolean): Boolean {
            return false
        }
    } // end inner class

    companion object {
        //---------------------------------------------------------------------
        //--------------------------  Caret Scroll  ---------------------------
        const val SCROLL_UP: Int = 0
        const val SCROLL_DOWN: Int = 1
        const val SCROLL_LEFT: Int = 2
        const val SCROLL_RIGHT: Int = 3

        /**
         * Scale factor for the width of a caret when on a NEWLINE or EOF char.
         * A factor of 1.0 is equals to the width of a space character
         */
        protected var EMPTY_CARET_WIDTH_SCALE: Float = 0.75f

        /**
         * When in selection mode, the caret height is scaled by this factor
         */
        protected var SEL_CARET_HEIGHT_SCALE: Float = 0.5f
        protected var DEFAULT_TAB_LENGTH_SPACES: Int = 4
        @JvmStatic
        var BASE_TEXT_SIZE_PIXELS: Int = 16
        protected var SCROLL_PERIOD: Long = 250 //in milliseconds

        /*
     * Hash map for determining which characters to let the user choose from when
	 * a hardware key is long-pressed. For example, long-pressing "e" displays
	 * choices of "é, è, ê, ë" and so on.
	 * This is biased towards European locales, but is standard Android behavior
	 * for TextView.
	 *
	 * Copied from android.text.method.QwertyKeyListener, dated 2006
	 */
        private val PICKER_SETS = SparseArray<String?>()

        init {
            PICKER_SETS.put('A'.code, "\u00C0\u00C1\u00C2\u00C4\u00C6\u00C3\u00C5\u0104\u0100")
            PICKER_SETS.put('C'.code, "\u00C7\u0106\u010C")
            PICKER_SETS.put('D'.code, "\u010E")
            PICKER_SETS.put('E'.code, "\u00C8\u00C9\u00CA\u00CB\u0118\u011A\u0112")
            PICKER_SETS.put('G'.code, "\u011E")
            PICKER_SETS.put('L'.code, "\u0141")
            PICKER_SETS.put('I'.code, "\u00CC\u00CD\u00CE\u00CF\u012A\u0130")
            PICKER_SETS.put('N'.code, "\u00D1\u0143\u0147")
            PICKER_SETS.put('O'.code, "\u00D8\u0152\u00D5\u00D2\u00D3\u00D4\u00D6\u014C")
            PICKER_SETS.put('R'.code, "\u0158")
            PICKER_SETS.put('S'.code, "\u015A\u0160\u015E")
            PICKER_SETS.put('T'.code, "\u0164")
            PICKER_SETS.put('U'.code, "\u00D9\u00DA\u00DB\u00DC\u016E\u016A")
            PICKER_SETS.put('Y'.code, "\u00DD\u0178")
            PICKER_SETS.put('Z'.code, "\u0179\u017B\u017D")
            PICKER_SETS.put('a'.code, "\u00E0\u00E1\u00E2\u00E4\u00E6\u00E3\u00E5\u0105\u0101")
            PICKER_SETS.put('c'.code, "\u00E7\u0107\u010D")
            PICKER_SETS.put('d'.code, "\u010F")
            PICKER_SETS.put('e'.code, "\u00E8\u00E9\u00EA\u00EB\u0119\u011B\u0113")
            PICKER_SETS.put('g'.code, "\u011F")
            PICKER_SETS.put('i'.code, "\u00EC\u00ED\u00EE\u00EF\u012B\u0131")
            PICKER_SETS.put('l'.code, "\u0142")
            PICKER_SETS.put('n'.code, "\u00F1\u0144\u0148")
            PICKER_SETS.put('o'.code, "\u00F8\u0153\u00F5\u00F2\u00F3\u00F4\u00F6\u014D")
            PICKER_SETS.put('r'.code, "\u0159")
            PICKER_SETS.put('s'.code, "\u00A7\u00DF\u015B\u0161\u015F")
            PICKER_SETS.put('t'.code, "\u0165")
            PICKER_SETS.put('u'.code, "\u00F9\u00FA\u00FB\u00FC\u016F\u016B")
            PICKER_SETS.put('y'.code, "\u00FD\u00FF")
            PICKER_SETS.put('z'.code, "\u017A\u017C\u017E")
            PICKER_SETS.put(
                KeyCharacterMap.PICKER_DIALOG_INPUT.code,
                "\u2026\u00A5\u2022\u00AE\u00A9\u00B1[]{}\\|"
            )
            PICKER_SETS.put('/'.code, "\\")

            // From packages/inputmethods/LatinIME/res/xml/kbd_symbols.xml
            PICKER_SETS.put('1'.code, "\u00b9\u00bd\u2153\u00bc\u215b")
            PICKER_SETS.put('2'.code, "\u00b2\u2154")
            PICKER_SETS.put('3'.code, "\u00b3\u00be\u215c")
            PICKER_SETS.put('4'.code, "\u2074")
            PICKER_SETS.put('5'.code, "\u215d")
            PICKER_SETS.put('7'.code, "\u215e")
            PICKER_SETS.put('0'.code, "\u207f\u2205")
            PICKER_SETS.put('$'.code, "\u00a2\u00a3\u20ac\u00a5\u20a3\u20a4\u20b1")
            PICKER_SETS.put('%'.code, "\u2030")
            PICKER_SETS.put('*'.code, "\u2020\u2021")
            PICKER_SETS.put('-'.code, "\u2013\u2014")
            PICKER_SETS.put('+'.code, "\u00b1")
            PICKER_SETS.put('('.code, "[{<")
            PICKER_SETS.put(')'.code, "]}>")
            PICKER_SETS.put('!'.code, "\u00a1")
            PICKER_SETS.put('"'.code, "\u201c\u201d\u00ab\u00bb\u02dd")
            PICKER_SETS.put('?'.code, "\u00bf")
            PICKER_SETS.put(','.code, "\u201a\u201e")

            // From packages/inputmethods/LatinIME/res/xml/kbd_symbols_shift.xml
            PICKER_SETS.put('='.code, "\u2260\u2248\u221e")
            PICKER_SETS.put('<'.code, "\u2264\u00ab\u2039")
            PICKER_SETS.put('>'.code, "\u2265\u00bb\u203a")
        }
    }
}
