import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import org.json.JSONArray
import org.luaj.vm2.*
import org.luaj.vm2.lib.*
import org.luaj.vm2.lib.VarArgFunction
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

class Luafile : OneArgFunction() {

    private val modName = "file"
    private var file = LuaTable()

    override fun call(env: LuaValue): LuaValue {

        file["isFile"]= object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                LuaValue.valueOf(File(path.checkjstring()).isFile)
        }

        file["isDir"] =  object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                LuaValue.valueOf(File(path.checkjstring()).isDirectory)
        }

        file["isExists"] =  object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                LuaValue.valueOf(File(path.checkjstring()).exists())
        }

        file["read"] =  object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue = try {
                val content = String(Files.readAllBytes(Paths.get(path.checkjstring())))
                LuaValue.valueOf(content)
            } catch (e: Exception) {
                LuaValue.NIL
            }
        }

        file["readBytes"] =  object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue = try {
                val bytes = Files.readAllBytes(Paths.get(path.checkjstring()))
                LuaValue.valueOf(String(bytes)) // 可改为 base64 或返回 userdata
            } catch (e: Exception) {
                LuaValue.NIL
            }
        }

        file["write"] =  object : TwoArgFunction() {
            override fun call(path: LuaValue, content: LuaValue): LuaValue = try {
                Files.write(Paths.get(path.checkjstring()), content.checkjstring().toByteArray())
                LuaValue.TRUE
            } catch (e: Exception) {
                LuaValue.FALSE
            }
        }

        file["writeBytes"] =  object : TwoArgFunction() {
            override fun call(path: LuaValue, content: LuaValue): LuaValue = try {
                Files.write(Paths.get(path.checkjstring()), content.checkstring().m_bytes)
                LuaValue.TRUE
            } catch (e: Exception) {
                LuaValue.FALSE
            }
        }

        file["append"] =  object : TwoArgFunction() {
            override fun call(path: LuaValue, content: LuaValue): LuaValue = try {
                Files.write(
                    Paths.get(path.checkjstring()),
                    content.checkjstring().toByteArray(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                )
                TRUE
            } catch (_: Exception) {
                FALSE
            }
        }

        file["appendBytes"] =  object : TwoArgFunction() {
            override fun call(path: LuaValue, content: LuaValue): LuaValue = try {
                Files.write(
                    Paths.get(path.checkjstring()),
                    content.checkstring().m_bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                )
                TRUE
            } catch (_: Exception) {
                FALSE
            }
        }

        file["copy"] =  object : TwoArgFunction() {
            override fun call(from: LuaValue, to: LuaValue): LuaValue = try {
                Files.copy(
                    Paths.get(from.checkjstring()),
                    Paths.get(to.checkjstring()),
                    StandardCopyOption.REPLACE_EXISTING
                )
                TRUE
            } catch (e: Exception) {
                FALSE
            }
        }

        file["move"] =  object : TwoArgFunction() {
            override fun call(from: LuaValue, to: LuaValue): LuaValue = try {
                Files.move(
                    Paths.get(from.checkjstring()),
                    Paths.get(to.checkjstring()),
                    StandardCopyOption.REPLACE_EXISTING
                )
                TRUE
            } catch (e: Exception) {
                FALSE
            }
        }

        file["rename"] =  object : TwoArgFunction() {
            override fun call(path: LuaValue, newName: LuaValue): LuaValue {
                val f = File(path.checkjstring())
                return valueOf(f.renameTo(File(f.parentFile, newName.checkjstring())))
            }
        }

        file["delete"] =  object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                valueOf(File(path.checkjstring()).delete())
        }

        file["getName"] =  object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                valueOf(File(path.checkjstring()).name)
        }

        file["getSize"] =  object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                valueOf(File(path.checkjstring()).length().toInt())
        }

        env.set(modName, file)
        return file
    }

}
