package com.talme.utsavas

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import com.talme.utsavas.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    private val inAppHosts: Set<String> by lazy {
        setOfNotNull(
            Uri.parse(BuildConfig.WEB_URL).host,
            "checkout.razorpay.com",
            "api.razorpay.com",
        ).map { host ->
            host.lowercase(Locale.US).removePrefix("www.")
        }.toSet()
    }

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileChooserCallback
            fileChooserCallback = null
            callback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data),
            )
        }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            deliverLocationPermission(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUi()
        setupWebView()

        if (savedInstanceState != null) {
            val restoredState = binding.webView.restoreState(savedInstanceState)
            if (restoredState == null) {
                loadHome()
            }
        } else {
            loadHome()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        binding.webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onPause() {
        binding.webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        pendingGeoCallback = null
        pendingGeoOrigin = null
        binding.webView.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            destroy()
        }
        super.onDestroy()
    }

    private fun setupUi() {
        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.utsavas_primary),
            ContextCompat.getColor(this, R.color.utsavas_accent),
        )
        binding.retryButton.setOnClickListener {
            loadHome()
        }

        binding.swipeRefresh.setOnRefreshListener {
            if (hasNetworkConnection()) {
                binding.offlineContainer.isVisible = false
                binding.webView.reload()
            } else {
                binding.swipeRefresh.isRefreshing = false
                showOfflineState()
            }
        }

        binding.webView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            binding.swipeRefresh.isEnabled = scrollY == 0
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.webView.canGoBack()) {
                        binding.webView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)

        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            allowContentAccess = true
            builtInZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            displayZoomControls = false
            setGeolocationEnabled(true)
            setSupportZoom(false)
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            userAgentString = "${userAgentString} UTSAVAS-Android"
        }

        binding.webView.webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest,
                ): Boolean {
                    return handleNavigation(request.url)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    binding.offlineContainer.isVisible = false
                    binding.loadingBar.isVisible = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    binding.swipeRefresh.isRefreshing = false
                    if (binding.loadingBar.progress >= 100) {
                        binding.loadingBar.isVisible = false
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError?,
                ) {
                    if (request.isForMainFrame) {
                        showOfflineState()
                    }
                }
            }

        binding.webView.webChromeClient =
            object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    updateLoadingProgress(newProgress)
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?,
                ): Boolean {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = filePathCallback

                    val chooserIntent = try {
                        fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                    } catch (_: Exception) {
                        null
                    }

                    if (chooserIntent == null) {
                        fileChooserCallback = null
                        toast(R.string.file_picker_unavailable)
                        return false
                    }

                    return try {
                        fileChooserLauncher.launch(chooserIntent)
                        true
                    } catch (_: ActivityNotFoundException) {
                        fileChooserCallback = null
                        toast(R.string.file_picker_unavailable)
                        false
                    }
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?,
                ) {
                    if (origin == null || callback == null) {
                        super.onGeolocationPermissionsShowPrompt(origin, callback)
                        return
                    }

                    handleGeolocationPrompt(origin, callback)
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?,
                ): Boolean {
                    val targetUrl = view?.hitTestResult?.extra ?: return false
                    view.post { handlePopupNavigation(targetUrl) }
                    return false
                }
            }

        binding.webView.setDownloadListener { url, _, _, _, _ ->
            if (!url.isNullOrBlank()) {
                openExternal(Uri.parse(url))
            }
        }
    }

    private fun loadHome() {
        if (!hasNetworkConnection()) {
            showOfflineState()
            return
        }

        binding.offlineContainer.isVisible = false
        binding.webView.loadUrl(BuildConfig.WEB_URL)
    }

    private fun showOfflineState() {
        binding.swipeRefresh.isRefreshing = false
        binding.loadingBar.isVisible = false
        binding.offlineContainer.isVisible = true
    }

    private fun updateLoadingProgress(progress: Int) {
        binding.loadingBar.progress = progress
        binding.loadingBar.isVisible = progress in 1..99
        if (progress >= 100) {
            binding.loadingBar.isVisible = false
        }
    }

    private fun handleNavigation(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return false

        return when (scheme) {
            "http", "https" -> {
                if (shouldLoadInWebView(uri)) {
                    false
                } else {
                    openExternal(uri)
                    true
                }
            }

            "intent" -> {
                openIntentUri(uri.toString())
                true
            }

            "about", "javascript" -> false
            else -> {
                openExternal(uri)
                true
            }
        }
    }

    private fun handlePopupNavigation(rawUrl: String) {
        val uri = Uri.parse(rawUrl)
        when (uri.scheme?.lowercase(Locale.US)) {
            "http", "https" -> {
                if (shouldLoadInWebView(uri)) {
                    binding.webView.loadUrl(rawUrl)
                } else {
                    openExternal(uri)
                }
            }

            "intent" -> openIntentUri(rawUrl)
            else -> openExternal(uri)
        }
    }

    private fun shouldLoadInWebView(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
        if (scheme != "http" && scheme != "https") {
            return false
        }

        val normalizedHost = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return false
        return normalizedHost in inAppHosts
    }

    private fun openIntentUri(rawUrl: String) {
        val intent = try {
            Intent.parseUri(rawUrl, Intent.URI_INTENT_SCHEME)
        } catch (_: Exception) {
            toast(R.string.open_external_error)
            return
        }

        val fallbackUrl = intent.getStringExtra("browser_fallback_url")
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null

        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            if (!fallbackUrl.isNullOrBlank()) {
                binding.webView.loadUrl(fallbackUrl)
            } else {
                toast(R.string.open_external_error)
            }
        }
    }

    private fun openExternal(uri: Uri) {
        val intent =
            when (uri.scheme?.lowercase(Locale.US)) {
                "tel" -> Intent(Intent.ACTION_DIAL, uri)
                "mailto" -> Intent(Intent.ACTION_SENDTO, uri)
                else -> Intent(Intent.ACTION_VIEW, uri)
            }

        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(R.string.open_external_error)
        }
    }

    private fun handleGeolocationPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback,
    ) {
        val fineGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            callback.invoke(origin, true, false)
            return
        }

        pendingGeoOrigin = origin
        pendingGeoCallback = callback
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    private fun deliverLocationPermission(granted: Boolean) {
        pendingGeoCallback?.invoke(pendingGeoOrigin, granted, false)
        pendingGeoCallback = null
        pendingGeoOrigin = null

        if (!granted) {
            toast(R.string.location_permission_denied)
        }
    }

    private fun hasNetworkConnection(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun toast(messageResId: Int) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show()
    }
}
