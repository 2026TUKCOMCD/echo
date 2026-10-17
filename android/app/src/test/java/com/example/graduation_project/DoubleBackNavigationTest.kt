package com.example.graduation_project

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * 화면 전환 중 뒤로가기 - NavHost 기본 전환(700ms 페이드) 동안 나가는 화면이 남아 있어도,
 * 그 화면의 ← 연타나 뒤늦은 자동 복귀(대화 종료 2초 뒤 onBack)가 아래 화면까지 한 번 더 pop하면 안 된다.
 *
 * MainActivity와 같은 구조(홈 → 일기 → 일기 상세, 홈 → 대화)의 NavHost에 앱이 쓰는
 * [popBackOnlyWhenResumed]를 연결하고, 화면 시계를 직접 돌려 전환 도중의 입력을 재현한다.
 * 대화 화면의 자동 복귀는 ConversationScreen과 같은 방식(2초 뒤, 화면이 보일 때 onBack)으로 둔다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class DoubleBackNavigationTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var controller: ActivityController<ComponentActivity>
    private lateinit var activity: ComponentActivity
    private lateinit var navController: NavHostController

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity = controller.get()
        activity.setContent {
            val nav = rememberNavController().also { navController = it }
            NavHost(nav, startDestination = HOME) {
                composable(HOME) { Text("홈") }
                composable(HISTORY) { Text("일기") }
                composable(DIARY_DETAIL) {
                    val onBack = popBackOnlyWhenResumed(nav)
                    Button(onClick = onBack) { Text("뒤로") }
                }
                composable(CONVERSATION) {
                    val onBack = popBackOnlyWhenResumed(nav)
                    val lifecycle = LocalLifecycleOwner.current.lifecycle
                    // ConversationScreen: 대화가 끝나면 작별 화면을 2초 보여 준 뒤, 화면이 보일 때 onBack()
                    LaunchedEffect(Unit) {
                        delay(2_000)
                        lifecycle.withResumed { onBack() }
                    }
                    Text("대화")
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `일기 상세에서 ←를 연타해도 한 단계만 돌아간다`() {
        navigate(HISTORY)
        navigate(DIARY_DETAIL)

        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("뒤로").performClick()
        compose.mainClock.advanceTimeBy(100)
        // 전환 중 아직 남아 있는(나가는) 상세 화면의 ←를 한 번 더 누름
        compose.onNodeWithText("뒤로").performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertEquals(HISTORY, navController.currentDestination?.route)
    }

    @Test
    fun `대화 종료 후 자동 복귀 전에 back을 누르면 홈에 머문다`() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { navController.navigate(CONVERSATION) }
        compose.mainClock.advanceTimeBy(1_500)

        // 자동 복귀(2초) 전에 시스템 back → 대화 화면이 나가는 전환(700ms) 도중에 2초가 됨
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(1_500)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertEquals(HOME, navController.currentDestination?.route)
    }

    @Test
    fun `대화 종료 후 2초 안에 홈으로 나갔다 돌아오면 그때 이전 화면으로 돌아간다`() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { navController.navigate(CONVERSATION) }
        compose.mainClock.advanceTimeBy(1_500)

        // 자동 복귀(2초) 전에 홈 버튼으로 나갔다가 2초가 지난 뒤 다시 들어옴
        compose.runOnUiThread { controller.pause().stop() }
        compose.mainClock.advanceTimeBy(1_500)
        compose.runOnUiThread { controller.start().resume() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        // 복귀를 버리면 버튼 없는 작별 화면에 남는다
        assertEquals(HOME, navController.currentDestination?.route)
    }

    private fun navigate(route: String) {
        compose.runOnUiThread { navController.navigate(route) }
        compose.waitForIdle()
    }

    private companion object {
        const val HOME = "home"
        const val HISTORY = "history"
        const val DIARY_DETAIL = "diary_detail"
        const val CONVERSATION = "conversation"
    }
}
