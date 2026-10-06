package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.TitleDef

/** Server-side mirror of UITitle's ownership gate. */
object DvtTitlePolicy {
    fun canEquip(titleId: Int, titleDefs: Map<Int, TitleDef>, ownedFlags: Set<Int>): Boolean {
        if (titleId == 0) return true
        val title = titleDefs[titleId] ?: return false
        return title.flagId == 0 || title.flagId in ownedFlags
    }
}
