package com.itrepos.aiotv.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itrepos.aiotv.R

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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(16.dp))

        SectionHeader("TorBox")
        OutlinedTextField(
            value = torBoxKey,
            onValueChange = { torBoxKey = it },
            label = { Text(stringResource(R.string.settings_torbox_key)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        SectionHeader("IPTV — Xtream Codes")
        OutlinedTextField(value = xtreamServer, onValueChange = { xtreamServer = it }, label = { Text(stringResource(R.string.settings_xtream_server)) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = xtreamUser, onValueChange = { xtreamUser = it }, label = { Text(stringResource(R.string.settings_xtream_user)) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = xtreamPass, onValueChange = { xtreamPass = it }, label = { Text(stringResource(R.string.settings_xtream_pass)) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(16.dp))
        SectionHeader("IPTV — M3U")
        OutlinedTextField(value = m3uUrl, onValueChange = { m3uUrl = it }, label = { Text("M3U URL") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = xmltvUrl, onValueChange = { xmltvUrl = it }, label = { Text("XMLTV EPG URL") }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(16.dp))
        SectionHeader("Stremio Addons")
        state.addonUrls.forEach { url ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(url, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = { viewModel.removeAddon(url) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = newAddonUrl,
                onValueChange = { newAddonUrl = it },
                label = { Text(stringResource(R.string.settings_addon_url)) },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                viewModel.addAddon(newAddonUrl)
                newAddonUrl = ""
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_add_addon))
            }
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
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Divider(Modifier.padding(vertical = 4.dp))
}
