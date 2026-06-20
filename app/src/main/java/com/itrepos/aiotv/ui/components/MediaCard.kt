package com.itrepos.aiotv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.itrepos.aiotv.ui.theme.Background

@Composable
fun MediaCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 160.dp,
    aspectRatio: Float = 2f / 3f,
    badge: String? = null,
    progress: Float? = null,
) {
    FocusableCard(
        onClick = onClick,
        modifier = modifier.width(width),
    ) {
        Column {
            Box(Modifier.aspectRatio(aspectRatio)) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0.6f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.7f)
                            )
                        )
                )
                badge?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color(0xFF00C853), MaterialTheme.shapes.small)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                progress?.let { p ->
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.3f))
                        )
                        Box(
                            Modifier
                                .fillMaxWidth(p.coerceIn(0f, 1f))
                                .background(Color(0xFF6C63FF))
                        )
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(6.dp)
            )
        }
    }
}
