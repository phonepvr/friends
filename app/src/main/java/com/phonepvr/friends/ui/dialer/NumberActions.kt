package com.phonepvr.friends.ui.dialer

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.phonepvr.friends.R
import com.phonepvr.friends.data.blocking.BlockAvailability
import com.phonepvr.friends.data.blocking.BlockedNumberManager
import com.phonepvr.friends.data.reachout.ReachOutLauncher
import com.phonepvr.friends.data.reachout.ReachOutMethod
import com.phonepvr.friends.role.DialerRoleManager
import com.phonepvr.friends.ui.components.Haptic
import com.phonepvr.friends.ui.components.PersonAvatar
import com.phonepvr.friends.ui.components.rememberHaptics
import com.phonepvr.friends.ui.permissions.BlockNeedsPhoneAppDialog
import com.phonepvr.friends.ui.permissions.RestrictedDialerSettingsDialog
import com.phonepvr.friends.ui.permissions.rememberPhoneAppRoleRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Blocking and the phone-app role, for [NumberActions]. */
@HiltViewModel
class NumberActionsViewModel @Inject constructor(
    private val blockedNumberManager: BlockedNumberManager,
    private val dialerRoleManager: DialerRoleManager,
) : ViewModel() {

    /** Whether Block can work here, or needs Bondwidth to be the phone app first. */
    fun blockAvailability(): BlockAvailability = blockedNumberManager.availability()

    suspend fun isBlocked(number: String): Boolean = blockedNumberManager.isBlocked(number)

    suspend fun setBlocked(number: String, blocked: Boolean): Boolean = if (blocked) {
        blockedNumberManager.block(number)
    } else {
        blockedNumberManager.unblock(number)
    }

    fun isPhoneApp(): Boolean = dialerRoleManager.isDefaultDialer()

    fun makePhoneAppRoleIntent(): Intent? = dialerRoleManager.makeAcquireRoleIntent()

    fun makeAppInfoIntent(): Intent = dialerRoleManager.makeAppInfoIntent()

    fun makeDefaultAppsSettingsIntent(): Intent = dialerRoleManager.makeDefaultAppsSettingsIntent()
}

/**
 * What a long-pressed number offers: Call, Message, WhatsApp, Signal, Copy number,
 * View or Add contact, Block / Unblock, and Delete for a call-log entry. Shared by
 * Recents, the Calls-tab search and the dialpad's contact matches, so a number
 * offers the same options wherever it is long-pressed.
 *
 * Also runs the block flow: blocking asks first (issue #45), and when Bondwidth
 * isn't the phone app it explains why and offers to fix that, then carries on to
 * the confirmation. Keep this in composition while the screen is shown and pass a
 * null [entry] when nothing is selected: the dialogs outlive the sheet.
 *
 * [onDelete] is offered only for real call-log entries (non-zero id).
 */
@Composable
internal fun NumberActions(
    entry: RecentEntry?,
    onDismiss: () -> Unit,
    onCall: (number: String) -> Unit,
    onOpenContact: (contactId: Long) -> Unit,
    onSaveNumber: (number: String) -> Unit,
    snackbarState: SnackbarHostState,
    onDelete: ((RecentEntry) -> Unit)? = null,
    viewModel: NumberActionsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var confirmBlockEntry by remember { mutableStateOf<RecentEntry?>(null) }
    // Block tapped while Bondwidth isn't the phone app: the entry waits here while
    // that is explained and fixed, then goes on to the usual block confirmation.
    var explainBlockFor by remember { mutableStateOf<RecentEntry?>(null) }
    var blockAfterPhoneApp by remember { mutableStateOf<RecentEntry?>(null) }
    var showRestrictedDialog by remember { mutableStateOf(false) }
    val requestPhoneApp = rememberPhoneAppRoleRequest(
        makeRequestIntent = viewModel::makePhoneAppRoleIntent,
        isPhoneApp = viewModel::isPhoneApp,
        onRefused = { showRestrictedDialog = true },
        onResult = { granted ->
            val pending = blockAfterPhoneApp
            blockAfterPhoneApp = null
            if (granted && pending != null) {
                scope.launch {
                    if (viewModel.isBlocked(pending.number)) {
                        snackbarState.showSnackbar("${pending.number} is already blocked")
                    } else {
                        confirmBlockEntry = pending
                    }
                }
            }
        },
    )
    val openChat: (ReachOutMethod, String) -> Unit = { method, number ->
        if (!ReachOutLauncher.launch(context, method, number)) {
            val app = if (method == ReachOutMethod.WHATSAPP) "WhatsApp" else "Signal"
            scope.launch { snackbarState.showSnackbar("Couldn't open $app") }
        }
    }

    entry?.let { shown ->
        val blockAvailability = remember(shown) { viewModel.blockAvailability() }
        val canBlock = blockAvailability == BlockAvailability.AVAILABLE
        // Loads asynchronously the first time the sheet opens for this
        // number. Null hides the row; true / false picks Unblock vs Block.
        var blockedStatus by remember(shown.number) { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(shown.number) {
            blockedStatus = if (canBlock && shown.number.isNotBlank()) {
                viewModel.isBlocked(shown.number)
            } else {
                null
            }
        }
        // App-install checks are cheap PackageManager lookups; memoize per
        // sheet open so we don't re-query on every recomposition. The
        // <queries> block in AndroidManifest declares both packages so
        // these resolve correctly on Android 11+.
        val hasWhatsApp = remember(context) {
            ReachOutLauncher.isAvailable(context, ReachOutMethod.WHATSAPP)
        }
        val hasSignal = remember(context) {
            ReachOutLauncher.isAvailable(context, ReachOutMethod.SIGNAL)
        }
        NumberActionsSheet(
            entry = shown,
            blockedStatus = blockedStatus,
            hasWhatsApp = hasWhatsApp,
            hasSignal = hasSignal,
            onDismiss = onDismiss,
            onCall = {
                onDismiss()
                onCall(shown.number)
            },
            onMessage = {
                onDismiss()
                openSms(context, shown.number)
            },
            onWhatsApp = {
                onDismiss()
                openChat(ReachOutMethod.WHATSAPP, shown.number)
            },
            onSignal = {
                onDismiss()
                openChat(ReachOutMethod.SIGNAL, shown.number)
            },
            onCopy = {
                onDismiss()
                copyNumberToClipboard(context, shown.number)
                // Android 13+ confirms copies itself; a second message would be noise.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    scope.launch { snackbarState.showSnackbar("Number copied") }
                }
            },
            onOpenContact = shown.contactId?.let { id ->
                {
                    onDismiss()
                    onOpenContact(id)
                }
            },
            onAddContact = if (shown.contactId == null) {
                {
                    onDismiss()
                    onSaveNumber(shown.number)
                }
            } else {
                null
            },
            onToggleBlock = {
                val number = shown.number
                onDismiss()
                if (blockedStatus == true) {
                    // Unblocking is harmless, so it happens straight away.
                    scope.launch {
                        val ok = viewModel.setBlocked(number, false)
                        snackbarState.showSnackbar(
                            if (ok) "$number unblocked" else "Couldn't update the block list",
                        )
                    }
                } else {
                    // Blocking is confirmed first (issue #45): the call you just
                    // finished sits at the top of Recents, so a cheek against a
                    // still-lit screen could otherwise block someone in two taps.
                    confirmBlockEntry = shown
                }
            },
            blockNeedsPhoneApp = blockAvailability == BlockAvailability.NEEDS_PHONE_APP,
            onBlockNeedsPhoneApp = {
                onDismiss()
                explainBlockFor = shown
            },
            // Only a real call-log entry (non-zero id) can be deleted; the sheet
            // also serves contacts from search and the dialpad (id 0).
            onDelete = if (onDelete != null && shown.id != 0L) {
                {
                    onDismiss()
                    onDelete(shown)
                }
            } else {
                null
            },
        )
    }

    confirmBlockEntry?.let { pending ->
        val who = pending.displayName ?: pending.number
        AlertDialog(
            onDismissRequest = { confirmBlockEntry = null },
            title = { Text("Block $who?") },
            text = {
                Text(
                    "Calls from ${pending.number} will be blocked and won't ring. " +
                        "You can unblock it from this menu.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val number = pending.number
                    confirmBlockEntry = null
                    haptics.perform(Haptic.Reject)
                    scope.launch {
                        val ok = viewModel.setBlocked(number, true)
                        snackbarState.showSnackbar(
                            if (ok) "$number blocked" else "Couldn't update the block list",
                        )
                    }
                }) { Text("Block") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBlockEntry = null }) { Text("Cancel") }
            },
        )
    }

    explainBlockFor?.let { pending ->
        BlockNeedsPhoneAppDialog(
            onDismiss = { explainBlockFor = null },
            onMakePhoneApp = {
                blockAfterPhoneApp = pending
                requestPhoneApp()
            },
        )
    }

    if (showRestrictedDialog) {
        RestrictedDialerSettingsDialog(
            onDismiss = { showRestrictedDialog = false },
            onOpenAppInfo = {
                runCatching { context.startActivity(viewModel.makeAppInfoIntent()) }
            },
            onOpenDefaultApps = {
                runCatching { context.startActivity(viewModel.makeDefaultAppsSettingsIntent()) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberActionsSheet(
    entry: RecentEntry,
    blockedStatus: Boolean?,
    hasWhatsApp: Boolean,
    hasSignal: Boolean,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onWhatsApp: () -> Unit,
    onSignal: () -> Unit,
    onCopy: () -> Unit,
    onOpenContact: (() -> Unit)?,
    onAddContact: (() -> Unit)?,
    onToggleBlock: () -> Unit,
    blockNeedsPhoneApp: Boolean,
    onBlockNeedsPhoneApp: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonAvatar(
                    photoRelativePath = entry.photoRelativePath,
                    displayName = entry.displayName ?: entry.number,
                    photoUri = entry.photoUri,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = entry.displayName ?: entry.number,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (entry.displayName != null) {
                        Text(
                            text = entry.number,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider()
            SheetAction(Icons.Filled.Call, "Call", onCall)
            SheetAction(Icons.AutoMirrored.Filled.Message, "Message", onMessage)
            // WhatsApp / Signal only show when the package is actually
            // installed — tapping otherwise would just snackbar a failure.
            if (hasWhatsApp) {
                SheetActionPainter(R.drawable.ic_brand_whatsapp, "WhatsApp", onWhatsApp)
            }
            if (hasSignal) {
                SheetActionPainter(R.drawable.ic_brand_signal, "Signal", onSignal)
            }
            SheetAction(Icons.Filled.ContentCopy, "Copy number", onCopy)
            onOpenContact?.let { SheetAction(Icons.Filled.Person, "View contact", it) }
            onAddContact?.let { SheetAction(Icons.Filled.PersonAdd, "Add to contacts", it) }
            // Block / Unblock — hidden when the device can't block at all, or
            // while the current state is still loading.
            blockedStatus?.let { blocked ->
                SheetAction(
                    icon = Icons.Filled.Block,
                    label = if (blocked) "Unblock this number" else "Block this number",
                    onClick = onToggleBlock,
                )
            } ?: run {
                // Only the phone app may block numbers: explain and offer to fix
                // that, rather than leave the option silently missing.
                if (blockNeedsPhoneApp) {
                    SheetAction(
                        icon = Icons.Filled.Block,
                        label = "Block this number",
                        onClick = onBlockNeedsPhoneApp,
                    )
                }
            }
            onDelete?.let {
                SheetAction(Icons.Filled.Delete, "Delete from call log", it)
            }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Same as [SheetAction] but for vector drawables (brand icons). */
@Composable
private fun SheetActionPainter(@DrawableRes iconRes: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun openSms(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_SENDTO, "smsto:$number".toUri())
    runCatching { context.startActivity(intent) }
}
