package com.example.redcassette

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Khởi tạo ViewModel ở cấp cao nhất để lấy màu chủ đạo
            val viewModel: RedCassetteViewModel = viewModel()
            val themeColorInt by viewModel.appThemeColor.collectAsState()
            val themeColor = Color(themeColorInt)

            var showSplash by remember { mutableStateOf(true) }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSplash) {
                        // Truyền themeColor vào SplashScreen để màn hình chờ đồng bộ màu
                        SplashScreen(themeColor = themeColor, onTimeout = { showSplash = false })
                    } else {
                        MainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}