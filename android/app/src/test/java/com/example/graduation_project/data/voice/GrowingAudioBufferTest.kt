package com.example.graduation_project.data.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * 스트리밍 재생 버퍼 검증.
 * readAt은 MediaPlayer 내부 스레드에서 호출되므로 "데이터가 올 때까지 대기"와 "중지 시 즉시 해제"가 핵심.
 */
class GrowingAudioBufferTest {

    private fun GrowingAudioBuffer.readAllFrom(position: Long, length: Int): Pair<Int, ByteArray> {
        val out = ByteArray(length)
        val n = readAt(position, out, 0, length)
        return n to out
    }

    @Test
    fun `도착한 데이터는 위치 기준으로 읽힌다`() {
        val buffer = GrowingAudioBuffer(initialCapacity = 2)  // 용량 확장 경로도 함께 검증
        buffer.append(byteArrayOf(1, 2, 3, 4, 5), 0, 5)

        val (n, out) = buffer.readAllFrom(position = 1, length = 3)

        assertEquals(3, n)
        assertArrayEquals(byteArrayOf(2, 3, 4), out)
    }

    @Test
    fun `요청보다 적게 도착했으면 도착한 만큼만 반환한다`() {
        val buffer = GrowingAudioBuffer()
        buffer.append(byteArrayOf(1, 2), 0, 2)

        assertEquals(2, buffer.readAllFrom(position = 0, length = 10).first)
    }

    @Test
    fun `아직 도착하지 않은 위치를 읽으면 데이터가 올 때까지 기다린다`() {
        val buffer = GrowingAudioBuffer()
        val result = AtomicInteger(Int.MIN_VALUE)
        val done = CountDownLatch(1)

        thread {
            result.set(buffer.readAllFrom(position = 0, length = 4).first)
            done.countDown()
        }

        assertFalse("데이터가 없으면 반환하지 않고 대기해야 함", done.await(200, TimeUnit.MILLISECONDS))
        buffer.append(byteArrayOf(9, 9), 0, 2)
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals(2, result.get())
    }

    @Test
    fun `정상 완료 후 끝 위치를 읽으면 -1이고 실패로 보지 않는다`() {
        val buffer = GrowingAudioBuffer()
        buffer.append(byteArrayOf(1), 0, 1)
        buffer.complete()

        assertEquals(-1, buffer.readAllFrom(position = 1, length = 4).first)
        assertFalse(buffer.isFailed)
    }

    @Test
    fun `스트림이 끊기면 받은 데이터까지만 읽히고 이후는 -1이며 실패로 기록된다`() {
        val buffer = GrowingAudioBuffer()
        buffer.append(byteArrayOf(1, 2), 0, 2)
        val cause = IOException("잘림")
        buffer.fail(cause)

        assertEquals(2, buffer.readAllFrom(position = 0, length = 4).first)
        assertEquals(-1, buffer.readAllFrom(position = 2, length = 4).first)
        assertTrue(buffer.isFailed)
        assertEquals(cause, buffer.failure)
    }

    @Test
    fun `정상 완료 뒤에 들어온 실패 신호는 무시한다`() {
        val buffer = GrowingAudioBuffer()
        buffer.complete()
        buffer.fail(IOException("늦게 온 닫힘"))

        assertFalse(buffer.isFailed)
    }

    @Test
    fun `close하면 대기 중인 읽기가 즉시 -1로 풀린다 (MediaPlayer release 교착 방지)`() {
        val buffer = GrowingAudioBuffer()
        val result = AtomicInteger(Int.MIN_VALUE)
        val done = CountDownLatch(1)
        thread {
            result.set(buffer.readAllFrom(position = 0, length = 4).first)
            done.countDown()
        }
        assertFalse(done.await(200, TimeUnit.MILLISECONDS))

        buffer.close()

        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals(-1, result.get())
    }

    @Test
    fun `길이 0 요청은 대기 없이 0을 반환한다`() {
        assertEquals(0, GrowingAudioBuffer().readAt(0, ByteArray(1), 0, 0))
    }
}
