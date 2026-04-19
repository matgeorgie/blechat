package com.bitchat.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.R
import com.bitchat.android.core.ui.component.sheet.BitchatBottomSheet
import com.bitchat.android.core.ui.component.sheet.BitchatSheetCenterTopBar
import com.bitchat.android.ui.theme.BASE_FONT_SIZE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoSheet(
    isPresented: Boolean,
    groupID: String,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isPresented) return

    val colorScheme = MaterialTheme.colorScheme
    val groupInfoMap by viewModel.groupInfoMap.collectAsStateWithLifecycle()
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val peerNicknames by viewModel.peerNicknames.collectAsStateWithLifecycle()
    val myNickname by viewModel.nickname.collectAsStateWithLifecycle()

    val groupInfo = groupInfoMap[groupID]
    val isAdmin = remember(groupInfo, viewModel.meshService.myPeerID) {
        groupInfo?.isAdmin(viewModel.meshService.myPeerID) == true
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var draftName by remember(groupID) { mutableStateOf("") }
    var draftDescription by remember(groupID) { mutableStateOf("") }
    var selectedAddPeerIDs by remember(groupID) { mutableStateOf(setOf<String>()) }

    LaunchedEffect(groupInfo?.updatedAtMs, groupInfo?.id) {
        if (groupInfo == null) {
            onDismiss()
        } else {
            draftName = groupInfo.name
            draftDescription = groupInfo.description
            selectedAddPeerIDs = emptySet()
        }
    }

    val eligiblePeers = remember(groupInfo, connectedPeers) {
        val memberIDs = groupInfo?.memberPeerIDs.orEmpty().toSet()
        connectedPeers
            .filter { it != viewModel.meshService.myPeerID && !memberIDs.contains(it) }
            .distinct()
    }
    val canSaveDetails = isAdmin &&
        groupInfo != null &&
        draftName.trim().isNotEmpty() &&
        (draftName.trim() != groupInfo.name || draftDescription.trim() != groupInfo.description)

    BitchatBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Group,
                                contentDescription = null,
                                tint = Color(0xFFFF9500),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.group_info),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }

                        TextButton(
                            onClick = viewModel::showCurrentConversationSummary,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = stringResource(R.string.conversation_summary),
                                color = Color(0xFFFF9500),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (isAdmin) {
                            OutlinedTextField(
                                value = draftName,
                                onValueChange = { draftName = it.take(40) },
                                label = { Text(stringResource(R.string.group_name_hint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = draftDescription,
                                onValueChange = { draftDescription = it.take(160) },
                                label = { Text(stringResource(R.string.group_description_hint)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (viewModel.updateGroupDetails(groupID, draftName.trim(), draftDescription.trim())) {
                                        draftName = draftName.trim()
                                        draftDescription = draftDescription.trim()
                                    }
                                },
                                enabled = canSaveDetails,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF9500),
                                    disabledContainerColor = colorScheme.onSurface.copy(alpha = 0.12f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.save_group_changes),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = groupInfo?.name.orEmpty(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            if (!groupInfo?.description.isNullOrBlank()) {
                                Text(
                                    text = groupInfo?.description.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurface.copy(alpha = 0.72f)
                                )
                            }
                        }
                    }
                }

                item("members_header") {
                    GroupSectionHeader(text = stringResource(R.string.group_members_title))
                }

                items(groupInfo?.memberPeerIDs.orEmpty(), key = { "member_$it" }) { memberPeerID ->
                    val displayName = groupInfo?.memberNicknames?.get(memberPeerID)
                        ?: peerNicknames[memberPeerID]
                        ?: memberPeerID.take(12)
                    val isMemberAdmin = groupInfo?.adminPeerIDs?.contains(memberPeerID) == true
                    val isMe = memberPeerID == viewModel.meshService.myPeerID
                    val isDark = colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue < 1.5f
                    val peerColor = viewModel.colorForMeshPeer(memberPeerID, isDark)

                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = peerColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isMe) "$displayName (${stringResource(R.string.you_label)})" else displayName,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = BASE_FONT_SIZE.sp
                                    ),
                                    color = peerColor
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isMemberAdmin) {
                                    Text(
                                        text = stringResource(R.string.group_admin_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFFF9500),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (isAdmin && !isMe) {
                                    IconButton(
                                        onClick = { viewModel.removeGroupMember(groupID, memberPeerID) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = stringResource(R.string.remove_member),
                                            tint = colorScheme.onSurface.copy(alpha = 0.55f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isAdmin) {
                    item("add_header") {
                        GroupSectionHeader(text = stringResource(R.string.add_members))
                    }

                    if (eligiblePeers.isEmpty()) {
                        item("no_addable_members") {
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
                    } else {
                        items(eligiblePeers, key = { "eligible_$it" }) { peerID ->
                            val displayName = peerNicknames[peerID] ?: peerID.take(12)
                            val isSelected = selectedAddPeerIDs.contains(peerID)
                            val isDark = colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue < 1.5f
                            val peerColor = viewModel.colorForMeshPeer(peerID, isDark)
                            val backgroundColor by animateColorAsState(
                                targetValue = if (isSelected) colorScheme.primaryContainer.copy(alpha = 0.18f) else Color.Transparent,
                                label = "groupAddMemberBg"
                            )

                            Surface(
                                onClick = {
                                    selectedAddPeerIDs = if (isSelected) {
                                        selectedAddPeerIDs - peerID
                                    } else {
                                        selectedAddPeerIDs + peerID
                                    }
                                },
                                color = backgroundColor,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Person,
                                            contentDescription = null,
                                            tint = peerColor,
                                            modifier = Modifier.size(16.dp)
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
                                    Icon(
                                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                        contentDescription = null,
                                        tint = if (isSelected) Color(0xFF00C851) else colorScheme.onSurface.copy(alpha = 0.3f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        item("add_button") {
                            Button(
                                onClick = {
                                    if (viewModel.addGroupMembers(groupID, selectedAddPeerIDs.toList())) {
                                        selectedAddPeerIDs = emptySet()
                                    }
                                },
                                enabled = selectedAddPeerIDs.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF9500),
                                    disabledContainerColor = colorScheme.onSurface.copy(alpha = 0.12f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.add_members),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                item("leave_button") {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            viewModel.leaveGroup(groupID)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.leave_group),
                            color = Color(0xFFFF3B30),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            BitchatSheetCenterTopBar(
                onClose = onDismiss,
                modifier = Modifier.align(Alignment.TopCenter),
                title = {
                    Text(
                        text = groupInfo?.name ?: myNickname,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            )
        }
    }
}

@Composable
private fun GroupSectionHeader(text: String) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            color = colorScheme.outline.copy(alpha = 0.2f),
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}
