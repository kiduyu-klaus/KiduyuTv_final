package com.kiduyuk.klausk.kiduyutv.ui.screens.home.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.model.IptvChannel
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.MobileBottomNavigation
import com.kiduyuk.klausk.kiduyutv.ui.components.mobile.MobileSearchTopBar
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.player.iptv.IptvPlayerActivity
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import com.kiduyuk.klausk.kiduyutv.viewmodel.LiveTvViewModel

@Composable
fun MobileLiveTvScreen(
    navController: NavController,
    onNavigate: (String) -> Unit = {},
    viewModel: LiveTvViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        viewModel.loadPlaylist()
    }

    Scaffold(
        topBar = {
            MobileSearchTopBar(
                onSearchClick = { onNavigate(Screen.Search.route) },
                onSettingsClick = { onNavigate(Screen.Settings.route) },
                title = "Live TV"
            )
        },
        bottomBar = { MobileBottomNavigation(navController, currentRoute) }
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {

            if (uiState.isLoading && selectedTab == 0) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    LottieLoadingView(size = 200.dp)
                }
                return@Box
            }

            if (uiState.error != null && selectedTab == 0) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(text = "${uiState.error}")
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.loadPlaylist(forceRefresh = true) }) {
                        Text("Retry")
                    }
                }
                return@Box
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Tab row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Black,
                    contentColor = Color.White
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Live TV") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            val favCount = viewModel.getFavoriteChannels().size
                            Text(
                                text = if (favCount > 0) "My Channels ($favCount)" else "My Channels"
                            )
                        }
                    )
                }

                // Tab content
                when (selectedTab) {
                    0 -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                placeholder = { Text("Search channels...") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotBlank()) {
                                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF121212),
                                    unfocusedContainerColor = Color(0xFF121212),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedPlaceholderColor = Color.LightGray,
                                    unfocusedPlaceholderColor = Color.LightGray,
                                    focusedBorderColor = PrimaryRed,
                                    unfocusedBorderColor = TextSecondary
                                ),
                                singleLine = true
                            )

                            if (uiState.searchQuery.isNotBlank()) {
                                if (uiState.searchResults.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(text = "No channels match \"${uiState.searchQuery}\"", color = TextSecondary)
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                                        items(uiState.searchResults) { channel ->
                                            ChannelRow(channel) { selected ->
                                                val intent = IptvPlayerActivity.createIntent(
                                                    context,
                                                    selected.name,
                                                    selected.url,
                                                    selected.logo,
                                                    selected.tvgId,
                                                    selected.tvgName,
                                                    selected.group
                                                )
                                                context.startActivity(intent)
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            } else {
                                if (uiState.selectedCategory == null) {
                                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                                        items(uiState.categories) { category ->
                                            CategoryRow(category.name, category.channelCount) {
                                                viewModel.selectCategory(category.name)
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                } else {
                                    // Channels list for selected category
                                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                                        items(uiState.channels) { channel ->
                                            ChannelRow(channel) { selected ->
                                                val intent = IptvPlayerActivity.createIntent(
                                                    context,
                                                    selected.name,
                                                    selected.url,
                                                    selected.logo,
                                                    selected.tvgId,
                                                    selected.tvgName,
                                                    selected.group
                                                )
                                                context.startActivity(intent)
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // My Channels tab - favorites
                        val favorites = viewModel.getFavoriteChannels()
                        if (favorites.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = "No favorite channels yet")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "Long-press a channel to add it to favorites")
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                                items(favorites) { channel ->
                                    ChannelRow(channel) { selected ->
                                        val intent = IptvPlayerActivity.createIntent(
                                            context,
                                            selected.name,
                                            selected.url,
                                            selected.logo,
                                            selected.tvgId,
                                            selected.tvgName,
                                            selected.group
                                        )
                                        context.startActivity(intent)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(name: String, count: Int, onClick: () -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = name, modifier = Modifier.weight(1f))
        Text(text = "${count} channels")
    }
}

@Composable
private fun ChannelRow(channel: IptvChannel, onPlay: (IptvChannel) -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable { onPlay(channel) }
        .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = channel.logo, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = channel.name)
            Text(text = channel.group ?: "", modifier = Modifier.padding(top = 4.dp))
        }
        Button(onClick = { onPlay(channel) }) { Text("Play") }
    }
}
