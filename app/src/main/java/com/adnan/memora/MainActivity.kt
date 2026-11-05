package com.adnan.memora

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.adnan.memora.ui.MemoraApp
import com.adnan.memora.ui.AppLockGate
import com.adnan.memora.ui.AppLockSettings
import com.adnan.memora.ui.theme.MemoraTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLockSettings.initialize(this)
        setContent { MemoraTheme { AppLockGate { MemoraApp() } } }
    }
}
