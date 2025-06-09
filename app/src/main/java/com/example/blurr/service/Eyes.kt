package com.example.blurr.service

import android.content.Context
import com.topjohnwu.superuser.Shell
import java.io.File

class Eyes(private val context: Context) {
    private val screenshotFile: File = File(context.filesDir, "latest.png")

    fun openEyes() {
        println("Eyes are open")
        val output = screenshotFile.absolutePath
        Shell.cmd("screencap -p $output").exec()
    }

    fun getScreenshotFile(): File {
        return screenshotFile
    }
}
