package com.leaderportal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leaderportal.ui.screens.LeaderDashboardScreen
import com.leaderportal.ui.screens.LoginScreen
import com.leaderportal.ui.theme.MyApplicationTheme
import com.leaderportal.ui.viewmodel.LeaderViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LeaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
        } catch (_: Exception) {
        }
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LeaderAdminApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.verifyActiveSession()
    }
}

@Composable
fun LeaderAdminApp(viewModel: LeaderViewModel) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            viewModel.verifyActiveSession()
        }
    }

    if (!isLoggedIn) {
        LoginScreen(
            onLogin = { email, password, onResult ->
                viewModel.login(email, password, onResult)
            },
            errorMessage = loginError
        )
    } else {
        LeaderDashboardScreen(viewModel = viewModel)
    }
}
