package com.tukcomcd.echo.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Recording state colors
// 경도인지장애 어르신 접근성: 따뜻한 청록색 (황변화 영향 적고 친근함)
val ListeningTeal = Color(0xFF00897B)        // Teal 700 - LISTENING + RECORDING 통일
val ListeningTealLight = Color(0xFF80CBC4)   // Teal 200 - 파동 효과용

// Legacy colors (하위 호환)
val ListeningBlue = Color(0xFF42A5F5)
val ListeningBlueLight = Color(0xFFBBDEFB)
val RecordingGreen = Color(0xFF66BB6A)
val RecordingGreenLight = Color(0xFFC8E6C9)
val IdleGray = Color(0xFF9E9E9E)
val PreparingAmber = Color(0xFFFFA726)

// Playback state colors (T2.3-2)
// 경도인지장애 어르신 접근성: 따뜻한 앰버색 (눈에 평안하고 친근함)
val PlayingAmber = Color(0xFFFFA000)         // Amber 700 - 따뜻하고 차분, 눈 피로감 적음
val PlayingAmberLight = Color(0xFFFFE082)    // Amber 200 - 파동 효과용

// Legacy colors (하위 호환)
val PlayingGreen = Color(0xFF43A047)
val PlayingGreenLight = Color(0xFFA5D6A7)
val PreparingOrange = Color(0xFFF57C00)

// Processing state color
val ProcessingGray = Color(0xFF757575)

// Character video background color (영상 배경색과 동일)
val CharacterVideoBgColor = Color(0xFF5A5A5A)