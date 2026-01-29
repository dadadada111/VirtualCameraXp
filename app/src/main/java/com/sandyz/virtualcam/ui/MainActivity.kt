package com.sandyz.virtualcam.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.sandyz.virtualcam.R
import com.sandyz.virtualcam.utils.LogFileManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var etGlobalUrl: EditText
    private lateinit var tvGlobalVideoPath: TextView
    private lateinit var etAppPackage: EditText
    private lateinit var etAppUrl: EditText
    private lateinit var tvAppVideoPath: TextView
    private lateinit var tvStatus: TextView

    private var selectedGlobalUri: Uri? = null
    private var selectedAppUri: Uri? = null

    private val publicDir = "/sdcard/DCIM/XVirtualCamera/"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            updateStatus("权限已获取")
        } else {
            updateStatus("权限被拒绝，无法保存配置")
        }
    }

    private val pickGlobalVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedGlobalUri = uri
            tvGlobalVideoPath.text = uri.path
            etGlobalUrl.setText("") // Clear URL if file selected
        }
    }

    private val pickAppVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedAppUri = uri
            tvAppVideoPath.text = uri.path
            etAppUrl.setText("") // Clear URL if file selected
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        Log.d(TAG, "MainActivity onCreate")
        LogFileManager.writeToFile(TAG, "MainActivity onCreate")

        initViews()
        checkPermissions()
        createPublicDir()
        loadConfigs()
    }

    private fun initViews() {
        etGlobalUrl = findViewById(R.id.et_global_url)
        tvGlobalVideoPath = findViewById(R.id.tv_global_video_path)
        etAppPackage = findViewById(R.id.et_app_package)
        etAppUrl = findViewById(R.id.et_app_url)
        tvAppVideoPath = findViewById(R.id.tv_app_video_path)
        tvStatus = findViewById(R.id.tv_status)

        findViewById<Button>(R.id.btn_global_select_video).setOnClickListener {
            pickGlobalVideoLauncher.launch("video/*")
        }

        findViewById<Button>(R.id.btn_app_select_video).setOnClickListener {
            pickAppVideoLauncher.launch("video/*")
        }

        findViewById<Button>(R.id.btn_global_save).setOnClickListener {
            saveGlobalConfig()
        }

        findViewById<Button>(R.id.btn_global_clear).setOnClickListener {
            clearConfig("")
        }

        findViewById<Button>(R.id.btn_app_save).setOnClickListener {
            saveAppConfig()
        }

        findViewById<Button>(R.id.btn_app_clear).setOnClickListener {
            val pkg = etAppPackage.text.toString().trim()
            if (pkg.isEmpty()) {
                updateStatus("请输入包名")
            } else {
                clearConfig(pkg)
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun createPublicDir() {
        val dir = File(publicDir)
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    private fun saveGlobalConfig() {
        val url = etGlobalUrl.text.toString().trim()
        Log.d(TAG, "saveGlobalConfig: url='$url', selectedGlobalUri=$selectedGlobalUri")
        LogFileManager.writeToFile(TAG, "saveGlobalConfig: url='$url', selectedGlobalUri=$selectedGlobalUri")
        
        if (url.isNotEmpty()) {
            saveConfig("", "stream.txt", url)
            // Delete virtual.mp4 to avoid conflict if exists
            File(publicDir + "virtual.mp4").delete()
            // Clear video selection
            tvGlobalVideoPath.text = "未选择"
            selectedGlobalUri = null
        } else if (selectedGlobalUri != null) {
            copyVideo(selectedGlobalUri!!, "", "virtual.mp4")
            // Delete stream.txt to avoid conflict
            File(publicDir + "stream.txt").delete()
            // Clear URL
            etGlobalUrl.setText("")
        } else {
            updateStatus("全局配置为空，未保存")
        }
    }

    private fun saveAppConfig() {
        val pkg = etAppPackage.text.toString().trim()
        if (pkg.isEmpty()) {
            updateStatus("请输入包名")
            return
        }
        
        val appDir = File(publicDir + pkg)
        if (!appDir.exists()) appDir.mkdirs()

        val url = etAppUrl.text.toString().trim()
        Log.d(TAG, "saveAppConfig: pkg='$pkg', url='$url', selectedAppUri=$selectedAppUri")
        LogFileManager.writeToFile(TAG, "saveAppConfig: pkg='$pkg', url='$url', selectedAppUri=$selectedAppUri")
        
        if (url.isNotEmpty()) {
            saveConfig(pkg, "stream.txt", url)
            File(appDir, "virtual.mp4").delete()
            // Clear video selection
            tvAppVideoPath.text = "未选择"
            selectedAppUri = null
        } else if (selectedAppUri != null) {
            copyVideo(selectedAppUri!!, pkg, "virtual.mp4")
            File(appDir, "stream.txt").delete()
            // Clear URL
            etAppUrl.setText("")
        } else {
            updateStatus("应用配置为空，未保存")
        }
    }

    private fun saveConfig(pkg: String, fileName: String, content: String) {
        try {
            val dirPath = if (pkg.isEmpty()) publicDir else "$publicDir$pkg/"
            val dir = File(dirPath)
            if (!dir.exists()) {
                val created = dir.mkdirs()
                Log.d(TAG, "saveConfig: 创建目录 $dirPath, 结果=$created")
                LogFileManager.writeToFile(TAG, "saveConfig: 创建目录 $dirPath, 结果=$created")
            }
            val file = File(dirPath + fileName)
            
            // 使用UTF-8编码保存，确保与读取一致
            // 确保内容末尾没有换行符
            val cleanContent = content.trim()
            val contentBytes = cleanContent.toByteArray(Charsets.UTF_8)
            val fos = FileOutputStream(file)
            fos.write(contentBytes)
            fos.flush()
            fos.close()
            
            // 设置文件权限，确保可读
            try {
                file.setReadable(true, false) // 对所有用户可读
                file.setWritable(true, false)  // 对所有用户可写
            } catch (e: Exception) {
                Log.w(TAG, "saveConfig: 设置文件权限失败: ${e.message}")
            }
            
            // 验证文件是否保存成功
            if (file.exists() && file.length() > 0) {
                // 读取验证
                val reader = BufferedReader(InputStreamReader(file.inputStream(), StandardCharsets.UTF_8))
                val savedContent = reader.readLine()?.trim() ?: ""
                reader.close()
                
                Log.d(TAG, "saveConfig: 保存成功 - 文件路径=${file.absolutePath}, 文件大小=${file.length()}, 保存内容='$content', 读取验证='$savedContent'")
                LogFileManager.writeToFile(TAG, "saveConfig: 保存成功 - 文件路径=${file.absolutePath}, 文件大小=${file.length()}, 保存内容='$content', 读取验证='$savedContent'")
                
                if (savedContent == content) {
                    updateStatus("保存成功: ${file.absolutePath}")
                } else {
                    Log.e(TAG, "saveConfig: 保存验证失败 - 保存内容与读取内容不一致")
                    LogFileManager.writeToFile(TAG, "saveConfig: 保存验证失败 - 保存内容='$content', 读取内容='$savedContent'")
                    updateStatus("保存成功但验证失败: ${file.absolutePath}")
                }
            } else {
                Log.e(TAG, "saveConfig: 文件保存失败 - 文件不存在或大小为0")
                LogFileManager.writeToFile(TAG, "saveConfig: 文件保存失败 - 文件不存在或大小为0")
                updateStatus("保存失败: 文件未创建")
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveConfig: 异常", e)
            LogFileManager.writeException(TAG, e)
            e.printStackTrace()
            updateStatus("保存失败: ${e.message}")
        }
    }

    private fun copyVideo(uri: Uri, pkg: String, fileName: String) {
        try {
            val dirPath = if (pkg.isEmpty()) publicDir else "$publicDir$pkg/"
            val outFile = File(dirPath + fileName)
            
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                updateStatus("无法读取源文件")
                return
            }
            
            val fos = FileOutputStream(outFile)
            val buffer = ByteArray(1024)
            var length: Int
            while (inputStream.read(buffer).also { length = it } > 0) {
                fos.write(buffer, 0, length)
            }
            fos.close()
            inputStream.close()
            
            updateStatus("视频保存成功: ${outFile.absolutePath}")
        } catch (e: Exception) {
            e.printStackTrace()
            updateStatus("视频保存失败: ${e.message}")
        }
    }

    private fun clearConfig(pkg: String) {
        try {
            val dirPath = if (pkg.isEmpty()) publicDir else "$publicDir$pkg/"
            File(dirPath + "stream.txt").delete()
            File(dirPath + "virtual.mp4").delete()
            updateStatus("配置已清除: $dirPath")
        } catch (e: Exception) {
            updateStatus("清除失败: ${e.message}")
        }
    }

    private fun loadConfigs() {
        // Load global config
        loadGlobalConfig()
        // Load app configs (we'll load the last saved one if any)
        loadLastAppConfig()
    }

    private fun loadGlobalConfig() {
        try {
            Log.d(TAG, "loadGlobalConfig: 开始加载全局配置")
            LogFileManager.writeToFile(TAG, "loadGlobalConfig: 开始加载全局配置")
            
            // Check for stream.txt (network URL)
            val streamFile = File(publicDir + "stream.txt")
            if (streamFile.exists() && streamFile.canRead()) {
                val reader = BufferedReader(InputStreamReader(streamFile.inputStream(), StandardCharsets.UTF_8))
                val url = reader.readLine()?.trim()?.removePrefix("\uFEFF") ?: ""
                reader.close()
                Log.d(TAG, "loadGlobalConfig: 读取到stream.txt, url='$url'")
                LogFileManager.writeToFile(TAG, "loadGlobalConfig: 读取到stream.txt, url='$url'")
                if (url.isNotEmpty()) {
                    etGlobalUrl.setText(url)
                    tvGlobalVideoPath.text = "未选择"
                    selectedGlobalUri = null
                    return
                }
            } else {
                Log.d(TAG, "loadGlobalConfig: stream.txt不存在或不可读")
                LogFileManager.writeToFile(TAG, "loadGlobalConfig: stream.txt不存在或不可读")
            }
            
            // Check for virtual.mp4 (local video)
            val videoFile = File(publicDir + "virtual.mp4")
            if (videoFile.exists()) {
                Log.d(TAG, "loadGlobalConfig: 找到virtual.mp4: ${videoFile.absolutePath}")
                LogFileManager.writeToFile(TAG, "loadGlobalConfig: 找到virtual.mp4: ${videoFile.absolutePath}")
                tvGlobalVideoPath.text = videoFile.absolutePath
                etGlobalUrl.setText("")
                // Note: We can't restore the original Uri, but we can show the path
            } else {
                Log.d(TAG, "loadGlobalConfig: virtual.mp4不存在")
                LogFileManager.writeToFile(TAG, "loadGlobalConfig: virtual.mp4不存在")
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadGlobalConfig: 异常", e)
            LogFileManager.writeException(TAG, e)
            e.printStackTrace()
        }
    }

    private fun loadLastAppConfig() {
        try {
            Log.d(TAG, "loadLastAppConfig: 开始加载应用配置")
            LogFileManager.writeToFile(TAG, "loadLastAppConfig: 开始加载应用配置")
            
            val publicDirFile = File(publicDir)
            if (!publicDirFile.exists() || !publicDirFile.isDirectory()) {
                Log.d(TAG, "loadLastAppConfig: 公共目录不存在")
                LogFileManager.writeToFile(TAG, "loadLastAppConfig: 公共目录不存在")
                return
            }
            
            // Find the most recently modified app directory
            // 排除系统目录和logs目录
            val appDirs = publicDirFile.listFiles { file ->
                file.isDirectory && 
                file.name != "." && 
                file.name != ".." && 
                file.name != "logs" &&
                !file.name.startsWith(".")
            } ?: return
            
            if (appDirs.isEmpty()) {
                Log.d(TAG, "loadLastAppConfig: 没有找到应用配置目录")
                LogFileManager.writeToFile(TAG, "loadLastAppConfig: 没有找到应用配置目录")
                return
            }
            
            // Get the most recently modified directory
            val lastModifiedDir = appDirs.maxByOrNull { it.lastModified() } ?: return
            val pkg = lastModifiedDir.name
            
            Log.d(TAG, "loadLastAppConfig: 找到最近修改的配置目录: $pkg")
            LogFileManager.writeToFile(TAG, "loadLastAppConfig: 找到最近修改的配置目录: $pkg")
            
            etAppPackage.setText(pkg)
            
            // Check for stream.txt (network URL)
            val streamFile = File(lastModifiedDir, "stream.txt")
            if (streamFile.exists() && streamFile.canRead()) {
                val reader = BufferedReader(InputStreamReader(streamFile.inputStream(), StandardCharsets.UTF_8))
                val url = reader.readLine()?.trim()?.removePrefix("\uFEFF") ?: ""
                reader.close()
                Log.d(TAG, "loadLastAppConfig: 读取到stream.txt, url='$url'")
                LogFileManager.writeToFile(TAG, "loadLastAppConfig: 读取到stream.txt, url='$url'")
                if (url.isNotEmpty()) {
                    etAppUrl.setText(url)
                    tvAppVideoPath.text = "未选择"
                    selectedAppUri = null
                    return
                }
            }
            
            // Check for virtual.mp4 (local video)
            val videoFile = File(lastModifiedDir, "virtual.mp4")
            if (videoFile.exists()) {
                Log.d(TAG, "loadLastAppConfig: 找到virtual.mp4: ${videoFile.absolutePath}")
                LogFileManager.writeToFile(TAG, "loadLastAppConfig: 找到virtual.mp4: ${videoFile.absolutePath}")
                tvAppVideoPath.text = videoFile.absolutePath
                etAppUrl.setText("")
                // Note: We can't restore the original Uri, but we can show the path
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadLastAppConfig: 异常", e)
            LogFileManager.writeException(TAG, e)
            e.printStackTrace()
        }
    }

    private fun updateStatus(msg: String) {
        tvStatus.text = "状态: $msg"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
