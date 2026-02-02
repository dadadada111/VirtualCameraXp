package com.sandyz.virtualcam.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.sandyz.virtualcam.hooks.IHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors


/**
 *@author sandyz987
 *@date 2023/11/18
 *@description
 */

@SuppressLint("StaticFieldLeak")
object HookUtils {
    var app: Context? = null

    // 获取当前Activity用
    private val activityTop = mutableListOf<WeakReference<Activity>>()
    fun getActivities(): List<Activity> {
        val activities = mutableListOf<Activity>()
        val iterator = activityTop.iterator()
        while (iterator.hasNext()) {
            val activity = iterator.next().get()
            if (activity != null && !activity.isFinishing) {
                activities.add(activity)
            } else {
                iterator.remove()
            }
        }
        return activities
    }

    fun getTopActivity(): Activity? {
        val activities = getActivities()
        return if (activities.isEmpty()) {
            null
        } else {
            activities[0]
        }
    }

    fun getLifecycle(): Lifecycle? {
        // 反射获取lifecycle提高成功率
        val activity = getTopActivity()
        mutableListOf(
            "androidx.lifecycle.LifecycleOwner",
            "android.arch.lifecycle.LifecycleOwner",
            "android.support.v4.app.FragmentActivity",
            "android.support.v4.app.SupportActivity",
            "androidx.fragment.app.FragmentActivity",
            "androidx.appcompat.app.AppCompatActivity",
            "androidx.activity.ComponentActivity",
            "androidx.core.app.ComponentActivity",
        ).forEach {
            try {
                val clazz = try {
                    XposedHelpers.findClass(it, activity?.classLoader)
                } catch (t: Throwable) {
                    Class.forName(it)
                }
                val activityCast = clazz?.cast(activity)
                val function = clazz?.getDeclaredMethod("getLifecycle")
                function?.isAccessible = true
                val lifecycle = function?.invoke(activityCast) as? Lifecycle
                if (lifecycle != null) {
                    return lifecycle
                } else {
                    xLog("lifecycle is null")
                }
            } catch (t: Throwable) {
                xLog(t.toString())
            }
        }
        return null
    }


    private val coroutineScopeMap = HashMap<Activity, CoroutineScope>()

    fun coroutineScope(): CoroutineScope = if (coroutineScopeMap[getTopActivity()] != null) {
        coroutineScopeMap[getTopActivity()]!!
    } else {
        MyCoroutineScope().also {
            xLog("activity: ${getTopActivity()}")
            xLog("lifecycle2: ${getLifecycle()}")
            val activity = getTopActivity()?: return@also
            val activityLifecycle = getLifecycle()?: return@also
            val lifecycleEventObserver = object :LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    if (event == Lifecycle.Event.ON_DESTROY) {
                        it.cancel()
                        activityLifecycle.removeObserver(this)
                        coroutineScopeMap.remove(activity)
                    }
                }
            }
            activityLifecycle.addObserver(lifecycleEventObserver)
            coroutineScopeMap[activity] = it
        }
    }

    fun getView(): View? = getTopActivity()?.window?.decorView

    fun getContentView(): ViewGroup? = getView()?.findViewById(android.R.id.content) as? ViewGroup

    fun dumpView(v: View?, depth: Int) {
        v ?: return
        xLog("${"  ".repeat(depth)}${v.javaClass.name}")
        if (v is ViewGroup) {
            v.children.forEach {
                dumpView(it, depth + 1)
            }
        }
    }

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        val instrumentation = XposedHelpers.findClass(
            "android.app.Instrumentation", lpparam.classLoader
        )
        XposedBridge.hookAllMethods(instrumentation, "callApplicationOnCreate", object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                app = param.args[0] as Context
            }
        })

        val activity = XposedHelpers.findClass(
            "android.app.Activity", lpparam.classLoader
        )
        XposedBridge.hookAllConstructors(activity, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!getActivities().contains(param.thisObject)) {
                    activityTop.add(0, WeakReference(param.thisObject as Activity))
                }
            }
        })
    }

}

/**
 * 日志文件管理器
 */
object LogFileManager {
    private const val LOG_DIR = "/sdcard/DCIM/XVirtualCamera/logs/"
    private const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024 // 5MB
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val executor = Executors.newSingleThreadExecutor()
    
    init {
        // 确保日志目录存在
        try {
            val dir = File(LOG_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
        } catch (e: Exception) {
            // 忽略初始化错误
        }
    }
    
    /**
     * 写入日志到文件
     */
    fun writeToFile(tag: String, msg: String?) {
        executor.execute {
            try {
                val logFile = getLogFile()
                if (logFile == null) return@execute
                
                val timestamp = timeFormat.format(Date())
                val logEntry = "[$timestamp] [$tag] $msg\n"
                
                // 检查文件大小，如果超过限制则轮转
                if (logFile.length() > MAX_LOG_FILE_SIZE) {
                    rotateLogFile(logFile)
                }
                
                FileWriter(logFile, true).use { writer ->
                    writer.append(logEntry)
                    writer.flush()
                }
            } catch (e: Exception) {
                // 写入失败时只输出到XposedBridge，避免循环
                // 忽略权限错误，避免LSPosed日志刷屏
            }
        }
    }
    
    /**
     * 获取当前日志文件
     */
    private fun getLogFile(): File? {
        try {
            val today = dateFormat.format(Date())
            val logFile = File(LOG_DIR, "xvirtualcamera_$today.log")
            
            // 如果文件不存在，创建它
            if (!logFile.exists()) {
                logFile.parentFile?.mkdirs()
                logFile.createNewFile()
                // 设置为所有人可读写，以便不同UID的进程都能写入日志
                logFile.setReadable(true, false)
                logFile.setWritable(true, false)
            } else {
                // 确保已有文件的权限也是开放的
                logFile.setReadable(true, false)
                logFile.setWritable(true, false)
            }
            
            return logFile
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * 轮转日志文件
     */
    private fun rotateLogFile(logFile: File) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val rotatedFile = File(logFile.parent, "${logFile.nameWithoutExtension}_$timestamp.log")
            logFile.renameTo(rotatedFile)
            
            // 删除7天前的日志文件
            cleanupOldLogs()
        } catch (e: Exception) {
            // 忽略轮转错误
        }
    }
    
    /**
     * 清理旧日志文件（保留最近7天）
     */
    private fun cleanupOldLogs() {
        try {
            val logDir = File(LOG_DIR)
            if (!logDir.exists() || !logDir.isDirectory) return
            
            val files = logDir.listFiles { file ->
                file.isFile && file.name.startsWith("xvirtualcamera_") && file.name.endsWith(".log")
            } ?: return
            
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            
            files.forEach { file ->
                if (file.lastModified() < sevenDaysAgo) {
                    try {
                        file.delete()
                    } catch (e: Exception) {
                        // 忽略删除错误
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略清理错误
        }
    }
    
    /**
     * 写入异常堆栈到日志文件
     */
    fun writeException(tag: String, throwable: Throwable) {
        executor.execute {
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTrace = sw.toString()
                
                val logFile = getLogFile()
                if (logFile == null) return@execute
                
                val timestamp = timeFormat.format(Date())
                val logEntry = "[$timestamp] [$tag] Exception:\n$stackTrace\n"
                
                FileWriter(logFile, true).use { writer ->
                    writer.append(logEntry)
                    writer.flush()
                }
            } catch (e: Exception) {
                // 忽略写入错误
            }
        }
    }
}

fun IHook.xLog(msg: String?) {
    val logMsg = "[${this::class.java.simpleName} ${Thread.currentThread().id}] $msg"
    XposedBridge.log(logMsg)
    LogFileManager.writeToFile(this::class.java.simpleName, msg)
}

fun xLog(msg: String?) {
    val logMsg = "[${Thread.currentThread().id}] $msg"
    XposedBridge.log(logMsg)
    LogFileManager.writeToFile("XVirtualCamera", msg)
}

fun xLog(param: XC_MethodHook.MethodHookParam?, msg: String?, depth: Int = 15) {
    xLog(msg)
    if (param == null) {
        return
    }
    val stackTrace = Thread.currentThread().stackTrace as Array<StackTraceElement>
    stackTrace.forEachIndexed { index, stackTraceElement ->
        if (stackTraceElement.className.equals("LSPHooker_")) {
            for (i in index + 1..index + depth) {
                if (i < stackTrace.size) {
                    xLog("          ${stackTrace[i].className}.${stackTrace[i].methodName}")
                }
            }
        }
    }
}

/**
 * 记录异常到日志文件
 */
fun xLogException(tag: String, throwable: Throwable) {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    throwable.printStackTrace(pw)
    val stackTrace = sw.toString()
    xLog("$tag Exception: $stackTrace")
    LogFileManager.writeException(tag, throwable)
}

fun xLogTrace(param: XC_MethodHook.MethodHookParam?, msg: String?) {
    if (param == null) {
        xLog(msg)
        return
    }
    xLog(msg)
    val stackTrace = Thread.currentThread().stackTrace as Array<StackTraceElement>
    stackTrace.forEach {
        xLog("          ${it.className}.${it.methodName}")

    }
}

fun toast(context: Context?, text: CharSequence, duration: Int) {
    try {
        context?.let {
            Toast.makeText(it, text, duration).show()
        }
    } catch (e: Throwable) {
        xLog("toast: $text")
    }
}

class MyCoroutineScope: CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext = Dispatchers.IO +
            job +
            CoroutineName("MyCoroutineScope") +
            CoroutineExceptionHandler{ coroutineContext, throwable ->
                xLog("coroutineException in $coroutineContext: $throwable")
            }
}