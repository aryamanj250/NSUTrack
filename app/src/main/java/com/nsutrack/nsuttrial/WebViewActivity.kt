package com.nsutrack.nsuttrial

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File

class WebViewActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var toolbar: Toolbar
    private lateinit var documentDownloader: DocumentDownloader

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        private const val TAG = "WebViewActivity"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        // Initialize document downloader
        documentDownloader = DocumentDownloader(this)

        // Get the URL and Title from intent
        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            Log.e(TAG, "URL not provided in Intent extras")
            Toast.makeText(this, "Error: Invalid URL", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Web Content" // Default title

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

        // Check if it's a plum_url.php link that needs special handling
        if (url.contains("plum_url.php")) {
            handlePlumUrl(url)
        } else {
            // Regular URL
            loadUrlWithCookies(url)
        }
    }

    // Handle special plum_url.php links
    private fun handlePlumUrl(url: String) {
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        supportActionBar?.title = "Downloading Document..."

        // Hide WebView while downloading
        webView.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val file = documentDownloader.downloadDocument(url)
                openDownloadedFile(file)
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading document", e)
                Toast.makeText(this@WebViewActivity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()

                // Fall back to normal WebView if download fails
                progressBar.visibility = View.GONE
                webView.visibility = View.VISIBLE
                loadUrlWithCookies(url)
            }
        }
    }

    private fun openDownloadedFile(file: File) {
        try {
            // Create content URI using FileProvider
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            // Determine MIME type
            val mimeType = getMimeType(file.name)

            // Create intent to view file
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Don't use resolveActivity() to avoid package visibility issues
            try {
                startActivity(intent)
                // Hide progress bar
                progressBar.visibility = View.GONE

                finish()
            } catch (e: android.content.ActivityNotFoundException) {
                // No app can handle this file type
                Toast.makeText(this, "No app found to open this type of file", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error opening file", e)
            Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_LONG).show()

            // Show WebView as fallback
            progressBar.visibility = View.GONE
            webView.visibility = View.VISIBLE
        }
    }

    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            fileName.endsWith(".doc", ignoreCase = true) -> "application/msword"
            fileName.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            fileName.endsWith(".xls", ignoreCase = true) -> "application/vnd.ms-excel"
            fileName.endsWith(".xlsx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            fileName.endsWith(".txt", ignoreCase = true) -> "text/plain"
            fileName.endsWith(".jpg", ignoreCase = true) -> "image/jpeg"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            else -> "application/octet-stream" // Generic binary data
        }
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

            // Important: Set custom user agent to mimic desktop browser
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.119 Safari/537.36"
        }

        // Ensure cookies are enabled and handled
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // Set up WebView client
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                Log.d(TAG, "Loading URL: $url")

                // Check if this is a plum_url.php link
                if (url.contains("plum_url.php")) {
                    handlePlumUrl(url)
                    return true // We're handling this URL
                }

                // Let WebView handle regular URLs
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
        }

        // Set up WebChromeClient for UI related events
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

            // Check if this is a plum_url.php link
            if (downloadUrl.contains("plum_url.php")) {
                handlePlumUrl(downloadUrl)
                return@setDownloadListener
            }

            // Regular download
            try {
                val request = DownloadManager.Request(Uri.parse(downloadUrl))
                request.setMimeType(mimetype)

                // Add cookies
                val cookies = CookieManager.getInstance().getCookie(downloadUrl)
                if (cookies != null) {
                    request.addRequestHeader("cookie", cookies)
                }

                // Add headers
                request.addRequestHeader("User-Agent", userAgent)
                request.addRequestHeader("Referer", webView.url ?: "https://www.imsnsit.org/imsnsit/notifications.php")

                // Set description
                request.setDescription("Downloading file...")

                // Determine filename
                var fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
                if (fileName.endsWith(".php", ignoreCase = true)) {
                    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype)
                    fileName = if (extension != null) "downloaded_file.$extension" else "downloaded_file"
                }

                request.setTitle(fileName)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                // Set destination
                request.setDestinationInExternalFilesDir(
                    this@WebViewActivity,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )

                // Enqueue the download
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)

                Toast.makeText(applicationContext, "Starting download: $fileName", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Log.e(TAG, "Error setting up download", e)
                Toast.makeText(applicationContext, "Error: Could not start download.", Toast.LENGTH_LONG).show()
            }
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
        webView.loadUrl(url)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.clearHistory()
        webView.clearCache(true)
        webView.loadUrl("about:blank")
        webView.onPause()
        webView.removeAllViews()
        webView.destroyDrawingCache()
        webView.destroy()
        super.onDestroy()
    }
}