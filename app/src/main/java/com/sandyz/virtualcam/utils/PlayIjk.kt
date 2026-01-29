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
        xLog("请求开始播放，virtualSurface: $vSurface, ijkMediaPlayer: $ijkMP")
        if (vSurface == null) {
            xLog("播放失败，virtualSurface为空！")
            toast(HookUtils.app, "播放失败！", Toast.LENGTH_SHORT)
            return
        } else if (ijkMP == null) {
            xLog("播放失败，ijkMediaPlayer为空！")
            toast(HookUtils.app, "播放失败！", Toast.LENGTH_SHORT)
            return
        }
        val pkgName = HookUtils.app?.packageName ?: ""
        val publicDir = "/sdcard/DCIM/XVirtualCamera/"
        
        // 1. Check public specific config
        var filePath = "$publicDir$pkgName/stream.txt"
        xLog("检查配置文件路径1: $filePath")
        var urlStr = readConfig(filePath)
        xLog("读取结果1: urlStr='$urlStr', isBlank=${urlStr.isBlank()}")
        
        // 2. Check public global config
        if (urlStr.isBlank()) {
            filePath = "${publicDir}stream.txt"
            xLog("检查配置文件路径2: $filePath")
            urlStr = readConfig(filePath)
            xLog("读取结果2: urlStr='$urlStr', isBlank=${urlStr.isBlank()}")
        }

        // 3. Fallback to original cache config
        if (urlStr.isBlank()) {
             filePath = HookUtils.app?.externalCacheDir?.path?.toString() + "/stream.txt"
             xLog("检查配置文件路径3: $filePath")
             urlStr = readConfig(filePath)
             xLog("读取结果3: urlStr='$urlStr', isBlank=${urlStr.isBlank()}")
        }

        if (urlStr.isBlank()) {
            // Check video files
            // 1. Public specific video
            var videoPath = "$publicDir$pkgName/virtual.mp4"
            if (!File(videoPath).exists()) {
                // 2. Public global video
                videoPath = "${publicDir}virtual.mp4"
                if (!File(videoPath).exists()) {
                    // 3. Original cache video
                    videoPath = HookUtils.app?.externalCacheDir?.path?.toString() + "/virtual.mp4"
                    if (!File(videoPath).exists()) {
                         toast(HookUtils.app, "未找到视频源！请在 /sdcard/DCIM/XVirtualCamera/ 下配置 stream.txt 或 virtual.mp4", Toast.LENGTH_LONG)
                         xLog("未找到视频源")
                         return
                    }
                }
            }
            urlStr = videoPath
            toast(HookUtils.app, "播放本地视频：$urlStr", Toast.LENGTH_LONG)
            xLog("播放本地视频：$urlStr")
        } else {
            // URL mode
            urlStr = urlStr.replace("https", "http")
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
            xLog("readConfig: 检查文件 $path, exists=${file.exists()}, canRead=${file.canRead()}, length=${if(file.exists()) file.length() else 0}")
            if (file.exists() && file.canRead() && file.length() > 0) {
                // 使用UTF-8编码读取，避免编码问题
                val reader = BufferedReader(InputStreamReader(file.inputStream(), StandardCharsets.UTF_8))
                // 读取所有行，然后合并（去除每行的空白）
                val lines = mutableListOf<String>()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim().removePrefix("\uFEFF")
                    if (trimmed.isNotEmpty()) {
                        lines.add(trimmed)
                    }
                }
                reader.close()
                
                // 取第一行非空内容作为URL
                val urlStr = lines.firstOrNull() ?: ""
                xLog("readConfig: 读取内容行数=${lines.size}, URL='$urlStr', 长度=${urlStr.length}")
                
                // 验证URL格式（简单检查是否包含协议）
                if (urlStr.isNotEmpty()) {
                    val lowerUrl = urlStr.lowercase()
                    // 检查是否是有效的URL格式（http://, https://, rtmp://, rtsp://等）
                    if (lowerUrl.startsWith("http://") || lowerUrl.startsWith("https://") || 
                        lowerUrl.startsWith("rtmp://") || lowerUrl.startsWith("rtsp://") ||
                        lowerUrl.startsWith("rtp://") || lowerUrl.startsWith("udp://")) {
                        xLog("readConfig: 检测到有效的网络URL")
                        return urlStr
                    } else {
                        xLog("readConfig: URL格式可能无效: $urlStr")
                        // 即使格式可能无效，也返回，让播放器尝试
                        return urlStr
                    }
                } else {
                    xLog("readConfig: 文件内容为空或只有空白字符")
                }
            } else {
                xLog("readConfig: 文件不存在、不可读或文件大小为0 $path")
            }
        } catch (e: Exception) {
            xLog("readConfig: 读取文件异常 $path, 错误: ${e.message}")
            e.printStackTrace()
        }
        return ""
    }
}
