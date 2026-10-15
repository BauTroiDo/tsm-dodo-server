package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.ItemDatLoader
import tw.tsm.gamelogic.systems.MiningSystem

/**
 * Client staging AssetBundles/Data/IOS_Item_C.dat and DEV File.dat/Data-ios
 * have identical SHA256 51f93c59411105d4de61f154bb29c0db10ca41c36b40ab4b1c99f728abc7845b.
 * Original Lua UIMine.OnClick_Set takes itemDatas[itemId].threshold for fuel.
 */
class Dvt115MineFuelOriginalItemDataTest {
    @Test fun miningFoodAmountsComeFromOriginalIOSItemData() {
        val relative = Path.of("File.dat", "Data-ios", "IOS_Item_C.dat")
        val candidates = listOf(relative, Path.of("..").resolve(relative))
        val source = candidates.firstOrNull { Files.isRegularFile(it) }
            ?: error("Original IOS_Item_C.dat missing in worktree")
        val all = ItemDatLoader.load(Files.readAllBytes(source))
        for ((index, id) in MiningSystem.FUEL_ITEM_IDS.withIndex()) {
            val def = all[id] ?: error("Missing original DVT food item " + id)
            println("DVT_FOOD_original index=" + index + " itemId=" + id +
                " threshold=" + def.threshold + " name=" + def.name)
            assertEquals(10, def.threshold, "DVT 1.15 client food fullness differs id=" + id)
        }
        assertEquals(4, MiningSystem.FUEL_ITEM_IDS.toSet().size)
    }
}
