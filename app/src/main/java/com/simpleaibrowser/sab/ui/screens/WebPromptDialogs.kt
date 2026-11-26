package com.simpleaibrowser.sab.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.simpleaibrowser.sab.core.browser.WebPromptRequest
import com.simpleaibrowser.sab.ui.theme.LocalSabColors

@Composable
fun WebPromptDialog(
    prompt: WebPromptRequest,
    onDismissComplete: () -> Unit
) {
    when (prompt) {
        is WebPromptRequest.Alert -> {
            AlertDialog(
                onDismissRequest = {
                    prompt.onDismiss()
                    onDismissComplete()
                },
                title = { Text(prompt.title.ifBlank { prompt.originHost }) },
                text = { Text(prompt.message) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            prompt.onDismiss()
                            onDismissComplete()
                        }
                    ) {
                        Text("OK")
                    }
                }
            )
        }

        is WebPromptRequest.Confirm -> {
            AlertDialog(
                onDismissRequest = {
                    prompt.onResult(false)
                    onDismissComplete()
                },
                title = { Text(prompt.title.ifBlank { prompt.originHost }) },
                text = { Text(prompt.message) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(true)
                            onDismissComplete()
                        }
                    ) {
                        Text(prompt.confirmLabel)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(false)
                            onDismissComplete()
                        }
                    ) {
                        Text(prompt.cancelLabel)
                    }
                }
            )
        }

        is WebPromptRequest.TextInput -> {
            var inputText by remember(prompt.id) { mutableStateOf(prompt.defaultValue) }
            AlertDialog(
                onDismissRequest = {
                    prompt.onResult(null)
                    onDismissComplete()
                },
                title = { Text(prompt.title.ifBlank { prompt.originHost }) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (prompt.message.isNotBlank()) {
                            Text(prompt.message)
                        }
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(inputText)
                            onDismissComplete()
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(null)
                            onDismissComplete()
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        is WebPromptRequest.SitePermission -> {
            AlertDialog(
                onDismissRequest = {
                    prompt.onResult(false)
                    onDismissComplete()
                },
                title = { Text("Site Permission") },
                text = { Text("${prompt.originHost} wants to ${prompt.permissionLabel}.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(true)
                            onDismissComplete()
                        }
                    ) {
                        Text("Allow")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(false)
                            onDismissComplete()
                        }
                    ) {
                        Text("Block")
                    }
                }
            )
        }

        is WebPromptRequest.HttpAuth -> {
            var username by remember(prompt.id) { mutableStateOf("") }
            var password by remember(prompt.id) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = {
                    prompt.onResult(null, null)
                    onDismissComplete()
                },
                title = { Text(prompt.title) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${prompt.originHost}: ${prompt.message}", style = MaterialTheme.typography.bodySmall)
                        if (!prompt.usernameOnly) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Username") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(username, password)
                            onDismissComplete()
                        }
                    ) {
                        Text("Sign In")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(null, null)
                            onDismissComplete()
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        is WebPromptRequest.ChoiceSelect -> {
            val selectedIds = remember(prompt.id) {
                mutableStateListOf<String>().apply {
                    addAll(prompt.options.filter { it.selected && !it.disabled }.map { it.id })
                }
            }
            AlertDialog(
                onDismissRequest = {
                    prompt.onResult(null)
                    onDismissComplete()
                },
                title = { Text(prompt.title) },
                text = {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        items(prompt.options) { option ->
                            when {
                                option.isSeparator -> HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                option.isGroupHeader -> {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                                else -> {
                                    val isChecked = option.id in selectedIds
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !option.disabled) {
                                                if (prompt.multiple) {
                                                    if (isChecked) selectedIds.remove(option.id) else selectedIds.add(option.id)
                                                } else {
                                                    prompt.onResult(listOf(option.id))
                                                    onDismissComplete()
                                                }
                                            }
                                            .padding(
                                                start = if (option.indented) 14.dp else 0.dp,
                                                top = 6.dp,
                                                bottom = 6.dp
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (prompt.multiple) {
                                            Checkbox(checked = isChecked, enabled = !option.disabled, onCheckedChange = null)
                                        } else {
                                            RadioButton(selected = isChecked, enabled = !option.disabled, onClick = null)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = option.label,
                                            color = if (option.disabled) LocalSabColors.current.subtleText
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (prompt.multiple) {
                        TextButton(
                            onClick = {
                                prompt.onResult(selectedIds.toList())
                                onDismissComplete()
                            }
                        ) {
                            Text("Apply")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(null)
                            onDismissComplete()
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        is WebPromptRequest.ColorPick -> {
            var hexValue by remember(prompt.id) { mutableStateOf(prompt.defaultValue) }
            val palette = remember(prompt.predefinedColors) {
                prompt.predefinedColors.ifEmpty {
                    listOf("#FF2E54", "#00D4FF", "#10B981", "#F59E0B", "#8B5CF6", "#FFFFFF", "#111827")
                }
            }
            AlertDialog(
                onDismissRequest = {
                    prompt.onResult(null)
                    onDismissComplete()
                },
                title = { Text(prompt.title) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            palette.take(7).forEach { hex ->
                                val parsed = runCatching {
                                    Color(android.graphics.Color.parseColor(hex))
                                }.getOrDefault(MaterialTheme.colorScheme.primary)
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(parsed)
                                        .border(
                                            width = if (hexValue.equals(hex, ignoreCase = true)) 2.dp else 0.5.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                        .clickable { hexValue = hex }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = hexValue,
                            onValueChange = { hexValue = it },
                            label = { Text("Hex color") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(hexValue)
                            onDismissComplete()
                        }
                    ) {
                        Text("Select")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(null)
                            onDismissComplete()
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        is WebPromptRequest.DateTimePick -> {
            var value by remember(prompt.id) { mutableStateOf(prompt.defaultValue) }
            AlertDialog(
                onDismissRequest = {
                    prompt.onResult(null)
                    onDismissComplete()
                },
                title = { Text(prompt.title) },
                text = {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("Format: ${prompt.modeHint}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(value)
                            onDismissComplete()
                        }
                    ) {
                        Text("Set")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(null)
                            onDismissComplete()
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        is WebPromptRequest.ExtensionInstall -> {
            var allowPrivate by remember(prompt.id) { mutableStateOf(false) }
            val sabColors = LocalSabColors.current
            AlertDialog(
                onDismissRequest = {
                    prompt.onResult(false, false)
                    onDismissComplete()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text("Add ${prompt.extensionName}?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Version ${prompt.version} • ${prompt.originHost}",
                            style = MaterialTheme.typography.bodySmall,
                            color = sabColors.subtleText
                        )
                        val allItems = (prompt.permissions + prompt.origins).distinct()
                        if (allItems.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(sabColors.chromeSurfaceElevated)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Requested permissions:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                allItems.take(8).forEach { perm ->
                                    Text("• $perm", style = MaterialTheme.typography.bodySmall, color = sabColors.subtleText)
                                }
                                if (allItems.size > 8) {
                                    Text("• +${allItems.size - 8} more", style = MaterialTheme.typography.bodySmall, color = sabColors.subtleText)
                                }
                            }
                        } else {
                            Text(
                                text = "This extension requires no special browser permissions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = sabColors.subtleText
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { allowPrivate = !allowPrivate }
                        ) {
                            Checkbox(checked = allowPrivate, onCheckedChange = { allowPrivate = it })
                            Text("Allow running in Private tabs", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(true, allowPrivate)
                            onDismissComplete()
                        }
                    ) {
                        Text("Add Extension")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(false, false)
                            onDismissComplete()
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        is WebPromptRequest.FileUpload,
        is WebPromptRequest.AndroidPermissions -> {
            // Handled via ActivityResult launchers in SabBrowserApp
        }
    }
}
