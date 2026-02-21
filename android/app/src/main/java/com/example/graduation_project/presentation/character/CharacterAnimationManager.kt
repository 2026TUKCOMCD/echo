package com.example.graduation_project.presentation.character

import android.content.Context
import android.net.Uri
import androidx.annotation.RawRes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
 * Idle                 → char_greeting    (루프)
 * Listening            → listening_state  (루프)
 * Recording            → listening_state  (루프)
 * Sending              → listening_state  (루프)
 * Playing              → speaking_state   (루프)
 * Ended                → last_greeting    (1회)
 * 오류 상태 전체        → listening_state  (루프)
 */
class CharacterAnimationManager(private val context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        volume = 0f // 캐릭터 영상 자체 소리 없음
        repeatMode = Player.REPEAT_MODE_ONE
        setPlaybackSpeed(0.8f) // 속도 느리게 (0.8배속)
    }

    var onFarewellFinished: (() -> Unit)? = null

    private var currentStateKey: String? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onFarewellFinished?.invoke()
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

        val (resId, shouldLoop) = resolveAnimation(state, error)
        player.repeatMode = if (shouldLoop) Player.REPEAT_MODE_ONE
                            else Player.REPEAT_MODE_OFF
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
    ): Pair<Int, Boolean> {
        // 오류가 있으면 listening_state 루프
        if (error != null) {
            return R.raw.listening_state to true
        }

        return when (state) {
            is ConversationState.Idle      -> R.raw.char_greeting   to true
            is ConversationState.Listening -> R.raw.listening_state to true
            is ConversationState.Recording -> R.raw.listening_state to true
            is ConversationState.Sending   -> R.raw.listening_state to true
            is ConversationState.Playing   -> R.raw.speaking_state  to true
            is ConversationState.Ended     -> R.raw.last_greeting   to false // 1회
        }
    }

    private fun getRawUri(@RawRes resId: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$resId")

    fun release() {
        player.release()
    }
}
