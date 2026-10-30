package com.proxyplatform.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.content.Intent

/** Compatibility entry from KUN Proxy; the standalone browser still requires its own sign-in and profile selection. */
class BrowserLaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, BrowserHomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
