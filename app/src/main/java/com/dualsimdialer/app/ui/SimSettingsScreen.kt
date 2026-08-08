package com.dualsimdialer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.dualsimdialer.app.data.SimPreferencesRepository
import com.dualsimdialer.app.model.SimProfile
import com.dualsimdialer.app.util.ColorUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SimSettingsScreen(
    profiles: List<SimProfile>,
    preferences: SimPreferencesRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    Column {
        TopAppBar(
            title = { Text("SIM settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
        )
        if (profiles.isEmpty()) {
            EmptyState("No active SIM accounts", "SIM aliases and colors become available when Telecom exposes a voice-capable SIM.", Modifier.padding(20.dp))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("These aliases are local to DualSimDialer. Existing logs resolve their current alias whenever they are shown.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(profiles, key = { it.key.serialized }) { profile ->
                    SimEditorCard(
                        profile = profile,
                        expanded = expandedKey == profile.key.serialized,
                        onExpand = { expandedKey = if (expandedKey == profile.key.serialized) null else profile.key.serialized },
                        onAliasChanged = { alias -> scope.launch { preferences.updateAlias(profile.key, alias) } },
                        onColorChanged = { color -> scope.launch { preferences.updateColor(profile.key, color) } },
                        onReset = { scope.launch { preferences.reset(profile.key) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun SimEditorCard(
    profile: SimProfile,
    expanded: Boolean,
    onExpand: () -> Unit,
    onAliasChanged: (String) -> Unit,
    onColorChanged: (Int) -> Unit,
    onReset: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onExpand),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(42.dp), shape = androidx.compose.foundation.shape.CircleShape, color = Color(profile.colorArgb)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${profile.slotIndex + 1}", color = Color(ColorUtils.foregroundFor(profile.colorArgb)), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(profile.systemLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (expanded) "Hide" else "Edit", color = MaterialTheme.colorScheme.primary)
            }
            Surface(
                Modifier.fillMaxWidth().height(52.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = Color(profile.colorArgb),
            ) {
                Box(contentAlignment = Alignment.CenterStart) {
                    Text(
                        "Preview · ${profile.displayName}",
                        Modifier.padding(horizontal = 16.dp),
                        color = Color(ColorUtils.foregroundFor(profile.colorArgb)),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (expanded) {
                SimEditor(
                    profile = profile,
                    onAliasChanged = onAliasChanged,
                    onColorChanged = onColorChanged,
                    onReset = onReset,
                )
            }
        }
    }
}

@Composable
private fun SimEditor(
    profile: SimProfile,
    onAliasChanged: (String) -> Unit,
    onColorChanged: (Int) -> Unit,
    onReset: () -> Unit,
) {
    var alias by remember(profile.key.serialized, profile.alias) { mutableStateOf(profile.alias) }
    var hex by remember(profile.key.serialized, profile.colorArgb) { mutableStateOf(ColorUtils.argbToHex(profile.colorArgb)) }
    val hsv = remember(profile.key.serialized, profile.colorArgb) { ColorUtils.argbToHsv(profile.colorArgb) }
    var hue by remember(profile.key.serialized, profile.colorArgb) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(profile.key.serialized, profile.colorArgb) { mutableFloatStateOf(hsv[1]) }
    var value by remember(profile.key.serialized, profile.colorArgb) { mutableFloatStateOf(hsv[2]) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = alias,
            onValueChange = { alias = it.take(24) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Alias") },
            supportingText = { Text("${alias.length}/24 · default is SIM ${profile.slotIndex + 1}") },
            trailingIcon = {
                IconButton(onClick = { onAliasChanged(alias) }, modifier = Modifier.semantics { contentDescription = "Save alias" }) {
                    Icon(Icons.Default.Check, contentDescription = null)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAliasChanged(alias) }),
        )
        Text("Accessible preset colors", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ColorUtils.presetArgb.forEach { color ->
                Surface(
                    Modifier.size(42.dp).clickable {
                        val next = color
                        onColorChanged(next)
                    }.semantics { contentDescription = "Use color ${ColorUtils.argbToHex(color)}" },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color(color),
                    border = if (color == profile.colorArgb) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (color == profile.colorArgb) Text("✓", color = Color(ColorUtils.foregroundFor(color)))
                    }
                }
            }
        }
        Text("Custom HSV", style = MaterialTheme.typography.labelLarge)
        Slider(value = hue, onValueChange = { hue = it; onColorChanged(ColorUtils.hsvToArgb(hue, saturation, value)) }, valueRange = 0f..360f, modifier = Modifier.semantics { contentDescription = "Hue" })
        Slider(value = saturation, onValueChange = { saturation = it; onColorChanged(ColorUtils.hsvToArgb(hue, saturation, value)) }, modifier = Modifier.semantics { contentDescription = "Saturation" })
        Slider(value = value, onValueChange = { value = it; onColorChanged(ColorUtils.hsvToArgb(hue, saturation, value)) }, modifier = Modifier.semantics { contentDescription = "Brightness" })
        OutlinedTextField(
            value = hex,
            onValueChange = { hex = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Custom color (HSV / hex)") },
            trailingIcon = {
                IconButton(
                    onClick = { ColorUtils.parseHex(hex)?.let(onColorChanged) },
                    modifier = Modifier.semantics { contentDescription = "Apply hex color" },
                ) { Icon(Icons.Default.Check, contentDescription = null) }
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onReset) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Reset")
            }
        }
    }
}
