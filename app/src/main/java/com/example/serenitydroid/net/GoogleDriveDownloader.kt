package com.example.serenitydroid.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object GoogleDriveDownloader {

    fun extractFileId(input: String): String {
        val trimmed = input.trim()
        if (trimmed.length in 25..50 && !trimmed.contains("/")) {
            return trimmed
        }
        val pattern = Pattern.compile("(?:/file/d/|id=|=)([a-zA-Z0-9_-]{25,50})")
        val matcher = pattern.matcher(trimmed)
        if (matcher.find()) {
            return matcher.group(1) ?: trimmed
        }
        return trimmed
    }

    suspend fun downloadFile(
        context: Context,
        fileIdOrUrl: String,
        targetFileName: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val fileId = extractFileId(fileIdOrUrl)
        val initialUrl = "https://drive.google.com/uc?export=download&id=$fileId"
        
        var connection = openConnection(initialUrl)
        var responseCode = connection.responseCode
        val cookies = connection.getHeaderField("Set-Cookie")

        // Handle Google Drive confirmation page for large files (>100MB)
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val contentType = connection.contentType ?: ""
            if (contentType.contains("text/html")) {
                val html = connection.inputStream.bufferedReader().use { it.readText() }
                val confirmToken = extractConfirmToken(html)
                if (!confirmToken.isNullOrEmpty()) {
                    val confirmUrl = "https://drive.google.com/uc?export=download&confirm=$confirmToken&id=$fileId"
                    connection.disconnect()
                    connection = openConnection(confirmUrl, cookies)
                }
            }
        } else if (responseCode in 300..399) {
            val redirectUrl = connection.getHeaderField("Location")
            connection.disconnect()
            if (!redirectUrl.isNullOrEmpty()) {
                connection = openConnection(redirectUrl, cookies)
            }
        }

        val modelsDir = File(context.filesDir, "Models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        val outputFile = File(modelsDir, targetFileName)
        val contentLength = connection.contentLengthLong

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            inputStream = connection.inputStream
            outputStream = FileOutputStream(outputFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                onProgress(totalRead, contentLength)
            }
            outputStream.flush()
            outputFile
        } finally {
            inputStream?.close()
            outputStream?.close()
            connection.disconnect()
        }
    }

    private fun openConnection(urlString: String, cookies: String? = null): HttpURLConnection {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        if (!cookies.isNullOrEmpty()) {
            conn.setRequestProperty("Cookie", cookies)
        }
        return conn
    }

    private fun extractConfirmToken(html: String): String? {
        val pattern = Pattern.compile("confirm=([a-zA-Z0-9_-]+)")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }
        val pattern2 = Pattern.compile("download_warning[^\"]*confirm=([a-zA-Z0-9_-]+)")
        val matcher2 = pattern2.matcher(html)
        if (matcher2.find()) {
            return matcher2.group(1)
        }
        return null
    }
}
