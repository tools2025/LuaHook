import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.kulipai.luahook.util.ShellManager

object DataRepository {
    private val _shellMode = MutableLiveData<ShellManager.Mode>()
    val ShellMode: LiveData<ShellManager.Mode> get() = _shellMode

    fun ShellInit(context: Context) {
        ShellManager.init(context) {
            _shellMode.postValue(ShellManager.getMode())
        }

    }



}
