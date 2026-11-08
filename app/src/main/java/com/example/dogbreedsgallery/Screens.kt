package com.example.dogbreedsgallery

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

// ======================= Общие компоненты =======================

/** Картинка Coil с индикатором загрузки и заглушкой при ошибке. */
@Composable
private fun DogImage(url: String, modifier: Modifier = Modifier) {
    val placeholderBg = MaterialTheme.colorScheme.surfaceVariant
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url.ifBlank { null })
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = {
            Box(Modifier.fillMaxSize().background(placeholderBg), Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            }
        },
        error = {
            Box(Modifier.fillMaxSize().background(placeholderBg), Alignment.Center) {
                Text("🐶", fontSize = 32.sp)
            }
        }
    )
}

@Composable
private fun FavoriteIcon(isFavorite: Boolean) {
    val color by animateColorAsState(
        if (isFavorite) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "favColor"
    )
    val scale by animateFloatAsState(
        if (isFavorite) 1.2f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "favScale"
    )
    Icon(
        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
        contentDescription = if (isFavorite) "Убрать из избранного" else "В избранное",
        tint = color,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    )
}

/** Список карточек — общий для главного экрана и избранного. */
@Composable
private fun BreedList(
    breeds: List<BreedEntity>,
    onBreedClick: (Int) -> Unit,
    onFavoriteClick: (BreedEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(breeds, key = { it.id }) { breed ->
            Card(
                onClick = { onBreedClick(breed.id) },
                modifier = Modifier.fillMaxWidth().animateItem(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row {
                    DogImage(breed.imageUrl, Modifier.size(112.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            breed.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        breed.origin?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            breed.description ?: breed.temperament,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    IconButton(onClick = { onFavoriteClick(breed) }) { FavoriteIcon(breed.isFavorite) }
                }
            }
        }
    }
}

/** Заглушка для загрузки / пустого списка / ошибки. Прокручиваемая, чтобы работал pull-to-refresh. */
@Composable
private fun MessageView(
    emoji: String?,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        Arrangement.Center,
        Alignment.CenterHorizontally
    ) {
        if (emoji == null) CircularProgressIndicator() else Text(emoji, fontSize = 56.sp)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (actionText != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) { Text(actionText) }
        }
    }
}

// ======================= Главный экран =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onBreedClick: (Int) -> Unit, onFavoritesClick: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.events.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Породы собак") },
                actions = {
                    IconButton(onClick = onFavoritesClick) {
                        Icon(Icons.Default.Favorite, contentDescription = "Избранное")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Поиск по названию") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true
            )

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f)
            ) {
                AnimatedContent(
                    targetState = state,
                    contentKey = { it::class },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "homeState"
                ) { current ->
                    when (current) {
                        HomeUiState.Loading -> MessageView(null, "Загружаем породы…")
                        is HomeUiState.Error -> MessageView(
                            "📡", "Не удалось загрузить данные",
                            subtitle = current.message, actionText = "Повторить", onAction = viewModel::refresh
                        )
                        is HomeUiState.Empty -> MessageView(
                            "🔍",
                            if (current.query.isBlank()) "Список пуст" else "Ничего не найдено по запросу «${current.query}»",
                            subtitle = if (current.query.isBlank()) "Потяните вниз, чтобы обновить" else null
                        )
                        is HomeUiState.Success -> BreedList(current.breeds, onBreedClick, viewModel::toggleFavorite)
                    }
                }
            }
        }
    }
}


// ======================= Детали =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(viewModel: DetailViewModel, onBack: () -> Unit) {
    val breed by viewModel.breed.collectAsStateWithLifecycle()
    val gallery by viewModel.gallery.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(breed?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        val b = breed
        if (b == null) {
            MessageView(null, "Загрузка…", Modifier.padding(padding))
            return@Scaffold
        }

        val images = (listOf(b.imageUrl) + gallery).filter { it.isNotBlank() }.distinct().ifEmpty { listOf("") }
        val pagerState = rememberPagerState(pageCount = { images.size })

        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            HorizontalPager(pagerState, Modifier.fillMaxWidth().height(300.dp)) { page ->
                DogImage(images[page], Modifier.fillMaxSize())
            }
            if (images.size > 1) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.Center) {
                    repeat(images.size.coerceAtMost(20)) { index ->
                        val dot by animateColorAsState(
                            if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            label = "dot"
                        )
                        Box(Modifier.padding(3.dp).size(8.dp).clip(CircleShape).background(dot))
                    }
                }
            }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(b.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                InfoRow("Происхождение", b.origin ?: "Нет данных")
                InfoRow("Продолжительность жизни", b.lifeSpan.ifBlank { "Нет данных" })
                InfoRow("Темперамент", b.temperament.ifBlank { "Нет данных" })
                Text(b.description ?: "Описание отсутствует", style = MaterialTheme.typography.bodyLarge)
                FilledTonalButton(onClick = viewModel::toggleFavorite, modifier = Modifier.fillMaxWidth()) {
                    FavoriteIcon(b.isFavorite)
                    Text(if (b.isFavorite) "В избранном" else "В избранное", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// ======================= Избранное =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(viewModel: FavoritesViewModel, onBreedClick: (Int) -> Unit, onBack: () -> Unit) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Избранное") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        val list = favorites
        when {
            list == null -> MessageView(null, "Загрузка…", Modifier.padding(padding))
            list.isEmpty() -> MessageView(
                "💔", "Пока ничего нет",
                Modifier.padding(padding), subtitle = "Добавляйте породы в избранное сердечком"
            )
            else -> BreedList(list, onBreedClick, viewModel::toggleFavorite, Modifier.padding(padding))
        }
    }
}