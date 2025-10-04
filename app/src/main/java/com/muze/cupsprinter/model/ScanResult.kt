package com.muze.cupsprinter.model

import java.io.File

data class ScanResult(
    val file: File?,
    val success: Boolean,
    val message: String? = null
)
