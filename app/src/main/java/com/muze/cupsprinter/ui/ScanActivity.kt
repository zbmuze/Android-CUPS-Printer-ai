package com.muze.cupsprinter.ui

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.muze.cupsprinter.R
import com.muze.cupsprinter.manager.ScannerManager

class ScanActivity : AppCompatActivity() {
    private lateinit var btnScan: Button
    private lateinit var imgResult: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        btnScan = findViewById(R.id.btn_scan)
        imgResult = findViewById(R.id.img_result)
        btnScan.setOnClickListener {
            // TODO: 调用 ScannerManager.scanDocument 并显示结果
        }
    }
}
