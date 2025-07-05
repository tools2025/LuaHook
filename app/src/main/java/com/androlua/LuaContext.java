package com.androlua;

import android.content.Context;
import java.util.ArrayList;
import java.util.Map;
import org.luaj.Globals;
import org.luaj.lib.ResourceFinder;

/* loaded from: luajpp_nocglib.jar:com/androlua/LuaContext.class */
public interface LuaContext extends ResourceFinder {
    void call(String str, Object... objArr);

    Object doFile(String str, Object... objArr);

    ArrayList<ClassLoader> getClassLoaders();

    Context getContext();

    Map getGlobalData();

    int getHeight();

    String getLuaDir();

    String getLuaDir(String str);

    String getLuaExtDir();

    String getLuaExtDir(String str);

    String getLuaExtPath(String str);

    String getLuaExtPath(String str, String str2);

    String getLuaPath();

    String getLuaPath(String str);

    String getLuaPath(String str, String str2);

    Globals getLuaState();

    Object getSharedData(String str);

    Object getSharedData(String str, Object obj);

    Map<String, ?> getSharedData();

    int getWidth();

//    void regGc(LuaGcable luaGcable);

    void sendError(String str, Exception exc);

    void sendMsg(String str);

    void set(String str, Object obj);

    void setLuaExtDir(String str);

    boolean setSharedData(String str, Object obj);
}