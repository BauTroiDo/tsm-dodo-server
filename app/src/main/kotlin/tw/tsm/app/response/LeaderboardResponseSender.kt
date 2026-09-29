package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.LeaderboardEntryDto
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.protocol.PacketWriter

/**
 * Tuần tự hoá gói tin bảng xếp hạng
 *
 * mainKind = 88
 * Nguồn: protocal.lua protocolTable[88], UILeaderboard.lua
 */
class LeaderboardResponseSender {

    /**
     * S:088-001 dữ liệu bảng xếp hạng
     *
     * Định dạng: +leaderboardId(1B) +page(1B) +maxPage(1B) +myRank(1B)
     *       +infoCount(1B) <<+scoreParam(4B LE)>>
     *       +count(1B) <<+rank(1B) +state(1B) +roleId(8B LE) +nameLen(1B) +name(Big5)
     *       +level(1B) +element(1B) +turn3Element(1B) +turn(1B) +career(1B)
     *       +scoreCount(1B) <<+score(4B LE)>>>>
     */
    fun sendLeaderboardData(
        session: SessionSender,
        leaderboardId: Int, page: Int, maxPage: Int, myRank: Int,
        myScoreParams: List<Int>,
        entries: List<LeaderboardEntryDto>
    ) {
        val writer = PacketWriter()
            .writeByte(Opcodes.LEADERBOARD).writeByte(1).writeByte(0)
            .writeByte(leaderboardId)
            .writeByte(page)
            .writeByte(maxPage)
            .writeByte(myRank)

        // scoreParams của chính mình
        writer.writeByte(myScoreParams.size)
        for (score in myScoreParams) {
            writer.writeIntLE(score)
        }

        // Danh sách xếp hạng
        writer.writeByte(entries.size)
        for (entry in entries) {
            writer
                .writeByte(entry.rank)
                .writeByte(entry.state)
                .writeLongLE(entry.roleId)
                .writeBig5WithByteLen(entry.name)
                .writeByte(entry.level)
                .writeByte(entry.element)
                .writeByte(entry.turn3Element)
                .writeByte(entry.turn)
                .writeByte(entry.career)

            writer.writeByte(entry.scoreParams.size)
            for (score in entry.scoreParams) {
                writer.writeIntLE(score)
            }
        }

        session.send(writer.toByteArray())
    }
}
