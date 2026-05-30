package com.phonepvr.friends.ui.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
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
                    onPickPhoto = viewModel::onPhotoPicked,
                    onRemovePhoto = viewModel::onRemovePhoto,
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
    onPickPhoto: (android.net.Uri) -> Unit,
    onRemovePhoto: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PhotoHeader(
            state = state,
            onPickPhoto = onPickPhoto,
            onRemovePhoto = onRemovePhoto,
        )
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

@Composable
private fun PhotoHeader(
    state: ContactEditUiState,
    onPickPhoto: (android.net.Uri) -> Unit,
    onRemovePhoto: () -> Unit,
) {
    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onPickPhoto(uri)
    }
    // The picked URI wins over the existing one so the new photo previews
    // immediately while the decode runs in the background.
    val previewModel: Any? = state.pickedPhotoUri
        ?: state.existingPhotoUri?.takeIf { it.isNotBlank() }?.toUri()
    val hasPhoto = previewModel != null
    val initial = state.form.displayName.trim().firstOrNull()?.uppercase() ?: "?"
    val launchPicker = {
        pickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = launchPicker),
            contentAlignment = Alignment.Center,
        ) {
            if (previewModel != null) {
                AsyncImage(
                    model = previewModel,
                    contentDescription = "Contact photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = initial,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            // Small pencil/camera badge bottom-right, so the avatar reads
            // as "tap to change" even when a photo is already there.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (hasPhoto) Icons.Filled.Edit else Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (state.processingPhoto) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.fillMaxWidth(0.02f))
                Text(
                    text = "Processing photo…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (hasPhoto) {
            TextButton(onClick = onRemovePhoto) { Text("Remove photo") }
        } else {
            TextButton(onClick = launchPicker) { Text("Add photo") }
        }
    }
}
