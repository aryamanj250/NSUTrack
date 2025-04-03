package com.nsutrack.nsuttrial

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles downloading files from the IMS website,
 * especially for plum_url.php links that require proper session handling
 */
class DocumentDownloader(private val context: Context) {

    companion object {
        private const val TAG = "DocumentDownloader"
        private const val BASE_URL = "https://www.imsnsit.org/imsnsit/"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.119 Safari/537.36"
    }

    /**
     * Downloads a document from a URL, handling cookies and headers properly
     * Returns the downloaded file
     */
    suspend fun downloadDocument(url: String): File {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Log.d(TAG, "Starting download from: $url")

            // Create a connection
            val connection = URL(url).openConnection() as HttpURLConnection

            // Set required headers
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5")
            connection.setRequestProperty("Connection", "keep-alive")
            connection.setRequestProperty("Upgrade-Insecure-Requests", "1")

            // The critical part: Set Referer header
            connection.setRequestProperty("Referer", "${BASE_URL}notifications.php")

            // Attach cookies from CookieManager
            val cookieManager = CookieManager.getInstance()
            val cookies = cookieManager.getCookie(url)
            if (cookies != null) {
                Log.d(TAG, "Adding cookies: $cookies")
                connection.setRequestProperty("Cookie", cookies)
            } else {
                Log.w(TAG, "No cookies found for URL")
            }

            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            try {
                // Get response code
                val responseCode = connection.responseCode
                Log.d(TAG, "Response code: $responseCode")

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    val errorMsg = "Server returned HTTP ${responseCode}"
                    Log.e(TAG, errorMsg)
                    throw Exception(errorMsg)
                }

                // Determine file name from headers or generate one
                var fileName = getFileNameFromHeaders(connection) ?: generateFileName(url)
                Log.d(TAG, "Using filename: $fileName")

                // Create file in cache directory
                val file = createFileInCache(fileName)

                // Download the file
                connection.inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }

                Log.d(TAG, "Download completed to: ${file.absolutePath}")
                file

            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * Try to extract filename from Content-Disposition header
     */
    private fun getFileNameFromHeaders(connection: HttpURLConnection): String? {
        val contentDisposition = connection.getHeaderField("Content-Disposition")
        if (contentDisposition != null) {
            val pattern = "filename=\"?([^\"]*)"
            val regex = Regex(pattern)
            val matchResult = regex.find(contentDisposition)
            return matchResult?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
        }
        return null
    }

    /**
     * Generate a filename based on URL and timestamp if one can't be extracted from headers
     */
    private fun generateFileName(url: String): String {
        // First try to get the last path segment
        val urlPath = URL(url).path
        val lastSegment = urlPath.substringAfterLast('/')

        // If it's a PHP file or has no extension, generate a timestamped PDF name
        return if (lastSegment.contains(".php") || !lastSegment.contains(".")) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            "document_$timestamp.pdf"
        } else {
            lastSegment
        }
    }

    /**
     * Creates a file in the app's cache directory
     */
    private fun createFileInCache(fileName: String): File {
        val cacheDir = context.cacheDir
        val file = File(cacheDir, fileName)

        // Remove old file if it exists
        if (file.exists()) {
            file.delete()
        }

        return file
    }
}