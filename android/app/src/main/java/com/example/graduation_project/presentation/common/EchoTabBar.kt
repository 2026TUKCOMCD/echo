package com.example.graduation_project.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.graduation_project.ui.theme.EchoAccentGreen
import com.example.graduation_project.ui.theme.EchoBgCard
import com.example.graduation_project.ui.theme.EchoBorderSubtle
import com.example.graduation_project.ui.theme.EchoTabInactive
import com.example.graduation_project.ui.theme.OutfitFontFamily

enum class EchoTab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "홈", Icons.Outlined.Home),
    HISTORY("history", "대화기록", Icons.Outlined.Forum),
    SETTINGS("settings", "설정", Icons.Outlined.Settings)
}

@Composable
fun EchoTabBar(
    currentRoute: String?,
    onTabSelected: (EchoTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = EchoBgCard,
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 21.dp)
                .padding(top = 12.dp, bottom = 12.dp)
                .navigationBarsPadding()
                .clip(RoundedCornerShape(36.dp))
                .border(1.dp, EchoBorderSubtle, RoundedCornerShape(36.dp))
                .background(EchoBgCard)
                .height(62.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoTab.entries.forEach { tab ->
                val isActive = currentRoute == tab.route
                EchoTabItem(
                    tab = tab,
                    isActive = isActive,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EchoTabItem(
    tab: EchoTab,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillShape = RoundedCornerShape(28.dp)

    Column(
        modifier = modifier
            .height(62.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .clip(pillShape)
            .then(
                if (isActive) Modifier.background(EchoAccentGreen)
                else Modifier
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = if (isActive) Color.White else EchoTabInactive,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = tab.label,
            color = if (isActive) Color.White else EchoTabInactive,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = OutfitFontFamily,
            lineHeight = 14.sp
        )
    }
}
