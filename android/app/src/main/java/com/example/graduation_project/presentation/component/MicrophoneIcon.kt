package com.example.graduation_project.presentation.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 마이크 아이콘 ImageVector
 * material-icons-extended 의존성 없이 사용 가능
 *
 * [T2.2-5] 녹음 상태 시각적 피드백
 */
private val MicIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Mic",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // Microphone body
            moveTo(12f, 14f)
            curveTo(13.66f, 14f, 14.99f, 12.66f, 14.99f, 11f)
            lineTo(15f, 5f)
            curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
            curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
            lineTo(9f, 11f)
            curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
            close()
            // Microphone stand
            moveTo(17.3f, 11f)
            curveTo(17.3f, 14f, 14.76f, 16.1f, 12f, 16.1f)
            curveTo(9.24f, 16.1f, 6.7f, 14f, 6.7f, 11f)
            lineTo(5f, 11f)
            curveTo(5f, 14.41f, 7.72f, 17.23f, 11f, 17.72f)
            lineTo(11f, 21f)
            lineTo(13f, 21f)
            lineTo(13f, 17.72f)
            curveTo(16.28f, 17.23f, 19f, 14.41f, 19f, 11f)
            lineTo(17.3f, 11f)
            close()
        }
    }.build()
}

/**
 * 마이크 아이콘 Composable
 * 어르신 접근성을 위해 기본 80.dp 크기
 *
 * [T2.2-5] 녹음 상태 시각적 피드백
 */
@Composable
fun MicrophoneIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 80.dp,
    contentDescription: String = "마이크"
) {
    Icon(
        imageVector = MicIcon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.then(Modifier.size(size))
    )
}
