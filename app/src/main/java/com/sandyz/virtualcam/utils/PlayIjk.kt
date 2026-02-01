package com.sandyz.virtualcam.utils

import android.view.Surface
import android.widget.Toast
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 *@author sandyz987
 *@date 2023/11/27
 *@description
 */

object PlayIjk {
    /**
     * 获取所有可能的publicDir路径
     * 因为/sdcard在Xposed hook上下文中可能无法正确解析，需要尝试多个路径
     */
    private fun getPublicDirPaths(): List<String> {
        val paths = mutableListOf<String>()
        
        // 1. 尝试 /sdcard/DCIM/XVirtualCamera/ (最常见)
        paths.add("/sdcard/DCIM/XVirtualCamera/")
        
        // 2. 尝试 /storage/emulated/0/DCIM/XVirtualCamera/ (实际路径)
        paths.add("/storage/emulated/0/DCIM/XVirtualCamera/")
        
        // 3. 尝试通过Context获取（如果可用） 
        try {
            val context = HookUtils.app
            if (context != null) {
                // 使用反射获取外部存储目录
                val envClass = Class.forName("android.os.Environment")
                val getExternalStorageDirectoryMethod = envClass.getMethod("getExternalStorageDirectory")
                val externalStorageDir = getExternalStorageDirectoryMethod.invoke(null) as? java.io.File
                if (externalStorageDir != null) {
                    val path = "${externalStorageDir.absolutePath}/DCIM/XVirtualCamera/"
                    if (!paths.contains(path)) {
                        paths.add(path)
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略反射失败
            LogFileManager.writeToFile("PlayIjk.getPublicDirPaths", "无法通过反射获取路径: ${e.message}")
        }
        
        return paths
    }
    
    /**
     * 播放视频总逻辑
     * vSurface: 要播放虚拟视频的surface
     * ijkMP: 播放器
     */
    fun play(vSurface: Surface?, ijkMP: IjkMediaPlayer?) {
        // 强制写入日志文件，确保即使xLog有问题也能看到
        try {
            LogFileManager.writeToFile("PlayIjk", "=== PlayIjk.play 被调用 ===")
            LogFileManager.writeToFile("PlayIjk", "virtualSurface: $vSurface, ijkMediaPlayer: $ijkMP")
            LogFileManager.writeToFile("PlayIjk", "HookUtils.app: ${HookUtils.app}, packageName: ${HookUtils.app?.packageName}")
        } catch (e: Exception) {
            // 忽略日志写入错误
        }
        
        xLog("请求开始播放，virtualSurface: $vSurface, ijkMediaPlayer: $ijkMP")
        if (vSurface == null) {
            xLog("播放失败，virtualSurface为空！")
            LogFileManager.writeToFile("PlayIjk", "播放失败，virtualSurface为空！")
            toast(HookUtils.app, "播放失败！", Toast.LENGTH_SHORT)
            return
        } else if (ijkMP == null) {
            xLog("播放失败，ijkMediaPlayer为空！")
            LogFileManager.writeToFile("PlayIjk", "播放失败，ijkMediaPlayer为空！")
            toast(HookUtils.app, "播放失败！", Toast.LENGTH_SHORT)
            return
        }
        val pkgName = HookUtils.app?.packageName ?: ""
        
        // 获取所有可能的publicDir路径（因为/sdcard在Xposed hook上下文中可能无法正确解析）
        val publicDirPaths = getPublicDirPaths()
        
        LogFileManager.writeToFile("PlayIjk", "=== PlayIjk.play 开始查找配置 ===")
        LogFileManager.writeToFile("PlayIjk", "包名='$pkgName', 尝试的publicDir路径: ${publicDirPaths.joinToString(", ")}")
        xLog("PlayIjk.play: 开始查找配置，包名='$pkgName', 尝试的publicDir路径: ${publicDirPaths.joinToString(", ")}")
        
        var urlStr = ""
        var foundPublicDir: String? = null
        
        // 由于Android 10+权限限制，无法直接写入其他应用的externalCacheDir
        // 所以优先检查 publicDir（实际保存的位置），然后检查 externalCacheDir（用户手动创建的情况）
        // 尝试所有可能的publicDir路径
        for (publicDir in publicDirPaths) {
            if (urlStr.isNotBlank()) break
            
            // 1. Check public specific config (实际保存的位置，优先检查)
            var filePath = "$publicDir$pkgName/stream.txt"
            LogFileManager.writeToFile("PlayIjk", "检查配置文件路径（publicDir特定应用）: $filePath")
            xLog("检查配置文件路径（publicDir特定应用）: $filePath")
            
            val configFile1 = File(filePath)
            val exists1 = configFile1.exists()
            val canRead1 = if (exists1) configFile1.canRead() else false
            val length1 = if (exists1) configFile1.length() else 0L
            LogFileManager.writeToFile("PlayIjk", "文件存在性检查: exists=$exists1, canRead=$canRead1, length=$length1")
            
            if (exists1 && canRead1 && length1 > 0) {
                urlStr = readConfig(filePath)
                LogFileManager.writeToFile("PlayIjk", "读取结果（特定应用）: urlStr='$urlStr', isBlank=${urlStr.isBlank()}, length=${urlStr.length}")
                xLog("读取结果（特定应用）: urlStr='$urlStr', isBlank=${urlStr.isBlank()}, length=${urlStr.length}")
                if (urlStr.isNotBlank()) {
                    foundPublicDir = publicDir
                    break
                }
            }
            
            // 2. Check public global config (全局配置)
            filePath = "${publicDir}stream.txt"
            LogFileManager.writeToFile("PlayIjk", "检查配置文件路径（publicDir全局）: $filePath")
            xLog("检查配置文件路径（publicDir全局）: $filePath")
            
            val configFile2 = File(filePath)
            val exists2 = configFile2.exists()
            val canRead2 = if (exists2) configFile2.canRead() else false
            val length2 = if (exists2) configFile2.length() else 0L
            LogFileManager.writeToFile("PlayIjk", "文件存在性检查: exists=$exists2, canRead=$canRead2, length=$length2")
            
            if (exists2 && canRead2 && length2 > 0) {
                urlStr = readConfig(filePath)
                LogFileManager.writeToFile("PlayIjk", "读取结果（全局）: urlStr='$urlStr', isBlank=${urlStr.isBlank()}, length=${urlStr.length}")
                xLog("读取结果（全局）: urlStr='$urlStr', isBlank=${urlStr.isBlank()}, length=${urlStr.length}")
                if (urlStr.isNotBlank()) {
                    foundPublicDir = publicDir
                    break
                }
            }
        }
        
        // 3. Check original cache config (符合readme.md文档的原始设计，用户手动创建的情况)
        if (urlStr.isBlank()) {
            val cacheDirPath = HookUtils.app?.externalCacheDir?.path?.toString()
            if (!cacheDirPath.isNullOrEmpty()) {
                val filePath = "$cacheDirPath/stream.txt"
                LogFileManager.writeToFile("PlayIjk", "检查配置文件路径（externalCacheDir，readme.md原始设计）: $filePath")
                xLog("检查配置文件路径（externalCacheDir，readme.md原始设计）: $filePath")
                
                val configFile3 = File(filePath)
                val exists3 = configFile3.exists()
                val canRead3 = if (exists3) configFile3.canRead() else false
                val length3 = if (exists3) configFile3.length() else 0L
                LogFileManager.writeToFile("PlayIjk", "文件存在性检查: exists=$exists3, canRead=$canRead3, length=$length3")
                
                if (exists3 && canRead3 && length3 > 0) {
                    urlStr = readConfig(filePath)
                    LogFileManager.writeToFile("PlayIjk", "读取结果（externalCacheDir）: urlStr='$urlStr', isBlank=${urlStr.isBlank()}, length=${urlStr.length}")
                    xLog("读取结果（externalCacheDir）: urlStr='$urlStr', isBlank=${urlStr.isBlank()}, length=${urlStr.length}")
                }
            }
        }
        
        // 额外检查：列出所有可能的配置文件
        val effectivePublicDir = foundPublicDir ?: publicDirPaths.firstOrNull()
        if (effectivePublicDir != null) {
            val publicDirExists = File(effectivePublicDir).exists()
            LogFileManager.writeToFile("PlayIjk", "检查目录内容 - publicDir='$effectivePublicDir', 存在=$publicDirExists")
            xLog("PlayIjk.play: 检查目录内容 - publicDir='$effectivePublicDir', 存在=$publicDirExists")
            if (publicDirExists) {
                val publicDirFile = File(effectivePublicDir)
                val files = publicDirFile.listFiles()
                val fileList = files?.map { it.name }?.joinToString(", ") ?: "null"
                LogFileManager.writeToFile("PlayIjk", "publicDir下的文件和目录: $fileList")
                xLog("PlayIjk.play: publicDir下的文件和目录: $fileList")
                
                // 检查特定应用目录
                val appConfigDir = File("$effectivePublicDir$pkgName")
                if (appConfigDir.exists()) {
                    val appFiles = appConfigDir.listFiles()
                    val appFileList = appFiles?.map { "${it.name}(${it.length()} bytes)" }?.joinToString(", ") ?: "null"
                    LogFileManager.writeToFile("PlayIjk", "应用配置目录 $pkgName 下的文件: $appFileList")
                    xLog("PlayIjk.play: 应用配置目录 $pkgName 下的文件: $appFileList")
                } else {
                    LogFileManager.writeToFile("PlayIjk", "应用配置目录 $pkgName 不存在")
                    xLog("PlayIjk.play: 应用配置目录 $pkgName 不存在")
                }
            }
        }

        if (urlStr.isBlank()) {
            // Check video files (按readme.md原始设计，优先检查externalCacheDir)
            // 1. Original cache video (符合readme.md文档的原始设计)
            var videoPath: String? = null
            val cacheDirPath = HookUtils.app?.externalCacheDir?.path?.toString()
            if (!cacheDirPath.isNullOrEmpty()) {
                val cacheVideoPath = "$cacheDirPath/virtual.mp4"
                LogFileManager.writeToFile("PlayIjk", "检查本地视频文件1（externalCacheDir）: $cacheVideoPath, 存在=${File(cacheVideoPath).exists()}")
                if (File(cacheVideoPath).exists()) {
                    videoPath = cacheVideoPath
                }
            }
            
            // 2. Public specific video (新增的路径，作为备用)
            if (videoPath == null) {
                val effectivePublicDir = foundPublicDir ?: publicDirPaths.firstOrNull()
                if (effectivePublicDir != null) {
                    val appVideoPath = "$effectivePublicDir$pkgName/virtual.mp4"
                    LogFileManager.writeToFile("PlayIjk", "检查本地视频文件2（publicDir特定应用）: $appVideoPath, 存在=${File(appVideoPath).exists()}")
                    if (File(appVideoPath).exists()) {
                        videoPath = appVideoPath
                    }
                }
            }
            
            // 3. Public global video (新增的路径，作为备用)
            if (videoPath == null) {
                for (publicDir in publicDirPaths) {
                    val globalVideoPath = "${publicDir}virtual.mp4"
                    LogFileManager.writeToFile("PlayIjk", "检查本地视频文件3（publicDir全局）: $globalVideoPath, 存在=${File(globalVideoPath).exists()}")
                    if (File(globalVideoPath).exists()) {
                        videoPath = globalVideoPath
                        break
                    }
                }
            }
            
            if (videoPath == null) {
                val errorMsg = "未找到视频源！请在 /storage/emulated/0/Android/data/$pkgName/cache/ 或 /sdcard/DCIM/XVirtualCamera/ 下配置 stream.txt 或 virtual.mp4"
                LogFileManager.writeToFile("PlayIjk", "错误: $errorMsg")
                val cachePath = HookUtils.app?.externalCacheDir?.path?.toString() ?: "未知"
                val checkedPaths = publicDirPaths.joinToString(", ") { "$it$pkgName/stream.txt, ${it}stream.txt" }
                LogFileManager.writeToFile("PlayIjk", "包名='$pkgName', 已检查的配置文件路径: $cachePath/stream.txt, $checkedPaths")
                toast(HookUtils.app, errorMsg, Toast.LENGTH_LONG)
                xLog("未找到视频源")
                return
            }
            
            urlStr = videoPath
            LogFileManager.writeToFile("PlayIjk", "播放本地视频：$urlStr")
            toast(HookUtils.app, "播放本地视频：$urlStr", Toast.LENGTH_LONG)
            xLog("播放本地视频：$urlStr")
        } else {
            // URL mode
            urlStr = urlStr.replace("https", "http")
            LogFileManager.writeToFile("PlayIjk", "播放网络流：$urlStr")
            toast(HookUtils.app, "播放网络流：$urlStr", Toast.LENGTH_LONG)
            xLog("播放网络流：$urlStr")
        }
        vSurface.let {
            ijkMP.setSurface(it)
            ijkMP.isLooping = true
            ijkMP.dataSource = urlStr
            ijkMP.prepareAsync()
            ijkMP.setOnPreparedListener {
                ijkMP.start()
            }
        }
        toast(HookUtils.app, "开始播放，ijk:$ijkMP，surface:$vSurface url:$urlStr", Toast.LENGTH_SHORT)
        xLog("开始播放，ijk:$ijkMP，surface:$vSurface url:$urlStr")
        xLog("currentActivity: ${HookUtils.getActivities()}, currentTopActivity: ${HookUtils.getTopActivity()}")
    }

    private fun readConfig(path: String): String {
        try {
            val file = File(path)
            val exists = file.exists()
            val canRead = if (exists) file.canRead() else false
            val length = if (exists) file.length() else 0L
            val absolutePath = file.absolutePath
            
            // 强制写入日志文件
            LogFileManager.writeToFile("PlayIjk.readConfig", "=== readConfig 开始 ===")
            LogFileManager.writeToFile("PlayIjk.readConfig", "检查文件: $path")
            LogFileManager.writeToFile("PlayIjk.readConfig", "绝对路径=$absolutePath, exists=$exists, canRead=$canRead, length=$length")
            
            xLog("readConfig: 检查文件 $path")
            xLog("readConfig: 绝对路径=$absolutePath, exists=$exists, canRead=$canRead, length=$length")
            
            if (!exists) {
                LogFileManager.writeToFile("PlayIjk.readConfig", "文件不存在: $absolutePath")
                xLog("readConfig: 文件不存在: $absolutePath")
                return ""
            }
            
            if (!canRead) {
                LogFileManager.writeToFile("PlayIjk.readConfig", "文件不可读: $absolutePath")
                xLog("readConfig: 文件不可读: $absolutePath")
                // 尝试使用其他方式读取
                try {
                    file.setReadable(true, false)
                    if (file.canRead()) {
                        LogFileManager.writeToFile("PlayIjk.readConfig", "设置权限后可以读取")
                    }
                } catch (e: Exception) {
                    LogFileManager.writeToFile("PlayIjk.readConfig", "设置权限失败: ${e.message}")
                }
                if (!file.canRead()) {
                    return ""
                }
            }
            
            if (length == 0L) {
                LogFileManager.writeToFile("PlayIjk.readConfig", "文件大小为0: $absolutePath")
                xLog("readConfig: 文件大小为0: $absolutePath")
                return ""
            }
            
            // 使用UTF-8编码读取，避免编码问题
            // 尝试多种读取方式
            var urlStr = ""
            try {
                // 方式1: 使用 BufferedReader
                val reader = BufferedReader(InputStreamReader(file.inputStream(), StandardCharsets.UTF_8))
                val lines = mutableListOf<String>()
                var line: String?
                var lineNumber = 0
                while (reader.readLine().also { line = it } != null) {
                    lineNumber++
                    val trimmed = line!!.trim().removePrefix("\uFEFF")
                    LogFileManager.writeToFile("PlayIjk.readConfig", "读取第${lineNumber}行: 原始='$line', 修剪后='$trimmed'")
                    xLog("readConfig: 读取第${lineNumber}行: 原始='$line', 修剪后='$trimmed'")
                    if (trimmed.isNotEmpty()) {
                        lines.add(trimmed)
                    }
                }
                reader.close()
                
                // 取第一行非空内容作为URL
                urlStr = lines.firstOrNull() ?: ""
                LogFileManager.writeToFile("PlayIjk.readConfig", "方式1读取结果: 行数=${lines.size}, URL='$urlStr', 长度=${urlStr.length}")
            } catch (e: Exception) {
                LogFileManager.writeToFile("PlayIjk.readConfig", "方式1读取失败: ${e.message}")
                // 方式2: 直接读取整个文件内容
                try {
                    val content = file.readText(Charsets.UTF_8).trim().removePrefix("\uFEFF")
                    urlStr = content.lines().firstOrNull { it.trim().isNotEmpty() }?.trim() ?: ""
                    LogFileManager.writeToFile("PlayIjk.readConfig", "方式2读取结果: URL='$urlStr', 长度=${urlStr.length}")
                } catch (e2: Exception) {
                    LogFileManager.writeToFile("PlayIjk.readConfig", "方式2读取也失败: ${e2.message}")
                    LogFileManager.writeException("PlayIjk.readConfig", e2)
                }
            }
            
            LogFileManager.writeToFile("PlayIjk.readConfig", "最终读取结果: URL='$urlStr', isBlank=${urlStr.isBlank()}, isEmpty=${urlStr.isEmpty()}, length=${urlStr.length}")
            xLog("readConfig: 最终读取结果: URL='$urlStr', 长度=${urlStr.length}")
            
            // 验证URL格式（简单检查是否包含协议）
            if (urlStr.isNotEmpty()) {
                val lowerUrl = urlStr.lowercase()
                // 检查是否是有效的URL格式（http://, https://, rtmp://, rtsp://等）
                if (lowerUrl.startsWith("http://") || lowerUrl.startsWith("https://") || 
                    lowerUrl.startsWith("rtmp://") || lowerUrl.startsWith("rtsp://") ||
                    lowerUrl.startsWith("rtp://") || lowerUrl.startsWith("udp://")) {
                    LogFileManager.writeToFile("PlayIjk.readConfig", "检测到有效的网络URL: $urlStr")
                    xLog("readConfig: 检测到有效的网络URL: $urlStr")
                    return urlStr
                } else {
                    LogFileManager.writeToFile("PlayIjk.readConfig", "URL格式可能无效，但返回尝试: $urlStr")
                    xLog("readConfig: URL格式可能无效，但返回尝试: $urlStr")
                    // 即使格式可能无效，也返回，让播放器尝试
                    return urlStr
                }
            } else {
                LogFileManager.writeToFile("PlayIjk.readConfig", "文件内容为空或只有空白字符，文件大小=$length")
                xLog("readConfig: 文件内容为空或只有空白字符，文件大小=$length")
            }
        } catch (e: Exception) {
            LogFileManager.writeException("PlayIjk.readConfig", e)
            xLog("readConfig: 读取文件异常 $path, 错误: ${e.message}")
            xLog("readConfig: 异常堆栈: ${e.stackTraceToString()}")
            e.printStackTrace()
        }
        return ""
    }
}
