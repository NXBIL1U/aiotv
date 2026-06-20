package com.itrepos.aiotv.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itrepos.aiotv.R
import com.itrepos.aiotv.ui.navigation.Screen
import com.itrepos.aiotv.ui.theme.AccentPrimary
import com.itrepos.aiotv.ui.theme.Background
import com.itrepos.aiotv.ui.theme.SurfaceCard

data class NavItem(
    val screen: Screen,
    val labelRes: Int,
    val icon: ImageVector,
)

val navItems = listOf(
    NavItem(Screen.Home, R.string.nav_home, Icons.Default.Home),
    NavItem(Screen.Search, R.string.nav_search, Icons.Default.Search),
    NavItem(Screen.Guide, R.string.nav_guide, Icons.Default.VideoLibrary),
    NavItem(Screen.Live, R.string.nav_live, Icons.Default.LiveTv),
    NavItem(Screen.Watchlist, R.string.nav_watchlist, Icons.Default.Bookmarks),
    NavItem(Screen.Addons, R.string.nav_addons, Icons.Default.Apps),
    NavItem(Screen.Settings, R.string.nav_settings, Icons.Default.Settings),
)

@Composable
fun TvNavRail(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(
        targetValue = if (expanded) 200.dp else 56.dp,
        animationSpec = tween(200),
        label = "railWidth",
    )

    Column(
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight()
            .background(SurfaceCard.copy(alpha = 0.9f))
            .selectableGroup()
    ) {
        Spacer(Modifier.height(24.dp))
        navItems.forEach { item ->
            val selected = selectedRoute == item.screen.route
            val interactionSource = remember { MutableInteractionSource() }
            val focused by interactionSource.collectIsFocusedAsState()
            if (focused && !expanded) expanded = true

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(if (selected) AccentPrimary.copy(alpha = 0.15f) else Color.Transparent)
                    .focusable(interactionSource = interactionSource)
                    .padding(horizontal = 16.dp),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = stringResource(item.labelRes),
                    tint = if (selected || focused) AccentPrimary else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp),
                )
                if (expanded) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(item.labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected || focused) AccentPrimary else Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
fun PhoneBottomNav(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shortItems = navItems.take(5)
    NavigationBar(
        modifier = modifier,
        containerColor = SurfaceCard,
    ) {
        shortItems.forEach { item ->
            NavigationBarItem(
                selected = selectedRoute == item.screen.route,
                onClick = { onNavigate(item.screen.route) },
                icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                label = { Text(stringResource(item.labelRes), style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}
