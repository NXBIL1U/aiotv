package com.itrepos.aiotv.ui.screen.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itrepos.aiotv.R
import com.itrepos.aiotv.domain.model.Quality
import kotlinx.coroutines.launch

/** All 7 region tags with friendly display labels. */
private val LIVE_TV_REGIONS = listOf(
    "US"    to "United States",
    "UK"    to "United Kingdom",
    "EN"    to "English",
    "LATAM" to "Latin America",
    "EU"    to "Europe",
    "MENA"  to "MENA / Africa / Asia",
    "OTHER" to "Other",
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var torBoxKey by rememberSaveable(state.torBoxKey) { mutableStateOf(state.torBoxKey) }
    var xtreamServer by rememberSaveable(state.xtreamServer) { mutableStateOf(state.xtreamServer) }
    var xtreamUser by rememberSaveable(state.xtreamUser) { mutableStateOf(state.xtreamUser) }
    var xtreamPass by rememberSaveable(state.xtreamPass) { mutableStateOf(state.xtreamPass) }
    var m3uUrl by rememberSaveable(state.m3uUrl) { mutableStateOf(state.m3uUrl) }
    var xmltvUrl by rememberSaveable(state.xmltvUrl) { mutableStateOf(state.xmltvUrl) }
    var newAddonUrl by remember { mutableStateOf("") }

    var torBoxKeyVisible by rememberSaveable { mutableStateOf(false) }
    var xtreamPassVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(16.dp))

        SectionHeader("TorBox")
        BringIntoViewField {
            OutlinedTextField(
                value = torBoxKey,
                onValueChange = { torBoxKey = it },
                label = { Text(stringResource(R.string.settings_torbox_key)) },
                singleLine = true,
                visualTransformation = if (torBoxKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    PasswordToggle(
                        visible = torBoxKeyVisible,
                        onToggle = { torBoxKeyVisible = !torBoxKeyVisible },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("IPTV — Xtream Codes")
        BringIntoViewField {
            OutlinedTextField(
                value = xtreamServer,
                onValueChange = { xtreamServer = it },
                label = { Text(stringResource(R.string.settings_xtream_server)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        BringIntoViewField {
            OutlinedTextField(
                value = xtreamUser,
                onValueChange = { xtreamUser = it },
                label = { Text(stringResource(R.string.settings_xtream_user)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        BringIntoViewField {
            OutlinedTextField(
                value = xtreamPass,
                onValueChange = { xtreamPass = it },
                label = { Text(stringResource(R.string.settings_xtream_pass)) },
                singleLine = true,
                visualTransformation = if (xtreamPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    PasswordToggle(
                        visible = xtreamPassVisible,
                        onToggle = { xtreamPassVisible = !xtreamPassVisible },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("IPTV — M3U")
        BringIntoViewField {
            OutlinedTextField(
                value = m3uUrl,
                onValueChange = { m3uUrl = it },
                label = { Text("M3U URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        BringIntoViewField {
            OutlinedTextField(
                value = xmltvUrl,
                onValueChange = { xmltvUrl = it },
                label = { Text("XMLTV EPG URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Stremio Addons")
        state.addonUrls.forEach { url ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(url, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = { viewModel.removeAddon(url) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }
        }
        BringIntoViewField(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newAddonUrl,
                onValueChange = { newAddonUrl = it },
                label = { Text(stringResource(R.string.settings_addon_url)) },
                placeholder = { Text("https://…/manifest.json") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    viewModel.addAddon(newAddonUrl)
                    newAddonUrl = ""
                }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                viewModel.addAddon(newAddonUrl)
                newAddonUrl = ""
            },
            enabled = newAddonUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_add_addon))
        }

        // ── Live TV — Regions ──────────────────────────────────────────────────
        Spacer(Modifier.height(16.dp))
        SectionHeader("Live TV — Regions")
        Text(
            "Select which regions to include in your Live TV channel list. Tap to toggle — changes apply immediately.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LIVE_TV_REGIONS.forEach { (tag, label) ->
                val selected = tag in state.liveRegions
                FilterChip(
                    selected = selected,
                    onClick = {
                        val updated = if (selected) state.liveRegions - tag
                                      else state.liveRegions + tag
                        viewModel.setLiveRegions(updated)
                    },
                    label = { Text(label) },
                )
            }
        }

        // ── Playback — Preferred Quality ───────────────────────────────────────
        Spacer(Modifier.height(16.dp))
        SectionHeader("Playback")
        Text(
            "Preferred quality for source ranking. 1080p is recommended for most connections.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.preferredQuality == Quality.HD_1080,
                onClick = { viewModel.setPreferredQuality(Quality.HD_1080) },
                label = { Text("1080p") },
            )
            FilterChip(
                selected = state.preferredQuality == Quality.UHD_2160,
                onClick = { viewModel.setPreferredQuality(Quality.UHD_2160) },
                label = { Text("4K") },
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.save(torBoxKey, xtreamServer, xtreamUser, xtreamPass, m3uUrl, xmltvUrl) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save")
        }

        if (state.saved) {
            Spacer(Modifier.height(8.dp))
            Text("Saved!", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PasswordToggle(visible: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (visible) "Hide password" else "Show password",
        )
    }
}

/**
 * Wraps a focusable field so it scrolls into view when it (or a descendant) gains focus.
 * Critical for D-pad navigation through this scrolling form on TV.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BringIntoViewField(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .bringIntoViewRequester(requester)
            .onFocusEvent { focusState ->
                if (focusState.hasFocus) {
                    scope.launch { requester.bringIntoView() }
                }
            }
    ) {
        content()
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
}
