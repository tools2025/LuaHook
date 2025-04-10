import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.kulipai.luahook.AppInfo
import com.kulipai.luahook.getInstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val _appList = MutableLiveData<List<AppInfo>>()
    val appList: LiveData<List<AppInfo>> = _appList

    private val _isLoaded = MutableLiveData<Boolean>(false)
    val isLoaded: LiveData<Boolean> = _isLoaded

    init {
        loadAppList()
    }

    private fun loadAppList() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = getInstalledApps(getApplication<Application>().applicationContext)
            withContext(Dispatchers.Main) {
                _appList.value = apps
                _isLoaded.value = true
            }
        }
    }
}
