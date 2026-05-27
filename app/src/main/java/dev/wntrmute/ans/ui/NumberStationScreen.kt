package dev.wntrmute.ans.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wntrmute.ans.NumberFormatter
import dev.wntrmute.ans.NumberStationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberStationScreen(viewModel: NumberStationViewModel = viewModel()) {
    // The field lives here (not in the ViewModel) so its caret survives
    // recomposition; playback uses a snapshot taken when Play is pressed.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val groups = remember(field.text) { NumberFormatter.groups(field.text) }
    val digitCount = remember(field.text) { NumberFormatter.digitsOnly(field.text).length }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Number Station") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = field,
                onValueChange = { new ->
                    // Reformat to groups of five on every change, then place the
                    // caret just after the same number of digits it preceded.
                    val before = NumberFormatter.digitsBefore(new.text, new.selection.start)
                    val formatted = NumberFormatter.format(new.text)
                    val caret = NumberFormatter.caretAfterDigits(before)
                        .coerceIn(0, formatted.length)
                    field = TextFieldValue(text = formatted, selection = TextRange(caret))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("Message — groups of five") },
                placeholder = { Text("e.g. 12345 67890") },
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp,
                    letterSpacing = 2.sp,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                enabled = !viewModel.isPlaying,
            )

            Text(
                text = "${groups.size} group${if (groups.size == 1) "" else "s"} · " +
                    "$digitCount digit${if (digitCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
            )

            RepeatControls(viewModel)

            Button(
                onClick = { viewModel.playOrStop(field.text) },
                enabled = viewModel.isPlaying || groups.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(text = if (viewModel.isPlaying) "Stop" else "Play", fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun RepeatControls(viewModel: NumberStationViewModel) {
    val enabled = !viewModel.isPlaying
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Repeat",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            Switch(
                checked = viewModel.repeatEnabled,
                onCheckedChange = { viewModel.repeatEnabled = it },
                enabled = enabled,
            )
        }

        if (viewModel.repeatEnabled) {
            RadioRow(
                selected = viewModel.loopUntilStopped,
                label = "Loop until stopped",
                enabled = enabled,
                onSelect = { viewModel.loopUntilStopped = true },
            )
            RadioRow(
                selected = !viewModel.loopUntilStopped,
                label = "Repeat a set number of times",
                enabled = enabled,
                onSelect = { viewModel.loopUntilStopped = false },
            )
            if (!viewModel.loopUntilStopped) {
                Stepper(
                    value = viewModel.repeatCount,
                    enabled = enabled,
                    onChange = viewModel::changeRepeatCount,
                    modifier = Modifier.padding(start = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun RadioRow(
    selected: Boolean,
    label: String,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun Stepper(
    value: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { onChange(value - 1) }, enabled = enabled && value > 1) {
            Text("−") // minus sign
        }
        Text(
            text = "$value",
            modifier = Modifier
                .widthIn(min = 48.dp)
                .padding(horizontal = 12.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedButton(onClick = { onChange(value + 1) }, enabled = enabled && value < 99) {
            Text("+")
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (value == 1) "repeat" else "repeats",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
