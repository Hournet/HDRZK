package com.hournet.hdrzk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.hournet.hdrzk.ui.theme.HDRZKTheme
import com.hournet.hdrzk.helper.AutoUpdate
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HDRZKTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Добавляем проверку автообновлений
                    AutoUpdate(client = httpClient)

                    Greeting(
                        name = "Android 1.2.5",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    HDRZKTheme {
        Greeting("Android v1.0.1")
    }
}