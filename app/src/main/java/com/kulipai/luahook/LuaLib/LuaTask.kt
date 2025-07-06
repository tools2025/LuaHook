
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.luaj.LuaTable
import org.luaj.LuaValue
import org.luaj.Varargs
import org.luaj.lib.OneArgFunction
import org.luaj.lib.VarArgFunction

class LuaTask : OneArgFunction() {

    override fun call(env: LuaValue): LuaValue {

        env["Task"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val func = args.checkfunction(1)
                val time = if(args.narg()==2) args.checknumber(2).tolong() else 0L
                CoroutineScope(Dispatchers.Main).launch {

                    delay(time)
                    func.invoke()
                }

                return NIL
            }

        }

        return NIL
    }

}
