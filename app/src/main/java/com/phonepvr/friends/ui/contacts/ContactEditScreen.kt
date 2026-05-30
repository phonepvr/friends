package com.phonepvr.friends.ui.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.ui.permissions.PermissionRationaleSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditScreen(
    onDone: () -> Unit,
    viewModel: ContactEditViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_CONTACTS,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var showRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    if (showRationale) {
        PermissionRationaleSheet(
            title = "Write contacts, to save this one",
            body = "Bondwidth needs permission to write to your phone's " +
                "contacts. Edits and new contacts saved here appear in your " +
                "system Contacts app too. Nothing leaves the device.",
            manualFallback = "If you'd rather not, close this screen and " +
                "add the contact in your usual contacts app.",
            grantLabel = "Grant access",
            manualLabel = "Cancel",
            onGrant = {
                showRationale = false
                permissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
            },
            onManualFallback = {
                showRationale = false
                onDone()
            },
            onDismiss = { showRationale = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (state.mode) {
                            ContactEditMode.NEW -> "New contact"
                            ContactEditMode.EDIT -> "Edit contact"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cancel",
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (hasPermission) viewModel.save() else showRationale = true
                        },
                        enabled = !state.saving && !state.loading,
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                else -> EditForm(
                    state = state,
                    onDisplayNameChange = viewModel::onDisplayNameChange,
                    onPhoneChange = viewModel::onPhoneChange,
                    onAddPhone = viewModel::onAddPhone,
                    onRemovePhone = viewModel::onRemovePhone,
                    onEmailChange = viewModel::onEmailChange,
                    onAddEmail = viewModel::onAddEmail,
                    onRemoveEmail = viewModel::onRemoveEmail,
                    onNotesChange = viewModel::onNotesChange,
                    onOrganizationChange = viewModel::onOrganizationChange,
                )
            }
        }
    }
}

@Composable
private fun EditForm(
    state: ContactEditUiState,
    onDisplayNameChange: (String) -> Unit,
    onPhoneChange: (Int, String) -> Unit,
    onAddPhone: () -> Unit,
    onRemovePhone: (Int) -> Unit,
    onEmailChange: (Int, String) -> Unit,
    onAddEmail: () -> Unit,
    onRemoveEmail: (Int) -> Unit,
    onNotesChange: (String) -> Unit,
    onOrganizationChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = state.form.displayName,
            onValueChange = onDisplayNameChange,
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = state.error == "Name is required",
        )
        RepeatableSection(
            label = "Phones",
            entries = state.form.phones,
            keyboardType = KeyboardType.Phone,
            placeholder = "Phone number",
            onChange = onPhoneChange,
            onAdd = onAddPhone,
            onRemove = onRemovePhone,
        )
        RepeatableSection(
            label = "Emails",
            entries = state.form.emails,
            keyboardType = KeyboardType.Email,
            placeholder = "Email",
            onChange = onEmailChange,
            onAdd = onAddEmail,
            onRemove = onRemoveEmail,
        )
        OutlinedTextField(
            value = state.form.organization,
            onValueChange = onOrganizationChange,
            label = { Text("Organization") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.form.notes,
            onValueChange = onNotesChange,
            label = { Text("Notes") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun RepeatableSection(
    label: String,
    entries: List<String>,
    keyboardType: KeyboardType,
    placeholder: String,
    onChange: (Int, String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        entries.forEachIndexed { index, value ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { onChange(index, it) },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemove(index) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Remove")
                }
            }
        }
        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.fillMaxWidth(0.05f))
            Text("Add ${label.lowercase().removeSuffix("s")}")
        }
    }
}
