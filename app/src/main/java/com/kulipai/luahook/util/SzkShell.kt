import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.kulipai.luahook.BuildConfig
import com.kulipai.luahook.IUserService
import com.kulipai.luahook.UserService
import rikka.shizuku.Shizuku



object SzkShell {
    private var userService: IUserService? = null

    fun bind(context: Context, onBound: (() -> Unit)? = null) {
        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, UserService::class.java.name)
        ).daemon(false).processNameSuffix("adb_service")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

        Shizuku.bindUserService(args, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder != null && binder.pingBinder()) {
                    userService = IUserService.Stub.asInterface(binder)
                    onBound?.invoke()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                userService = null
            }
        })
    }

    fun shell(cmd: String): String {
        if (userService == null) return "Service not bound"
        return try {
            userService!!.exec(cmd)
        } catch (e: RemoteException) {
            e.printStackTrace()
            "RemoteException: ${e.message}"
        }
    }
}
