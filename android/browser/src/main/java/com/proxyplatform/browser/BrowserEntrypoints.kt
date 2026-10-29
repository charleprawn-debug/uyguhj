package com.proxyplatform.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/** Public launcher entry: opens safely without proxy extras; browsing stays locked until KUN hands off a live connection. */
class BrowserHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, BrowserActivity::class.java))
        finish()
    }
}

/** Signature-protected entry used only by the companion KUN app to pass the already-running proxy endpoint. */
class BrowserLaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, BrowserActivity::class.java).putExtras(intent).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
