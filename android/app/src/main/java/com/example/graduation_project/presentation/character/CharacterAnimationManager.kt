package com.example.graduation_project.presentation.character

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.RawRes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.example.graduation_project.R
import com.example.graduation_project.presentation.model.ConversationError
import com.example.graduation_project.presentation.model.ConversationState

/**
 * ExoPlayer 캐릭터 애니메이션 관리자
 *
 * Compose에서는 remember { CharacterAnimationManager(context) } 로 생성 후
 * DisposableEffect로 release() 호출
 *
 * ── 상태별 mp4 매핑 ───────────────────────────────────────────
 * Idle                 → char_greeting    (5초 간격 반복)
 * Listening            → listening_state  (루프)
 * Recording            → listening_state  (루프)
 * Sending              → listening_state  (루프)
 * Playing              → speaking_state   (루프)
 * Ended                → last_greeting    (1회)
 * 오류 상태 전체        → listening_state  (루프)
 */
class CharacterAnimationManager(private val context: Context) {

    // 부드러운 루프 재생을 위한 버퍼 설정
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            5000,   // 최소 버퍼 (5초)
            30000,  // 최대 버퍼 (30초)
            1000,   // 재생 시작 버퍼 (1초)
            2000    // 리버퍼링 버퍼 (2초)
        )
        .build()

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        .build()
        .apply {
            volume = 0f // 캐릭터 영상 자체 소리 없음
            repeatMode = Player.REPEAT_MODE_ONE
            setPlaybackSpeed(0.7f) // 속도 느리게 (0.7배속)
        }

    var onFarewellFinished: (() -> Unit)? = null

    private var currentStateKey: String? = null
    private var isIdleState = false
    private val handler = Handler(Looper.getMainLooper())
    private val replayRunnable = Runnable {
        if (isIdleState) {
            player.seekTo(0)
            player.play()
        }
    }

    companion object {
        private const val IDLE_REPLAY_DELAY_MS = 5000L // 5초 간격
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (isIdleState) {
                        // Idle 상태: 5초 후 다시 재생
                        handler.postDelayed(replayRunnable, IDLE_REPLAY_DELAY_MS)
                    } else {
                        // Ended 상태: 콜백 호출
                        onFarewellFinished?.invoke()
                    }
                }
            }
        })
    }

    /**
     * ConversationState와 ConversationError 기반으로 캐릭터 애니메이션 변경
     * @param state 현재 대화 상태
     * @param error 현재 오류 (null이면 오류 없음)
     */
    fun changeState(state: ConversationState, error: ConversationError? = null) {
        val stateKey = buildStateKey(state, error)
        // 동일한 상태면 무시 (불필요한 재생 방지)
        if (currentStateKey == stateKey) return
        currentStateKey = stateKey

        // 이전 상태의 예약된 재생 취소
        handler.removeCallbacks(replayRunnable)

        val (resId, repeatMode) = resolveAnimation(state, error)
        isIdleState = (state is ConversationState.Idle && error == null)

        player.repeatMode = repeatMode
        player.setMediaItem(MediaItem.fromUri(getRawUri(resId)))
        player.prepare()
        player.play()
    }

    private fun buildStateKey(state: ConversationState, error: ConversationError?): String {
        return "${state::class.simpleName}_${error?.let { it::class.simpleName } ?: "NoError"}"
    }

    private fun resolveAnimation(
        state: ConversationState,
        error: ConversationError?
    ): Pair<Int, Int> {
        // 오류가 있으면 listening_state 루프
        if (error != null) {
            return R.raw.listening_state to Player.REPEAT_MODE_ONE
        }

        return when (state) {
            // Idle: 5초 간격으로 재생 (REPEAT_MODE_OFF + Handler로 재생)
            is ConversationState.Idle      -> R.raw.char_greeting   to Player.REPEAT_MODE_OFF
            is ConversationState.Listening -> R.raw.listening_state to Player.REPEAT_MODE_ONE
            is ConversationState.Recording -> R.raw.listening_state to Player.REPEAT_MODE_ONE
            is ConversationState.Sending   -> R.raw.listening_state to Player.REPEAT_MODE_ONE
            is ConversationState.Playing   -> R.raw.speaking_state  to Player.REPEAT_MODE_ONE
            is ConversationState.Ended     -> R.raw.last_greeting   to Player.REPEAT_MODE_OFF // 1회
        }
    }

    private fun getRawUri(@RawRes resId: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$resId")

    fun release() {
        handler.removeCallbacks(replayRunnable)
        player.release()
    }
}
