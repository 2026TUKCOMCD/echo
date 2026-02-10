package com.example.graduation_project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.graduation_project.presentation.conversation.ConversationScreen
import com.example.graduation_project.ui.theme.Graduation_projectTheme

/**
 * 앱의 메인 액티비티
 *
 * ## Jetpack Compose 구조
 * - ComponentActivity: Compose 전용 액티비티
 * - enableEdgeToEdge(): 시스템 바 영역까지 콘텐츠 확장
 * - setContent { }: Compose UI를 설정하는 진입점
 *
 * ## 화면 구성
 * - Graduation_projectTheme: Material 3 테마 적용
 * - ConversationScreen: 대화 메인 화면
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Graduation_projectTheme {
                // 대화 화면 표시
                ConversationScreen()
            }
        }
    }
}

// 앱 미리보기
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MainActivityPreview() {
    Graduation_projectTheme {
        ConversationScreen()
    }
}