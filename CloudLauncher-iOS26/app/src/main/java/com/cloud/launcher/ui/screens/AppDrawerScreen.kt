package com.cloud.launcher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloud.launcher.R
import com.cloud.launcher.data.AppInfo
import com.cloud.launcher.ui.LauncherViewModel
import com.cloud.launcher.ui.components.AppContextMenu
import com.cloud.launcher.ui.components.AppTile
import com.cloud.launcher.ui.components.CloudSearchBar
import com.cloud.launcher.ui.theme.CloudAccent
import com.cloud.launcher.ui.theme.CloudBlack
import com.cloud.launcher.ui.theme.CloudGray

@Composable
fun AppDrawerScreen(vm: LauncherViewModel, onBack: () -> Unit) {
    val apps by vm.drawerApps.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sortByUsage by vm.sortByUsage.collectAsStateWithLifecycle()
    val protectedPkgs by vm.protectedPkgs.collectAsStateWithLifecycle()
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }

    BackHandler(enabled = query.isNotEmpty()) { vm.setQuery("") }
    DisposableEffect(Unit) {
        onDispose { vm.setQuery("") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CloudBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CloudSearchBar(
                query = query,
                onQueryChange = vm::setQuery,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { vm.setSortByUsage(!sortByUsage) }) {
                Icon(
                    imageVector = if (sortByUsage) Icons.Filled.TrendingUp else Icons.Filled.SortByAlpha,
                    contentDescription = stringResource(if (sortByUsage) R.string.sort_usage else R.string.sort_az),
                    tint = if (sortByUsage) CloudAccent else CloudGray
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppTile(
                    app = app,
                    onClick = { vm.launchApp(app.packageName) },
                    onLongClick = { menuApp = app },
                    showLockBadge = app.packageName in protectedPkgs
                )
            }
        }
    }

    menuApp?.let { app -> AppContextMenu(app = app, vm = vm, onDismiss = { menuApp = null }) }
}
