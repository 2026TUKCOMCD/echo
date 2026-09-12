package com.example.graduation_project.presentation.settings

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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

// 경도인지장애 어르신 사용성을 고려해 이 다이얼로그들은 앱 전반의 기본값보다 한 단계 큰
// 글자 크기를 쓴다(설정의 "글씨 크기" 배율이 여기에도 곱해져 추가로 커질 수 있음).
private val TITLE_SIZE = 24.sp
private val SECTION_TITLE_SIZE = 19.sp
private val BODY_SIZE = 17.sp
private val CAPTION_SIZE = 16.sp
private val BUTTON_TEXT_SIZE = 17.sp

private fun formatSchedule(place: RoutinePlaceResponse): String {
    val days = place.routineDays.joinToString(",") { WEEKDAY_KOREAN[it] ?: it }
    val time = if (place.routineTimeRangeStart != null && place.routineTimeRangeEnd != null) {
        "${place.routineTimeRangeStart?.take(5)}~${place.routineTimeRangeEnd?.take(5)}"
    } else null
    return listOfNotNull(days.ifBlank { null }, time).joinToString(" · ").ifBlank { "요일·시간대 파악 중" }
}

/** 요일 다중 선택 칩 - "카드에서 수정할 때 요일/시간도 고칠 수 있으면 좋겠다"는 요청으로 추가 */
@Composable
private fun WeekdaySelector(selected: Set<String>, onToggle: (String) -> Unit, enabled: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        WEEKDAY_KOREAN.forEach { (value, label) ->
            FilterChip(
                selected = value in selected,
                onClick = { onToggle(value) },
                enabled = enabled,
                label = { Text(label, fontSize = CAPTION_SIZE) },
                modifier = Modifier.height(44.dp)
            )
        }
    }
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
                    fontSize = TITLE_SIZE,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                ConsentSection(
                    title = "이런 기능이에요",
                    body = "회사, 병원, 복지관처럼 자주 가시는 곳을 기억해두었다가 대화에 " +
                        "자연스럽게 활용해요. 오늘 다녀오신 곳을 여쭤보는 기존 대화 기능과는 " +
                        "별개의 저장 기능이에요."
                )
                Spacer(Modifier.height(16.dp))
                ConsentSection(
                    title = "무엇을 저장하나요",
                    body = "방문 좌표와 방문 요일·시간대만 저장해요. 정확한 상호명·주소는 " +
                        "저장하지 않고, 확인이 필요할 때만 그때그때 조회해요."
                )
                Spacer(Modifier.height(16.dp))
                ConsentSection(
                    title = "얼마나 보관하나요",
                    body = "패턴 분석용 임시 방문 기록은 최근 6주만 보관 후 자동 삭제돼요. " +
                        "직접 확정하신 장소 라벨은 삭제하시기 전까지 보관돼요."
                )
                Spacer(Modifier.height(16.dp))
                ConsentSection(
                    title = "언제든 삭제할 수 있어요",
                    body = "설정 화면에서 개별 장소를 삭제할 수 있고, 동의를 철회하면 저장된 " +
                        "모든 관련 정보가 즉시 삭제돼요."
                )

                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = onAgree,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "동의하고 시작", fontSize = 19.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "다음에", fontSize = 19.sp)
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
            fontSize = SECTION_TITLE_SIZE,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            fontSize = BODY_SIZE,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
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
    onUpdatePlace: (id: Long, category: String, days: List<String>, start: String?, end: String?) -> Unit,
    onDeletePlace: (Long) -> Unit,
    onToggleDetection: (Boolean) -> Unit,
    onWithdrawConsent: () -> Unit,
    onDismiss: () -> Unit
) {
    var showWithdrawConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .padding(24.dp)
            ) {
                Text(
                    text = "반복 방문 장소",
                    fontSize = TITLE_SIZE,
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
                            fontSize = SECTION_TITLE_SIZE,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(10.dp))
                        uiState.candidates.forEach { candidate ->
                            CandidateRow(
                                candidate = candidate,
                                isProcessing = uiState.isProcessing,
                                onConfirm = { category -> onConfirmCandidate(candidate.id, category) },
                                onDismiss = { onDismissCandidate(candidate.id) }
                            )
                            Spacer(Modifier.height(14.dp))
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    }

                    Text(
                        text = "확정된 장소",
                        fontSize = SECTION_TITLE_SIZE,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    if (uiState.confirmedPlaces.isEmpty()) {
                        Text(
                            text = "아직 확정된 장소가 없어요. 반복해서 방문하시면 며칠 후 후보로 제안해드릴게요.",
                            fontSize = BODY_SIZE,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 24.sp
                        )
                    } else {
                        uiState.confirmedPlaces.forEach { place ->
                            ConfirmedPlaceRow(
                                place = place,
                                isProcessing = uiState.isProcessing,
                                onUpdate = { category, days, start, end -> onUpdatePlace(place.id, category, days, start, end) },
                                onDelete = { onDeletePlace(place.id) }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "반복 방문 장소 감지",
                            fontSize = SECTION_TITLE_SIZE,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (uiState.consented) "켜짐 · 새 패턴을 계속 찾아요" else "꺼짐 · 확정해둔 장소는 그대로 남아있어요",
                            fontSize = CAPTION_SIZE,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.consented,
                        onCheckedChange = onToggleDetection,
                        enabled = !uiState.isProcessing
                    )
                }

                Spacer(Modifier.height(10.dp))

                TextButton(onClick = { showWithdrawConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "동의 완전 철회 (확정 장소 포함 모든 정보 삭제)",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = CAPTION_SIZE
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "닫기", fontSize = BUTTON_TEXT_SIZE)
                }
            }
        }
    }

    if (showWithdrawConfirm) {
        AlertDialog(
            onDismissRequest = { showWithdrawConfirm = false },
            title = { Text("정말 철회하시겠어요?", fontSize = SECTION_TITLE_SIZE, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "확정해두신 장소를 포함해 저장된 모든 정보가 즉시 삭제되며 되돌릴 수 없어요.",
                    fontSize = BODY_SIZE,
                    lineHeight = 24.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWithdrawConfirm = false
                    onWithdrawConsent()
                }) {
                    Text("철회 및 삭제", color = MaterialTheme.colorScheme.error, fontSize = BUTTON_TEXT_SIZE)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawConfirm = false }) {
                    Text("취소", fontSize = BUTTON_TEXT_SIZE)
                }
            }
        )
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
            fontSize = BODY_SIZE,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = formatSchedule(candidate),
            fontSize = CAPTION_SIZE,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                placeholder = { Text("예: 회사, 병원", fontSize = BODY_SIZE) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = BODY_SIZE),
                singleLine = true,
                enabled = !isProcessing,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onConfirm(label) },
                enabled = !isProcessing && label.isNotBlank()
            ) {
                Text("확정", fontSize = BUTTON_TEXT_SIZE)
            }
        }
        TextButton(onClick = onDismiss, enabled = !isProcessing) {
            Text("아니에요", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = BUTTON_TEXT_SIZE)
        }
    }
}

@Composable
private fun ConfirmedPlaceRow(
    place: RoutinePlaceResponse,
    isProcessing: Boolean,
    onUpdate: (category: String, days: List<String>, start: String?, end: String?) -> Unit,
    onDelete: () -> Unit
) {
    var editing by remember(place.id) { mutableStateOf(false) }
    var label by remember(place.id) { mutableStateOf(place.category.orEmpty()) }
    var selectedDays by remember(place.id) { mutableStateOf(place.routineDays.toSet()) }
    var startTime by remember(place.id) { mutableStateOf(place.routineTimeRangeStart?.take(5).orEmpty()) }
    var endTime by remember(place.id) { mutableStateOf(place.routineTimeRangeEnd?.take(5).orEmpty()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (editing) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = BODY_SIZE),
                        singleLine = true,
                        enabled = !isProcessing,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(text = place.category ?: "(라벨 없음)", fontSize = SECTION_TITLE_SIZE, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = formatSchedule(place), fontSize = CAPTION_SIZE, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (editing) {
                TextButton(
                    onClick = {
                        editing = false
                        onUpdate(label, selectedDays.toList(), startTime.trim().ifBlank { null }, endTime.trim().ifBlank { null })
                    },
                    enabled = !isProcessing && label.isNotBlank()
                ) { Text("저장", fontSize = BUTTON_TEXT_SIZE) }
            } else {
                TextButton(onClick = { editing = true }, enabled = !isProcessing) { Text("수정", fontSize = BUTTON_TEXT_SIZE) }
            }
            TextButton(onClick = onDelete, enabled = !isProcessing) {
                Text("삭제", color = MaterialTheme.colorScheme.error, fontSize = BUTTON_TEXT_SIZE)
            }
        }

        if (editing) {
            Spacer(Modifier.height(8.dp))
            Text("요일", fontSize = CAPTION_SIZE, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            WeekdaySelector(
                selected = selectedDays,
                onToggle = { day -> selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day },
                enabled = !isProcessing
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text("시작 (HH:mm)", fontSize = CAPTION_SIZE) },
                    placeholder = { Text("09:00", fontSize = BODY_SIZE) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = BODY_SIZE),
                    singleLine = true,
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    label = { Text("종료 (HH:mm)", fontSize = CAPTION_SIZE) },
                    placeholder = { Text("18:00", fontSize = BODY_SIZE) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = BODY_SIZE),
                    singleLine = true,
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
