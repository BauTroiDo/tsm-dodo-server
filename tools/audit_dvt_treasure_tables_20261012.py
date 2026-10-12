#!/usr/bin/env python3
"""Decode the SIX original DVT Treasure_C.dat tables directly from source Lua.

Read-only: no server loot, ownership, item costs, account, or client mutations.
"""
from __future__ import annotations
from collections import Counter,defaultdict
from dataclasses import dataclass
from pathlib import Path
import hashlib,struct

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"File.dat/Data-ios"
# Fields copied in exact read order from lua/Data/Treasure*Data.lua
SCHEMAS={
 "TreasureActivityCriteria":("<HIBH",("id","string","type","number")),
 "TreasureGroupInfo":("<HI6H",("id","string","treasureIds")),
 "TreasureGroupLV":("<HBHHHi",("id","level","environmentId","characterId","att","value")),
 "TreasureLv":("<HBHHHiBii",("id","level","environmentId","characterId","att","value","count","itemCount","moneyCount")),
 "TreasureRoleCriteria":("<HBBBBH",("id","role","element","turn","country","collectBookId")),
 "TreasureStar":("<HBHHHiii",("id","star","environmentId","characterId","att","value","itemCount","moneyCount")),
}
def load_table(key: str, base=DATA):
    fmt,fields=SCHEMAS[key]
    path=Path(base)/f"IOS_{key}_C.dat"
    blob=path.read_bytes()
    if len(blob)<4:raise ValueError(f"{key} truncated header")
    count,=struct.unpack_from("<i",blob)
    size=struct.calcsize(fmt)
    if count<0 or len(blob)!=4+count*size:
        raise ValueError(f"{key} requires {count}*{size}+4={4+count*size}, has {len(blob)}")
    records=[]
    for i in range(count):
        raw=struct.unpack_from(fmt,blob,4+i*size)
        if key=="TreasureGroupInfo":
            raw=(raw[0],raw[1],raw[2:])
        row=dict(zip(fields,raw))
        records.append(row)
    return {"name":key,"rows":records,"count":count,"size":size,
            "sha256":hashlib.sha256(blob).hexdigest(),"length":len(blob)}

def load_all(base=DATA):
    return {name:load_table(name,base) for name in SCHEMAS}

def audit(tables):
    rows={name:t["rows"] for name,t in tables.items()}
    level=rows["TreasureLv"]; star=rows["TreasureStar"]
    groups=rows["TreasureGroupInfo"];gl=rows["TreasureGroupLV"]
    ids={r["id"] for r in level}
    duplicate_levels=[key for key,n in Counter((r["id"],r["level"]) for r in level).items() if n!=1]
    duplicate_stars=[key for key,n in Counter((r["id"],r["star"]) for r in star).items() if n!=1]
    level_by_id=defaultdict(set);star_by_id=defaultdict(set)
    for row in level:level_by_id[row["id"]].add(row["level"])
    for row in star:star_by_id[row["id"]].add(row["star"])
    missing_level={k: sorted(set(range(1,11))-v) for k,v in level_by_id.items() if set(range(1,11))-v}
    missing_star={k: sorted(set(range(1,7))-v) for k,v in star_by_id.items() if set(range(1,7))-v}
    groups_with_missing_ids={r["id"]:[tid for tid in r["treasureIds"] if tid and tid not in ids] for r in groups if any(tid and tid not in ids for tid in r["treasureIds"])}
    group_id_set={r["id"] for r in groups}
    unmapped_group_levels=sorted({r["id"] for r in gl}-group_id_set)
    invalid_costs={kind:[(r["id"],r.get("level",r.get("star"))) for r in entries if r.get("itemCount",0)<0 or r.get("moneyCount",0)<0] for kind,entries in (("level",level),("star",star))}
    return {
      "treasure_ids":sorted(ids),
      "treasure_count":len(ids),
      "star_ids":sorted(star_by_id),
      "combination_ids":sorted(group_id_set),
      "level_rows_by_id":dict(sorted(Counter(r["id"] for r in level).items())),
      "star_rows_by_id":dict(sorted(Counter(r["id"] for r in star).items())),
      "lv_values":dict(sorted(Counter(r["level"] for r in level).items())),
      "star_values":dict(sorted(Counter(r["star"] for r in star).items())),
      "duplicate_levels":duplicate_levels,
      "duplicate_stars":duplicate_stars,
      "missing_levels":missing_level,
      "missing_stars":missing_star,
      "group_missing_treasure_refs":groups_with_missing_ids,
      "group_lv_missing_group_refs":unmapped_group_levels,
      "invalid_costs":invalid_costs,
      "level_item_count_range":[min(r["itemCount"] for r in level),max(r["itemCount"] for r in level)],
      "level_money_count_range":[min(r["moneyCount"] for r in level),max(r["moneyCount"] for r in level)],
      "star_item_count_range":[min(r["itemCount"] for r in star),max(r["itemCount"] for r in star)],
      "star_money_count_range":[min(r["moneyCount"] for r in star),max(r["moneyCount"] for r in star)],
      "level_card_count_range":[min(r["count"] for r in level),max(r["count"] for r in level)],
      "group_nonzero_card_counts":dict(sorted(Counter(sum(bool(v) for v in r["treasureIds"]) for r in groups).items())),
    }
if __name__=="__main__":
    import json
    tables=load_all()
    for name,t in tables.items():
        print("TABLE",name,"COUNT",t["count"],"ROW",t["size"],
            "LEN",t["length"],"SHA",t["sha256"])
        print("FIRST",t["rows"][0],"LAST",t["rows"][-1])
    print("AUDIT",json.dumps(audit(tables),ensure_ascii=False,indent=2))

# File.lock: canonical DVT packed tables on feature branch, not DEV mutations.
EXPECTED_SHA256={
 "TreasureActivityCriteria":"3c5b12cff73c284e2eb748c48c95cdfc5545b6e4e62bb7be3b0ab13c0bc90501",
 "TreasureGroupInfo":"060ae17bcfddfe8e113caeb8d87093ab3a6b24565e74e9d0d4c5fa57b1f94116",
 "TreasureGroupLV":"278496f1256f8257c0aaa7af9c2624f849e53c04c071f6363d204b3ef47dd13c",
 "TreasureLv":"e1067500d8a00d3ca76fc341e69d81f8f33fc08fa6901c12138a654e1cc05678",
 "TreasureRoleCriteria":"cf6a50dcc31f3c9f93f91706327820e3731f14fb32ee15201b365c0be5c8ce85",
 "TreasureStar":"075dc315bdf2dd8f7944e3a06a2f60930b3f31d1c1f167fa25e5c695a14bf864",
}

def load_treasure_item_catalog(file=DATA/"IOS_Item_C.dat"):
    """Return original ItemData kind98 -> (item id, skillLink, name).

    Exact field widths taken from ItemData.New(reader) and ItemDatLoader.kt,
    *not* guessed from a compact item table or texture IDs.
    """
    raw=Path(file).read_bytes()
    cursor=0
    def take(fmt):
        nonlocal cursor
        data=struct.unpack_from(fmt,raw,cursor)
        cursor+=struct.calcsize(fmt)
        return data
    records=take("<i")[0]
    if records<0:raise ValueError("Item_C negative count")
    treasure={}
    for _ in range(records):
        length=take("<H")[0]
        name=raw[cursor:cursor+length].decode("utf-16le");cursor+=length
        kind,item_id=take("<BH")
        take("<HHH") # icon and two body-image indexes
        take("<HBiHBi") # two attribute groups
        take("<BBBH") # material, itemLv, fitType, ability
        take("<8i") # dye color tints
        take("<BBiiBBiBi") # availability, lv, prices, restrictions, element
        link=take("<H")[0]
        take("<BHBHBHBBHi6BI3B") # remaining ItemData.New fields
        length=take("<H")[0]
        cursor+=length # localizable description
        if kind==98:
            if link in treasure:raise ValueError(f"Duplicate treasure skillLink {link}")
            treasure[link]=(item_id,name)
    if cursor!=len(raw):raise ValueError(f"Item_C packed content not exhausted: {cursor}/{len(raw)}")
    return treasure

def level_requirement(treasure_id:int,current_level:int,tables=None):
    """READ-ONLY DVT next-level quote; no grants, currency or bag mutations.

    UI uses Treasure.GetCardCount: remainingCards = ownedCardCount -
      sum(TreasureLv.count for already-upgraded levels).
    Only the next-level static requirements are returned; a separate atomic
    resource transaction is needed to validate true balances and deduct once.
    """
    if not 1<=treasure_id<=114 or not 0<=current_level<=10:
        raise ValueError("Invalid treasure id/level")
    if current_level==10:return None
    tables=load_all() if tables is None else tables
    rows=[r for r in tables["TreasureLv"]["rows"] if r["id"]==treasure_id]
    by_level={r["level"]:r for r in rows}
    if len(rows)!=10 or set(by_level)!=set(range(1,11)):
        raise ValueError("Incomplete original TreasureLv curve")
    next_level=current_level+1
    next_row=by_level[next_level]
    return {
        "treasureId":treasure_id,
        "targetLevel":next_level,
        "cardCopiesRequired":next_row["count"],
        "consumedCardCopiesFromPriorLevels":sum(by_level[x]["count"] for x in range(1,next_level)),
        "materialsItemId":29869,
        "materialsCount":next_row["itemCount"],
        "goldCost":next_row["moneyCount"],
        "bonusAttribute":next_row["att"],
        "bonusValue":next_row["value"],
        "characterCriteriaId":next_row["characterId"],
        "environmentCriteriaId":next_row["environmentId"],
    }

def star_requirement(treasure_id:int,star:int,tables=None):
    """Static next-star source row only: NOT a ready-to-run upgrade quote.

    Original C123-003 supports multi-use counts (1,5,10), and each S123-003
    carries both star and StarExp. Never model it as an automatic +1 star.
    """
    if not 1<=treasure_id<=114 or not 0<=star<=6:raise ValueError("Invalid treasure/star")
    if star==6:return None
    tables=load_all() if tables is None else tables
    rows=[r for r in tables["TreasureStar"]["rows"] if r["id"]==treasure_id and r["star"]==star+1]
    if len(rows)!=1:raise ValueError("Missing next-star definition")
    return dict(rows[0])
