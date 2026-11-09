package com.cloud.launcher.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloud.launcher.R
import com.cloud.launcher.ui.LauncherViewModel
import com.cloud.launcher.ui.theme.CloudAccent
import com.cloud.launcher.ui.theme.CloudDanger
import com.cloud.launcher.ui.theme.CloudGray
import com.cloud.launcher.ui.theme.CloudSuccess
import com.cloud.launcher.ui.theme.CloudSurface
import com.cloud.launcher.ui.theme.CloudWhite
import com.cloud.launcher.utils.PermissionUtils

@Composable
fun SetupDialog(vm: LauncherViewModel, onRequestNotifications: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val root by vm.rootAvailable.collectAsStateWithLifecycle()
    val setup by vm.setup.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CloudSurface,
        titleContentColor = CloudWhite,
        textContentColor = CloudGray,
        title = { Text(stringResource(R.string.setup_title)) },
        text = {
            Column {
                Text(stringResource(R.string.setup_root_note), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                SetupRow(stringResource(R.string.setup_root), root == true, stringResource(R.string.setup_grant)) {
                    vm.requestRoot()
                }
                SetupRow(stringResource(R.string.setup_usage), setup.usageAccess, stringResource(R.string.setup_grant)) {
                    PermissionUtils.openUsageAccessSettings(context)
                }
                SetupRow(stringResource(R.string.setup_accessibility), setup.accessibility, stringResource(R.string.setup_grant)) {
                    PermissionUtils.openAccessibilitySettings(context)
                }
                SetupRow(stringResource(R.string.setup_notifications), setup.notifications, stringResource(R.string.setup_grant)) {
                    onRequestNotifications()
                }
                SetupRow(stringResource(R.string.setup_default_launcher), false, stringResource(R.string.setup_set)) {
                    PermissionUtils.openDefaultLauncherSettings(context)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.setup_close), color = CloudAccent)
            }
        }
    )
}

@Composable
private fun SetupRow(title: String, granted: Boolean, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (granted) CloudSuccess else CloudDanger
        )
        Spacer(Modifier.width(12.dp))
        Text(title, color = CloudWhite, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (granted) {
            Text(stringResource(R.string.setup_granted), color = CloudSuccess, style = MaterialTheme.typography.labelSmall)
        } else {
            TextButton(onClick = onAction) { Text(actionLabel, color = CloudAccent) }
        }
    }
}
