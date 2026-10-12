"""DVT stage1 quest123xx: source-specific chest and catch-mob entry Door navigation."""
import unittest
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_quest_navigation import audit,read_steps
from patch_dvt_123xx_chest_capture_entry_doors_20261012 import TARGETS,source_proof,patch,sha

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios/IOS_MarkStep_C.dat"
EVE=ROOT/"gamedata/Eve.emg"
SHA="5d89bbc211ae6c1cbc9cf55e846cbb13634adc8b25cd081897ba7d11a28896f0"
class ChestCaptureQuestStage1EveDoors(unittest.TestCase):
 def test_all_eight_genuine_original_door_eve_targets(self):
  self.assertEqual(len(TARGETS),8)
  self.assertEqual(source_proof(EVE),{q:spec[:5] for q,spec in TARGETS.items()})
  arc=EveArchive(EVE)
  for q,(sid,x,y,kind,did,eid,mode) in TARGETS.items():
   with self.subTest(quest=q):
    sc=arc.parse_scene(sid)
    door=next(d for d in sc["doors"] if d["id"]==did)
    self.assertEqual((door["x"],door["y"],door["events"]),(x,y,[eid]))
    self.assertFalse(any(eid in n["events"] for n in sc["npcs"]))
    event=sc["events"][eid]
    self.assertEqual(event["when_happen"],[False,True,False,False])
    self.assertEqual(len(event["conditions"]),1)
    gate=event["conditions"][0]
    self.assertEqual((gate["class"],gate["param"],gate["pstyle"],gate["ops"],gate["val"],
                      gate["and_num"]),(2,q,1,5,1,1))
    self.assertTrue(any(z["type"]==0 and z["class"]==2 and
                  z["param"]==q and z["pstyle"]==1 and z["val"]==1 for z in gate["results"]))
    self.assertTrue(any(z["type"]==0 and z["class"]==3 for z in gate["results"]))
 def test_only_first_stage_per_quest_has_unique_door_endpoint(self):
  rows={(x["mission"],x["step"]):x for x in read_steps(DATA)}
  for q,spec in TARGETS.items():
   with self.subTest(quest=q):
    self.assertEqual(rows[(q,1)]["end"],spec[:5])
    self.assertFalse(any(x[0] for x in rows[(q,1)]["conditions"]))
    from patch_dvt_123xx_four_capture_battle_routes_20261012 import ARENAS as CAPTURE_ARENAS
    from patch_dvt_123xx_four_chest_choice_routes_20261012 import ARENAS as CHEST_ARENAS
    for step in range(2,6):
     expected=(0,0,0,0,0)
     if q in CAPTURE_ARENAS:
      sid,npcid,coords=CAPTURE_ARENAS[q]
      expected=(sid,*coords[step-2],1,step)
     if q in CHEST_ARENAS:
      sid,choices=CHEST_ARENAS[q]
      npc,x,y,eid=choices[step-2]
      expected=(sid,x,y,1,npc)
     self.assertEqual(rows[(q,step)]["end"],expected)
 def test_original_later_chest_and_capture_eve_events_unmodified(self):
  arc=EveArchive(EVE)
  for q,(sid,x,y,k,did,eid,mode) in TARGETS.items():
   with self.subTest(quest=q,mode=mode):
    scene=arc.parse_scene(sid)
    next_events=[e for eid,e in scene["events"].items() if
          eid!=1 and any(c["class"]==2 and c["param"]==q for c in e["conditions"])]
    self.assertTrue(next_events)
    if mode=="capture":
     self.assertTrue(any(any(c["class"]==8 for c in ev["conditions"]) for ev in next_events))
    else:
     self.assertTrue(any(any(c["class"]==2 and c["val"] in (3,4) for c in ev["conditions"]) for ev in next_events))
 def test_checksum_and_idempotent_safe_patch(self):
  raw=DATA.read_bytes()
  self.assertEqual(sha(raw),SHA)
  self.assertEqual(patch(DATA,EVE,False),SHA)
  self.assertEqual(DATA.read_bytes(),raw)
 def test_valid_scene_door_and_static_census(self):
  report=audit(DATA,EVE)
  self.assertEqual(report["markstep_rows"],4505)
  self.assertEqual(report["targets"]["located"],5921)
  self.assertEqual(sum(1 for x in read_steps(DATA) if x["end"]==(0,0,0,0,0)),70)
  self.assertFalse(report["missing_scenes"])
  self.assertFalse(report["missing_npcs"])
  self.assertFalse(report["missing_doors"])
if __name__=="__main__":unittest.main()
