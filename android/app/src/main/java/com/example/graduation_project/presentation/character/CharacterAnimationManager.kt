package com.example.graduation_project.presentation.character

import android.content.Context
import android.net.Uri
import androidx.annotation.RawRes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.graduation_project.R

/**
 * ExoPlayer 캐릭터 애니메이션 관리자
 *
 * Compose에서는 remember { CharacterAnimationManager(context) } 로 생성 후
 * DisposableEffect로 release() 호출
 *
 * ── 상태별 mp4 매핑 ───────────────────────────────────────────
 * IDLE                → char_greeting    (루프)
 * LISTENING           → listening_state  (루프)
 * PROCESSING          → listening_state  (루프)
 * SPEAKING            → speaking_state   (루프)
 * FAREWELL            → last_greeting    (1회)
 * 오류 상태 전체       → listening_state  (루프)
 */
class CharacterAnimationManager(private val context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        volume = 0f // 캐릭터 영상 자체 소리 없음
        repeatMode = Player.REPEAT_MODE_ONE
    }

    var onFarewellFinished: (() -> Unit)? = null

    private var currentState: CharacterState? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onFarewellFinished?.invoke()
                }
            }
        })
    }

    fun changeState(state: CharacterState) {
        // 동일한 상태면 무시 (불필요한 재생 방지)
        if (currentState == state) return
        currentState = state

        val (resId, shouldLoop) = resolveAnimation(state)
        player.repeatMode = if (shouldLoop) Player.REPEAT_MODE_ONE
                            else Player.REPEAT_MODE_OFF
        player.setMediaItem(MediaItem.fromUri(getRawUri(resId)))
        player.prepare()
        player.play()
    }

    private fun resolveAnimation(state: CharacterState): Pair<Int, Boolean> = when (state) {
        CharacterState.IDLE                -> R.raw.char_greeting   to true
        CharacterState.LISTENING           -> R.raw.listening_state  to true
        CharacterState.PROCESSING          -> R.raw.listening_state  to true
        CharacterState.SPEAKING            -> R.raw.speaking_state   to true
        CharacterState.FAREWELL            -> R.raw.last_greeting    to false // 1회
        CharacterState.SPEECH_UNRECOGNIZED -> R.raw.listening_state  to true
        CharacterState.NETWORK_ERROR       -> R.raw.listening_state  to true
        CharacterState.SERVER_ERROR        -> R.raw.listening_state  to true
        CharacterState.TTS_ERROR           -> R.raw.listening_state  to true
    }

    private fun getRawUri(@RawRes resId: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$resId")

    fun release() {
        player.release()
    }
}
