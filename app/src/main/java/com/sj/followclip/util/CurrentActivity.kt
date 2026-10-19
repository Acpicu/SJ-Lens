package com.sj.followclip.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * 当前处于前台的 Activity。
 *
 * 检查流程需要它：快手只能用真实 WebView 抓，而 WebView 必须挂到窗口上。
 * 检查跑在**应用级协程**里（切页面也不能断），所以拿 Activity 不能靠 composable 的上下文，
 * 只能由 Activity 自己登记过来。
 */
object CurrentActivity {

    private var ref: WeakReference<Activity>? = null

    fun get(): Activity? = ref?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    ref = WeakReference(activity)
                }

                override fun onActivityPaused(activity: Activity) {
                    if (ref?.get() === activity) ref = null
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) {
                    if (ref?.get() === activity) ref = null
                }
            }
        )
    }
}
