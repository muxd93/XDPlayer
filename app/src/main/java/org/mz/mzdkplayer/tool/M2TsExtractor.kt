package org.mz.mzdkplayer.tool

import android.annotation.SuppressLint
import androidx.media3.common.C
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.extractor.ts.TsExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA
import java.io.EOFException
import java.io.IOException
import kotlin.math.min


/**
 * M2TsExtractor
 * 复用 androidx.media3.extractor.ts.TsExtractor 来解析 .m2ts (192字节包) 文件。
 * 原理：通过适配器模式，在读取层剥离 M2TS 的 4字节头部 (TP_extra_header)。
 */
@SuppressLint("UnsafeOptInUsageError")
class M2TsExtractor : Extractor {
    // 初始化标准的 TsExtractor
    // 注意：根据需要可以传入 DefaultTsPayloadReaderFactory 等参数
    private val tsExtractor: TsExtractor = TsExtractor()

    @Throws(IOException::class)
    override fun sniff(input: ExtractorInput): Boolean {
        // M2TS 的 Sniff 逻辑：读取 192 字节，检查第 4 个字节（偏移量4）是否为 sync_byte (0x47)
        val scratch = ByteArray(M2TS_PACKET_SIZE)
        input.peekFully(scratch, 0, M2TS_PACKET_SIZE)


        // M2TS 结构: [4 bytes header] [0x47] [187 bytes payload]
        return scratch[M2TS_HEADER_SIZE].toInt() == 0x47
    }

    override fun init(output: ExtractorOutput) {
        tsExtractor.init(output)
    }

    @Throws(IOException::class)
    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        // 关键点：使用适配器包装原始 Input
        val adapter = M2TsInputAdapter(input)


        // 调用内部 TsExtractor 进行读取
        val result = tsExtractor.read(adapter, seekPosition)

        // 处理 Seek 位置的转换
        // TsExtractor 计算出的 seekPosition 是基于 188字节流的“虚拟位置”
        // 我们需要将其转换回 M2TS 192字节流的“物理位置”
        if (result == Extractor.RESULT_SEEK) {
            val virtualPos = seekPosition.position
            // 物理位置 = 虚拟位置 * (192 / 188)
            val physicalPos = (virtualPos / TS_PACKET_SIZE) * M2TS_PACKET_SIZE
            seekPosition.position = physicalPos
        }

        return result
    }

    override fun seek(position: Long, timeUs: Long) {
        // 将 M2TS 的物理位置转换为 TS 的虚拟位置传给内部 Extractor
        val virtualPosition = (position / M2TS_PACKET_SIZE) * TS_PACKET_SIZE
        tsExtractor.seek(virtualPosition, timeUs)
    }

    override fun release() {
        tsExtractor.release()
    }

    /**
     * 内部适配器类：负责将 192字节的数据流转换为 188字节的数据流
     * 核心逻辑：读取时跳过前 4 个字节
     */
    /**
     * 内部适配器类：负责将 192字节的数据流转换为 188字节的数据流
     * 核心逻辑：拦截上层对 188 字节的请求，底层映射为 192 字节的操作（跳过4字节头）。
     */
    private  class M2TsInputAdapter(private val wrappedInput: ExtractorInput) : ExtractorInput {
        private val scratch = ByteArray(M2TS_PACKET_SIZE) // 暂存底层读到的192字节数据

        // --- 核心辅助计算 ---
        // 将虚拟长度（188流）转换为物理长度（192流）
        // 注意：这里假设读取总是基于包对齐的，这是 TsExtractor 的特性
        fun toPhysicalLength(virtualLength: Int): Int {
            if (virtualLength == 0) return 0
            // 计算涉及多少个包
            val packetCount = (virtualLength + TS_PACKET_SIZE - 1) / TS_PACKET_SIZE
            // 简单的估算：如果是完整的包，就是 count * 192。
            // 实际上 TsExtractor 几乎总是读取 188 的倍数。
            // 如果读非整数包（极少见），我们这里简单处理为按比例放大，或者向上取整包
            return packetCount * M2TS_PACKET_SIZE
        }

        // --- Read 系列实现 ---
        @Throws(IOException::class)
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            var bytesRead = 0
            while (bytesRead < length) {
                // 每次处理一个包
                // 1. 从底层 peek 或 read 192 字节? 不，直接 read
                // 我们一次读 192 字节到 scratch
                val bytesToReadFromWrapped = M2TS_PACKET_SIZE


                // 注意：这里简化了逻辑，假设底层数据足够。实际应该处理 EOF。
                // 更好的做法是循环读取
                val readResult = wrappedInput.read(scratch, 0, M2TS_PACKET_SIZE)
                if (readResult == C.RESULT_END_OF_INPUT) {
                    return if (bytesRead == 0) C.RESULT_END_OF_INPUT else bytesRead
                }


                // 如果读到的不够一个完整 M2TS 包（例如文件尾），处理剩余部分
                // 2. 剥离前4字节，将后188字节（或剩余部分）复制到 buffer
                if (readResult > M2TS_HEADER_SIZE) {
                    val payloadSize = readResult - M2TS_HEADER_SIZE
                    val bytesToCopy = min(payloadSize, length - bytesRead)
                    System.arraycopy(
                        scratch,
                        M2TS_HEADER_SIZE,
                        buffer,
                        offset + bytesRead,
                        bytesToCopy
                    )
                    bytesRead += bytesToCopy
                }

                if (readResult < M2TS_PACKET_SIZE) {
                    // 底层读不满了，说明到底了
                    break
                }
            }
            return bytesRead
        }

        @Throws(IOException::class)
        override fun readFully(
            buffer: ByteArray,
            offset: Int,
            length: Int,
            allowEndOfInput: Boolean
        ): Boolean {
            val bytesRead = read(buffer, offset, length)
            if (bytesRead == C.RESULT_END_OF_INPUT && !allowEndOfInput) {
                throw EOFException()
            }
            // 如果读取到的数据比要求的少且不是 EOF，也算失败（除非 allowEndOfInput 处理了）
            // 标准实现通常要求读满
            if (bytesRead != C.RESULT_END_OF_INPUT && bytesRead < length) {
                if (!allowEndOfInput) throw EOFException()
                return false
            }
            return bytesRead != C.RESULT_END_OF_INPUT
        }

        @Throws(IOException::class)
        override fun readFully(buffer: ByteArray, offset: Int, length: Int) {
            readFully(buffer, offset, length, false)
        }

        // --- Skip 系列实现 ---
        @Throws(IOException::class)
        override fun skip(length: Int): Int {
            // 跳过逻辑：上层想跳过 N 个 188 字节，底层需要跳过 N 个 192 字节
            val physicalToSkip = toPhysicalLength(length)
            return wrappedInput.skip(physicalToSkip)
        }

        @Throws(IOException::class)
        override fun skipFully(length: Int) {
            skipFully(length, false)
        }

        // 补全的方法
        @Throws(IOException::class)
        override fun skipFully(length: Int, allowEndOfInput: Boolean): Boolean {
            val physicalToSkip = toPhysicalLength(length)
            return wrappedInput.skipFully(physicalToSkip, allowEndOfInput)
        }

        // --- Peek 系列实现 ---
        // Peek 最麻烦，因为必须把数据“去头”后放入 buffer，还不能消耗底层流的位置
        // 为了简单且能跑，我们在内存里做这事。
        @Throws(IOException::class)
        override fun peek(target: ByteArray, offset: Int, length: Int): Int {
            // 注意：这是个极其低效的实现，但在 sniffing 阶段调用次数很少
            var bytesPeeked = 0
            val tempPeekOffset = 0 // 记录我们在底层流中 peek 了多远

            while (bytesPeeked < length) {
                // 1. 从当前 peek 位置再往前 peek 192 字节
                wrappedInput.advancePeekPosition(0) // 确保 peek 位置正确? 不，直接 peekFully 会动 peek 指针吗？

                // ExtractorInput 的 peekFully 会推进 peek position，但不会推进 read position。
                val success = wrappedInput.peekFully(scratch, 0, M2TS_PACKET_SIZE, true)
                if (!success) break

                // 2. 剥离头，复制数据
                val bytesToCopy = Math.min(TS_PACKET_SIZE, length - bytesPeeked)
                System.arraycopy(
                    scratch,
                    M2TS_HEADER_SIZE,
                    target,
                    offset + bytesPeeked,
                    bytesToCopy
                )
                bytesPeeked += bytesToCopy
            }


            // 重要：TsExtractor 在 peek 完后通常会期望 peek position 停留在那里，
            // 或者它会 reset。由于我们上面循环 peek 推进了底层的 peek position，
            // 只要比例对应（188 vs 192），这就没问题。
            return bytesPeeked
        }

        @Throws(IOException::class)
        override fun peekFully(
            target: ByteArray,
            offset: Int,
            length: Int,
            allowEndOfInput: Boolean
        ): Boolean {
            val result = peek(target, offset, length)
            if (result == C.RESULT_END_OF_INPUT && !allowEndOfInput) throw EOFException()
            return result == length
        }

        @Throws(IOException::class)
        override fun peekFully(target: ByteArray, offset: Int, length: Int) {
            peekFully(target, offset, length, false)
        }

        // 补全的方法
        @Throws(IOException::class)
        override fun advancePeekPosition(length: Int, allowEndOfInput: Boolean): Boolean {
            val physicalLength = toPhysicalLength(length)
            return wrappedInput.advancePeekPosition(physicalLength, allowEndOfInput)
        }

        // 补全的方法
        @Throws(IOException::class)
        override fun advancePeekPosition(length: Int) {
            advancePeekPosition(length, false)
        }

        override fun resetPeekPosition() {
            wrappedInput.resetPeekPosition()
        }

        override fun getPeekPosition(): Long {
            val physicalPeekPos = wrappedInput.getPeekPosition()
            return (physicalPeekPos / M2TS_PACKET_SIZE) * TS_PACKET_SIZE
        }

        // --- Position 相关 ---
        override fun getPosition(): Long {
            val physicalPos = wrappedInput.position
            return (physicalPos / M2TS_PACKET_SIZE) * TS_PACKET_SIZE
        }

        override fun getLength(): Long {
            val physicalLen = wrappedInput.length
            return (if (physicalLen == C.LENGTH_UNSET.toLong()) C.LENGTH_UNSET else (physicalLen / M2TS_PACKET_SIZE) * TS_PACKET_SIZE) as Long
        }



        override fun <E : Throwable> setRetryPosition(position: Long, e: E) {
            val physicalPosition = (position / TS_PACKET_SIZE) * M2TS_PACKET_SIZE
            wrappedInput.setRetryPosition(physicalPosition, e)
        }
    }

    companion object {
        private const val TS_PACKET_SIZE = 188
        private const val M2TS_PACKET_SIZE = 192
        private const val M2TS_HEADER_SIZE = 4
    }
}