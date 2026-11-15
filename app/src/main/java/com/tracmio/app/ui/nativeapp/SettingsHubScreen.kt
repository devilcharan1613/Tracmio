package com.tracmio.app.ui.nativeapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsHubScreen(
    settings: LiquidGlassSettings,
    palette: ThemePalette,
    viewModel: NativeTracmioViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    var userName by remember(settings.userName) { mutableStateOf(settings.userName) }
    var currency by remember(settings.currencySymbol) { mutableStateOf(settings.currencySymbol) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = palette.surfaceElevated)) {
                    Text("Back", color = palette.textPrimary)
                }
                Text("Settings & Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
            }
        }
        item {
            GlassCard(settings = settings) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassBadge3D("👤", palette.primary, 44.dp)
                    Text("Your profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                    OutlinedTextField(value = userName, onValueChange = { userName = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = currency, onValueChange = { currency = it }, label = { Text("Currency symbol (₹, $, €)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            viewModel.updateSettings { it.copy(userName = userName.trim().ifBlank { it.userName }, currencySymbol = currency.trim().ifBlank { it.currencySymbol }) }
                            viewModel.postStatus("Profile settings saved")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save profile", color = palette.background, fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            GlassCard(settings = settings) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Device motion effects", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                            Text("Subtle movement-responsive background", color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = settings.deviceMotionEnabled, onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(deviceMotionEnabled = enabled) } })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Round-up savings", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                            Text("Enable round-up tracking for purchases", color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = settings.roundUpEnabled, onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(roundUpEnabled = enabled) } })
                    }
                }
            }
        }
        item {
            Text("App controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
        }
        items(
            listOf(
                "themes" to ("🎨" to "Themes & Wallpapers"),
                "app_lock" to ("🔐" to "App Lock & Biometrics"),
                "permissions" to ("🛡️" to "Privacy & Permissions"),
                "reminders" to ("🔔" to "Reminders & Notifications"),
                "data_export" to ("💾" to "Backup, Restore & Export"),
                "guide" to ("📘" to "Help & App Guide")
            )
        ) { (route, item) ->
            GlassCard(settings = settings, onClick = { onNavigate(route) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GlassBadge3D(item.first, palette.primary, 40.dp)
                    Text(item.second, color = palette.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("›", color = palette.primary, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
