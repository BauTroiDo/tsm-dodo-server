package tw.tsm.protocol

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Trình giải mã khung (frame) gói tin phiên bản game mobile
 *
 * Định dạng gói tin (tất cả các bytes XOR với 0xAD):
 * [header 2B] [length 2B LE = tổng kích thước frame] [flags 2B] [game_packet...]
 *
 * Output sau khi giải mã: game_packet đã được giải mã = [mainKind][subKind][compress][data...]
 */
class FrameDecoder : ByteToMessageDecoder() {

    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        while (input.readableBytes() >= FRAME_HEADER_SIZE) {
            input.markReaderIndex()

            // Đọc header(2) + length(2) = 4 bytes
            val header0 = (input.readByte().toInt() and 0xFF) xor XOR_KEY
            val header1 = (input.readByte().toInt() and 0xFF) xor XOR_KEY
            val lenLo = (input.readByte().toInt() and 0xFF) xor XOR_KEY
            val lenHi = (input.readByte().toInt() and 0xFF) xor XOR_KEY

            val totalLength = lenLo + (lenHi shl 8)

            // Xác thực header của frame (Giao thức cố định là 0xC0 0x91)
            if (header0 != HEADER_BYTE_0 || header1 != HEADER_BYTE_1) {
                logger.warn { "Header của frame không hợp lệ: ${header0.toString(16)},${header1.toString(16)}, đóng kết nối" }
                ctx.close()
                return
            }

            logger.debug { "Frame: totalLength=$totalLength readableAfter=${input.readableBytes()}" }

            if (totalLength < MIN_FRAME_SIZE || totalLength > MAX_FRAME_SIZE) {
                logger.warn { "Độ dài gói tin không hợp lệ: $totalLength, đóng kết nối" }
                ctx.close()
                return
            }

            // Số bytes cần đọc thêm = totalLength - 4 (đã đọc 4 bytes)
            val remaining = totalLength - 4
            if (input.readableBytes() < remaining) {
                input.resetReaderIndex()
                return
            }

            // Bỏ qua flags(2), đọc game_packet
            input.skipBytes(2)

            val packetSize = totalLength - FRAME_HEADER_SIZE
            val packet = ByteArray(packetSize)
            input.readBytes(packet)

            // Giải mã game_packet bằng phép XOR 0xAD
            for (i in packet.indices) {
                packet[i] = (packet[i].toInt() xor XOR_KEY).toByte()
            }

            out.add(packet)
        }
    }

    companion object {
        /** Khóa mã hóa XOR */
        const val XOR_KEY = 0xAD

        /** Giá trị cố định của header frame */
        private const val HEADER_BYTE_0 = 0xC0
        private const val HEADER_BYTE_1 = 0x91

        /** Kích thước header của frame: header(2) + length(2) + flags(2) = 6 */
        private const val FRAME_HEADER_SIZE = 6

        /** Kích thước frame tối thiểu: header(6) + mainKind(1) + subKind(1) + compress(1) = 9 */
        private const val MIN_FRAME_SIZE = FRAME_HEADER_SIZE + 3

        /** Kích thước gói tin tối đa (64 KB) */
        private const val MAX_FRAME_SIZE = 65536
    }
}