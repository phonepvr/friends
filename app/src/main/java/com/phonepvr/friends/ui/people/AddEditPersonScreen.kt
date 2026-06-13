package com.phonepvr.friends.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.ui.common.DateTextField
import com.phonepvr.friends.ui.common.parseDateDigits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPersonScreen(
    onDone: () -> Unit,
    onDeleted: () -> Unit = onDone,
    viewModel: AddEditPersonViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit bond") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onDone) }) {
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
            // Name and phone numbers come from the linked contact, so they're
            // shown read-only here — change them in the contact itself.
            Text(form.displayName, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Name and numbers come from this contact. " +
                    "To change them, edit the contact.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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

            EventDateEntry(
                label = "Birthday",
                fields = form.birthday,
                onChange = viewModel::updateBirthday,
            )
            EventDateEntry(
                label = "Wedding anniversary",
                fields = form.anniversary,
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
                    onClick = { viewModel.delete(onDeleted) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Remove bond")
                }
            }
        }
    }
}

@Composable
private fun EventDateEntry(
    label: String,
    fields: DateFields,
    onChange: ((DateFields) -> DateFields) -> Unit,
) {
    val digits = fields.digits
    val parsed = parseDateDigits(digits)
    val isCompleteAttempt = digits.length == 4 || digits.length == 8
    val isError = isCompleteAttempt && parsed == null
    val supporting = when {
        digits.isEmpty() -> "DD/MM/YYYY — leave year blank if unknown"
        digits.length == 4 && parsed != null -> "Year unknown"
        digits.length == 4 -> "Invalid date"
        digits.length == 8 && parsed == null -> "Invalid date"
        digits.length in 5..7 -> "Keep typing or shorten to skip the year"
        else -> null
    }
    DateTextField(
        digits = digits,
        onDigitsChange = { newDigits -> onChange { it.copy(digits = newDigits) } },
        label = label,
        allowYearOptional = true,
        isError = isError,
        supportingText = supporting,
        modifier = Modifier.fillMaxWidth(),
    )
}
