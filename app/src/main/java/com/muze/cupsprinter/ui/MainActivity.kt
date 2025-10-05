package com.muze.cupsprinter.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.muze.cupsprinter.R
import com.muze.cupsprinter.manager.PrintingManager
import com.muze.cupsprinter.model.CupsServerConfig
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var etServerIp: EditText
    private lateinit var etServerPort: EditText
    private lateinit var spPrinterName: Spinner
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnPickFile: Button
    private lateinit var btnPrint: Button
    private var pickedFile: File? = null
    private var pickedMime: String = "application/pdf"

    // 替换过时的 startActivityForResult，使用 Activity Result API
    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                val file = uriToFile(uri)
                if (file != null) {
                    pickedFile = file
                    pickedMime = contentResolver.getType(uri) ?: "application/pdf"
                    Toast.makeText(this, "已选择文件: ${file.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "文件选择失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

    etServerIp = findViewById(R.id.et_server_ip)
    etServerPort = findViewById(R.id.et_server_port)
    spPrinterName = findViewById(R.id.et_printer_name)
    etUsername = findViewById(R.id.et_username)
    etPassword = findViewById(R.id.et_password)
    btnPickFile = findViewById(R.id.btn_pick_file)
    btnPrint = findViewById(R.id.btn_print)

        // 自动获取打印机列表（延迟到UI初始化完成后执行）
        btnPrint.post {
            loadPrinterList()
        }

        // 长按打印按钮查询打印机状态
        btnPrint.setOnLongClickListener {
            queryPrinterStatus()
            true
        }

        // 选择文件按钮点击事件
        btnPickFile.setOnClickListener {
            pickFile()
        }

        // 打印按钮点击事件
        btnPrint.setOnClickListener {
            printFile()
        }
    }

    /**
     * 加载CUPS服务器上的打印机列表
     */
    private fun loadPrinterList() {
        val ip = etServerIp.text.toString().trim()
        val port = etServerPort.text.toString().trim().toIntOrNull() ?: 631 // 默认CUPS端口631
        val username = etUsername.text.toString().trim().ifEmpty { null }
        val password = etPassword.text.toString().trim().ifEmpty { null }

        // 校验服务器IP
        if (ip.isEmpty()) {
            Toast.makeText(this, "请输入CUPS服务器IP", Toast.LENGTH_SHORT).show()
            return
        }

        // 子线程执行网络请求，避免主线程阻塞
        Thread {
            val config = CupsServerConfig(ip, port, username, password)
            val printers = PrintingManager.getPrinterList(config)

            // 主线程更新Spinner（UI操作必须在主线程）
            runOnUiThread {
                if (printers.isNotEmpty()) {
                    val adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_item,
                        printers
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spPrinterName.adapter = adapter
                } else {
                    Toast.makeText(this, "未获取到打印机列表", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /**
     * 查询选中打印机的状态
     */
    private fun queryPrinterStatus() {
        val ip = etServerIp.text.toString().trim()
        val port = etServerPort.text.toString().trim().toIntOrNull() ?: 631
        val username = etUsername.text.toString().trim().ifEmpty { null }
        val password = etPassword.text.toString().trim().ifEmpty { null }
        val printerName = spPrinterName.selectedItem?.toString() ?: return // 无选中项直接返回

        // 校验IP和打印机名
        if (ip.isEmpty()) {
            Toast.makeText(this, "请输入CUPS服务器IP", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            val config = CupsServerConfig(ip, port, username, password)
            val status = PrintingManager.getPrinterStatus(config, printerName)

            // 主线程显示状态
            runOnUiThread {
                Toast.makeText(this, "打印机状态: $status", Toast.LENGTH_SHORT).show() // 修复字符串模板
            }
        }.start()
    }

    /**
     * 打开文件选择器（支持PDF、PNG、JPEG）
     */
    private fun pickFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*" // 所有类型文件（后续通过EXTRA_MIME_TYPES过滤）
        val supportedMimeTypes = arrayOf("application/pdf", "image/png", "image/jpeg")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, supportedMimeTypes)
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true) // 仅本地文件

        // 启动文件选择器（通过Activity Result Launcher）
        pickFileLauncher.launch(Intent.createChooser(intent, "选择打印文件"))
    }

    /**
     * 将Content Uri（如content://xxx）转换为本地文件（保存到cacheDir）
     */
    private fun uriToFile(uri: Uri): File? {
        return try {
            // 1. 从Uri获取输入流（读取文件内容）
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            // 2. 获取原文件名（保留扩展名，避免格式错误）
            val originalFileName = uri.lastPathSegment ?: "print_file_${System.currentTimeMillis()}"
            // 3. 在应用缓存目录创建文件（无需外部存储权限）
            val targetFile = File(cacheDir, originalFileName)

            // 4. 复制文件内容到目标文件（use关键字自动关闭流，避免内存泄漏）
            inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 4096) // 4KB缓冲区，提升复制效率
                }
            }

            targetFile // 返回转换后的本地文件
        } catch (e: Exception) {
            e.printStackTrace()
            null // 异常时返回null
        }
    }

    /**
     * 发送文件到CUPS服务器打印
     */
    private fun printFile() {
        val ip = etServerIp.text.toString().trim()
        val port = etServerPort.text.toString().trim().toIntOrNull() ?: 631
        val printerName = spPrinterName.selectedItem?.toString() ?: return
        val username = etUsername.text.toString().trim().ifEmpty { null }
        val password = etPassword.text.toString().trim().ifEmpty { null }
        val targetFile = pickedFile

        // 输入校验：IP、文件必须存在
        if (ip.isEmpty()) {
            Toast.makeText(this, "请输入CUPS服务器IP", Toast.LENGTH_SHORT).show()
            return
        }
        if (targetFile == null) {
            Toast.makeText(this, "请先选择打印文件", Toast.LENGTH_SHORT).show()
            return
        }

        // 子线程执行打印请求（网络操作+文件读写，避免主线程阻塞）
        Thread {
            val config = CupsServerConfig(ip, port, username, password)
            val printSuccess = PrintingManager.printFile(
                config = config,
                printerName = printerName,
                file = targetFile,
                mimeType = pickedMime
            )

            // 主线程显示打印结果
            runOnUiThread {
                if (printSuccess) {
                    Toast.makeText(this, "打印任务已发送", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "打印失败，请检查服务器或网络", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
