package com.cloud.launcher.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloud.launcher.R
import com.cloud.launcher.data.ProtectedAppsRepository
import com.cloud.launcher.ui.LauncherViewModel
import com.cloud.launcher.ui.components.AppIcon
import com.cloud.launcher.ui.components.CloudSearchBar
import com.cloud.launcher.ui.theme.CloudAccent
import com.cloud.launcher.ui.theme.CloudBlack
import com.cloud.launcher.ui.theme.CloudDanger
import com.cloud.launcher.ui.theme.CloudGray
import com.cloud.launcher.ui.theme.CloudSuccess
import com.cloud.launcher.ui.theme.CloudSurface
import com.cloud.launcher.ui.theme.CloudSurfaceHigh
import com.cloud.launcher.ui.theme.CloudWhite
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectedAppsScreen(vm: LauncherViewModel, onBack: () -> Unit) {
    val protectedPkgs by vm.protectedPkgs.collectAsStateWithLifecycle()
    val appMap by vm.appMap.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val root by vm.rootAvailable.collectAsStateWithLifecycle()
    val allApps by vm.apps.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    val max = ProtectedAppsRepository.MAX_PROTECTED

    LaunchedEffect(Unit) {
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
                text = stringResource(R.string.protected_title) + "  (" + protectedPkgs.size + "/" + max + ")",
                color = CloudWhite,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }

        if (root == false) {
            Text(
                text = stringResource(R.string.root_required),
                color = CloudDanger,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (protectedPkgs.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.protected_empty),
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
                items(protectedPkgs, key = { it }) { pkg ->
                    val app = appMap[pkg]
                    val run = running[pkg]
                    Surface(shape = RoundedCornerShape(16.dp), color = CloudSurfaceHigh) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(packageName = pkg, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app?.label ?: pkg,
                                    color = CloudWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (run != null) {
                                    Text(
                                        text = stringResource(R.string.protected_running) + "  •  PID " + run.pid + "  •  " + run.rssMb + " MB",
                                        color = CloudSuccess,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.protected_stopped),
                                        color = CloudGray,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            IconButton(onClick = { vm.toggleProtect(pkg) }) {
                                Icon(Icons.Filled.Close, contentDescription = null, tint = CloudDanger)
                            }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            OutlinedButton(
                onClick = { showPicker = true },
                enabled = protectedPkgs.size < max,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.protected_add))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = vm::reapplyProtection,
                enabled = root == true && protectedPkgs.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CloudAccent, contentColor = CloudBlack)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.protect_now))
            }
        }
    }

    if (showPicker) {
        var pickerQuery by remember { mutableStateOf("") }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val candidates = remember(allApps, protectedPkgs, pickerQuery) {
            allApps.filter { it.packageName !in protectedPkgs && it.label.contains(pickerQuery.trim(), ignoreCase = true) }
        }
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            sheetState = sheetState,
            containerColor = CloudSurface,
            contentColor = CloudWhite
        ) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                Text(
                    text = stringResource(R.string.picker_title),
                    color = CloudWhite,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                CloudSearchBar(
                    query = pickerQuery,
                    onQueryChange = { pickerQuery = it },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(candidates, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.toggleProtect(app.packageName)
                                    showPicker = false
                                }
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(packageName = app.packageName, size = 40.dp)
                            Spacer(Modifier.width(16.dp))
                            Text(app.label, color = CloudWhite, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
