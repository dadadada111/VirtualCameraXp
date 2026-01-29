package com.sandyz.virtualcam.utils

import android.view.Surface
import android.widget.Toast
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

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
        var urlStr = readConfig(filePath)
        
        // 2. Check public global config
        if (urlStr.isBlank()) {
            filePath = "${publicDir}stream.txt"
            urlStr = readConfig(filePath)
        }

        // 3. Fallback to original cache config
        if (urlStr.isBlank()) {
             filePath = HookUtils.app?.externalCacheDir?.path?.toString() + "/stream.txt"
             urlStr = readConfig(filePath)
        }

        if (urlStr.isBlank()) {
            // Check video files
            // 1. Public specific video
            urlStr = "$publicDir$pkgName/virtual.mp4"
            if (!File(urlStr).exists()) {
                // 2. Public global video
                urlStr = "${publicDir}virtual.mp4"
                if (!File(urlStr).exists()) {
                    // 3. Original cache video
                    urlStr = HookUtils.app?.externalCacheDir?.path?.toString() + "/virtual.mp4"
                    if (!File(urlStr).exists()) {
                         toast(HookUtils.app, "未找到视频源！请在 /sdcard/DCIM/XVirtualCamera/ 下配置 stream.txt 或 virtual.mp4", Toast.LENGTH_LONG)
                         xLog("未找到视频源")
                         return
                    }
                }
            }
            
            toast(HookUtils.app, "播放本地视频：$urlStr", Toast.LENGTH_LONG)
            xLog("播放本地视频：$urlStr")
        } else {
            urlStr = urlStr.replace("https", "http")
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
            if (file.exists()) {
                val reader = BufferedReader(FileReader(file))
                val content = reader.readLine() ?: ""
                reader.close()
                return content.trim()
            }
        } catch (e: Exception) {
            // ignore
        }
        return ""
    }
}
