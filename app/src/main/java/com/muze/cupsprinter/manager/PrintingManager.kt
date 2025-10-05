package com.muze.cupsprinter.manager

import android.util.Base64 // 替换为 Android 原生 Base64
import com.muze.cupsprinter.model.CupsServerConfig
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

// object 是 Kotlin 单例，调用时直接用 PrintingManager.方法名() 即可
object PrintingManager {

    /**
     * 获取 CUPS 服务器上的打印机列表（通过 IPP 协议）
     */
    fun getPrinterList(config: CupsServerConfig): List<String> {
        val printers = mutableListOf<String>()
        // 修复：字符串模板语法（移除多余的 '${'$'}'）
        val urlStr = "http://${config.ip}:${config.port}/printers/"
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            
            // 处理用户名密码认证
            if (!config.username.isNullOrEmpty() && !config.password.isNullOrEmpty()) {
                // 修复1：字符串模板语法；修复2：改用 android.util.Base64，添加 NO_WRAP 标志（避免换行）
                val authStr = "${config.username}:${config.password}"
                val auth = Base64.encodeToString(authStr.toByteArray(), Base64.NO_WRAP)
                conn.setRequestProperty("Authorization", "Basic $auth")
            }
            
            // 读取服务器响应并解析打印机名
            val html = conn.inputStream.bufferedReader().readText()
            val regex = Regex("/printers/([^\"]+)") // 匹配 /printers/ 后的非引号内容
            for (match in regex.findAll(html)) {
                val name = match.groupValues[1]
                if (name.isNotEmpty() && !printers.contains(name)) {
                    printers.add(name)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace() // 实际项目中建议用日志框架（如 Log.e）替代，便于调试
        }
        return printers
    }

    /**
     * 查询打印机状态（通过 CUPS jobs API）
     * @param config CUPS 服务器配置
     * @param printerName 打印机名
     * @return 打印任务状态字符串（如 idle、processing、未知、查询失败）
     */
    fun getPrinterStatus(config: CupsServerConfig, printerName: String): String {
        // 修复：字符串模板语法（拼接打印机名）
        val urlStr = "http://${config.ip}:${config.port}/printers/$printerName"
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            
            // 处理用户名密码认证
            if (!config.username.isNullOrEmpty() && !config.password.isNullOrEmpty()) {
                val authStr = "${config.username}:${config.password}"
                val auth = Base64.encodeToString(authStr.toByteArray(), Base64.NO_WRAP)
                conn.setRequestProperty("Authorization", "Basic $auth")
            }
            
            // 读取响应并解析状态（匹配 <TR><TH>Status:</TH><TD>xxx</TD></TR>）
            val html = conn.inputStream.bufferedReader().readText()
            val statusRegex = Regex("<TR><TH>Status:</TH><TD>(.*?)</TD></TR>", RegexOption.IGNORE_CASE)
            val match = statusRegex.find(html)
            return match?.groupValues?.get(1) ?: "未知" // 未匹配到状态返回「未知」
        } catch (e: Exception) {
            e.printStackTrace()
            return "查询失败" // 异常时返回「查询失败」
        }
    }

    /**
     * 发送文件到 CUPS 打印服务器（支持 PDF、图片等格式）
     * @param config CUPS 服务器配置
     * @param printerName 打印机名
     * @param file 待打印的文件
     * @param mimeType 文件类型（如 "application/pdf"、"image/png"）
     * @return true=发送成功，false=发送失败
     */
    fun printFile(
        config: CupsServerConfig,
        printerName: String,
        file: File,
        mimeType: String
    ): Boolean {
        // 修复：字符串模板语法
        val urlStr = "http://${config.ip}:${config.port}/printers/$printerName"
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true // 允许向服务器写入数据（文件内容）
            
            // 设置请求头（文件类型、文件大小、认证）
            conn.setRequestProperty("Content-Type", mimeType)
            conn.setRequestProperty("Content-Length", file.length().toString())
            if (!config.username.isNullOrEmpty() && !config.password.isNullOrEmpty()) {
                val authStr = "${config.username}:${config.password}"
                val auth = Base64.encodeToString(authStr.toByteArray(), Base64.NO_WRAP)
                conn.setRequestProperty("Authorization", "Basic $auth")
            }
            
            // 读取文件并写入服务器（use 关键字会自动关闭流，避免内存泄漏）
            FileInputStream(file).use { inputStream ->
                conn.outputStream.use { outputStream ->
                    inputStream.copyTo(outputStream) // 高效拷贝文件内容
                }
            }
            
            // 响应码 200-299 表示成功（如 201 Created）
            val responseCode = conn.responseCode
            return responseCode in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
