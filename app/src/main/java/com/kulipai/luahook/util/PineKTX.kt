package com.kulipai.luahook.util

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method


//调用方法
operator fun Any.invoke(methodName: String, vararg params: Any?): Any? {
    //如果this是it，就获取it.thisObject
    val thisObject = if (this is XC_MethodHook.MethodHookParam) {
        this.thisObject
    } else {
        this
    }
    return XposedHelpers.callMethod(thisObject, methodName, *params)
}


fun Any.invokeTypes(methodName: String, types: Array<Class<*>>, vararg params: Class<*>?): Any? {
    val method = this.javaClass.getDeclaredMethod(methodName, *types)
    method.isAccessible = true
    return method.invoke(this, *params)
}
//
//fun Any.getObjectField(name: String): Any? {
//    return XposedHelpers.getObjectField(this, name)
//}


// 反射获取字段值
fun Any.getField(name: String): Any? = XposedHelpers.getObjectField(this, name)

// 反射设置字段值
fun Any.setField(name: String, value: Any?) = XposedHelpers.setObjectField(this, name, value)

// 获取静态字段
fun Class<*>.getStaticField(name: String): Any? = XposedHelpers.getStaticObjectField(this, name)

// 设置静态字段
fun Class<*>.setStaticField(name: String, value: Any?) = XposedHelpers.setStaticObjectField(this, name, value)


fun Class<*>.invokeStatic(methodName: String, vararg params: Any?): Any? {
    return XposedHelpers.callStaticMethod(this, methodName, *params)
}

fun String.invoke(classLoader: ClassLoader, methodName: String, vararg params: Any?): Any? {
    return XposedHelpers.callStaticMethod(
        XposedHelpers.findClass(this, classLoader), methodName, *params
    )
}

fun Class<*>.new(vararg params: Any?): Any? = XposedHelpers.newInstance(this, *params)


fun String.findClass(classLoader: ClassLoader): Class<*> {
    return XposedHelpers.findClass(this, classLoader)
}

fun String.tryFindClass(classLoader: ClassLoader): Class<*>? {
    try {
        return XposedHelpers.findClass(this, classLoader)
    } catch (e: Throwable) {
        e.log("tryFindClass")
    }
    return null
}

fun String.hook(
    classLoader: ClassLoader,
    methodName: String,
    vararg paramsType: Any?,
    before: (param: XC_MethodHook.MethodHookParam) -> Unit = {},
    after: (param: XC_MethodHook.MethodHookParam) -> Unit = {}
) {
    XposedHelpers.findAndHookMethod(this,
        classLoader,
        methodName,
        *paramsType,
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam?) {
                param?.let { before.invoke(it) }
            }

            override fun afterHookedMethod(param: MethodHookParam?) {
                param?.let { after.invoke(it) }
            }
        })
}

fun String.replaceMethod(
    classLoader: ClassLoader,
    methodName: String,
    vararg paramsType: Any?,
    replace: (param: XC_MethodHook.MethodHookParam) -> Any = { }
) {
    XposedHelpers.findAndHookMethod(this,
        classLoader,
        methodName,
        *paramsType,
        object : XC_MethodReplacement() {
            override fun replaceHookedMethod(p0: MethodHookParam?): Any {
                return replace.invoke(p0!!)
            }
        })
}

fun Class<*>.hook(
    methodName: String,
    vararg paramsType: Any?,
    before: (param: XC_MethodHook.MethodHookParam) -> Unit = {},
    after: (param: XC_MethodHook.MethodHookParam) -> Unit = {}
) {
    XposedHelpers.findAndHookMethod(this, methodName, *paramsType, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam?) {
            param?.let { before.invoke(it) }
        }

        override fun afterHookedMethod(param: MethodHookParam?) {
            param?.let { after.invoke(it) }
        }
    })
}

fun Class<*>.replace(
    methodName: String,
    vararg paramsType: Any?,
    replace: (param: XC_MethodHook.MethodHookParam) -> Any? = { }
) {
    XposedHelpers.findAndHookMethod(this, methodName, *paramsType, object : XC_MethodReplacement() {
        override fun replaceHookedMethod(p0: MethodHookParam?): Any? {
            return replace.invoke(p0!!)
        }
    })
}

fun Class<*>.hookCtor(
    vararg paramsType: Any?,
    before: (param: XC_MethodHook.MethodHookParam) -> Unit = {},
    after: (param: XC_MethodHook.MethodHookParam) -> Unit = {}
) {
    XposedHelpers.findAndHookConstructor(this, *paramsType, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam?) {
            param?.let { before.invoke(it) }
        }

        override fun afterHookedMethod(param: MethodHookParam?) {
            param?.let { after.invoke(it) }
        }
    })
}

fun Class<*>.hookAllCtor(
    before: (param: XC_MethodHook.MethodHookParam) -> Unit = {},
    after: (param: XC_MethodHook.MethodHookParam) -> Unit = {}
) {
    XposedBridge.hookAllConstructors(this, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam?) {
            param?.let { before.invoke(it) }
        }

        override fun afterHookedMethod(param: MethodHookParam?) {
            param?.let { after.invoke(it) }
        }
    })
}

fun String.hookCtor(
    classLoader: ClassLoader,
    vararg paramsType: Any?,
    before: (param: XC_MethodHook.MethodHookParam) -> Unit = {},
    after: (param: XC_MethodHook.MethodHookParam) -> Unit = {}
) {
    XposedHelpers.findAndHookConstructor(XposedHelpers.findClass(this, classLoader),
        *paramsType,
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam?) {
                param?.let { before.invoke(it) }
            }

            override fun afterHookedMethod(param: MethodHookParam?) {
                param?.let { after.invoke(it) }
            }
        })
}

fun XC_MethodHook.MethodHookParam.callOrig(vararg args: Any?): Any? {
    return XposedBridge.invokeOriginalMethod(this.method, this, args)
}

fun XC_MethodHook.MethodHookParam.orig(): Any? {
    return XposedBridge.invokeOriginalMethod(this.method, this, this.args)
}

fun Method.hook(
    after: (XC_MethodHook.MethodHookParam) -> Unit = {},
    before: (XC_MethodHook.MethodHookParam) -> Unit = {}
) {
    XposedBridge.hookMethod(this, object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam?) {
            param?.let { after.invoke(it) }
        }

        override fun beforeHookedMethod(param: MethodHookParam?) {
            param?.let { before.invoke(it) }
        }
    })
}

fun Method.replace(
    replace: (XC_MethodHook.MethodHookParam) -> Any? = {}
) {
    XposedBridge.hookMethod(this, object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam?): Any? {
            return param?.let { replace.invoke(it) }
        }
    })
}
