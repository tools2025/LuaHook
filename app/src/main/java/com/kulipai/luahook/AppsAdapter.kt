import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.kulipai.luahook.AppInfo
import com.kulipai.luahook.R




class AppsAdapter(private var apps: List<AppInfo>) :
    RecyclerView.Adapter<AppsAdapter.AppsViewHolder>() {

    inner class AppsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name:TextView = itemView.findViewById(R.id.name)
        val icon:ImageView = itemView.findViewById(R.id.icon)
        val packageName:TextView = itemView.findViewById(R.id.packageName)
        val version:TextView = itemView.findViewById(R.id.version)
        val card:MaterialCardView = itemView.findViewById(R.id.card)

        init {
            card.setOnClickListener{

            }


        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppsViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_apps, parent, false) // 加载自定义布局
        return AppsViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: AppsViewHolder, position: Int) {
        holder.name.text= apps[position].appName
        holder.packageName.text= apps[position].packageName
        holder.version.text= apps[position].versionName
        holder.icon.setImageDrawable(apps[position].icon)

    }

    override fun getItemCount(): Int {
        return apps.size
    }


    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newList: List<AppInfo>) {
        apps = newList
        notifyDataSetChanged()
    }
}