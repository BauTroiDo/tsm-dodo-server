"""Original DVT Treasure card reward definitions outside Eve item results.

Exact source packed readers:
  BlissBagData.New   <H H I I B>, 37,257 entries
  ExchangeData.New   <H H H>, 5,036 entries
  AchievementData.New: 37-byte records, 600 entries
These are source distribution definitions, NOT proof of S123 ownership banking.
"""
from collections import Counter
from pathlib import Path
from struct import calcsize,unpack_from
from audit_dvt_treasure_tables_20261012 import load_treasure_item_catalog, DATA

def decode_rows(suffix,fmt):
    blob=(DATA/f"IOS_{suffix}_C.dat").read_bytes()
    n=unpack_from("<i",blob)[0]
    size=calcsize(fmt)
    if n<0 or len(blob)!=4+n*size:
        raise ValueError(f"Invalid DVT {suffix} packed rows")
    return [unpack_from(fmt,blob,4+i*size) for i in range(n)]

def source_card_rewards():
    valid={item for item,_ in load_treasure_item_catalog().values()}
    bag=decode_rows("BlissBag","<HHIIB")
    exchange=decode_rows("Exchange","<HHH")
    achievements=decode_rows("AchievementData","<HIBBBBIBBIBIHBHHBI")
    return {
        "blissbag": [r for r in bag if r[1] in valid],
        "exchange":[r for r in exchange if r[1] in valid],
        "achievements":[r for r in achievements if r[12] in valid],
        "total_rows":{"blissbag":len(bag),"exchange":len(exchange),"achievements":len(achievements)},
    }

if __name__=="__main__":
    import json
    out=source_card_rewards()
    for label in ("blissbag","exchange","achievements"):
        rows=out[label]
        print(label,"total",out["total_rows"][label],
            "card_rows",len(rows),
            "distinct_cards",len({r[1] if label!="achievements" else r[12] for r in rows}))
        print("samples",json.dumps(rows[:15]))
    print("blissbag_container_ids",sorted({r[0] for r in out["blissbag"]}))
    print("exchange_table_ids",sorted({r[0] for r in out["exchange"]}))
