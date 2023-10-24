package com.thenotesgiver.callhistoryeditorandbackup

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity



class Privacy: AppCompatActivity() {
    private var webView: WebView? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)
        webView = findViewById<View>(R.id.privacy) as WebView

        // displaying content in WebView from html file that stored in assets folder
            webView!!.settings.javaScriptEnabled = true
            webView!!.webViewClient = WebViewClient()
            webView!!.loadUrl("https://privacypolicythenotesgiver.blogspot.com/2023/10/call-history-editor-and-backup-app.html")

    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

}