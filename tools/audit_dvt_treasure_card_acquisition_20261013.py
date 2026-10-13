"""Source triage: acquisition events for 114 DVT Treasure cards.

Observational audit only. An Eve item award is not evidence that a separate
S123 Treasure entitlement was banked. Detect all possible item mentions in
original DVT Eve result packets without mutating game data.
"""
from collections import Counter, defaultdict
from pathlib import Path
from eve_tool import EveArchive
from audit_dvt_treasure_tables_20261012 import load_treasure_item_catalog

ROOT=Path(__file__).resolve().parents[1]
CARD_ITEMS={item_id:skill_link for skill_link,(item_id,_) in load_treasure_item_catalog().items()}
MATERIALS={29869:"level material",29870:"star material"}
scan_ids=set(CARD_ITEMS)|set(MATERIALS)
def scan():
    eve=EveArchive(ROOT/"gamedata/Eve.emg")
    mentions=[]
    class_count=Counter()
    for scene_id in eve.scenes:
        scene=eve.parse_scene(scene_id)
        for eid, ev in scene["events"].items():
            for cond in ev["conditions"]:
                for result in cond["results"]:
                    if result["class"]!=1:
                        continue
                    class_count[(result["type"],result["class"],result["pstyle"])]+=1
                    if result["param"] not in scan_ids:continue
                    mentions.append({
                        "scene":scene_id,"eve":eid,"condition_no":cond["no"],
                        "cond_class":cond["class"],"cond_param":cond["param"],
                        "cond_pstyle":cond["pstyle"],"cond_ops":cond["ops"],"cond_val":cond["val"],
                        "result_type":result["type"],"result_class":result["class"],
                        "result_param":result["param"],"result_pstyle":result["pstyle"],
                        "result_val":result["val"],"card_skill_link":CARD_ITEMS.get(result["param"],0),
                        "material":MATERIALS.get(result["param"],""),
                        "npc_owners":[n["id"] for n in scene["npcs"] if eid in n["events"]],
                        "door_owners":[d["id"] for d in scene["doors"] if eid in d["events"]],
                    })
    return mentions,class_count
if __name__=="__main__":
    import json
    mentions,counts=scan()
    print("TOTAL_CARD_AND_MATERIAL_REFERENCES",len(mentions))
    print("RECORD_CLASS1_STYLES",counts.most_common(30))
    print("RESULT_REFS",json.dumps(mentions[:100],ensure_ascii=False,indent=2))
    card=Counter(r["result_param"] for r in mentions if r["card_skill_link"])
    print("CARD_IDS_REFERENCED",len(card),sorted(card.items()))
    print("MATERIAL_REFERENCE_COUNTS",Counter(r["result_param"] for r in mentions if r["material"]))
