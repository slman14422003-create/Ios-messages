package app.ios.messages.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.ios.messages.R

@Composable
fun BackupPasswordDialog(confirm: Boolean, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    val ok = pw.length >= 4 && (!confirm || pw == pw2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (confirm) R.string.backup_set_password else R.string.backup_enter_password))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.backup_password)) }
                )
                if (confirm) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pw2,
                        onValueChange = { pw2 = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text(stringResource(R.string.backup_password_confirm)) }
                    )
                    if (pw2.isNotEmpty() && pw != pw2) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.backup_mismatch), color = Color(0xFFFF3B30))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onOk(pw) }, enabled = ok) { Text(stringResource(R.string.backup_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
