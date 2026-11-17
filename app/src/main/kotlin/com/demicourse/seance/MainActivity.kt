package com.demicourse.seance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.demicourse.seance.data.SeanceRepository
import com.demicourse.seance.ui.SeanceScreen
import com.demicourse.seance.ui.SeanceViewModel
import com.demicourse.seance.ui.theme.LocalSeanceColors
import com.demicourse.seance.ui.theme.SeanceTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = SeanceRepository(applicationContext)
        setContent {
            DemiCourseApp(repository)
        }
    }
}

@Composable
private fun DemiCourseApp(repository: SeanceRepository) {
    val factory = remember(repository) { viewModelFactory { initializer { SeanceViewModel(repository) } } }
    val viewModel: SeanceViewModel = viewModel(factory = factory)
    val theme by remember(viewModel) {
        viewModel.state.map { it.settings.theme }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = viewModel.state.value.settings.theme)

    SeanceTheme(themeChoice = theme) {
        Surface(modifier = Modifier.fillMaxSize(), color = LocalSeanceColors.current.bg) {
            SeanceScreen(viewModel)
        }
    }
}
