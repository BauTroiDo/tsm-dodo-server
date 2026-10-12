"""Guard dormant DVT Treasure SQL accounting until cache and StarExp are proven."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def src(path):return (ROOT/path).read_text(encoding="utf8")
class DvtTreasureTransactionStage(unittest.TestCase):
 def test_state_table_has_character_card_primary_key_and_version(self):
  t=src('persistence/src/main/kotlin/tw/tsm/persistence/tables/CharacterTreasures.kt')
  self.assertIn('Table("character_treasures")',t)
  self.assertIn('PrimaryKey(characterId, treasureId)',t)
  for col in ('card_copies_total','star_exp','version','updated_at_ms'):
   self.assertIn(col,t)
 def test_unwired_db_migration_and_handler_are_safe(self):
  factory=src('persistence/src/main/kotlin/tw/tsm/persistence/DatabaseFactory.kt')
  handler=src('app/src/main/kotlin/tw/tsm/app/handlers/TreasureHandler.kt')
  wiring=src('app/src/main/kotlin/tw/tsm/app/ServerModule.kt')
  self.assertNotIn('CharacterTreasures,',factory)
  self.assertNotIn('TreasureRepository',wiring)
  self.assertNotIn('purchaseLevel(',handler)
  self.assertIn('.writeByte(255)',handler)
 def test_transaction_has_authoritative_costs_locks_version_and_single_scope(self):
  repo=src('persistence/src/main/kotlin/tw/tsm/persistence/repositories/TreasureRepositoryImpl.kt')
  self.assertIn('private val originalLevelCosts',repo)
  self.assertIn('val cost = originalLevelCosts[treasureId to targetLevel] ?: return false',repo)
  self.assertIn('return suspendTransaction {',repo)
  self.assertGreaterEqual(repo.count('.forUpdate()'),3)
  self.assertIn('CharacterTreasures.version eq expectedVersion',repo)
  self.assertIn('Inventories.storageType eq 1',repo)
  self.assertIn('Inventories.itemId] != 29869',repo)
  self.assertIn('CharacterMoney.update(',repo)
  self.assertIn('CharacterTreasures.update(',repo)
  self.assertIn('Inventories.deleteWhere',repo)
  self.assertNotIn('StarExp',repo)
  self.assertNotIn('insert {',repo)
 def test_reference_data_is_original_not_fake_defaults(self):
  header=src('domain/src/main/kotlin/tw/tsm/domain/interfaces/repositories/TreasureRepository.kt')
  self.assertIn('cardCopiesTotal',header)
  self.assertIn('expectedVersion',header)
  self.assertNotIn('suspend fun grant',header)
  test=src('tests/src/test/kotlin/tw/tsm/tests/DvtTreasureAtomicPersistenceRegressionTest.kt')
  self.assertIn('DB_CLOSE_DELAY=-1;MODE=MySQL',test)
  self.assertIn('assertFalse(repo.purchaseLevel(charId,1,0,0,7))',test)
if __name__=='__main__':unittest.main()
