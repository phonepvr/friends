package com.phonepvr.friends.ui.contacts

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.core.content.FileProvider
import com.phonepvr.friends.data.contacts.ContactDetails
import com.phonepvr.friends.data.contacts.ContactShareText
import com.phonepvr.friends.data.contacts.PhoneType
import com.phonepvr.friends.data.contacts.VCardBuilder
import java.io.File

/**
 * "Share contact": asks how to send it. A contact card (.vcf) is what WhatsApp,
 * Telegram and email show as a contact the receiver saves in one tap; plain text
 * (name and numbers) works in SMS and anywhere else. Bondwidth only hands it to
 * the app the user picks; it never sends anything itself.
 */
@Composable
fun ShareContactDialog(
    details: ContactDetails,
    onDismiss: () -> Unit,
    context: Context,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share ${details.displayName}") },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("Contact card") },
                    supportingContent = {
                        Text("Saves in one tap in WhatsApp, Telegram, email and Contacts.")
                    },
                    leadingContent = { Icon(Icons.Filled.ContactPage, contentDescription = null) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        shareContactCard(context, details)
                    },
                )
                ListItem(
                    headlineContent = { Text("As text") },
                    supportingContent = { Text("Name and numbers in a message. Works in SMS and any app.") },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        onDismiss()
                        shareContactText(context, details)
                    },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Writes the card under cacheDir/shared_contacts and offers it to other apps. */
fun shareContactCard(context: Context, details: ContactDetails) {
    runCatching {
        val dir = File(context.cacheDir, "shared_contacts").apply { mkdirs() }
        val file = File(dir, VCardBuilder.fileName(details.displayName))
        file.writeText(VCardBuilder.build(details))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/x-vcard"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, details.displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Share ${details.displayName}")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Offers the contact as plain text (name and labelled numbers). */
fun shareContactText(context: Context, details: ContactDetails) {
    runCatching {
        val text = ContactShareText.build(details) { phone ->
            if (phone.type == PhoneType.CUSTOM && !phone.customLabel.isNullOrBlank()) {
                phone.customLabel
            } else {
                phoneTypeLabel(phone.type)
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, details.displayName)
        }
        context.startActivity(
            Intent.createChooser(intent, "Share ${details.displayName}")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
