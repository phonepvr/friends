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

/**
 * Drives the multi-SIM "call from which SIM?" flow for one screen.
 *
 * [call] is `(number, account) -> place the call` — typically
 * `viewModel.place(number, account)`. [launch] does the right thing:
 *   - one SIM, or a system default set → call straight through (account = null),
 *     so single-SIM users never see a sheet;
 *   - 2+ SIMs and no default → opens the chooser, then calls with the picked
 *     account.
 */
class SimCallLauncher internal constructor(
    private val accountsProvider: () -> List<CallPlacer.SimAccount>,
    private val needsChoice: () -> Boolean,
    private val call: (String, PhoneAccountHandle?) -> Unit,
    private val setPending: (PendingCall?) -> Unit,
) {
    internal data class PendingCall(
        val number: String,
        val accounts: List<CallPlacer.SimAccount>,
    )

    fun launch(number: String) {
        if (needsChoice()) {
            setPending(PendingCall(number, accountsProvider()))
        } else {
            call(number, null)
        }
    }

    internal fun pick(number: String, account: PhoneAccountHandle?) {
        setPending(null)
        if (account != null) call(number, account)
    }

    internal fun cancel() = setPending(null)
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
    val launcher = remember(call) {
        SimCallLauncher(
            accountsProvider = accounts,
            needsChoice = needsChoice,
            call = call,
            setPending = { pending = it },
        )
    }
    pending?.let { p ->
        SimChooserSheet(
            accounts = p.accounts,
            onPick = { launcher.pick(p.number, it) },
            onDismiss = { launcher.cancel() },
        )
    }
    return launcher
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimChooserSheet(
    accounts: List<CallPlacer.SimAccount>,
    onPick: (PhoneAccountHandle) -> Unit,
    onDismiss: () -> Unit,
) {
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
                        .clickable { onPick(sim.handle) }
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
        }
    }
}
