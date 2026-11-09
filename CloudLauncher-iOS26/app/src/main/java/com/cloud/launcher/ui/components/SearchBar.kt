package com.cloud.launcher.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cloud.launcher.R
import com.cloud.launcher.ui.theme.CloudAccent
import com.cloud.launcher.ui.theme.CloudGray
import com.cloud.launcher.ui.theme.CloudSurfaceHigh
import com.cloud.launcher.ui.theme.CloudWhite

@Composable
fun CloudSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint), color = CloudGray) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = CloudGray) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = CloudGray)
                }
            }
        },
        shape = RoundedCornerShape(26.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0x26FFFFFF),
            unfocusedContainerColor = Color(0x26FFFFFF),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = CloudWhite,
            unfocusedTextColor = CloudWhite,
            cursorColor = CloudAccent
        )
    )
}
