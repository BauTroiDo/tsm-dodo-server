package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.enums.TeamState
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Hệ thống tổ đội — Quản lý tổ đội/rời đội/đá người/đổi đội trưởng/quân sư
 *
 * Chỉ thao tác trên bộ nhớ (RAM), không cần lưu trữ vĩnh viễn (persistence) vào DB.
 * Nguồn: Team.lua
 *
 * Cấu trúc dữ liệu:
 * - leaders: playerId của đội trưởng → playerId của quân sư (0 = không có quân sư)
 * - members: playerId của người chơi → playerId của đội trưởng trực thuộc
 */
class PartySystem {

    companion object {
        /** Số thành viên tối đa của tổ đội (Bao gồm cả đội trưởng) */
        const val MAX_MEMBERS = 5
    }

    /** Khóa toàn cục, bảo vệ tính nguyên tử (atomicity) của các thao tác nhiều bước xuyên suốt bản đồ (map) */
    private val lock = Any()

    /** Đội trưởng → Quân sư (0 = Không có quân sư) */
    private val leaders = ConcurrentHashMap<Long, Long>()

    /** Người chơi → Đội trưởng trực thuộc */
    private val members = ConcurrentHashMap<Long, Long>()

    /** Đội trưởng → Tập hợp đội viên (Chỉ mục ngược, tránh việc quét toàn bộ bảng O(n)) */
    private val partyMembers = ConcurrentHashMap<Long, MutableSet<Long>>()

    // ── Phương thức truy vấn ──

    /**
     * Lấy trạng thái tổ đội của người chơi
     */
    fun getTeamState(playerId: Long): TeamState {
        if (leaders.containsKey(playerId)) {
            return TeamState.LEADER
        }
        val leaderId = members[playerId] ?: return TeamState.NONE
        if (leaders[leaderId] == playerId) {
            return TeamState.ADVISER
        }
        return TeamState.MEMBER
    }

    /**
     * Lấy ID đội trưởng của người chơi (Nếu bản thân là đội trưởng thì trả về chính mình)
     */
    fun getLeaderId(playerId: Long): Long {
        if (leaders.containsKey(playerId)) return playerId
        return members[playerId] ?: 0L
    }

    /**
     * Lấy ID quân sư của tổ đội (0 = Không có quân sư)
     */
    fun getAdviserId(leaderId: Long): Long {
        return leaders[leaderId] ?: 0L
    }

    /**
     * Lấy tất cả thành viên của tổ đội (Bao gồm cả đội trưởng)
     */
    fun getTeamMembers(leaderId: Long): List<Long> {
        if (!leaders.containsKey(leaderId)) return emptyList()
        return partyMembers[leaderId]?.toList() ?: emptyList()
    }

    /**
     * Lấy số lượng người hiện tại của tổ đội
     */
    fun getTeamSize(leaderId: Long): Int {
        if (!leaders.containsKey(leaderId)) return 0
        return partyMembers[leaderId]?.size ?: 0
    }

    /**
     * Kiểm tra xem người chơi có đang trong tổ đội hay không
     */
    fun isInParty(playerId: Long): Boolean {
        return members.containsKey(playerId)
    }

    // ── Phương thức thao tác ──

    /**
     * Thành lập tổ đội (Khi 2 người tạo đội thành công)
     *
     * @return true Thành công
     */
    fun createParty(leaderId: Long, memberId: Long): Boolean {
        synchronized(lock) {
            if (members.containsKey(leaderId) || members.containsKey(memberId)) {
                logger.warn { "Tạo tổ đội thất bại: $leaderId hoặc $memberId đã ở trong đội" }
                return false
            }
            leaders[leaderId] = 0L  // Không có quân sư
            members[leaderId] = leaderId
            members[memberId] = leaderId
            partyMembers[leaderId] = mutableSetOf(leaderId, memberId)
            logger.info { "Tổ đội được thành lập: Đội trưởng=$leaderId Đội viên=$memberId" }
            return true
        }
    }

    /**
     * Tham gia vào tổ đội đã có
     *
     * @return 0=Thành công, 1=Đội đối phương đã đầy, 2=Đội mình đã đầy, 3=Đang trong sự kiện
     */
    fun addMember(leaderId: Long, newMemberId: Long): Int {
        synchronized(lock) {
            if (!leaders.containsKey(leaderId)) return 6  // Tổ đội không tồn tại
            if (members.containsKey(newMemberId)) return 2  // Bản thân đã có đội
            if (getTeamSize(leaderId) >= MAX_MEMBERS) return 1  // Tổ đội đã đầy
            members[newMemberId] = leaderId
            partyMembers.getOrPut(leaderId) { mutableSetOf() }.add(newMemberId)
            logger.info { "Gia nhập tổ đội: Đội trưởng=$leaderId Đội viên mới=$newMemberId Số người=${getTeamSize(leaderId)}" }
            return 0
        }
    }

    /**
     * Người chơi rời khỏi tổ đội
     *
     * @return Trạng thái tổ đội sau khi rời đi: Danh sách thành viên (Nếu rỗng nghĩa là đã giải tán), null nghĩa là người chơi không ở trong đội
     */
    fun leaveParty(playerId: Long): LeaveResult? {
        synchronized(lock) {
            val leaderId = members[playerId] ?: return null

            if (playerId == leaderId) {
                // Đội trưởng rời đội → Giải tán tổ đội
                return disbandParty(leaderId)
            }

            // Đội viên bình thường hoặc quân sư rời đội
            members.remove(playerId)
            partyMembers[leaderId]?.remove(playerId)

            // Nếu là quân sư, xóa bỏ thân phận quân sư
            if (leaders[leaderId] == playerId) {
                leaders[leaderId] = 0L
            }

            // Chỉ còn lại một mình đội trưởng → Tự động giải tán
            val remaining = getTeamMembers(leaderId)
            if (remaining.size <= 1) {
                return disbandParty(leaderId)
            }

            logger.info { "Rời khỏi tổ đội: $playerId rời khỏi đội của đội trưởng $leaderId, còn lại ${remaining.size} người" }
            return LeaveResult(leaderId = leaderId, disbanded = false, affectedMembers = remaining)
        }
    }

    /**
     * Giải tán tổ đội
     */
    private fun disbandParty(leaderId: Long): LeaveResult {
        val allMembers = getTeamMembers(leaderId)
        for (pid in allMembers) {
            members.remove(pid)
        }
        leaders.remove(leaderId)
        partyMembers.remove(leaderId)
        logger.info { "Giải tán tổ đội: Đội trưởng=$leaderId Thành viên=$allMembers" }
        return LeaveResult(leaderId = leaderId, disbanded = true, affectedMembers = allMembers)
    }

    /**
     * Đội trưởng đá người
     *
     * @return true Thành công
     */
    fun kickMember(leaderId: Long, targetId: Long): Boolean {
        synchronized(lock) {
            if (!leaders.containsKey(leaderId)) return false
            if (targetId == leaderId) return false  // Không thể đá chính mình
            if (members[targetId] != leaderId) return false  // Không cùng đội

            members.remove(targetId)
            partyMembers[leaderId]?.remove(targetId)

            // Nếu là quân sư thì xóa bỏ
            if (leaders[leaderId] == targetId) {
                leaders[leaderId] = 0L
            }

            // Chỉ còn lại một mình đội trưởng → Tự động giải tán
            val remaining = getTeamMembers(leaderId)
            if (remaining.size <= 1) {
                disbandParty(leaderId)
            }

            logger.info { "Đá đội viên: Đội trưởng=$leaderId Người bị đá=$targetId" }
            return true
        }
    }

    /**
     * Thiết lập quân sư
     *
     * @return true Thành công
     */
    fun setAdviser(leaderId: Long, adviserId: Long): Boolean {
        synchronized(lock) {
            if (!leaders.containsKey(leaderId)) return false
            if (adviserId == leaderId) return false  // Đội trưởng không thể làm quân sư
            if (members[adviserId] != leaderId) return false  // Không cùng đội
            if (leaders[leaderId] != 0L) return false  // Đã có quân sư rồi

            leaders[leaderId] = adviserId
            logger.info { "Thiết lập quân sư: Đội trưởng=$leaderId Quân sư=$adviserId" }
            return true
        }
    }

    /**
     * Hủy bỏ quân sư
     *
     * @return true Thành công
     */
    fun removeAdviser(leaderId: Long, adviserId: Long): Boolean {
        synchronized(lock) {
            if (leaders[leaderId] != adviserId) return false

            leaders[leaderId] = 0L
            logger.info { "Hủy bỏ quân sư: Đội trưởng=$leaderId Cựu quân sư=$adviserId" }
            return true
        }
    }

    /**
     * Đổi đội trưởng
     *
     * @return Danh sách thành viên mới (Không bao gồm tân đội trưởng), null nếu thất bại
     */
    fun changeLeader(oldLeaderId: Long, newLeaderId: Long): List<Long>? {
        synchronized(lock) {
            if (!leaders.containsKey(oldLeaderId)) return null
            if (newLeaderId == oldLeaderId) return null
            if (members[newLeaderId] != oldLeaderId) return null  // Không cùng đội

            // Chuyển giao chức đội trưởng
            leaders.remove(oldLeaderId)
            leaders[newLeaderId] = 0L  // Tân đội trưởng mặc định không có quân sư

            // Cập nhật đội trưởng trực thuộc cho tất cả thành viên
            val oldMembers = partyMembers.remove(oldLeaderId) ?: mutableSetOf()
            val allMembers = mutableListOf<Long>()
            for (pid in oldMembers) {
                members[pid] = newLeaderId
                if (pid != newLeaderId) allMembers.add(pid)
            }
            partyMembers[newLeaderId] = oldMembers

            logger.info { "Đổi đội trưởng: $oldLeaderId → $newLeaderId Thành viên=$allMembers" }
            return allMembers
        }
    }

    /**
     * Dọn dẹp tổ đội khi người chơi ngắt kết nối
     */
    fun handleDisconnect(playerId: Long) {
        if (!members.containsKey(playerId)) return
        leaveParty(playerId)
    }

    /**
     * Lấy tất cả dữ liệu tổ đội (Dùng để đồng bộ khi đăng nhập)
     *
     * @return ID Đội trưởng → Danh sách đội viên
     */
    fun getAllParties(): Map<Long, List<Long>> {
        val result = mutableMapOf<Long, MutableList<Long>>()
        for ((pid, lid) in members) {
            result.getOrPut(lid) { mutableListOf() }.add(pid)
        }
        return result
    }

    /**
     * Lấy danh sách ID của tất cả quân sư
     */
    fun getAllAdvisers(): List<Long> {
        return leaders.values.filter { it > 0L }.toList()
    }

    /**
     * Kết quả rời đội
     */
    data class LeaveResult(
        val leaderId: Long,
        val disbanded: Boolean,
        val affectedMembers: List<Long>
    )
}