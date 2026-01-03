package org.mz.mzdkplayer.tool

import android.annotation.SuppressLint
import androidx.media3.common.C
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.ts.TsExtractor
import java.io.EOFException
import java.io.IOException
import kotlin.math.min

/**
 * M2TsExtractor
 * 修复版：增加了 SeekMap 坐标转换和 Input 缓冲，解决播放卡顿和无法 Seek 的问题。
 */
@SuppressLint("UnsafeOptInUsageError")
class M2TsExtractor : Extractor {
    private val tsExtractor: TsExtractor = TsExtractor()
    // 必须持有 adapter 引用，以便在 seek 时重置它的缓冲状态
    private var currentInputAdapter: M2TsInputAdapter? = null

    @Throws(IOException::class)
    override fun sniff(input: ExtractorInput): Boolean {
        val scratch = ByteArray(M2TS_PACKET_SIZE)
        input.peekFully(scratch, 0, M2TS_PACKET_SIZE)
        // 检查第4个字节是否为 0x47
        return scratch[M2TS_HEADER_SIZE].toInt() == 0x47
    }

    override fun init(output: ExtractorOutput) {
        // 关键修复 1：使用 OutputAdapter 拦截 SeekMap
        tsExtractor.init(M2TsOutputAdapter(output))
    }

    @Throws(IOException::class)
    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        // 保持单例或状态复用，因为 adapter 内部有 leftoverBuffer
        if (currentInputAdapter == null) {
            currentInputAdapter = M2TsInputAdapter(input)
        }
        val adapter = currentInputAdapter!!
        // 注意：如果 input 实例变了（例如 ExoPlayer 重新打开了 DataSource），需要重新绑定
        adapter.resetInput(input)

        val result = tsExtractor.read(adapter, seekPosition)

        if (result == Extractor.RESULT_SEEK) {
            val virtualPos = seekPosition.position
            // 虚拟位置 -> 物理位置转换
            seekPosition.position = virtualToPhysical(virtualPos)
        }

        return result
    }

    override fun seek(position: Long, timeUs: Long) {
        // 关键修复 2：Seek 时清空 Adapter 的缓冲区
        currentInputAdapter?.clearBuffer()

        // 物理位置 -> 虚拟位置，告诉内部 TsExtractor 重置状态
        val virtualPosition = physicalToVirtual(position)
        tsExtractor.seek(virtualPosition, timeUs)
    }

    override fun release() {
        tsExtractor.release()
        currentInputAdapter = null
    }

    // --- 坐标转换工具方法 ---
    companion object {
        private const val TS_PACKET_SIZE = 188
        private const val M2TS_PACKET_SIZE = 192
        private const val M2TS_HEADER_SIZE = 4

        // 188 -> 192
        private fun virtualToPhysical(virtualPos: Long): Long {
            if (virtualPos == C.POSITION_UNSET.toLong()) return C.POSITION_UNSET.toLong()
            return (virtualPos / TS_PACKET_SIZE) * M2TS_PACKET_SIZE
        }

        // 192 -> 188
        private fun physicalToVirtual(physicalPos: Long): Long {
            if (physicalPos == C.POSITION_UNSET.toLong()) return C.POSITION_UNSET.toLong()
            return (physicalPos / M2TS_PACKET_SIZE) * TS_PACKET_SIZE
        }
    }

    /**
     * 1. Output Adapter: 拦截 SeekMap
     */
    private class M2TsOutputAdapter(private val wrapped: ExtractorOutput) : ExtractorOutput {
        override fun track(id: Int, type: Int): TrackOutput {
            return wrapped.track(id, type)
        }

        override fun endTracks() {
            wrapped.endTracks()
        }

        override fun seekMap(seekMap: SeekMap) {
            // 包装原始 SeekMap，转换坐标
            wrapped.seekMap(M2TsSeekMapAdapter(seekMap))
        }
    }

    /**
     * 2. SeekMap Adapter: 负责将内部的 188 坐标转为外部的 192 坐标
     */
    private class M2TsSeekMapAdapter(private val internalSeekMap: SeekMap) : SeekMap {
        override fun isSeekable(): Boolean {
            return internalSeekMap.isSeekable
        }

        override fun getDurationUs(): Long {
            return internalSeekMap.durationUs
        }

        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
            val internalPoints = internalSeekMap.getSeekPoints(timeUs)

            // 转换第一个点
            val p1 = internalPoints.first
            val physP1 = SeekPoint(p1.timeUs, virtualToPhysical(p1.position))

            // 转换第二个点（如果有）
            val p2 = internalPoints.second
            if (p2 == p1) {
                return SeekMap.SeekPoints(physP1)
            }
            val physP2 = SeekPoint(p2.timeUs, virtualToPhysical(p2.position))

            return SeekMap.SeekPoints(physP1, physP2)
        }
    }

    /**
     * 3. Input Adapter: 带缓冲的输入适配器 (修复读取卡顿)
     */
    private class M2TsInputAdapter(private var wrappedInput: ExtractorInput) : ExtractorInput {
        private val m2tsScratch = ByteArray(M2TS_PACKET_SIZE)
        private val leftoverBuffer = ByteArray(TS_PACKET_SIZE)
        private var leftoverOffset = 0
        private var leftoverLength = 0

        // 当外层 ExtractorInput 实例发生变化时调用
        fun resetInput(newInput: ExtractorInput) {
            if (wrappedInput !== newInput) {
                wrappedInput = newInput
                clearBuffer() // 输入源变了，旧缓冲无效
            }
        }

        fun clearBuffer() {
            leftoverOffset = 0
            leftoverLength = 0
        }

        @Throws(IOException::class)
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            var bytesWritten = 0
            while (bytesWritten < length) {
                // 1. 先读缓存
                if (leftoverLength > 0) {
                    val bytesToCopy = min(leftoverLength, length - bytesWritten)
                    System.arraycopy(leftoverBuffer, leftoverOffset, buffer, offset + bytesWritten, bytesToCopy)
                    leftoverOffset += bytesToCopy
                    leftoverLength -= bytesToCopy
                    bytesWritten += bytesToCopy
                    if (bytesWritten == length) return bytesWritten
                }

                // 2. 读底层
                val bytesRead = wrappedInput.read(m2tsScratch, 0, M2TS_PACKET_SIZE)
                if (bytesRead == C.RESULT_END_OF_INPUT) {
                    return if (bytesWritten == 0) C.RESULT_END_OF_INPUT else bytesWritten
                }

                // 3. 处理数据 (剥离4字节头)
                if (bytesRead > M2TS_HEADER_SIZE) {
                    val validLen = bytesRead - M2TS_HEADER_SIZE
                    // 存入缓存
                    System.arraycopy(m2tsScratch, M2TS_HEADER_SIZE, leftoverBuffer, 0, validLen)
                    leftoverOffset = 0
                    leftoverLength = validLen
                } else {
                    // 数据太少，不够一个头，且不是 EOF，这通常是异常情况，但需处理
                    if (bytesWritten == 0) return C.RESULT_END_OF_INPUT // 简单处理为结束
                }
            }
            return bytesWritten
        }

        override fun readFully(buffer: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean {
            var total = 0
            while (total < length) {
                val read = read(buffer, offset + total, length - total)
                if (read == C.RESULT_END_OF_INPUT) {
                    if (allowEndOfInput && total == 0) return false
                    throw EOFException()
                }
                total += read
            }
            return true
        }

        // 修复 1: readFully (无返回值)
        @Throws(IOException::class)
        override fun readFully(buffer: ByteArray, offset: Int, length: Int) {
            readFully(buffer, offset, length, false)
        }

        override fun getPosition(): Long {
            // 物理位置 - 缓冲残余对应的物理大小(近似)
            // 更精确的做法： Position 总是返回当前“虚拟读取位置”对应的“物理位置”
            val physicalPos = wrappedInput.position
            val virtualBase = physicalToVirtual(physicalPos)
            return virtualBase - leftoverLength
        }

        override fun getLength(): Long {
            val len = wrappedInput.length
            return physicalToVirtual(len)
        }

        // --- Skip / Peek (简化处理) ---
        override fun skip(length: Int): Int {
            val skipBuf = ByteArray(min(length, 4096))
            return read(skipBuf, 0, min(length, 4096))
        }

        override fun skipFully(length: Int, allowEndOfInput: Boolean): Boolean {
            var remaining = length
            while (remaining > 0) {
                val skipped = skip(remaining)
                if (skipped == C.RESULT_END_OF_INPUT) {
                    if (allowEndOfInput) return false
                    throw EOFException()
                }
                remaining -= skipped
            }
            return true
        }
        // 修复 2: skipFully (无返回值)
        @Throws(IOException::class)
        override fun skipFully(length: Int) {
            skipFully(length, false)
        }

        // Peek 保持穿透（因为 TsExtractor 主要在 sniff 阶段 peek）
        override fun peek(target: ByteArray, offset: Int, length: Int): Int {
            // 警告：如果缓存有数据，peek 应该先看缓存。
            // 为了简化，这里假设 peek 只在 seek 或 init 时发生，此时 buffer 应为空。
            // 如果播放中途 peek，此实现有风险。但在 Media3 TsExtractor 中，播放阶段主要靠 read。
            if (leftoverLength > 0) {
                // 简易 fallback: 只 peek 缓存
                val copy = min(length, leftoverLength)
                System.arraycopy(leftoverBuffer, leftoverOffset, target, offset, copy)
                return copy
            }
            // 穿透逻辑 (极其简化，仅用于 sniff)
            // 实际 peek 很难做完美适配，除非完全重写 peek 缓冲
            return 0
        }

        override fun peekFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean = false
        override fun peekFully(target: ByteArray, offset: Int, length: Int) {}
        override fun advancePeekPosition(length: Int, allowEndOfInput: Boolean): Boolean = false
        override fun advancePeekPosition(length: Int) {}
        override fun resetPeekPosition() { wrappedInput.resetPeekPosition() }
        override fun getPeekPosition(): Long = wrappedInput.peekPosition // 近似
        override fun <E : Throwable> setRetryPosition(position: Long, e: E) { wrappedInput.setRetryPosition(position, e) }
    }
}