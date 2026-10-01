package tw.tsm.tests

import io.mockk.mockk
import tw.tsm.domain.data.CommonAwardEntry
import tw.tsm.domain.data.DispatchBonusDef
import tw.tsm.domain.data.DispatchDef
import tw.tsm.domain.data.LuckyBagDef
import tw.tsm.domain.interfaces.repositories.DispatchRepository
import tw.tsm.gamelogic.systems.DispatchSystem
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.MoneySystem
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.gamelogic.systems.PointSystem
import tw.tsm.gamelogic.systems.RoleCountSystem

internal fun dispatchSystemForTest(
    repo: DispatchRepository,
    pets: PetSystem,
    defs: Map<Int, Map<Int, List<DispatchDef>>>,
    bonusDefs: Map<Int, DispatchBonusDef>,
    commonAwards: Map<Int, List<CommonAwardEntry>> = emptyMap(),
    luckyBags: Map<Int, LuckyBagDef> = emptyMap(),
    inventory: InventorySystem = mockk(relaxed = true),
    money: MoneySystem = mockk(relaxed = true),
    points: PointSystem = mockk(relaxed = true),
    roleCounts: RoleCountSystem = mockk(relaxed = true),
): DispatchSystem = DispatchSystem(
    repo,
    pets,
    defs,
    bonusDefs,
    commonAwards,
    luckyBags,
    inventory,
    money,
    points,
    roleCounts,
)
