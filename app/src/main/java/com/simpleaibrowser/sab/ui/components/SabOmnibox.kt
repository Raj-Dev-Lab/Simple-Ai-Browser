package com.simpleaibrowser.sab.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.simpleaibrowser.sab.core.browser.FindInPageResult
import com.simpleaibrowser.sab.core.browser.SearchEngine
import com.simpleaibrowser.sab.core.browser.SecurityState
import com.simpleaibrowser.sab.core.browser.TabUiState
import com.simpleaibrowser.sab.core.browser.UrlResolver
import com.simpleaibrowser.sab.ui.theme.JetBrainsMonoFamily
import com.simpleaibrowser.sab.ui.theme.LocalSabColors
import com.simpleaibrowser.sab.ui.viewmodel.OmniboxSuggestion

@Composable
fun SabTopAddressBar(
    tab: TabUiState,
    adBlockEnabled: Boolean,
    onAddressClick: () -> Unit,
    onShieldClick: () -> Unit,
    onReloadOrStop: () -> Unit,
    onSwipeTab: (step: Int) -> Unit = {},
    showTopDivider: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sabColors = LocalSabColors.current
    val surfaceColor = if (tab.isPrivate) sabColors.privateSurface else sabColors.chromeBar
    val pillColor = if (tab.isPrivate) sabColors.privateSurface.copy(alpha = 0.85f) else sabColors.chromeSurfaceElevated
    var accumulatedDrag by remember(tab.id) { mutableFloatStateOf(0f) }

    Surface(
        color = surfaceColor,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(tab.id) {
                detectHorizontalDragGestures(
                    onDragStart = { accumulatedDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> accumulatedDrag += dragAmount },
                    onDragEnd = {
                        if (accumulatedDrag > 90f) {
                            onSwipeTab(-1)
                        } else if (accumulatedDrag < -90f) {
                            onSwipeTab(1)
                        }
                        accumulatedDrag = 0f
                    }
                )
            }
    ) {
        Column {
            if (showTopDivider) {
                if (tab.isLoading) {
                    LinearProgressIndicator(
                        progress = { (tab.progress / 100f).coerceIn(0.05f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = if (tab.isPrivate) sabColors.privateAccent else MaterialTheme.colorScheme.primary,
                        trackColor = sabColors.chromeBorder.copy(alpha = 0.35f)
                    )
                } else {
                    HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.45f), thickness = 0.5.dp)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Privacy Shield Pill
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(pillColor)
                        .border(1.dp, sabColors.chromeBorder, CircleShape)
                        .clickable(onClick = onShieldClick)
                        .testTag("shield_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (tab.isPrivate) Icons.Filled.VisibilityOff else Icons.Filled.Shield,
                        contentDescription = stringResource(R.string.shield_title),
                        tint = when {
                            tab.isPrivate -> sabColors.privateAccent
                            adBlockEnabled -> sabColors.shieldGreen
                            else -> sabColors.subtleText
                        },
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Floating Pill Address Display (swipe horizontally to switch tabs)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(pillColor)
                        .border(
                            width = 1.dp,
                            color = if (tab.isPrivate) sabColors.privateAccent.copy(alpha = 0.45f)
                            else sabColors.chromeBorder,
                            shape = RoundedCornerShape(22.dp)
                        )
                        .clickable(onClick = onAddressClick)
                        .padding(horizontal = 14.dp)
                        .testTag("omnibox_display"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when (tab.securityState) {
                            SecurityState.SECURE -> Icons.Filled.Lock
                            SecurityState.INSECURE -> Icons.Filled.WarningAmber
                            else -> Icons.Filled.Search
                        },
                        contentDescription = "Connection security",
                        tint = when (tab.securityState) {
                            SecurityState.SECURE -> sabColors.shieldGreen
                            SecurityState.INSECURE -> MaterialTheme.colorScheme.error
                            else -> sabColors.subtleText
                        },
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (tab.isStartPage) {
                            stringResource(
                                if (tab.isPrivate) R.string.omnibox_private_placeholder
                                else R.string.omnibox_placeholder
                            )
                        } else {
                            UrlResolver.extractHost(tab.url)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (tab.isStartPage) sabColors.subtleText
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (tab.textZoomPercent != 100 && !tab.isStartPage) {
                        Text(
                            text = "${tab.textZoomPercent}%",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    if (tab.blockedTrackersCount > 0 && adBlockEnabled) {
                        Text(
                            text = "${tab.blockedTrackersCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = sabColors.shieldGreen,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(sabColors.shieldGreen.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Reload / Stop button
                if (!tab.isStartPage) {
                    IconButton(
                        onClick = onReloadOrStop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(pillColor)
                            .testTag("reload_stop_button")
                    ) {
                        Icon(
                            imageVector = if (tab.isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                            contentDescription = stringResource(
                                if (tab.isLoading) R.string.action_stop else R.string.action_reload
                            ),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            if (!showTopDivider) {
                if (tab.isLoading) {
                    LinearProgressIndicator(
                        progress = { (tab.progress / 100f).coerceIn(0.05f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = if (tab.isPrivate) sabColors.privateAccent else MaterialTheme.colorScheme.primary,
                        trackColor = sabColors.chromeBorder.copy(alpha = 0.35f)
                    )
                } else {
                    HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.45f), thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun SabOmniboxEditorOverlay(
    query: String,
    activePageUrl: String?,
    searchEngine: SearchEngine,
    isPrivate: Boolean,
    suggestions: List<OmniboxSuggestion>,
    onQueryChange: (String) -> Unit,
    onSelectSearchEngine: (SearchEngine) -> Unit,
    onCopyUrl: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sabColors = LocalSabColors.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isPrivate) sabColors.privateSurface else sabColors.chromeBar)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(sabColors.chromeSurfaceElevated)
                    .border(
                        width = 1.5.dp,
                        color = if (isPrivate) sabColors.privateAccent else MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = searchEngine.badgeChar,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search ${searchEngine.displayName} or type URL",
                            style = MaterialTheme.typography.bodyMedium,
                            color = sabColors.subtleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { onSubmit(query) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("omnibox_text_input")
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_clear),
                            tint = sabColors.subtleText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("omnibox_cancel_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close address bar",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // One-Tap Search Engine Switcher Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isPrivate) sabColors.privateSurface else sabColors.chromeBar)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchEngine.entries.forEach { engine ->
                val selected = engine == searchEngine
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else sabColors.chromeSurfaceElevated
                        )
                        .border(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else sabColors.chromeBorder,
                            RoundedCornerShape(50)
                        )
                        .clickable { onSelectSearchEngine(engine) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = engine.badgeChar,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) MaterialTheme.colorScheme.primary else sabColors.subtleText
                    )
                    Text(
                        text = engine.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else sabColors.subtleText
                    )
                }
            }
        }

        // Quick Copy Active URL Row
        if (!activePageUrl.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(sabColors.chromeSurfaceElevated.copy(alpha = 0.55f))
                    .clickable { onCopyUrl(activePageUrl) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy current URL",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = "Copy link: ${UrlResolver.extractHost(activePageUrl)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "COPY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(vertical = 6.dp)
        ) {
            if (query.isNotBlank()) {
                item(key = "direct_search") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSubmit(query) }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("omnibox_go_action"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = query,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Go or search with ${searchEngine.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = sabColors.subtleText
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Navigate",
                            tint = sabColors.subtleText
                        )
                    }
                }
            }

            items(suggestions, key = { it.url }) { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSubmit(suggestion.url) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = when (suggestion.source) {
                            "Bookmark" -> Icons.Filled.Bookmark
                            "Speed Dial" -> Icons.Filled.Star
                            "History" -> Icons.Filled.History
                            else -> Icons.Filled.Public
                        },
                        contentDescription = suggestion.source,
                        tint = sabColors.subtleText,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = suggestion.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = suggestion.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = sabColors.subtleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FindInPageBar(
    visible: Boolean,
    query: String,
    findResult: FindInPageResult,
    onQueryChange: (String) -> Unit,
    onFindPrevious: () -> Unit,
    onFindNext: () -> Unit,
    onClose: () -> Unit
) {
    val sabColors = LocalSabColors.current
    AnimatedVisibility(visible = visible) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(sabColors.chromeBar)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(sabColors.chromeSurfaceElevated)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.action_find_in_page),
                            style = MaterialTheme.typography.bodyMedium,
                            color = sabColors.subtleText
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onFindNext() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("find_in_page_input")
                    )
                }
                if (query.isNotEmpty()) {
                    Text(
                        text = "${findResult.currentMatch}/${findResult.totalMatches}",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = if (findResult.totalMatches > 0) MaterialTheme.colorScheme.primary
                        else sabColors.subtleText,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            IconButton(
                onClick = onFindPrevious,
                enabled = query.isNotBlank(),
                modifier = Modifier.size(38.dp).testTag("find_prev_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Previous match",
                    tint = if (query.isNotBlank()) MaterialTheme.colorScheme.onSurface
                    else sabColors.subtleText.copy(alpha = 0.4f)
                )
            }
            IconButton(
                onClick = onFindNext,
                enabled = query.isNotBlank(),
                modifier = Modifier.size(38.dp).testTag("find_next_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Next match",
                    tint = if (query.isNotBlank()) MaterialTheme.colorScheme.onSurface
                    else sabColors.subtleText.copy(alpha = 0.4f)
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close find in page",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
