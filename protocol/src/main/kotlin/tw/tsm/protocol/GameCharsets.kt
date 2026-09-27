package tw.tsm.protocol

import java.nio.charset.Charset

/**
 * Hằng số bảng mã ký tự sử dụng trong gói tin game
 *
 * Dùng chung cho PacketReader/PacketWriter để tránh định nghĩa lặp lại.
 */
object GameCharsets {
    /** Bảng mã Big5 (Chuỗi tiếng Trung phồn thể) */
    val BIG5: Charset = Charset.forName("Big5")

    /** Bảng mã UTF-16LE (Một phần chuỗi Unicode) */
    val UTF16LE: Charset = Charset.forName("UTF-16LE")

    /** Bảng mã UTF-8 (Tương thích tên tiếng Anh và ký tự đa ngôn ngữ) */
    val UTF8: Charset = Charset.forName("UTF-8")
}