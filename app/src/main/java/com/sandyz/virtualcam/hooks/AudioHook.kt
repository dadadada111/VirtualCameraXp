package com.sandyz.virtualcam.hooks

import android.content.res.XModuleResources
import android.media.AudioRecord
import com.sandyz.virtualcam.utils.xLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.File
import java.nio.ByteBuffer

class AudioHook : IHook {

    override fun getName(): String = "麦克风音量控制 (Microphone Volume Control)"

    override fun getSupportedPackages() = listOf(
        "com.ss.android.ugc.aweme",         // 抖音
        "com.ss.android.ugc.aweme.lite",    // 抖音极速版
        "tv.danmaku.bili",                  // 哔哩哔哩
        "com.smile.gifmaker",               // 快手
        "com.kuaishou.nebula",              // 快手极速版
        "com.taobao.live",                  // 淘宝直播
        "com.xingin.xhs",                   // 小红书
        "com.tencent.mm",                   // 微信
        "com.zhiliaoapp.musically",         // TikTok
        "com.instagram.android"             // Instagram
    )

    override fun init(cl: ClassLoader?) {}

    override fun registerRes(moduleRes: XModuleResources?) {}

    // Volatile to ensure visibility across threads
    @Volatile
    private var volume: Float = 1.0f
    private var lastCheckTime: Long = 0
    private val CHECK_INTERVAL_MS = 2000L // Check config every 2 seconds
    private val configPath = "/sdcard/DCIM/XVirtualCamera/mic_volume.txt"
    private var hasLoggedFirstPacket = false
    private var lastErrorLogTime: Long = 0

    private fun updateVolumeConfig() {
        val now = System.currentTimeMillis()
        if (now - lastCheckTime > CHECK_INTERVAL_MS) {
            lastCheckTime = now
            try {
                // Try reading from multiple possible locations
                // 1. Specific app path (if we can read it)
                var file = File(configPath)
                if (!file.exists() || !file.canRead()) {
                    // 2. Try global legacy path (sometimes works)
                    file = File("/sdcard/DCIM/XVirtualCamera/mic_volume.txt")
                }
                
                if (file.exists() && file.canRead()) {
                    val content = file.readText().trim()
                    if (content.isNotEmpty()) {
                        val newVolume = content.toFloatOrNull()
                        if (newVolume != null) {
                            if (volume != newVolume) {
                                xLog("Microphone volume updated: $newVolume")
                            }
                            volume = newVolume.coerceIn(0.0f, 10.0f)
                        }
                    }
                } else {
                    if (now - lastErrorLogTime > 60000) { // Log error at most once per minute
                        xLog("Cannot read volume config from $configPath or legacy path. Permissions issue? Volume remains $volume")
                        lastErrorLogTime = now
                    }
                }
            } catch (e: Exception) {
                if (now - lastErrorLogTime > 60000) {
                    xLog("Error reading volume config: ${e.message}")
                    lastErrorLogTime = now
                }
            }
        }
    }

    private fun processAudioBuffer(buffer: ByteBuffer, readSize: Int) {
        if (readSize <= 0) return
        
        if (!hasLoggedFirstPacket) {
            xLog("AudioHook: First audio buffer packet received. Size=$readSize, Volume=$volume")
            hasLoggedFirstPacket = true
        }

        if (volume == 1.0f) return
        
        // AudioRecord updates position. We need to process the data just written.
        // Assuming position is at the end of written data.
        val currentPos = buffer.position()
        val startPos = currentPos - readSize
        
        if (startPos < 0) return

        if (volume == 0.0f) {
             for (i in startPos until currentPos) {
                 buffer.put(i, 0)
             }
             return
        }

        for (i in startPos until currentPos step 2) {
            if (i + 1 < currentPos) {
                val low = buffer.get(i).toInt() and 0xFF
                val high = buffer.get(i + 1).toInt().toShort()
                var sample = (high.toInt() shl 8) or low
                
                sample = (sample * volume).toInt()
                
                if (sample > 32767) sample = 32767
                if (sample < -32768) sample = -32768
                
                buffer.put(i, (sample and 0xFF).toByte())
                buffer.put(i + 1, ((sample shr 8) and 0xFF).toByte())
            }
        }
    }

    override fun hook(lpparam: LoadPackageParam?) {
        xLog("Initializing AudioHook for ${lpparam?.packageName}")

        try {
            // Hook Constructor to detect if AudioRecord is used at all
            XposedBridge.hookAllConstructors(AudioRecord::class.java, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    xLog("AudioHook: AudioRecord instance created. Source=${param.args.getOrNull(0)}")
                }
            })

            // Hook startRecording to see when recording begins
            XposedHelpers.findAndHookMethod(AudioRecord::class.java, "startRecording", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    xLog("AudioHook: startRecording() called")
                }
            })

            // Hook AudioRecord.read(byte[], int, int)
            XposedHelpers.findAndHookMethod(
                AudioRecord::class.java,
                "read",
                ByteArray::class.java,
                Int::class.java,
                Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        updateVolumeConfig()
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val result = param.result as Int
                        if (result > 0) {
                            val data = param.args[0] as ByteArray
                            val offset = param.args[1] as Int
                            processAudioDataWithOffset(data, offset, result)
                        }
                    }
                }
            )
            
            // Hook AudioRecord.read(byte[], int, int, int)
             XposedHelpers.findAndHookMethod(
                AudioRecord::class.java,
                "read",
                ByteArray::class.java,
                Int::class.java,
                Int::class.java,
                Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        updateVolumeConfig()
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val result = param.result as Int
                        if (result > 0) {
                            val data = param.args[0] as ByteArray
                            val offset = param.args[1] as Int
                            processAudioDataWithOffset(data, offset, result)
                        }
                    }
                }
            )

            // Hook AudioRecord.read(ByteBuffer, int)
            XposedHelpers.findAndHookMethod(
                AudioRecord::class.java,
                "read",
                ByteBuffer::class.java,
                Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        updateVolumeConfig()
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val result = param.result as Int
                        if (result > 0) {
                            val buffer = param.args[0] as ByteBuffer
                            processAudioBuffer(buffer, result)
                        }
                    }
                }
            )
            
             // Hook AudioRecord.read(ByteBuffer, int, int)
            XposedHelpers.findAndHookMethod(
                AudioRecord::class.java,
                "read",
                ByteBuffer::class.java,
                Int::class.java,
                Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        updateVolumeConfig()
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val result = param.result as Int
                        if (result > 0) {
                            val buffer = param.args[0] as ByteBuffer
                            processAudioBuffer(buffer, result)
                        }
                    }
                }
            )

        } catch (e: Throwable) {
            xLog("AudioHook failed: ${e.message}")
        }
    }
    
    private fun processAudioDataWithOffset(data: ByteArray, offset: Int, readSize: Int) {
         if (!hasLoggedFirstPacket) {
             xLog("AudioHook: First audio array packet received. Size=$readSize, Volume=$volume")
             hasLoggedFirstPacket = true
         }
         
         if (volume == 1.0f) return

        if (volume == 0.0f) {
            // Mute
             for (i in offset until (offset + readSize)) {
                 data[i] = 0
             }
            return
        }

        // PCM 16-bit
        // We assume 16-bit. If 8-bit (ENCODING_PCM_8BIT), handling is different.
        // But 16-bit is 99% of cases.
        // How to know encoding? 
        // We can get it from 'thisObject' (AudioRecord instance) -> getAudioFormat()
        // But calling getAudioFormat() every time is slow.
        // For now, assume 16-bit which is standard for mic.
        
        // Ensure we don't go out of bounds
        val end = offset + readSize
        // 2 bytes step
        for (i in offset until end step 2) {
            if (i + 1 < end) {
                val low = data[i].toInt() and 0xFF
                val high = data[i + 1].toInt().toShort()
                var sample = (high.toInt() shl 8) or low
                
                sample = (sample * volume).toInt()
                
                if (sample > 32767) sample = 32767
                if (sample < -32768) sample = -32768
                
                data[i] = (sample and 0xFF).toByte()
                data[i + 1] = ((sample shr 8) and 0xFF).toByte()
            }
        }
    }
}
