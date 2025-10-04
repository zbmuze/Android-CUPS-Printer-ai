package com.muze.cupsprinter.manager

import com.muze.cupsprinter.model.CupsServerConfig
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

object PrintingManager {

    /**
     * 获取 CUPS 服务器上的打印机列表（通过 IPP 协议）
     */
    fun getPrinterList(config: CupsServerConfig): List<String> {
        val printers = mutableListOf<String>()
        val urlStr = "http://${'$'}{config.ip}:${'$'}{config.port}/printers/"
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            if (!config.username.isNullOrEmpty() && !config.password.isNullOrEmpty()) {
                val auth = Base64.getEncoder().encodeToString("${'$'}{config.username}:${'$'}{config.password}".toByteArray())
                conn.setRequestProperty("Authorization", "Basic ${'$'}auth")
            }
            val html = conn.inputStream.bufferedReader().readText()
            // 简单解析 HTML，提取打印机名（<A HREF="/printers/xxx">xxx</A>）
            val regex = Regex("/printers/([^"]+)")
            regex.findAll(html).forEach { match ->
                val name = match.groupValues[1]
                if (name.isNotEmpty()) {
                    if (!printers.contains(name)) printers.add(name)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return printers
    }

    /**
     * 查询打印任务状态（通过 CUPS jobs API）
     * @param config CUPS 服务器配置
     * @param printerName 打印机名
     * @return 打印任务状态字符串
     */
    fun getPrinterStatus(config: CupsServerConfig, printerName: String): String {
        val urlStr = "http://${'$'}{config.ip}:${'$'}{config.port}/printers/${'$'}printerName"
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            if (!config.username.isNullOrEmpty() && !config.password.isNullOrEmpty()) {
                val auth = Base64.getEncoder().encodeToString("${'$'}{config.username}:${'$'}{config.password}".toByteArray())
                conn.setRequestProperty("Authorization", "Basic ${'$'}auth")
            }
            val html = conn.inputStream.bufferedReader().readText()
            // 简单解析 HTML，查找状态（如 idle, processing, stopped）
            val statusRegex = Regex("<TR><TH>Status:</TH><TD>(.*?)</TD></TR>", RegexOption.IGNORE_CASE)
            val match = statusRegex.find(html)
            return match?.groupValues?.get(1) ?: "未知"
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "查询失败"
    }
    /**
     * 发送文件到 CUPS 打印服务器，支持 PDF、图片等格式
     * @param config CUPS 服务器配置
     * @param printerName 打印机名
     * @param file 待打印的文件
     * @param mimeType 文件类型
     * @return true 表示发送成功，false 表示失败
     */
    fun printFile(config: CupsServerConfig, printerName: String, file: File, mimeType: String): Boolean {
        val urlStr = "http://${'$'}{config.ip}:${'$'}{config.port}/printers/${'$'}printerName"
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", mimeType)
            conn.setRequestProperty("Content-Length", file.length().toString())
            if (!config.username.isNullOrEmpty() && !config.password.isNullOrEmpty()) {
                val auth = Base64.getEncoder().encodeToString("${'$'}{config.username}:${'$'}{config.password}".toByteArray())
                conn.setRequestProperty("Authorization", "Basic ${'$'}auth")
            }
            FileInputStream(file).use { input ->
                conn.outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            val responseCode = conn.responseCode
            return responseCode in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
