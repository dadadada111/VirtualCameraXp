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
        val publicDir = "/sdcard/DCIM/XVirtualCamera/"
        
        LogFileManager.writeToFile("PlayIjk", "开始查找配置，包名='$pkgName', publicDir='$publicDir'")
        xLog("PlayIjk.play: 开始查找配置，包名='$pkgName', publicDir='$publicDir'")
        
        // 1. Check public specific config
        var filePath = "$publicDir$pkgName/stream.txt"
        LogFileManager.writeToFile("PlayIjk", "=== 开始检查配置 ===")
        LogFileManager.writeToFile("PlayIjk", "包名: $pkgName")
        LogFileManager.writeToFile("PlayIjk", "publicDir: $publicDir")
        LogFileManager.writeToFile("PlayIjk", "检查配置文件路径1: $filePath")
        xLog("检查配置文件路径1: $filePath")
        
        // 先检查文件是否存在
        val configFile = File(filePath)
        LogFileManager.writeToFile("PlayIjk", "文件存在性检查: exists=${configFile.exists()}, canRead=${configFile.canRead()}, length=${if(configFile.exists()) configFile.length() else 0}")
        
        var urlStr = readConfig(filePath)
        LogFileManager.writeToFile("PlayIjk", "读取结果1: urlStr='$urlStr', isBlank=${urlStr.isBlank()}, isEmpty=${urlStr.isEmpty()}, length=${urlStr.length}")
        xLog("读取结果1: urlStr='$urlStr', isBlank=${urlStr.isBlank()}, length=${urlStr.length}")
        
        // 2. Check public global config
        if (urlStr.isBlank()) {
            filePath = "${publicDir}stream.txt"
            LogFileManager.writeToFile("PlayIjk", "检查配置文件路径2: $filePath")
            xLog("检查配置文件路径2: $filePath")
            urlStr = readConfig(filePath)
            LogFileManager.writeToFile("PlayIjk", "读取结果2: urlStr='$urlStr', isBlank=${urlStr.isBlank()}, length=${urlStr.length}")
            xLog("读取结果2: urlStr='$urlStr', isBlank=${urlStr.isBlank()}, length=${urlStr.length}")
        }

        // 3. Fallback to original cache config
        if (urlStr.isBlank()) {
             filePath = HookUtils.app?.externalCacheDir?.path?.toString() + "/stream.txt"
             LogFileManager.writeToFile("PlayIjk", "检查配置文件路径3: $filePath")
             xLog("检查配置文件路径3: $filePath")
             urlStr = readConfig(filePath)
             LogFileManager.writeToFile("PlayIjk", "读取结果3: urlStr='$urlStr', isBlank=${urlStr.isBlank()}, length=${urlStr.length}")
             xLog("读取结果3: urlStr='$urlStr', isBlank=${urlStr.isBlank()}, length=${urlStr.length}")
        }
        
        // 额外检查：列出所有可能的配置文件
        val publicDirExists = File(publicDir).exists()
        LogFileManager.writeToFile("PlayIjk", "检查目录内容 - publicDir存在=$publicDirExists")
        xLog("PlayIjk.play: 检查目录内容 - publicDir存在=$publicDirExists")
        if (publicDirExists) {
            val publicDirFile = File(publicDir)
            val files = publicDirFile.listFiles()
            val fileList = files?.map { it.name }?.joinToString(", ") ?: "null"
            LogFileManager.writeToFile("PlayIjk", "publicDir下的文件和目录: $fileList")
            xLog("PlayIjk.play: publicDir下的文件和目录: $fileList")
            
            // 检查特定应用目录
            val appConfigDir = File("$publicDir$pkgName")
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

        if (urlStr.isBlank()) {
            // Check video files
            // 1. Public specific video
            var videoPath = "$publicDir$pkgName/virtual.mp4"
            LogFileManager.writeToFile("PlayIjk", "检查本地视频文件1: $videoPath, 存在=${File(videoPath).exists()}")
            if (!File(videoPath).exists()) {
                // 2. Public global video
                videoPath = "${publicDir}virtual.mp4"
                LogFileManager.writeToFile("PlayIjk", "检查本地视频文件2: $videoPath, 存在=${File(videoPath).exists()}")
                if (!File(videoPath).exists()) {
                    // 3. Original cache video
                    videoPath = HookUtils.app?.externalCacheDir?.path?.toString() + "/virtual.mp4"
                    LogFileManager.writeToFile("PlayIjk", "检查本地视频文件3: $videoPath, 存在=${File(videoPath).exists()}")
                    if (!File(videoPath).exists()) {
                         val errorMsg = "未找到视频源！请在 /sdcard/DCIM/XVirtualCamera/ 下配置 stream.txt 或 virtual.mp4"
                         LogFileManager.writeToFile("PlayIjk", "错误: $errorMsg")
                         LogFileManager.writeToFile("PlayIjk", "包名='$pkgName', 已检查的配置文件路径: $publicDir$pkgName/stream.txt, ${publicDir}stream.txt")
                         toast(HookUtils.app, errorMsg, Toast.LENGTH_LONG)
                         xLog("未找到视频源")
                         return
                    }
                }
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
            LogFileManager.writeToFile("PlayIjk.readConfig", "检查文件: $path")
            LogFileManager.writeToFile("PlayIjk.readConfig", "绝对路径=$absolutePath, exists=$exists, canRead=$canRead, length=$length")
            
            xLog("readConfig: 检查文件 $path")
            xLog("readConfig: 绝对路径=$absolutePath, exists=$exists, canRead=$canRead, length=$length")
            
            if (exists && canRead && length > 0) {
                // 使用UTF-8编码读取，避免编码问题
                val reader = BufferedReader(InputStreamReader(file.inputStream(), StandardCharsets.UTF_8))
                // 读取所有行，然后合并（去除每行的空白）
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
                val urlStr = lines.firstOrNull() ?: ""
                LogFileManager.writeToFile("PlayIjk.readConfig", "读取内容行数=${lines.size}, 有效行数=${lines.size}, URL='$urlStr', 长度=${urlStr.length}")
                xLog("readConfig: 读取内容行数=${lines.size}, 有效行数=${lines.size}, URL='$urlStr', 长度=${urlStr.length}")
                
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
            } else {
                val reason = when {
                    !exists -> "文件不存在"
                    !canRead -> "文件不可读"
                    length == 0L -> "文件大小为0"
                    else -> "未知原因"
                }
                LogFileManager.writeToFile("PlayIjk.readConfig", "$reason: $absolutePath")
                if (!exists) {
                    xLog("readConfig: 文件不存在: $absolutePath")
                } else if (!canRead) {
                    xLog("readConfig: 文件不可读: $absolutePath")
                } else if (length == 0L) {
                    xLog("readConfig: 文件大小为0: $absolutePath")
                }
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
