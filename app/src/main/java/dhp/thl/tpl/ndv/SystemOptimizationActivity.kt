package dhp.thl.tpl.ndv

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class SystemOptimizationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // No manual fullscreen code here - handled by Manifest theme
        val webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            // Useful for games:
            mediaPlaybackRequiresUserGesture = false
        }

        webView.webViewClient = WebViewClient()
        
        // Ensure your file is in: app/src/main/assets/duoibat.html
        webView.loadUrl("file:///android_asset/duoibat.html")
    }
}
