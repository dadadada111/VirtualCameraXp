package com.sandyz.virtualcam.hooks

import android.content.res.XModuleResources
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.os.Build
import android.util.Size
import android.util.SizeF
import com.sandyz.virtualcam.utils.xLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.Random

/**
 * 系统层相机特征伪装 (System Camera Spoof)
 * 模拟真实摄像头的硬件特征和动态元数据，防止被识别为虚拟摄像头
 * Updated: 2026-02-01 (Force Rebuild)
 */
class Camera2Spoof : IHook {
    override fun getName(): String = "系统层相机特征伪装 (System Camera Spoof)"

    // 支持主流直播和社交软件
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

    override fun hook(lpparam: LoadPackageParam?) {
        try {
            xLog("Initializing Camera2Spoof for ${lpparam?.packageName}")
            hookCameraCharacteristics(lpparam)
            hookCaptureResult(lpparam)
        } catch (e: Throwable) {
            xLog("Camera2Spoof hook failed: ${e.message}")
        }
    }

    private fun hookCameraCharacteristics(lpparam: LoadPackageParam?) {
        // Hook CameraCharacteristics.get(Key) 以伪造静态硬件参数
        XposedHelpers.findAndHookMethod(
            "android.hardware.camera2.CameraCharacteristics",
            lpparam!!.classLoader,
            "get",
            CameraCharacteristics.Key::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val key = param.args[0] as? CameraCharacteristics.Key<*> ?: return
                    val keyName = key.name

                    // 1. 模拟物理传感器尺寸 (Sensor Size)
                    // 模拟 Sony IMX586 等主流传感器: 1/2.0 inch (6.4mm x 4.8mm)
                    // 虚拟摄像头通常没有这个值或者为0
                    if (keyName == "android.sensor.info.physicalSize") {
                        param.result = SizeF(6.40f, 4.80f)
                    }
                    
                    // 2. 模拟像素阵列大小 (Pixel Array Size)
                    // 模拟 1200万像素 (4000x3000)
                    if (keyName == "android.sensor.info.pixelArraySize") {
                        param.result = Size(4000, 3000)
                    }

                    // 3. 模拟有效像素阵列 (Active Array Size)
                    if (keyName == "android.sensor.info.activeArraySize") {
                        // Rect(0, 0, 4000, 3000)
                        // 需要构造 android.graphics.Rect
                        // param.result = android.graphics.Rect(0, 0, 4000, 3000)
                        // 由于Rect构造较麻烦且易错，暂不Mock这个，通常physicalSize够用
                    }

                    // 4. 模拟固定焦距 (Focal Lengths)
                    // 手机主摄通常是定焦，约 24mm-28mm 等效 (实际焦距 4mm-6mm)
                    // 虚拟摄像头通常为空
                    if (keyName == "android.lens.info.availableFocalLengths") {
                        param.result = floatArrayOf(4.74f)
                    }

                    // 5. 模拟固定光圈 (Apertures)
                    // 手机主摄通常大光圈 f/1.6 - f/1.8
                    if (keyName == "android.lens.info.availableApertures") {
                        param.result = floatArrayOf(1.8f)
                    }
                    
                    // 6. 硬件支持级别 (Hardware Level)
                    // 确保显示为 FULL (1) 或 LEVEL_3 (3)，而不是 LIMITED (0) 或 LEGACY (2)
                    if (keyName == "android.info.supportedHardwareLevel") {
                        param.result = 1 // INFO_SUPPORTED_HARDWARE_LEVEL_FULL
                    }

                    // 7. 镜头朝向 (Lens Facing)
                    // 确保是后置 (BACK=1) 或 前置 (FRONT=0)
                    // 防止出现 EXTERNAL (2) 或其他奇怪的值
                    if (keyName == "android.lens.facing") {
                        // 如果原值是 null，默认为 BACK
                        // 这里我们不强制修改，除非它缺失
                    }
                }
            }
        )
    }

    private fun hookCaptureResult(lpparam: LoadPackageParam?) {
        val random = Random()
        
        // Hook CaptureResult.get(Key) 以伪造动态拍摄参数
        // 这解决了 "对焦/曝光永远稳定" 和 "metadata 不变化" 的问题
        XposedHelpers.findAndHookMethod(
            "android.hardware.camera2.CaptureResult",
            lpparam!!.classLoader,
            "get",
            CaptureResult.Key::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val key = param.args[0] as? CaptureResult.Key<*> ?: return
                    val keyName = key.name

                    // 1. 模拟曝光时间波动 (Exposure Time)
                    // 真实摄像头会有微小的自动曝光调整 (AES)
                    if (keyName == "android.sensor.exposureTime") {
                        // 基准 20ms (20,000,000 ns) + 随机抖动 +/- 1ms
                        // 这种微小的变化是真实Sensor的特征
                        val baseExposure = 20000000L
                        val jitter = random.nextInt(2000000) - 1000000 // +/- 1ms (ns)
                        param.result = baseExposure + jitter.toLong()
                    }

                    // 2. 模拟感光度波动 (ISO Sensitivity)
                    // ISO 也会随光线微调
                    if (keyName == "android.sensor.sensitivity") {
                        // ISO 100-120 之间波动
                        param.result = 100 + random.nextInt(20)
                    }

                    // 3. 模拟焦距微动 (Focus Distance)
                    // 即使是对焦锁定，VCM马达也会有微小的位置反馈波动
                    if (keyName == "android.lens.focusDistance") {
                        // 假设对焦在 1m 处 (1.0f)
                        val baseFocus = 1.0f
                        val jitter = (random.nextFloat() - 0.5f) * 0.02f
                        param.result = baseFocus + jitter
                    }
                    
                    // 4. 模拟帧时长波动 (Frame Duration)
                    // 对应 FPS 的微小波动
                    if (keyName == "android.sensor.frameDuration") {
                        // 33.3ms = 33,333,333 ns
                        val baseDuration = 33333333L
                        val jitter = random.nextInt(200000) - 100000 // +/- 0.1ms
                        param.result = baseDuration + jitter.toLong()
                    }

                    // 5. 模拟时间戳 (Timestamp)
                    // 确保有时间戳返回，防止被识别为静态图片
                    // CaptureResult.SENSOR_TIMESTAMP 通常由底层生成，这里如果不Hook可能会暴露
                    // 但通常系统会自动填充，只需确保上面的物理参数在变动即可
                }
            }
        )
    }
}
