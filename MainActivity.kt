package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.DraftPreferences
import com.example.firebase.configureAppCheck
import com.example.data.repository.StudioRepository
import com.example.ui.StudioScreen
import com.example.ui.StudioViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.components.BlueprintGridBackground
import com.example.ui.theme.EnsisViTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            EnsisViRoot()
        }
    }
}

@Composable
fun EnsisViRoot() {
    val context = LocalContext.current.applicationContext
    var firebaseState by remember { mutableStateOf<Pair<FirebaseAuth, FirebaseFirestore>?>(null) }

    LaunchedEffect(Unit) {
        val pair = withContext(Dispatchers.IO) {
            val auth = Firebase.auth
            val dbId = context.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(dbId)
            auth to db
        }
        firebaseState = pair
    }

    val initialized = firebaseState
    if (initialized == null) {
        EnsisViTheme(darkTheme = false) {
            BlueprintGridBackground {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
        return
    }

    val (auth, db) = initialized
    var currentUser by remember(auth) { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { fbAuth ->
            currentUser = fbAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        EnsisViTheme(darkTheme = false) {
            AuthScreen(
                onAuthSuccess = { currentUser = auth.currentUser }
            )
        }
    } else {
        val uid = user.uid
        val displayName = user.displayName ?: "Songwriter"
        val userEmail = user.email
        key(uid) {
            AuthenticatedStudio(
                db = db,
                auth = auth,
                currentUserId = uid,
                displayName = displayName,
                userEmail = userEmail,
                onSignedOut = { currentUser = null }
            )
        }
    }
}

@Composable
private fun AuthenticatedStudio(
    db: FirebaseFirestore,
    auth: FirebaseAuth,
    currentUserId: String,
    displayName: String,
    userEmail: String?,
    onSignedOut: () -> Unit
) {
    val viewModel: StudioViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val repo = StudioRepository(db, auth)
                val drafts = DraftPreferences(app)
                StudioViewModel(
                    appContext = app,
                    repository = repo,
                    draftPreferences = drafts,
                    currentUserId = currentUserId,
                    initialDisplayName = displayName
                )
            }
        }
    )

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val themeStyle by viewModel.themeStyle.collectAsStateWithLifecycle()

    EnsisViTheme(darkTheme = settings.darkTheme, themeStyle = themeStyle) {
        StudioScreen(
            viewModel = viewModel,
            userEmail = userEmail,
            onSignedOut = onSignedOut
        )
    }
}
