package com.example.graduation_project.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.graduation_project.ui.theme.EchoAccentGreen
import com.example.graduation_project.ui.theme.EchoBgCard
import com.example.graduation_project.ui.theme.EchoBorderSubtle
import com.example.graduation_project.ui.theme.EchoTextPrimary
import com.example.graduation_project.ui.theme.EchoTextTertiary
import com.example.graduation_project.ui.theme.OutfitFontFamily
import java.time.LocalDate

/** "yyyy-MM-dd" → (년, 월, 일) 문자열. 빈 문자열이면 ("", "", "") */
internal fun parseBirthday(value: String): Triple<String, String, String> {
    if (value.isBlank()) return Triple("", "", "")
    return runCatching {
        val d = LocalDate.parse(value)
        Triple(d.year.toString(), d.monthValue.toString(), d.dayOfMonth.toString())
    }.getOrDefault(Triple("", "", ""))
}

/** (년, 월, 일) → "yyyy-MM-dd". 유효하지 않으면 null */
internal fun buildBirthdayString(year: String, month: String, day: String): String? {
    val y = year.toIntOrNull() ?: return null
    val m = month.toIntOrNull() ?: return null
    val d = day.toIntOrNull() ?: return null
    return runCatching { LocalDate.of(y, m, d).toString() }.getOrNull()
}

/** 년/월/일 세 칸 직접 입력 Row. 온보딩 스텝과 설정 다이얼로그 양쪽에서 재사용 */
@Composable
internal fun BirthdayInputRow(
    year: String,
    month: String,
    day: String,
    onYearChange: (String) -> Unit,
    onMonthChange: (String) -> Unit,
    onDayChange: (String) -> Unit,
    isError: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BirthdayField(
            value = year,
            suffix = "년",
            maxLength = 4,
            placeholder = "1952",
            weight = 2.2f,
            onValueChange = onYearChange,
            isError = isError
        )
        BirthdayField(
            value = month,
            suffix = "월",
            maxLength = 2,
            placeholder = "1",
            weight = 1f,
            onValueChange = onMonthChange,
            isError = isError
        )
        BirthdayField(
            value = day,
            suffix = "일",
            maxLength = 2,
            placeholder = "1",
            weight = 1f,
            onValueChange = onDayChange,
            isError = isError
        )
    }
}

@Composable
private fun RowScope.BirthdayField(
    value: String,
    suffix: String,
    maxLength: Int,
    placeholder: String,
    weight: Float,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(v.filter { it.isDigit() }.take(maxLength)) },
        suffix = {
            Text(
                text = suffix,
                fontFamily = OutfitFontFamily,
                fontSize = 16.sp,
                color = EchoTextTertiary
            )
        },
        placeholder = {
            Text(
                text = placeholder,
                fontFamily = OutfitFontFamily,
                fontSize = 20.sp,
                color = EchoTextTertiary
            )
        },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(
            fontFamily = OutfitFontFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = EchoTextPrimary
        ),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = EchoBgCard,
            unfocusedContainerColor = EchoBgCard,
            focusedBorderColor = EchoAccentGreen,
            unfocusedBorderColor = EchoBorderSubtle,
            focusedTextColor = EchoTextPrimary,
            unfocusedTextColor = EchoTextPrimary,
            errorContainerColor = EchoBgCard
        ),
        modifier = Modifier.weight(weight)
    )
}
