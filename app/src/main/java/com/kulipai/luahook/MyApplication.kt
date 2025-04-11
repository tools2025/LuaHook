package com.kulipai.luahook
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyApplication : Application() {

    private var cachedAppList: List<AppInfo>? = null
    private var isLoading = false
    private val waiters = mutableListOf<CompletableDeferred<List<AppInfo>>>()


    companion object {
        lateinit var instance: MyApplication
            private set
    }


    // 挂起函数：异步获取 AppInfo 列表
    suspend fun getAppInfoList(packageNames: List<String>): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = packageManager
        val appInfoList = mutableListOf<AppInfo>()

        for (pkg in packageNames) {
            try {
                val packageInfo = pm.getPackageInfo(pkg, 0)
                val applicationInfo = pm.getApplicationInfo(pkg, 0)

                val appName = pm.getApplicationLabel(applicationInfo).toString()
                val icon = pm.getApplicationIcon(applicationInfo)
                val versionName = packageInfo.versionName ?: "N/A"
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }

                appInfoList.add(
                    AppInfo(
                        appName = appName,
                        packageName = pkg,
                        icon = icon,
                        versionName = versionName,
                        versionCode = versionCode
                    )
                )

            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
        }

        return@withContext appInfoList
    }
    suspend fun getAppListAsync(): List<AppInfo> {
        if (cachedAppList != null) return cachedAppList!!

        val deferred = CompletableDeferred<List<AppInfo>>()
        waiters.add(deferred)

        if (!isLoading) {
            isLoading = true
            // 启动加载
            CoroutineScope(Dispatchers.IO).launch {
                val apps = getInstalledApps(applicationContext)
                withContext(Dispatchers.Main) {
                    cachedAppList = apps
                    isLoading = false
                    waiters.forEach { it.complete(apps) }
                    waiters.clear()
                }
            }
        }

        return deferred.await()
    }
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        instance = this

    }
}