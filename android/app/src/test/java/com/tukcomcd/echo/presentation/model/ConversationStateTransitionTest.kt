package com.tukcomcd.echo.presentation.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ConversationState.canTransitionTo() 단위 테스트
 *
 * 허용 전이 규칙:
 * Idle      → Sending
 * Listening → Recording | Sending
 * Recording → Sending
 * Sending   → Playing | Idle | Ended | Listening
 * Playing   → Listening
 * Ended     → Idle
 */
class ConversationStateTransitionTest {

    // ===== Idle =====

    @Test
    fun idle_canTransitionTo_Sending() {
        assertTrue(ConversationState.Idle.canTransitionTo(ConversationState.Sending))
    }

    @Test
    fun idle_cannotTransitionTo_Listening() {
        assertFalse(ConversationState.Idle.canTransitionTo(ConversationState.Listening))
    }

    @Test
    fun idle_cannotTransitionTo_Recording() {
        assertFalse(ConversationState.Idle.canTransitionTo(ConversationState.Recording))
    }

    @Test
    fun idle_cannotTransitionTo_Playing() {
        assertFalse(ConversationState.Idle.canTransitionTo(ConversationState.Playing))
    }

    @Test
    fun idle_cannotTransitionTo_Ended() {
        assertFalse(ConversationState.Idle.canTransitionTo(ConversationState.Ended))
    }

    @Test
    fun idle_cannotTransitionTo_Idle() {
        assertFalse(ConversationState.Idle.canTransitionTo(ConversationState.Idle))
    }

    // ===== Listening =====

    @Test
    fun listening_canTransitionTo_Recording() {
        assertTrue(ConversationState.Listening.canTransitionTo(ConversationState.Recording))
    }

    @Test
    fun listening_canTransitionTo_Sending() {
        assertTrue(ConversationState.Listening.canTransitionTo(ConversationState.Sending))
    }

    @Test
    fun listening_cannotTransitionTo_Idle() {
        assertFalse(ConversationState.Listening.canTransitionTo(ConversationState.Idle))
    }

    @Test
    fun listening_cannotTransitionTo_Playing() {
        assertFalse(ConversationState.Listening.canTransitionTo(ConversationState.Playing))
    }

    @Test
    fun listening_cannotTransitionTo_Ended() {
        assertFalse(ConversationState.Listening.canTransitionTo(ConversationState.Ended))
    }

    @Test
    fun listening_cannotTransitionTo_Listening() {
        assertFalse(ConversationState.Listening.canTransitionTo(ConversationState.Listening))
    }

    // ===== Recording =====

    @Test
    fun recording_canTransitionTo_Sending() {
        assertTrue(ConversationState.Recording.canTransitionTo(ConversationState.Sending))
    }

    @Test
    fun recording_cannotTransitionTo_Listening() {
        assertFalse(ConversationState.Recording.canTransitionTo(ConversationState.Listening))
    }

    @Test
    fun recording_cannotTransitionTo_Playing() {
        assertFalse(ConversationState.Recording.canTransitionTo(ConversationState.Playing))
    }

    @Test
    fun recording_cannotTransitionTo_Idle() {
        assertFalse(ConversationState.Recording.canTransitionTo(ConversationState.Idle))
    }

    @Test
    fun recording_cannotTransitionTo_Ended() {
        assertFalse(ConversationState.Recording.canTransitionTo(ConversationState.Ended))
    }

    @Test
    fun recording_cannotTransitionTo_Recording() {
        assertFalse(ConversationState.Recording.canTransitionTo(ConversationState.Recording))
    }

    // ===== Sending =====

    @Test
    fun sending_canTransitionTo_Playing() {
        assertTrue(ConversationState.Sending.canTransitionTo(ConversationState.Playing))
    }

    @Test
    fun sending_canTransitionTo_Idle() {
        assertTrue(ConversationState.Sending.canTransitionTo(ConversationState.Idle))
    }

    @Test
    fun sending_canTransitionTo_Ended() {
        assertTrue(ConversationState.Sending.canTransitionTo(ConversationState.Ended))
    }

    @Test
    fun sending_canTransitionTo_Listening() {
        assertTrue(ConversationState.Sending.canTransitionTo(ConversationState.Listening))
    }

    @Test
    fun sending_cannotTransitionTo_Recording() {
        assertFalse(ConversationState.Sending.canTransitionTo(ConversationState.Recording))
    }

    @Test
    fun sending_cannotTransitionTo_Sending() {
        assertFalse(ConversationState.Sending.canTransitionTo(ConversationState.Sending))
    }

    // ===== Playing =====

    @Test
    fun playing_canTransitionTo_Listening() {
        assertTrue(ConversationState.Playing.canTransitionTo(ConversationState.Listening))
    }

    @Test
    fun playing_cannotTransitionTo_Sending() {
        assertFalse(ConversationState.Playing.canTransitionTo(ConversationState.Sending))
    }

    @Test
    fun playing_cannotTransitionTo_Idle() {
        assertFalse(ConversationState.Playing.canTransitionTo(ConversationState.Idle))
    }

    @Test
    fun playing_cannotTransitionTo_Ended() {
        assertFalse(ConversationState.Playing.canTransitionTo(ConversationState.Ended))
    }

    @Test
    fun playing_cannotTransitionTo_Recording() {
        assertFalse(ConversationState.Playing.canTransitionTo(ConversationState.Recording))
    }

    @Test
    fun playing_cannotTransitionTo_Playing() {
        assertFalse(ConversationState.Playing.canTransitionTo(ConversationState.Playing))
    }

    // ===== Ended =====

    @Test
    fun ended_canTransitionTo_Idle() {
        assertTrue(ConversationState.Ended.canTransitionTo(ConversationState.Idle))
    }

    @Test
    fun ended_cannotTransitionTo_Listening() {
        assertFalse(ConversationState.Ended.canTransitionTo(ConversationState.Listening))
    }

    @Test
    fun ended_cannotTransitionTo_Sending() {
        assertFalse(ConversationState.Ended.canTransitionTo(ConversationState.Sending))
    }

    @Test
    fun ended_cannotTransitionTo_Playing() {
        assertFalse(ConversationState.Ended.canTransitionTo(ConversationState.Playing))
    }

    @Test
    fun ended_cannotTransitionTo_Recording() {
        assertFalse(ConversationState.Ended.canTransitionTo(ConversationState.Recording))
    }

    @Test
    fun ended_cannotTransitionTo_Ended() {
        assertFalse(ConversationState.Ended.canTransitionTo(ConversationState.Ended))
    }
}
