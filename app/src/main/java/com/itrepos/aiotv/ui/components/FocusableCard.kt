package com.itrepos.aiotv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.itrepos.aiotv.ui.theme.AccentPrimary
import com.itrepos.aiotv.ui.theme.Outline
import com.itrepos.aiotv.ui.theme.SurfaceCard

@Composable
fun FocusableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(180),
        label = "scale"
    )
    val elevation by animateFloatAsState(
        targetValue = if (focused) 16f else 2f,
        animationSpec = tween(180),
        label = "elevation"
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .graphicsLayer { shadowElevation = elevation }
            .focusable(interactionSource = interactionSource),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = if (focused) BorderStroke(2.dp, AccentPrimary) else BorderStroke(1.dp, Outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box { content() }
    }
}
