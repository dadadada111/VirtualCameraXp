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

    private fun updateVolumeConfig() {
        val now = System.currentTimeMillis()
        if (now - lastCheckTime > CHECK_INTERVAL_MS) {
            lastCheckTime = now
            try {
                val file = File(configPath)
                if (file.exists() && file.canRead()) {
                    val content = file.readText().trim()
                    if (content.isNotEmpty()) {
                        val newVolume = content.toFloatOrNull()
                        if (newVolume != null) {
                            if (volume != newVolume) {
                                xLog("Microphone volume updated: $newVolume")
                            }
                            volume = newVolume.coerceIn(0.0f, 10.0f) // Allow up to 10x gain? strict 0-1 for now based on request "not record external"
                        }
                    }
                } else {
                     // Default to 1.0 if no config found, or keep last valid?
                     // Keep last valid is safer to avoid sudden jumps if file is busy
                }
            } catch (e: Exception) {
                // Ignore read errors
            }
        }
    }

    private fun processAudioData(data: ByteArray, readSize: Int) {
        if (readSize <= 0) return
        
        // Optimize: skip processing if volume is 1.0
        if (volume == 1.0f) return

        if (volume == 0.0f) {
            // Fast path for mute
            java.util.Arrays.fill(data, 0, readSize, 0.toByte())
            return
        }

        // PCM 16-bit processing
        // Assuming AudioFormat.ENCODING_PCM_16BIT which is standard
        // We iterate 2 bytes at a time
        for (i in 0 until readSize step 2) {
            if (i + 1 < readSize) {
                // Little Endian
                val low = data[i].toInt() and 0xFF
                val high = data[i + 1].toInt().toShort()
                var sample = (high.toInt() shl 8) or low
                
                // Apply volume
                sample = (sample * volume).toInt()
                
                // Clamp
                if (sample > 32767) sample = 32767
                if (sample < -32768) sample = -32768
                
                // Write back
                data[i] = (sample and 0xFF).toByte()
                data[i + 1] = ((sample shr 8) and 0xFF).toByte()
            }
        }
    }
    
    private fun processAudioBuffer(buffer: ByteBuffer, readSize: Int) {
        if (readSize <= 0) return
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
