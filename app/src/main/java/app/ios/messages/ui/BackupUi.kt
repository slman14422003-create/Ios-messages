package app.ios.messages.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ios.messages.R

@Composable
fun BackupPasswordDialog(confirm: Boolean, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    val ok = pw.length >= 8 && (!confirm || pw == pw2)
    IosAlert(
        title = stringResource(if (confirm) R.string.backup_set_password else R.string.backup_enter_password),
        actions = listOf(
            AlertAction(stringResource(R.string.cancel), onClick = onDismiss),
            AlertAction(stringResource(R.string.backup_ok), bold = true, enabled = ok, onClick = { onOk(pw) })
        ),
        onDismiss = onDismiss
    ) {
        IosField(pw, { pw = it }, stringResource(R.string.backup_password), password = true)
        if (confirm) {
            Spacer(Modifier.height(8.dp))
            IosField(pw2, { pw2 = it }, stringResource(R.string.backup_password_confirm), password = true)
            if (pw2.isNotEmpty() && pw != pw2) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.backup_mismatch), color = Red, fontSize = 13.sp)
            }
        }
    }
}
