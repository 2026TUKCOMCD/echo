package com.example.graduation_project

import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavDestination
import androidx.navigation.NavDestinationBuilder
import androidx.navigation.NavHostController
import androidx.navigation.Navigator
import androidx.navigation.createGraph
import androidx.navigation.get
import com.example.graduation_project.presentation.common.EchoTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 로그아웃하면 탭 전환 때 저장해 둔 탭 상태(ViewModel)까지 지워져, 같은 화면에서 다른 계정으로 로그인해도
 * 이전 계정의 화면 상태가 복원되지 않아야 한다 (DiaryViewModel은 생성할 때 userId를 한 번만 읽음).
 *
 * MainActivity와 같은 탭 전환(navigateToTab)·로그아웃(navigateToLoginAfterLogout) 함수를 쓰고,
 * Compose 없이 백스택·상태 저장만 검증하도록 전환 애니메이션 없이 바로 이동하는 최소 Navigator를 쓴다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class LogoutNavigationTest {

    @Navigator.Name("screen")
    class ScreenNavigator : Navigator<NavDestination>() {
        override fun createDestination() = NavDestination(this)
    }

    /** 탭 화면의 ViewModel 자리 (계정 A의 DiaryViewModel 역할) */
    class TabViewModel : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    private lateinit var navController: NavHostController

    @Before
    fun setUp() {
        val lifecycleOwner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED

        navController = NavHostController(RuntimeEnvironment.getApplication()).apply {
            navigatorProvider.addNavigator(ScreenNavigator())
            setLifecycleOwner(lifecycleOwner)
            setViewModelStore(ViewModelStore())
            graph = createGraph(startDestination = LOGIN) {
                (listOf(LOGIN) + EchoTab.entries.map { it.route }).forEach { route ->
                    destination(NavDestinationBuilder(navigatorProvider[ScreenNavigator::class], route))
                }
            }
        }
    }

    @Test
    fun `로그아웃 후 다른 계정으로 로그인하면 일기 탭이 이전 계정의 ViewModel을 복원하지 않는다`() {
        // 계정 A: 로그인 → 일기 탭 → 설정 탭 (일기 탭 상태는 saveState로 저장됨)
        login()
        navController.navigateToTab(EchoTab.HISTORY)
        val accountAViewModel = tabViewModel()
        navController.navigateToTab(EchoTab.SETTINGS)

        // 설정에서 로그아웃 → 같은 화면에서 계정 B 로그인 → 일기 탭
        navController.navigateToLoginAfterLogout()
        assertEquals("로그아웃하면 로그인 화면", LOGIN, navController.currentDestination?.route)
        login()
        navController.navigateToTab(EchoTab.HISTORY)

        assertNotSame(accountAViewModel, tabViewModel())
        assertTrue("이전 계정의 ViewModel은 정리되어야 함", accountAViewModel.cleared)
    }

    @Test
    fun `로그아웃하지 않고 탭을 오가면 일기 탭 상태는 그대로 복원된다`() {
        login()
        navController.navigateToTab(EchoTab.HISTORY)
        val viewModel = tabViewModel()

        navController.navigateToTab(EchoTab.SETTINGS)
        navController.navigateToTab(EchoTab.HISTORY)

        assertSame(viewModel, tabViewModel())
    }

    /** 로그인 성공 → 홈 (MainActivity는 CHECKING을 거쳐 같은 방식으로 이동) */
    private fun login() {
        navController.navigate(EchoTab.HOME.route) {
            popUpTo(LOGIN) { inclusive = true }
        }
    }

    private fun tabViewModel(): TabViewModel =
        ViewModelProvider(navController.currentBackStackEntry!!)[TabViewModel::class.java]

    private companion object {
        const val LOGIN = "login"
    }
}
