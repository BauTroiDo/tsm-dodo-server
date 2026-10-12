#!/usr/bin/env python3
"""Restore DVT original UiOpen gate columns exactly, undoing DEV-only bypass.

Authoritative input: tracked File.dat/Data-ios/IOS_UiOpen_C.dat (84 original
records) matching archival pre-2026-10-10 baseline. Mutates only the
four show/use level/mission-flag columns. Does NOT alter Lua, game state,
quests, source asset, or the menu/server-switch and event logic.
"""
import argparse,datetime,hashlib,os
from pathlib import Path
import struct

def parse(raw):
    """Decode all original DVT FunctionData.New fields and retain packed offsets."""
    n=struct.unpack_from("<I",raw,0)[0]
    if n!=84:
        raise ValueError(f"Expected 84 original DVT menu records, found {n}")
    rows=[]
    p=4
    for _ in range(n):
        start=p
        record_id,kind,group=struct.unpack_from("<BBB",raw,p)
        p+=3
        name=struct.unpack_from("<I",raw,p)[0]
        p+=4
        names=[]
        for _ in range(2):
            length=struct.unpack_from("<H",raw,p)[0]
            p+=2
            names.append(raw[p:p+length].decode("utf-16le"))
            p+=length
        offsets=(p,p+1,p+3,p+4)
        showLv=raw[p];p+=1
        showFlag=struct.unpack_from("<H",raw,p)[0];p+=2
        requireLv=raw[p];p+=1
        requireFlag=struct.unpack_from("<H",raw,p)[0];p+=2
        msg=struct.unpack_from("<I",raw,p)[0];p+=4
        switch=raw[p];p+=1
        rows.append(dict(id=record_id,kind=kind,group=group,name=name,
                         icon=names[0],animation=names[1],
                         showLv=showLv,showFlag=showFlag,
                         requireLv=requireLv,requireFlag=requireFlag,
                         msg=msg,switch=switch,positions=offsets,
                         start=start,end=p))
    if p!=len(raw) or len({r["id"] for r in rows})!=84:
        raise ValueError("Invalid or unrecognized DVT UiOpen binary layout")
    return rows


ORIGINAL_SHA="af759749cf6d33081df7de99f715cf682e324bb48fb0ff4c57edb932a78c8b4e"
DEV_SHA="d3a30b6ae1c6e67454df8218d7a2fea7ed705f29d8180fb3cb528db4b4fb7980"
GATES=("showLv","showFlag","requireLv","requireFlag")
def sha(data): return hashlib.sha256(data).hexdigest()

def compare_gates(current,original):
    if len(current)!=len(original) or len(current)!=2978:
        raise ValueError("Unknown original UiOpen file size")
    original_rows=parse(original)
    current_rows=parse(current)
    o={r["id"]:r for r in original_rows}
    diff_ids=[]
    allowed=set()
    for r in current_rows:
        previous=o.get(r["id"])
        if previous is None: raise ValueError(f"Unknown record ID {r['id']}")
        for field in ("id","kind","group","name","icon","animation","msg","switch","positions","start","end"):
            if r[field]!=previous[field]:
                raise ValueError(f"Non-gating content changed: id={r['id']} field={field}")
        p0,p1,p2,p3=r["positions"]
        allowed.update((p0,p1,p1+1,p2,p3,p3+1))
        if any(r[field]!=previous[field] for field in GATES):
            diff_ids.append(r["id"])
    different={i for i,(a,b) in enumerate(zip(current,original)) if a!=b}
    if not different.issubset(allowed):raise ValueError("Non-gating byte difference")
    print("VERIFIED_DVT_ORIGINAL",len(original_rows),"SOURCE_HASH",sha(original),
          "CURRENT",sha(current),"GATE_RESTORES",len(diff_ids),
          "CHANGED_BYTES",len(different),"IDS",diff_ids)
    return different,diff_ids

def restore(target,source,apply=False):
    target=Path(target);source=Path(source)
    original=source.read_bytes()
    current=target.read_bytes()
    if sha(original)!=ORIGINAL_SHA:
        raise ValueError("Canonical DVT source table SHA mismatch: refuse")
    if sha(current) not in (DEV_SHA,ORIGINAL_SHA):
        raise ValueError(f"Unknown target version; refuse: {target} {sha(current)}")
    compare_gates(current,original)
    if current==original:
        print("ALREADY_CANONICAL",target,ORIGINAL_SHA)
        return ORIGINAL_SHA
    if not apply:
        print("DRY_RUN_ONLY",target)
        return ORIGINAL_SHA
    backup=target.with_name(target.name+".before-dvt-gate-restore-"+
         datetime.datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
    if backup.exists():raise FileExistsError(backup)
    backup.write_bytes(current)
    if sha(backup.read_bytes())!=sha(current):raise IOError("Backup failed")
    temporary=target.with_name(target.name+".dvt-gate-restore.tmp")
    if temporary.exists():raise FileExistsError(temporary)
    try:
        temporary.write_bytes(original)
        if sha(temporary.read_bytes())!=ORIGINAL_SHA:raise IOError("Stage failed")
        os.replace(temporary,target)
    finally:
        if temporary.exists():temporary.unlink()
    if sha(target.read_bytes())!=ORIGINAL_SHA:raise IOError("Postwrite hash mismatch")
    print("RESTORED_CANONICAL",target,ORIGINAL_SHA)
    print("BACKUP",backup)
    return ORIGINAL_SHA

if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("target",type=Path)
    p.add_argument("--source",type=Path,default=Path("File.dat/Data-ios/IOS_UiOpen_C.dat"))
    p.add_argument("--apply",action="store_true")
    args=p.parse_args()
    restore(args.target,args.source,args.apply)
