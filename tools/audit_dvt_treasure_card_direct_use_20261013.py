"""Original ItemData.New evidence that all 114 Treasure cards can be directly used.

Server must not consume directUse Treasure kind98 as generic attribute item.
"""
from pathlib import Path
from struct import unpack_from,calcsize
ROOT=Path(__file__).resolve().parents[1]
ITEM=ROOT/"File.dat/Data-ios/IOS_Item_C.dat"

def treasure_use_records():
    b=ITEM.read_bytes()
    ptr=4
    n=unpack_from("<i",b)[0]
    out=[]
    def rd(fmt):
        nonlocal ptr
        vals=unpack_from(fmt,b,ptr)
        ptr+=calcsize(fmt)
        return vals
    for _ in range(n):
        strlen=rd("<H")[0]
        ptr+=strlen
        kind,item_id=rd("<BH")
        rd("<HHH")                 # icon, male/female image
        rd("<HBiHBi")              # attribute kinds and values
        material,level,fit,ability=rd("<BBBH")
        rd("<8i")                  # colors
        rd("<BBiiBBiBi")
        skill_link=rd("<H")[0]
        tail=rd("<BHBHBHBBHi6BI3B")
        length=rd("<H")[0]
        ptr+=length
        if kind==98:
            out.append((item_id,skill_link,ability,tail[7]))
    if ptr!=len(b):raise ValueError("Item_C packed content not exhausted")
    return out
if __name__=="__main__":
    from collections import Counter
    cards=treasure_use_records()
    print("CARDS",len(cards))
    print("ABILITIES",Counter(r[2] for r in cards))
    print("DIRECT_USE",Counter(r[3] for r in cards))
