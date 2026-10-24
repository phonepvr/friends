package com.phonepvr.friends.ui.dialer

import android.telecom.PhoneAccountHandle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phonepvr.friends.data.dialer.CallPlacer
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.phonepvr.friends.data.dialer.SimMemory
import com.phonepvr.friends.data.dialer.SimMemoryKeys
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Drives the multi-SIM "call from which SIM?" flow for one screen.
 *
 * [call] is `(number, account) -> place the call` — typically
 * `viewModel.place(number, account)`. [launch] does the right thing:
 *   - a SIM remembered for this number (and still in the phone) → call with it,
 *     even when Android has a default SIM: the per-number choice wins, as in the
 *     stock dialer;
 *   - one SIM, or a system default set → call straight through (account = null),
 *     so single-SIM users never see a sheet;
 *   - 2+ SIMs and no default → opens the chooser, which can remember the pick
 *     for this number; then calls with the picked account.
 */
class SimCallLauncher internal constructor(
    private val accountsProvider: () -> List<CallPlacer.SimAccount>,
    private val needsChoice: () -> Boolean,
    private val call: (String, PhoneAccountHandle?) -> Unit,
    private val setPending: (PendingCall?) -> Unit,
    private val rememberedSim: (String, List<PhoneAccountHandle>) -> PhoneAccountHandle? = { _, _ -> null },
    private val rememberSim: (String, PhoneAccountHandle) -> Unit = { _, _ -> },
) {
    internal data class PendingCall(
        val number: String,
        val accounts: List<CallPlacer.SimAccount>,
    )

    fun launch(number: String) {
        val accounts = accountsProvider()
        if (accounts.size >= 2) {
            rememberedSim(number, accounts.map { it.handle })?.let { remembered ->
                call(number, remembered)
                return
            }
        }
        if (needsChoice()) {
            setPending(PendingCall(number, accounts))
        } else {
            call(number, null)
        }
    }

    internal fun pick(number: String, account: PhoneAccountHandle?, rememberForNumber: Boolean) {
        setPending(null)
        if (account != null) {
            if (rememberForNumber) rememberSim(number, account)
            call(number, account)
        }
    }

    internal fun cancel() = setPending(null)
}

/** Reaches the app-wide [SimMemory] from composables shared by several screens. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SimMemoryEntryPoint {
    fun simMemory(): SimMemory
    fun callPlacer(): CallPlacer
}

@Composable
private fun rememberSimEntryPoint(): SimMemoryEntryPoint? {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, SimMemoryEntryPoint::class.java)
        }.getOrNull()
    }
}

@Composable
internal fun rememberSimMemory(): SimMemory? {
    val entryPoint = rememberSimEntryPoint()
    return remember(entryPoint) { entryPoint?.let { runCatching { it.simMemory() }.getOrNull() } }
}

/**
 * The SIM button on a contact's or bond's number row (dual-SIM phones only):
 * shows whether a SIM is remembered for [number] and lets the user pick one,
 * or go back to asking / the phone's default. Draws nothing on single-SIM
 * phones or without call permission.
 */
@Composable
fun SimForNumberButton(number: String) {
    val entryPoint = rememberSimEntryPoint() ?: return
    val simMemory = remember(entryPoint) { entryPoint.simMemory() }
    val accounts = remember(entryPoint) {
        runCatching { entryPoint.callPlacer().callCapableAccounts() }.getOrDefault(emptyList())
    }
    if (accounts.size < 2 || SimMemoryKeys.numberKey(number) == null) return
    val choices by simMemory.choices.collectAsState()
    val current = remember(choices, number, accounts) {
        simMemory.simFor(number, accounts.map { it.handle })
    }
    val currentLabel = accounts.firstOrNull { it.handle == current }?.label
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(
            Icons.Filled.SimCard,
            contentDescription = currentLabel?.let { "Calls use $it. Change SIM" } ?: "Choose a SIM for this number",
            tint = if (current != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Call $number with") },
            text = {
                Column {
                    SimOption(label = "Ask each time (or the phone's default)", selected = current == null) {
                        simMemory.forget(number)
                        open = false
                    }
                    accounts.forEach { sim ->
                        SimOption(label = sim.label, selected = sim.handle == current) {
                            simMemory.remember(number, sim.handle)
                            open = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun SimOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Remembers a [SimCallLauncher] for [call] and hosts the chooser sheet inline,
 * so a screen only needs `val launcher = rememberSimCallLauncher(...)` then
 * `launcher.launch(number)` wherever it used to place a call.
 */
@Composable
fun rememberSimCallLauncher(
    accounts: () -> List<CallPlacer.SimAccount>,
    needsChoice: () -> Boolean,
    call: (String, PhoneAccountHandle?) -> Unit,
): SimCallLauncher {
    var pending by remember { mutableStateOf<SimCallLauncher.PendingCall?>(null) }
    val simMemory = rememberSimMemory()
    val launcher = remember(call, simMemory) {
        SimCallLauncher(
            accountsProvider = accounts,
            needsChoice = needsChoice,
            call = call,
            setPending = { pending = it },
            rememberedSim = { number, available -> simMemory?.simFor(number, available) },
            rememberSim = { number, handle -> simMemory?.remember(number, handle) },
        )
    }
    pending?.let { p ->
        SimChooserSheet(
            accounts = p.accounts,
            offerRemember = SimMemoryKeys.numberKey(p.number) != null,
            onPick = { handle, rememberIt -> launcher.pick(p.number, handle, rememberIt) },
            onDismiss = { launcher.cancel() },
        )
    }
    return launcher
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SimChooserSheet(
    accounts: List<CallPlacer.SimAccount>,
    offerRemember: Boolean,
    onPick: (PhoneAccountHandle, rememberForNumber: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var rememberIt by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Call with",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            accounts.forEach { sim ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(sim.handle, rememberIt) }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Icon(
                        Icons.Filled.SimCard,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(20.dp))
                    Text(sim.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (offerRemember) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = rememberIt,
                            role = Role.Checkbox,
                            onValueChange = { rememberIt = it },
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = rememberIt, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Remember for this number", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
