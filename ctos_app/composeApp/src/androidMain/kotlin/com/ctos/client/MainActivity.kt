package com.ctos.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.ctos.client.data.AppViewModel
import com.ctos.client.screens.App

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidContext.init(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(viewModel)
        }
    }
}
