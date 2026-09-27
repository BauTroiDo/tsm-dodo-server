from pathlib import Path
import hashlib, shutil, datetime, json, sys
sys.path.insert(0, r"C:\Server-Game\tsm_dodo")
import lz4_packed
base=Path(r"C:\Server-Game\tsm_dodo\cdn\mirror\tsr")
root=base/"2.4.76"/"ResourcesPacked"
original=base/"2.4.76 - Copy"/"ResourcesPacked"
stamp=datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
backup=base/("jmg-repair-backup-"+stamp);backup.mkdir()
manifest=root/"ETCJmg.dat"
lines=manifest.read_text(encoding="utf-8-sig").splitlines()
plan=[]
for line in lines:
 a=line.split("|")
 if len(a)!=4:raise ValueError("Invalid manifest line")
 rel=a[0]+".lz4";src=original/rel;dst=root/rel
 b=src.read_bytes();raw=lz4_packed.decompress(b)
 if len(raw)!=int(a[2]) or hashlib.md5(raw).hexdigest().upper()!=a[1].upper():
  src=dst; b=src.read_bytes(); raw=lz4_packed.decompress(b)
  if len(raw)!=int(a[2]) or hashlib.md5(raw).hexdigest().upper()!=a[1].upper():raise ValueError("Current content differs: "+rel)
 a[3]=str(len(b));plan.append((src,dst,"|".join(a)))
shutil.copy2(manifest,backup/"ETCJmg.dat")
for src,dst,line in plan:
 save=backup/dst.name
 if dst.exists():shutil.copy2(dst,save)
 tmp=dst.with_name(dst.name+".repair-temp")
 shutil.copy2(src,tmp);tmp.replace(dst)
tmp=manifest.with_name("ETCJmg.dat.repair-temp")
tmp.write_text("\r\n".join(p[2] for p in plan)+"\r\n",encoding="utf-8",newline="")
tmp.replace(manifest)
print(json.dumps({"restored":len(plan),"backup":str(backup)}))
