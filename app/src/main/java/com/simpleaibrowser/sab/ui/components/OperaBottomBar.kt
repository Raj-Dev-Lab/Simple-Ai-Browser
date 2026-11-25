package com.simpleaibrowser.sab.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.simpleaibrowser.sab.core.browser.TabUiState
import com.simpleaibrowser.sab.ui.theme.LocalSabColors
import com.simpleaibrowser.sab.ui.theme.SpaceGroteskFamily

/**
 * Opera Mobile-inspired 5-slot thumb-zone bottom navigation bar:
 * [Back] [Home] [Center SAB Fast Action Orb] [Tabs Counter] [Opera Menu]
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OperaBottomBar(
    activeTab: TabUiState,
    totalTabCount: Int,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onFastActionClick: () -> Unit,
    onFastActionLongPress: () -> Unit,
    onOpenTabGallery: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sabColors = LocalSabColors.current
    val barBg = if (activeTab.isPrivate) sabColors.privateSurface else sabColors.chromeBar
    val primaryAccent = if (activeTab.isPrivate) sabColors.privateAccent else MaterialTheme.colorScheme.primary

    Surface(
        color = barBg,
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(
                color = sabColors.chromeBorder.copy(alpha = 0.55f),
                thickness = 0.6.dp
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Slot 1: Back
                val canNavigateBack = activeTab.canGoBack || !activeTab.isStartPage
                IconButton(
                    onClick = onBack,
                    enabled = canNavigateBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("bottom_nav_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.nav_back),
                        tint = if (canNavigateBack) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            sabColors.subtleText.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Slot 2: Speed Dial Home
                IconButton(
                    onClick = onHome,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("bottom_nav_home")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = stringResource(R.string.nav_home),
                        tint = if (activeTab.isStartPage) {
                            primaryAccent
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Slot 3: Center Opera Fast Action Orb (Search / New Tab on long-press)
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    primaryAccent,
                                    primaryAccent.copy(alpha = 0.78f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .combinedClickable(
                            onClick = onFastActionClick,
                            onLongClick = onFastActionLongPress
                        )
                        .testTag("bottom_nav_fab_search"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.nav_search),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Slot 4: Tab Gallery Counter Badge
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = onOpenTabGallery,
                            onLongClick = onFastActionLongPress
                        )
                        .testTag("bottom_nav_tabs"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .border(
                                width = 1.8.dp,
                                color = if (activeTab.isPrivate) sabColors.privateAccent
                                else MaterialTheme.colorScheme.onSurface,
                                shape = RoundedCornerShape(7.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (activeTab.isPrivate) {
                            Icon(
                                imageVector = Icons.Filled.VisibilityOff,
                                contentDescription = stringResource(R.string.nav_tabs),
                                tint = sabColors.privateAccent,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Text(
                                text = if (totalTabCount > 99) "∞" else totalTabCount.toString(),
                                fontFamily = SpaceGroteskFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Slot 5: Opera Control Menu
                IconButton(
                    onClick = onOpenMenu,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("bottom_nav_menu")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.nav_menu),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }
        }
    }
}
