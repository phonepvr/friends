package com.phonepvr.friends.ui.dialer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.phonepvr.friends.data.db.entity.FavouriteContactEntity
import com.phonepvr.friends.data.repository.FavouriteOrder
import com.phonepvr.friends.ui.components.PersonAvatar

/** All · Missed · Incoming · Outgoing, at the top of the Calls tab. */
@Composable
internal fun RecentsFilterRow(selected: RecentsFilter, onSelect: (RecentsFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RecentsFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

private val ROW_HEIGHT = 64.dp

/**
 * Reorder and remove favourites. Drag a row by its handle; it follows the finger
 * and swaps with a neighbour once dragged more than halfway over it. The new order
 * is saved when the drag ends. Screen-reader users get "Move up" / "Move down"
 * actions on each row instead of dragging.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditFavouritesSheet(
    favourites: List<FavouriteContactEntity>,
    photoByLookupKey: Map<String, String>,
    onReorder: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        // Local copy so rows move under the finger; reset whenever the stored
        // favourites change (after a save, or a removal).
        var order by remember(favourites) { mutableStateOf(favourites) }
        var draggingKey by remember { mutableStateOf<String?>(null) }
        var dragStartIndex by remember { mutableIntStateOf(0) }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        val rowHeightPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }

        fun save() = onReorder(order.map { it.lookupKey })

        fun moveBy(fromIndex: Int, delta: Int): Boolean {
            val to = fromIndex + delta
            if (to !in order.indices) return false
            order = FavouriteOrder.move(order, fromIndex, to)
            save()
            return true
        }

        fun endDrag() {
            if (draggingKey == null) return
            draggingKey = null
            dragOffset = 0f
            save()
        }

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = "Edit favourites",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                text = "Drag the handle to change the order on the Calls tab.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                order.forEachIndexed { index, fav ->
                    key(fav.lookupKey) {
                        val dragging = draggingKey == fav.lookupKey
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ROW_HEIGHT)
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer {
                                    // Follow the finger, minus the slots already
                                    // swapped past, so the row stays under it.
                                    translationY = if (dragging) {
                                        dragOffset - (index - dragStartIndex) * rowHeightPx
                                    } else {
                                        0f
                                    }
                                    shadowElevation = if (dragging) 8.dp.toPx() else 0f
                                }
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .semantics(mergeDescendants = true) {
                                    customActions = buildList {
                                        if (index > 0) {
                                            add(CustomAccessibilityAction("Move up") { moveBy(index, -1) })
                                        }
                                        if (index < order.lastIndex) {
                                            add(CustomAccessibilityAction("Move down") { moveBy(index, 1) })
                                        }
                                    }
                                }
                                .padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DragHandle,
                                contentDescription = "Drag to reorder",
                                modifier = Modifier
                                    .size(56.dp)
                                    .pointerInput(fav.lookupKey) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingKey = fav.lookupKey
                                                dragStartIndex = order.indexOfFirst {
                                                    it.lookupKey == fav.lookupKey
                                                }
                                                dragOffset = 0f
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                                val current = order.indexOfFirst {
                                                    it.lookupKey == fav.lookupKey
                                                }
                                                val target = FavouriteOrder.dragTarget(
                                                    from = dragStartIndex,
                                                    offsetPx = dragOffset,
                                                    rowHeightPx = rowHeightPx,
                                                    size = order.size,
                                                )
                                                if (target != current) {
                                                    order = FavouriteOrder.move(order, current, target)
                                                }
                                            },
                                            onDragEnd = { endDrag() },
                                            onDragCancel = { endDrag() },
                                        )
                                    }
                                    .padding(16.dp),
                            )
                            PersonAvatar(
                                photoRelativePath = fav.photoRelativePath,
                                displayName = fav.displayName,
                                diameter = 40.dp,
                                photoUri = photoByLookupKey[fav.lookupKey],
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fav.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = fav.primaryNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            IconButton(onClick = { onRemove(fav.lookupKey) }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove ${fav.displayName} from favourites",
                                )
                            }
                        }
                    }
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 16.dp, top = 8.dp),
            ) { Text("Done") }
        }
    }
}

/**
 * Shown on the Calls tab while Bondwidth isn't the phone app. A call started here
 * then shows the other app's call screen, and only the phone app may block
 * numbers, so without this both look like Bondwidth features silently not working.
 */
@Composable
internal fun PhoneAppBanner(onMakePhoneApp: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                text = "Bondwidth isn't your phone app",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Calls you start here show your current phone app's call screen, " +
                    "and blocking numbers needs Bondwidth as the phone app.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Not now") }
                TextButton(onClick = onMakePhoneApp) { Text("Make it the phone app") }
            }
        }
    }
}
