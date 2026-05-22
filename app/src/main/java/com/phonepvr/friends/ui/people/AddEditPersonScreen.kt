package com.phonepvr.friends.ui.people

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPersonScreen(
    onDone: () -> Unit,
    viewModel: AddEditPersonViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Edit person" else "Add person") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(onDone) },
                        enabled = form.displayName.isNotBlank(),
                    ) {
                        Text("Save")
                    }
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
            OutlinedTextField(
                value = form.displayName,
                onValueChange = viewModel::onNameChange,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Phone numbers", style = MaterialTheme.typography.titleSmall)
            form.phoneNumbers.forEachIndexed { index, number ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = number,
                        onValueChange = { viewModel.onPhoneChange(index, it) },
                        label = { Text("Phone") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.onRemovePhone(index) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove phone number")
                    }
                }
            }
            TextButton(onClick = viewModel::onAddPhone) {
                Text("Add phone number")
            }

            OutlinedTextField(
                value = form.relationshipTag,
                onValueChange = viewModel::onTagChange,
                label = { Text("Relationship (e.g. Family, Friend)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.cadenceTargetDays,
                onValueChange = viewModel::onCadenceChange,
                label = { Text("Stay in touch every N days (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            DateEntry(
                label = "Birthday (year optional)",
                value = form.birthday,
                onChange = viewModel::updateBirthday,
            )
            DateEntry(
                label = "Wedding anniversary (year optional)",
                value = form.anniversary,
                onChange = viewModel::updateAnniversary,
            )

            OutlinedTextField(
                value = form.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            if (viewModel.isEditing) {
                OutlinedButton(
                    onClick = { viewModel.delete(onDone) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete person")
                }
            }
        }
    }
}

@Composable
private fun DateEntry(
    label: String,
    value: DateFields,
    onChange: ((DateFields) -> DateFields) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value.month,
                onValueChange = { input ->
                    onChange { it.copy(month = input.filter { c -> c.isDigit() }.take(2)) }
                },
                label = { Text("Month") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = value.day,
                onValueChange = { input ->
                    onChange { it.copy(day = input.filter { c -> c.isDigit() }.take(2)) }
                },
                label = { Text("Day") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = value.year,
                onValueChange = { input ->
                    onChange { it.copy(year = input.filter { c -> c.isDigit() }.take(4)) }
                },
                label = { Text("Year") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1.4f),
            )
        }
    }
}
