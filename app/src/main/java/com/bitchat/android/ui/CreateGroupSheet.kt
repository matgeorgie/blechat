package com.bitchat.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.R
import com.bitchat.android.core.ui.component.sheet.BitchatBottomSheet
import com.bitchat.android.core.ui.component.sheet.BitchatSheetCenterTopBar
import com.bitchat.android.ui.theme.BASE_FONT_SIZE

/**
 * Bottom sheet for creating a new WhatsApp-style group chat.
 *
 * Displays a group name input and a list of connected mesh peers to select as members.
 * On "Create", calls [ChatViewModel.createGroup] with the name and selected peer IDs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupSheet(
    isPresented: Boolean,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val peerNicknames by viewModel.peerNicknames.collectAsStateWithLifecycle()
    val nickname by viewModel.nickname.collectAsStateWithLifecycle()

    var groupName by remember { mutableStateOf("") }
    var selectedPeerIDs by remember { mutableStateOf(setOf<String>()) }

    val focusManager = LocalFocusManager.current

    // Filter out self from the peer list
    val selectablePeers = remember(connectedPeers) {
        connectedPeers.filter { it != viewModel.meshService.myPeerID }
    }

    val canCreate = groupName.isNotBlank() && selectedPeerIDs.isNotEmpty()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (isPresented) {
        BitchatBottomSheet(
            modifier = modifier,
            onDismissRequest = {
                // Reset state on dismiss
                groupName = ""
                selectedPeerIDs = emptySet()
                onDismiss()
            },
            sheetState = sheetState,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 64.dp)
                ) {
                    // Group name input
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it.take(40) },
                        label = { Text(stringResource(R.string.group_name_hint)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF9500),
                            cursorColor = Color(0xFFFF9500),
                            focusedLabelColor = Color(0xFFFF9500)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    )

                    // Section header
                    Text(
                        text = stringResource(R.string.select_members).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onSurface.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 16.dp, bottom = 4.dp)
                    )

                    if (selectablePeers.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_peers_for_group),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            color = colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 40.dp, vertical = 12.dp)
                        )
                    }

                    // Peer selection list
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(
                            items = selectablePeers,
                            key = { "create_group_peer_$it" }
                        ) { peerID ->
                            val displayName = peerNicknames[peerID] ?: peerID.take(12)
                            val isSelected = selectedPeerIDs.contains(peerID)

                            // Compute peer color (iOS-compatible)
                            val isDark = colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue < 1.5f
                            val peerColor = viewModel.colorForMeshPeer(peerID, isDark)

                            val bgColor by animateColorAsState(
                                targetValue = if (isSelected) colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent,
                                label = "selectBg"
                            )

                            Surface(
                                onClick = {
                                    selectedPeerIDs = if (isSelected) {
                                        selectedPeerIDs - peerID
                                    } else {
                                        selectedPeerIDs + peerID
                                    }
                                },
                                color = bgColor,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = peerColor
                                        )

                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = BASE_FONT_SIZE.sp
                                            ),
                                            color = peerColor
                                        )
                                    }

                                    // Checkbox indicator
                                    Icon(
                                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                        contentDescription = if (isSelected) stringResource(R.string.cd_selected) else null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (isSelected) Color(0xFF00C851) else colorScheme.onSurface.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }

                    // Create button at the bottom
                    Button(
                        onClick = {
                            viewModel.createGroup(groupName.trim(), selectedPeerIDs.toList())
                            groupName = ""
                            selectedPeerIDs = emptySet()
                            onDismiss()
                        },
                        enabled = canCreate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9500),
                            disabledContainerColor = colorScheme.onSurface.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .height(48.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.create_group),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Top bar
                BitchatSheetCenterTopBar(
                    onClose = {
                        groupName = ""
                        selectedPeerIDs = emptySet()
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                    title = {
                        Text(
                            text = stringResource(R.string.new_group),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                )
            }
        }
    }
}
