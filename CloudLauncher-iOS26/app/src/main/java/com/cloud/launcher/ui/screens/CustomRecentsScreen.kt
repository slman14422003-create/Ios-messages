package com.cloud.launcher.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloud.launcher.R
import com.cloud.launcher.data.AppInfo
import com.cloud.launcher.root.RunningApp
import com.cloud.launcher.ui.LauncherViewModel
import com.cloud.launcher.ui.components.AppContextMenu
import com.cloud.launcher.ui.components.AppIcon
import com.cloud.launcher.ui.theme.CloudAccent
import com.cloud.launcher.ui.theme.CloudBlack
import com.cloud.launcher.ui.theme.CloudDanger
import com.cloud.launcher.ui.theme.CloudGray
import com.cloud.launcher.ui.theme.CloudSurface
import com.cloud.launcher.ui.theme.CloudSurfaceHigh
import com.cloud.launcher.ui.theme.CloudWhite
import kotlinx.coroutines.delay

@Composable
fun CustomRecentsScreen(vm: LauncherViewModel, onBack: () -> Unit) {
    val recentPkgs by vm.recentPkgs.collectAsStateWithLifecycle()
    val appMap by vm.appMap.collectAsStateWithLifecycle()
    val protectedPkgs by vm.protectedPkgs.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val root by vm.rootAvailable.collectAsStateWithLifecycle()
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    val items = remember(recentPkgs, appMap) { recentPkgs.mapNotNull { appMap[it] } }

    LaunchedEffect(Unit) {
        vm.syncRecents()
        while (true) {
            vm.refreshRunning()
            delay(4_000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CloudBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = CloudWhite)
            }
            Text(
                text = stringResource(R.string.recents_title),
                color = CloudWhite,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = vm::killAll,
                enabled = root == true && items.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = CloudDanger, contentColor = CloudBlack)
            ) {
                Icon(Icons.Filled.ClearAll, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.kill_all))
            }
        }

        if (root == false) {
            Text(
                text = stringResource(R.string.root_required),
                color = CloudDanger,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (items.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.recents_empty),
                    color = CloudGray,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.packageName }) { app ->
                    val isProtected = app.packageName in protectedPkgs
                    RecentRow(
                        app = app,
                        run = running[app.packageName],
                        rootAvailable = root,
                        isProtected = isProtected,
                        onOpen = { vm.launchApp(app.packageName) },
                        onClose = { vm.forceStop(app.packageName) },
                        onPin = { vm.toggleProtect(app.packageName) },
                        onSwiped = {
                            if (!isProtected) vm.forceStop(app.packageName)
                            vm.removeFromRecents(app.packageName)
                        },
                        onLongPress = { menuApp = app }
                    )
                }
            }
        }
    }

    menuApp?.let { app -> AppContextMenu(app = app, vm = vm, onDismiss = { menuApp = null }) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    app: AppInfo,
    run: RunningApp?,
    rootAvailable: Boolean?,
    isProtected: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onPin: () -> Unit,
    onSwiped: () -> Unit,
    onLongPress: () -> Unit
) {
    val currentOnSwiped by rememberUpdatedState(onSwiped)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.Settled) {
                false
            } else {
                currentOnSwiped()
                true
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CloudDanger.copy(alpha = 0.35f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = CloudWhite)
            }
        },
        content = {
            Surface(shape = RoundedCornerShape(16.dp), color = CloudSurfaceHigh) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(packageName = app.packageName, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            color = CloudWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        val subtitle = when {
                            run != null -> "PID ${run.pid}  •  ${run.rssMb} MB"
                            rootAvailable == true -> stringResource(R.string.status_not_running)
                            else -> stringResource(R.string.status_unknown)
                        }
                        Text(
                            text = subtitle,
                            color = CloudGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    IconButton(onClick = onPin) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = stringResource(R.string.pin_app),
                            tint = if (isProtected) CloudAccent else CloudGray
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close_app),
                            tint = if (isProtected) CloudGray.copy(alpha = 0.4f) else CloudDanger
                        )
                    }
                }
            }
        }
    )
}
