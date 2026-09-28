package com.example.graduation_project.data.voice

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 네트워크에서 도착하는 대로 커지는 오디오 버퍼. 스트리밍 재생용 MediaDataSource의 저장소다.
 * (Android API에 의존하지 않도록 분리해 JVM 단위 테스트가 가능하게 함)
 *
 * - 쓰는 쪽(네트워크 수신 스레드): [append] → [complete] 또는 [fail]
 * - 읽는 쪽(MediaPlayer 내부 스레드): [readAt] - 아직 도착하지 않은 위치면 데이터가 올 때까지 블로킹
 * - [close]: 재생 중지 시 블로킹 중인 읽기를 즉시 풀어준다. MediaPlayer.release()가 블로킹된
 *   readAt을 기다리며 멈추는 것(교착)을 막으려면 release 전에 반드시 호출해야 한다.
 *
 * 실패 시 readAt은 예외 대신 -1(끝)을 돌려준다. MediaPlayer가 읽기 예외를 "오류"로 볼지 "끝"으로 볼지는
 * 기기마다 달라서, 항상 "끝"으로 만들고 재생 완료 시점에 [isFailed]로 잘림을 판정한다.
 */
class GrowingAudioBuffer(initialCapacity: Int = 64 * 1024) {

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()

    private var data = ByteArray(initialCapacity)
    private var size = 0
    private var finished = false
    private var closed = false

    /** 스트림이 끝까지 오지 못한 원인 (null이면 실패 아님) */
    var failure: Throwable? = null
        get() = lock.withLock { field }
        private set

    val isFailed: Boolean get() = failure != null

    fun append(bytes: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        lock.withLock {
            if (closed || finished) return
            if (size + length > data.size) {
                data = data.copyOf(maxOf(data.size * 2, size + length))
            }
            System.arraycopy(bytes, offset, data, size, length)
            size += length
            changed.signalAll()
        }
    }

    /** 모든 데이터를 정상적으로 받음 */
    fun complete() {
        lock.withLock {
            finished = true
            changed.signalAll()
        }
    }

    /** 스트림이 중간에 끊김 - 이미 받은 데이터까지만 읽히고 이후는 끝(-1)으로 처리된다 */
    fun fail(cause: Throwable) {
        lock.withLock {
            if (!finished && failure == null) failure = cause
            finished = true
            changed.signalAll()
        }
    }

    /** 재생 중지 - 블로킹 중인 읽기를 풀고 이후 모든 읽기를 끝(-1)으로 만든다 */
    fun close() {
        lock.withLock {
            closed = true
            changed.signalAll()
        }
    }

    /**
     * [position]부터 최대 [length]바이트를 [buffer]에 복사한다.
     * @return 복사한 바이트 수, 더 읽을 데이터가 없으면 -1
     */
    fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        lock.withLock {
            while (true) {
                if (closed) return -1
                if (position < size) {
                    val count = minOf(length.toLong(), size - position).toInt()
                    System.arraycopy(data, position.toInt(), buffer, offset, count)
                    return count
                }
                if (finished) return -1
                changed.await()
            }
        }
    }
}
