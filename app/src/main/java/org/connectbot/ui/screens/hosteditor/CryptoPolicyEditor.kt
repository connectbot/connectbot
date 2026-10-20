/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.ui.screens.hosteditor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.connectbot.R
import org.connectbot.data.entity.HostCryptoConfig
import org.connectbot.sshlib.AlgorithmCategory
import org.connectbot.sshlib.SshAlgorithm
import org.connectbot.sshlib.SshCryptoPolicy
import org.connectbot.util.cryptoPolicyTitleRes
import org.connectbot.util.titleRes
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val ALGORITHM_SETTLE_DELAY_MILLIS = 1200L
private const val ALGORITHM_EXPANSION_DURATION_MILLIS = 200
private const val ALGORITHM_PLACEMENT_DURATION_MILLIS = 300

@Composable
internal fun CryptoPolicyEditor(
    config: HostCryptoConfig,
    catalog: Map<AlgorithmCategory, List<SshAlgorithm>>,
    defaults: Map<AlgorithmCategory, List<String>>,
    onChange: (HostCryptoConfig) -> Unit,
    onResolve: suspend (HostCryptoConfig) -> SshCryptoPolicy?,
    defaultMinimumRsaBits: Int? = null,
    defaultMinimumDhBits: Int? = null,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val policies = listOf("DEFAULT", "FUTURE", "FIPS", "LEGACY", "CUSTOM")
    val resolve by rememberUpdatedState(onResolve)
    var selectedPolicy by remember { mutableStateOf<SshCryptoPolicy?>(null) }
    LaunchedEffect(config, catalog) {
        selectedPolicy = try {
            resolve(config)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            null
        }
    }
    Column {
        PolicyChoice(
            value = config.preset,
            options = policies,
            label = stringResource(R.string.crypto_policy_title),
            display = { policyLabel(it) },
            subtitle = { policySubtitle(it) },
            onSelect = { onChange(config.copy(preset = it)) },
        )
        Text(
            stringResource(
                when (config.preset) {
                    "FUTURE" -> R.string.crypto_policy_future_summary
                    "FIPS" -> R.string.crypto_policy_fips_summary
                    "LEGACY" -> R.string.crypto_policy_legacy_summary
                    "CUSTOM" -> R.string.crypto_policy_custom_summary
                    else -> R.string.crypto_policy_default_summary
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        // Reserve the summary line while resolution runs so the controls below stay in place.
        Text(
            text = selectedPolicy?.let { stringResource(R.string.crypto_minimum_values, it.minimumRsaBits, it.minimumDhBits) }.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            minLines = 1,
        )
        if (config.preset == "CUSTOM") {
            CryptoMinimumField(config.minimumRsaBits, R.string.crypto_minimum_rsa, false, defaultMinimumRsaBits) { onChange(config.copy(minimumRsaBits = it)) }
            CryptoMinimumField(config.minimumDhBits, R.string.crypto_minimum_dh, true, defaultMinimumDhBits) { onChange(config.copy(minimumDhBits = it)) }
            AlgorithmCategory.entries.forEach { category ->
                TextButton(onClick = { editing = category.name }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(stringResource(category.titleRes()))
                        Text(config.expression(category) ?: stringResource(R.string.crypto_mode_default), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        editing?.let { name ->
            val category = AlgorithmCategory.valueOf(name)
            AlgorithmEditorDialog(
                category = category,
                config = config,
                catalog = catalog[category].orEmpty(),
                defaults = defaults[category].orEmpty(),
                onResolve = onResolve,
                onDismiss = { editing = null },
                onApply = {
                    onChange(config.withExpression(category, it))
                    editing = null
                },
            )
        }
    }
}

@Composable
private fun policyLabel(policy: String): String = cryptoPolicyTitleRes(policy)?.let { stringResource(it) } ?: policy

@Composable
private fun policySubtitle(policy: String): String = stringResource(
    when (policy) {
        "FUTURE" -> R.string.crypto_policy_future_subtitle
        "FIPS" -> R.string.crypto_policy_fips_subtitle
        "LEGACY" -> R.string.crypto_policy_legacy_subtitle
        "CUSTOM" -> R.string.crypto_policy_custom_subtitle
        else -> R.string.crypto_policy_default_subtitle
    },
)

@Composable
private fun CryptoMinimumField(value: Int?, label: Int, dh: Boolean, inheritedMinimum: Int?, onChange: (Int?) -> Unit) {
    var text by rememberSaveable { mutableStateOf(value?.toString().orEmpty()) }
    val parsed = text.toIntOrNull()
    val invalid = text.isNotEmpty() && (parsed == null || parsed < 1024 || (dh && parsed > 8192))
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(if (it.isEmpty()) null else it.toIntOrNull() ?: -1)
        },
        label = { Text(stringResource(label)) },
        placeholder = { Text(if (inheritedMinimum == null) stringResource(R.string.crypto_mode_default) else stringResource(R.string.crypto_inherit_bits, inheritedMinimum)) },
        supportingText = { Text(stringResource(if (invalid) R.string.crypto_minimum_invalid else R.string.crypto_minimum_summary)) },
        isError = invalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PolicyChoice(value: String, options: List<String>, label: String, display: @Composable (String) -> String, subtitle: (@Composable (String) -> String)? = null, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = display(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(display(option))
                        subtitle?.let {
                            Text(it(option), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }, onClick = {
                    onSelect(option)
                    expanded = false
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlgorithmEditorDialog(
    category: AlgorithmCategory,
    config: HostCryptoConfig,
    catalog: List<SshAlgorithm>,
    defaults: List<String>,
    onResolve: suspend (HostCryptoConfig) -> SshCryptoPolicy?,
    onDismiss: () -> Unit,
    onApply: (String?) -> Unit,
) {
    val original = config.expression(category).orEmpty()
    val initialRules = remember(original) { runCatching { algorithmRules(original) }.getOrDefault(emptyList()) }
    var mode by rememberSaveable {
        mutableStateOf(
            if (original.isBlank()) {
                AlgorithmEditMode.DEFAULT
            } else if (initialRules.firstOrNull()?.operation == '=') {
                AlgorithmEditMode.EXACT
            } else {
                AlgorithmEditMode.DELTA
            },
        )
    }
    val delta = rememberTextFieldState(initialText = if (initialRules.firstOrNull()?.operation != '=') original else "")
    val exact = rememberTextFieldState(initialText = if (initialRules.firstOrNull()?.operation == '=') original else "")
    var exactInitialized by rememberSaveable { mutableStateOf(initialRules.firstOrNull()?.operation == '=') }
    val expression = when (mode) {
        AlgorithmEditMode.DEFAULT -> ""
        AlgorithmEditMode.DELTA -> delta.text.toString()
        AlgorithmEditMode.EXACT -> exact.text.toString()
    }
    val syntax = remember(expression) { runCatching { algorithmRules(expression) } }
    val rules = syntax.getOrDefault(emptyList())
    val valid = syntax.isSuccess && (mode != AlgorithmEditMode.EXACT || expression.isNotBlank()) &&
        (rules.isEmpty() || (mode == AlgorithmEditMode.EXACT) == (rules.first().operation == '='))
    val resolve by rememberUpdatedState(onResolve)
    var preview by remember { mutableStateOf<SshCryptoPolicy?>(null) }
    var showEffectiveOrder by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(expression, valid, config.minimumDhBits, config.minimumRsaBits, catalog) {
        preview = if (valid) {
            try {
                resolve(config.withExpression(category, expression))
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                null
            }
        } else {
            null
        }
    }
    var expandedAlgorithm by rememberSaveable { mutableStateOf<String?>(null) }
    var displayedRules by remember { mutableStateOf(initialRules) }
    var selectionRevision by remember { mutableIntStateOf(0) }
    var settleRequested by remember { mutableStateOf(false) }
    var draggingOperation by remember { mutableStateOf<Char?>(null) }
    LaunchedEffect(expression, mode, selectionRevision, draggingOperation) {
        if (valid && draggingOperation == null && (rules != displayedRules || settleRequested)) {
            // Keep the row under the user's finger while they choose a state.
            delay(ALGORITHM_SETTLE_DELAY_MILLIS)
            if (expandedAlgorithm != null) {
                expandedAlgorithm = null
                delay(ALGORITHM_EXPANSION_DURATION_MILLIS.toLong())
            }
            displayedRules = rules
            settleRequested = false
        }
    }
    fun change(value: String, immediately: Boolean = false) {
        val target = if (mode == AlgorithmEditMode.DELTA) delta else exact
        target.edit {
            replace(0, length, value)
            selection = TextRange(value.length)
        }
        if (immediately) displayedRules = algorithmRules(value)
    }
    val names = algorithmDisplayOrder(catalog.map { it.name }, displayedRules)
    val list = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderable = rememberReorderableLazyListState(list) { from, to ->
        if (valid) {
            val reordered = moveAlgorithmRule(expression, from.key as String, to.key as String)
            if (reordered != expression) {
                change(reordered, immediately = true)
                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text(stringResource(category.titleRes()), style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) }
                    TextButton(onClick = { onApply(expression.takeIf { it.isNotBlank() }) }, enabled = valid) { Text(stringResource(R.string.crypto_apply)) }
                }
                LazyColumn(state = list, modifier = Modifier.weight(1f).testTag("algorithm-list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "mode") {
                        PolicyChoice(mode.name, AlgorithmEditMode.entries.map { it.name }, stringResource(R.string.crypto_mode_title), { name ->
                            stringResource(
                                when (AlgorithmEditMode.valueOf(name)) {
                                    AlgorithmEditMode.DEFAULT -> R.string.crypto_mode_default
                                    AlgorithmEditMode.DELTA -> R.string.crypto_mode_delta
                                    AlgorithmEditMode.EXACT -> R.string.crypto_mode_exact
                                },
                            )
                        }) { selected ->
                            val next = AlgorithmEditMode.valueOf(selected)
                            if (next == AlgorithmEditMode.EXACT && !exactInitialized) {
                                val seeded = intendedAlgorithmOrder(defaults, rules).joinToString(",")
                                exact.edit {
                                    replace(0, length, seeded)
                                    selection = TextRange(seeded.length)
                                }
                                exactInitialized = true
                            }
                            mode = next
                        }
                    }
                    if (mode != AlgorithmEditMode.DEFAULT) {
                        item(key = "expression") {
                            OutlinedTextField(
                                state = if (mode == AlgorithmEditMode.DELTA) delta else exact,
                                label = { Text(stringResource(R.string.crypto_expression)) },
                                supportingText = { Text(stringResource(if (valid) R.string.crypto_expression_help else R.string.crypto_expression_invalid)) },
                                isError = !valid,
                                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (mode == AlgorithmEditMode.DELTA) {
                        item(key = "legend") { Text(stringResource(R.string.crypto_states_help), style = MaterialTheme.typography.bodySmall) }
                    }
                    item(key = "preview") {
                        TextButton(onClick = { showEffectiveOrder = !showEffectiveOrder }) {
                            Text(stringResource(if (showEffectiveOrder) R.string.crypto_hide_effective_order else R.string.crypto_show_effective_order))
                        }
                        AnimatedVisibility(showEffectiveOrder) {
                            Text(preview?.algorithms?.get(category)?.joinToString(", ") ?: defaults.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (mode != AlgorithmEditMode.DEFAULT) {
                        items(names, key = { it }) { name ->
                            val rule = rules.firstOrNull { it.name == name }
                            val algorithm = catalog.firstOrNull { it.name == name }
                            val toggleStateChoices: () -> Unit = {
                                expandedAlgorithm = if (expandedAlgorithm == name) null else name
                                selectionRevision++
                            }
                            val draggable = valid && (rule?.operation in listOf('=', '^', '+'))
                            val acceptsDrag = draggable && (draggingOperation == null || draggingOperation == rule?.operation)
                            val group = rules.filter { it.operation == rule?.operation }.map { it.name }
                            val index = group.indexOf(name)
                            val up = stringResource(R.string.automation_move_up)
                            val down = stringResource(R.string.automation_move_down)
                            val drag = stringResource(R.string.automation_drag)
                            ReorderableItem(
                                reorderable,
                                key = name,
                                enabled = acceptsDrag,
                                animateItemModifier = Modifier.animateItem(placementSpec = tween(ALGORITHM_PLACEMENT_DURATION_MILLIS)),
                            ) { _ ->
                                Column(
                                    Modifier.fillMaxWidth().testTag("algorithm-row-$name").semantics {
                                        if (draggable) {
                                            customActions = listOf(
                                                CustomAccessibilityAction(up) {
                                                    group.getOrNull(index - 1)?.let { change(moveAlgorithmRule(expression, name, it), immediately = true) }
                                                    true
                                                },
                                                CustomAccessibilityAction(down) {
                                                    group.getOrNull(index + 1)?.let { change(moveAlgorithmRule(expression, name, it), immediately = true) }
                                                    true
                                                },
                                            )
                                        }
                                    },
                                ) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (mode == AlgorithmEditMode.EXACT) {
                                            Checkbox(checked = rule != null, onCheckedChange = { change(setAlgorithmRule(expression, name, if (it) '=' else null), immediately = true) }, enabled = syntax.isSuccess, modifier = Modifier.semantics { contentDescription = name })
                                        } else {
                                            val stateLabel = stringResource(algorithmOperationTitleRes(rule?.operation))
                                            val changeLabel = stringResource(R.string.crypto_change_algorithm_state, name)
                                            OutlinedButton(
                                                onClick = toggleStateChoices,
                                                enabled = syntax.isSuccess,
                                                shape = RoundedCornerShape(4.dp),
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier.size(48.dp).semantics {
                                                    contentDescription = changeLabel
                                                    stateDescription = stateLabel
                                                },
                                            ) { Text(algorithmOperationSymbol(rule?.operation)) }
                                        }
                                        Column(
                                            Modifier.weight(1f).then(
                                                if (mode == AlgorithmEditMode.DELTA) {
                                                    Modifier.clickable(enabled = syntax.isSuccess, role = Role.Button, onClick = toggleStateChoices)
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                        ) {
                                            Text(name, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                stringResource(
                                                    when {
                                                        algorithm?.available != true -> R.string.crypto_algorithm_unavailable
                                                        preview?.warnings?.any { it.algorithm == name } == true -> R.string.crypto_algorithm_filtered
                                                        name in defaults -> R.string.crypto_algorithm_default
                                                        else -> R.string.crypto_algorithm_optional
                                                    },
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                        if (draggable) {
                                            IconButton(
                                                onClick = {},
                                                enabled = acceptsDrag,
                                                modifier = Modifier.testTag("algorithm-drag-$name").draggableHandle(
                                                    enabled = acceptsDrag,
                                                    onDragStarted = { draggingOperation = rule?.operation },
                                                    onDragStopped = { draggingOperation = null },
                                                ),
                                            ) { Icon(Icons.Default.DragHandle, contentDescription = drag) }
                                        }
                                    }
                                    AnimatedVisibility(
                                        visible = mode == AlgorithmEditMode.DELTA && expandedAlgorithm == name,
                                        enter = expandVertically(tween(ALGORITHM_EXPANSION_DURATION_MILLIS), expandFrom = Alignment.Top) + fadeIn(tween(ALGORITHM_EXPANSION_DURATION_MILLIS)),
                                        exit = shrinkVertically(tween(ALGORITHM_EXPANSION_DURATION_MILLIS), shrinkTowards = Alignment.Top) + fadeOut(tween(ALGORITHM_EXPANSION_DURATION_MILLIS)),
                                    ) {
                                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf('^', '+', null, '-').forEach { operation ->
                                                val label = stringResource(algorithmOperationTitleRes(operation))
                                                OutlinedButton(
                                                    onClick = {
                                                        if (rule?.operation != operation) change(setAlgorithmRule(expression, name, operation))
                                                        settleRequested = true
                                                        selectionRevision++
                                                    },
                                                    enabled = syntax.isSuccess,
                                                    shape = RoundedCornerShape(4.dp),
                                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        containerColor = if (rule?.operation == operation) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                                    ),
                                                    modifier = Modifier.weight(1f).semantics {
                                                        contentDescription = label
                                                        selected = rule?.operation == operation
                                                    },
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(algorithmOperationSymbol(operation))
                                                        Text(label, style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun algorithmOperationTitleRes(operation: Char?): Int = when (operation) {
    '^' -> R.string.crypto_prepend
    '+' -> R.string.crypto_append
    '-' -> R.string.crypto_exclude
    else -> R.string.crypto_inherit
}

private fun algorithmOperationSymbol(operation: Char?): String = operation?.toString() ?: "✓"
