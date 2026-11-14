package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.DraftPreferences
import com.example.data.repository.StudioRepository
import com.example.ui.StudioScreen
import com.example.ui.StudioViewModel
import com.example.ui.theme.EnsisViTheme

/**
 * Local-only entry point: no Google sign-in, no Firebase.
 * The studio opens straight away and everything is stored on this phone.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EnsisViRoot()
        }
    }
}

@Composable
fun EnsisViRoot() {
    val viewModel: StudioViewModel = viewModel(
        key = StudioRepository.LOCAL_USER_ID,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                StudioViewModel(
                    appContext = app,
                    repository = StudioRepository(app),
                    draftPreferences = DraftPreferences(app),
                    currentUserId = StudioRepository.LOCAL_USER_ID,
                    initialDisplayName = "Songwriter"
                )
            }
        }
    )

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val themeStyle by viewModel.themeStyle.collectAsStateWithLifecycle()

    EnsisViTheme(darkTheme = settings.darkTheme, themeStyle = themeStyle) {
        StudioScreen(
            viewModel = viewModel,
            userEmail = null,
            onSignedOut = {}
        )
    }
}
