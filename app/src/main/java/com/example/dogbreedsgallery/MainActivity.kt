package com.example.dogbreedsgallery

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.dogbreedsgallery.ui.theme.DogBreedsGalleryTheme
import kotlinx.serialization.Serializable

class DogApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDependencies.init(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DogBreedsGalleryTheme { AppNavigation() }
        }
    }
}

@Serializable data object HomeKey : NavKey
@Serializable data object FavoritesKey : NavKey
@Serializable data class DetailKey(val breedId: Int) : NavKey

@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(HomeKey)
    val back = { backStack.removeLastOrNull(); Unit }

    NavDisplay(
        backStack = backStack,
        onBack = { back() },
        entryProvider = entryProvider {
            entry<HomeKey> {
                HomeScreen(
                    viewModel = viewModel(factory = HomeViewModel.Factory),
                    onBreedClick = { backStack.add(DetailKey(it)) },
                    onFavoritesClick = { backStack.add(FavoritesKey) }
                )
            }
            entry<FavoritesKey> {
                FavoritesScreen(
                    viewModel = viewModel(factory = FavoritesViewModel.Factory),
                    onBreedClick = { backStack.add(DetailKey(it)) },
                    onBack = back
                )
            }
            entry<DetailKey> { key ->
                DetailScreen(
                    // key нужен, чтобы у каждой породы был свой ViewModel
                    viewModel = viewModel(key = "detail_${key.breedId}", factory = DetailViewModel.factory(key.breedId)),
                    onBack = back
                )
            }
        }
    )
}
