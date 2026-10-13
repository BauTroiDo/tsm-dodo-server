"""Fail closed: BAG -> S123 Treasure bank transfer is staging-only (2026-10-13).

Do not enable C023-015 without verified native 245 semantics and coordinated
in-session cache publication; this only proves the SQL primitive.
"""
import unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1]
def s(p):return (R/p).read_text(encoding="utf8")
class TreasureBankDormant(unittest.TestCase):
 def test_source_item_245_is_not_named_in_client_eitemusekind_enum(self):
  lua=s("lua/Data/ItemData.lua")
  enum=lua.split("EItemUseKind = {",1)[1].split("\n}",1)[0]
  self.assertNotIn("= 245,",enum)
  # Definition flag245 is not a documented client-side state transition.
 def test_authoritative_id_map_and_strict_atomic_bag_to_bank_primitive(self):
  repo=s("persistence/src/main/kotlin/tw/tsm/persistence/repositories/TreasureRepositoryImpl.kt")
  section=repo.split("override suspend fun bankCard(",1)[1].split("override suspend fun purchaseLevel(",1)[0]
  self.assertIn("val expectedItemId = originalCardItemIds[treasureId] ?: return false",section)
  self.assertIn("return suspendTransaction {",section)
  self.assertIn("Inventories.itemId] != expectedItemId",section)
  self.assertIn("Inventories.deleteWhere",section)
  self.assertIn("Inventories.update(",section)
  self.assertIn("CharacterTreasures.insert {",section)
  self.assertIn("CharacterTreasures.update(",section)
  self.assertIn("CharacterTreasures.version eq expectedVersion",section)
  self.assertIn("beforeCards.toLong() + quantity.toLong() > Int.MAX_VALUE",section)
  self.assertGreaterEqual(section.count(".forUpdate()"),2)
  self.assertNotIn("CharacterMoney.update(",section)
  self.assertNotIn("StarExp",section)
 def test_no_handler_runtime_migration_or_unverified_credits(self):
  handler=s("app/src/main/kotlin/tw/tsm/app/handlers/ItemHandler.kt")
  proc=s("gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/ItemUseProcessor.kt")
  treas=s("app/src/main/kotlin/tw/tsm/app/handlers/TreasureHandler.kt")
  di=s("app/src/main/kotlin/tw/tsm/app/ServerModule.kt")
  db=s("persistence/src/main/kotlin/tw/tsm/persistence/DatabaseFactory.kt")
  self.assertNotIn("bankCard(",handler)
  self.assertNotIn("bankCard(",proc)
  self.assertNotIn("bankCard(",treas)
  self.assertNotIn("TreasureRepositoryImpl(",di)
  self.assertNotIn("CharacterTreasures,",db)
  self.assertIn("if (def.kind == 98)",proc)
  self.assertIn("return ItemUseResult(success = false, consumed = false)",proc)
 def test_h2_rollback_and_replay_cases_are_tested(self):
  tests=s("tests/src/test/kotlin/tw/tsm/tests/DvtTreasureCardBankTransactionTest.kt")
  for name in ("firstValidBankedCopyDebitsActualBagAndCreatesExactlyOneS123Row",
               "wrongItemSlotQuantityOrTreasureIdCannotCreditBank",
               "existingBalanceCanOnlyBeIncrementedOnceAtExpectedVersion",
               "failedFinalS123InsertRollsBackPhysicalBagDebit",
               "failedFinalS123UpdateRollsBackBagDebitAndPreservesVersion",
               "unwiredEmptyCatalogCanNeverBankCard"):
   self.assertIn(name,tests)
if __name__=="__main__":unittest.main()
