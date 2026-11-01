package com.adnan.memora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.adnan.memora.ui.MemoraApp
import com.adnan.memora.ui.theme.MemoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MemoraTheme { MemoraApp() } }
    }
}
