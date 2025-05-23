package com.androlua

import android.app.Application
import dx.proxy.Enhancer
import dx.proxy.EnhancerInterface
import dx.proxy.MethodFilter
import dx.proxy.MethodInterceptor
import org.luaj.LuaValue
import java.lang.reflect.Method

/**
 * Created by nirenr on 2018/12/19.
 */
internal class LuaEnhancer(cls: Class<*>?) {
    private val mEnhancer: Enhancer = Enhancer(Application())

    constructor(cls: String) : this(Class.forName(cls))

    init {
        mEnhancer.setSuperclass(cls)
    }

    fun setInterceptor(obj: EnhancerInterface, interceptor: MethodInterceptor?) {
        obj.setMethodInterceptor_Enhancer(interceptor)
    }

    fun create(): Class<*>? {
        try {
            return mEnhancer.create()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun create(filer: MethodFilter?): Class<*>? {
        try {
            mEnhancer.setMethodFilter(filer)
            return mEnhancer.create()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun create(arg: LuaValue): Class<*>? {
        val filter = MethodFilter { method: Method?, name: String? -> !arg.get(name).isnil() }
        try {
            mEnhancer.setMethodFilter(filter)
            val cls = mEnhancer.create()
            setInterceptor(cls, LuaMethodInterceptor(arg))
            return cls
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    companion object {
        fun setInterceptor(obj: Class<*>, interceptor: MethodInterceptor?) {
            try {
                val field = obj.getDeclaredField("methodInterceptor")
                field.setAccessible(true)
                field.set(obj, interceptor)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
