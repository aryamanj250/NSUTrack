package com.nsutrack.nsuttrial

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import java.io.File
import androidx.core.content.ContextCompat
import java.lang.IllegalArgumentException
import java.lang.Exception
import android.database.Cursor
import android.content.ActivityNotFoundException

/**
 * Activity for displaying web content with session cookies and download support.
 * Inherits from AppCompatActivity to use Toolbar easily.
 */
class WebViewActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var toolbar: Toolbar
    private var downloadReference: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_REFERER = "extra_referer"
        private const val TAG = "WebViewActivity"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        // Get the URL and Title from intent
        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            Log.e(TAG, "URL not provided in Intent extras")
            Toast.makeText(this, "Error: Invalid URL", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Web Content"

        // Initialize UI
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        progressBar = findViewById(R.id.progressBar)
        webView = findViewById(R.id.webView)

        setupWebView()
        setupDownloadListener()
        setupBackButtonHandler()

        // Register BroadcastReceiver for download completion
        registerDownloadReceiver()

        loadUrlWithCookies(url)
    }

    // Handle Toolbar back button press
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                Log.d(TAG, "Loading URL: ${request.url}")
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 0
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                supportActionBar?.title = view?.title ?: intent.getStringExtra(EXTRA_TITLE) ?: "Web Content"
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                progressBar.visibility = View.GONE
                val errorMessage = "Error loading page: ${error?.description} (Code: ${error?.errorCode})"
                Log.e(TAG, "$errorMessage URL: ${request?.url}")
                if (error?.errorCode != ERROR_UNKNOWN && error?.errorCode != ERROR_UNSUPPORTED_SCHEME) {
                    Toast.makeText(this@WebViewActivity, "Load Error: ${error?.description}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                Log.e(TAG, "HTTP Error: ${errorResponse?.statusCode} for ${request?.url}")
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed()
                Log.w(TAG, "SSL Error received: ${error?.primaryError}")
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                super.onLoadResource(view, url)
                if (url?.contains("imsnsit") == true && url.contains("plum_url.php")) {
                    Log.d(TAG, "Loading resource: $url")
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                } else {
                    progressBar.visibility = View.VISIBLE
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrBlank()) {
                    supportActionBar?.title = title
                }
            }
        }
    }

    private fun setupDownloadListener() {
        webView.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, contentLength ->
            Log.d(TAG, "Download requested for URL: $downloadUrl")
            Log.d(TAG, "MIME type: $mimetype, Content-Disposition: $contentDisposition")

            val referer = intent.getStringExtra(EXTRA_REFERER)

            try {
                val request = DownloadManager.Request(Uri.parse(downloadUrl))
                request.setMimeType(mimetype)

                // Add cookies to the request
                val cookies = CookieManager.getInstance().getCookie(downloadUrl)
                if (cookies != null) {
                    request.addRequestHeader("cookie", cookies)
                    Log.d(TAG, "Adding cookies to download request: $cookies")
                } else {
                    Log.w(TAG, "No cookies found for download URL: $downloadUrl")
                }

                request.addRequestHeader("User-Agent", userAgent)

                if (referer != null) {
                    request.addRequestHeader("Referer", referer)
                    Log.d(TAG, "Adding Referer to download request: $referer")
                } else {
                    Log.w(TAG, "Referer header is null, not adding to download request.")
                }

                // Get notification title from intent and sanitize it for use as filename
                var fileName = intent.getStringExtra(EXTRA_TITLE) ?: "download"
                fileName = sanitizeFileName(fileName)

                // Add appropriate file extension based on mimetype
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype) ?:
                guessExtensionFromMimeType(mimetype)
                fileName = "$fileName.$extension"

                Log.d(TAG, "Using filename: $fileName")

                request.setDescription("Downloading file...")
                request.setTitle(fileName)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                // Set destination to a public directory so we can open it later
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(downloadsDir, fileName)
                request.setDestinationUri(Uri.fromFile(destFile))

                // Enqueue the download and save the reference ID
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadReference = dm.enqueue(request)

                Toast.makeText(applicationContext, "Downloading: $fileName", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e(TAG, "Error setting up download: ${e.message}", e)
                Toast.makeText(applicationContext, "Error: Could not start download.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sanitizeFileName(fileName: String): String {
        // Remove invalid file name characters
        var sanitized = fileName.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_") // Replace invalid chars with underscore
            .replace(Regex("\\s+"), "_")            // Replace spaces with underscore

        // Limit the length to avoid extremely long filenames
        if (sanitized.length > 100) {
            sanitized = sanitized.substring(0, 100)
        }

        return sanitized
    }

    private fun guessExtensionFromMimeType(mimeType: String): String {
        return when {
            mimeType.contains("pdf", ignoreCase = true) -> "pdf"
            mimeType.contains("msword", ignoreCase = true) -> "doc"
            mimeType.contains("ms-excel", ignoreCase = true) -> "xls"
            mimeType.contains("ms-powerpoint", ignoreCase = true) -> "ppt"
            mimeType.contains("image/jpeg", ignoreCase = true) -> "jpg"
            mimeType.contains("image/png", ignoreCase = true) -> "png"
            mimeType.contains("text/plain", ignoreCase = true) -> "txt"
            mimeType.contains("text/html", ignoreCase = true) -> "html"
            mimeType.contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document") -> "docx"
            mimeType.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") -> "xlsx"
            mimeType.contains("application/vnd.openxmlformats-officedocument.presentationml.presentation") -> "pptx"
            else -> "bin" // Default extension if we can't determine the type
        }
    }


    // Inside your WebViewActivity class:
    private fun registerDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1

                if (id != -1L && id == downloadReference) {
                    Log.d(TAG, "Download with ID $id completed (matches reference $downloadReference)")

                    val downloadManager = context?.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    if (downloadManager == null) {
                        Log.e(TAG, "DownloadManager service not found.")
                        return
                    }

                    val query = DownloadManager.Query().setFilterById(id)
                    var cursor: Cursor? = null
                    try {
                        cursor = downloadManager.query(query)
                        if (cursor != null && cursor.moveToFirst()) {
                            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else -1

                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                                val mimeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)

                                val uriString = if (uriIndex >= 0) cursor.getString(uriIndex) else null
                                val mimeType = if (mimeIndex >= 0) cursor.getString(mimeIndex) else null

                                if (uriString != null) {
                                    try {
                                        val fileUri = Uri.parse(uriString)
                                        Log.d(TAG, "Attempting to open file URI: $fileUri with MIME type: $mimeType")
                                        openDownloadedFile(fileUri, mimeType) // Pass URI and MIME type
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error parsing or opening downloaded file URI: $uriString", e)
                                        Toast.makeText(context, "Error accessing downloaded file", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Log.e(TAG, "Downloaded file URI is null for ID $id")
                                    Toast.makeText(context, "Downloaded file location not found", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else -1
                                Log.e(TAG, "Download failed for ID $id. Status: $status, Reason: $reason")
                                Toast.makeText(context, "Download failed (Status: $status)", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.w(TAG, "Download Manager cursor is null or empty for ID $id")
                            if (context != null) {
                                Toast.makeText(context, "Download info not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error querying DownloadManager for ID $id", e)
                        if (context != null) {
                            Toast.makeText(context, "Error checking download status", Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        cursor?.close()
                    }
                    downloadReference = -1L // Reset reference only after handling this specific download

                } else if (id != -1L) {
                    Log.d(TAG, "Received download complete for unrelated ID: $id (current reference: $downloadReference)")
                }
            }
        }

        ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Log.i(TAG, "Download receiver registered.")
    }

    // Inside your WebViewActivity class:
    private fun openDownloadedFile(fileUri: Uri, mimeType: String?) {
        try {
            // Use the URI directly provided by DownloadManager.
            // No need to convert to File path or use FileProvider here,
            // as DownloadManager should provide a usable content:// URI.
            val intent = Intent(Intent.ACTION_VIEW).apply {
                // Set the data (URI) and the explicit MIME type.
                // Use "*/*" if MIME type is unknown.
                setDataAndType(fileUri, mimeType ?: "*/*")
                // Grant permission to the receiving app to read this URI.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // Recommended flag when starting from a non-Activity context like a Receiver,
                // though technically called from Activity here, it's safe.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            Log.d(TAG, "Creating intent to view URI: $fileUri with type: ${mimeType ?: "*/*"}")
            startActivity(intent)
            Log.i(TAG, "Intent ACTION_VIEW started for $fileUri")

        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No activity found to handle ACTION_VIEW for URI: $fileUri and MIME type: $mimeType", e)
            Toast.makeText(this, "No app found to open this file type.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // Catch other potential errors, like SecurityException if permissions are wrong
            Log.e(TAG, "Error opening downloaded file: ${e.message}", e)
            Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupBackButtonHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun loadUrlWithCookies(url: String) {
        Log.i(TAG, "Loading URL: $url")

        val referer = intent.getStringExtra(EXTRA_REFERER)

        try {
            if (url.contains("imsnsit.org")) {
                val headers = mutableMapOf<String, String>()
                referer?.let { headers["Referer"] = it }

                val cookies = CookieManager.getInstance().getCookie(url)
                if (cookies != null && cookies.isNotEmpty()) {
                    headers["Cookie"] = cookies
                    Log.d(TAG, "Adding cookies to request: ${cookies.take(50)}...")
                }

                webView.loadUrl(url, headers)
            } else {
                webView.loadUrl(url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading URL with cookies: ${e.message}", e)
            webView.loadUrl(url)
        }
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        CookieManager.getInstance().flush()
        Log.d(TAG, "WebView paused and cookies flushed.")
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        Log.d(TAG, "WebView resumed.")
    }

    // Inside your WebViewActivity class:
    override fun onDestroy() {
        downloadReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.i(TAG, "Download receiver unregistered.")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Error unregistering receiver (might be expected): ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error unregistering receiver: ${e.message}", e)
            }
            downloadReceiver = null
        }

        webView.stopLoading()
        webView.clearHistory()
        webView.clearCache(true)
        webView.loadUrl("about:blank")
        webView.onPause()
        webView.removeAllViews()
        webView.destroyDrawingCache()
        webView.destroy()

        super.onDestroy()
        Log.d(TAG, "WebViewActivity destroyed.")
    }
}