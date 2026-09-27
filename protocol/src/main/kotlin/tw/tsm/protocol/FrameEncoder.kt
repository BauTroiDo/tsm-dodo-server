package tw.tsm.protocol

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder

/**
 * Trình mã hóa khung (frame) gói tin phiên bản game mobile
 *
 * Định dạng gói tin (tất cả các bytes XOR với 0xAD):
 * [header 2B] [length 2B LE = tổng kích thước frame] [flags 2B] [game_packet...]
 *
 * Quy trình mã hóa:
 * 1. Tính totalLength = 6 + game_packet.size
 * 2. Ghi header (sau khi XOR)
 * 3. Ghi length (sau khi XOR)
 * 4. Ghi flags (sau khi XOR)
 * 5. Ghi game_packet, mỗi byte XOR với 0xAD
 */
class FrameEncoder : MessageToByteEncoder<ByteArray>() {

    override fun encode(ctx: ChannelHandlerContext, msg: ByteArray, out: ByteBuf) {
        val totalLength = FRAME_HEADER_SIZE + msg.size

        // header: 0xC0, 0x91 (Ghi ra sau khi XOR với 0xAD)
        out.writeByte(HEADER_0 xor XOR_KEY)
        out.writeByte(HEADER_1 xor XOR_KEY)

        // length: uint16 LE (Ghi ra sau khi XOR với 0xAD)
        out.writeByte((totalLength and 0xFF) xor XOR_KEY)
        out.writeByte(((totalLength shr 8) and 0xFF) xor XOR_KEY)

        // flags: 0x00, 0x00 (Ghi ra sau khi XOR với 0xAD)
        out.writeByte(0x00 xor XOR_KEY)
        out.writeByte(0x00 xor XOR_KEY)

        // game_packet: mỗi byte XOR với 0xAD
        for (b in msg) {
            out.writeByte(b.toInt() xor XOR_KEY)
        }
    }

    companion object {
        /** Khóa mã hóa XOR */
        private const val XOR_KEY = FrameDecoder.XOR_KEY

        /** Kích thước header của frame: header(2) + length(2) + flags(2) = 6 */
        private const val FRAME_HEADER_SIZE = 6

        /** Bytes header S→C (Tạm thời dùng giá trị giống C→S, nếu Client không nhận thì sẽ điều chỉnh lại) */
        private const val HEADER_0 = 0xC0
        private const val HEADER_1 = 0x91
    }
}