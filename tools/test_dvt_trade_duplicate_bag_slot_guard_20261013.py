"""Revalidate C025 item trade's bag slot uniqueness at upload and settlement."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def src(p):return (ROOT/p).read_text(encoding='utf8')
class DvtTradeDuplicateSlotSafety(unittest.TestCase):
 def test_uploaded_offer_and_final_commit_validate_distinct_bag_slots(self):
  t=src('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeSystem.kt')
  self.assertIn('slots.size == slots.toSet().size && slots.all { it in 0..255 }',t)
  block=t.split('fun uploadItems(',1)[1].split('fun uploadPet(',1)[0]
  self.assertIn('if (!hasUniqueTradeBagSlots(bagIndices))',block)
  self.assertIn('cancelTradeInternal(trade, playerId, REASON_SYSTEM',block)
  final=t.split('private fun executeTrade(',1)[1].split('private fun executePetTrade(',1)[0]
  self.assertIn('!hasUniqueTradeBagSlots(offerA.bagIndices)',final)
  self.assertIn('!hasUniqueTradeBagSlots(offerB.bagIndices)',final)
  self.assertLess(final.index('!hasUniqueTradeBagSlots(offerA.bagIndices)'),final.index('inventorySystem.setBagItemSync('))
 def test_existing_two_player_fences_and_packet_path_remain_unchanged(self):
  t=src('gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/TradeSystem.kt')
  self.assertIn('CharacterPersistenceGuard.withLocks(idA, idB)',t)
  self.assertIn('responseSender.sendTradeCancel(sessionA, REASON_SUCCESS)',t)
  self.assertIn('responseSender.sendTradeCancel(sessionB, REASON_SUCCESS)',t)
  self.assertNotIn('commitTwoCharacterTradeAtomic(',t)
 def test_kotlin_duplicate_and_range_cases_exist(self):
  t=src('tests/src/test/kotlin/tw/tsm/tests/DvtTradeDuplicateBagSlotSafetyTest.kt')
  for name in ('uniqueOriginalU8IndicesAreAccepted','repeatedSlotThatCouldCloneOneStackIsRejected','forgedOutOfProtocolRangeIndicesCannotPass'):
   self.assertIn(name,t)
if __name__=='__main__':unittest.main()
