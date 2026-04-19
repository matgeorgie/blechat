package com.bitchat.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.core.ui.component.sheet.BitchatBottomSheet
import com.bitchat.android.core.ui.component.sheet.BitchatSheetCenterTopBar
import com.bitchat.android.summary.ConversationSummaryResult
import com.bitchat.android.summary.ConversationSummarySheetData
import com.bitchat.android.summary.SummaryItem
import com.bitchat.android.ui.theme.BASE_FONT_SIZE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationSummarySheet(
    data: ConversationSummarySheetData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BitchatBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 64.dp, bottom = 24.dp)
            ) {
                item("overview") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Conversation Summary",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        data.summary.notificationText?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                        data.summary.mainTopic?.let {
                            Text(
                                text = "Focus: ${it.text}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                data.summary.importantUpdates.takeIf { it.isNotEmpty() }?.let { items ->
                    item("updates") {
                        SummarySection(
                            title = "Key updates",
                            items = items
                        )
                    }
                }

                data.summary.openQuestions.takeIf { it.isNotEmpty() }?.let { items ->
                    item("questions") {
                        SummarySection(
                            title = "Open questions",
                            items = items
                        )
                    }
                }

                data.summary.actionItems.takeIf { it.isNotEmpty() }?.let { items ->
                    item("actions") {
                        SummarySection(
                            title = "Action items",
                            items = items
                        )
                    }
                }

                data.summary.decisions.takeIf { it.isNotEmpty() }?.let { items ->
                    item("decisions") {
                        SummarySection(
                            title = "Decisions",
                            items = items
                        )
                    }
                }

                data.catchUpSummary?.takeIf { it.messageCount > 0 }?.let { catchUp ->
                    item("catch_up") {
                        CatchUpSection(catchUp)
                    }
                }
            }

            BitchatSheetCenterTopBar(
                onClose = onDismiss,
                modifier = Modifier.align(Alignment.TopCenter),
                title = {
                    Text(
                        text = data.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            )
        }
    }
}

@Composable
private fun SummarySection(
    title: String,
    items: List<SummaryItem>
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            color = colorScheme.outline.copy(alpha = 0.2f),
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = colorScheme.onSurface.copy(alpha = 0.75f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        items.forEach { item ->
            SummaryCard(item)
        }
    }
}

@Composable
private fun SummaryCard(item: SummaryItem) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = BASE_FONT_SIZE.sp
                ),
                color = colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CatchUpSection(catchUp: ConversationSummaryResult) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            color = colorScheme.outline.copy(alpha = 0.2f),
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "Since you last opened",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFFF9500),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        catchUp.notificationText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }
        SummarySection(
            title = "Catch-up highlights",
            items = catchUp.importantUpdates.take(2) + catchUp.openQuestions.take(1) + catchUp.actionItems.take(1)
        )
    }
}
