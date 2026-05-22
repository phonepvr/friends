package com.phonepvr.friends.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.domain.model.InteractionType
import com.phonepvr.friends.ui.people.DateFields

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogInteractionScreen(
    onDone: () -> Unit,
    viewModel: LogInteractionViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log interaction") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onDone) }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Type", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InteractionType.entries.forEach { type ->
                    FilterChip(
                        selected = form.type == type,
                        onClick = { viewModel.onTypeChange(type) },
                        label = {
                            Text(type.name.lowercase().replaceFirstChar { it.uppercase() })
                        },
                    )
                }
            }

            Text("Date", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(
                    label = "Month",
                    value = form.date.month,
                    maxLength = 2,
                    modifier = Modifier.weight(1f),
                    onValueChange = { input -> viewModel.onDateChange { it.copy(month = input) } },
                )
                DateField(
                    label = "Day",
                    value = form.date.day,
                    maxLength = 2,
                    modifier = Modifier.weight(1f),
                    onValueChange = { input -> viewModel.onDateChange { it.copy(day = input) } },
                )
                DateField(
                    label = "Year",
                    value = form.date.year,
                    maxLength = 4,
                    modifier = Modifier.weight(1.4f),
                    onValueChange = { input -> viewModel.onDateChange { it.copy(year = input) } },
                )
            }

            OutlinedTextField(
                value = form.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text("Note (optional)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DateField(
    label: String,
    value: String,
    maxLength: Int,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onValueChange(input.filter { it.isDigit() }.take(maxLength))
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
