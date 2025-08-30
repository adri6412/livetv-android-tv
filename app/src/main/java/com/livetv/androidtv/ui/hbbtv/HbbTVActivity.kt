package com.livetv.androidtv.ui.hbbtv

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import androidx.fragment.app.FragmentActivity
import com.livetv.androidtv.R
import com.livetv.androidtv.databinding.ActivityHbbtvBinding
import com.livetv.androidtv.data.entity.Channel

class HbbTVActivity : FragmentActivity() {
    
    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_CHANNEL = "extra_channel"
    }
    
    private lateinit var binding: ActivityHbbtvBinding
    private var hbbtvUrl: String? = null
    private var channel: Channel? = null
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Mantieni schermo acceso e nascondi barre di sistema
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        
        binding = ActivityHbbtvBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Ottieni parametri dall'intent
        hbbtvUrl = intent.getStringExtra(EXTRA_URL)
        channel = intent.getParcelableExtra(EXTRA_CHANNEL)
        
        if (hbbtvUrl.isNullOrEmpty()) {
            finish()
            return
        }
        
        setupWebView()
        setupUI()
        
        // Carica l'URL HbbTV
        loadHbbTVUrl(hbbtvUrl!!)
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                allowUniversalAccessFromFileURLs = true
                allowFileAccessFromFileURLs = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_NO_CACHE
                
                // User agent per HbbTV
                userAgentString = "Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Safari/537.36 HbbTV/1.5.1"
            }
            
            webViewClient = HbbTVWebViewClient()
            webChromeClient = HbbTVWebChromeClient()
            
            // Aggiungi interfaccia JavaScript per HbbTV
            addJavascriptInterface(HbbTVJavaScriptInterface(), "HbbTV")
        }
    }
    
    private fun setupUI() {
        channel?.let { ch ->
            binding.textChannelName.text = ch.getDisplayName()
        }
        
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonRefresh.setOnClickListener { 
            hbbtvUrl?.let { loadHbbTVUrl(it) }
        }
        
        // Nascondi controlli inizialmente
        hideControls()
    }
    
    private fun loadHbbTVUrl(url: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.textError.visibility = View.GONE
        
        try {
            binding.webView.loadUrl(url)
        } catch (e: Exception) {
            showError("Errore nel caricamento: ${e.message}")
        }
    }
    
    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
    }
    
    private fun showControls() {
        binding.layoutControls.visibility = View.VISIBLE
    }
    
    private fun hideControls() {
        binding.layoutControls.visibility = View.GONE
    }
    
    private fun toggleControls() {
        if (binding.layoutControls.visibility == View.VISIBLE) {
            hideControls()
        } else {
            showControls()
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                    true
                } else if (binding.layoutControls.visibility == View.VISIBLE) {
                    hideControls()
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            KeyEvent.KEYCODE_MENU -> {
                toggleControls()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // Simula click su WebView
                binding.webView.performClick()
                true
            }
            // Tasti colorati del telecomando per HbbTV
            KeyEvent.KEYCODE_PROG_RED -> {
                injectKeyEvent("red")
                true
            }
            KeyEvent.KEYCODE_PROG_GREEN -> {
                injectKeyEvent("green")
                true
            }
            KeyEvent.KEYCODE_PROG_YELLOW -> {
                injectKeyEvent("yellow")
                true
            }
            KeyEvent.KEYCODE_PROG_BLUE -> {
                injectKeyEvent("blue")
                true
            }
            else -> {
                // Passa altri tasti alla WebView
                super.onKeyDown(keyCode, event)
            }
        }
    }
    
    private fun injectKeyEvent(color: String) {
        val javascript = """
            if (typeof window.hbbtvKeyEvent === 'function') {
                window.hbbtvKeyEvent('$color');
            } else {
                var event = new KeyboardEvent('keydown', {
                    key: '$color',
                    code: 'Color$color',
                    bubbles: true
                });
                document.dispatchEvent(event);
            }
        """.trimIndent()
        
        binding.webView.evaluateJavascript(javascript, null)
    }
    
    inner class HbbTVWebViewClient : WebViewClient() {
        
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            binding.progressBar.visibility = View.VISIBLE
            binding.textError.visibility = View.GONE
        }
        
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            binding.progressBar.visibility = View.GONE
            
            // Inietta script HbbTV
            injectHbbTVScript()
        }
        
        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            super.onReceivedError(view, request, error)
            showError("Errore di caricamento: ${error?.description}")
        }
        
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            // Permetti navigazione all'interno del dominio HbbTV
            return false
        }
    }
    
    inner class HbbTVWebChromeClient : WebChromeClient() {
        
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            // Aggiorna progress bar se necessario
        }
        
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            // Log messaggi console per debug
            return super.onConsoleMessage(consoleMessage)
        }
    }
    
    inner class HbbTVJavaScriptInterface {
        
        @JavascriptInterface
        fun exit() {
            runOnUiThread { finish() }
        }
        
        @JavascriptInterface
        fun getChannelInfo(): String {
            return channel?.let { 
                """{"name": "${it.name}", "number": ${it.number}, "id": "${it.id}"}"""
            } ?: "{}"
        }
    }
    
    private fun injectHbbTVScript() {
        val script = """
            // HbbTV API simulation
            if (!window.hbbtvApi) {
                window.hbbtvApi = {
                    version: '1.5.1',
                    exit: function() {
                        HbbTV.exit();
                    },
                    getChannelInfo: function() {
                        return JSON.parse(HbbTV.getChannelInfo());
                    }
                };
                
                // Dispatch HbbTV ready event
                var event = new Event('hbbtvready');
                document.dispatchEvent(event);
            }
        """.trimIndent()
        
        binding.webView.evaluateJavascript(script, null)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        binding.webView.destroy()
    }
}