package com.example.graduation_project.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.graduation_project.ui.theme.EchoAccentGreen
import com.example.graduation_project.ui.theme.EchoTextPrimary
import com.example.graduation_project.ui.theme.EchoTextSecondary
import com.example.graduation_project.ui.theme.OutfitFontFamily

/** "HH:mm" → (isAm, hour 1-12, minute 0-55 rounded to nearest 5) */
internal fun parseTime(value: String): Triple<Boolean, Int, Int> {
    if (value.isBlank()) return Triple(true, 9, 0)
    return runCatching {
        val (hStr, mStr) = value.split(":")
        val h = hStr.toInt()
        val m = (mStr.toInt() / 5) * 5
        val isAm = h < 12
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        Triple(isAm, h12, m)
    }.getOrDefault(Triple(true, 9, 0))
}

/** (isAm, hour 1-12, minute 0-55) → "HH:mm" */
internal fun buildTimeString(isAm: Boolean, hour: Int, minute: Int): String {
    val h24 = when {
        isAm && hour == 12 -> 0
        !isAm && hour == 12 -> 12
        !isAm -> hour + 12
        else -> hour
    }
    return "%02d:%02d".format(h24, minute)
}

/** 오전/오후 + 시/분 스테퍼. 온보딩 스텝과 설정 다이얼로그 양쪽에서 재사용 */
@Composable
internal fun EchoTimePickerContent(
    value: String,
    onValueChange: (String) -> Unit
) {
    val initial = remember { parseTime(value) }
    var isAm by remember { mutableStateOf(initial.first) }
    var hour by remember { mutableStateOf(initial.second) }
    var minute by remember { mutableStateOf(initial.third) }

    fun notify() = onValueChange(buildTimeString(isAm, hour, minute))

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(true to "오전", false to "오후").forEach { (amVal, label) ->
                FilterChip(
                    selected = isAm == amVal,
                    onClick = { isAm = amVal; notify() },
                    label = {
                        Text(
                            label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = OutfitFontFamily
                        )
                    },
                    modifier = Modifier.height(44.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EchoAccentGreen,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            TimeSpinner(
                display = hour.toString(),
                label = "시",
                onUp = { hour = if (hour == 12) 1 else hour + 1; notify() },
                onDown = { hour = if (hour == 1) 12 else hour - 1; notify() }
            )
            Text(
                text = ":",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OutfitFontFamily,
                color = EchoTextPrimary,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 20.dp)
            )
            TimeSpinner(
                display = "%02d".format(minute),
                label = "분",
                onUp = { minute = (minute + 5) % 60; notify() },
                onDown = { minute = if (minute == 0) 55 else minute - 5; notify() }
            )
        }
    }
}

@Composable
private fun TimeSpinner(
    display: String,
    label: String,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onUp, modifier = Modifier.size(52.dp)) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowUp,
                contentDescription = "증가",
                tint = EchoAccentGreen,
                modifier = Modifier.size(36.dp)
            )
        }
        Text(
            text = display,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OutfitFontFamily,
            color = EchoTextPrimary
        )
        IconButton(onClick = onDown, modifier = Modifier.size(52.dp)) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = "감소",
                tint = EchoAccentGreen,
                modifier = Modifier.size(36.dp)
            )
        }
        Text(
            text = label,
            fontSize = 15.sp,
            fontFamily = OutfitFontFamily,
            color = EchoTextSecondary
        )
    }
}
