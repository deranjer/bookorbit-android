package com.bookorbit.feature.bookdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bookorbit.feature.bookdetail.BookDetailViewModel.CollectionsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionPickerSheet(
    state: CollectionsState,
    onLoad: () -> Unit,
    onToggle: (collectionId: Int, isMember: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Add to Collection",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        when {
            state.loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.items.isEmpty() -> Text(
                "No collections yet. Create one from the Collections tab.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )

            else -> state.items.forEach { collection ->
                val isMember = collection.memberCount > 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = state.togglingId == null) {
                            onToggle(collection.id, isMember)
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        collection.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    when {
                        state.togglingId == collection.id ->
                            CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                        isMember -> Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "In collection",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        else -> Icon(
                            Icons.Outlined.Circle,
                            contentDescription = "Not in collection",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
