package com.myopicmobile.textwarrior.android

import android.R
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.ListPopupWindow
import android.widget.TextView
import com.myopicmobile.textwarrior.common.Flag
import com.myopicmobile.textwarrior.common.Language
import com.myopicmobile.textwarrior.common.LanguageNonProg
import java.util.Locale
import kotlin.math.min

class AutoCompletePanel(private val _textField: FreeScrollingTextField) {
    private val _context: Context
    private var _autoCompletePanel: ListPopupWindow? = null
    private var _adapter: MyAdapter? = null
    private var _filter: Filter? = null

    private var _verticalOffset = 0

    private var _height = 0

    private var _horizontal = 0

    private var _constraint: CharSequence? = null
    private val _userWords = arrayOfNulls<String>(0)

    private var _backgroundColor = 0

    private var gd: GradientDrawable? = null

    private var _textColor = 0

    init {
        _context = _textField.getContext()
        initAutoCompletePanel()
    }

    fun setTextColor(color: Int) {
        _textColor = color
        gd!!.setStroke(1, color)
        _autoCompletePanel!!.setBackgroundDrawable(gd)
    }




    fun setBackgroundColor(color: Int) {
        _backgroundColor = color
        gd!!.setColor(color)
        _autoCompletePanel!!.setBackgroundDrawable(gd)
    }

    fun setBackground(color: Drawable?) {
        _autoCompletePanel!!.setBackgroundDrawable(color)
    }

    @SuppressLint("ResourceType")
    private fun initAutoCompletePanel() {
        _autoCompletePanel = ListPopupWindow(_context)
        _autoCompletePanel!!.setAnchorView(_textField)
        _adapter = MyAdapter(_context, R.layout.simple_list_item_1)
        _autoCompletePanel!!.setAdapter(_adapter)
        //_autoCompletePanel.setDropDownGravity(Gravity.BOTTOM | Gravity.LEFT);
        _filter = _adapter!!.getFilter()
        setHeight(300)

        val array = _context.getTheme().obtainStyledAttributes(
            intArrayOf(
                R.attr.colorBackground,
                R.attr.textColorPrimary,
            )
        )
        val backgroundColor = array.getColor(0, 0xFF00FF)
        val textColor = array.getColor(1, 0xFF00FF)
        array.recycle()
        gd = GradientDrawable()
        gd!!.setColor(backgroundColor)
        gd!!.setCornerRadius(4f)
        gd!!.setStroke(1, textColor)
        setTextColor(textColor)
        _autoCompletePanel!!.setBackgroundDrawable(gd)
        _autoCompletePanel!!.setOnItemClickListener(object : OnItemClickListener {
            override fun onItemClick(p1: AdapterView<*>?, p2: View, p3: Int, p4: Long) {
                // TODO: Implement this method
                _textField.replaceText(
                    _textField.caretPosition - _constraint!!.length,
                    _constraint!!.length,
                    (p2 as TextView).getText().toString()
                )
                _adapter!!.abort()
                dismiss()
            }
        })
    }

    fun setWidth(width: Int) {
        // TODO: Implement this method
        _autoCompletePanel!!.setWidth(width)
    }

    private fun setHeight(height: Int) {
        // TODO: Implement this method

        if (_height != height) {
            _height = height
            _autoCompletePanel!!.setHeight(height)
        }
    }

    private fun setHorizontalOffset(horizontal: Int) {
        // TODO: Implement this method
        var horizontal = horizontal
        horizontal = min(horizontal.toDouble(), (_textField.getWidth() / 2).toDouble()).toInt()
        if (_horizontal != horizontal) {
            _horizontal = horizontal
            _autoCompletePanel!!.setHorizontalOffset(horizontal)
        }
    }


    private fun setVerticalOffset(verticalOffset: Int) {
        // TODO: Implement this method
        //verticalOffset=Math.min(verticalOffset,_textField.getWidth()/2);
        var verticalOffset = verticalOffset
        val max = 0 - _autoCompletePanel!!.getHeight()
        if (verticalOffset > max) {
            _textField.scrollBy(0, verticalOffset - max)
            verticalOffset = max
        }
        if (_verticalOffset != verticalOffset) {
            _verticalOffset = verticalOffset
            _autoCompletePanel!!.setVerticalOffset(verticalOffset)
        }
    }

    fun update(constraint: CharSequence?) {
        _adapter!!.restart()
        _filter!!.filter(constraint)
    }

    fun show() {
        if (!_autoCompletePanel!!.isShowing()) _autoCompletePanel!!.show()
        _autoCompletePanel!!.getListView()!!.setFadingEdgeLength(0)
    }

    fun dismiss() {
        if (_autoCompletePanel!!.isShowing()) {
            _autoCompletePanel!!.dismiss()
        }
    }

    @Synchronized
    fun setLanguage(lang: Language) {
        language = lang
    }

    /**
     * Adapter定义
     */
    internal inner class MyAdapter(context: Context, resource: Int) :
        ArrayAdapter<String?>(context, resource), Filterable {
        private var _h = 0
        private val _abort: Flag

        private val dm: DisplayMetrics?

        init {
            _abort = Flag()
            setNotifyOnChange(false)
            dm = context.getResources().getDisplayMetrics()
        }

        fun abort() {
            _abort.set()
        }


        private fun dp(n: Float): Int {
            // TODO: Implement this method
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, n, dm).toInt()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            // TODO: Implement this method
            val view = super.getView(position, convertView, parent) as TextView
            /*TextView view=null;
			if(convertView==null){
				 view=new TextView(_context);
				 view.setTextSize(16);
				 view.setPadding(dp(8),dp(3),dp(8),dp(3));
			}
			else{
				view=(TextView) convertView;
			}
			view.setText(getItem(position));*/
            view.setTextColor(_textColor)
            return view
        }


        fun restart() {
            // TODO: Implement this method
            _abort.clear()
        }

        val itemHeight: Int
            get() {
                if (_h != 0) return _h

                val inflater =
                    getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                val item = inflater.inflate(
                    R.layout.simple_list_item_1,
                    null
                ) as TextView
                item.measure(0, 0)
                _h = item.getMeasuredHeight()
                return _h
            }

        /**
         * 实现自动完成的过滤算法
         */
        override fun getFilter(): Filter {
            val filter: Filter = object : Filter() {
                /**
                 * 本方法在后台线程执行，定义过滤算法
                 */
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    /*int l=constraint.length();
					 int i=l;
					 for(;i>0;i--){
					 if(constraint.charAt(l-1)=='.')
					 break;
					 }
					 if(i>0){
					 constraint=constraint.subSequence(i,l);
					 }*/

                    // 此处实现过滤
                    // 过滤后利用FilterResults将过滤结果返回

                    val buf = ArrayList<String?>()
                    var keyword = constraint.toString().lowercase(Locale.getDefault())
                    val ss =
                        keyword.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (ss.size == 2) {
                        val pkg: String? = ss[0]
                        keyword = ss[1]
                        if (language.isBasePackage(pkg)) {
                            val keywords: Array<String> = language.getBasePackage(pkg) as Array<String>
                            for (k in keywords) {
                                if (k.lowercase(Locale.getDefault()).startsWith(keyword)) buf.add(k)
                            }
                        }
                    } else if (ss.size == 1) {
                        if (keyword.get(keyword.length - 1) == '.') {
                            val pkg = keyword.substring(0, keyword.length - 1)
                            keyword = ""
                            if (language.isBasePackage(pkg)) {
                                val keywords: Array<String> = language.getBasePackage(pkg) as Array<String>
                                for (k in keywords) {
                                    buf.add(k)
                                }
                            }
                        } else {
                            var keywords: Array<String> = language.userWord as Array<String>
                            for (k in keywords) {
                                if (k.lowercase(Locale.getDefault()).startsWith(keyword)) buf.add(k)
                            }
                            keywords = language.keywords as Array<String>
                            for (k in keywords) {
                                if (k.indexOf(keyword) == 0) buf.add(k)
                            }
                            keywords = language.names as Array<String>
                            for (k in keywords) {
                                if (k.lowercase(Locale.getDefault()).startsWith(keyword)) buf.add(k)
                            }
                        }
                    }
                    _constraint = keyword
                    val filterResults = FilterResults()
                    filterResults.values = buf // results是上面的过滤结果
                    filterResults.count = buf.size // 结果数量
                    return filterResults
                }

                /**
                 * 本方法在UI线程执行，用于更新自动完成列表
                 */
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    if (results != null && results.count > 0 && !_abort.isSet) {
                        // 有过滤结果，显示自动完成列表
                        this@MyAdapter.clear() // 清空旧列表
                        this@MyAdapter.addAll((results.values as java.util.ArrayList<kotlin.String?>?)!!)
                        //int y = _textField.getPaintBaseline(_textField.getCaretRow()) - _textField.getScrollY();
                        val y =
                            _textField.caretY + _textField.rowHeight() / 2 - _textField.getScrollY()
                        setHeight(itemHeight * min(2.0, results.count.toDouble()).toInt())

                        //setHeight((int)(Math.min(_textField.getContentHeight()*0.4,getItemHeight() * Math.min(6, results.count))));
                        setHorizontalOffset(_textField.caretX - _textField.getScrollX())
                        setVerticalOffset(y - _textField.getHeight()) //_textField.getCaretY()-_textField.getScrollY()-_textField.getHeight());
                        notifyDataSetChanged()
                        show()
                    } else {
                        // 无过滤结果，关闭列表
                        notifyDataSetInvalidated()
                    }
                }
            }
            return filter
        }
    }

    companion object {
        @get:Synchronized
        var language: Language = LanguageNonProg.instance
            private set
    }
}
