package com.phonepvr.friends.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.domain.model.InteractionType
import com.phonepvr.friends.ui.common.DateTextField
import com.phonepvr.friends.ui.common.parseDateDigits

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
                title = {
                    Text(if (viewModel.isEditing) "Edit interaction" else "Log interaction")
                },
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

            val dateDigits = form.date.digits
            val parsedDate = parseDateDigits(dateDigits)
            DateTextField(
                digits = dateDigits,
                onDigitsChange = { newDigits -> viewModel.onDateChange { it.copy(digits = newDigits) } },
                label = "Date",
                allowYearOptional = false,
                isError = dateDigits.length == 8 && parsedDate?.year == null,
                supportingText = when {
                    dateDigits.length == 8 && parsedDate == null -> "Invalid date"
                    dateDigits.length in 1..7 -> "Keep typing — DD/MM/YYYY"
                    else -> null
                },
                modifier = Modifier.fillMaxWidth(),
            )

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
