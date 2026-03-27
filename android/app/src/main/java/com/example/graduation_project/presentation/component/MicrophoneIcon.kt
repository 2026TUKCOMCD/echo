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
 * 마이크 꺼짐 아이콘 ImageVector
 * material-icons-extended 의존성 없이 사용 가능
 */
private val MicOffIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MicOff",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // Microphone body with slash
            moveTo(19f, 11f)
            lineTo(17.3f, 11f)
            curveTo(17.3f, 11.74f, 17.14f, 12.43f, 16.87f, 13.05f)
            lineTo(18.1f, 14.28f)
            curveTo(18.66f, 13.3f, 19f, 12.19f, 19f, 11f)
            close()
            moveTo(14.98f, 11.17f)
            curveTo(14.98f, 11.11f, 15f, 11.06f, 15f, 11f)
            lineTo(15f, 5f)
            curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
            curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
            lineTo(9f, 5.18f)
            lineTo(14.98f, 11.17f)
            close()
            moveTo(4.27f, 3f)
            lineTo(3f, 4.27f)
            lineTo(9.01f, 10.28f)
            lineTo(9.01f, 11f)
            curveTo(9.01f, 12.66f, 10.34f, 14f, 12f, 14f)
            curveTo(12.22f, 14f, 12.44f, 13.97f, 12.65f, 13.92f)
            lineTo(14.31f, 15.58f)
            curveTo(13.6f, 15.91f, 12.81f, 16.1f, 12f, 16.1f)
            curveTo(9.24f, 16.1f, 6.7f, 14f, 6.7f, 11f)
            lineTo(5f, 11f)
            curveTo(5f, 14.41f, 7.72f, 17.23f, 11f, 17.72f)
            lineTo(11f, 21f)
            lineTo(13f, 21f)
            lineTo(13f, 17.72f)
            curveTo(13.91f, 17.59f, 14.77f, 17.27f, 15.54f, 16.82f)
            lineTo(19.73f, 21f)
            lineTo(21f, 19.73f)
            lineTo(4.27f, 3f)
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

/**
 * 마이크 꺼짐 아이콘 Composable
 * 어르신 접근성을 위해 기본 80.dp 크기
 */
@Composable
fun MicrophoneOffIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 80.dp,
    contentDescription: String = "마이크 꺼짐"
) {
    Icon(
        imageVector = MicOffIcon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.then(Modifier.size(size))
    )
}
