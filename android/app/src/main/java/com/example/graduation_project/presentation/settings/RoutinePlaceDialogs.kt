package com.example.graduation_project.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.graduation_project.data.model.RoutinePlaceResponse

private val WEEKDAY_KOREAN = mapOf(
    "MONDAY" to "월", "TUESDAY" to "화", "WEDNESDAY" to "수", "THURSDAY" to "목",
    "FRIDAY" to "금", "SATURDAY" to "토", "SUNDAY" to "일"
)

private fun formatSchedule(place: RoutinePlaceResponse): String {
    val days = place.routineDays.joinToString(",") { WEEKDAY_KOREAN[it] ?: it }
    val time = if (place.routineTimeRangeStart != null && place.routineTimeRangeEnd != null) {
        "${place.routineTimeRangeStart?.take(5)}~${place.routineTimeRangeEnd?.take(5)}"
    } else null
    return listOfNotNull(days.ifBlank { null }, time).joinToString(" · ").ifBlank { "요일·시간대 파악 중" }
}

/**
 * 반복 방문 장소(회사/병원 등) 감지 기능 동의 안내.
 *
 * 목적·수집 정보·보유 기간·삭제 방법을 명시한다(현재 앱에 위치 관련 별도 동의 화면이
 * 없었던 공백을 이 기능에서부터 채움). 동의 전까지는 서버가 방문 이력을 저장하지 않는다.
 */
@Composable
fun RoutinePlaceConsentDialog(
    onAgree: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "반복 방문 장소 감지",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))

                ConsentSection(
                    title = "이런 기능이에요",
                    body = "회사, 병원, 복지관처럼 자주 가시는 곳을 기억해두었다가 대화에 " +
                        "자연스럽게 활용해요. 오늘 다녀오신 곳을 여쭤보는 기존 대화 기능과는 " +
                        "별개의 저장 기능이에요."
                )
                Spacer(Modifier.height(12.dp))
                ConsentSection(
                    title = "무엇을 저장하나요",
                    body = "방문 좌표와 방문 요일·시간대만 저장해요. 정확한 상호명·주소는 " +
                        "저장하지 않고, 확인이 필요할 때만 그때그때 조회해요."
                )
                Spacer(Modifier.height(12.dp))
                ConsentSection(
                    title = "얼마나 보관하나요",
                    body = "패턴 분석용 임시 방문 기록은 최근 6주만 보관 후 자동 삭제돼요. " +
                        "직접 확정하신 장소 라벨은 삭제하시기 전까지 보관돼요."
                )
                Spacer(Modifier.height(12.dp))
                ConsentSection(
                    title = "언제든 삭제할 수 있어요",
                    body = "설정 화면에서 개별 장소를 삭제할 수 있고, 동의를 철회하면 저장된 " +
                        "모든 관련 정보가 즉시 삭제돼요."
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = onAgree,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "동의하고 시작", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "다음에", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun ConsentSection(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}

/**
 * 반복 방문 장소 관리 - 확인 대기 중인 후보와 이미 확정된 장소를 함께 보여준다.
 *
 * 후보는 시스템이 반복 패턴을 감지해 제안한 것으로, 사용자가 라벨을 붙여 확정해야만
 * 대화에 실제로 쓰인다("집 등록"과 동일하게 자기결정권을 보장).
 */
@Composable
fun RoutinePlaceManageDialog(
    uiState: RoutinePlaceUiState,
    onConfirmCandidate: (Long, String) -> Unit,
    onDismissCandidate: (Long) -> Unit,
    onUpdateCategory: (Long, String) -> Unit,
    onDeletePlace: (Long) -> Unit,
    onRevokeConsent: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .padding(24.dp)
            ) {
                Text(
                    text = "반복 방문 장소",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (uiState.candidates.isNotEmpty()) {
                        Text(
                            text = "이런 곳에 자주 가시는 것 같아요",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        uiState.candidates.forEach { candidate ->
                            CandidateRow(
                                candidate = candidate,
                                isProcessing = uiState.isProcessing,
                                onConfirm = { category -> onConfirmCandidate(candidate.id, category) },
                                onDismiss = { onDismissCandidate(candidate.id) }
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    Text(
                        text = "확정된 장소",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    if (uiState.confirmedPlaces.isEmpty()) {
                        Text(
                            text = "아직 확정된 장소가 없어요. 반복해서 방문하시면 며칠 후 후보로 제안해드릴게요.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    } else {
                        uiState.confirmedPlaces.forEach { place ->
                            ConfirmedPlaceRow(
                                place = place,
                                isProcessing = uiState.isProcessing,
                                onUpdateCategory = { category -> onUpdateCategory(place.id, category) },
                                onDelete = { onDeletePlace(place.id) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = onRevokeConsent, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "감지 기능 끄고 저장된 정보 삭제", color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "닫기", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: RoutinePlaceResponse,
    isProcessing: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember(candidate.id) { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = candidate.previewAddress ?: "장소 확인 중",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = formatSchedule(candidate),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                placeholder = { Text("예: 회사, 병원") },
                singleLine = true,
                enabled = !isProcessing,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onConfirm(label) },
                enabled = !isProcessing && label.isNotBlank()
            ) {
                Text("확정")
            }
        }
        TextButton(onClick = onDismiss, enabled = !isProcessing) {
            Text("아니에요", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConfirmedPlaceRow(
    place: RoutinePlaceResponse,
    isProcessing: Boolean,
    onUpdateCategory: (String) -> Unit,
    onDelete: () -> Unit
) {
    var editing by remember(place.id) { mutableStateOf(false) }
    var label by remember(place.id) { mutableStateOf(place.category.orEmpty()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (editing) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(text = place.category ?: "(라벨 없음)", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(text = formatSchedule(place), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (editing) {
            TextButton(
                onClick = {
                    editing = false
                    onUpdateCategory(label)
                },
                enabled = !isProcessing && label.isNotBlank()
            ) { Text("저장") }
        } else {
            TextButton(onClick = { editing = true }, enabled = !isProcessing) { Text("수정") }
        }
        TextButton(onClick = onDelete, enabled = !isProcessing) {
            Text("삭제", color = MaterialTheme.colorScheme.error)
        }
    }
}
