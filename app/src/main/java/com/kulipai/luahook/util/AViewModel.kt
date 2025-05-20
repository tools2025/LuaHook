import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.kulipai.luahook.util.ShellManager

class AViewModel : ViewModel() {
    val data: LiveData<ShellManager.Mode> = DataRepository.ShellMode
}
