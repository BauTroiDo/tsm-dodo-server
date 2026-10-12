#!/usr/bin/env python3
"""DEV-only UI icon menu visibility/usage unlock; no map/teleport, battle, data mutation.

Keeps server-switch, event, house/battle/watch-mode checks in Lua Function.lua.
Patch ONLY showLv/showFlag/requireLv/requireFlag in UiOpen_C.dat.
"""
import argparse,datetime,hashlib,os,struct
from pathlib import Path

INPUT_HASH="e5d123bb74520866f2002643ac90606432ea5dbd7240c8064c8e4e23f366eb48"
PROTECTED_IDS={32} # EFunction.Teleport: quest/map gate intentionally preserved.
EXCLUDED_GROUPS={0,5,6} # ghost/avatar flags, combat controls.
def sha(raw):return hashlib.sha256(raw).hexdigest()
def parse(raw):
    n=struct.unpack_from("<I",raw,0)[0]
    if n!=84:raise ValueError(f"Original DVT UiOpen row count expected84, got{n}")
    p=4; result=[]
    for _ in range(n):
        start=p
        record_id,kind,group=struct.unpack_from("<BBB",raw,p);p+=3
        name=struct.unpack_from("<I",raw,p)[0];p+=4
        fields=[]
        for _ in range(2):
            l=struct.unpack_from("<H",raw,p)[0];p+=2
            text=raw[p:p+l].decode("utf-16le");p+=l
            fields.append(text)
        locations=(p,p+1,p+3,p+4)
        shlv=raw[p];p+=1
        shflag=struct.unpack_from("<H",raw,p)[0];p+=2
        relv=raw[p];p+=1
        reflag=struct.unpack_from("<H",raw,p)[0];p+=2
        msg=struct.unpack_from("<I",raw,p)[0];p+=4
        switch=raw[p];p+=1
        result.append(dict(id=record_id,kind=kind,group=group,name=name,
                           icon=fields[0],animation=fields[1],
                           showLv=shlv,showFlag=shflag,
                           requireLv=relv,requireFlag=reflag,msg=msg,
                           switch=switch,positions=locations,start=start,end=p))
    if p!=len(raw) or len({r['id'] for r in result})!=n:
        raise ValueError("Original DVT UiOpen binary layout not recognized")
    return result
def patch_bytes(raw):
    rows=parse(raw);updated=bytearray(raw);intended=[]
    for r in rows:
        if r["id"] in PROTECTED_IDS or r["group"] in EXCLUDED_GROUPS:
            continue
        if r["group"] not in (1,2,3) or r["kind"]==0 or not r["icon"] or r["icon"]=="0":
            continue
        p0,p1,p2,p3=r["positions"]
        if r["showLv"]>1:
            updated[p0]=1
        if r["showFlag"]>0:
            struct.pack_into("<H",updated,p1,0)
        if r["requireLv"]>0:
            updated[p2]=0
        if r["requireFlag"]>0:
            struct.pack_into("<H",updated,p3,0)
        intended.extend(range(p0,p0+1))
        intended.extend(range(p1,p1+2))
        intended.extend(range(p2,p2+1))
        intended.extend(range(p3,p3+2))
    new=parse(updated)
    o={r["id"]:r for r in rows};by={r["id"]:r for r in new}
    touched=[]
    for k,v in o.items():
        w=by[k]
        non_gate=['id','kind','group','name','icon','animation','msg','switch','positions','start','end']
        if any(v[x]!=w[x] for x in non_gate):
            raise ValueError(f"Changed UiOpen non-gating column on {k}")
        gates=['showLv','showFlag','requireLv','requireFlag']
        if any(v[x]!=w[x] for x in gates):
            if v['group'] not in (1,2,3) or k in PROTECTED_IDS:
                raise ValueError(f"Changed protected UI row {k}")
            touched.append(k)
    differences={i for i,(a,b) in enumerate(zip(raw,updated)) if a!=b}
    if not differences.issubset(set(intended)):
        raise ValueError("Out-of-scope byte mutation")
    protected=by[32]
    if (protected["showFlag"],protected["requireFlag"],protected["switch"])!=(
        o[32]["showFlag"],o[32]["requireFlag"],o[32]["switch"]):
        raise ValueError("Teleport map gate changed")
    return bytes(updated),touched,len(differences),rows,new

def execute(path,apply=False):
    path=Path(path)
    before=path.read_bytes()
    if sha(before)!=INPUT_HASH and sha(before)!=OUTPUT_HASH:
        raise ValueError(f"Refuse unknown data version SHA: {path}: {sha(before)}")
    changed,ids,diff,orig,after=patch_bytes(before)
    if before==changed:
        if sha(before)!=OUTPUT_HASH:
            raise ValueError("Unknown already-unlocked UI data")
        print("ALREADY_PATCHED",path,sha(before));return sha(before)
    if sha(before)!=INPUT_HASH or sha(changed)!=OUTPUT_HASH:
        raise ValueError("Exact baseline/new SHA mismatch")
    print("DATA",path,"ROWS",len(orig),"CHANGED_ICONS",len(ids),
          "CHANGED_BYTES",diff,"SHA",sha(before),"->",sha(changed))
    print("UNLOCK_IDS",ids)
    if apply:
        backup=path.with_name(path.name+".before-dev-icon-unlock-"+datetime.datetime.now().strftime("%Y%m%d_%H%M%S")+".bak")
        if backup.exists():raise FileExistsError(backup)
        backup.write_bytes(before)
        if sha(backup.read_bytes())!=INPUT_HASH:raise IOError("Backup SHA mismatch")
        temp=path.with_name(path.name+".dev-icon-unlock.tmp")
        if temp.exists():raise FileExistsError(temp)
        try:
            temp.write_bytes(changed)
            if sha(temp.read_bytes())!=OUTPUT_HASH:raise IOError("Staging SHA mismatch")
            os.replace(temp,path)
        finally:
            if temp.exists():temp.unlink()
        if sha(path.read_bytes())!=OUTPUT_HASH:raise IOError("Installed SHA mismatch")
        print("BACKUP",backup)
    else:
        print("DRY_RUN_ONLY")
    return sha(changed)

OUTPUT_HASH="d3a30b6ae1c6e67454df8218d7a2fea7ed705f29d8180fb3cb528db4b4fb7980"
if __name__=="__main__":
    ap=argparse.ArgumentParser()
    ap.add_argument("file",type=Path)
    ap.add_argument("--apply",action="store_true")
    args=ap.parse_args()
    execute(args.file,args.apply)
