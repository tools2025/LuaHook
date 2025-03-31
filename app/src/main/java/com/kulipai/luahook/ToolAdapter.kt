import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kulipai.luahook.LuaEditor
import com.kulipai.luahook.R

class ToolAdapter(private val symbols: List<String>, private val editor: LuaEditor) :
    RecyclerView.Adapter<ToolAdapter.SymbolViewHolder>() {

    inner class SymbolViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val symbolTextView: TextView = itemView.findViewById(R.id.symbolTextView) // 使用自定义布局中的 ID

        init {
            symbolTextView.setOnClickListener {
                val symbol = symbols[adapterPosition]
                var idx = editor.selectionStart
                if (editor.isSelected && symbol == "\"") {
                    editor.insert(editor.selectionStart, symbol)
                    editor.insert(editor.selectionEnd, symbol)
                }
                when (symbol) {
                    "hook" -> editor.insert(
                        idx,
                        """hook("class",
lpparam.classLoader,
"fun",
--"type",
function(it)
  --log(it.args[1])

end,
function(it)
  --log(it.result)
end)"""
                    )

                    "lp" -> editor.insert(idx,"lpparam")
                    else -> editor.insert(editor.selectionStart, symbol)
                }


                // 在实际应用中，这里会将符号插入到编辑框中
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SymbolViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_symbol, parent, false) // 加载自定义布局
        return SymbolViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: SymbolViewHolder, position: Int) {
        holder.symbolTextView.text = symbols[position]
    }

    override fun getItemCount(): Int {
        return symbols.size
    }
}