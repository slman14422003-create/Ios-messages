package com.cloud.launcher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloud.launcher.R
import com.cloud.launcher.data.AppInfo
import com.cloud.launcher.ui.LauncherViewModel
import com.cloud.launcher.ui.theme.CloudDanger
import com.cloud.launcher.ui.theme.CloudSurface
import com.cloud.launcher.ui.theme.CloudWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContextMenu(app: AppInfo, vm: LauncherViewModel, onDismiss: () -> Unit) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val protectedPkgs by vm.protectedPkgs.collectAsStateWithLifecycle()
    val recents by vm.recentPkgs.collectAsStateWithLifecycle()
    val root by vm.rootAvailable.collectAsStateWithLifecycle()
    val pkg = app.packageName
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CloudSurface,
        contentColor = CloudWhite
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(packageName = pkg, size = 44.dp)
                Spacer(Modifier.width(16.dp))
                Text(app.label, style = MaterialTheme.typography.titleLarge, color = CloudWhite)
            }

            MenuItem(
                icon = Icons.Filled.Home,
                text = stringResource(if (pkg in favorites) R.string.menu_remove_home else R.string.menu_add_home)
            ) {
                vm.toggleFavorite(pkg)
                onDismiss()
            }
            MenuItem(
                icon = Icons.Filled.Security,
                text = stringResource(if (pkg in protectedPkgs) R.string.menu_remove_protect else R.string.menu_add_protect)
            ) {
                vm.toggleProtect(pkg)
                onDismiss()
            }
            if (pkg !in recents) {
                MenuItem(icon = Icons.Filled.History, text = stringResource(R.string.menu_add_recents)) {
                    vm.addToRecents(pkg)
                    onDismiss()
                }
            }
            MenuItem(icon = Icons.Filled.Info, text = stringResource(R.string.menu_app_info)) {
                vm.openAppInfo(pkg)
                onDismiss()
            }
            MenuItem(icon = Icons.Filled.Delete, text = stringResource(R.string.menu_uninstall)) {
                vm.uninstall(pkg)
                onDismiss()
            }
            MenuItem(
                icon = Icons.Filled.Close,
                text = stringResource(R.string.menu_force_stop),
                tint = CloudDanger,
                enabled = root == true
            ) {
                vm.forceStop(pkg)
                onDismiss()
            }
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    text: String,
    tint: Color = CloudWhite,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val color = if (enabled) tint else tint.copy(alpha = 0.35f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(20.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}
