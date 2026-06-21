package com.itrepos.aiotv.ui.screen.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.itrepos.aiotv.ui.theme.AccentPrimary
import com.itrepos.aiotv.ui.theme.SurfaceCard
import com.itrepos.aiotv.ui.theme.SurfaceElevated

/** All region tags in display order. */
private val ALL_REGIONS = listOf(
    "US"    to "United States",
    "UK"    to "United Kingdom",
    "EN"    to "English",
    "LATAM" to "Latin America",
    "EU"    to "Europe",
    "MENA"  to "MENA / Africa / Asia",
    "OTHER" to "Other",
)

/**
 * A Material3 ModalBottomSheet for choosing one or more Live TV regions.
 *
 * @param selectedRegions the currently active region set (from [LiveTvState.selectedRegions]).
 * @param onConfirm       called with the new set when the user taps "Apply".
 * @param onDismiss       called when the sheet is dismissed without confirming.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionPicker(
    selectedRegions: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local copy of selection so changes are only committed on "Apply".
    var localSelection by remember(selectedRegions) { mutableStateOf(selectedRegions) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceCard,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Select Regions",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            ALL_REGIONS.forEach { (tag, label) ->
                val selected = tag in localSelection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            localSelection = if (selected) {
                                localSelection - tag
                            } else {
                                localSelection + tag
                            }
                        }
                        .background(
                            if (selected) AccentPrimary.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (selected) Icons.Filled.CheckBox
                                      else Icons.Filled.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = if (selected) AccentPrimary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) AccentPrimary else Color.White.copy(alpha = 0.85f),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        // Ensure at least one region is always selected.
                        val effective = if (localSelection.isEmpty()) selectedRegions else localSelection
                        onConfirm(effective)
                    },
                ) {
                    Text("Apply")
                }
            }
        }
    }
}
