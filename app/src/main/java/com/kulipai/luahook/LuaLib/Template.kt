import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import org.json.JSONArray
import org.luaj.*
import org.luaj.lib.*
import org.luaj.lib.VarArgFunction
import java.io.IOException

class LuaTem : OneArgFunction() {

    private val modName = ""
    private var mod = LuaTable()

    override fun call(env: LuaValue): LuaValue {

        //func
        mod["func1"] = object : VarArgFunction() {

        }
        //...


        env.set(modName, mod)
        return mod
    }

}
