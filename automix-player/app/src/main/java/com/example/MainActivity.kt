package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.AppDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.PlaylistViewModel

class MainActivity : ComponentActivity() {
  
  private val viewModel: PlaylistViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        AppDashboard(viewModel = viewModel)
      }
    }
  }
}
