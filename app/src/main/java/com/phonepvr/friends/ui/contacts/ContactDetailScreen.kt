package com.phonepvr.friends.ui.contacts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.phonepvr.friends.data.contacts.ContactDate
import com.phonepvr.friends.data.contacts.ContactDetails
import com.phonepvr.friends.data.contacts.PhoneType
import com.phonepvr.friends.data.contacts.VCardBuilder
import com.phonepvr.friends.data.dialer.CallPlacer
import com.phonepvr.friends.ui.permissions.PermissionRationaleSheet
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    onBack: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: ContactDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Bonded contacts have a single unified home: the bonded profile. When
    // this contact turns out to be tracked, hand off to the person screen
    // (replacing this entry so Back returns to wherever the user came from,
    // not to this transient contact view). Non-bonded contacts stay here.
    val trackedId = state.trackedPersonId
    LaunchedEffect(state.isTracked, trackedId) {
        if (state.isTracked && trackedId != null) {
            onOpenPerson(trackedId)
        }
    }
    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var hasCallPhone by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var showCallPermissionSheet by remember { mutableStateOf(false) }
    var pendingNumber by remember { mutableStateOf<String?>(null) }

    // Shows the SIM chooser first on a multi-SIM device with no default.
    val simLauncher = rememberSimCallLauncher(
        accounts = viewModel::callCapableAccounts,
        needsChoice = viewModel::needsSimChoice,
        call = { number, account ->
            val msg = when (viewModel.placeCall(number, account)) {
                CallPlacer.PlaceResult.OK -> null
                CallPlacer.PlaceResult.NO_PERMISSION ->
                    "Couldn't dial — Call permission missing."
                CallPlacer.PlaceResult.INVALID_NUMBER -> "Invalid number."
                CallPlacer.PlaceResult.ERROR -> "Couldn't place the call. Try again."
            }
            if (msg != null) scope.launch { snackbarState.showSnackbar(msg) }
        },
    )

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCallPhone = granted
        pendingNumber?.let { num ->
            pendingNumber = null
            if (granted) simLauncher.launch(num)
        }
    }

    if (showCallPermissionSheet) {
        PermissionRationaleSheet(
            title = "Permission to call",
            body = "Bondwidth needs your phone's Call permission to dial " +
                "numbers from this screen. Nothing leaves the device.",
            manualFallback = "Skip and Bondwidth will hand the dial off " +
                "to your existing phone app instead.",
            grantLabel = "Grant access",
            manualLabel = "Use system dialer",
            onGrant = {
                showCallPermissionSheet = false
                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            },
            onManualFallback = {
                showCallPermissionSheet = false
                pendingNumber?.let { num ->
                    pendingNumber = null
                    val intent = Intent(Intent.ACTION_DIAL, "tel:$num".toUri())
                    context.startActivity(intent)
                }
            },
            onDismiss = {
                showCallPermissionSheet = false
                pendingNumber = null
            },
        )
    }

    val onCallNumber: (String) -> Unit = { number ->
        if (hasCallPhone) {
            simLauncher.launch(number)
        } else {
            pendingNumber = number
            showCallPermissionSheet = true
        }
    }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete contact?") },
            text = {
                Text(
                    "This removes the contact from your phone for good. " +
                        "If they're bonded in Bondwidth, their cadence " +
                        "and timeline are archived but kept.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteContact()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) { Snackbar(it) } },
        topBar = {
            TopAppBar(
                title = { Text(state.details?.displayName ?: "Contact") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (state.details != null && !state.deleting) {
                        val sharable = state.details
                        IconButton(onClick = { sharable?.let { shareContact(context, it) } }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share contact")
                        }
                        // Pinning needs a primary number — hide if the
                        // contact has none, otherwise the tap silently no-ops.
                        if (state.details?.phoneNumbers?.isNotEmpty() == true) {
                            IconButton(onClick = viewModel::toggleFavourite) {
                                Icon(
                                    imageVector = if (state.isFavourite) {
                                        Icons.Filled.Star
                                    } else {
                                        Icons.Filled.StarBorder
                                    },
                                    contentDescription = if (state.isFavourite) {
                                        "Unpin from favourites"
                                    } else {
                                        "Pin to favourites"
                                    },
                                    tint = if (state.isFavourite) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        androidx.compose.ui.graphics.Color.Unspecified
                                    },
                                )
                            }
                        }
                        IconButton(onClick = { onEdit(viewModel.contactId) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
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
                state.notFound -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Contact not found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    val d = state.details ?: return@Box
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        HeroHeader(
                            context = context,
                            displayName = d.displayName,
                            photoRelativePath = state.photoRelativePath,
                            photoUri = d.photoUri,
                            isBonded = state.isTracked,
                            lastContactedAt = state.lastContactedAt,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            BondActions(
                                isTracked = state.isTracked,
                                mutating = state.mutating,
                                onToggle = viewModel::toggleTracked,
                                onOpenPerson = state.trackedPersonId
                                    ?.takeIf { state.isTracked }
                                    ?.let { id -> { onOpenPerson(id) } },
                            )
                            if (d.phoneEntries.isNotEmpty()) {
                                SectionLabel("Phone")
                                val multiple = d.phoneEntries.size > 1
                                d.phoneEntries.forEach { phone ->
                                    PhoneRow(
                                        number = phone.number,
                                        typeLabel = phoneTypeLabel(phone.type)
                                            .let { base ->
                                                if (phone.type == PhoneType.CUSTOM &&
                                                    !phone.customLabel.isNullOrBlank()
                                                ) {
                                                    phone.customLabel
                                                } else {
                                                    base
                                                }
                                            },
                                        isPrimary = phone.isPrimary,
                                        // Only offer "set primary" when there's
                                        // more than one number to choose between.
                                        canSetPrimary = multiple && !phone.isPrimary,
                                        onCall = { onCallNumber(phone.number) },
                                        onSetPrimary = { viewModel.setPrimaryNumber(phone.dataId) },
                                    )
                                }
                            }
                            if (d.emails.isNotEmpty()) {
                                SectionLabel("Email")
                                d.emails.forEach { address ->
                                    IconTextRow(
                                        icon = Icons.Filled.Email,
                                        text = address,
                                    )
                                }
                            }
                            d.organization?.let { company ->
                                IconTextRow(
                                    icon = Icons.Filled.Business,
                                    text = company,
                                )
                            }
                            d.birthday?.let {
                                DateRow(
                                    label = "Birthday",
                                    date = it,
                                    icon = Icons.Filled.Cake,
                                )
                            }
                            d.anniversary?.let {
                                DateRow(
                                    label = "Anniversary",
                                    date = it,
                                    icon = Icons.Filled.CalendarMonth,
                                )
                            }
                            d.notes?.let { notes ->
                                SectionLabel("Notes")
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Notes,
                                        contentDescription = null,
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Text(notes, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            RingtoneRow(
                                customRingtone = d.customRingtone,
                                contactName = d.displayName,
                                onPicked = viewModel::setCustomRingtone,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RingtoneRow(
    customRingtone: String?,
    contactName: String,
    onPicked: (android.net.Uri?) -> Unit,
) {
    val context = LocalContext.current
    // Resolve the URI to a human label. getRingtone() can hit MediaStore so
    // we cache the result per URI via remember.
    val title = remember(customRingtone) {
        if (customRingtone == null) {
            "Default ringtone"
        } else {
            runCatching {
                RingtoneManager.getRingtone(context, customRingtone.toUri())
                    ?.getTitle(context)
            }.getOrNull() ?: "Custom ringtone"
        }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val picked: android.net.Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    android.net.Uri::class.java,
                )
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra<android.net.Uri>(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                )
            }
            // The picker returns the system default URI when "Default" is
            // selected. Translate that back to null so our DB stores
            // "use whatever the system default is right now" rather than
            // pinning a copy of it.
            val systemDefault = RingtoneManager.getActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE,
            ) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            onPicked(if (picked == systemDefault) null else picked)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                    putExtra(
                        RingtoneManager.EXTRA_RINGTONE_TITLE,
                        "Ringtone for $contactName",
                    )
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    customRingtone?.let {
                        putExtra(
                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            it.toUri(),
                        )
                    }
                }
                runCatching { launcher.launch(intent) }
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Notifications, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Tap to choose ringtone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (customRingtone != null) {
            TextButton(onClick = { onPicked(null) }) { Text("Reset") }
        }
    }
}

@Composable
private fun HeroHeader(
    context: Context,
    displayName: String,
    photoRelativePath: String?,
    photoUri: String?,
    isBonded: Boolean,
    lastContactedAt: Long?,
) {
    // Prefer the bonded person's locally-saved photo, then the system
    // contact's photo, else a tinted block with a big initial.
    val photoModel: Any? = when {
        photoRelativePath != null -> File(context.filesDir, photoRelativePath)
        photoUri != null -> photoUri.toUri()
        else -> null
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
    ) {
        if (photoModel != null) {
            AsyncImage(
                model = photoModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = displayName.trim().firstOrNull()?.uppercase() ?: "?",
                    fontSize = 96.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        // Dark scrim at the bottom so the white name text stays legible over
        // any photo.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        startY = 220f,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isBonded) {
                    HeroChip(icon = Icons.Filled.Favorite, text = "Bonded")
                    Spacer(Modifier.width(8.dp))
                }
                lastContactedAt?.let { ms ->
                    HeroChip(icon = null, text = lastContactedLabel(ms))
                }
            }
        }
    }
}

@Composable
private fun HeroChip(icon: ImageVector?, text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = Color.White,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun BondActions(
    isTracked: Boolean,
    mutating: Boolean,
    onToggle: () -> Unit,
    onOpenPerson: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isTracked) {
            Button(
                onClick = onToggle,
                enabled = !mutating,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Bonded")
            }
        } else {
            Button(
                onClick = onToggle,
                enabled = !mutating,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.FavoriteBorder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add to Bonds")
            }
        }
        if (onOpenPerson != null) {
            OutlinedButton(onClick = onOpenPerson) { Text("Open profile") }
        }
    }
}

private fun lastContactedLabel(ms: Long): String {
    val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - ms).toInt()
    return when {
        days <= 0 -> "Spoke today"
        days == 1 -> "Spoke yesterday"
        days < 30 -> "Last spoke $days days ago"
        days < 60 -> "Last spoke a month ago"
        days < 365 -> "Last spoke ${days / 30} months ago"
        else -> "Last spoke over a year ago"
    }
}

private fun shareContact(context: Context, details: ContactDetails) {
    runCatching {
        val dir = File(context.cacheDir, "shared_contacts").apply { mkdirs() }
        val file = File(dir, VCardBuilder.fileName(details.displayName))
        file.writeText(VCardBuilder.build(details))
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/x-vcard"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share contact"))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PhoneRow(
    number: String,
    typeLabel: String,
    isPrimary: Boolean,
    canSetPrimary: Boolean,
    onCall: () -> Unit,
    onSetPrimary: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCall)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Call, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(number, style = MaterialTheme.typography.bodyLarge)
            // "Mobile · Default" / "Work" / "Custom: Office line" — one line
            // that names the role of this number before any default chip.
            val subtitle = listOfNotNull(
                typeLabel.takeIf { it.isNotBlank() },
                if (isPrimary) "Default" else null,
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPrimary) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        when {
            isPrimary -> Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Default number",
                tint = MaterialTheme.colorScheme.primary,
            )
            canSetPrimary -> IconButton(onClick = onSetPrimary) {
                Icon(
                    imageVector = Icons.Filled.StarBorder,
                    contentDescription = "Set as default number",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IconTextRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DateRow(label: String, date: ContactDate, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(formatDate(date), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun formatDate(date: ContactDate): String {
    val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
    val monthName = months.getOrNull(date.month - 1) ?: date.month.toString()
    return if (date.year != null) "$monthName ${date.day}, ${date.year}" else "$monthName ${date.day}"
}
