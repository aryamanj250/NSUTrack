package com.nsutrack.nsuttrial

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
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
import androidx.appcompat.app.AppCompatActivity // Import AppCompatActivity
import androidx.appcompat.widget.Toolbar // Import AppCompat Toolbar

/**
 * Activity for displaying web content with session cookies and download support.
 * Inherits from AppCompatActivity to use Toolbar easily.
 */
class WebViewActivity : AppCompatActivity() { // Changed to AppCompatActivity
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var toolbar: Toolbar // Use androidx.appcompat.widget.Toolbar

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        private const val TAG = "WebViewActivity"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview) // Ensure this layout exists

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
        setSupportActionBar(toolbar) // Set the toolbar as the ActionBar
        supportActionBar?.title = title
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Show back button
        supportActionBar?.setDisplayShowHomeEnabled(true)

        progressBar = findViewById(R.id.progressBar)
        webView = findViewById(R.id.webView)

        setupWebView() // Configure WebView settings and clients
        setupDownloadListener() // Setup the download listener
        setupBackButtonHandler() // Setup custom back button logic

        loadUrlWithCookies(url) // Load the initial URL
    }

    // Handle Toolbar back button press
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed() // Trigger back press logic
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true // Essential for many modern websites
            domStorageEnabled = true // Needed for localStorage/sessionStorage
            databaseEnabled = true // May be needed for some web apps
            loadWithOverviewMode = true // Zoom out to fit content
            useWideViewPort = true // Ensure viewport is set correctly
            builtInZoomControls = true // Allow pinch zoom
            displayZoomControls = false // Hide the +/- zoom buttons
            setSupportMultipleWindows(true) // Can be important for some auth flows or popups
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW // Adjust as needed for HTTPS/HTTP content
            // Consider setting a User-Agent string if the site requires a specific one
            // userAgentString = "Your Custom User Agent"
        }

        // Ensure cookies are enabled and handled
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true) // Accept third-party cookies if needed
        }

        // Set up WebView client to handle page loading events within the WebView
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Let the WebView handle loading URLs within the main frame
                // Return false to load the URL in the current WebView
                // You might add logic here to open external links in a browser
                Log.d(TAG, "Loading URL: ${request.url}")
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 0 // Reset progress
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                // Update toolbar title with the actual page title if available
                supportActionBar?.title = view?.title ?: intent.getStringExtra(EXTRA_TITLE) ?: "Web Content"
                // Ensure cookies are persisted
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                progressBar.visibility = View.GONE
                val errorMessage = "Error loading page: ${error?.description} (Code: ${error?.errorCode})"
                Log.e(TAG, "$errorMessage URL: ${request?.url}")
                // Avoid showing toast for minor errors like net::ERR_UNKNOWN_URL_SCHEME
                if (error?.errorCode != ERROR_UNKNOWN && error?.errorCode != ERROR_UNSUPPORTED_SCHEME) {
                    Toast.makeText(this@WebViewActivity, "Load Error: ${error?.description}", Toast.LENGTH_SHORT).show()
                }
                // Optionally, load a custom error page or show an error message view
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                Log.e(TAG, "HTTP Error: ${errorResponse?.statusCode} for ${request?.url}")
                // Handle HTTP errors (like 404, 500) if needed
            }
        }

        // Set up WebChromeClient for UI related events like progress, alerts, etc.
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
                // Update toolbar title dynamically as page title changes
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

            try {
                val request = DownloadManager.Request(Uri.parse(downloadUrl))
                request.setMimeType(mimetype)

                // --- Crucial: Add cookies ---
                val cookies = CookieManager.getInstance().getCookie(downloadUrl)
                if (cookies != null) {
                    request.addRequestHeader("cookie", cookies)
                    Log.d(TAG, "Adding cookies to download request: $cookies")
                } else {
                    Log.w(TAG, "No cookies found for download URL: $downloadUrl")
                }
                // --- Add other headers ---
                request.addRequestHeader("User-Agent", userAgent)
                // Add Referer if needed (though often not required for direct download link)
                // request.addRequestHeader("Referer", webView.url)

                request.setDescription("Downloading file...") // Description shown in download manager

                // --- Determine filename ---
                var fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
                Log.d(TAG, "Guessed filename: $fileName")
                // Prevent saving as .php, give a more generic name or use extension from mime type if possible
                if (fileName.endsWith(".php", ignoreCase = true)) {
                    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype)
                    fileName = if (extension != null) "downloaded_file.$extension" else "downloaded_file"
                    Log.d(TAG, "Adjusted filename (was PHP): $fileName")
                }

                request.setTitle(fileName) // Title shown in notification
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                // --- Set destination ---
                // Saving to app-specific directory in Downloads - no storage permission needed post KitKat
                request.setDestinationInExternalFilesDir(
                    this@WebViewActivity,
                    Environment.DIRECTORY_DOWNLOADS, // Standard directory
                    fileName
                )

                // --- Enqueue the download ---
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)

                Toast.makeText(applicationContext, "Starting download: $fileName", Toast.LENGTH_LONG).show()

                // Optional: If the download link was the main purpose, you might want to finish the activity
                // if (!webView.canGoBack()) { finish() } // Example: finish if it was the first page loaded

            } catch (e: Exception) {
                Log.e(TAG, "Error setting up download: ${e.message}", e)
                Toast.makeText(applicationContext, "Error: Could not start download.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupBackButtonHandler() {
        // Handle back presses: Go back in WebView history first, then finish activity
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack() // Go back in WebView history
                } else {
                    // If WebView can't go back, disable this callback and let default behavior (finish activity) occur
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun loadUrlWithCookies(url: String) {
        Log.i(TAG, "Loading URL: $url")
        // Cookies are already configured in setupWebView, just load the URL
        webView.loadUrl(url)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause() // Pause WebView timers, etc.
        // Persist cookies when pausing
        CookieManager.getInstance().flush()
        Log.d(TAG, "WebView paused and cookies flushed.")
    }

    override fun onResume() {
        super.onResume()
        webView.onResume() // Resume WebView
        Log.d(TAG, "WebView resumed.")
    }

    // Handle memory cleanup
    override fun onDestroy() {
        // Prevent memory leaks by properly destroying the WebView
        webView.stopLoading()
        webView.clearHistory()
        webView.clearCache(true)
        webView.loadUrl("about:blank") // Load a blank page
        webView.onPause()
        webView.removeAllViews()
        webView.destroyDrawingCache()
        webView.destroy()
        // super.onDestroy() should be called last
        super.onDestroy()
        Log.d(TAG, "WebView destroyed.")
    }
}