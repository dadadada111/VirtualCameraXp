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
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : AppCompatActivity() {

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

        initViews()
        checkPermissions()
        createPublicDir()
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
        if (url.isNotEmpty()) {
            saveConfig("", "stream.txt", url)
            // Delete virtual.mp4 to avoid conflict if exists
            File(publicDir + "virtual.mp4").delete()
        } else if (selectedGlobalUri != null) {
            copyVideo(selectedGlobalUri!!, "", "virtual.mp4")
            // Delete stream.txt to avoid conflict
            File(publicDir + "stream.txt").delete()
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
        if (url.isNotEmpty()) {
            saveConfig(pkg, "stream.txt", url)
            File(appDir, "virtual.mp4").delete()
        } else if (selectedAppUri != null) {
            copyVideo(selectedAppUri!!, pkg, "virtual.mp4")
            File(appDir, "stream.txt").delete()
        } else {
            updateStatus("应用配置为空，未保存")
        }
    }

    private fun saveConfig(pkg: String, fileName: String, content: String) {
        try {
            val dirPath = if (pkg.isEmpty()) publicDir else "$publicDir$pkg/"
            val dir = File(dirPath)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dirPath + fileName)
            // 使用UTF-8编码保存，确保与读取一致
            val fos = FileOutputStream(file)
            fos.write(content.toByteArray(Charsets.UTF_8))
            fos.close()
            updateStatus("保存成功: ${file.absolutePath}")
        } catch (e: Exception) {
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

    private fun updateStatus(msg: String) {
        tvStatus.text = "状态: $msg"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
