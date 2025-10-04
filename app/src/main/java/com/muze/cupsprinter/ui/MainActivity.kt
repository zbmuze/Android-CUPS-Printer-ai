package com.muze.cupsprinter.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.muze.cupsprinter.R
import com.muze.cupsprinter.manager.PrintingManager
import com.muze.cupsprinter.model.CupsServerConfig
import com.muze.cupsprinter.utils.ToastUtils
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

        // 自动获取打印机列表
        btnPrint.post {
            loadPrinterList()
        }

        // 打印状态查询按钮（可选）
        btnPrint.setOnLongClickListener {
            queryPrinterStatus()
            true
        }

        btnPickFile.setOnClickListener {
            pickFile()
        }

        btnPrint.setOnClickListener {
            printFile()
        }
    }

    private fun loadPrinterList() {
        val ip = etServerIp.text.toString().trim()
        val port = etServerPort.text.toString().trim().toIntOrNull() ?: 631
        val username = etUsername.text.toString().trim().ifEmpty { null }
        val password = etPassword.text.toString().trim().ifEmpty { null }
        if (ip.isEmpty()) return
        Thread {
            val config = CupsServerConfig(ip, port, username, password)
            val printers = PrintingManager.getPrinterList(config)
            runOnUiThread {
                if (printers.isNotEmpty()) {
                    val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, printers)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spPrinterName.adapter = adapter
                } else {
                    ToastUtils.show(this, "未获取到打印机列表")
                }
            }
        }.start()
    }

    private fun queryPrinterStatus() {
        val ip = etServerIp.text.toString().trim()
        val port = etServerPort.text.toString().trim().toIntOrNull() ?: 631
        val username = etUsername.text.toString().trim().ifEmpty { null }
        val password = etPassword.text.toString().trim().ifEmpty { null }
        val printer = spPrinterName.selectedItem?.toString() ?: return
        Thread {
            val config = CupsServerConfig(ip, port, username, password)
            val status = PrintingManager.getPrinterStatus(config, printer)
            runOnUiThread {
                ToastUtils.show(this, "打印机状态: ${'$'}status")
            }
        }.start()
    }

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        val mimeTypes = arrayOf("application/pdf", "image/png", "image/jpeg")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        startActivityForResult(Intent.createChooser(intent, "选择文件"), 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                val file = uriToFile(uri)
                if (file != null) {
                    pickedFile = file
                    pickedMime = contentResolver.getType(uri) ?: "application/pdf"
                    ToastUtils.show(this, "已选择文件: ${file.name}")
                } else {
                    ToastUtils.show(this, "文件选择失败")
                }
            }
        }
    }

    private fun uriToFile(uri: Uri): File? {
        // 简单实现：将文件复制到 cache 目录
        return try {
            val input = contentResolver.openInputStream(uri) ?: return null
            val file = File(cacheDir, "picked.pdf")
            input.use { inputStream ->
                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun printFile() {
        val ip = etServerIp.text.toString().trim()
        val port = etServerPort.text.toString().trim().toIntOrNull() ?: 631
    val printer = spPrinterName.selectedItem?.toString() ?: ""
        val username = etUsername.text.toString().trim().ifEmpty { null }
        val password = etPassword.text.toString().trim().ifEmpty { null }
        val file = pickedFile
        if (ip.isEmpty() || printer.isEmpty() || file == null) {
            ToastUtils.show(this, "请填写服务器、打印机名并选择文件")
            return
        }
        val config = CupsServerConfig(ip, port, username, password)
        val success = PrintingManager.printFile(config, printer, file, pickedMime)
        if (success) {
            ToastUtils.show(this, "打印任务已发送")
        } else {
            ToastUtils.show(this, "打印失败，请检查服务器或网络")
        }
    }
}
