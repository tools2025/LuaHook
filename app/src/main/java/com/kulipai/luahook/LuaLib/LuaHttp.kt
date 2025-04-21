import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.luaj.vm2.*
import org.luaj.vm2.lib.*
import java.io.IOException

class LuaHttp : OneArgFunction() {

    private val client = OkHttpClient()  // 确保 OkHttpClient 实例已定义
    override fun call(env: LuaValue): LuaValue {
        val http = LuaTable()


        http["get"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val url = args.checkjstring(1)
                val callback = args.checkfunction(args.narg())
                val headers = if (args.narg() > 2 && args.istable(2)) args.checktable(2) else null
                val cookie = if (args.narg() > 3) args.optjstring(3, null) else null

                val requestBuilder = Request.Builder().url(url)

                // 添加请求头
                headers?.let {
                    val headerMap = mutableMapOf<String, String>()

                    // 遍历 LuaTable
                    var key: LuaValue = NIL
                    while (true) {
                        val nextPair = headers.next(key)
                        key = nextPair.arg1()  // 获取下一个键
                        val value = nextPair.arg(2)  // 获取键对应的值

                        if (key.isnil()) break  // 遍历完所有键，退出循环

                        headerMap[key.tojstring()] = value.tojstring()
                    }

                    for ((k, v) in headerMap) {
                        requestBuilder.addHeader(k, v)
                    }
                }

                // 添加 Cookie
                cookie?.let {
                    requestBuilder.addHeader("Cookie", it)
                }

                val request = requestBuilder.build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback.call(LuaValue.valueOf(-1), LuaValue.valueOf(e.message ?: ""))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body?.string() ?: ""
                        callback.call(LuaValue.valueOf(response.code), LuaValue.valueOf(body))
                    }
                })

                return NIL
            }
        }

        http["post"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val url = args.checkjstring(1)
                val postData = args.checkjstring(2)
                val callback = args.checkfunction(args.narg())
                val headers = if (args.narg() > 3 && args.istable(3)) args.checktable(3) else null
                val cookie = if (args.narg() > 4) args.optjstring(4, null) else null

                // 默认内容类型
                var contentType = "application/x-www-form-urlencoded"

                // 检查headers中是否指定了Content-Type
                headers?.let {
                    var key: LuaValue = LuaValue.NIL
                    while (true) {
                        val nextPair = headers.next(key)
                        key = nextPair.arg1()
                        val value = nextPair.arg(2)

                        if (key.isnil()) break

                        if (key.tojstring().equals("Content-Type", ignoreCase = true)) {
                            contentType = value.tojstring()
                            break
                        }
                    }
                }

                // 根据内容类型创建RequestBody
                val requestBody = postData.toRequestBody(contentType.toMediaTypeOrNull())

                val requestBuilder = Request.Builder().url(url).post(requestBody)

                // 添加请求头
                headers?.let {
                    val headerMap = mutableMapOf<String, String>()

                    var key: LuaValue = LuaValue.NIL
                    while (true) {
                        val nextPair = headers.next(key)
                        key = nextPair.arg1()  // 获取下一个键
                        val value = nextPair.arg(2)  // 获取键对应的值

                        if (key.isnil()) break  // 遍历完所有键，退出循环

                        headerMap[key.tojstring()] = value.tojstring()
                    }
                    for ((k, v) in headerMap) {
                        requestBuilder.addHeader(k, v)
                    }
                }

                // 添加 Cookie
                cookie?.let {
                    requestBuilder.addHeader("Cookie", it)
                }

                val request = requestBuilder.build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback.call(LuaValue.valueOf(-1), LuaValue.valueOf(e.message ?: ""))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body?.string() ?: ""
                        callback.call(LuaValue.valueOf(response.code), LuaValue.valueOf(body))
                    }
                })

                return NIL
            }
        }


        // 新增 postJson 方法，专门用于发送 JSON 数据
        http["postJson"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val url = args.checkjstring(1)
                val jsonData = args.checkjstring(2)
                val callback = args.checkfunction(args.narg())
                val headers = if (args.narg() > 3 && args.istable(3)) args.checktable(3) else null
                val cookie = if (args.narg() > 4) args.optjstring(4, null) else null

                // 创建 JSON 类型的请求体
                val requestBody = jsonData.toRequestBody("application/json".toMediaTypeOrNull())

                val requestBuilder = Request.Builder().url(url).post(requestBody)

                // 添加请求头
                headers?.let {
                    val headerMap = mutableMapOf<String, String>()

                    var key: LuaValue = LuaValue.NIL
                    while (true) {
                        val nextPair = headers.next(key)
                        key = nextPair.arg1()
                        val value = nextPair.arg(2)

                        if (key.isnil()) break

                        headerMap[key.tojstring()] = value.tojstring()
                    }
                    for ((k, v) in headerMap) {
                        requestBuilder.addHeader(k, v)
                    }
                }

                // 添加 Cookie
                cookie?.let {
                    requestBuilder.addHeader("Cookie", it)
                }

                val request = requestBuilder.build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback.call(LuaValue.valueOf(-1), LuaValue.valueOf(e.message ?: ""))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body?.string() ?: ""
                        callback.call(LuaValue.valueOf(response.code), LuaValue.valueOf(body))
                    }
                })

                return NIL
            }
        }

        env.set("http", http)
        return http
    }
}